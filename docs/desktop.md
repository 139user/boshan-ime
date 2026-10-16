# Windows 与 macOS

桌面端不改输入法程序:用官方的小狼毫(Windows)/ 鼠须管(macOS),装上博山的配置包。两边的 librime 都带 lua(5.4)与 OpenCC,博山方案原样能跑。

## Windows(`windows/`)

`windows/build.sh` 在 VPS 上用 makensis 编 `boshan-setup-<版本>.exe`(约 25MB),里面带小狼毫官方安装包(`tools/lock.json` 的 `weasel`,按 SHA-256 校验,原样不改)。安装时:

1. 注册表找小狼毫(`HKLM\Software\Rime\Weasel` 的 `WeaselRoot`,32 / 64 位两个视图),没有就 `/S` 静默装上带的那份。
2. 数据包放进 `%APPDATA%\Rime`(小狼毫的用户目录)。原有的 `default.custom.yaml`、`weasel.custom.yaml` 里没有 `boshan` 字样的先备份到 `Rime\boshan-backup`(已备份过不再覆盖)。
   `weasel.custom.yaml`:候选窗字体 `Microsoft YaHei, Boshan Seal`(小篆在第三平面,雅黑里没有,往后退到博山篆)、横排、米色纸朱红高亮的配色。
3. 博山篆字体装进系统字体(`$FONTS` + 注册表 + `AddFontResource`)。
4. 博山助手(`boshan-helper.ps1`,由 `boshan-helper.vbs` 隐藏窗口启动;注册表 Run 项开机自启,装完经 explorer 以普通权限启动一次):
   - 篆图:盯着 `%APPDATA%\Rime\boshan_image_request.txt`(lua 的 `seal_image.lua` 选字时写),用 WPF 以博山篆画成图片,放进剪贴板(位图 + PNG),再 `SendKeys ^v` 贴进当前程序。
     不让 lua 起进程是因为小狼毫是窗口程序,`os.execute` 会闪黑框。
   - 每 6 小时取一次博山后台的默认设置,写 `boshan_defaults.txt`。
   - `-Once` 取一次就退(安装时)、`-Stop` 停掉在跑的(卸载时)、`-Render <文本文件> -Out <png> [-FontDir <目录>]` 只出图到文件(测试用)。
5. `WeaselDeployer.exe /deploy` 重新部署。

卸载只拿掉博山的东西(方案、lua、生成的数据、字体、助手),把备份的原配置放回去,重新部署;小狼毫本身留着。

已知限制:Windows 用户名含中文时,lua 的 `io.open` 打不开用户目录下的文件(librime-lua 在 Windows 上按 ANSI 路径打开),句库、开关记忆、篆图会失效。

## macOS(`macos/`)

只能在 macOS 上打包:GitHub Actions 的 `.github/workflows/macos.yml`(手动触发)从博山后台取最新数据包(按 `/api/config` 里的 SHA-256 校验)与鼠须管官方 pkg
(`lock.json` 的 `squirrel`),跑 `macos/build-pkg.sh` 出 `boshan-<版本>.pkg`(约 41MB),作为构建产物上传;VPS 上 `gh run download` 取回再 `tools/publish.sh macos`。

- 产品包 = 鼠须管官方组件包(原样)+ 博山组件:`/Library/Application Support/BoshanIME/`(数据包、`squirrel.custom.yaml`、`seal_image.js`、`update-defaults.sh`、`uninstall.sh`)、
  `/Library/Fonts/BoshanSeal.otf`、`/Library/LaunchAgents/com.boshanlu.ime.defaults.plist`(每 6 小时取默认设置)。
- 安装后脚本(`macos/scripts/postinstall`,root):给当前登录用户把数据放进 `~/Library/Rime`(原有配置先备份到 `boshan-backup`)、篆图脚本放进 `~/Library/Rime/boshan/`、
  取一次默认设置、启动 LaunchAgent、`Squirrel --reload`。
- 篆图:lua 直接 `osascript -l JavaScript ~/Library/Rime/boshan/seal_image.js <请求文件>`(macOS 上不闪窗口),用 AppKit 画图放进剪贴板(PNG + TIFF),
  再试着用 System Events 按 ⌘V(要给"辅助功能"权限;没给就发通知让用户自己按)。
- 安装包没签名(没有 Apple 开发者证书):用户第一次打开要右键 → 打开,或在"隐私与安全性"里放行。
- 卸载:`sudo bash "/Library/Application Support/BoshanIME/uninstall.sh"`。
- **macOS 版没在真机上测过**(手边没有 Mac),装完要实际用一下:候选窗字体、篆图、LaunchAgent。
