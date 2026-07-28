#!/usr/bin/env bash
# 回退脚本：将应用回退到「当前版本」v1.6.2（用户要求：必须支持回退到当前版本）。
#
# 为什么需要这个脚本：
#   Android 不允许 versionCode 递减安装（v1.6.2=35 < v1.6.3=36），
#   设备上的 v1.6.3 无法「原地降级」到 v1.6.2。
#   因此回退 = 用 v1.6.2 源码重建 versionCode=37 / versionName=1.6.2.1 的发布，
#   使其可覆盖安装到已装 v1.6.3 的设备上。
#
# ⚠️ DB Schema 兼容（与 v1.6.1 回退的关键差异）：
#   v1.6.3 将 Room DB 升级到 version 5（date 等索引）。若回退包用纯 v1.6.2
#   数据层（version 4），已跑过 v1.6.3 的设备打开时 Room 触发 5→4 降级异常直接崩溃。
#   因此回退包 = v1.6.2 全部源码 + 保留 v1.6.3 的两个数据层文件：
#     - data/Transaction.kt（date 索引声明）
#     - data/JianjiDatabase.kt（version=5 + MIGRATION_4_5）
#   索引对 v1.6.2 业务逻辑完全透明，行为等同 v1.6.2。
#
# 命名说明：tag 必须为纯数字（v1.6.2.1）以通过 CI 的 versionName 一致性守卫。
#
# 纪律（沿用项目发布规范）：
#   - 基于远程 main 当前 HEAD 作为 parent，构建 1 个「回退」commit + 1 个全新 annotated tag
#   - tag 已存在则退出，避免重复发布
#   - 通过 GitHub Actions CI 构建 APK，禁止本地构建
#
# 注意：本脚本默认【不自动执行】。仅在确认 v1.6.3 需要回退时运行：
#   bash rollback_to_v162.sh
#
# 更简单（无需本脚本）的回退路径：设备上「卸载 v1.6.3」（DB 一并清除）后，
#   从 GitHub Releases 重装 v1.6.2 APK，再通过公共下载目录的自动备份 JSON 恢复数据。
set -euo pipefail
REPO=gnaiq/jianji
NEW_TAG=v1.6.2.1
RB_VERSIONCODE=37   # 必须 > 线上最高 versionCode（v1.6.3=36）才能覆盖安装
RB_VERSIONNAME=1.6.2.1
KEEP_FROM_V163=(
  "app/src/main/java/com/example/jianji/data/Transaction.kt"
  "app/src/main/java/com/example/jianji/data/JianjiDatabase.kt"
)

if gh api "repos/$REPO/git/refs/tags/$NEW_TAG" >/dev/null 2>&1; then
  echo "ERROR: $NEW_TAG 已存在，停止以免重复发布"
  exit 1
fi

# 解析 tag → 真实源码 commit（annotated tag 需二跳）
resolve_tag() {
  local ref_obj ref_type
  ref_obj=$(gh api "repos/$REPO/git/refs/tags/$1" --jq '.object.sha')
  ref_type=$(gh api "repos/$REPO/git/refs/tags/$1" --jq '.object.type')
  if [ "$ref_type" = "tag" ]; then
    gh api "repos/$REPO/git/tags/$ref_obj" --jq '.object.sha'
  else
    echo "$ref_obj"
  fi
}
BASE=$(resolve_tag v1.6.2);  echo "v1.6.2 source commit = $BASE"
C163=$(resolve_tag v1.6.3);  echo "v1.6.3 source commit = $C163"

PARENT=$(gh api "repos/$REPO/git/ref/heads/main" --jq '.object.sha')
echo "parent (current main) = $PARENT"

gh api "repos/$REPO/git/trees/$BASE?recursive=1" > /tmp/rb162_base_tree.json
gh api "repos/$REPO/git/trees/$C163?recursive=1" > /tmp/rb162_v163_tree.json

