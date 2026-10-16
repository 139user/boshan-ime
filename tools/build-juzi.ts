// 由 corpus/ 生成两份词库数据:
// 1. boshan_juzi.txt —— 句库,给 lua/boshan/juzi.lua 做"打每个字的拼音首字母,联想出整句"。
//    每行一句(按 。？！ 断开,去掉引号书名号),带出处、简体、繁体、逐字首字母。格式见 writeJuzi 的注释。
// 2. cn_dicts/boshan_classics.dict.yaml —— 把句中的小句(逗号分号之间,3–12 字)作为词条挂进拼音主词库,
//    全拼打 xueershixizhi 也能直接出"学而时习之"。不写编码,由 Rime 按字表自动注音。
import { readdirSync, readFileSync } from 'node:fs'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { BOOKS, type Book } from './fetch-corpus'
import { initials, type Readings } from './readings'

const HAN = /\p{Script=Han}/u
const CLAUSE_PUNCT = new Set(['，', '；', '：', '、'])
const DROP = /[「」『』《》〈〉]/g
const SENTENCE_END = /[。？！□○●─－\n]/

const CN_NUM = '〇一二三四五六七八九'
function cnNumber(n: number): string {
  if (n < 10) return CN_NUM[n]!
  if (n < 20) return '十' + (n % 10 ? CN_NUM[n % 10] : '')
  return CN_NUM[Math.floor(n / 10)] + '十' + (n % 10 ? CN_NUM[n % 10] : '')
}

/** p1:每个汉字一个字母(主读音的首字母,查不到读音是 ?);alts:多音字 "汉字序号:全部首字母",逗号隔开 */
export type Sentence = { src: number; hans: string; hant: string; p1: string; alts: string }

/** 一段原文切成句子:简繁逐字对齐(语料保证了等长),在同样的位置下刀 */
function splitParagraph(hans: string, hant: string): { hans: string; hant: string }[] {
  const a = [...hans]
  const b = [...hant]
  const out: { hans: string; hant: string }[] = []
  let cs: string[] = []
  let ct: string[] = []
  const flush = () => {
    // 去头尾标点、合并连续标点
    while (cs.length && !HAN.test(cs[0]!)) (cs.shift(), ct.shift())
    while (cs.length && !HAN.test(cs[cs.length - 1]!)) (cs.pop(), ct.pop())
    const s: string[] = []
    const t: string[] = []
    for (let i = 0; i < cs.length; i++) {
      const isHan = HAN.test(cs[i]!)
      if (!isHan && s.length && !HAN.test(s[s.length - 1]!)) continue
      s.push(isHan ? cs[i]! : cs[i]!)
      t.push(isHan ? ct[i]! : cs[i]!)
    }
    if (s.filter((c) => HAN.test(c)).length >= 2) out.push({ hans: s.join(''), hant: t.join('') })
    cs = []
    ct = []
  }
  for (let i = 0; i < a.length; i++) {
    const c = a[i]!
    if (DROP.test(c)) {
      DROP.lastIndex = 0
      continue
    }
    DROP.lastIndex = 0
    if (SENTENCE_END.test(c)) {
      flush()
      continue
    }
    if (HAN.test(c)) {
      cs.push(c)
      ct.push(b[i] && HAN.test(b[i]!) ? b[i]! : c)
    } else if (CLAUSE_PUNCT.has(c)) {
      cs.push(c)
      ct.push(c)
    } else flush() // 别的符号一律当断句
  }
  flush()
  return out
}

export function buildSentences(readings: Readings, corpusDir: string) {
  const srcs: string[] = []
  const sentences: Sentence[] = []
  const missing = new Map<string, number>()
  const order = new Map<string, number>(BOOKS.map((b, i) => [b.slug, i]))
  const files = readdirSync(corpusDir)
    .filter((f) => f.endsWith('.json'))
    .sort((x, y) => (order.get(x.slice(0, -5)) ?? 99) - (order.get(y.slice(0, -5)) ?? 99))
  for (const f of files) {
    const book = JSON.parse(readFileSync(join(corpusDir, f), 'utf8')) as Book
    for (const ch of book.chapters) {
      const single = book.chapters.length === 1 // 道德经整部一篇,按段落当章
      ch.paragraphs.forEach((p, pi) => {
        const title = single
          ? `第${cnNumber(pi + 1)}章`
          : ch.title.replace(/[^\p{Script=Han}]/gu, '')
        if (single || pi === 0) srcs.push(`${book.title}·${title}`)
        const src = srcs.length - 1
        for (const s of splitParagraph(p.hans, p.hant)) {
          let p1 = ''
          const alts: string[] = []
          for (const c of s.hans) {
            if (!HAN.test(c)) continue
            const l = initials(readings, c)
            if (!l) missing.set(c, (missing.get(c) ?? 0) + 1)
            if (l.length > 1) alts.push(`${p1.length}:${l}`)
            p1 += l ? l[0] : '?'
          }
          if (p1.length > 1000) continue // lua 那边按 汉字序号 < 1024 编码
          sentences.push({ src, hans: s.hans, hant: s.hant === s.hans ? '' : s.hant, p1, alts: alts.join(',') })
        }
      })
    }
  }
  return { srcs, sentences, missing }
}

