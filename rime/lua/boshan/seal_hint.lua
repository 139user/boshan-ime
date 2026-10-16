-- 博山:候选旁显示小篆(开关 seal_hint)。默认只给单字显示,boshan/seal_hint_max_length 可以放宽到词;
-- 词里只要有一个字没有篆形就不显示,免得半篆半楷看不明白。篆模式下候选本身就是篆字,这里不再加。
local util = require('boshan.util')
local sealmap = require('boshan.sealmap')

local M = {}

function M.init(env)
  env.max_len = env.engine.schema.config:get_int('boshan/seal_hint_max_length') or 1
end

local function hint_of(text, max_len)
  local n = util.len(text)
  if n == 1 then
    local v = sealmap.get()[text]
    if not v then return nil end
    -- 两个篆形(一简对多繁)中间空一格
    local forms = {}
    for ch in util.chars(v) do forms[#forms + 1] = ch end
    return table.concat(forms, ' ')
  end
  if n < 2 or n > max_len then return nil end
  local parts = {}
  for ch in util.chars(text) do
    local f = sealmap.first(ch)
    if not f then return nil end
    parts[#parts + 1] = f
  end
  return table.concat(parts)
end

function M.func(input, env)
  local ctx = env.engine.context
  if not ctx:get_option('seal_hint') or ctx:get_option('seal') then
    for cand in input:iter() do yield(cand) end
    return
  end
  for cand in input:iter() do
    local hint = cand.type ~= 'juzi' and hint_of(cand.text, env.max_len)
    if hint then
      local cm = cand.comment
      yield(ShadowCandidate(cand, cand.type, cand.text, (cm ~= '' and (cm .. ' ') or '') .. hint))
    else
      yield(cand)
    end
  end
end

return M
