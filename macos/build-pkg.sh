#!/bin/bash
# 打 macOS 安装包(只能在 macOS 上跑,GitHub Actions 的 .github/workflows/macos.yml 调):
#   官方鼠须管(拆出组件包,原样)+ 博山组件(数据、字体、篆图脚本、定时取设置)→ build/dist/boshan-<版本>.pkg
# 用法:macos/build-pkg.sh <博山数据包 zip> <鼠须管官方 pkg>
set -euo pipefail
cd "$(dirname "$0")/.."
ZIP=$1; SQPKG=$2
VER=$(cat version.txt)
W=build/macos; rm -rf "$W"; mkdir -p "$W" build/dist

# 博山组件的内容
P="$W/payload"
APP="$P/Library/Application Support/BoshanIME"
mkdir -p "$APP" "$P/Library/Fonts" "$P/Library/LaunchAgents"
unzip -q "$ZIP" -d "$W/data"
cp -R "$W/data/rime" "$APP/rime"
cp "$W/data/fonts/BoshanSeal.otf" "$P/Library/Fonts/"
cp macos/squirrel.custom.yaml macos/seal_image.js macos/update-defaults.sh macos/uninstall.sh "$APP/"
cp macos/com.boshanlu.ime.defaults.plist "$P/Library/LaunchAgents/"
chmod 755 macos/scripts/postinstall
pkgbuild --root "$P" --scripts macos/scripts --identifier com.boshanlu.ime --version "$VER" --install-location / "$W/boshan-component.pkg"

# 鼠须管官方安装包:本身是组件包就原样用;是产品包(带 Distribution)就拆出里面的组件包(产品包不能套产品包)
pkgutil --expand "$SQPKG" "$W/sq"
if [ -f "$W/sq/Distribution" ]; then
  n=0
  for c in "$W"/sq/*.pkg; do pkgutil --flatten "$c" "$W/squirrel-$n.pkg"; n=$((n+1)); done
else
  cp "$SQPKG" "$W/squirrel-0.pkg"
fi

# 合成产品包:先鼠须管,后博山
cd "$W"
productbuild --synthesize $(for f in squirrel-*.pkg; do printf -- '--package %s ' "$f"; done) --package boshan-component.pkg dist.xml
# 标题
sed -i '' 's|<installer-gui-script minSpecVersion="[0-9]*">|&<title>博山输入法</title>|' dist.xml
productbuild --distribution dist.xml --package-path . "../dist/boshan-$VER.pkg"
ls -la "../dist/boshan-$VER.pkg"