/** boshan_juzi.txt,三种行:
 *    @<出处号>\t<书·篇>          出处表(号从 0 起)
 *    <出处号>\t<简体句>\t<繁体句,与简体相同时留空>\t<主读音首字母串>\t<多音字>
 *        句号按出现次序从 1 起;句中只有汉字和 ，；：、;首字母串每个汉字一个字母(? = 查不到读音);
 *        多音字一项 "汉字序号(从 0 起):这个字所有读音的首字母",逗号隔开
 *    =<三个字母>\t<句号*1024+汉字序号>,…   索引:每个小句开头处的前三个首字母(多音字展开)→ 从哪儿开始
 *  索引在这里算好,lua 那边加载时只按行存字符串,不用逐字扫(手机上逐字扫一遍要好几秒) */
async function writeJuzi(file: string, srcs: string[], sentences: Sentence[]) {
  const lines = ['# 博山句库:先秦儒道典籍原文,tools/build-juzi.ts 生成,勿手改', '# 来源:银汉时空 https://sky.boshanlu.com']
  srcs.forEach((s, i) => lines.push(`@${i}\t${s}`))
  const index = new Map<string, number[]>()
  sentences.forEach((s, i) => {
    const sid = i + 1
    lines.push(`${s.src}\t${s.hans}\t${s.hant}\t${s.p1}\t${s.alts}`)
    const alts = new Map(s.alts ? s.alts.split(',').map((x) => [Number(x.split(':')[0]), x.split(':')[1]!] as const) : [])
    const at = (k: number) => alts.get(k) ?? s.p1[k]!
    // 小句开头:句首和每个标点之后
    let idx = 0
    let start = true
    for (const ch of s.hans) {
      if (!HAN.test(ch)) {
        start = true
        continue
      }
      if (start && s.p1.length - idx >= 3) {
        for (const a of at(idx)) for (const b of at(idx + 1)) for (const c of at(idx + 2)) {
          const key = a + b + c
          if (key.includes('?')) continue
          let list = index.get(key)
          if (!list) index.set(key, (list = []))
          list.push(sid * 1024 + idx)
        }
      }
      start = false
      idx++
    }
  })
  for (const [k, list] of [...index].sort((x, y) => (x[0] < y[0] ? -1 : 1))) lines.push(`=${k}\t${list.join(',')}`)
  await writeFile(file, lines.join('\n') + '\n')
}

/** 典籍里出现、8105 字表里没有的字,带上全部读音单独成一个字表,挂进主词库(不挂整个 41448 大字表,手机上部署太慢) */
async function writeCharsDict(file: string, readings: Readings, sentences: Sentence[]) {
  const rare = new Map<string, number>()
  for (const s of sentences) {
    for (const c of s.hans) {
      const r = readings.get(c)
      if (HAN.test(c) && r && !r.common) rare.set(c, (rare.get(c) ?? 0) + 1)
    }
  }
  const head = [
    '# Rime dictionary',
    '# encoding: utf-8',
    '# 博山输入法:先秦儒道典籍里出现、《通用规范汉字表》以外的字,读音取雾凇 41448 大字表与 Unihan。tools/build-juzi.ts 生成,勿手改。',
    '---',
    'name: boshan_chars',
    'version: "1"',
    'sort: by_weight',
    '...',
  ]
  const lines: string[] = []
  for (const [c, n] of rare) for (const syl of readings.get(c)!.syllables) lines.push(`${c}\t${syl}\t${n}`)
  await writeFile(file, head.join('\n') + '\n' + lines.join('\n') + '\n')
  return rare.size
}

async function writeClassicsDict(file: string, readings: Readings, sentences: Sentence[]) {
  const seen = new Set<string>()
  const words: string[] = []
  for (const s of sentences) {
    for (const clause of s.hans.split(/[，；：、]/)) {
      const n = [...clause].length
      if (n < 3 || n > 12 || seen.has(clause)) continue
      if ([...clause].some((c) => !readings.has(c))) continue // 有查不到读音的字,Rime 注不了音
      seen.add(clause)
      words.push(clause)
    }
  }
  const head = [
    '# Rime dictionary',
    '# encoding: utf-8',
    '# 博山输入法:先秦儒道典籍里的小句(道德经、论语、孟子、荀子、礼记、周易、管子),tools/build-juzi.ts 生成,勿手改。',
    '# 不写编码,Rime 按字表自动注音(多音字取权重占比 5% 以上的读音)。',
    '---',
    'name: boshan_classics',
    'version: "1"',
    'sort: original',
    '...',
  ]
  await writeFile(file, head.join('\n') + '\n' + words.map((w) => `${w}\t\t500`).join('\n') + '\n')
  return words.length
}

export async function buildJuzi(readings: Readings, corpusDir: string, outDir: string) {
  const { srcs, sentences, missing } = buildSentences(readings, corpusDir)
  await mkdir(join(outDir, 'cn_dicts'), { recursive: true })
  await writeJuzi(join(outDir, 'boshan_juzi.txt'), srcs, sentences)
  const words = await writeClassicsDict(join(outDir, 'cn_dicts', 'boshan_classics.dict.yaml'), readings, sentences)
  const rare = await writeCharsDict(join(outDir, 'cn_dicts', 'boshan_chars.dict.yaml'), readings, sentences)
  const top = [...missing].sort((a, b) => b[1] - a[1]).slice(0, 20)
  console.error(`句库:${srcs.length} 篇 ${sentences.length} 句;主词库加小句 ${words} 条、生僻字 ${rare} 个;无读音的字 ${missing.size} 个 ${top.map((x) => x.join('')).join(' ')}`)
}
