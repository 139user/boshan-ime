// 组装博山输入法的 Rime 数据包(三端共用):
//   build/rime/   雾凇拼音的文件 + rime/ 下博山自己的方案与 lua + 生成的 OpenCC 数据、句库、典籍小句词库、小篆对照
//   build/fonts/BoshanSeal.otf   博山篆字体(开元小篆重描,只收常用 3500 字的篆字;第一次构建要十来分钟,之后用缓存)
//   build/android/default.custom.yaml   安卓键盘用的补丁(装进安卓 assets 时换掉桌面那份)
//   build/dist/boshan-rime-<版本>.zip   上面几样打包,桌面安装包与安卓(含 cowork)都用它
// 用法:bun tools/build-rime.ts [--no-font] [--no-zip]
import { existsSync } from 'node:fs'
import { cp, mkdir, readdir, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { buildJuzi } from './build-juzi'
import { buildSeal } from './build-seal'
import { CACHE, common3500, kaiyuan, opencc, rimeIce, ROOT, unihan } from './fetch'
import lock from './lock.json'
import { loadReadings } from './readings'

const BUILD = join(ROOT, 'build')
const OUT = join(BUILD, 'rime')
const FONTS = join(BUILD, 'fonts')
const args = new Set(process.argv.slice(2))

async function run(cmd: string[], cwd = ROOT) {
  const p = Bun.spawn(cmd, { cwd, stdout: 'inherit', stderr: 'inherit' })
  if ((await p.exited) !== 0) throw new Error(`命令失败:${cmd.join(' ')}`)
}

async function gitRev(): Promise<string> {
  const p = Bun.spawn(['git', 'rev-parse', '--short', 'HEAD'], { cwd: ROOT, stdout: 'pipe', stderr: 'ignore' })
  const out = (await new Response(p.stdout).text()).trim()
  return (await p.exited) === 0 && out ? out : 'dev'
}

export async function version(): Promise<string> {
  const pkg = await Bun.file(join(ROOT, 'version.txt')).text()
  return `${pkg.trim()}-${await gitRev()}`
}

/** 博山篆字体,只收 codepoints 里的篆字(常用 3500 字用到的) */
async function font(codepoints: number[]): Promise<string> {
  const otf = join(FONTS, 'BoshanSeal.otf')
  const list = codepoints.map((c) => c.toString(16).toUpperCase()).join('\n') + '\n'
  const key = new Bun.CryptoHasher('sha256').update(lock.kaiyuan.sha256 + list).digest('hex').slice(0, 12)
  const cached = join(CACHE, 'font', `BoshanSeal-${key}.otf`)
  if (!existsSync(cached)) {
    const ky = await kaiyuan()
    const retraced = join(CACHE, 'font', 'retraced')
    const listFile = join(CACHE, 'font', `codepoints-${key}.txt`)
    await mkdir(join(CACHE, 'font'), { recursive: true })
    await writeFile(listFile, list)
    await run(['bash', join(ROOT, 'tools/retrace-glyphs.sh'), ky, retraced, listFile])
    await run(['python3', '-E', '-P', join(ROOT, 'tools/build-font.py'), retraced, cached + '.part', '--subset', listFile])
    await run(['mv', cached + '.part', cached])
    await cp(join(ky, 'OFL.txt'), join(CACHE, 'font', 'OFL.txt'))
  }
  await mkdir(FONTS, { recursive: true })
  await cp(cached, otf)
  await cp(join(CACHE, 'font', 'OFL.txt'), join(FONTS, 'OFL.txt'))
  return otf
}

async function main() {
  await rm(OUT, { recursive: true, force: true })
  await mkdir(OUT, { recursive: true })

  // 1. 雾凇拼音
  const ice = await rimeIce()
  for (const m of lock.rimeIce.members) {
    await cp(join(ice, m), join(OUT, m === 'LICENSE' ? 'LICENSE.rime-ice' : m), { recursive: true })
  }
  // 2. 博山自己的方案、补丁、lua(安卓专用的补丁放 build/android/,安卓那边装的时候换上)
  for (const e of await readdir(join(ROOT, 'rime'))) {
    if (e === 'android') continue
    await cp(join(ROOT, 'rime', e), join(OUT, e), { recursive: true })
  }
  await rm(join(BUILD, 'android'), { recursive: true, force: true })
  await cp(join(ROOT, 'rime', 'android'), join(BUILD, 'android'), { recursive: true })
  // 3. 简繁篆转换数据
  const glyphs = new Set((await readdir(join(await kaiyuan(), 'glyphs'))).map((f) => parseInt(f.slice(1, -4), 16)))
  const { codepoints } = await buildSeal(await opencc(), OUT, join(BUILD, 'tmp'), await common3500(), glyphs)
  // 4. 句库与典籍小句词库
  const readings = loadReadings(ice, await unihan())
  await buildJuzi(readings, join(ROOT, 'corpus'), OUT)
  // 5. 版本号:安卓键盘靠它判断要不要重新部署
  const ver = await version()
  await writeFile(join(OUT, 'boshan_version.txt'), ver + '\n')
  console.error(`数据包版本 ${ver}`)

  if (!args.has('--no-font')) await font(codepoints)
  if (!args.has('--no-zip')) {
    const dist = join(BUILD, 'dist')
    await mkdir(dist, { recursive: true })
    const zip = join(dist, `boshan-rime-${ver}.zip`)
    await rm(zip, { force: true })
    await run(['zip', '-q', '-r', '-9', zip, 'rime', 'android', ...(existsSync(FONTS) ? ['fonts'] : [])], BUILD)
    console.error(`打包 ${zip}`)
  }
}

if (import.meta.main) await main()
