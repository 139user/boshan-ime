package com.boshanlu.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 符号面板(按"符"进,照搜狗截图):盖住工具栏加键盘那一整块。左边一列:分类(可滚)、锁、返回;右边白底四列的符号格子,往下滚。
 * 点一个符号就打出去并回到字母键盘;锁上了就留在这里连着打。尺寸是参考像素(见 ImeTheme)。
 */
class SymbolPanel(ctx: Context, private val theme: ImeTheme) : LinearLayout(ctx) {
  /** 打出这个符号(原样,不再转全角) */
  var onSymbol: ((String) -> Unit)? = null
  var onBack: (() -> Unit)? = null
  /** 锁定:打完不回字母键盘 */
  var locked = false
    private set

  private class Cat(val name: String, val items: List<String>)
  private val cats = listOf(
    Cat("常用", listOf("——", "，", "～", "+", "！", "@", "=", "《", "!", "》", "&", "^", "/", ".", "«", "*", "：", "_", "\"", "。",
      "？", "、", "；", "（", "）", "“", "”", "‘", "’", "…", "·", "#", "%", "-", "\\", "|", "~", "$", "￥", "【", "】")),
    Cat("中文", split("，。？！、：；“”‘’（）《》〈〉【】「」『』〔〕…—～·￥") + listOf("……", "——")),
    Cat("英文", split(",.?!:;'\"()[]{}<>@#\$%^&*-+=_/\\|~`")),
    Cat("数学", split("+-×÷=≠≈±<>≤≥%‰√∞°∑π∫∈∩∪⊂∵∴")),
    Cat("特殊", split("★☆●○◆◇■□▲△※→←↑↓✓✔✗©®™§¶†‡")),
    Cat("网址", listOf(".com", ".cn", "www.", "http://", "https://", "@qq.com", "@163.com", "@gmail.com", "/", ":", "-", "_")),
  )
  private val tabs = LinearLayout(ctx).apply { orientation = VERTICAL }
  private val grid = GridLayout(ctx).apply { columnCount = COLS; setBackgroundColor(theme.divider) }
  private val scroll = ScrollView(ctx).apply {
    isVerticalScrollBarEnabled = false; addView(grid)
    background = GradientDrawable().apply { setColor(theme.panel); cornerRadius = theme.r(ImeTheme.RADIUS) }
    clipToOutline = true
  }

  /** 锁:画一把挂锁,开着 / 锁上 */
  private val lock = object : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = theme.r(2.6f); color = theme.text; strokeCap = Paint.Cap.ROUND }
    override fun onDraw(c: Canvas) {
      val cx = width / 2f; val cy = height / 2f + theme.r(5f)
      val body = RectF(cx - theme.r(15f), cy - theme.r(9f), cx + theme.r(15f), cy + theme.r(13f))
      c.drawRoundRect(body, theme.r(4f), theme.r(4f), p)
      val shackle = RectF(cx - theme.r(10f), cy - theme.r(27f), cx + theme.r(10f), cy - theme.r(7f))
      if (locked) c.drawArc(shackle, 180f, 180f, false, p) else c.drawArc(RectF(shackle).apply { offset(theme.r(4f), -theme.r(3f)) }, 180f, 160f, false, p)
      if (locked) { c.drawLine(shackle.left, shackle.centerY(), shackle.left, body.top, p); c.drawLine(shackle.right, shackle.centerY(), shackle.right, body.top, p) }
      else c.drawLine(shackle.left, shackle.centerY(), shackle.left, body.top, p)
    }
  }.apply {
    background = keyBg(theme.keySpecial)
    setOnClickListener { locked = !locked; refreshLock() }
  }

  init {
    orientation = HORIZONTAL
    setBackgroundColor(theme.background)
    setPadding(theme.ri(8f), theme.ri(4f), theme.ri(10f), theme.ri(4f))
    val left = LinearLayout(ctx).apply { orientation = VERTICAL }
    val tabScroll = ScrollView(ctx).apply {
      isVerticalScrollBarEnabled = false; addView(tabs)
      background = keyBg(theme.keySpecial); clipToOutline = true
    }
    left.addView(tabScroll, LayoutParams(LayoutParams.MATCH_PARENT, theme.ri(286f)))
    left.addView(lock, LayoutParams(LayoutParams.MATCH_PARENT, theme.ri(92f)).apply { topMargin = theme.ri(9f) })
    left.addView(TextView(ctx).apply {
      text = "返回"; setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.r(ImeTheme.FUNC)); gravity = Gravity.CENTER; setTextColor(theme.text)
      background = keyBg(theme.keySpecial)
      setOnClickListener { onBack?.invoke() }
    }, LayoutParams(LayoutParams.MATCH_PARENT, theme.ri(92f)).apply { topMargin = theme.ri(9f) })
    addView(left, LayoutParams(theme.ri(108f), LayoutParams.MATCH_PARENT))
    addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = theme.ri(8f) })
    cats.forEachIndexed { i, c ->
      tabs.addView(TextView(ctx).apply {
        text = c.name; setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.r(ImeTheme.FUNC)); gravity = Gravity.CENTER; setTextColor(theme.text)
        setOnClickListener { select(i) }
      }, LayoutParams(LayoutParams.MATCH_PARENT, theme.ri(96f)))
    }
    select(0)
  }

  /** 每次进来:回到第一类、解除锁定 */
  fun reset() { locked = false; refreshLock(); select(0) }

  private fun select(i: Int) {
    for (k in 0 until tabs.childCount) tabs.getChildAt(k).setBackgroundColor(if (k == i) theme.tabSelected else 0)
    grid.removeAllViews()
    val cellH = theme.ri(96f)
    val line = theme.ri(2f)
    for (s in cats[i].items) {
      grid.addView(TextView(context).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.r(if (s.length > 2) ImeTheme.FUNC else ImeTheme.SYMBOL))
        gravity = Gravity.CENTER; setTextColor(theme.text); setBackgroundColor(theme.panel)
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onSymbol?.invoke(s) }
      }, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
        width = 0; height = cellH; setMargins(0, 0, line, line)
      })
    }
    scroll.scrollTo(0, 0)
  }

  private fun refreshLock() { lock.invalidate() }

  private fun keyBg(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = theme.r(ImeTheme.RADIUS) }

  companion object {
    private const val COLS = 4
    /** 一串字符拆成一个个(都是单个 UTF-16 单元的符号) */
    private fun split(s: String): List<String> = s.map { it.toString() }
  }
}
