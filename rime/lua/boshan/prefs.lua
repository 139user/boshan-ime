-- 博山:几个开关的默认值与记忆,外加快捷键。
--
-- 小篆提示(seal_hint)的取值顺序:用户自己切过的(boshan_prefs.txt) > 管理员在博山后台定的默认值(boshan_defaults.txt,
-- 安卓 App / 桌面端的更新脚本从后台拉下来写进用户目录) > 方案里的 boshan/seal_hint_default。
-- 简 / 繁 / 篆(simplification / traditionalization / seal 三选一)也记在 boshan_prefs.txt:librime 只在用户从方案选单切换时
-- 才保存选项,快捷键、安卓键盘按钮切的都不保存,所以统一由这里记 —— 监听选项变化,不管从哪儿切的都写下来。
--
-- 快捷键(在 key_binder 之前处理):
--   Ctrl+Shift+4  简 → 繁 → 篆 轮换
--   Ctrl+Shift+5  小篆提示 开 / 关
--   Ctrl+Shift+6  篆图模式 开 / 关(桌面端)
local util = require('boshan.util')

local M = {}

local SCRIPT = { 'simplification', 'traditionalization', 'seal' }
local REMEMBER = { seal_hint = true, simplification = true, traditionalization = true, seal = true }

local function prefs_path()
  return rime_api.get_user_data_dir() .. '/boshan_prefs.txt'
end

local function set_script(ctx, which)
  for _, name in ipairs(SCRIPT) do
    ctx:set_option(name, name == which)
  end
end

local function current_script(ctx)
  if ctx:get_option('seal') then return 'seal' end
  if ctx:get_option('traditionalization') then return 'traditionalization' end
  return 'simplification'
end

--- 管理员默认值,没有就用方案里的(返回 '1' / '0')
function M.default_hint(cfg)
  local d = util.read_kv(rime_api.get_user_data_dir() .. '/boshan_defaults.txt').seal_hint
  if d ~= nil then return util.truthy(d) and '1' or '0' end
  local b = cfg:get_bool('boshan/seal_hint_default')
  return (b == nil or b) and '1' or '0'
end

function M.init(env)
  local ctx = env.engine.context
  local cfg = env.engine.schema.config
  local prefs = util.read_kv(prefs_path())

  env.applying = true
  ctx:set_option('seal_hint', util.truthy(prefs.seal_hint or M.default_hint(cfg)))
  local script = prefs.script
  if script == 'simplification' or script == 'traditionalization' or script == 'seal' then
    set_script(ctx, script)
  end
  env.applying = false

  env.keys = {
    script = { KeyEvent('Control+Shift+4'), KeyEvent('Control+Shift+dollar') },
    hint = { KeyEvent('Control+Shift+5'), KeyEvent('Control+Shift+percent') },
    image = { KeyEvent('Control+Shift+6'), KeyEvent('Control+Shift+asciicircum') },
  }

  env.conn = ctx.option_update_notifier:connect(function(c, name)
    if env.applying or not REMEMBER[name] then return end
    -- 三选一切换时会连着来几次通知,只在"有一个是开着的"时记
    local p = util.read_kv(prefs_path())
    if name == 'seal_hint' then
      -- 切到跟默认值一样就算"没自己选",以后管理员改默认值还能跟着走
      local v = c:get_option('seal_hint') and '1' or '0'
      if v == M.default_hint(cfg) then p.seal_hint = nil else p.seal_hint = v end
    elseif c:get_option(name) then
      p.script = name
    else
      return
    end
    util.write_kv(prefs_path(), p, '博山输入法:用户自己切过的开关(lua/boshan/prefs.lua 写)')
  end)
end

function M.fini(env)
  if env.conn then env.conn:disconnect() end
end

local function hit(key, list)
  for _, k in ipairs(list) do
    if key:eq(k) then return true end
  end
  return false
end

function M.func(key, env)
  if key:release() then return 2 end
  local ctx = env.engine.context
  if hit(key, env.keys.script) then
    local cur = current_script(ctx)
    local nxt = cur == 'simplification' and 'traditionalization' or cur == 'traditionalization' and 'seal' or 'simplification'
    set_script(ctx, nxt)
    ctx:refresh_non_confirmed_composition()
    return 1
  elseif hit(key, env.keys.hint) then
    ctx:set_option('seal_hint', not ctx:get_option('seal_hint'))
    ctx:refresh_non_confirmed_composition()
    return 1
  elseif hit(key, env.keys.image) then
    ctx:set_option('seal_image', not ctx:get_option('seal_image'))
    return 1
  end
  return 2
end

return M
