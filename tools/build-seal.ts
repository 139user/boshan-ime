// 简繁与小篆的转换数据,都出自 OpenCC(Apache-2.0,小篆对照取自 Unicode 18.0 的 SealSources.txt):
// - opencc/ 下的 OpenCC 字典(编成 .ocd2)和配置:s2t.json(简→繁)、s2seal.json(简→篆)、t2seal.json(繁→篆),
//   给方案里的 simplifier 用(繁体模式、小篆模式整句转换)。
// - boshan_seal.txt:字 → 小篆(简繁都能查,一个简体字对应几个繁体字时最多给两个篆字),给 lua/boshan/seal.lua 在候选旁边显示小篆。
import { existsSync, readFileSync } from 'node:fs'
import { copyFile, mkdir, rm, writeFile } from 'node:fs/promises'
import { basename, join } from 'node:path'

function readDict(file: string): Map<string, string[]> {
  const m = new Map<string, string[]>()
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    if (!line || line.startsWith('#')) continue
    const [k, v] = line.split('\t')
    if (k && v) m.set(k, v.split(' ').filter(Boolean))
  }
  return m
}

/** SealCharacters.txt(篆 → 今字)反过来:今字 → 篆(码位从小到大,即正篆在前;@reverse-prefer 指定的提到最前) */
function reverseSeal(file: string): Map<string, string[]> {
  const rev = new Map<string, string[]>()
  const prefer = new Map<string, string>()
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    const p = line.match(/^# @reverse-prefer: (\S+) (\S+)/)
    if (p) prefer.set(p[1]!, p[2]!)
    if (!line || line.startsWith('#')) continue
    const [seal, modern] = line.split('\t')
    for (const m of modern!.split(' ')) {
      const list = rev.get(m) ?? []
      list.push(seal!)
      rev.set(m, list)
    }
  }
  for (const list of rev.values()) list.sort((a, b) => a.codePointAt(0)! - b.codePointAt(0)!)
  for (const [m, s] of prefer) {
    const list = rev.get(m)
    if (list?.includes(s)) rev.set(m, [s, ...list.filter((x) => x !== s)])
  }
  return rev
}

/** 文本字典编成 .ocd2。较旧的 opencc_dict(Debian 13 是 1.1.9)不认 # 注释行,先去掉 */
async function ocd2(txt: string, out: string, tmp: string): Promise<void> {
  const clean = join(tmp, basename(txt) + '.clean')
  const body = readFileSync(txt, 'utf8').split('\n').filter((l) => l && !l.startsWith('#'))
  await writeFile(clean, body.join('\n') + '\n')
  await rm(out, { force: true })
  const p = Bun.spawn(['opencc_dict', '-i', clean, '-o', out, '-f', 'text', '-t', 'ocd2'], { stderr: 'pipe' })
  const err = await new Response(p.stderr).text()
  if ((await p.exited) !== 0 || err.trim() || !existsSync(out)) throw new Error(`opencc_dict 失败:${txt}\n${err}`)
}

const dict = (file: string) => ({ type: 'ocd2', file })
const S2T_GROUP = { type: 'group', dicts: [dict('STPhrases.ocd2'), dict('STCharacters.ocd2')] }

/** common:只做这些字(常用 3500 字)的小篆 —— 连同它们的繁体,别的字在篆模式下原样留着,字体也只收这些篆字。
 *  glyphs:字体里有字形的篆字码位,没有字形的不进对照表(否则显示成方框) */
