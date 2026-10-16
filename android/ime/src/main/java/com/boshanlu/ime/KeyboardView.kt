package com.boshanlu.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * 键盘本体:自己画(Canvas)。布局见 ImeKeys(按搜狗截图量的参考坐标),尺寸配色见 ImeTheme,图标见 KeyIcons。
 * 手感照搜狗:字符键抬起时才打(按下时震一下、变色),键顶有小字的上划过半个键高或长按打小字;
 * 退格按下就删、长按连删;长按空格说话(松开结束);"分词"键中文时插音节分隔符,英文时当 Shift(点一下大写一个、连点两下锁定)。
 * 只管把"按了哪个键"报给 BoshanIme,中英文、拼音都不在这里。
 */
class KeyboardView(ctx: Context, private val theme: ImeTheme) : View(ctx) {
  interface Listener {
    /** shifted:这一下要不要当大写 */
    fun onKey(key: Key, shifted: Boolean)
    /** 长按空格:true 开始说话,false 松开了 */
    fun onSpaceHold(start: Boolean)
  }
  var listener: Listener? = null
  var page: Page = Page.LETTERS
    set(v) { field = v; shift = 0; layoutKeys(); invalidate() }
  /** 回车键上写什么(搜索 / 发送……);"↵" = 没有动作,画回车图标 */
  var enterLabel: String = "↵"
    set(v) { field = v; invalidate() }
  /** 中/英键上标出现在是哪个 */
  var chinese: Boolean = true
    set(v) { field = v; shift = 0; invalidate() }
  /** 正在组词:回车键这时是"拼音原样上屏",画成普通的回车 */
  var composing: Boolean = false
    set(v) { if (field != v) { field = v; invalidate() } }
  /** 0 没按 / 1 下一个字母大写 / 2 锁定(只在英文时) */
  var shift = 0
    private set(v) { field = v; invalidate() }

  private class Slot(val key: Key, val rect: RectF)
  /** 一根手指按着的键:按下的位置、做过了没有(长按打过小字、开始说话了……) */
  private class Touch(val slot: Slot, val y: Float, var done: Boolean = false, var holding: Boolean = false)
  private var slots: List<Slot> = emptyList()
  private val touches = HashMap<Int, Touch>()
  private val handler = Handler(Looper.getMainLooper())
  private var lastShiftTap = 0L
  private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
  private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
  private val icons = KeyIcons(theme)
  private val radius = theme.r(ImeTheme.RADIUS)
  private val shadow = theme.r(1.5f)

