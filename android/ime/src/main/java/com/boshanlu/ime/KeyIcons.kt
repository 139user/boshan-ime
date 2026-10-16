package com.boshanlu.ime

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * 键上的几个图标(照搜狗的样子用线条画,不靠字体里的 ⌫ ↵ 这些字符,各机型字体不一样):
 * 退格、回车、空格键(上面一个淡淡的麦克风 —— 长按空格说话,下面一道 ⎵)、Shift。尺寸都是参考像素,经 theme.r 换算。
 */
class KeyIcons(private val theme: ImeTheme) {
  private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
  private val path = Path()

  private fun pen(color: Int, w: Float) { stroke.color = color; stroke.strokeWidth = theme.r(w) }

  /** 退格:左边尖的五边形框,里面一个 × */
  fun backspace(c: Canvas, cx: Float, cy: Float, color: Int) {
    pen(color, 2.6f)
    val w = theme.r(38f); val h = theme.r(26f); val tip = theme.r(11f)
    val l = cx - w / 2; val r = cx + w / 2; val t = cy - h / 2; val b = cy + h / 2
    path.reset()
    path.moveTo(l + tip, t); path.lineTo(r, t); path.lineTo(r, b); path.lineTo(l + tip, b); path.lineTo(l, cy); path.close()
    c.drawPath(path, stroke)
    val xc = cx + tip / 2; val d = theme.r(6f)
    c.drawLine(xc - d, cy - d, xc + d, cy + d, stroke)
    c.drawLine(xc - d, cy + d, xc + d, cy - d, stroke)
  }

  /** 回车:右上竖下来、往左拐的箭头 */
  fun enter(c: Canvas, cx: Float, cy: Float, color: Int) {
    pen(color, 2.8f)
    val w = theme.r(36f); val h = theme.r(14f)
    val r = cx + w / 2; val l = cx - w / 2; val t = cy - h; val b = cy + theme.r(4f)
    path.reset()
    path.moveTo(r, t); path.lineTo(r, b); path.lineTo(l, b)
    c.drawPath(path, stroke)
    val a = theme.r(9f)
    c.drawLine(l, b, l + a, b - a, stroke)
    c.drawLine(l, b, l + a, b + a, stroke)
  }

  /** 空格:上面淡色麦克风,下面 ⎵ */
  fun space(c: Canvas, rect: RectF, color: Int, micColor: Int) {
    val cx = rect.centerX()
    // ⎵:参考截图在键底往上约 30 的地方,宽 56、两头翘 8
    pen(color, 2.8f)
    val y = rect.bottom - theme.r(31f); val half = theme.r(28f); val up = theme.r(8f)
    path.reset()
    path.moveTo(cx - half, y - up); path.lineTo(cx - half, y); path.lineTo(cx + half, y); path.lineTo(cx + half, y - up)
    c.drawPath(path, stroke)
    // 麦克风:圆角胶囊 + 底下的托和竖杆
    pen(micColor, 2.2f)
    val mt = rect.top + theme.r(14f); val mw = theme.r(10f); val mh = theme.r(17f)
    c.drawRoundRect(cx - mw / 2, mt, cx + mw / 2, mt + mh, mw / 2, mw / 2, stroke)
    val arc = RectF(cx - theme.r(9f), mt + theme.r(5f), cx + theme.r(9f), mt + mh + theme.r(5f))
    c.drawArc(arc, 0f, 180f, false, stroke)
    c.drawLine(cx, arc.bottom, cx, arc.bottom + theme.r(4f), stroke)
  }

  /** Shift:空心向上箭头;locked 时底下加一横 */
  fun shift(c: Canvas, cx: Float, cy: Float, color: Int, filled: Boolean, locked: Boolean) {
    pen(color, 2.6f)
    val w = theme.r(13f); val h = theme.r(14f); val sw = theme.r(6f)
    path.reset()
    path.moveTo(cx, cy - h); path.lineTo(cx + w, cy); path.lineTo(cx + sw, cy); path.lineTo(cx + sw, cy + h * 0.7f)
    path.lineTo(cx - sw, cy + h * 0.7f); path.lineTo(cx - sw, cy); path.lineTo(cx - w, cy); path.close()
    stroke.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
    c.drawPath(path, stroke)
    stroke.style = Paint.Style.STROKE
    if (locked) c.drawLine(cx - sw, cy + h * 1.1f, cx + sw, cy + h * 1.1f, stroke)
  }
}
