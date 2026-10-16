// 安卓构建前的准备(在 build-rime.ts 之后跑):
//   android/ime/src/main/cpp/prebuilt/   fcitx5-android 预编译的 librime 静态库(按 lock.json 校验)
//   android/ime/src/main/assets/rime/    数据包(build/rime),default.custom.yaml 换成安卓那份,version.txt 给键盘判断要不要重新部署
//   android/ime/src/main/assets/fonts/   博山篆字体
// 都不入库。用法:bun tools/prepare-android.ts
import { existsSync } from 'node:fs'
import { cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { ROOT } from './fetch'
import lock from './lock.json'

const LIB = join(ROOT, 'android/ime/src/main')

async function prebuilt() {
  const p = lock.androidPrebuilt
  for (const f of p.files) {
    const dest = join(LIB, 'cpp/prebuilt', f.path)
    if (existsSync(dest)) {
      const got = new Bun.CryptoHasher('sha256').update(await readFile(dest)).digest('hex')
      if (got === f.sha256) continue
    }
    const res = await fetch(p.base + f.path)
    if (!res.ok) throw new Error(`${f.path}: HTTP ${res.status}`)
    const buf = Buffer.from(await res.arrayBuffer())
    const got = new Bun.CryptoHasher('sha256').update(buf).digest('hex')
    if (got !== f.sha256) throw new Error(`${f.path}: SHA-256 不符`)
    await mkdir(join(dest, '..'), { recursive: true })
    await writeFile(dest, buf)
    console.error(`下载 ${f.path}`)
  }
}

/** 把数据包(build/ 下的 rime 与 fonts)放进某个安卓模块的 assets;cowork 的准备脚本照同样的布局放 */
export async function installAssets(buildDir: string, assets: string) {
  const rime = join(assets, 'rime')
  await rm(rime, { recursive: true, force: true })
  await cp(join(buildDir, 'rime'), rime, { recursive: true })
  await cp(join(buildDir, 'android', 'default.custom.yaml'), join(rime, 'default.custom.yaml'))
  const ver = (await readFile(join(rime, 'boshan_version.txt'), 'utf8')).trim()
  await writeFile(join(rime, 'version.txt'), `${ver}-android\n`)
  await mkdir(join(assets, 'fonts'), { recursive: true })
  await cp(join(buildDir, 'fonts', 'BoshanSeal.otf'), join(assets, 'fonts', 'BoshanSeal.otf'))
  return ver
}

if (import.meta.main) {
  const build = join(ROOT, 'build')
  if (!existsSync(join(build, 'rime', 'boshan_version.txt')) || !existsSync(join(build, 'fonts', 'BoshanSeal.otf'))) {
    throw new Error('先跑 bun tools/build-rime.ts')
  }
  await prebuilt()
  const ver = await installAssets(build, join(LIB, 'assets'))
  console.error(`安卓资源已就位,数据包 ${ver}`)
}
