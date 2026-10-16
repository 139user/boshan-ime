package com.boshanlu.ime.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.boshanlu.ime.BoshanPrefs
import com.boshanlu.ime.SealFont
import com.boshanlu.ime.SealMap

/**
 * 博山输入法的设置页(桌面图标点进来的就是它):启用 / 切换输入法的两步、候选旁显示小篆的开关、用法说明、试打框、关于。
 * 键盘在另一个进程(:ime),这里改开关只写 boshan_prefs.txt,键盘下次弹出时读(BoshanIme.applyPrefs)。
 */
class SettingsActivity : Activity() {
  private lateinit var enableStatus: TextView
  private lateinit var selectStatus: TextView
  private lateinit var sealSwitch: Switch
  private lateinit var adminNote: TextView

  private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

  private fun text(s: CharSequence, size: Float = 15f, color: Int = Color.parseColor("#333333"), bold: Boolean = false) = TextView(this).apply {
    text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, size); setTextColor(color)
    if (bold) typeface = Typeface.DEFAULT_BOLD
    setPadding(0, dp(4), 0, dp(4))
  }

  private fun card(vararg views: View) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(12), dp(16), dp(12))
    background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() }
    for (v in views) addView(v)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val col = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(16), dp(32), dp(16), dp(32))
      setBackgroundColor(Color.parseColor("#F6EFDF"))
    }
    fun add(v: View) = col.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })

    add(LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      addView(text(SealMap.convert(this@SettingsActivity, "博山"), 40f, Color.parseColor("#B22D1E")).apply { typeface = SealFont.typeface(this@SettingsActivity) })
      addView(text("  博山输入法", 24f, Color.parseColor("#2B2118"), bold = true))
    })

    enableStatus = text("")
    selectStatus = text("")
    add(card(
      text("第一步:在系统里启用", 16f, bold = true), enableStatus,
      Button(this).apply { text = "打开输入法设置"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } },
      text("第二步:切换成博山输入法", 16f, bold = true), selectStatus,
      Button(this).apply { text = "选择输入法"; setOnClickListener { imm().showInputMethodPicker() } },
    ))

    sealSwitch = Switch(this).apply {
      text = "打字时在候选旁显示常用字的小篆"
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      setOnCheckedChangeListener { _, on -> if (isPressed) { BoshanPrefs.setUserSealHint(this@SettingsActivity, on); refresh() } }
    }
    adminNote = text("", 13f, Color.parseColor("#888888"))
    add(card(text("小篆提示", 16f, bold = true), sealSwitch, adminNote))

    add(card(
      text("怎么用", 16f, bold = true),
      text("· 简 / 繁 / 篆:点键盘工具栏上的「简」轮换;长按它开关小篆提示。篆模式下打出的是 Unicode 18.0 的小篆字符(常用 3500 字)。"),
      text("· 篆图:点工具栏上的「篆图」,之后选的字会做成小篆图片发出去;输入框里先选中一段字再点,直接把那段做成图片。"),
      text("· 典籍联想:道德经、论语、孟子、荀子、礼记(含学记)、周易、管子,从一句的开头起打每个字拼音的首字母就能出整句,比如打 hwzcmxmcsjmcz 出「后王之成名：刑名从商，爵名从周」。zh / ch / sh 只打 z / c / s。"),
    ))

    add(card(text("试一试", 16f, bold = true), EditText(this).apply { hint = "在这里试打" ; minLines = 3 }))

    add(text("版本 ${BuildConfig.VERSION_NAME}\n基于 Rime(librime)与雾凇拼音;小篆字形来自开元小篆(SIL OFL 1.1),简繁与小篆对照来自 OpenCC。", 12f, Color.parseColor("#888888")))

    setContentView(ScrollView(this).apply { addView(col) })
  }

  override fun onResume() {
    super.onResume()
    refresh()
    // 顺手取一次管理员定的默认值
    BoshanPrefs.fetchDefaults(this, ImeService.CONFIG_URL, force = true) { runOnUiThread { refresh() } }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) refresh() // 从输入法选择框回来
  }

  private fun imm() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

  private fun refresh() {
    val enabled = imm().enabledInputMethodList.any { it.packageName == packageName }
    val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
    enableStatus.text = if (enabled) "✓ 已启用" else "还没启用:点下面的按钮,在列表里打开「博山输入法」"
    selectStatus.text = if (current.startsWith("$packageName/")) "✓ 正在用博山输入法" else "现在用的不是博山输入法"
    sealSwitch.isChecked = BoshanPrefs.effectiveSealHint(this)
    val admin = BoshanPrefs.adminSealHint(this)
    val mine = BoshanPrefs.userSealHint(this)
    adminNote.text = buildString {
      append("管理员定的默认:").append(when (admin) { true -> "显示"; false -> "不显示"; null -> "(还没取到,按显示)" })
      if (mine != null) append(";你自己改过,以你的为准")
    }
  }
}
