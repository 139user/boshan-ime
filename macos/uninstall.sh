#!/bin/bash
# 卸载博山输入法(鼠须管留着,要卸另说):sudo bash "/Library/Application Support/BoshanIME/uninstall.sh"
user=$(stat -f %Su /dev/console); uid=$(id -u "$user")
home=$(dscl . -read "/Users/$user" NFSHomeDirectory | awk '{print $2}'); RIME="$home/Library/Rime"
launchctl bootout "gui/$uid/com.boshanlu.ime.defaults" 2>/dev/null
rm -f /Library/LaunchAgents/com.boshanlu.ime.defaults.plist /Library/Fonts/BoshanSeal.otf
rm -rf "$RIME/lua/boshan" "$RIME/boshan" "$RIME"/boshan.*.yaml "$RIME"/boshan_*.txt "$RIME"/cn_dicts/boshan_*.dict.yaml \
  "$RIME/opencc/BoshanSeal.ocd2" "$RIME/opencc/s2seal.json" "$RIME/opencc/t2seal.json" "$RIME/default.custom.yaml" "$RIME/squirrel.custom.yaml"
[ -d "$RIME/boshan-backup" ] && cp "$RIME"/boshan-backup/* "$RIME"/ 2>/dev/null; rm -rf "$RIME/boshan-backup"
pkgutil --forget com.boshanlu.ime >/dev/null 2>&1
rm -rf "/Library/Application Support/BoshanIME"
SQ="/Library/Input Methods/Squirrel.app/Contents/MacOS/Squirrel"
[ -x "$SQ" ] && launchctl asuser "$uid" sudo -u "$user" "$SQ" --reload
echo "已卸载博山输入法"
