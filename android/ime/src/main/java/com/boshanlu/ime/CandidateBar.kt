package com.boshanlu.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 候选条(照搜狗截图):组词时顶在键盘上面(替掉工具栏)。第一个候选就在最左边(蓝字),往右一路横滑,滑到头自动接着取(不分页);
 * 最右边一道细竖线隔开一个 › ,点它展开成整块候选面板(CandidatePanel)。拼音不在这里(浮在键盘左上角的小标签里,见 BoshanIme)。
 */
class CandidateBar(ctx: Context, private val theme: ImeTheme) : LinearLayout(ctx) {
  /** 选了第几个(从头数) */
  var onSelect: ((Int) -> Unit)? = null
  var onExpand: (() -> Unit)? = null
  /** 从 offset 起取 limit 个候选;second = 后面还有 */
  var loader: ((Int, Int) -> Pair<List<RimeEngine.Cand>, Boolean>)? = null

  private val row = LinearLayout(ctx).apply { orientation = HORIZONTAL; setPadding(theme.ri(7f), 0, 0, 0) }
  private val scroll = object : HorizontalScrollView(ctx) {
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
      super.onScrollChanged(l, t, oldl, oldt)
      // 快滑到头了:接着取下一批
      if (more && l + width > row.width - width / 2) loadMore()
    }
  }.apply { isHorizontalScrollBarEnabled = false; addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
  private var count = 0
  private var more = false

  /** 右边的 › :参考截图 12×23、灰色 */
  private val expand = object : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = theme.r(2.6f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = theme.textMuted }
    override fun onDraw(c: Canvas) {
      val cx = width / 2f; val cy = height / 2f; val w = theme.r(6f); val h = theme.r(11.5f)
      c.drawLine(cx - w, cy - h, cx + w, cy, p)
      c.drawLine(cx + w, cy, cx - w, cy + h, p)
    }
  }.apply { setOnClickListener { onExpand?.invoke() } }

  init {
    orientation = HORIZONTAL
    setBackgroundColor(theme.background)
    addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    addView(View(ctx).apply { setBackgroundColor(theme.keyShadow) }, LayoutParams(theme.ri(2f), theme.ri(34f)).apply { gravity = Gravity.CENTER_VERTICAL })
    addView(expand, LayoutParams(theme.ri(114f), LayoutParams.MATCH_PARENT))
  }

  /** 拼音变了:从头重取 */
  fun refresh() {
    row.removeAllViews()
    count = 0
    more = true
    loadMore()
    scroll.scrollTo(0, 0)
  }

  private fun loadMore() {
    val (items, hasMore) = loader?.invoke(count, BATCH) ?: (emptyList<RimeEngine.Cand>() to false)
    more = hasMore
    for (cand in items) {
      val i = count++
      row.addView(TextView(context).apply {
        this.text = CandText.format(cand, theme)
        typeface = SealFont.typeface(context)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.r(ImeTheme.CANDIDATE))
        setTextColor(if (i == 0) theme.firstCandidate else theme.candidate)
        gravity = Gravity.CENTER
        setPadding(theme.ri(29f), 0, theme.ri(29f), 0)
        setOnClickListener { onSelect?.invoke(i) }
      }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
  }

  companion object { private const val BATCH = 30 }
}
