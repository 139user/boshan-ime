// 从银汉时空(sky.boshanlu.com)抓先秦儒道典籍原文,存成 corpus/<书>.json(简繁对照,按篇分段)。
// 用法:bun tools/fetch-corpus.ts [书的 slug …](不给就抓全部)
// 收录范围就是下面 BOOKS 这几部,别往里加别的书 —— 词库刻意只收这几部。
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'

export const BOOKS = [
  { slug: 'dao-de-jing', title: '道德经' },
  { slug: 'analects', title: '论语' },
  { slug: 'mengzi', title: '孟子' },
  { slug: 'xunzi', title: '荀子' },
  { slug: 'liji', title: '礼记' }, // 学记是礼记的一篇(liji/xue-ji),随礼记一起收
  { slug: 'book-of-changes', title: '周易' },
  { slug: 'guanzi', title: '管子' },
] as const

const API = 'https://sky.boshanlu.com/api/ctp/'
const OUT = join(import.meta.dir, '..', 'corpus')

type ApiNode = { ctp: string; titleHans: string; titleHant: string; kind: string; children?: ApiNode[] }
type ApiPage = {
  node: ApiNode
  children: ApiNode[]
  paragraphs?: { ordinal: number; textHans: string; textHant: string }[]
}
export type Chapter = { urn: string; title: string; titleHant: string; paragraphs: { hans: string; hant: string }[] }
export type Book = { slug: string; title: string; source: string; chapters: Chapter[] }

async function get(ctp: string): Promise<ApiPage> {
  for (let i = 0; ; i++) {
    const res = await fetch(API + ctp)
    if (res.ok) return (await res.json()) as ApiPage
    if (i >= 3) throw new Error(`${ctp}: HTTP ${res.status}`)
    await Bun.sleep(1000 * (i + 1))
  }
}

/** 深度优先走目录树,叶子节点带正文 */
async function walk(ctp: string, out: Chapter[]): Promise<void> {
  const page = await get(ctp)
  if (page.paragraphs?.length) {
    out.push({
      urn: page.node.ctp,
      title: page.node.titleHans,
      titleHant: page.node.titleHant,
      paragraphs: page.paragraphs
        .sort((a, b) => a.ordinal - b.ordinal)
        .map((p) => ({ hans: p.textHans, hant: p.textHant })),
    })
  }
  for (const c of page.children) {
    await Bun.sleep(150) // 别给人家站点添压力
    await walk(c.ctp, out)
  }
}

if (import.meta.main) {
  const want = process.argv.slice(2)
  await mkdir(OUT, { recursive: true })
  for (const b of BOOKS) {
    if (want.length && !want.includes(b.slug)) continue
    const chapters: Chapter[] = []
    await walk('ctp:' + b.slug, chapters)
    const book: Book = { slug: b.slug, title: b.title, source: `https://sky.boshanlu.com/read/${b.slug}`, chapters }
    const chars = chapters.reduce((n, c) => n + c.paragraphs.reduce((m, p) => m + p.hans.length, 0), 0)
    await writeFile(join(OUT, `${b.slug}.json`), JSON.stringify(book, null, 1) + '\n')
    console.log(`${b.title}: ${chapters.length} 篇, ${chars} 字`)
  }
}
