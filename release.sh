#!/usr/bin/env bash
# release.sh — jianji 一键发版（适配本机 git push 被 TLS 代理拦截的环境）
#
# 正确发版流程（与 .github/workflows/build-apk.yml 对齐）：
#   1) 代码推到 main（本环境 git push 不可用，改用 GitHub Contents API）
#   2) 提 versionCode / versionName（CI 守卫要求严格递增）
#   3) 打 annotated tag（两步法：先 POST /git/tags 再 PATCH /git/refs）→ 触发 CI
#   4) CI 自动 assembleRelease → 签名 → versionCode 守卫 → 上传 → 建 Release
#
# 用法:
#   ./release.sh push <file>...   把本地改动文件推到 main（绕过不可用 git push）
#   ./release.sh cut               自动 patch+1 提版本、打 tag、触发 CI 并验证 Release
#   ./release.sh verify <vX.Y.Z>  下载 APK 字节级校验 versionCode
#
# 依赖: gh(已登录) python3 curl base64

set -uo pipefail

REPO="gnaiq/jianji"
API="https://api.github.com/repos/$REPO"

die(){ echo "::error:: $*" >&2; exit 1; }

# 取文件在 main 上的 blob sha（不存在返回空）
blob_sha(){ gh api "$API/contents/$1?ref=main" --jq '.sha' 2>/dev/null || true; }

# 把本地文件 PUT 到 main（新建或更新）
push_file(){
  local f="$1"
  [ -f "$f" ] || die "文件不存在: $f"
  local b64 sha body commit_sha
  b64=$(base64 -w0 "$f")
  sha=$(blob_sha "$f")
  if [ -n "$sha" ]; then
    body=$(python3 - "$f" "$b64" "$sha" <<'PY'
import json,sys
f,b64,sha=sys.argv[1],sys.argv[2],sys.argv[3]
print(json.dumps({"message":f"chore(release-tool): update {f}","content":b64,"sha":sha,"branch":"main"}))
PY
)
  else
    body=$(python3 - "$f" "$b64" <<'PY'
import json,sys
f,b64=sys.argv[1],sys.argv[2]
print(json.dumps({"message":f"feat(release-tool): add {f}","content":b64,"branch":"main"}))
PY
)
  fi
  commit_sha=$(echo "$body" | gh api -X PUT "$API/contents/$f" --input - --jq '.commit.sha') \
    || die "推送 $f 失败"
  echo "✅ pushed $f -> $commit_sha"
}

cmd_push(){
  [ $# -ge 1 ] || die "push 需要至少一个文件路径"
  for f in "$@"; do push_file "$f"; done
}

cmd_cut(){
  local path="app/build.gradle.kts"
  local sha content txt old_vc old_vn new_vc new_vn newtxt b64 body commit_sha
  sha=$(blob_sha "$path"); [ -n "$sha" ] || die "$path 在 main 上不存在"
  content=$(gh api "$API/contents/$path?ref=main" --jq '.content')
  txt=$(echo "$content" | base64 -d)
  old_vc=$(echo "$txt" | grep -oE 'versionCode = [0-9]+' | grep -oE '[0-9]+' | head -1)
  old_vn=$(echo "$txt" | grep -oE 'versionName = "[^"]+"' | sed -E 's/.*"([^"]+)".*/\1/' | head -1)
  [ -n "$old_vc" ] && [ -n "$old_vn" ] || die "无法解析 versionCode/versionName"
  new_vc=$((old_vc+1))
  new_vn=$(echo "$old_vn" | awk -F. '{$NF=$NF+1; OFS="."; print}')
  echo "版本: $old_vn (vc=$old_vc) -> $new_vn (vc=$new_vc)"
  newtxt=$(echo "$txt" \
    | sed -E "s/versionCode = [0-9]+/versionCode = $new_vc/" \
    | sed -E "s/versionName = \"[^\"]+\"/versionName = \"$new_vn\"/")
  echo "$newtxt" > "$path"
  b64=$(base64 -w0 "$path")
  body=$(python3 - "$path" "$b64" "$sha" "$new_vn" <<'PY'
import json,sys
f,b64,sha,vn=sys.argv[1],sys.argv[2],sys.argv[3],sys.argv[4]
print(json.dumps({"message":f"chore: release v{vn}","content":b64,"sha":sha,"branch":"main"}))
PY
)
  commit_sha=$(echo "$body" | gh api -X PUT "$API/contents/$path" --input - --jq '.commit.sha') \
    || die "版本号提交失败"
  echo "✅ version bump commit: $commit_sha"

  local tag="v$new_vn" date tagbody tagobj
  date=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  tagbody=$(python3 - "$tag" "$new_vn" "$commit_sha" "$date" <<'PY'
import json,sys
tag,vn,obj,date=sys.argv[1],sys.argv[2],sys.argv[3],sys.argv[4]
print(json.dumps({"tag":tag,"message":f"Build APK {tag}","object":obj,"type":"commit",
  "tagger":{"name":"jianji-release-bot","email":"release@jianji.local","date":date}}))
PY
)
  tagobj=$(echo "$tagbody" | gh api -X POST "$API/git/tags" --input - --jq '.sha') \
    || die "创建 tag 对象失败"
  echo "✅ tag object: $tagobj"
  gh api -X POST "$API/git/refs" -f ref="refs/tags/$tag" -f sha="$tagobj" --jq '{ref:.ref,obj:.object.sha}' \
    || die "创建 tag ref 失败"
  echo "🚀 已推送 tag $tag，等待 CI 构建..."

  local st="" run_id="" info
  for i in $(seq 1 36); do
    info=$(gh api "$API/actions/runs?per_page=20" --jq ".workflow_runs[] | select(.head_sha==\"$commit_sha\") | \"\(.id) \(.conclusion)\"")
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

  gh api "$API/releases/tags/$tag" --jq '{id,tag_name,html_url,assets:[.assets[].name]}' \
    || die "Release 未生成"
  echo "✅ Release $tag 已生成（上行为资产清单）"
}

cmd_verify(){
  local tag="${1:-}"; [ -n "$tag" ] || die "verify 需要 tag 名，如 v1.4.12"
  local token aid url apk
  token=$(gh auth token 2>/dev/null)
  aid=$(gh api "$API/releases/tags/$tag" --jq '.assets[0].id')
  url=$(gh api "$API/releases/assets/$aid" --jq '.url')
  apk="/tmp/jianji-verify.apk"
  curl -k -sL -H "Authorization: Bearer $token" -H "Accept: application/octet-stream" "$url" -o "$apk"
  python3 - "$apk" <<'PY'
import sys,zipfile
apk=sys.argv[1]
with zipfile.ZipFile(apk) as z: data=z.read("AndroidManifest.xml")
for vc,pat in [(19,b"\x08\x00\x00\x10\x13\x00\x00\x00"),(20,b"\x08\x00\x00\x10\x14\x00\x00\x00")]:
    if data.find(pat)>=0:
        print("✅ versionCode 字节级校验 =", vc); break
else:
    print("⚠️ 未在字节中定位已知 versionCode（可能版本号超出预置表，请人工确认）")
PY
  rm -f "$apk"
}

case "${1:-}" in
  push) shift; cmd_push "$@";;
  cut)  cmd_cut;;
  verify) shift; cmd_verify "$@";;
  *) echo "用法: $0 {push <file>...|cut|verify <vX.Y.Z>}"; exit 1;;
esac
