; 博山输入法 Windows 安装包:官方小狼毫(Weasel,GPL-3.0)+ 博山配置包。用 makensis 在 Linux 上编(windows/build.sh)。
; 装的时候:
;   1. 没装小狼毫就静默装上安装包里带的那份(官方原版,未改动)
;   2. 博山方案与数据放进 %APPDATA%\Rime(小狼毫的用户目录);会被盖掉的 default.custom.yaml、weasel.custom.yaml 先备份
;   3. 博山篆字体装进系统字体
;   4. 博山助手(篆图出图、定时取管理员默认设置)放进安装目录,登录时自动启动
;   5. 从博山后台取一次默认设置,再让小狼毫重新部署
; 卸载只拿掉博山的东西(方案、字体、助手),小狼毫留着(要卸在"应用"里卸)。
Unicode true
SetCompressor /SOLID lzma

!ifndef VERSION
  !define VERSION "0.0.0"
!endif
!ifndef STAGE
  !define STAGE "stage"
!endif

!define NAME "博山输入法"
!define UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\BoshanIME"
!define RUN_KEY "Software\Microsoft\Windows\CurrentVersion\Run"
!define FONTS_KEY "SOFTWARE\Microsoft\Windows NT\CurrentVersion\Fonts"

Name "${NAME}"
OutFile "${OUTFILE}"
InstallDir "$PROGRAMFILES64\BoshanIME"
RequestExecutionLevel admin
BrandingText "${NAME} ${VERSION}"

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "x64.nsh"
!include "WinMessages.nsh"

!define MUI_ICON "${STAGE}\boshan.ico"
!define MUI_UNICON "${STAGE}\boshan.ico"
!define MUI_WELCOMEPAGE_TITLE "安装博山输入法"
!define MUI_WELCOMEPAGE_TEXT "博山输入法基于小狼毫(Rime)。$\r$\n$\r$\n· 简 / 繁 / 篆 三态切换(Ctrl+Shift+4),候选旁显示常用字的小篆(Ctrl+Shift+5)$\r$\n· 篆图:选字直接贴出小篆图片(Ctrl+Shift+6 开关)$\r$\n· 先秦儒道典籍:从一句开头打每个字拼音的首字母,联想出整句$\r$\n$\r$\n没装小狼毫的话会一并装上(官方原版)。"
!define MUI_FINISHPAGE_TEXT "装好了。在任务栏的输入法里选「小狼毫」即可使用博山输入法(方案名「博山拼音」)。$\r$\n$\r$\n第一次部署词库要一两分钟,期间可能打不出字。"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${STAGE}\LICENSE.txt"
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "SimpChinese"

Var WeaselRoot

; 小狼毫装在哪(它的安装程序写 HKLM\Software\Rime\Weasel 的 WeaselRoot;32 / 64 位两个视图都看)
Function FindWeasel
  StrCpy $WeaselRoot ""
  SetRegView 64
  ReadRegStr $WeaselRoot HKLM "Software\Rime\Weasel" "WeaselRoot"
  SetRegView 32
  ${If} $WeaselRoot == ""
    ReadRegStr $WeaselRoot HKLM "Software\Rime\Weasel" "WeaselRoot"
  ${EndIf}
  SetRegView 64
FunctionEnd

; 用户自己的配置文件不是博山的就先备份(博山的文件里都有 boshan 字样;已经备份过的不再覆盖):$0 = 文件名
Function BackupIfForeign
  ${If} ${FileExists} "$APPDATA\Rime\$0"
  ${AndIfNot} ${FileExists} "$APPDATA\Rime\boshan-backup\$0"
    nsExec::ExecToStack 'findstr /m /c:"boshan" "$APPDATA\Rime\$0"'
    Pop $1
    ${If} $1 != 0
      CreateDirectory "$APPDATA\Rime\boshan-backup"
      CopyFiles /SILENT "$APPDATA\Rime\$0" "$APPDATA\Rime\boshan-backup\$0"
      DetailPrint "已备份原来的 $0 到 Rime\boshan-backup"
    ${EndIf}
  ${EndIf}
FunctionEnd

