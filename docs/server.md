# 博山后台(`server/`)

Bun 写的小服务,不做用户登录。部署在 VPS:`server/deploy.sh` 把代码拷到 `/opt/boshan/server`,systemd 服务 `boshan`(端口 18930,只听 127.0.0.1),
数据目录 `/var/lib/boshan`,公网经 cowork 的网址 https://boshan.zhilin.cowork.nj7.uk(`cowork domains set boshan 18930`)。

- `GET /`:首页,各端最新安装包的下载(按 `downloads/manifest.json`)。
- `GET /api/config`:各端取的设置 `{ sealHintDefault, updatedAt, releases }`。加字段时各端要能忽略不认识的、缺了用自己的默认。
- `GET /download/<文件>`:`downloads/` 里的文件(安装包、数据包、`BoshanSeal.woff2`)。`tools/publish.sh <种类> <文件>` 放文件并更新 manifest。
- `GET /admin`:管理页。管理口令登录(第一次启动随机生成,明文在 `admin-initial-password.txt`,只本用户可读;页面里可改),cookie 会话 7 天(HMAC 签名,不存服务端),
  登录错 8 次锁 10 分钟。能改的只有"打字时在候选旁显示常用字的小篆"的默认值,以及口令。
- 管理接口只收同源的 POST(cookie 是 SameSite=Strict,另查 Origin 与 `x-forwarded-host`)。
- 存储:`config.json`(下发的设置)、`admin.json`(口令哈希与签名密钥)。

各端取设置的频率:安卓 App 12 小时(键盘弹出时)、设置页打开时;Windows 博山助手 6 小时;macOS LaunchAgent 6 小时。cowork 的键盘不访问这里,用 cowork 自己的管理面板设置。
