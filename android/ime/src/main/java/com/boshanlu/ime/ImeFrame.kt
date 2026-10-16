package com.boshanlu.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 键盘的整体框架(照搜狗截图):从上到下
 * - 拼音小标签那一条(透明,浮在应用上面、不占应用的地方、点得透 —— 见 computeInsets):组词时左边露出一个灰底小标签写着拼音(ju'z);
 * - 键盘块(灰底):顶栏(工具栏 / 候选条,TOP_H)+ 键区(KEYS_H);符号面板、展开的候选面板盖住这一整块;
 * - 底下让开导航栏 / 手势横条(系统给多少让多少,小米把切换输入法、剪贴板两个按钮画在这里)。
 */
class ImeFrame(ctx: Context, private val theme: ImeTheme, top: View, keyboard: View, overlays: List<View>) : LinearLayout(ctx) {
  private val pinyin = TextView(ctx).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.r(ImeTheme.TAB_TEXT))
    setTextColor(theme.text); gravity = Gravity.CENTER_VERTICAL; maxLines = 1
    setPadding(theme.ri(32f), 0, theme.ri(32f), 0)
    minWidth = theme.ri(114f)
    background = GradientDrawable().apply {
      setColor(theme.background)
      val r = theme.r(ImeTheme.RADIUS)
      cornerRadii = floatArrayOf(0f, 0f, r, r, 0f, 0f, 0f, 0f)
    }
    visibility = View.INVISIBLE
  }
  private val strip = FrameLayout(ctx).apply {
    addView(pinyin, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START))
  }
  /** 灰底的那一整块(含底下让开的一截) */
  val block: FrameLayout = FrameLayout(ctx).apply {
    setBackgroundColor(theme.background)
    val stack = LinearLayout(ctx).apply {
      orientation = VERTICAL
      addView(top, LayoutParams(LayoutParams.MATCH_PARENT, theme.ri(ImeTheme.TOP_H)))
      addView(keyboard, LayoutParams(LayoutParams.MATCH_PARENT, theme.ri(ImeTheme.KEYS_H)))
    }
    addView(stack, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    val h = theme.ri(ImeTheme.TOP_H + ImeTheme.KEYS_H)
    for (o in overlays) addView(o, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, h))
    setOnApplyWindowInsetsListener { v, insets ->
      val nav = if (android.os.Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        else @Suppress("DEPRECATION") insets.systemWindowInsetBottom
      v.setPadding(0, 0, 0, nav)
      insets
    }
  }

  init {
    orientation = VERTICAL
    addView(strip, LayoutParams(LayoutParams.MATCH_PARENT, theme.ri(ImeTheme.TAB_H)))
    addView(block, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
  }

  /** 拼音小标签:空串收起 */
  fun setPinyin(text: String) {
    pinyin.text = text
    pinyin.visibility = if (text.isEmpty()) View.INVISIBLE else View.VISIBLE
  }

  /** 应用只给键盘块让地方,上面那条透明的点得透(InputMethodService.onComputeInsets 里调) */
  fun computeInsets(out: InputMethodService.Insets) {
    if (!isShown) return
    val loc = IntArray(2)
    block.getLocationInWindow(loc)
    out.contentTopInsets = loc[1]
    out.visibleTopInsets = loc[1]
    out.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE
  }
}