Section "install"
  SetShellVarContext current
  SetRegView 64

  ; 1. 小狼毫
  Call FindWeasel
  ${If} $WeaselRoot == ""
    DetailPrint "安装小狼毫(官方原版 ${WEASEL_VERSION})……"
    InitPluginsDir
    File "/oname=$PLUGINSDIR\weasel-installer.exe" "${STAGE}\weasel-installer.exe"
    ExecWait '"$PLUGINSDIR\weasel-installer.exe" /S' $0
    Call FindWeasel
    ${If} $WeaselRoot == ""
      MessageBox MB_ICONSTOP "小狼毫没装上(安装程序返回 $0)。可以先从 rime.im 装好小狼毫,再运行本安装包。"
      Abort
    ${EndIf}
  ${Else}
    DetailPrint "已装小狼毫:$WeaselRoot"
  ${EndIf}

  ; 2. 博山方案与数据
  CreateDirectory "$APPDATA\Rime"
  StrCpy $0 "default.custom.yaml"
  Call BackupIfForeign
  StrCpy $0 "weasel.custom.yaml"
  Call BackupIfForeign
  SetOutPath "$APPDATA\Rime"
  File /r "${STAGE}\rime\*.*"
  File "${STAGE}\weasel.custom.yaml"

  ; 3. 字体
  SetOutPath "$FONTS"
  File "${STAGE}\BoshanSeal.otf"
  WriteRegStr HKLM "${FONTS_KEY}" "Boshan Seal (OpenType)" "BoshanSeal.otf"
  System::Call 'gdi32::AddFontResourceW(w "$FONTS\BoshanSeal.otf") i .r0'
  SendMessage ${HWND_BROADCAST} ${WM_FONTCHANGE} 0 0 /TIMEOUT=2000

  ; 4. 博山助手
  SetOutPath "$INSTDIR"
  File "${STAGE}\boshan-helper.ps1"
  File "${STAGE}\boshan-helper.vbs"
  File "${STAGE}\boshan.ico"
  File "${STAGE}\LICENSE.txt"
  WriteRegStr HKCU "${RUN_KEY}" "BoshanIMEHelper" 'wscript.exe "$INSTDIR\boshan-helper.vbs"'

  ; 5. 取管理员定的默认设置(助手里也会每 6 小时取一次),重新部署,启动助手
  DetailPrint "取默认设置……"
  nsExec::Exec 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\boshan-helper.ps1" -Once'
  DetailPrint "部署词库(第一次要一两分钟)……"
  ExecWait '"$WeaselRoot\WeaselDeployer.exe" /deploy'
  ; 经 explorer 启动,助手以普通权限跑(安装程序是管理员权限)
  Exec 'explorer.exe "$INSTDIR\boshan-helper.vbs"'

  ; 卸载信息
  WriteUninstaller "$INSTDIR\uninstall.exe"
  WriteRegStr HKLM "${UNINST_KEY}" "DisplayName" "${NAME}"
  WriteRegStr HKLM "${UNINST_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKLM "${UNINST_KEY}" "Publisher" "博山输入法"
  WriteRegStr HKLM "${UNINST_KEY}" "DisplayIcon" "$INSTDIR\boshan.ico"
  WriteRegStr HKLM "${UNINST_KEY}" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegDWORD HKLM "${UNINST_KEY}" "NoModify" 1
  WriteRegDWORD HKLM "${UNINST_KEY}" "NoRepair" 1
SectionEnd

Section "Uninstall"
  SetShellVarContext current
  SetRegView 64
  ; 停掉助手
  nsExec::Exec 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\boshan-helper.ps1" -Stop'
  DeleteRegValue HKCU "${RUN_KEY}" "BoshanIMEHelper"
  ; 方案与数据里博山自己的文件(雾凇拼音的公共文件留着,不碍事)
  Delete "$APPDATA\Rime\boshan.schema.yaml"
  Delete "$APPDATA\Rime\boshan.dict.yaml"
  Delete "$APPDATA\Rime\boshan_*.txt"
  Delete "$APPDATA\Rime\cn_dicts\boshan_*.dict.yaml"
  RMDir /r "$APPDATA\Rime\lua\boshan"
  Delete "$APPDATA\Rime\opencc\BoshanSeal.ocd2"
  Delete "$APPDATA\Rime\opencc\s2seal.json"
  Delete "$APPDATA\Rime\opencc\t2seal.json"
  Delete "$APPDATA\Rime\default.custom.yaml"
  Delete "$APPDATA\Rime\weasel.custom.yaml"
  ; 装之前备份过的原配置放回去
  CopyFiles /SILENT "$APPDATA\Rime\boshan-backup\*.*" "$APPDATA\Rime"
  RMDir /r "$APPDATA\Rime\boshan-backup"
  ; 字体
  System::Call 'gdi32::RemoveFontResourceW(w "$FONTS\BoshanSeal.otf") i .r0'
  Delete /REBOOTOK "$FONTS\BoshanSeal.otf"
  DeleteRegValue HKLM "${FONTS_KEY}" "Boshan Seal (OpenType)"
  ; 重新部署小狼毫(回到它自己的方案)
  Call un.FindWeasel
  ${If} $WeaselRoot != ""
    ExecWait '"$WeaselRoot\WeaselDeployer.exe" /deploy'
  ${EndIf}
  RMDir /r "$INSTDIR"
  DeleteRegKey HKLM "${UNINST_KEY}"
SectionEnd

Function un.FindWeasel
  StrCpy $WeaselRoot ""
  SetRegView 64
  ReadRegStr $WeaselRoot HKLM "Software\Rime\Weasel" "WeaselRoot"
  SetRegView 32
  ${If} $WeaselRoot == ""
    ReadRegStr $WeaselRoot HKLM "Software\Rime\Weasel" "WeaselRoot"
  ${EndIf}
  SetRegView 64
FunctionEnd
