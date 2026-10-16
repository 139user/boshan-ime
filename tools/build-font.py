#!/usr/bin/env python3
"""把开元小篆(Kaiyuan Small Seal,SIL OFL 1.1)的逐字 SVG 编成博山篆字体 BoshanSeal.otf。

字形挂在 Unicode 18.0 篆书区块(U+3D000..U+3FC3F)的码位上;另加一个空格字形。
CFF 轮廓(三次曲线,比 TTF 小),纯 fontTools。按 OFL 改了名字(开元小篆没有保留字体名,改名是为了跟上游正式版区分)。

用法:python3 -E -P tools/build-font.py <开元小篆仓库目录> <输出 .otf> [--subset 码位清单文件]
  --subset:只收清单里的码位(每行一个十六进制码位),用来出精简版。
"""

import argparse
import re
import sys
from pathlib import Path

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.recordingPen import RecordingPen
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.pens.transformPen import TransformPen
from fontTools.svgLib.path import parse_path

UPM, ASC, DESC = 1000, 880, -120
FIRST, LAST = 0x3D000, 0x3FC3F
FAMILY = "Boshan Seal"
FAMILY_ZH = "博山篆"
VERSION = "1.000"
COPYRIGHT = ("Copyright (c) 2026 Kaiyuan Small Seal Font Contributors "
             "(https://github.com/frankslin/kaiyuan-small-seal-font). "
             "Modified for Boshan IME: renamed, CFF outlines.")
LICENSE = ("This Font Software is licensed under the SIL Open Font License, Version 1.1. "
           "This license is available with a FAQ at: https://openfontlicense.org")


def outline(svg: Path) -> RecordingPen:
    """SVG 的 path 转成字体轮廓(y 朝上)。两种来源:
    - 开元小篆原件:viewBox 0 -880 1000 1000,y 朝下,直接翻转;
    - tools/retrace-glyphs.sh 重描的:potrace 输出,256px 画布,<g transform="translate(0,256) scale(s,-s)">,
      路径本身已经 y 朝上,按 1000/256 放大、下移 120(基线)。"""
    text = svg.read_text(encoding="utf-8")
    rec = RecordingPen()
    m = re.search(r'translate\(0(?:\.0+)?,([\d.]+)\)\s*scale\(([\d.]+),-[\d.]+\)', text)
    if "potrace" in text and m:
        size, s = float(m.group(1)), float(m.group(2))
        k = s * UPM / size
        matrix = (k, 0, 0, k, 0, DESC)
    else:
        matrix = (1, 0, 0, -1, 0, 0)
    for d in re.findall(r'<path[^>]*\sd="([^"]*)"', text, re.S):
        parse_path(d.replace("\n", " "), TransformPen(rec, matrix))
    return rec


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("kaiyuan")
    ap.add_argument("out")
    ap.add_argument("--subset")
    a = ap.parse_args()

    # 可以直接给重描过的字形目录(里面就是 uXXXXX.svg),也可以给开元小篆仓库(用其 glyphs/ 与 data/overrides/)
    root = Path(a.kaiyuan)
    glyph_dir = root / "glyphs" if (root / "glyphs").is_dir() else root
    overrides = root / "data" / "overrides"
    keep = None
    if a.subset:
        keep = {int(x, 16) for x in Path(a.subset).read_text().split() if x}

    names = [".notdef", "space"]
    cmap = {0x20: "space", 0x3000: "space"}
    charstrings = {}
    metrics = {".notdef": (UPM, 0), "space": (UPM // 2, 0)}

    def blank(width):
        pen = T2CharStringPen(width, None)
        return pen.getCharString()

    charstrings[".notdef"] = blank(UPM)
    charstrings["space"] = blank(UPM // 2)

    for svg in sorted(glyph_dir.glob("u*.svg")):
        cp = int(svg.stem[1:], 16)
        if not FIRST <= cp <= LAST or (keep is not None and cp not in keep):
            continue
        src = overrides / svg.name
        rec = outline(src if src.exists() else svg)
        pen = T2CharStringPen(UPM, None)
        rec.replay(pen)
        bp = BoundsPen(None)
        rec.replay(bp)
        name = "u%05X" % cp
        names.append(name)
        cmap[cp] = name
        charstrings[name] = pen.getCharString()
        metrics[name] = (UPM, int(bp.bounds[0]) if bp.bounds else 0)

    fb = FontBuilder(UPM, isTTF=False)
    fb.setupGlyphOrder(names)
    fb.setupCharacterMap(cmap)
    ps_name = "BoshanSeal-Regular"
    fb.setupCFF(ps_name, {"FullName": "Boshan Seal Regular", "FamilyName": FAMILY, "Weight": "Regular"},
                charstrings, {})
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=ASC, descent=DESC)
    fb.setupNameTable({
        "copyright": COPYRIGHT,
        "familyName": FAMILY,
        "styleName": "Regular",
        "uniqueFontIdentifier": f"{ps_name};{VERSION}",
        "fullName": "Boshan Seal Regular",
        "version": f"Version {VERSION}",
        "psName": ps_name,
        "licenseDescription": LICENSE,
        "licenseInfoURL": "https://openfontlicense.org",
        "description": "小篆 Unicode 18.0 Seal block, from Kaiyuan Small Seal (traced from public-domain 說文解字 scans).",
    })
    # 码位在第三平面,OS/2 的 Unicode 范围位里没有对应的位,置 0 即可;CodePage 标中文
    fb.setupOS2(sTypoAscender=ASC, sTypoDescender=DESC, sTypoLineGap=0, usWinAscent=ASC, usWinDescent=-DESC,
                achVendID="NONE", fsType=0, ulCodePageRange1=(1 << 18) | (1 << 20))
    fb.setupPost()
    # 中文名字直接写 Windows 平台记录(fontTools 的 setupNameTable 不认 zh-CN 标签)
    name = fb.font["name"]
    for lang in (0x0804, 0x0404, 0x0C04):
        for nid in (1, 4):
            name.setName(FAMILY_ZH, nid, 3, 1, lang)
    fb.save(a.out)
    print(f"{a.out}: {len(names) - 2} 个篆字", file=sys.stderr)


if __name__ == "__main__":
    main()
