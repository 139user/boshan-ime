// 博山后台的存储:一个 JSON 配置文件 + 管理口令。都在数据目录(BOSHAN_DATA,缺省 /var/lib/boshan)里。
// - config.json:下发给各端的默认设置(现在只有"候选旁显示小篆"的默认值)
// - admin.json:管理口令的哈希与签 cookie 用的密钥;第一次启动时随机生成口令,明文写进 admin-initial-password.txt(只有本用户可读)
// - downloads/:安装包与数据包,manifest.json 记各端最新版本(发布脚本写)
import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto'
import { existsSync } from 'node:fs'
import { chmod, mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { join } from 'node:path'

export const DATA = process.env.BOSHAN_DATA ?? '/var/lib/boshan'
export const DOWNLOADS = join(DATA, 'downloads')

/** 下发给客户端的设置。加字段时各端要能忽略不认识的字段、缺字段时用自己的默认值 */
export type ClientConfig = {
  /** 候选旁是否默认显示小篆(用户自己切过的以用户为准) */
  sealHintDefault: boolean
  updatedAt: number
}

export type Release = { version: string; file: string; size?: number; sha256?: string; at?: number }
export type Manifest = Partial<Record<'android' | 'windows' | 'macos' | 'data', Release>>

const DEFAULT_CONFIG: ClientConfig = { sealHintDefault: true, updatedAt: 0 }

async function writeJson(file: string, value: unknown, mode = 0o600) {
  await writeFile(file + '.tmp', JSON.stringify(value, null, 2) + '\n', { mode })
  await rename(file + '.tmp', file)
}

async function readJson<T>(file: string, fallback: T): Promise<T> {
  try {
    return { ...fallback, ...JSON.parse(await readFile(file, 'utf8')) }
  } catch {
    return fallback
  }
}

let config: ClientConfig = DEFAULT_CONFIG
let admin: { hash: string; secret: string } = { hash: '', secret: '' }

export async function init(): Promise<void> {
  await mkdir(DOWNLOADS, { recursive: true })
  config = await readJson(join(DATA, 'config.json'), DEFAULT_CONFIG)
  const adminFile = join(DATA, 'admin.json')
  if (existsSync(adminFile)) {
    admin = JSON.parse(await readFile(adminFile, 'utf8'))
  } else {
    const password = process.env.BOSHAN_ADMIN_PASSWORD || randomBytes(9).toString('base64url')
    admin = { hash: await Bun.password.hash(password), secret: randomBytes(32).toString('hex') }
    await writeJson(adminFile, admin)
    if (!process.env.BOSHAN_ADMIN_PASSWORD) {
      const f = join(DATA, 'admin-initial-password.txt')
      await writeFile(f, password + '\n', { mode: 0o600 })
      await chmod(f, 0o600)
      console.log(`已生成管理口令,见 ${f}`)
    }
  }
}

export function getConfig(): ClientConfig {
  return config
}

export async function setConfig(patch: Partial<ClientConfig>): Promise<ClientConfig> {
  const next: ClientConfig = { ...config }
  if (typeof patch.sealHintDefault === 'boolean') next.sealHintDefault = patch.sealHintDefault
  next.updatedAt = Date.now()
  await writeJson(join(DATA, 'config.json'), next, 0o644)
  config = next
  return config
}

export async function getManifest(): Promise<Manifest> {
  return readJson<Manifest>(join(DOWNLOADS, 'manifest.json'), {})
}

export async function checkPassword(password: string): Promise<boolean> {
  return !!admin.hash && (await Bun.password.verify(password, admin.hash))
}

export async function changePassword(password: string): Promise<void> {
  admin = { ...admin, hash: await Bun.password.hash(password) }
  await writeJson(join(DATA, 'admin.json'), admin)
}

// 管理端会话:cookie 里放 过期时间.签名,不在服务端存会话
const SESSION_MS = 7 * 24 * 3600 * 1000

function sign(s: string): string {
  return createHmac('sha256', admin.secret).update(s).digest('base64url')
}

export function issueSession(): string {
  const exp = String(Date.now() + SESSION_MS)
  return `${exp}.${sign(exp)}`
}

export function verifySession(token: string | undefined): boolean {
  if (!token) return false
  const [exp, sig] = token.split('.')
  if (!exp || !sig || Number(exp) < Date.now()) return false
  const want = Buffer.from(sign(exp))
  const got = Buffer.from(sig)
  return want.length === got.length && timingSafeEqual(want, got)
}
