package com.boshanlu.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * 键盘配色(跟系统深浅色走)与尺寸;View 都从这里取,不碰资源文件。
 * 尺寸、颜色、字号照用户手机上搜狗 26 键的截图逐项量的(docs/android.md):单位是"参考像素",截图宽 706,竖屏时屏宽就是 706 个参考像素,
 * r() 换成真像素 —— 跟搜狗一样随屏宽等比缩放。横屏按屏幕短边算,不会变得巨大。
 */
class ImeTheme(ctx: Context) {
  val dark: Boolean = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
  private val density = ctx.resources.displayMetrics.density
  private val scale: Float = ctx.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) } / REF_W
  fun dp(v: Float): Float = v * density
  fun dpi(v: Int): Int = (v * density + 0.5f).toInt()
  /** 参考像素 → 真像素 */
  fun r(v: Float): Float = v * scale
  fun ri(v: Float): Int = (v * scale + 0.5f).toInt()

  private fun c(light: String, darkHex: String) = Color.parseColor(if (dark) darkHex else light)
  val background = c("#EEEFF3", "#1E1F22")
  val key = c("#FFFFFF", "#3C3D41")
  val keySpecial = c("#DDE2E8", "#2B2C30")
  val keyPressed = c("#C9D0D8", "#55575D")
  val keyShadow = c("#D0D3D9", "#141518")
  /** 回车有动作(搜索 / 发送……)时、第一个候选的颜色 */
  val keyActive = c("#3482FF", "#3B74E0")
  val firstCandidate = c("#3C81F2", "#7AA7FF")
  val text = c("#151618", "#F1F2F4")
  val hint = c("#B9BBBA", "#7D828A")
  val icon = c("#60656B", "#B4B9C0")
  val textMuted = c("#6B6F77", "#9AA0A8")
  val accent = c("#2563EB", "#7AA7FF")
  /** 面板(候选格子、符号格子)的底色与分隔线、符号面板选中的分类 */
  val panel = c("#FFFFFF", "#2E2F33")
  val divider = c("#F0F0F2", "#3A3B40")
  val tabSelected = c("#C9D0D8", "#45474D")
  val danger: Int = Color.parseColor("#E5484D")
  val candidate = text

  companion object {
    const val REF_W = 706f
    /** 候选条 / 工具栏高(参考像素) */
    const val TOP_H = 84f
    /** 键区高:四行键 + 底下一点(键区从候选条下沿算起) */
    const val KEYS_H = 412f
    /** 拼音小标签那一条的高(浮在键盘上面,不占应用的地方) */
    const val TAB_H = 46f
    const val RADIUS = 9f
    /** 字号(参考像素):字母、键顶小字、中文功能键、123、中/英的大字与小字、逗号句号、回车上的字、数字键盘的数字 */
    const val LETTER = 35f
    const val HINT = 18f
    const val FUNC = 27f
    const val FUNC_LATIN = 28f
    const val LANG_BIG = 30f
    const val LANG_SMALL = 17f
    const val PUNCT = 34f
    const val ENTER = 28f
    const val DIGIT = 38f
    /** 候选字、符号格子、面板按钮、拼音小标签 */
    const val CANDIDATE = 36f
    const val SYMBOL = 34f
    const val PANEL_BTN = 27f
    const val TAB_TEXT = 30f
    /** 拼音放在哪:false = 像搜狗那样浮在键盘左上角的小标签里;true = 以组字(带下划线)直接放进输入框 */
    const val PINYIN_IN_EDITOR = false
  }
}
