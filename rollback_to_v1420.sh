#!/usr/bin/env bash
# 回退脚本：将应用回退到「当前版本」v1.4.20（用户要求：必须支持回退到当前版本）
#
# 为什么需要这个脚本：
#   Android 不允许 versionCode 递减安装（v1.4.20=28 < v1.4.21=29），
#   设备上的 v1.4.21 无法「原地降级」到 v1.4.20。
#   因此回退 = 用 v1.4.20 的真实源码（commit 1a09d2ab，即 tag v1.4.20 指向的 commit）
#   重建一个 versionCode=30 / versionName=1.4.20-rollback 的发布，
#   使其可覆盖安装到已装 v1.4.21 的设备上，行为完全等同 v1.4.20。
#
# 纪律（沿用项目发布规范）：
#   - 仅基于远程 main 当前 HEAD 作为 parent，构建 1 个「回退」commit + 1 个全新 annotated tag
#   - tag 已存在则退出，避免重复发布
#   - 通过 GitHub Actions CI 构建 APK，禁止本地构建
#
# 注意：本脚本默认【不自动执行】。仅在确认 v1.4.21 需要回退时运行：
#   bash /root/jianji/rollback_to_v1420.sh
set -euo pipefail
REPO=gnaiq/jianji
NEW_TAG=v1.4.20-rollback
RB_VERSIONCODE=35   # 必须 > 线上最高 versionCode（v1.6.1=34）才能覆盖安装
RB_VERSIONNAME=1.4.20-rollback

if gh api "repos/$REPO/git/refs/tags/$NEW_TAG" >/dev/null 2>&1; then
  echo "ERROR: $NEW_TAG 已存在，停止以免重复发布"
  exit 1
fi

# 1) 解析 v1.4.20 真实源码 commit（tag v1.4.20 可能指向 annotated tag 对象）
REF_OBJ=$(gh api "repos/$REPO/git/refs/tags/v1.4.20" --jq '.object.sha')
REF_TYPE=$(gh api "repos/$REPO/git/refs/tags/v1.4.20" --jq '.object.type')
if [ "$REF_TYPE" = "tag" ]; then
  BASE=$(gh api "repos/$REPO/git/tags/$REF_OBJ" --jq '.object.sha')
else
  BASE=$REF_OBJ
fi
echo "v1.4.20 source commit = $BASE"

# 2) 当前 main HEAD 作为回退 commit 的 parent（保持线性历史，不清空 v1.4.21）
PARENT=$(gh api "repos/$REPO/git/ref/heads/main" --jq '.object.sha')
echo "parent (current main) = $PARENT"

# 3) 取 v1.4.20 完整文件树
gh api "repos/$REPO/git/trees/$BASE?recursive=1" > /tmp/rb_base_tree.json

# 4) 仅覆写 app/build.gradle.kts：提升 versionCode、改写 versionName 以匹配回退 tag
BG_SHA=$(jq -r '.tree[] | select(.path=="app/build.gradle.kts") | .sha' /tmp/rb_base_tree.json)
RAW=$(gh api "repos/$REPO/git/blobs/$BG_SHA" --jq '.content' | base64 -d)
NEW=$(echo "$RAW" \
  | sed -e 's/versionCode = 28/versionCode = '"$RB_VERSIONCODE"'/' \
        -e 's/versionName = "1.4.20"/versionName = "'"$RB_VERSIONNAME"'"/')
NEW_B64=$(echo "$NEW" | base64 -w0)
NEW_SHA=$(gh api "repos/$REPO/git/blobs" -f "content=$NEW_B64" -f encoding=base64 --jq '.sha')
echo "build.gradle.kts blob -> $NEW_SHA (versionCode=$RB_VERSIONCODE versionName=$RB_VERSIONNAME)"

# 5) 以 v1.4.20 树为基底，仅替换 build.gradle.kts
OVERRIDE_JSON=$(jq -n --arg p "app/build.gradle.kts" --arg s "$NEW_SHA" '. + {($p): $s}')
TREE=$(jq -n \
  --arg basec "$BASE" \
  --argjson base "$(jq -c '.tree | map(select(.type=="blob"))' /tmp/rb_base_tree.json)" \
  --argjson ov "$OVERRIDE_JSON" \
  '($base | map(.sha = ($ov[.path] // .sha))) as $upd | {base_tree:$basec, tree:$upd}')
TREE_SHA=$(echo "$TREE" | gh api "repos/$REPO/git/trees" --input - --jq '.sha')
echo "tree = $TREE_SHA"

DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
MSG="rollback: 回退到 v1.4.20（versionCode=$RB_VERSIONCODE，行为等同 v1.4.20，可覆盖安装到 v1.6.1）"
COMMIT_JSON=$(jq -n --arg tree "$TREE_SHA" --arg parent "$PARENT" --arg msg "$MSG" '{tree:$tree, parents:[$parent], message:$msg}')
COMMIT_SHA=$(echo "$COMMIT_JSON" | gh api "repos/$REPO/git/commits" --input - --jq '.sha')
echo "commit = $COMMIT_SHA"

gh api -X PATCH "repos/$REPO/git/refs/heads/main" -F sha="$COMMIT_SHA" -F force=false
echo "main updated (rolled back source to v1.4.20 code)"

TAG_JSON=$(jq -n \
  --arg tag "$NEW_TAG" --arg msg "$NEW_TAG" --arg obj "$COMMIT_SHA" \
  --arg name "jianji" --arg email "jianji@users.noreply.github.com" --arg date "$DATE" \
  '{tag:$tag, message:$msg, object:$obj, type:"commit", tagger:{name:$name, email:$email, date:$date}}')
TAG_SHA=$(echo "$TAG_JSON" | gh api "repos/$REPO/git/tags" --input - --jq '.sha')
echo "tag object = $TAG_SHA"
gh api -X POST "repos/$REPO/git/refs" -f ref="refs/tags/$NEW_TAG" -f sha="$TAG_SHA"
echo "DONE tag=$NEW_TAG commit=$COMMIT_SHA —— CI 将构建可覆盖安装到 v1.4.22 的回退 APK"
