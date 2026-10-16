-- 博山输入法 lua 公用的小工具:找数据文件、读写 key=value 小文件、按 UTF-8 拆字、判断一串字母是不是正常全拼。
local M = {}

M.charpattern = utf8 and utf8.charpattern or '[%z\1-\127\194-\244][\128-\191]*'

--- 逐个字(UTF-8)迭代
function M.chars(s)
  return s:gmatch(M.charpattern)
end

--- 字数(按 UTF-8)
function M.len(s)
  if utf8 and utf8.len then return utf8.len(s) or #s end
  local n = 0
  for _ in M.chars(s) do n = n + 1 end
  return n
end

local function exists(p)
  local f = io.open(p, 'r')
  if f then f:close() return true end
  return false
end

--- 数据文件:先找用户目录(可覆盖),再找共享目录
function M.data_path(name)
  local dirs = { rime_api.get_user_data_dir(), rime_api.get_shared_data_dir() }
  for _, d in ipairs(dirs) do
    if d and d ~= '' then
      local p = d .. '/' .. name
      if exists(p) then return p end
    end
  end
  return nil
end

--- 读 key=value 文件(# 开头是注释),读不到返回空表
function M.read_kv(path)
  local t = {}
  local f = path and io.open(path, 'r')
  if not f then return t end
  for line in f:lines() do
    local k, v = line:match('^%s*([%w_]+)%s*=%s*(.-)%s*$')
    if k then t[k] = v end
  end
  f:close()
  return t
end

function M.write_kv(path, t, header)
  local f = io.open(path, 'w')
  if not f then return false end
  if header then f:write('# ', header, '\n') end
  local keys = {}
  for k in pairs(t) do keys[#keys + 1] = k end
  table.sort(keys)
  for _, k in ipairs(keys) do f:write(k, '=', tostring(t[k]), '\n') end
  f:close()
  return true
end

function M.truthy(v)
  return v == true or v == '1' or v == 'true' or v == 'yes' or v == 'on'
end

-- 全拼音节(雾凇 8105 字表里出现过的,去掉 m n ng hm hng 这几个叹词音)
local SYLLABLES = {}
for s in ([[a ai an ang ao ba bai ban bang bao bei ben beng bi bian biang biao bie bin bing bo bu ca cai can cang cao ce cei cen ceng
cha chai chan chang chao che chen cheng chi chong chou chu chua chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo
da dai dan dang dao de dei den deng di dia dian diao die ding diu dong dou du duan dui dun duo e ei en eng er fa fan fang fei fen
feng fiao fo fou fu ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo ha hai han hang hao he hei hen
heng hong hou hu hua huai huan huang hui hun huo ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun ka kai kan kang kao
ke kei ken keng kong kou ku kua kuai kuan kuang kui kun kuo la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu
lo long lou lu luan lun luo lv lve lue ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu na nai nan nang
nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu nuan nuo nv nve nue o ou pa pai pan pang pao pei pen peng pi
pian piao pie pin ping po pou pu qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun ran rang rao re ren reng ri rong rou
ru rua ruan rui run ruo sa sai san sang sao se sen seng sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan
shuang shui shun shuo si song sou su suan sui sun suo ta tai tan tang tao te tei teng ti tian tiao tie ting tong tou tu tuan tui
tun tuo wa wai wan wang wei wen weng wo wu xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun ya yan yang yao ye yi yin
ying yo yong you yu yuan yue yun za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi zhong zhou
zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo]]):gmatch('%a+') do
  SYLLABLES[s] = true
end

--- 整串能不能完全切成全拼音节(最长 6 个字母一个音节)
function M.is_full_pinyin(s)
  local n = #s
  if n == 0 then return false end
  local ok = { [0] = true }
  for i = 1, n do
    for l = 1, math.min(6, i) do
      if ok[i - l] and SYLLABLES[s:sub(i - l + 1, i)] then
        ok[i] = true
        break
      end
    end
  end
  return ok[n] == true
end

return M