# 1) build.gradle.kts：提升 versionCode、改写 versionName
BG_SHA=$(jq -r '.tree[] | select(.path=="app/build.gradle.kts") | .sha' /tmp/rb162_base_tree.json)
RAW=$(gh api "repos/$REPO/git/blobs/$BG_SHA" --jq '.content' | base64 -d)
NEW=$(echo "$RAW" \
  | sed -e 's/versionCode = 35/versionCode = '"$RB_VERSIONCODE"'/' \
        -e 's/versionName = "1.6.2"/versionName = "'"$RB_VERSIONNAME"'"/')
NEW_B64=$(echo "$NEW" | base64 -w0)
NEW_SHA=$(gh api "repos/$REPO/git/blobs" -f "content=$NEW_B64" -f encoding=base64 --jq '.sha')
echo "build.gradle.kts blob -> $NEW_SHA (versionCode=$RB_VERSIONCODE versionName=$RB_VERSIONNAME)"

# 2) 组装覆盖表：build.gradle.kts 用新 blob；DB 两文件直接引用 v1.6.3 的 blob SHA
OVERRIDE_JSON=$(jq -n --arg p "app/build.gradle.kts" --arg s "$NEW_SHA" '. + {($p): $s}')
for path in "${KEEP_FROM_V163[@]}"; do
  SHA163=$(jq -r --arg p "$path" '.tree[] | select(.path==$p) | .sha' /tmp/rb162_v163_tree.json)
  if [ -z "$SHA163" ] || [ "$SHA163" = "null" ]; then
    echo "ERROR: v1.6.3 树中找不到 $path"; exit 1
  fi
  OVERRIDE_JSON=$(echo "$OVERRIDE_JSON" | jq --arg p "$path" --arg s "$SHA163" '. + {($p): $s}')
  echo "keep from v1.6.3: $path -> $SHA163"
done

# 3) 以 v1.6.2 树为基底套用覆盖表
TREE=$(jq -n \
  --arg basec "$BASE" \
  --argjson base "$(jq -c '.tree | map(select(.type=="blob"))' /tmp/rb162_base_tree.json)" \
  --argjson ov "$OVERRIDE_JSON" \
  '($base | map(.sha = ($ov[.path] // .sha))) as $upd | {base_tree:$basec, tree:$upd}')
TREE_SHA=$(echo "$TREE" | gh api "repos/$REPO/git/trees" --input - --jq '.sha')
echo "tree = $TREE_SHA"

DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
MSG="rollback: 回退到 v1.6.2 行为（versionCode=$RB_VERSIONCODE, 可覆盖安装到 v1.6.3；保留 DB schema v5 防降级崩溃）"
COMMIT_JSON=$(jq -n --arg tree "$TREE_SHA" --arg parent "$PARENT" --arg msg "$MSG" '{tree:$tree, parents:[$parent], message:$msg}')
COMMIT_SHA=$(echo "$COMMIT_JSON" | gh api "repos/$REPO/git/commits" --input - --jq '.sha')
echo "commit = $COMMIT_SHA"

gh api -X PATCH "repos/$REPO/git/refs/heads/main" -F sha="$COMMIT_SHA" -F force=false
echo "main updated (rolled back source to v1.6.2 behavior)"

TAG_JSON=$(jq -n \
  --arg tag "$NEW_TAG" --arg msg "$NEW_TAG" --arg obj "$COMMIT_SHA" \
  --arg name "jianji" --arg email "jianji@users.noreply.github.com" --arg date "$DATE" \
  '{tag:$tag, message:$msg, object:$obj, type:"commit", tagger:{name:$name, email:$email, date:$date}}')
TAG_SHA=$(echo "$TAG_JSON" | gh api "repos/$REPO/git/tags" --input - --jq '.sha')
echo "tag object = $TAG_SHA"
gh api -X POST "repos/$REPO/git/refs" -f ref="refs/tags/$NEW_TAG" -f sha="$TAG_SHA"
echo "DONE tag=$NEW_TAG commit=$COMMIT_SHA —— CI 将构建可覆盖安装到 v1.6.3 的回退 APK（v1.6.2 行为）"
