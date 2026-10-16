-- 博山:篆图模式(开关 seal_image,Ctrl+Shift+6;桌面端用)。开着时选字(空格或数字键)不上屏文字,
-- 而是把选中的候选转成小篆,交给博山助手画成图片、放进剪贴板并粘贴到当前程序:
--   - Windows:写请求文件 boshan_image_request.txt(用户目录),常驻的博山助手(boshan-helper.ps1)盯着这个文件;
--     不从这里起进程 —— 小狼毫是窗口程序,os.execute 会闪一下黑框。
--   - macOS:直接起 osascript 跑共享目录里的 boshan/seal_image.js。
-- 请求文件两行:第一行要画的字(已转成篆字),第二行原来的字。安卓键盘有自己的"篆图"按钮,不走这里。
local util = require('boshan.util')
local sealmap = require('boshan.sealmap')

local M = {}

local IS_WINDOWS = package.config:sub(1, 1) == '\\'

function M.init(env)
  env.page_size = env.engine.schema.config:get_int('menu/page_size') or 5
end

local function request(text, original)
  local dir = rime_api.get_user_data_dir()
  local path = dir .. '/boshan_image_request.txt'
  local f = io.open(path, 'w')
  if not f then return end
  f:write(text, '\n', original, '\n')
  f:close()
  if not IS_WINDOWS then
    local js = util.data_path('boshan/seal_image.js')
    if js then
      os.execute(string.format("/usr/bin/osascript -l JavaScript '%s' '%s' >/dev/null 2>&1 &", js, path))
    end
  end
end

function M.func(key, env)
  local ctx = env.engine.context
  if key:release() or key:ctrl() or key:alt() or not ctx:get_option('seal_image') or not ctx:has_menu() then
    return 2
  end
  local kc = key.keycode
  local seg = ctx.composition:back()
  if not seg then return 2 end
  local cand
  if kc == 0x20 then
    cand = seg:get_selected_candidate()
  elseif kc >= 0x31 and kc <= 0x39 then
    local page_start = math.floor(seg.selected_index / env.page_size) * env.page_size
    cand = seg:get_candidate_at(page_start + kc - 0x31)
  else
    return 2
  end
  if not cand then return 2 end
  local text = cand.text
  local seal = ctx:get_option('seal') and text or sealmap.convert(text)
  request(seal, text)
  ctx:clear()
  return 1
end

return M
