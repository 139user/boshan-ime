-- 博山:字 → 小篆(Unicode 18.0 篆书区块)的对照表,来自 boshan_seal.txt(tools/build-seal.ts 由 OpenCC 的小篆对照生成)。
-- 简体繁体都能查;一个简体字对应几个繁体字(发 → 發 / 髮)时最多两个篆字。第一次用时加载,各会话共用。
local util = require('boshan.util')

local M = {}
local MAP

function M.get()
  if MAP then return MAP end
  MAP = {}
  local p = util.data_path('boshan_seal.txt')
  if p then
    for line in io.lines(p) do
      local k, v = line:match('^([^#\t][^\t]*)\t(.+)$')
      if k then MAP[k] = v end
    end
  end
  return MAP
end

--- 一个字的第一个篆形,没有返回 nil
function M.first(ch)
  local v = M.get()[ch]
  return v and v:match(util.charpattern) or nil
end

--- 整段转小篆:有篆形的字换掉,没有的原样留着
function M.convert(s)
  local out = {}
  for ch in util.chars(s) do out[#out + 1] = M.first(ch) or ch end
  return table.concat(out)
end

return M
