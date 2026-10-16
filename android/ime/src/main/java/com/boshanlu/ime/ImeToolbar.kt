package com.boshanlu.ime

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 键盘上方的工具栏(没在组词时显示),照搜狗横着均分放:
 *   切换输入法 · 中/英 · 简/繁/篆 · 状态文字(占两格)· [宿主加的按钮] · 篆图 · 收起
 * - 简/繁/篆:点一下轮换;长按开关"候选旁显示小篆"。
 * - 篆图:有选中的字就把它做成小篆图片;否则开 / 关篆图模式(开着时选候选出图片而不是文字),开着时变主色。
 * - 状态文字(词库准备、宿主的提示)可点,点了做什么由 BoshanIme(或子类)定。
 * 宿主(比如 cowork 的语音、说到电脑、剪贴板)经 BoshanIme.extraToolbarButtons 加图标按钮,排在状态文字和篆图之间。
 */
class ImeToolbar(ctx: Context, private val theme: ImeTheme, extra: List<Button>) : LinearLayout(ctx) {
  /** 宿主加的图标按钮;tint / scale 可以随时改 */
  class Button(val icon: Int, val desc: String, val click: () -> Unit) {
    internal var view: ImageButton? = null
    fun tint(color: Int) { view?.imageTintList = ColorStateList.valueOf(color) }
    fun scale(s: Float) { view?.let { it.scaleX = s; it.scaleY = s } }
  }

  var onLang: (() -> Unit)? = null
  var onScript: (() -> Unit)? = null
  var onScriptLong: (() -> Unit)? = null
  var onSealImage: (() -> Unit)? = null
  var onSwitch: (() -> Unit)? = null
  var onHide: (() -> Unit)? = null
  var onStatus: (() -> Unit)? = null

  private fun iconButton(res: Int, desc: String, click: () -> Unit) = ImageButton(context).apply {
    setImageResource(res); background = null
    scaleType = ImageView.ScaleType.CENTER_INSIDE
    val p = (theme.r(ImeTheme.TOP_H) - theme.r(32f)).toInt() / 2
    setPadding(0, p, 0, p)
    imageTintList = ColorStateList.valueOf(theme.icon)
    contentDescription = desc
    setOnClickListener { click() }
  }

  private fun textButton(desc: String, click: () -> Unit) = TextView(context).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.r(ImeTheme.FUNC))
    setTextColor(theme.icon); gravity = Gravity.CENTER
    contentDescription = desc
    setOnClickListener { click() }
  }

  private val switch = iconButton(R.drawable.ic_ime_keyboard, "切换输入法") { onSwitch?.invoke() }
  private val lang = textButton("中英切换") { onLang?.invoke() }
  private val script = textButton("简繁篆切换,长按开关小篆提示") { onScript?.invoke() }.apply {
    setOnLongClickListener { onScriptLong?.invoke(); true }
  }
  private val status = TextView(ctx).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.r(24f))
    setTextColor(theme.textMuted); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
    gravity = Gravity.CENTER
    setOnClickListener { onStatus?.invoke() }
  }
  private val sealImage = textButton("篆图:把字做成小篆图片") { onSealImage?.invoke() }.apply {
    text = "篆图"
    setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.r(ImeTheme.FUNC) * 0.85f)
  }
  private val hide = iconButton(R.drawable.ic_ime_hide, "收起键盘") { onHide?.invoke() }

  init {
    orientation = HORIZONTAL
    setBackgroundColor(theme.background)
    fun slot(v: View, w: Float = 1f) = addView(v, LayoutParams(0, LayoutParams.MATCH_PARENT, w))
    slot(switch); slot(lang); slot(script); slot(status, 2f)
    for (b in extra) { val v = iconButton(b.icon, b.desc, b.click); b.view = v; slot(v) }
    slot(sealImage); slot(hide)
  }

  fun setChinese(cn: Boolean) { lang.text = if (cn) "中" else "英" }

  /** 简 / 繁 / 篆;篆字用博山篆字体显示 */
  fun setScript(label: String, sealHint: Boolean) {
    script.text = label
    script.typeface = SealFont.typeface(context)
    // 小篆提示开着时字下面带一道主色的线
    script.paintFlags = if (sealHint) script.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
      else script.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
  }

  fun setSealImage(on: Boolean) { sealImage.setTextColor(if (on) theme.accent else theme.icon) }

  fun setStatus(text: String, actionable: Boolean = false) {
    status.text = text
    status.setTextColor(if (actionable) theme.accent else theme.textMuted)
  }
}
