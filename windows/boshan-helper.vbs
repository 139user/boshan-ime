' 博山助手的启动器:隐藏窗口跑 boshan-helper.ps1(开机登录时由注册表 Run 项调起)
Set sh = CreateObject("WScript.Shell")
dir = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
sh.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File """ & dir & "\boshan-helper.ps1""", 0, False
