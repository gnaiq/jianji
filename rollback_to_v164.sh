#!/usr/bin/env bash
# v1.6.5 回退到 v1.6.4 脚本（默认不执行）。
#
# 背景：v1.6.5 未升级 DB schema（仍为 v5），与 v1.6.4 数据完全兼容。
# 因此回退有两种方式：
#   方式A（推荐，零构建）：Android 设备「卸载 v1.6.5」→ 重装已发布的 v1.6.4 APK，
#        公共下载目录的自动备份 JSON 仍在，数据无损。
#        （注意：Android 降级需先卸载，会清应用数据；若想保留数据请走方式B）
#   方式B（本脚本，覆盖安装免卸载）：重建 v1.6.4 源码为新 tag v1.6.4.1
#        （versionCode=39 > v1.6.5 的 38，可覆盖安装），走 CI 重新出包，保留数据。
#
# 本脚本采用方式B：以 v1.6.4 发布树的源码为基，仅把 versionCode/versionName
# 提到 39 / 1.6.4.1，满足 CI 的 versionCode 单调递增 + versionName==tag 守卫，
# 让 v1.6.4.1 能直接「升级覆盖」已装的 v1.6.5。
#
# 纪律：Git Data API 通路；annotated tag 两步走。
set -euo pipefail
cd "${WORKDIR:-/root/jianji}"
REPO=gnaiq/jianji
SRC_TAG=v1.6.4
NEW_TAG=v1.6.4.1
MSG="rollback: 回退到 v1.6.4（tag v1.6.4.1, versionCode=39）——覆盖安装免卸载"

if gh api "repos/$REPO/releases/tags/$NEW_TAG" >/dev/null 2>&1; then
  echo "ERROR: $NEW_TAG 已有 Release，禁止重发"; exit 1
fi

# v1.6.4 标注 tag -> tag 对象 -> commit -> tree
TAG_OBJ=$(gh api "repos/$REPO/git/refs/tags/$SRC_TAG" --jq '.object.sha')
SRC_COMMIT=$(gh api "repos/$REPO/git/tags/$TAG_OBJ" --jq '.object.sha')
BASE_TREE=$(gh api "repos/$REPO/git/commits/$SRC_COMMIT" --jq '.tree.sha')
echo "v1.6.4 source tree = $BASE_TREE"

# 取 v1.6.4 的 build.gradle.kts，改 versionCode/versionName
RAW_B64=$(gh api "repos/$REPO/contents/app/build.gradle.kts?ref=$SRC_TAG" --jq '.content' | tr -d '\n')
RAW=$(printf '%s' "$RAW_B64" | base64 -d)
NEW_GRADLE=$(printf '%s' "$RAW" \
  | sed -E 's/versionCode = [0-9]+/versionCode = 39/' \
  | sed -E 's/versionName = "[^"]+"/versionName = "1.6.4.1"/')
NEW_GRADLE_B64=$(printf '%s' "$NEW_GRADLE" | base64 -w0)
GRADLE_BLOB=$(gh api "repos/$REPO/git/blobs" -f "content=$NEW_GRADLE_B64" -f encoding=base64 --jq '.sha')
echo "build.gradle blob = $GRADLE_BLOB"

TREE_SHA=$(jq -n --arg basec "$BASE_TREE" --arg s "$GRADLE_BLOB" \
  '{base_tree:$basec, tree:[{path:"app/build.gradle.kts", mode:"100644", type:"blob", sha:$s}]}' \
  | gh api "repos/$REPO/git/trees" --input - --jq '.sha')
echo "tree = $TREE_SHA"

BASE=$(gh api "repos/$REPO/git/ref/heads/main" --jq '.object.sha')
COMMIT_SHA=$(jq -n --arg tree "$TREE_SHA" --arg parent "$BASE" --arg msg "$MSG" \
  '{tree:$tree, parents:[$parent], message:$msg}' \
  | gh api "repos/$REPO/git/commits" --input - --jq '.sha')
echo "commit = $COMMIT_SHA"

gh api -X PATCH "repos/$REPO/git/refs/heads/main" -F sha="$COMMIT_SHA" -F force=false
echo "main updated"

DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
ROLL_TAG_SHA=$(jq -n \
  --arg tag "$NEW_TAG" --arg msg "$NEW_TAG" --arg obj "$COMMIT_SHA" \
  --arg name "jianji" --arg email "jianji@users.noreply.github.com" --arg date "$DATE" \
  '{tag:$tag, message:$msg, object:$obj, type:"commit", tagger:{name:$name, email:$email, date:$date}}' \
  | gh api "repos/$REPO/git/tags" --input - --jq '.sha')
echo "tag object = $ROLL_TAG_SHA"
gh api -X POST "repos/$REPO/git/refs" -f ref="refs/tags/$NEW_TAG" -f sha="$ROLL_TAG_SHA"
echo "DONE rollback tag=$NEW_TAG commit=$COMMIT_SHA —— CI 将构建 v1.6.4.1 APK（覆盖安装免卸载）"