  init { isHapticFeedbackEnabled = true }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), theme.ri(ImeTheme.KEYS_H))
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { layoutKeys() }

  private fun layoutKeys() {
    if (width == 0) { slots = emptyList(); return }
    val sx = width / ImeTheme.REF_W
    slots = ImeKeys.keys(page).map { k -> Slot(k, RectF(k.x0 * sx, theme.r(k.y0), k.x1 * sx, theme.r(k.y1))) }
  }

  private fun isAction(k: Key) = k.code == Code.ENTER && enterLabel != "↵" && !composing

  override fun onDraw(canvas: Canvas) {
    canvas.drawColor(theme.background)
    val down = touches.values.map { it.slot }
    for (s in slots) {
      val k = s.key
      val white = !k.special && !(page == Page.NUMBERS && k.code in listOf('.'.code, '@'.code))
      val base = when {
        isAction(k) -> theme.keyActive
        white -> theme.key
        else -> theme.keySpecial
      }
      fill.color = theme.keyShadow
      canvas.drawRoundRect(s.rect.left, s.rect.top + shadow, s.rect.right, s.rect.bottom + shadow, radius, radius, fill)
      fill.color = if (s in down && !isAction(k)) theme.keyPressed else base
      canvas.drawRoundRect(s.rect, radius, radius, fill)
      drawFace(canvas, s, k)
    }
  }

  private fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
    label.textSize = theme.r(size); label.color = color; label.textAlign = align
    c.drawText(t, x, y - (label.descent() + label.ascent()) / 2, label)
  }

  private fun drawFace(c: Canvas, s: Slot, k: Key) {
    val r = s.rect; val cx = r.centerX(); val cy = r.centerY()
    when (k.code) {
      Code.DEL -> icons.backspace(c, cx, cy, theme.text)
      Code.SPACE -> icons.space(c, r, theme.text, theme.hint)
      Code.ENTER -> if (isAction(k)) text(c, enterLabel, cx, cy, ImeTheme.ENTER, 0xFFFFFFFF.toInt()) else icons.enter(c, cx, cy, theme.text)
      Code.SPLIT -> if (chinese) text(c, "分词", cx, cy, ImeTheme.FUNC, theme.text) else icons.shift(c, cx, cy, theme.text, shift > 0, shift == 2)
      Code.LANG -> drawLang(c, r)
      Code.NUM -> text(c, k.label, cx, cy, ImeTheme.FUNC_LATIN, theme.text)
      Code.SYM, Code.ABC -> text(c, k.label, cx, cy, ImeTheme.FUNC, theme.text)
      Code.LIST -> {
        val items = k.items ?: return
        val h = r.height() / items.length
        items.forEachIndexed { i, ch -> text(c, ch.toString(), cx, r.top + h * (i + 0.5f), ImeTheme.PUNCT, theme.text) }
      }
      else -> {
        val ch = k.code.toChar()
        if (page == Page.NUMBERS) { text(c, k.label, cx, cy, if (ch.isDigit()) ImeTheme.DIGIT else ImeTheme.PUNCT, theme.text); return }
        k.hint?.let { h -> text(c, h.toString(), cx, r.top + theme.r(15.5f), ImeTheme.HINT, theme.hint) }
        if (ch.isLetter()) text(c, if (chinese || shift > 0) k.label.uppercase() else k.label, cx, cy + theme.r(2.5f), ImeTheme.LETTER, theme.text)
        else text(c, ImeKeys.shown(ch, chinese), cx, cy, ImeTheme.PUNCT, theme.text)
      }
    }
  }

  /** 中/英键:现在是哪个就大字,另一个跟着一道斜杠、小字灰色,整体居中 */
  private fun drawLang(c: Canvas, r: RectF) {
    val (on, off) = if (chinese) "中" to "/英" else "英" to "/中"
    label.textSize = theme.r(ImeTheme.LANG_BIG)
    val wOn = label.measureText(on)
    label.textSize = theme.r(ImeTheme.LANG_SMALL)
    val wOff = label.measureText(off)
    val x = r.centerX() - (wOn + wOff) / 2
    text(c, on, x, r.centerY(), ImeTheme.LANG_BIG, theme.text, Paint.Align.LEFT)
    text(c, off, x + wOn, r.centerY() + theme.r(3f), ImeTheme.LANG_SMALL, theme.hint, Paint.Align.LEFT)
  }

  private fun hit(x: Float, y: Float): Slot? = slots.firstOrNull { it.rect.contains(x, y) }
    // 键之间的缝也算最近的那个键,免得点在缝上没反应
    ?: slots.minByOrNull { d2(it.rect, x, y) }?.takeIf { d2(it.rect, x, y) < theme.r(12f) * theme.r(12f) }

  private fun d2(r: RectF, x: Float, y: Float): Float {
    val dx = maxOf(r.left - x, 0f, x - r.right)
    val dy = maxOf(r.top - y, 0f, y - r.bottom)
    return dx * dx + dy * dy
  }

  override fun onTouchEvent(e: MotionEvent): Boolean {
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
        val i = e.actionIndex
        val s = hit(e.getX(i), e.getY(i)) ?: return true
        val id = e.getPointerId(i)
        // 别的手指还按着字符键:先把它打出去(快速连打时两指交替)
        for ((pid, t) in touches.toList()) if (pid != id && !t.done && !t.slot.key.special) { release(t, t.y); touches.remove(pid) }
        val t = Touch(s, e.getY(i))
        touches[id] = t
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        pressDown(id, t)
        invalidate()
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
        val i = e.actionIndex
        touches.remove(e.getPointerId(i))?.let { release(it, e.getY(i)) }
        stopRepeat()
        invalidate()
      }
      MotionEvent.ACTION_CANCEL -> {
        if (touches.values.any { it.holding }) listener?.onSpaceHold(false)
        touches.clear(); stopRepeat(); handler.removeCallbacksAndMessages(null); invalidate()
      }
    }
    return true
  }

  /** 按下:Shift、退格当场做;带小字的键、空格开始等长按 */
  private fun pressDown(id: Int, t: Touch) {
    val k = t.slot.key
    when {
      k.code == Code.SPLIT && !chinese -> {
        val now = SystemClock.uptimeMillis()
        shift = if (now - lastShiftTap < 320) 2 else if (shift == 0) 1 else 0
        lastShiftTap = now
        t.done = true
      }
      k.code == Code.DEL -> { fire(k); startRepeat(k); t.done = true }
      k.code == Code.SPACE -> handler.postDelayed({
        if (touches[id] === t && !t.done) { t.done = true; t.holding = true; performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); listener?.onSpaceHold(true) }
      }, SPACE_HOLD_MS)
      k.hint != null -> handler.postDelayed({
        if (touches[id] === t && !t.done) { t.done = true; performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); fireHint(k) }
      }, LONG_PRESS_MS)
    }
  }

  /** 抬起:没做过的键这时打;往上划过半个键高打小字;长按空格的松开就是说完了 */
  private fun release(t: Touch, y: Float) {
    if (t.holding) { t.holding = false; listener?.onSpaceHold(false); return }
    if (t.done) return
    t.done = true
    val k = t.slot.key
    when {
      k.code == Code.LIST -> {
        val items = k.items ?: return
        val i = ((t.y - t.slot.rect.top) / t.slot.rect.height() * items.length).toInt().coerceIn(0, items.length - 1)
        listener?.onKey(Key(items[i].toString(), items[i].code, 0f, 0f, 0f, 0f), false)
      }
      k.hint != null && t.y - y > t.slot.rect.height() / 2 -> fireHint(k)
      else -> fire(k)
    }
  }

  private fun fireHint(k: Key) { k.hint?.let { listener?.onKey(Key(it.toString(), it.code, 0f, 0f, 0f, 0f), false) } }

  private fun fire(k: Key) {
    val shifted = shift > 0
    listener?.onKey(k, shifted)
    // 只大写一个:打了字母就复原(功能键、符号不算)
    if (shift == 1 && k.code > 0 && k.code.toChar().isLetter()) shift = 0
  }

  private var repeating: Runnable? = null
  private fun startRepeat(k: Key) {
    stopRepeat()
    val r = object : Runnable { override fun run() { fire(k); handler.postDelayed(this, 45) } }
    repeating = r
    handler.postDelayed(r, 380)
  }
  private fun stopRepeat() { repeating?.let { handler.removeCallbacks(it) }; repeating = null }

  override fun onDetachedFromWindow() { stopRepeat(); handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow() }

  companion object {
    private const val LONG_PRESS_MS = 350L
    private const val SPACE_HOLD_MS = 400L
  }
}
