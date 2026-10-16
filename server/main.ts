// 博山输入法后台:给各端下发默认设置、管理员改设置、放安装包下载。不做用户登录。
//   GET  /                     首页(下载)
//   GET  /api/config           各端拉取的设置 { sealHintDefault, updatedAt } 与最新版本
//   GET  /download/<文件>      安装包、数据包、字体
//   GET  /admin                管理页(管理口令登录,cookie 会话)
//   POST /api/admin/config     { sealHintDefault }
//   POST /api/admin/password   { password }
// 环境变量:BOSHAN_PORT(缺省 18930)、BOSHAN_DATA(缺省 /var/lib/boshan)、BOSHAN_ADMIN_PASSWORD(第一次启动时用它作管理口令)
import { join, normalize } from 'node:path'
import { adminPage, homePage, loginPage } from './pages'
import * as store from './store'

const PORT = Number(process.env.BOSHAN_PORT ?? 18930)
const COOKIE = 'boshan_admin'

// 登录限速:每个地址 10 分钟内最多错 8 次
const failures = new Map<string, number[]>()
function limited(ip: string): boolean {
  const now = Date.now()
  const list = (failures.get(ip) ?? []).filter((t) => now - t < 600_000)
  failures.set(ip, list)
  return list.length >= 8
}

function json(body: unknown, status = 200, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'access-control-allow-origin': '*', ...extra },
  })
}

function html(body: string, status = 200, extra: Record<string, string> = {}): Response {
  return new Response(body, { status, headers: { 'content-type': 'text/html; charset=utf-8', ...extra } })
}

function cookieOf(req: Request, name: string): string | undefined {
  const raw = req.headers.get('cookie') ?? ''
  for (const part of raw.split(';')) {
    const [k, ...v] = part.trim().split('=')
    if (k === name) return v.join('=')
  }
  return undefined
}

function clientIp(req: Request, server: { requestIP(r: Request): { address: string } | null }): string {
  return req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() || server.requestIP(req)?.address || '?'
}

const sessionCookie = (v: string, maxAge: number) =>
  `${COOKIE}=${v}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${maxAge}`

await store.init()

Bun.serve({
  port: PORT,
  hostname: '127.0.0.1',
  async fetch(req, server) {
    const url = new URL(req.url)
    const path = url.pathname
    const authed = store.verifySession(cookieOf(req, COOKIE))

    if (req.method === 'GET' && path === '/') return html(homePage(await store.getManifest()))
    if (req.method === 'GET' && path === '/api/config') {
      return json({ ...store.getConfig(), releases: await store.getManifest() }, 200, { 'cache-control': 'no-cache' })
    }
    if (req.method === 'GET' && path.startsWith('/download/')) {
      const name = decodeURIComponent(path.slice('/download/'.length))
      const file = normalize(join(store.DOWNLOADS, name))
      if (!file.startsWith(store.DOWNLOADS + '/') || name.includes('/') || name === 'manifest.json') {
        return new Response('not found', { status: 404 })
      }
      const f = Bun.file(file)
      if (!(await f.exists())) return new Response('not found', { status: 404 })
      return new Response(f, { headers: { 'content-disposition': `attachment; filename*=UTF-8''${encodeURIComponent(name)}` } })
    }

    // ---- 管理 ----
    if (req.method === 'GET' && path === '/admin') return html(authed ? adminPage(store.getConfig()) : loginPage())
    if (req.method === 'POST' && path === '/admin/login') {
      const ip = clientIp(req, server)
      if (limited(ip)) return html(loginPage('错得太多了,10 分钟后再试'), 429)
      const form = await req.formData()
      if (await store.checkPassword(String(form.get('password') ?? ''))) {
        return new Response(null, { status: 303, headers: { location: '/admin', 'set-cookie': sessionCookie(store.issueSession(), 7 * 86400) } })
      }
      failures.get(ip)!.push(Date.now())
      return html(loginPage('口令不对'), 401)
    }
    if (req.method === 'POST' && path === '/admin/logout') {
      return new Response(null, { status: 303, headers: { location: '/admin', 'set-cookie': sessionCookie('', 0) } })
    }
    if (path.startsWith('/api/admin/')) {
      if (!authed) return json({ error: '没有登录' }, 401)
      if (req.method !== 'POST') return json({ error: '只收 POST' }, 405)
      // 同源检查,防跨站提交(cookie 已是 SameSite=Strict,这里再挡一道)
      const origin = req.headers.get('origin')
      if (origin && new URL(origin).host !== (req.headers.get('x-forwarded-host') ?? url.host)) {
        return json({ error: '来源不对' }, 403)
      }
      const body = (await req.json().catch(() => ({}))) as Record<string, unknown>
      if (path === '/api/admin/config') {
        if (typeof body.sealHintDefault !== 'boolean') return json({ error: 'sealHintDefault 要是 true / false' }, 400)
        return json(await store.setConfig({ sealHintDefault: body.sealHintDefault }))
      }
      if (path === '/api/admin/password') {
        const pw = String(body.password ?? '')
        if (pw.length < 8) return json({ error: '口令至少 8 位' }, 400)
        await store.changePassword(pw)
        return json({ ok: true })
      }
    }
    return new Response('not found', { status: 404 })
  },
})

console.log(`博山后台在 127.0.0.1:${PORT},数据目录 ${store.DATA}`)
