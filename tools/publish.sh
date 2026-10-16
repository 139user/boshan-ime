#!/usr/bin/env bash
# 把构建产物放到博山后台的下载目录,并更新 manifest.json(首页和 /api/config 据此给出最新版本)。
# 用法:tools/publish.sh <种类> <文件>    种类:data(数据包 zip)| android(apk)| windows(exe)| macos(pkg)| font(woff2 / otf,不进 manifest)
set -euo pipefail
KIND=$1; FILE=$2
DL=${BOSHAN_DOWNLOADS:-/var/lib/boshan/downloads}
NAME=$(basename "$FILE")
install -m 644 "$FILE" "$DL/$NAME"
[[ $KIND == font ]] && { echo "已放 $DL/$NAME"; exit 0; }
VER=$(echo "$NAME" | sed -E 's/^boshan(-rime|-setup)?-//; s/\.(zip|apk|exe|pkg)$//')
SHA=$(sha256sum "$FILE" | cut -c1-64)
SIZE=$(stat -c %s "$FILE")
M="$DL/manifest.json"
[[ -f $M ]] || echo '{}' > "$M"
python3 -E -P - "$M" "$KIND" "$VER" "$NAME" "$SIZE" "$SHA" <<'PY'
import json, sys, time
m, kind, ver, name, size, sha = sys.argv[1:]
d = json.load(open(m))
d[kind] = {"version": ver, "file": name, "size": int(size), "sha256": sha, "at": int(time.time() * 1000)}
open(m + ".tmp", "w").write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
import os; os.replace(m + ".tmp", m)
PY
echo "已发布 $KIND $VER:$NAME($SHA)"