export async function buildSeal(files: Record<string, string>, outDir: string, srcDir: string, common: string[], glyphs: Set<number>) {
  const oc = join(outDir, 'opencc')
  const tmp = join(srcDir, 'opencc-txt')
  await mkdir(oc, { recursive: true })
  await mkdir(tmp, { recursive: true })
  const st = readDict(files['data/dictionary/STCharacters.txt']!)
  const variants = readDict(files['data/dictionary/SealVariants.txt']!)
  const rev = reverseSeal(files['data/dictionary/SealCharacters.txt']!)

  // 允许的今字 = 常用字 + 它们的繁体;每个今字取一个篆形(先经 SealVariants 换成《说文》隶定字,再查反向表,正篆优先)
  const has = (seal?: string) => (seal && glyphs.has(seal.codePointAt(0)!) ? seal : undefined)
  const sealOf = (t: string) => has(rev.get(variants.get(t)?.[0] ?? t)?.[0]) ?? has(rev.get(t)?.[0])
  const toSeal = new Map<string, string>()
  for (const s of common) {
    for (const m of [s, ...(st.get(s) ?? [])]) {
      const seal = sealOf(m)
      if (seal && !toSeal.has(m)) toSeal.set(m, seal)
    }
  }

  // OpenCC 字典:简繁两份原样;今字 → 篆合成一份(已经桥接过隶定字,不再要 SealVariants 那一步)
  const sealTxt = join(tmp, 'BoshanSeal.txt')
  await writeFile(sealTxt, [...toSeal].map(([m, s]) => `${m}\t${s}`).join('\n') + '\n')
  await ocd2(files['data/dictionary/STCharacters.txt']!, join(oc, 'STCharacters.ocd2'), tmp)
  await ocd2(files['data/dictionary/STPhrases.txt']!, join(oc, 'STPhrases.ocd2'), tmp)
  await ocd2(sealTxt, join(oc, 'BoshanSeal.ocd2'), tmp)
  await copyFile(files['LICENSE']!, join(oc, 'LICENSE.OpenCC'))

  const seg = { type: 'mmseg', dict: dict('STPhrases.ocd2') }
  const configs: Record<string, object> = {
    's2t.json': { name: 'Simplified Chinese to Traditional Chinese', segmentation: seg, conversion_chain: [{ dict: S2T_GROUP }] },
    's2seal.json': {
      name: 'Simplified Chinese to Small Seal Script (常用 3500 字)',
      segmentation: seg,
      conversion_chain: [{ dict: S2T_GROUP }, { dict: dict('BoshanSeal.ocd2') }],
    },
    't2seal.json': {
      name: 'Traditional Chinese to Small Seal Script (常用 3500 字)',
      segmentation: { type: 'mmseg', dict: dict('BoshanSeal.ocd2') },
      conversion_chain: [{ dict: dict('BoshanSeal.ocd2') }],
    },
  }
  for (const [name, c] of Object.entries(configs)) await writeFile(join(oc, name), JSON.stringify(c, null, 2) + '\n')

  // 给 lua 的字 → 篆表:常用字给它所有繁体的篆形(最多两个,如 发 → 發 髮),繁体字给自己的
  const lines = ['# 字 → 小篆(Unicode 18.0 篆书区块,只含常用 3500 字及其繁体),tools/build-seal.ts 由 OpenCC 的小篆对照生成,勿手改']
  const done = new Set<string>()
  for (const s of common) {
    const forms = [...new Set((st.get(s) ?? [s]).map((t) => toSeal.get(t)).filter((x): x is string => !!x))].slice(0, 2)
    if (!forms.length && toSeal.has(s)) forms.push(toSeal.get(s)!)
    if (forms.length) {
      lines.push(`${s}\t${forms.join('')}`)
      done.add(s)
    }
  }
  for (const [m, seal] of toSeal) if (!done.has(m)) lines.push(`${m}\t${seal}`)
  await writeFile(join(outDir, 'boshan_seal.txt'), lines.join('\n') + '\n')
  const codepoints = [...new Set(toSeal.values())].map((x) => x.codePointAt(0)!).sort((a, b) => a - b)
  console.error(`小篆:常用 ${common.length} 字及其繁体共 ${toSeal.size} 个今字有篆形,用到 ${codepoints.length} 个篆字`)
  return { codepoints }
}
