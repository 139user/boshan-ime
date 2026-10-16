# 博山助手(Windows):登录后常驻(由 boshan-helper.vbs 隐藏窗口启动),做两件事:
# 1. 篆图:盯着 %APPDATA%\Rime\boshan_image_request.txt(小狼毫里 lua/boshan/seal_image.lua 选字时写),
#    把第一行的字(已转成小篆)用博山篆字体画成图片,放进剪贴板(位图 + PNG 两种格式),再按 Ctrl+V 粘贴到当前程序。
#    不让 lua 起进程是因为小狼毫是窗口程序,os.execute 会闪一下黑框。
# 2. 每 6 小时从博山后台取管理员定的默认设置(候选旁显不显示小篆),写进 %APPDATA%\Rime\boshan_defaults.txt。
# 只跑一份(命名互斥量)。-Once:取一次默认设置就退(安装时用);-Stop:停掉在跑的助手(卸载时用);
# -Render <UTF-8 文本文件> -Out <png> [-FontDir <放 BoshanSeal.otf 的目录>]:只把字画成图片存到文件(测试出图,不碰剪贴板)。
param([switch]$Once, [switch]$Stop, [string]$Render, [string]$Out, [string]$FontDir)

$ErrorActionPreference = 'Continue'
if ($Stop) {
  Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" |
    Where-Object { $_.CommandLine -like '*boshan-helper.ps1*' -and $_.ProcessId -ne $PID } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
  exit 0
}
$mutex = New-Object System.Threading.Mutex($false, 'Local\BoshanIMEHelper')
if (-not $mutex.WaitOne(0)) { exit 0 }

Add-Type -AssemblyName PresentationCore, PresentationFramework, WindowsBase, System.Windows.Forms

$rime = Join-Path $env:APPDATA 'Rime'
$request = Join-Path $rime 'boshan_image_request.txt'
$defaults = Join-Path $rime 'boshan_defaults.txt'
$configUrl = 'https://boshan.zhilin.cowork.nj7.uk/api/config'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Update-Defaults {
  try {
    $c = Invoke-RestMethod -Uri $configUrl -TimeoutSec 10
    if ($null -eq $c.sealHintDefault) { return }
    $v = if ($c.sealHintDefault) { '1' } else { '0' }
    $body = "# 博山输入法:管理员在后台定的默认值,博山助手从后台取来写入`nseal_hint=$v`n"
    $old = if (Test-Path $defaults) { [IO.File]::ReadAllText($defaults, $utf8) } else { '' }
    if ($old -ne $body) { [IO.File]::WriteAllText($defaults, $body, $utf8) }
  } catch { }
}

