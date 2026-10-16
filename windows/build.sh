#!/usr/bin/env bash
# 打 Windows 安装包(在 VPS 上用 makensis 编,不用 Windows):build/dist/boshan-setup-<版本>.exe
# 先跑 bun tools/build-rime.ts。用法:windows/build.sh
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
VER=$(cat version.txt)
STAGE=$ROOT/build/windows-stage
rm -rf "$STAGE"; mkdir -p "$STAGE" build/dist .cache/dl
[[ -f build/rime/boshan_version.txt && -f build/fonts/BoshanSeal.otf ]] || { echo "先跑 bun tools/build-rime.ts" >&2; exit 1; }

# 小狼毫官方安装包(按 lock.json 校验)
read -r WURL WSHA WVER < <(python3 -E -P -c "import json;w=json.load(open('tools/lock.json'))['weasel'];print(w['url'],w['sha256'],w['version'])")
W=.cache/dl/weasel-$WVER.exe
if [[ ! -f $W ]] || [[ $(sha256sum "$W" | cut -c1-64) != "$WSHA" ]]; then curl -fsSL -o "$W" "$WURL"; fi
[[ $(sha256sum "$W" | cut -c1-64) == "$WSHA" ]] || { echo "小狼毫安装包校验不对" >&2; exit 1; }
cp "$W" "$STAGE/weasel-installer.exe"

cp -r build/rime "$STAGE/rime"
cp build/fonts/BoshanSeal.otf branding/boshan.ico windows/weasel.custom.yaml "$STAGE/"
# PowerShell 5.1 认带 BOM 的 UTF-8;WSH 认带 BOM 的 UTF-16;NSIS 的许可页认带 BOM 的 UTF-8
printf '\xef\xbb\xbf' | cat - windows/boshan-helper.ps1 > "$STAGE/boshan-helper.ps1"
iconv -f UTF-8 -t UTF-16LE windows/boshan-helper.vbs | { printf '\xff\xfe'; cat; } > "$STAGE/boshan-helper.vbs"
printf '\xef\xbb\xbf' | cat - windows/LICENSE.txt > "$STAGE/LICENSE.txt"

OUT=$ROOT/build/dist/boshan-setup-$VER.exe
makensis -V2 -DVERSION="$VER" -DSTAGE="$STAGE" -DOUTFILE="$OUT" -DWEASEL_VERSION="$WVER" windows/boshan.nsi
ls -la "$OUT"
