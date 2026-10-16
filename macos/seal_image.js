// 博山篆图(macOS):鼠须管里 lua/boshan/seal_image.lua 选字时用 osascript -l JavaScript 跑这个脚本,
// 参数是请求文件(第一行是要画的字,已转成小篆)。用博山篆字体画成图片(米色纸、墨色字),放进剪贴板,
// 再试着按 ⌘V 粘贴到当前程序(要给"辅助功能"权限;没给就弹通知让用户自己按)。
ObjC.import('AppKit')

function run(argv) {
  const raw = $.NSString.stringWithContentsOfFileEncodingError(argv[0], $.NSUTF8StringEncoding, null)
  if (!raw || raw.isNil()) return
  const text = raw.js.split('\n')[0].trim()
  if (!text) return
  $.NSFileManager.defaultManager.removeItemAtPathError(argv[0], null)

  // 每行最多 8 个字(按码点)
  const chars = Array.from(text).filter((c) => c.trim())
  const lines = []
  for (let i = 0; i < chars.length; i += 8) lines.push(chars.slice(i, i + 8).join(''))
  const size = 160, pad = 48, lineH = size * 1.25
  const font = $.NSFont.fontWithNameSize('BoshanSeal-Regular', size)
  const attrs = $.NSMutableDictionary.alloc.init
  attrs.setObjectForKey(font.isNil() ? $.NSFont.systemFontOfSize(size) : font, $.NSFontAttributeName)
  attrs.setObjectForKey($.NSColor.colorWithSRGBRedGreenBlueAlpha(0x2B / 255, 0x21 / 255, 0x18 / 255, 1), $.NSForegroundColorAttributeName)
  const strs = lines.map((l) => $.NSAttributedString.alloc.initWithStringAttributes(l, attrs))
  const w = Math.max(size, ...strs.map((s) => s.size.width)) + pad * 2
  const h = lineH * strs.length + pad * 2

  const rep = $.NSBitmapImageRep.alloc.initWithBitmapDataPlanesPixelsWidePixelsHighBitsPerSampleSamplesPerPixelHasAlphaIsPlanarColorSpaceNameBytesPerRowBitsPerPixel(
    null, Math.ceil(w), Math.ceil(h), 8, 4, true, false, $.NSDeviceRGBColorSpace, 0, 0)
  $.NSGraphicsContext.saveGraphicsState
  $.NSGraphicsContext.setCurrentContext($.NSGraphicsContext.graphicsContextWithBitmapImageRep(rep))
  $.NSColor.colorWithSRGBRedGreenBlueAlpha(0xF6 / 255, 0xEF / 255, 0xDF / 255, 1).setFill
  $.NSBezierPath.bezierPathWithRoundedRectXRadiusYRadius($.NSMakeRect(0, 0, w, h), pad / 2, pad / 2).fill
  strs.forEach((s, i) => {
    const x = (w - s.size.width) / 2
    const y = h - pad - lineH * (i + 1) + (lineH - s.size.height) / 2 // 坐标原点在左下
    s.drawAtPoint($.NSMakePoint(x, y))
  })
  $.NSGraphicsContext.restoreGraphicsState
  const png = rep.representationUsingTypeProperties($.NSBitmapImageFileTypePNG, $({}))

  const pb = $.NSPasteboard.generalPasteboard
  pb.clearContents
  pb.setDataForType(png, $.NSPasteboardTypePNG)
  pb.setDataForType(rep.TIFFRepresentation, $.NSPasteboardTypeTIFF)

  const app = Application.currentApplication()
  app.includeStandardAdditions = true
  try {
    Application('System Events').keystroke('v', { using: 'command down' })
  } catch (e) {
    app.displayNotification('小篆图片已复制,按 ⌘V 粘贴', { withTitle: '博山输入法' })
  }
}