function New-SealImage([string]$text) {
  # 每行最多 8 个字(按码点算,篆字在第三平面,一个字两个 UTF-16 单元)
  $cps = New-Object System.Collections.Generic.List[string]
  $e = [Globalization.StringInfo]::GetTextElementEnumerator($text)
  while ($e.MoveNext()) { if (-not [string]::IsNullOrWhiteSpace($e.GetTextElement())) { $cps.Add($e.GetTextElement()) } }
  if ($cps.Count -eq 0) { return $null }
  $lines = for ($i = 0; $i -lt $cps.Count; $i += 8) { ($cps.GetRange($i, [Math]::Min(8, $cps.Count - $i))) -join '' }
  $size = 160.0; $pad = 48.0
  $family = if ($FontDir) { New-Object Windows.Media.FontFamily([Uri]('file:///' + ($FontDir -replace '\\', '/') + '/'), './#Boshan Seal, Microsoft YaHei') }
            else { New-Object Windows.Media.FontFamily('Boshan Seal, Microsoft YaHei') }
  $typeface = New-Object Windows.Media.Typeface($family, [Windows.FontStyles]::Normal, [Windows.FontWeights]::Normal, [Windows.FontStretches]::Normal)
  $ink = New-Object Windows.Media.SolidColorBrush([Windows.Media.Color]::FromRgb(0x2B, 0x21, 0x18))
  $paper = New-Object Windows.Media.SolidColorBrush([Windows.Media.Color]::FromRgb(0xF6, 0xEF, 0xDF))
  $texts = foreach ($l in $lines) {
    New-Object Windows.Media.FormattedText($l, [Globalization.CultureInfo]::GetCultureInfo('zh-CN'), [Windows.FlowDirection]::LeftToRight, $typeface, $size, $ink, 1.0)
  }
  $lineH = $size * 1.25
  $w = [Math]::Max(($texts | ForEach-Object { $_.WidthIncludingTrailingWhitespace } | Measure-Object -Maximum).Maximum, $size) + 2 * $pad
  $h = $lineH * $texts.Count + 2 * $pad
  $dv = New-Object Windows.Media.DrawingVisual
  $dc = $dv.RenderOpen()
  $dc.DrawRoundedRectangle($paper, $null, (New-Object Windows.Rect(0, 0, $w, $h)), $pad / 2, $pad / 2)
  for ($i = 0; $i -lt $texts.Count; $i++) {
    $t = $texts[$i]
    $x = ($w - $t.WidthIncludingTrailingWhitespace) / 2
    $y = $pad + $lineH * $i + ($lineH - $t.Height) / 2
    $dc.DrawText($t, (New-Object Windows.Point($x, $y)))
  }
  $dc.Close()
  $bmp = New-Object Windows.Media.Imaging.RenderTargetBitmap([int][Math]::Ceiling($w), [int][Math]::Ceiling($h), 96, 96, [Windows.Media.PixelFormats]::Pbgra32)
  $bmp.Render($dv)
  return $bmp
}

function Send-SealImage([string]$text) {
  $bmp = New-SealImage $text
  if ($null -eq $bmp) { return }
  # 位图给大多数程序;PNG 给认 PNG 的(微信、QQ、浏览器、Office),保留圆角透明
  $png = New-Object IO.MemoryStream
  $enc = New-Object Windows.Media.Imaging.PngBitmapEncoder
  $enc.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($bmp))
  $enc.Save($png)
  $data = New-Object Windows.DataObject
  $data.SetImage($bmp)
  $data.SetData('PNG', $png)
  for ($i = 0; $i -lt 5; $i++) {
    try { [Windows.Clipboard]::SetDataObject($data, $true); break } catch { Start-Sleep -Milliseconds 100 }
  }
  Start-Sleep -Milliseconds 120
  [System.Windows.Forms.SendKeys]::SendWait('^v')
}

function Handle-Request {
  if (-not (Test-Path $request)) { return }
  $text = ''
  for ($i = 0; $i -lt 5; $i++) {
    try { $text = ([IO.File]::ReadAllText($request, $utf8) -split "`n")[0].Trim(); Remove-Item $request -ErrorAction SilentlyContinue; break }
    catch { Start-Sleep -Milliseconds 50 }
  }
  if ($text) { Send-SealImage $text }
}

if ($Render) {
  $bmp = New-SealImage ([IO.File]::ReadAllText($Render, $utf8).Trim())
  $enc = New-Object Windows.Media.Imaging.PngBitmapEncoder
  $enc.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($bmp))
  $fs = [IO.File]::Create($Out); $enc.Save($fs); $fs.Close()
  exit 0
}

if (-not (Test-Path $rime)) { New-Item -ItemType Directory -Force $rime | Out-Null }
Update-Defaults
if ($Once) { Handle-Request; exit 0 }

$watcher = New-Object IO.FileSystemWatcher($rime, 'boshan_image_request.txt')
$watcher.NotifyFilter = [IO.NotifyFilters]'FileName, LastWrite, Size'
$lastConfig = Get-Date
while ($true) {
  $r = $watcher.WaitForChanged([IO.WatcherChangeTypes]'Created, Changed', 5000)
  if (-not $r.TimedOut) { Start-Sleep -Milliseconds 30; Handle-Request }
  if (((Get-Date) - $lastConfig).TotalHours -ge 6) { Update-Defaults; $lastConfig = Get-Date }
}
