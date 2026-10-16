#!/usr/bin/env bash
# 把后台部署到本机:代码拷到 /opt/boshan/server,systemd 服务 boshan(端口 18930,数据 /var/lib/boshan),
# 公网经 cowork 的网址 https://boshan.zhilin.cowork.nj7.uk 进来。要 sudo。
set -euo pipefail
cd "$(dirname "$0")"
BUN=${BUN:-$(command -v bun)}
sudo install -d -o "$USER" -g "$USER" /opt/boshan/server /var/lib/boshan /var/lib/boshan/downloads
install -m 644 main.ts store.ts pages.ts /opt/boshan/server/
sudo tee /etc/systemd/system/boshan.service >/dev/null <<UNIT
[Unit]
Description=博山输入法后台(默认设置下发、管理页、下载)
After=network.target

[Service]
User=$USER
Environment=BOSHAN_PORT=18930
Environment=BOSHAN_DATA=/var/lib/boshan
ExecStart=$BUN /opt/boshan/server/main.ts
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now boshan
sudo systemctl restart boshan
sleep 1
systemctl --no-pager --lines=3 status boshan | head -5
