#!/usr/bin/env bash
# 开元小篆的 SVG 是从刻本扫描逐点描的,点很密(编出来 20MB)。先栅格化到 256px、轻微模糊去毛边,再用 potrace 粗一点重描,体积降到三分之一左右。
# 用法:tools/retrace-glyphs.sh <开元小篆仓库目录> <输出目录> [码位清单]   依赖 rsvg-convert、ImageMagick、potrace
#   码位清单:每行一个十六进制码位,只重描这些(已经重描过的跳过)
set -euo pipefail
SRC=$1/glyphs; OVR=$1/data/overrides; OUT=$2; LIST=${3:-}
mkdir -p "$OUT"
one() {
  local svg=$1 name tmp
  name=$(basename "$svg" .svg)
  [[ -s "$OUT/$name.svg" ]] && return 0
  [[ -f "$OVR/$name.svg" ]] && svg="$OVR/$name.svg"
  tmp=$(mktemp -d)
  rsvg-convert -w 256 -h 256 -b white "$svg" -o "$tmp/a.png"
  convert "$tmp/a.png" -colorspace Gray -blur 0x0.8 -threshold 50% "$tmp/a.pbm"
  potrace -s -t 4 -a 1.2 -O 1.5 -u 4 "$tmp/a.pbm" -o "$OUT/$name.svg"
  rm -rf "$tmp"
}
export -f one; export OUT OVR
if [[ -n "$LIST" ]]; then
  while read -r cp; do [[ -n "$cp" && -f "$SRC/u$cp.svg" ]] && echo "$SRC/u$cp.svg"; done < "$LIST"
else
  ls "$SRC"/u*.svg
fi | xargs -P "$(nproc)" -I{} bash -c 'one {}'
echo "$(ls "$OUT" | wc -l) 个字形" >&2
