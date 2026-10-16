-- 博山:先秦儒道典籍首字母整句联想。
-- 打每个字拼音的首字母(zh ch sh 只打 z c s),从小句开头算起,就能联想出原文:
--   hwzcmxmcsjmcz → 后王之成名：刑名从商，爵名从周   〔荀子·正名〕
-- 只打前几个字母时,给出到小句末尾的片段,头一条另给到整句末尾的补全。
-- 句库是 boshan_juzi.txt(tools/build-juzi.ts 生成,格式见那里),第一次用时读进来,各会话共用。
--   索引以每个小句开头处的前 3 个首字母(多音字展开)为键,值是 句号 * 1024 + 汉字序号;查的时候按键取出,再逐字核对后面的字母。
-- 繁体 / 篆模式下直接给原文繁体(篆模式再逐字换成篆字),不经 OpenCC。
local util = require('boshan.util')
local sealmap = require('boshan.sealmap')

local M = {}

local PUNCT = { ['，'] = true, ['；'] = true, ['：'] = true, ['、'] = true }

-- 句库:每句一行原样存成字符串,查到时再拆;索引也是原样的 "句号*1024+序号,…" 字符串,查到哪个键才拆哪个
local LINES, BUCKETS, SRCNAME
local loaded = false

local function load()
  if loaded then return end
  loaded = true
  LINES, BUCKETS, SRCNAME = {}, {}, {}
  local path = util.data_path('boshan_juzi.txt')
  if not path then return end
  local n = 0
  for line in io.lines(path) do
    local first = line:byte(1)
    if first == 61 then -- '='
      BUCKETS[line:sub(2, 4)] = line:sub(6)
    elseif first == 64 then -- '@'
      local i, name = line:match('^@(%d+)\t(.*)$')
      if i then SRCNAME[tonumber(i)] = name end
    elseif first and first ~= 35 then -- 不是 '#'
      n = n + 1
      LINES[n] = line
    end
  end
end

--- 拆开一句:出处号、简体、繁体(与简体相同时为 nil)、主读音首字母串、多音字表
local function sentence(sid)
  local src, hans, hant, p1, alts = LINES[sid]:match('^(%d+)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t?([^\t]*)$')
  local t
  if alts and alts ~= '' then
    t = {}
    for i, l in alts:gmatch('(%d+):(%a+)') do t[tonumber(i)] = l end
  end
  return { src = tonumber(src), hans = hans, hant = hant ~= '' and hant or nil, p1 = p1, alts = t }
end

--- 第 sid 句从第 c 个汉字起,能不能对上 code 的每个字母
local function match_at(st, c, code, L)
  local p1 = st.p1
  if #p1 - c < L then return false end
  local alts = st.alts
  for k = 1, L do
    local want = code:byte(k)
    if p1:byte(c + k) ~= want then
      local a = alts and alts[c + k - 1]
      if not (a and a:find(string.char(want), 1, true)) then return false end
    end
  end
  return true
end

--- 算片段的字位置(按字计,1 起):start = 第 c 个汉字;stop1 = 第 c+L-1 个汉字所在小句的末尾;stop2 = 句末。
--- exact = 打的首字母正好到小句末尾
local function locate(hans, c, L)
  local pos, idx = 0, 0
  local start, last, stop1
  for ch in util.chars(hans) do
    pos = pos + 1
    if PUNCT[ch] then
      if last and not stop1 then stop1 = pos - 1 end
    else
      if idx == c then start = pos end
      if idx == c + L - 1 then last = pos end
      idx = idx + 1
    end
  end
  stop1 = stop1 or pos
  return start, stop1, pos, stop1 == last
end

local function slice(s, i, j)
  local out, pos = {}, 0
  for ch in util.chars(s) do
    pos = pos + 1
    if pos > j then break end
    if pos >= i then out[#out + 1] = ch end
  end
  return table.concat(out)
end

function M.init(env)
  local cfg = env.engine.schema.config
  env.min_len = cfg:get_int('boshan/juzi/min_length') or 4
  env.full_min = cfg:get_int('boshan/juzi/full_pinyin_min_length') or 6
  env.max_results = cfg:get_int('boshan/juzi/max_results') or 6
  env.max_chars = cfg:get_int('boshan/juzi/max_chars') or 60
end

function M.func(input, seg, env)
  if not seg:has_tag('abc') then return end
  local code = input:gsub("'", '')
  if not code:match('^[a-z]+$') then return end
  local L = #code
  if L < env.min_len or (L < env.full_min and util.is_full_pinyin(code)) then return end
  load()
  local list = BUCKETS[code:sub(1, 3)]
  if not list then return end

  local hits, cache = {}, {}
  for num in list:gmatch('%d+') do
    local enc = tonumber(num)
    local sid, c = math.floor(enc / 1024), enc % 1024
    local st = cache[sid]
    if not st then
      st = sentence(sid)
      cache[sid] = st
    end
    if match_at(st, c, code, L) then
      local start, stop1, stop2, exact = locate(st.hans, c, L)
      hits[#hits + 1] = { sid = sid, st = st, start = start, stop1 = stop1, stop2 = stop2, exact = exact, extra = stop1 - start + 1 }
      if #hits >= 300 then break end
    end
  end
  if #hits == 0 then return end
  -- 正好打到小句末尾的在前;再按多出来的字少的在前;再按书的次序(句号小的在前)
  table.sort(hits, function(a, b)
    if a.exact ~= b.exact then return a.exact end
    if a.extra ~= b.extra then return a.extra < b.extra end
    return a.sid < b.sid
  end)

  local ctx = env.engine.context
  local seal = ctx:get_option('seal')
  local trad = seal or ctx:get_option('traditionalization')
  local function text_of(st, i, j)
    local s = slice(trad and st.hant or st.hans, i, j)
    return seal and sealmap.convert(s) or s
  end
  local function emit(text, st, tail)
    local cand = Candidate('juzi', seg.start, seg._end, text, '〔' .. (SRCNAME[st.src] or '') .. (tail and '·全句' or '') .. '〕')
    cand.quality = 1000
    cand.preedit = input
    yield(cand)
  end

  local seen, n = {}, 0
  for i, h in ipairs(hits) do
    local key = slice(h.st.hans, h.start, h.stop1)
    if not seen[key] then
      seen[key] = true
      emit(text_of(h.st, h.start, h.stop1), h.st, false)
      n = n + 1
      -- 头一条:另给到句末的补全(太长的不给)
      if i == 1 and h.stop2 > h.stop1 and h.stop2 - h.start + 1 <= env.max_chars then
        local whole = slice(h.st.hans, h.start, h.stop2)
        if not seen[whole] then
          seen[whole] = true
          emit(text_of(h.st, h.start, h.stop2), h.st, true)
        end
      end
      if n >= env.max_results then break end
    end
  end
end

return M
