#!/usr/bin/env bash
# 把安卓键盘库(android/ime)的源码同步进 cowork:client/src-tauri/gen/android/boshan-ime/。
# cowork 的安卓键盘 = 这个库 + cowork 自己的语音、跨设备(CoworkIme 继承 BoshanIme)。
# 只拷源码与构建脚本;assets(数据包、字体)与 cpp/prebuilt 由 cowork 的 prepare-tools.ts android 按 tools.lock.json 下载。
# 用法:tools/sync-cowork.sh [cowork 仓库目录,缺省 ~/code/cowork]
set -euo pipefail
cd "$(dirname "$0")/.."
COWORK=${1:-$HOME/code/cowork}
DEST=$COWORK/client/src-tauri/gen/android/boshan-ime
mkdir -p "$DEST"
rsync -a --delete \
  --exclude 'build/' --exclude '.cxx/' \
  --exclude 'src/main/assets/' --exclude 'src/main/cpp/prebuilt/' \
  --exclude 'VENDOR.md' \
  android/ime/ "$DEST/"
REV=$(git rev-parse --short HEAD)$(git diff --quiet -- android/ime || echo '+改动')
cat > "$DEST/VENDOR.md" <<MD
# 博山键盘库(同步件,别在这里改)

源头是博山输入法仓库(github.com/139user/boshan-ime,VPS 上 ~/code/boshan-ime)的 \`android/ime\`,
用那边的 \`tools/sync-cowork.sh\` 同步过来。当前对应提交:$REV。
要改键盘本身(拼音、候选、简繁篆、篆图……)去博山仓库改再同步;cowork 自己的语音、跨设备在 app 的 \`uk.nj7.cowork.ime\` 包里。
MD
echo "已同步到 $DEST($REV)"
