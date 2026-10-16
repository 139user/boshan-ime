// 外部来源的下载与校验:按 tools/lock.json 里锁定的地址和 SHA-256 下到 .cache/,解开后返回目录。
// 下载的东西当不可信数据:只读不执行,解到各自的空目录里。
import { createHash } from 'node:crypto'
import { existsSync } from 'node:fs'
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import lock from './lock.json'

export const ROOT = join(import.meta.dir, '..')
export const CACHE = join(ROOT, '.cache')

async function download(url: string, sha256: string, file: string): Promise<string> {
  if (existsSync(file)) {
    const got = createHash('sha256').update(await readFile(file)).digest('hex')
    if (got === sha256) return file
  }
  await mkdir(dirname(file), { recursive: true })
  console.error(`下载 ${url}`)
  const res = await fetch(url)
  if (!res.ok) throw new Error(`${url}: HTTP ${res.status}`)
  const buf = Buffer.from(await res.arrayBuffer())
  const got = createHash('sha256').update(buf).digest('hex')
  if (got !== sha256) throw new Error(`${url}: SHA-256 不符,期望 ${sha256},实得 ${got}`)
  await writeFile(file + '.part', buf)
  await rename(file + '.part', file)
  return file
}

async function run(cmd: string[], cwd?: string): Promise<void> {
  const p = Bun.spawn(cmd, { cwd, stdout: 'inherit', stderr: 'inherit' })
  if ((await p.exited) !== 0) throw new Error(`命令失败:${cmd.join(' ')}`)
}

/** 解 tar.gz 到 .cache/<name>/,返回包内根目录 */
async function untar(tgz: string, name: string, root: string, members?: string[]): Promise<string> {
  const dir = join(CACHE, name)
  const stamp = join(dir, '.ok')
  if (existsSync(stamp) && (await readFile(stamp, 'utf8')) === tgz) return join(dir, root)
  await rm(dir, { recursive: true, force: true })
  await mkdir(dir, { recursive: true })
  await run(['tar', 'xzf', tgz, '-C', dir, ...(members ?? []).map((m) => `${root}/${m}`)])
  await writeFile(stamp, tgz)
  return join(dir, root)
}

export async function rimeIce(): Promise<string> {
  const r = lock.rimeIce
  const tgz = await download(r.url, r.sha256, join(CACHE, 'dl', 'rime-ice.tgz'))
  return untar(tgz, 'rime-ice', r.root, r.members)
}

/** OpenCC 数据文件,返回 {相对路径: 本地路径} */
export async function opencc(): Promise<Record<string, string>> {
  const o = lock.opencc
  const out: Record<string, string> = {}
  for (const [path, sha] of Object.entries(o.files)) {
    out[path] = await download(o.base + path, sha, join(CACHE, 'opencc', path))
  }
  return out
}

export async function unihan(): Promise<string> {
  const u = lock.unihan
  const zip = await download(u.url, u.sha256, join(CACHE, 'dl', 'Unihan.zip'))
  const dir = join(CACHE, 'unihan')
  const file = join(dir, 'Unihan_Readings.txt')
  if (!existsSync(file)) {
    await mkdir(dir, { recursive: true })
    await run(['unzip', '-o', '-q', zip, 'Unihan_Readings.txt', '-d', dir])
  }
  return file
}

export async function kaiyuan(): Promise<string> {
  const k = lock.kaiyuan
  const tgz = await download(k.url, k.sha256, join(CACHE, 'dl', 'kaiyuan.tgz'))
  return untar(tgz, 'kaiyuan', k.root, ['glyphs', 'OFL.txt', 'THIRD_PARTY_NOTICES.md'])
}

/** 常用 3500 字(一级字表)*/
export async function common3500(): Promise<string[]> {
  const c = lock.common3500
  const file = await download(c.url, c.sha256, join(CACHE, 'dl', 'level-1.txt'))
  return [...(await readFile(file, 'utf8'))].filter((ch) => /\p{Script=Han}/u.test(ch))
}
