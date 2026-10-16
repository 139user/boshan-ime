// 博山后台的两张网页:首页(下载)与管理页(改默认设置、改口令)。纯 HTML + 一点内联脚本,不引前端框架。
import type { ClientConfig, Manifest } from './store'

const STYLE = `
  body { font-family: system-ui, "PingFang SC", "Microsoft YaHei", sans-serif; max-width: 720px; margin: 40px auto; padding: 0 20px; color: #222; }
  h1 { font-weight: 600; } h1 small { font-size: 16px; color: #888; font-weight: normal; margin-left: 8px; }
  .card { border: 1px solid #e5e5e5; border-radius: 10px; padding: 16px 20px; margin: 16px 0; }
  .muted { color: #888; font-size: 14px; }
  a.btn, button { display: inline-block; background: #7a4b2a; color: #fff; border: 0; border-radius: 6px; padding: 8px 16px;
    text-decoration: none; font-size: 15px; cursor: pointer; }
  button.ghost { background: #eee; color: #333; }
  input[type=password] { padding: 8px; font-size: 15px; border: 1px solid #ccc; border-radius: 6px; width: 240px; }
  label { display: flex; align-items: center; gap: 8px; margin: 10px 0; }
  .seal { font-family: "Boshan Seal"; font-size: 40px; }
  @font-face { font-family: "Boshan Seal"; src: url(/download/BoshanSeal.woff2) format("woff2"); }
`

function esc(s: string): string {
  return s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!)
}

function fmtSize(n?: number): string {
  return n ? `${(n / 1024 / 1024).toFixed(1)} MB` : ''
}

export function homePage(m: Manifest): string {
  const item = (key: keyof Manifest, name: string, note: string) => {
    const r = m[key]
    if (!r) return `<div class="card"><b>${name}</b> <span class="muted">还没有发布</span><div class="muted">${note}</div></div>`
    return `<div class="card"><b>${name}</b> <span class="muted">${esc(r.version)} ${fmtSize(r.size)}</span>
      <div class="muted">${note}</div><p><a class="btn" href="/download/${encodeURIComponent(r.file)}">下载</a></p></div>`
  }
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>博山输入法</title><style>${STYLE}</style></head><body>
<h1>博山输入法 <small>简 · 繁 · 篆</small></h1>
<p>拼音输入,一键切换简体、繁体、小篆;打字时候选旁显示常用字的小篆;先秦儒道典籍(道德经、论语、孟子、荀子、礼记、周易、管子)
只打每个字拼音的首字母,就能联想出整句。</p>
<p class="seal" title="博山">𽚃𾩚</p>
${item('android', '安卓', '自带键盘;装好后在系统"语言和输入法"里启用')}
${item('windows', 'Windows', '基于小狼毫(Rime);安装包会一并装好小狼毫、博山方案与小篆字体')}
${item('macos', 'macOS', '基于鼠须管(Rime);安装后在"系统设置 → 键盘 → 输入法"里添加')}
<p class="muted">基于 <a href="https://rime.im">Rime</a> 与 <a href="https://github.com/iDvel/rime-ice">雾凇拼音</a>;
小篆字形来自<a href="https://github.com/frankslin/kaiyuan-small-seal-font">开元小篆</a>(SIL OFL 1.1)。</p>
</body></html>`
}

export function loginPage(error?: string): string {
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>博山输入法 · 管理</title><style>${STYLE}</style></head><body>
<h1>博山输入法 <small>管理</small></h1>
<form method="post" action="/admin/login" class="card">
  <p><input type="password" name="password" placeholder="管理口令" autofocus> <button>登录</button></p>
  ${error ? `<p style="color:#c00">${esc(error)}</p>` : ''}
</form></body></html>`
}

export function adminPage(c: ClientConfig): string {
  const when = c.updatedAt ? new Date(c.updatedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) : '从没改过'
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>博山输入法 · 管理</title><style>${STYLE}</style></head><body>
<h1>博山输入法 <small>管理</small></h1>
<div class="card">
  <b>默认设置</b>
  <label><input type="checkbox" id="sealHint" ${c.sealHintDefault ? 'checked' : ''}> 打字时在候选旁显示常用字的小篆</label>
  <p class="muted">这是默认值:用户在输入法里自己开关过的,以用户的选择为准。安卓键盘每天、桌面端每 6 小时来取一次。上次修改:${when}</p>
  <button onclick="save()">保存</button> <span id="msg" class="muted"></span>
</div>
<div class="card">
  <b>修改管理口令</b>
  <p><input type="password" id="pw" placeholder="新口令(至少 8 位)"> <button onclick="changePw()">修改</button> <span id="pwmsg" class="muted"></span></p>
</div>
<form method="post" action="/admin/logout"><button class="ghost">退出</button></form>
<script>
async function post(url, body) {
  const r = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) })
  if (!r.ok) throw new Error((await r.json().catch(() => ({}))).error || r.statusText)
  return r.json()
}
async function save() {
  const msg = document.getElementById('msg')
  try { await post('/api/admin/config', { sealHintDefault: document.getElementById('sealHint').checked }); msg.textContent = '已保存' }
  catch (e) { msg.textContent = '保存失败:' + e.message }
}
async function changePw() {
  const msg = document.getElementById('pwmsg')
  try { await post('/api/admin/password', { password: document.getElementById('pw').value }); msg.textContent = '已修改' }
  catch (e) { msg.textContent = '修改失败:' + e.message }
}
</script></body></html>`
}
