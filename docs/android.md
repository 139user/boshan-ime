# 安卓

`android/` 是一个 Gradle 工程:`ime`(键盘库,包 `com.boshanlu.ime`)+ `app`(独立的博山输入法 App,`applicationId com.boshanlu.ime`)。
键盘最早是 cowork 的安卓键盘,抽出来成了这个库;cowork 现在用同一份源码(见下面"在 cowork 里")。

## 键盘库 `ime`

纯原生 View,不用 WebView(键盘要秒开)。排版、尺寸、手感照搜狗 26 键在手机上的截图逐项量(`ImeTheme.kt`、`ImeKeys.kt`,单位"参考像素",截图宽 706)。

- `BoshanIme.kt`:`InputMethodService`(open class)。从上到下 拼音小标签那一条(透明、点得透)、灰块(顶栏:工具栏 `ImeToolbar` / 组词时的候选条 `CandidateBar`;
  键区 `KeyboardView`;符号面板 `SymbolPanel`、候选面板 `CandidatePanel` 盖在上面)、底下让开导航栏。
  - 工具栏:切换输入法 · 中/英 · 简/繁/篆 · 状态文字(两格)· [宿主的按钮] · 篆图 · 收起。
  - 简繁篆:点「简」轮换(设 Rime 的三个选项,lua 记进 `boshan_prefs.txt`);长按开关小篆提示(同时写 `boshan_prefs.txt`)。「简」字下有线 = 小篆提示开着。
  - 篆图(`SealImage.kt`):输入框里选着字就把那段做成图片;否则开关篆图模式,开着时点候选 / 按空格出图片(拼音标签前面标"篆图")。
    画:米色纸、墨色字、博山篆字体,每行 8 字;发:输入框声明收 image/png 就 `commitContent`,否则放剪贴板提示长按粘贴。FileProvider 用库自己的子类 `SealFileProvider`
    (权限名 `<包名>.boshan.seal`),免得和宿主自己的 FileProvider 撞名。
  - 每次弹出:按 用户选择 > 管理员默认(`boshan_defaults.txt`)> 显示 设好 `seal_hint`(`BoshanPrefs`);`configUrl` 不为空就隔 12 小时从博山后台取一次默认值。
- 候选的注释(小篆提示、〔荀子·正名〕出处、篆模式下的今字)在候选条和面板里跟在字后面,小篆提示原大、深灰,文字注释小一号(`CandText.kt`)。整段用博山篆字体(`SealFont`),字体里没有的字系统自动退回默认字体。
- 拼音引擎:`RimeEngine.kt` → `RimeJni.kt` → `cpp/rime_jni.cpp`(librime C API 包成 JNI,`librime_jni.so`,librime 及依赖是 fcitx5-android 的预编译静态库,整库链入)。
  APK 的 `assets/rime/version.txt` 与 `filesDir/rime/shared` 的不一致就重拷、重部署(第一次一两分钟,后台线程,期间工具栏提示、字母直接上屏)。用户目录 `filesDir/rime/user`。
- 扩展点(给宿主):`extraToolbarButtons()`、`extraPanels()`、`onKeyboardShown/Hidden`、`statusText()`、`onStatusTapped()`、`onSpaceHold()`、`configUrl`、`reloadPrefs()`,
  以及 `commitComposition()`、`enter()`、`showBody()`、`selection` 等。

## 独立 App `app`

- `ImeService`:继承 `BoshanIme`,`configUrl` = 博山后台的 `/api/config`。清单里跑在 `:ime` 进程。
- `SettingsActivity`:启用 / 切换输入法两步、"候选旁显示小篆"开关(写 `boshan_prefs.txt`,显示管理员默认值)、用法说明、试打框、关于。
- 签名:正式密钥在 VPS 的 `~/.config/boshan-ime/`(`release.jks` + `keystore-password`,别弄丢——丢了以后的包就装不上旧版之上),
  `tools/laptop-android.sh` 每次传到笔记本写成 `android/keystore.properties`。没有就用 debug 签名。

## 构建

```sh
bun tools/build-rime.ts         # 数据包
bun tools/prepare-android.ts    # 预编译 librime → ime/src/main/cpp/prebuilt;数据包与字体 → ime/src/main/assets(换上 rime/android/default.custom.yaml)
tools/laptop-android.sh         # 传到笔记本 C:\code\boshan-ime 编 :app:assembleRelease,APK 拷回 build/dist/
```

只出 arm64。AGP / Kotlin 版本跟 cowork 一致(8.11 / 1.9.25),因为同一份库要在 cowork 里编。

## 在 cowork 里

cowork 把 boshan-ime 仓库作为 git 子模块挂在 `client/src-tauri/gen/android/boshan-ime`,Gradle 直接用子模块里的 `android/ime`(模块名 `:boshan-ime`)。
**是否集成由环境变量 `COWORK_IME=1` 控制**,默认不集成:不设时不需要子模块,cowork 照样能编(安卓包里就没有键盘);cowork 自己的构建(Actions、笔记本的
`build-all.ps1`、发版)都设了 `COWORK_IME=1`。

- cowork 的键盘部分在它自己的模块 `:cowork-ime`(`gen/android/cowork-ime`):`CoworkIme` 继承 `BoshanIme`,加 说到电脑 / 麦克风 / 剪贴板 三个按钮、剪贴板面板、
  长按空格说话、状态文字;小篆提示的默认值由 cowork 管理员在管理面板"语音与键盘"里定,键盘经旁路连接的 `ime.config` 取来写进 `boshan_defaults.txt`(不访问博山后台)。
- cowork 的 `prepare-tools.ts android`(`COWORK_IME=1` 时)下载预编译 librime 到子模块的 `android/ime/src/main/cpp/prebuilt`、从博山后台下载数据包
  (cowork `tools.lock.json` 的 `rime.data`,地址 + SHA-256)解到 `android/ime/src/main/assets`,这两处在本仓库的 `.gitignore` 里,子模块保持干净。
- 改了键盘库:在本仓库提交推送,cowork 里 `git -C client/src-tauri/gen/android/boshan-ime pull` 再提交子模块指针。发布了新数据包,cowork 要改 `tools.lock.json` 的地址和哈希。
- 因为同一份库要在 cowork 里编,AGP / Kotlin 版本要跟 cowork 一致;库的扩展点(`BoshanIme` 的 open / protected 成员)改了要同时改 cowork 的 `CoworkIme`。
