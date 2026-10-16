#!/usr/bin/env bash
# 在笔记本(Windows,SSH,pwsh)上编安卓:把 android/(含 prepare-android.ts 准备好的 assets、prebuilt)与 version.txt 传到 C:\code\boshan-ime,
# 再跑 gradle。产物拷回 build/dist/。用法:tools/laptop-android.sh [gradle 任务,缺省 :app:assembleRelease]
# 先跑:bun tools/build-rime.ts && bun tools/prepare-android.ts
set -euo pipefail
cd "$(dirname "$0")/.."
HOST=${LAPTOP:-<笔记本>}
TASK=${1:-:app:assembleRelease}
ssh "$HOST" 'New-Item -ItemType Directory -Force C:\code\boshan-ime\signing | Out-Null'
# 正式签名:密钥在 VPS 的 ~/.config/boshan-ime/(release.jks + keystore-password),每次传过去;没有就用 debug 签名
KEY=~/.config/boshan-ime
if [[ -f $KEY/release.jks ]]; then
  scp -q "$KEY/release.jks" "$HOST:C:/code/boshan-ime/signing/release.jks"
  printf 'storeFile=../signing/release.jks\nkeyAlias=boshan\npassword=%s\n' "$(cat "$KEY/keystore-password")" \
    | ssh "$HOST" '$input | Set-Content -Encoding ascii C:\code\boshan-ime\android\keystore.properties'
fi
tar czf - --exclude='android/**/build' --exclude='android/.gradle' --exclude='android/**/.cxx' android version.txt \
  | ssh "$HOST" 'tar xzf - -C C:/code/boshan-ime'
ssh "$HOST" "\$ErrorActionPreference='Stop'
if (-not \$env:ANDROID_HOME) { \$env:ANDROID_HOME = \"\$env:LOCALAPPDATA\\Android\\Sdk\" }
if (-not \$env:NDK_HOME) { \$env:NDK_HOME = (Get-ChildItem \"\$env:ANDROID_HOME\\ndk\" -Directory | Sort-Object Name | Select-Object -Last 1).FullName }
Set-Content -Encoding ascii C:\\code\\boshan-ime\\android\\local.properties (\"sdk.dir=\" + (\$env:ANDROID_HOME -replace '\\\\','/'))
cd C:\\code\\boshan-ime\\android
.\\gradlew.bat --no-daemon --console=plain $TASK
if (\$LASTEXITCODE -ne 0) { exit \$LASTEXITCODE }"
mkdir -p build/dist
VER=$(cat version.txt)
scp "$HOST:C:/code/boshan-ime/android/app/build/outputs/apk/release/app-release.apk" "build/dist/boshan-$VER.apk" 2>/dev/null && ls -la "build/dist/boshan-$VER.apk" || true
