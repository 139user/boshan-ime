# 方案与数据

三端共用一个 Rime 数据包(`bun tools/build-rime.ts` → `build/rime` + `build/fonts` → `build/dist/boshan-rime-<版本>.zip`)。

## 来源(都在 `tools/lock.json` 里按地址与 SHA-256 锁定,`tools/fetch.ts` 下到 `.cache/`)

- 雾凇拼音 rime-ice(GPL-3.0)固定提交:方案底子、8105 字表、base / ext / others 词表(不带腾讯词向量,手机上部署太久)、英文、部件拆字、lua。
  41448 大字表只解出来求字音,不进数据包。
- OpenCC(Apache-2.0)固定提交:`STCharacters` / `STPhrases`(简→繁)、`SealCharacters` / `SealVariants`(Unicode 18.0 的小篆对照,
  出自 UCD 的 `SealSources.txt`,带"正篆优先"的 `@reverse-prefer`)。
- Unicode 18.0 Unihan:只用读音兜底。
- 《通用规范汉字表》一级字表 3500 字:小篆只做这些字及其繁体。
- 开元小篆(SIL OFL 1.1)逐字 SVG:博山篆字体的来源。
- 典籍原文:`corpus/*.json`(入库),`bun tools/fetch-corpus.ts` 从 sky.boshanlu.com 的 `/api/ctp/` 抓,简繁逐字对齐。
  **范围只限** 道德经、论语、孟子、荀子、礼记(学记是礼记的一篇)、周易、管子,约 46.6 万字。

## 方案(`rime/boshan.schema.yaml`)

雾凇的 rime_ice.schema.yaml 改出来的(改的地方标了「博山」),拼写运算直接 `__include` 雾凇的。在它上面加:

- 开关:`simplification / traditionalization / seal` 三选一(简 / 繁 / 篆)、`seal_hint`(小篆提示)、`seal_image`(篆图,桌面)。
- `simplifier@traditionalize`(s2t.json)、`simplifier@seal`(s2seal.json,候选换成小篆,旁注今字)。整句联想的候选不经这两个,它自己给原文繁体。
- `lua_translator@*boshan.juzi` + `lua_filter@*boshan.juzi_filter`:整句联想。
- `lua_filter@*boshan.seal_hint`:候选旁的小篆(默认只给单字,`boshan/seal_hint_max_length` 可放宽到词;词里有字没篆形就不显示)。
- `lua_processor@*boshan.prefs`:开关记忆与快捷键;`lua_processor@*boshan.seal_image`:篆图模式选字时写请求文件。
- 主词库 `boshan.dict.yaml` = 雾凇的字表与词表 + `boshan_chars`(典籍里 8105 以外的生僻字,带全部读音)+ `boshan_classics`(典籍小句,3–12 字,
  不写编码由 Rime 按字表自动注音,全拼能直接打出)。

### 开关记忆(`lua/boshan/prefs.lua`)

librime 只在用户从方案选单切换时保存选项,快捷键、安卓按钮切的都不存;而 `reset` 又会盖过保存的值。所以博山自己记:
- `boshan_prefs.txt`(用户目录):用户自己切过的 `script`(简繁篆)与 `seal_hint`。小篆提示切到跟默认值一样就清掉这一项,算"没自己选",以后管理员改默认值还跟着走。
- `boshan_defaults.txt`(用户目录):管理员在后台定的默认值,各端从后台取来写入(安卓 App、Windows 博山助手、macOS LaunchAgent;cowork 由键盘经 `ime.config` 取)。
- 都没有时用方案里的 `boshan/seal_hint_default`(显示)。

### 整句联想(`lua/boshan/juzi.lua`,数据 `boshan_juzi.txt`)

- 句库按 。？！ 断句,去掉引号书名号,句中只留汉字与 ，；：、。每句一行:出处号、简体、繁体(与简体相同留空)、主读音首字母串(每字一个字母)、多音字表。
- 索引在构建时算好写进文件:每个小句开头处前 3 个首字母(多音字展开)→ `句号*1024+汉字序号` 列表。lua 加载时只按行存字符串(约 90ms、8MB),
  查的时候按前 3 个字母取出候选位置,再逐字核对(主读音字母或多音字表里的任一字母)。
- 片段:从小句开头到第 L 个汉字,再延到所在小句末尾;正好打到小句末尾的排前,其次多出来的字少的,再按书的次序。头一条另给到句末的补全(60 字以内)。
- 至少 4 个字母才联想;输入本身是一串正常全拼(`zhege`)时至少 6 个字母,且排到第 3 个候选(`juzi_filter`)。
- 繁体 / 篆模式下给原文繁体(篆模式再逐字换成篆字),不经 OpenCC。
- 字音:雾凇 8105(带字频,主读音取权重最高的)+ 41448 + Unihan(kMandarin、kHanyuPinyin、kXHC1983、kTGHZ2013),只取首字母,所以多收读音无妨
  (说 yue、乐 yao 之类古读都能对上)。zh / ch / sh 只算 z / c / s。

### 小篆(`tools/build-seal.ts`)

- 今字 → 篆:先经 `SealVariants`(现代字 → 《说文》隶定字,如 年 → 秊),再查 `SealCharacters` 的反表(一字多篆取码位最小的正篆,`@reverse-prefer` 例外)。
- 只做常用 3500 字及其繁体,且只留字体里有字形的(开元小篆缺 41 个),共 2,890 个今字、2,853 个篆字。合成一份 `BoshanSeal.ocd2`,
  `s2seal.json` = s2t 再接它,`t2seal.json` = 只用它。lua 用的对照表是 `boshan_seal.txt`(简体字给它所有繁体的篆形,最多两个,如 发 → 發 髮)。
- 《说文》里本来没有的字(于、朋、们……)没有篆形,篆模式下原样留着。

### 字体(`tools/retrace-glyphs.sh` + `tools/build-font.py`)

开元小篆的 SVG 是从刻本扫描逐点描的,直接编出来 20MB。先栅格化到 256px、轻微模糊去毛边,再用 potrace 粗一点重描(体积约三分之一),
只收用到的 2,853 个篆字,CFF 轮廓 → `BoshanSeal.otf` 1.6MB,字体名 Boshan Seal / 博山篆(按 OFL 改了名)。第一次重描十来分钟,结果缓存在 `.cache/font/`。

## 本机实测

系统装了 `librime-bin librime-plugin-lua librime-dev`(Debian):

```sh
cc -O1 -o build/rime_test tools/rime-test/rime_test.c -lrime
build/rime_test build/rime /tmp/bsuser --deploy hwzcm xue "{Control+Shift+4}xue"   # 第一次部署一两分钟
build/rime_test build/rime /tmp/bsuser -o seal=1 -o simplification=0 xuexi
```
