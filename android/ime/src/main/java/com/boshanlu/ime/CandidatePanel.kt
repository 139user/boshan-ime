package com.boshanlu.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 展开的候选面板(候选条上点 ›,照搜狗截图):盖住候选条加键盘那一整块。左边白底格子(四格一行,长词占两格或整行,格子间细线),
 * 往下滚,滚到底自动接着取;右边一列 返回 / 退格 / 重输。尺寸是参考像素(见 ImeTheme)。
 */
class CandidatePanel(ctx: Context, private val theme: ImeTheme) : LinearLayout(ctx) {
  var onSelect: ((Int) -> Unit)? = null
  var onCollapse: (() -> Unit)? = null
  var onReset: (() -> Unit)? = null
  var onDelete: (() -> Unit)? = null
  var loader: ((Int, Int) -> Pair<List<RimeEngine.Cand>, Boolean>)? = null

  private val grid = GridLayout(ctx).apply { columnCount = COLS; setBackgroundColor(theme.divider) }
  private val scroll = object : ScrollView(ctx) {
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
      super.onScrollChanged(l, t, oldl, oldt)
      if (more && t + height > grid.height - height / 2) loadMore()
    }
  }.apply {
    isVerticalScrollBarEnabled = false; addView(grid)
    background = GradientDrawable().apply { setColor(theme.panel); cornerRadius = theme.r(ImeTheme.RADIUS) }
    clipToOutline = true
  }
  private var count = 0
  private var more = false

  init {
    orientation = HORIZONTAL
    setBackgroundColor(theme.background)
    setPadding(theme.ri(10f), theme.ri(4f), theme.ri(9f), theme.ri(4f))
    addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = theme.ri(7f) })
    // 右列:参考截图 5 格(返回、退格、重输、拼/笔、全/字),后两样我们没有,留空占位让前三个跟搜狗一样大
    val side = LinearLayout(ctx).apply { orientation = VERTICAL }
    side.addView(button("返回") { onCollapse?.invoke() }, cell())
    side.addView(button("⌫") { onDelete?.invoke() }, cell())
    side.addView(button("重输") { onReset?.invoke() }, cell())
    side.addView(View(ctx), cell())
    side.addView(View(ctx), cell())
    addView(side, LayoutParams(theme.ri(108f), LayoutParams.MATCH_PARENT))
  }

  private fun cell() = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, theme.ri(4f), 0, theme.ri(4f)) }

  private fun button(label: String, click: () -> Unit) = TextView(context).apply {
    text = label; setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.r(ImeTheme.PANEL_BTN)); gravity = Gravity.CENTER; setTextColor(theme.text)
    background = GradientDrawable().apply { setColor(theme.keySpecial); cornerRadius = theme.r(ImeTheme.RADIUS) }
    setOnClickListener { click() }
  }

  /** 打开时、拼音变了时从头取 */
  fun refresh() {
    grid.removeAllViews()
    count = 0
    more = true
    loadMore()
    scroll.scrollTo(0, 0)
  }

  private fun loadMore() {
    val (items, hasMore) = loader?.invoke(count, BATCH) ?: (emptyList<RimeEngine.Cand>() to false)
    more = hasMore
    val h = theme.ri(97f)
    val line = theme.ri(1f).coerceAtLeast(1)
    for (cand in items) {
      val i = count++
      val n = cand.text.length + (if (cand.comment.isEmpty()) 0 else cand.comment.length / 2 + 1)
      val span = when { n > 4 -> COLS; n > 2 -> 2; else -> 1 }
      grid.addView(TextView(context).apply {
        this.text = CandText.format(cand, theme)
        typeface = SealFont.typeface(context)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.r(ImeTheme.CANDIDATE))
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(theme.ri(8f), 0, theme.ri(8f), 0)
        setTextColor(if (i == 0) theme.firstCandidate else theme.candidate)
        setBackgroundColor(theme.panel)
        gravity = Gravity.CENTER
        setOnClickListener { onSelect?.invoke(i) }
      }, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, span, span.toFloat())).apply {
        width = 0; height = h; setMargins(0, 0, line, line)
      })
    }
  }

  companion object {
    private const val COLS = 4
    private const val BATCH = 40
  }
}
