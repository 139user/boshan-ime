# 博山输入法 项目规则

## 是什么
- 基于 Rime 的拼音输入法,三端:安卓(自己的键盘,`android/`)、Windows(官方小狼毫 + 博山配置包,`windows/`)、macOS(官方鼠须管 + 博山配置包,`macos/`)。
- 特色:简 / 繁 / 篆三态、候选旁显示常用字小篆(管理员在后台定默认值,用户可关)、篆图(小篆图片)、先秦儒道典籍首字母整句联想。
- 典籍范围**只限**:道德经、论语、孟子、荀子、礼记(含学记)、周易、管子(`tools/fetch-corpus.ts` 的 BOOKS)。别往里加别的书。
- 小篆只做常用 3500 字(《通用规范汉字表》一级字表)及其繁体;字体只收这些字的篆形。
- cowork 的安卓键盘就是 `android/ime` 这个库,cowork 在上面加语音、跨设备(见 docs/android.md 的"在 cowork 里")。改了库要用 `tools/sync-cowork.sh` 同步过去。

## 代码
- 构建工具 Bun + TS(`tools/`),字体脚本 Python(fontTools);后台 Bun(`server/`);安卓 Kotlin + JNI(librime)。
- 进模型上下文与界面的文字写简体中文;注释也写中文。单个手写源码文件不超过 400 行。
- 数据流:`bun tools/build-rime.ts` → `build/rime`(方案 + 生成的句库 / 词库 / OpenCC 数据)、`build/fonts/BoshanSeal.otf`、`build/dist/boshan-rime-<版本>.zip`。
  `rime/` 下是手写的方案与 lua;生成物不入库(`.cache/`、`build/`、安卓的 assets 与 prebuilt)。
- 本机实测方案:`cc -O1 -o build/rime_test tools/rime-test/rime_test.c -lrime`,再 `build/rime_test build/rime /tmp/bsuser --deploy hwzcm`(系统装了 librime-bin 与 librime-plugin-lua)。

## 机器
- 代码在 VPS 上写;VPS 内存小,不做安卓构建、不做类型检查。
- 安卓构建在笔记本(Windows,SSH 地址见用户的全局规则,也写在 VPS 的 `~/.config/boshan-ime/laptop`;pwsh),仓库位置 `C:\code\boshan-ime`:把 `android/`(含准备好的 assets、prebuilt)与 `version.txt` 传过去,`cd android; .\gradlew.bat :app:assembleRelease`。
- Windows 安装包在 VPS 上用 makensis 编:`windows/build.sh`。
- macOS 安装包只能在 GitHub Actions 上打(私有仓库 139user/boshan-ime):`gh workflow run macos.yml -R 139user/boshan-ime`,`gh run download` 取回。
- 发布到后台下载:`tools/publish.sh data|android|windows|macos <文件>`。数据包换了,cowork 的 `client/scripts/tools.lock.json` 的 `rime.data` 要跟着改。
- 设计说明在 `docs/`(data.md 方案与数据、android.md、desktop.md、server.md),改了行为同步改文档。
- 后台跑在 VPS 上:systemd `boshan.service`,端口 18930,公网 https://boshan.zhilin.cowork.nj7.uk ,数据目录 /var/lib/boshan。
