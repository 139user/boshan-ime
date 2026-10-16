# 博山输入法

基于 [Rime](https://rime.im) 的拼音输入法,安卓、Windows、macOS 三端。底子是[雾凇拼音](https://github.com/iDvel/rime-ice),特色:

- **简 / 繁 / 篆**三态:篆 = Unicode 18.0 篆书区块(U+3D000..U+3FC3F)的小篆字符,只做常用 3500 字(《通用规范汉字表》一级字表)及其繁体。
- **候选旁显示小篆**:打字时常用字旁边带上它的小篆。管理员在后台定默认显示还是不显示,用户可以自己关(以用户的为准)。
- **篆图**:把字做成小篆图片发出去(安卓 commitContent / 剪贴板;桌面由博山助手贴进当前程序)。
- **先秦儒道典籍整句联想**:道德经、论语、孟子、荀子、礼记(含学记)、周易、管子。从一句的开头起打每个字拼音的首字母就能出整句,
  比如 `hwzcmxmcsjmcz` → 「后王之成名：刑名从商，爵名从周」〔荀子·正名〕;只打前几个字母时给到小句末尾,头一条另给整句补全。
  典籍里的小句也挂进了主词库,全拼(`xueershixizhi`)也能直接出。
- 内置**博山篆**字体(由[开元小篆](https://github.com/frankslin/kaiyuan-small-seal-font)重描,SIL OFL 1.1),候选窗、键盘、图片里的小篆都用它显示。

暂时不做:语音输入、用户登录。

## 三端

| | 怎么做的 | 安装包 |
|---|---|---|
| 安卓 | 自己的键盘(`android/ime` 库 + `android/app`),纯原生 View,librime 经 JNI | `boshan-<版本>.apk` |
| Windows | 官方小狼毫(Weasel)+ 博山配置包 + 博山助手,NSIS 安装包(`windows/`) | `boshan-setup-<版本>.exe` |
| macOS | 官方鼠须管(Squirrel)+ 博山组件,productbuild 安装包(`macos/`,GitHub Actions 上打) | `boshan-<版本>.pkg`(未签名,首次打开要右键 → 打开) |

快捷键(桌面):`Ctrl+Shift+4` 简 → 繁 → 篆、`Ctrl+Shift+5` 小篆提示开关、`Ctrl+Shift+6` 篆图开关;`F4` / ``Ctrl+` `` 方案选单里也能切。
安卓:工具栏「简」点一下轮换、长按开关小篆提示;「篆图」开关篆图模式(输入框里先选中字再点则直接把选中的做成图片)。

cowork 的安卓键盘也是这个库:cowork 以 git 子模块引用本仓库(环境变量 `COWORK_IME=1` 时集成),在上面加了语音和跨设备(见 docs/android.md)。

## 目录

```
corpus/      典籍原文(sky.boshanlu.com 抓的,简繁对照),tools/fetch-corpus.ts
rime/        博山方案(boshan.schema.yaml)、主词库入口、default 补丁、lua/boshan/*(联想、小篆提示、篆图、开关记忆)
tools/       数据构建:build-rime.ts(总入口)、build-juzi.ts(句库与典籍词库)、build-seal.ts(简繁篆 OpenCC 数据)、
             build-font.py + retrace-glyphs.sh(博山篆字体)、readings.ts(字音)、fetch.ts + lock.json(外部来源锁定)、
             prepare-android.ts、laptop-android.sh、publish.sh、rime-test/(本机实测方案的小程序)
android/     安卓:ime(键盘库)、app(独立 App)
windows/     NSIS 安装脚本、博山助手(PowerShell)、小狼毫样式补丁、build.sh
macos/       pkg 打包脚本、安装后脚本、篆图脚本(JXA)、鼠须管样式补丁、定时取设置
server/      博山后台:默认设置下发、管理页、下载(Bun),deploy.sh
docs/        设计说明:data.md(方案与数据)、android.md、desktop.md、server.md
```

## 构建与发布

```sh
bun tools/build-rime.ts                 # → build/rime、build/fonts/BoshanSeal.otf、build/dist/boshan-rime-<版本>.zip
tools/publish.sh data build/dist/boshan-rime-*.zip   # 数据包放到后台(cowork 与 macOS 打包从这里取)
bun tools/prepare-android.ts && tools/laptop-android.sh   # 安卓(在笔记本上编)→ build/dist/boshan-<版本>.apk
windows/build.sh                        # Windows(VPS 上 makensis)→ build/dist/boshan-setup-<版本>.exe
gh workflow run macos.yml -R 139user/boshan-ime          # macOS(Actions),gh run download 取回
tools/publish.sh android|windows|macos <文件>            # 挂到后台首页的下载
```

后台:https://boshan.zhilin.cowork.nj7.uk(管理页 `/admin`,初始口令在 VPS 的 `/var/lib/boshan/admin-initial-password.txt`)。

## 许可

博山自己的方案、脚本与代码按 GPL-3.0(跟雾凇拼音、小狼毫、鼠须管一致)。数据与字体各按其许可:雾凇拼音 GPL-3.0、OpenCC 数据 Apache-2.0、
博山篆字体 SIL OFL 1.1、Unicode 数据 Unicode License v3;典籍原文来自[银汉时空](https://sky.boshanlu.com)。
