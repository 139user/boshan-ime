// 字音表:字 → 读音(不带声调)。来源按可靠程度叠加:雾凇 8105 字表(带字频权重)→ 雾凇 41448 大字表 → Unihan 的
// kMandarin / kHanyuPinyin / kXHC1983 / kTGHZ2013。只用来求"拼音首字母",所以多收几个读音无妨(古籍里"说"读 yue、"乐"读 yao 都要能对上)。
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

export type Readings = Map<string, { syllables: Set<string>; primary?: string; common?: boolean }>

/** 带声调的拼音 → 不带声调的小写,ü 写成 v */
export function plain(s: string): string {
  return s
    .normalize('NFD')
    .replace(/\u0308/g, '\u0000') // ü 的两点先占位
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/u\u0000/g, 'v')
    .replace(/\u0000/g, '')
    .replace(/ê/g, 'e')
    .toLowerCase()
}

function addDict(map: Readings, file: string, weighted: boolean): void {
  let body = false
  const best = new Map<string, number>()
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    if (!body) {
      if (line === '...') body = true
      continue
    }
    if (!line || line.startsWith('#')) continue
    const [ch, code, w] = line.split('\t')
    if (!ch || !code || [...ch].length !== 1 || !/^[a-z]+$/.test(code)) continue
    let r = map.get(ch)
    if (!r) map.set(ch, (r = { syllables: new Set() }))
    r.syllables.add(code)
    if (weighted) {
      r.common = true // 在 8105 字表里
      const n = Number(w) || 0
      if (n > (best.get(ch) ?? -1)) {
        best.set(ch, n)
        r.primary = code
      }
    } else if (!r.primary) r.primary = code
  }
}

function addUnihan(map: Readings, file: string): void {
  const fields = new Set(['kMandarin', 'kHanyuPinyin', 'kXHC1983', 'kTGHZ2013'])
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    if (!line.startsWith('U+')) continue
    const [cp, field, value] = line.split('\t')
    if (!fields.has(field!)) continue
    const ch = String.fromCodePoint(parseInt(cp!.slice(2), 16))
    // kHanyuPinyin 之类是 "页码.位置:读音,读音 …",kMandarin 是 "读音 读音"
    const syls = value!
      .split(' ')
      .flatMap((x) => (x.includes(':') ? x.split(':')[1]!.split(',') : [x]))
      .map(plain)
      .filter((s) => /^[a-z]+$/.test(s))
    if (!syls.length) continue
    let r = map.get(ch)
    if (!r) map.set(ch, (r = { syllables: new Set() }))
    for (const s of syls) r.syllables.add(s)
    if (!r.primary && field === 'kMandarin') r.primary = syls[0]
  }
}

export function loadReadings(rimeIce: string, unihanReadings: string): Readings {
  const map: Readings = new Map()
  addDict(map, join(rimeIce, 'cn_dicts/8105.dict.yaml'), true)
  addDict(map, join(rimeIce, 'cn_dicts/41448.dict.yaml'), false)
  addUnihan(map, unihanReadings)
  return map
}

/** 一个字的首字母集合,主读音的字母排在最前 */
export function initials(map: Readings, ch: string): string {
  const r = map.get(ch)
  if (!r) return ''
  const first = r.primary ? r.primary[0]! : ''
  const rest = [...new Set([...r.syllables].map((s) => s[0]!))].filter((c) => c !== first).sort()
  return first + rest.join('')
}
