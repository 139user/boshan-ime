-- 博山:整句联想的位置。打的若本身就是一串正常全拼(比如 zhege),整句联想多半不是想要的,
-- 挪到第 boshan/juzi/full_pinyin_index 个候选;只打首字母时它本来就在最前,不动。
local util = require('boshan.util')

local M = {}

function M.init(env)
  env.index = env.engine.schema.config:get_int('boshan/juzi/full_pinyin_index') or 3
end

function M.func(input, env)
  local code = env.engine.context.input:gsub("'", '')
  if not util.is_full_pinyin(code) then
    for cand in input:iter() do yield(cand) end
    return
  end
  local held, n = {}, 0
  for cand in input:iter() do
    if cand.type == 'juzi' and n < env.index - 1 then
      held[#held + 1] = cand
    else
      yield(cand)
      n = n + 1
      if n == env.index - 1 and #held > 0 then
        for _, h in ipairs(held) do yield(h) end
        held = {}
      end
    end
  end
  for _, h in ipairs(held) do yield(h) end
end

return M
