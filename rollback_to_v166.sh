#!/usr/bin/env bash
# rollback_to_v166.sh — jianji 回退到当前稳定版 v1.6.6 的防御脚本
#
# 适用场景：未来版本（如 v1.6.7）发布后若发现严重问题，用它快速发一个
#           「更高 versionCode 的 v1.6.6」，覆盖安装即可把 App 行为回退到本稳定版
#           （CI 自动构建签名 APK 并建 Release）。v1.6.6 已内置
#           fallbackToDestructiveMigration()，降 schema 不会闪退。
#
# 触发方式：GitHub Actions（build-apk.yml）在打 tag 时自动构建，因此本脚本只负责
#           基于 v1.6.6 的源码树创建一次专门的高 versionCode 提交 + annotated tag，
#           由 tag push 触发 CI。本地绝不构建 APK（环境铁律）。
#
# ⚠️ 数据风险提示（务必先看）：
#   1) 回退属于「降 schema」，Room 走 fallbackToDestructiveMigration 会清空本地数据库。
#      交易/分类/标签/回收站等 ALL 数据回退即弃，属预期内损失；云备份（如有）需重新导入。
#   2) 仅在确认不需要当前（坏）版本数据时使用。
#
# 用法:
#   GITHUB_TOKEN=<token> ./rollback_to_v166.sh
#   （或用已 gh 登录的环境直接执行）
#
# 依赖: gh(已登录) python3 base64

set -uo pipefail

REPO="gnaiq/jianji"
API="https://api.github.com/repos/$REPO"
BASE_TAG="v1.6.6"
ROLLBACK_TAG="rollback/v1.6.6"

die(){ echo "::error:: $*" >&2; exit 1; }

# 1) 解析 v1.6.6 annotated tag -> commit sha
tagref=$(gh api "$API/git/refs/tags/$BASE_TAG" --jq '.object.sha') \
  || die "无法解析 $BASE_TAG 的 tag ref"
tagobj=$(gh api "$API/git/tags/$tagref" --jq '{sha:.object.sha,type:.object.type}') \
  || die "无法读取 tag 对象"
commit_sha=$(echo "$tagobj" | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])')
[ -n "$commit_sha" ] || die "无法取得 $BASE_TAG 的 commit"
echo "✅ $BASE_TAG commit = $commit_sha"

# 2) 取该 commit 的 tree
tree_sha=$(gh api "$API/git/commits/$commit_sha" --jq '.tree.sha') \
  || die "无法读取 tree"
echo "✅ base tree = $tree_sha"

# 3) 计算回退版 versionCode：取当前 main 的 versionCode + 1，确保 > 任何已发版本可覆盖安装
BASE_VC=$(gh api "$API/contents/app/build.gradle.kts?ref=main" --jq '.content' \
  | base64 -d | grep -oE 'versionCode = [0-9]+' | grep -oE '[0-9]+')
ROLLBACK_VC=$(( ${BASE_VC:-39} + 1 ))
ROLLBACK_VN="1.6.6"
echo "✅ 回退版 versionCode = $ROLLBACK_VC (base main vc=${BASE_VC:-39})"

new_gradle=$(gh api "$API/contents/app/build.gradle.kts?ref=$commit_sha" --jq '.content' \
  | base64 -d \
  | sed -E "s/versionCode = [0-9]+/versionCode = $ROLLBACK_VC/" \
  | sed -E "s/versionName = \"[^\"]+\"/versionName = \"$ROLLBACK_VN\"/")
[ -n "$new_gradle" ] || die "无法生成回退版 build.gradle.kts"
b64_gradle=$(printf '%s' "$new_gradle" | base64 -w0)

# 4) 建 blob
blob_sha=$(printf '%s' "$b64_gradle" | python3 -c 'import json,sys; print(json.dumps({"content":sys.stdin.read()}))' \
  | gh api -X POST "$API/git/blobs" --input - --jq '.sha') \
  || die "创建 blob 失败"
echo "✅ gradle blob = $blob_sha"

# 5) 建新 tree（替换 app/build.gradle.kts）
new_tree=$(python3 - "$tree_sha" "$blob_sha" <<'PY'
import json,sys
base, blob = sys.argv[1], sys.argv[2]
print(json.dumps({"base_tree": base, "tree": [
  {"path":"app/build.gradle.kts","mode":"100644","type":"blob","sha":blob}
]}))
PY
)
new_tree_sha=$(echo "$new_tree" | gh api -X POST "$API/git/trees" --input - --jq '.sha') \
  || die "创建 tree 失败"
echo "✅ new tree = $new_tree_sha"

# 6) 建 commit（基于 v1.6.6 的 parent）
parent_info=$(python3 - "$commit_sha" "$new_tree_sha" "$ROLLBACK_TAG" <<'PY'
import json,sys
p,t,tag=sys.argv[1],sys.argv[2],sys.argv[3]
print(json.dumps({"message":f"rollback: rebuild {tag} (vc={40}) on top of v1.6.6",
  "tree":t,"parents":[p]}))
PY
)
new_commit=$(echo "$parent_info" | gh api -X POST "$API/git/commits" --input - --jq '.sha') \
  || die "创建 commit 失败"
echo "✅ rollback commit = $new_commit"

# 7) 建 annotated tag（两步法）
date=$(date -u +%Y-%m-%dT%H:%M:%SZ)
tagbody=$(python3 - "$ROLLBACK_TAG" "$new_commit" "$date" <<'PY'
import json,sys
tag,obj,date=sys.argv[1],sys.argv[2],sys.argv[3]
print(json.dumps({"tag":tag,"message":f"Rollback build {tag}","object":obj,"type":"commit",
  "tagger":{"name":"jianji-release-bot","email":"release@jianji.local","date":date}}))
PY
)
tagobj_sha=$(echo "$tagbody" | gh api -X POST "$API/git/tags" --input - --jq '.sha') \
  || die "创建 tag 对象失败"
gh api -X POST "$API/git/refs" -f ref="refs/tags/$ROLLBACK_TAG" -f sha="$tagobj_sha" \
  || die "创建 tag ref 失败"
echo "🚀 已推送 tag $ROLLBACK_TAG（vc=$ROLLBACK_VC），等待 CI 构建回退版 APK..."

# 8) 轮询 CI
st=""
for i in $(seq 1 36); do
  info=$(gh api "$API/actions/runs?per_page=20" --jq ".workflow_runs[] | select(.head_sha==\"$new_commit\") | \"\(.id) \(.conclusion)\"")
  if [ -n "$info" ]; then
    run_id=$(echo "$info" | head -1 | awk '{print $1}')
    st=$(echo "$info" | head -1 | awk '{print $2}')
    echo "poll $i: run=$run_id conclusion=$st"
    [ "$st" != "null" ] && break
  else
    echo "poll $i: CI run 尚未出现"
  fi
  sleep 10
done
[ "$st" = "success" ] || die "CI 未成功 (conclusion=$st)"
gh api "$API/releases/tags/$ROLLBACK_TAG" --jq '{id,tag_name,html_url,assets:[.assets[].name]}' \
  || die "Release 未生成"
echo "✅ 回退版 Release $ROLLBACK_TAG 已生成（上行为资产清单）"
