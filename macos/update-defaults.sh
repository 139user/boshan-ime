#!/bin/bash
# 博山输入法(macOS):从博山后台取管理员定的默认设置(候选旁显不显示小篆),写进 ~/Library/Rime/boshan_defaults.txt。
# 由 LaunchAgent(com.boshanlu.ime.defaults)每 6 小时跑一次,装的时候也跑一次。
RIME="$HOME/Library/Rime"
mkdir -p "$RIME"
json=$(/usr/bin/curl -fsS --max-time 10 https://boshan.zhilin.cowork.nj7.uk/api/config) || exit 0
case "$json" in
  *'"sealHintDefault":true'*) v=1 ;;
  *'"sealHintDefault":false'*) v=0 ;;
  *) exit 0 ;;
esac
body=$(printf '# 博山输入法:管理员在后台定的默认值,由 update-defaults.sh 从后台取来写入\nseal_hint=%s' "$v")
[ "$(cat "$RIME/boshan_defaults.txt" 2>/dev/null)" = "$body" ] || printf '%s\n' "$body" > "$RIME/boshan_defaults.txt"
