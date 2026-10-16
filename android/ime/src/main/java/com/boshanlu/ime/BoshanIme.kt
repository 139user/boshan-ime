package com.boshanlu.ime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast

/**
 * 博山输入法的安卓键盘(博山仓库 docs/android.md):系统输入法服务,纯原生 View(键盘要秒开)。独立的博山 App 直接用它,
 * 别的应用(cowork)继承它,经下面的 open 成员加自己的东西(工具栏按钮、面板、长按空格、状态文字),不用改这里。
 * 从上到下:顶栏(工具栏 ImeToolbar,组词时换成候选条 CandidateBar)、主体(键盘 KeyboardView / 符号面板 SymbolPanel /
 * 展开的候选面板 CandidatePanel / 宿主的面板 几选一)、底下避开手势横条的一截。手感照搜狗:
 * - 中文:字母交给拼音引擎 RimeEngine(博山方案),拼音浮在键盘左上角的小标签里;候选条第一个在最左边,空格选它,回车把拼音原样上屏。
 * - 简 / 繁 / 篆:工具栏上点一下轮换;长按开关"候选旁显示小篆"(默认值由管理员在后台定,见 BoshanPrefs)。
 * - 篆图:有选中的字就把它做成小篆图片发出去;否则开关篆图模式,开着时选候选出图片(SealImage)。
 * - 密码框、邮箱、网址框不出中文;数字 / 电话框直接开在数字键盘。中/英的选择记在 SharedPreferences 里。
 */
open class BoshanIme : InputMethodService(), KeyboardView.Listener {
  protected lateinit var theme: ImeTheme
  protected lateinit var toolbar: ImeToolbar
  protected lateinit var candidates: CandidateBar
  protected lateinit var keyboard: KeyboardView
  private lateinit var symbols: SymbolPanel
  private lateinit var panel: CandidatePanel
  private lateinit var frame: ImeFrame
  private lateinit var overlays: List<View>
  private var chinese = true
  /** 这个输入框不许中文(密码、邮箱、网址) */
  private var noChinese = false
  /** 回车要做的编辑动作;0 = 普通回车 */
  private var enterAction = 0
  /** 篆图模式:选候选出图片 */
  private var sealImageMode = false
  private val rimeListener: () -> Unit = { onRimeState() }
  /** 输入框里正摆着我们的组字(拼音) */
  private var composingShown = false
  /** 输入框此刻的选区(宿主做"选中文字改写"之类用) */
  var selection = 0 to 0
    private set

  // ---- 给子类(宿主应用)的扩展点 ----

  /** 加在工具栏状态文字与"篆图"之间的图标按钮 */
  protected open fun extraToolbarButtons(): List<ImeToolbar.Button> = emptyList()
  /** 盖住键盘块的面板(和符号面板一样),用 showBody 切过去 */
  protected open fun extraPanels(): List<View> = emptyList()
  /** 键盘弹出(换了输入框)/ 收起 */
  protected open fun onKeyboardShown(info: EditorInfo) {}
  protected open fun onKeyboardHidden() {}
  /** 状态文字:子类有话说就返回 (文字, 可点),返回 null 用默认的(词库准备中之类) */
  protected open fun statusText(): Pair<String, Boolean>? = null
  /** 点了状态文字;子类处理了返回真 */
  protected open fun onStatusTapped(): Boolean = false
  /** 博山后台的设置地址(取管理员定的默认值);null = 不取(宿主自己写 boshan_defaults.txt) */
  protected open val configUrl: String? = null
  /** 长按空格(搜狗那边是语音);true 开始,false 松开 */
  override fun onSpaceHold(start: Boolean) {}

  override fun onCreate() {
    super.onCreate()
    chinese = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CN, true)
    RimeEngine.onChange(rimeListener)
    RimeEngine.ensureStarted(this)
  }

  override fun onDestroy() {
    RimeEngine.removeListener(rimeListener)
    super.onDestroy()
  }

  override fun onCreateInputView(): View {
    theme = ImeTheme(this)
    toolbar = ImeToolbar(this, theme, extraToolbarButtons()).apply {
      onLang = { toggleLang() }
      onScript = { cycleScript() }
      onScriptLong = { toggleSealHint() }
      onSealImage = { tapSealImage() }
      onSwitch = { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
      onHide = { requestHideSelf(0) }
      onStatus = { if (!onStatusTapped()) defaultStatusTap() }
    }
    val load: (Int, Int) -> Pair<List<RimeEngine.Cand>, Boolean> = { off, n -> RimeEngine.candidates(off, n) }
    candidates = CandidateBar(this, theme).apply {
      loader = load
      onSelect = { i -> pick(i) }
      onExpand = { showPanel(true) }
      visibility = View.GONE
    }
    keyboard = KeyboardView(this, theme).apply { listener = this@BoshanIme }
    symbols = SymbolPanel(this, theme).apply {
      onSymbol = { s -> commitComposition(); currentInputConnection?.commitText(s, 1); if (!locked) showBody(keyboard) }
      onBack = { showBody(keyboard) }
    }
    panel = CandidatePanel(this, theme).apply {
      loader = load
      onSelect = { i -> pick(i) }
      onCollapse = { showPanel(false) }
      onReset = { RimeEngine.clear(); showCandidates(RimeEngine.EMPTY) }
      onDelete = { if (RimeEngine.processKey(RimeJni.KEY_BACKSPACE)) afterRime() }
    }
    val top = FrameLayout(this).apply {
      addView(toolbar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
      addView(candidates, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
    overlays = listOf(symbols, panel) + extraPanels()
    frame = ImeFrame(this, theme, top, keyboard, overlays)
    showBody(keyboard)
    applyMode()
    refreshScript()
    refreshStatus()
    return frame
  }

  /** 应用只给灰底的键盘块让地方;上面拼音小标签那一条透明、点得透 */
  override fun onComputeInsets(outInsets: Insets) {
    super.onComputeInsets(outInsets)
    if (::frame.isInitialized) frame.computeInsets(outInsets)
  }

  /** 横屏也不进全屏编辑模式(那会把输入框搬到键盘上方,很怪) */
  override fun onEvaluateFullscreenMode(): Boolean = false

  override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
    super.onStartInputView(info, restarting)
    val cls = info.inputType and InputType.TYPE_MASK_CLASS
    val variation = info.inputType and InputType.TYPE_MASK_VARIATION
    val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
    val password = (cls == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    noChinese = password || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_URI || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
    val multiLine = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
    val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
    enterAction = if (multiLine || noEnterAction) 0 else action
    keyboard.enterLabel = when (enterAction) {
      EditorInfo.IME_ACTION_SEND -> "发送"
      EditorInfo.IME_ACTION_SEARCH -> "搜索"
      EditorInfo.IME_ACTION_GO -> "前往"
      EditorInfo.IME_ACTION_DONE -> "完成"
      EditorInfo.IME_ACTION_NEXT -> "下一项"
      else -> "↵"
    }
    keyboard.page = if (numeric) Page.NUMBERS else Page.LETTERS
    showBody(keyboard)
    composingShown = false
    RimeEngine.clear()
    applyMode()
    applyPrefs()
    configUrl?.let { url -> BoshanPrefs.fetchDefaults(this, url) { changed -> if (changed) keyboard.post { applyPrefs() } } }
    showCandidates(RimeEngine.EMPTY)
    onKeyboardShown(info)
    refreshStatus()
  }

  override fun onFinishInputView(finishingInput: Boolean) {
    if (composingShown) currentInputConnection?.let { it.setComposingText("", 1); it.finishComposingText() }
    composingShown = false
    RimeEngine.clear()
    showCandidates(RimeEngine.EMPTY)
    onKeyboardHidden()
    super.onFinishInputView(finishingInput)
  }

  /** 人在输入框里点了别处、光标挪走了:正在组的拼音作废(跟搜狗一样) */
  override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    selection = newSelStart to newSelEnd
    if (!composingShown || !composing()) return
    // 只认"组字区还在、光标跑到它外面"这一种:组字区没了(candidatesStart < 0)的通知多半是自己刚上屏那一下迟到的
    if (candidatesStart >= 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
      currentInputConnection?.finishComposingText()
      composingShown = false
      RimeEngine.clear()
      showCandidates(RimeEngine.EMPTY)
    }
  }

  private val useChinese: Boolean get() = chinese && !noChinese

  private fun applyMode() {
    keyboard.chinese = useChinese
    toolbar.setChinese(useChinese)
  }

  private fun toggleLang() {
    if (noChinese) { toast("这个输入框只能打英文"); return }
    commitComposition()
    chinese = !chinese
    getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CN, chinese).apply()
    applyMode()
    refreshStatus()
  }

  // ---- 简 / 繁 / 篆、小篆提示、篆图 ----

  /** 按 用户选择 > 管理员默认 > 方案默认 把小篆提示设到引擎上(键盘每次弹出、后台默认值变了时) */
  private fun applyPrefs() {
    if (!RimeEngine.ready) return
    val want = BoshanPrefs.effectiveSealHint(this)
    if (RimeEngine.getOption("seal_hint") != want) RimeEngine.setOption("seal_hint", want)
    refreshScript()
  }

  private fun currentScript(): String = when {
    RimeEngine.getOption("seal") -> "seal"
    RimeEngine.getOption("traditionalization") -> "traditionalization"
    else -> "simplification"
  }

  private fun refreshScript() {
    if (!::toolbar.isInitialized) return
    val label = when (currentScript()) { "seal" -> SealMap.convert(this, "篆"); "traditionalization" -> "繁"; else -> "简" }
    toolbar.setScript(label, RimeEngine.getOption("seal_hint"))
    toolbar.setSealImage(sealImageMode)
  }

  /** 简 → 繁 → 篆 → 简。选项变化由 lua/boshan/prefs.lua 记进 boshan_prefs.txt */
  private fun cycleScript() {
    if (!RimeEngine.ready) { toast("拼音词库还在准备"); return }
    val next = when (currentScript()) { "simplification" -> "traditionalization"; "traditionalization" -> "seal"; else -> "simplification" }
    for (o in SCRIPTS) RimeEngine.setOption(o, o == next)
    refreshScript()
    if (composing()) showCandidates(RimeEngine.context())
    toast(when (next) { "seal" -> "篆:候选换成小篆"; "traditionalization" -> "繁体"; else -> "简体" })
  }

  private fun toggleSealHint() {
    if (!RimeEngine.ready) return
    val on = !RimeEngine.getOption("seal_hint")
    RimeEngine.setOption("seal_hint", on)
    BoshanPrefs.setUserSealHint(this, on)
    refreshScript()
    toast(if (on) "候选旁显示小篆" else "候选旁不显示小篆")
  }

  private fun tapSealImage() {
    val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
    if (selected.isNotBlank()) { sendSealImage(selected); return }
    sealImageMode = !sealImageMode
    refreshScript()
    toast(if (sealImageMode) "篆图:选字后发出小篆图片" else "篆图已关")
  }

  private fun sendSealImage(text: String) {
    val seal = if (SealMap.hasSeal(text)) text else SealMap.convert(this, text)
    SealImage.deliver(this, currentInputConnection, currentInputEditorInfo, seal)?.let { toast(it) }
  }

  /** 点了第 i 个候选:篆图模式下出图片,否则交给引擎 */
  private fun pick(i: Int) {
    if (sealImageMode) {
      val text = RimeEngine.candidates(i, 1).first.firstOrNull()?.text ?: return
      RimeEngine.clear()
      showCandidates(RimeEngine.EMPTY)
      sendSealImage(text)
      return
    }
    if (RimeEngine.selectAt(i)) afterRime()
  }

  /** 键盘一直在底下;符号面板、候选面板、宿主的面板盖在上面 */
  protected fun showBody(v: View) {
    for (x in overlays) x.visibility = if (x === v) View.VISIBLE else View.GONE
  }

  /** 候选面板展开 / 收起 */
  private fun showPanel(open: Boolean) {
    if (open && composing()) { panel.refresh(); showBody(panel) } else showBody(keyboard)
  }

  // ---- 按键 ----

  override fun onKey(key: Key, shifted: Boolean) {
    val ic = currentInputConnection ?: return
    when (key.code) {
      Code.DEL -> if (composing()) { if (RimeEngine.processKey(RimeJni.KEY_BACKSPACE)) afterRime() } else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
      Code.SPACE -> if (composing()) { if (sealImageMode) pick(lastCtx.highlight) else if (RimeEngine.processKey(RimeJni.KEY_SPACE)) afterRime() } else ic.commitText(" ", 1)
      Code.ENTER -> if (composing()) { if (RimeEngine.processKey(RimeJni.KEY_RETURN)) afterRime() } else enter(ic)
      Code.SYM -> { commitComposition(); symbols.reset(); showBody(symbols) }
      Code.NUM -> { commitComposition(); keyboard.page = Page.NUMBERS }
      Code.ABC -> { commitComposition(); keyboard.page = Page.LETTERS }
      Code.LANG -> toggleLang()
      // 分词:组着词时插音节分隔符(xian → xi'an);英文时它是 Shift,键盘自己处理了
      Code.SPLIT -> if (composing() && RimeEngine.processKey('\''.code)) afterRime()
      Code.SHIFT -> {}
      else -> typeChar(ic, key.code.toChar(), shifted)
    }
  }

  private fun typeChar(ic: InputConnection, c: Char, shifted: Boolean) {
    if (c.isLetter() && keyboard.page == Page.LETTERS) {
      // 中文模式:小写字母交给拼音引擎;大写(按了 Shift)直接上屏,先把正在组的词定下来
      if (useChinese && !shifted && RimeEngine.ready) {
        if (RimeEngine.processKey(c.code)) { afterRime(); return }
      }
      commitComposition()
      ic.commitText(if (shifted) c.uppercase() else c.toString(), 1)
      return
    }
    // 数字、标点:组着词时先上屏当前候选;中文模式下字母页上的标点换全角(数字键盘上的照原样)
    commitComposition()
    val text = if (useChinese && keyboard.page == Page.LETTERS && ImeKeys.isPunct(c.code)) ImeKeys.chinesePunct(c) else c.toString()
    ic.commitText(text, 1)
  }

  /** 按回车:输入框有"发送 / 搜索"之类的动作就做那个动作,否则发回车键 */
  protected fun enter(ic: InputConnection) {
    if (enterAction != 0) ic.performEditorAction(enterAction) else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
  }

  open fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

  private var lastCtx: RimeEngine.Ctx = RimeEngine.EMPTY
  protected fun composing(): Boolean = lastCtx.composing

  /** 组着词时按了别的(切页、切英文、标点):把第一个候选上屏,选完还有剩的拼音就原样上屏 */
  protected fun commitComposition() {
    if (!composing()) return
    RimeEngine.select(lastCtx.highlight)
    val got = RimeEngine.takeCommit()
    val rest = RimeEngine.context()
    val text = (got ?: "") + rest.input
    currentInputConnection?.commitText(text, 1)
    composingShown = false
    RimeEngine.clear()
    showCandidates(RimeEngine.EMPTY)
  }

  /** 引擎处理完一个键之后:有定下来的字就上屏(顶掉拼音),再刷拼音与候选 */
  private fun afterRime() {
    RimeEngine.takeCommit()?.let { if (it.isNotEmpty()) { currentInputConnection?.commitText(it, 1); composingShown = false } }
    showCandidates(RimeEngine.context())
  }

  private fun showCandidates(c: RimeEngine.Ctx) {
    lastCtx = c
    if (!::frame.isInitialized) return
    val ic = currentInputConnection
    if (c.composing) {
      // 拼音浮在键盘左上角的小标签里(PINYIN_IN_EDITOR 时以组字放进输入框);篆图模式下前面标出来
      val py = c.preedit.replace(' ', '\'')
      if (ImeTheme.PINYIN_IN_EDITOR) { ic?.setComposingText(py, 1); composingShown = true; frame.setPinyin(if (sealImageMode) "篆图" else "") }
      else frame.setPinyin(if (sealImageMode) "篆图 · $py" else py)
      keyboard.composing = true
      candidates.refresh()
      if (panel.visibility == View.VISIBLE) panel.refresh()
      candidates.visibility = View.VISIBLE
      toolbar.visibility = View.GONE
    } else {
      if (composingShown) { ic?.setComposingText("", 1); ic?.finishComposingText(); composingShown = false }
      frame.setPinyin("")
      keyboard.composing = false
      if (panel.visibility == View.VISIBLE) showBody(keyboard)
      candidates.visibility = View.GONE
      toolbar.visibility = View.VISIBLE
    }
  }

  // ---- 状态文字 ----

  private fun onRimeState() {
    if (RimeEngine.ready) applyPrefs()
    refreshStatus()
  }

  open fun refreshStatus() {
    if (!::toolbar.isInitialized) return
    val mine = statusText()
    when {
      mine != null -> toolbar.setStatus(mine.first, actionable = mine.second)
      useChinese && RimeEngine.state == RimeEngine.State.PREPARING -> toolbar.setStatus("词库准备中(第一次要一两分钟),先用英文")
      useChinese && RimeEngine.state == RimeEngine.State.FAILED -> toolbar.setStatus("拼音引擎起不来:${RimeEngine.message}")
      else -> toolbar.setStatus("")
    }
  }

  private fun defaultStatusTap() {}

  companion object {
    private const val PREFS = "boshan_ime"
    private const val KEY_CN = "chinese"
    private val SCRIPTS = listOf("simplification", "traditionalization", "seal")
    private val PASSWORD_VARIATIONS = setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
  }
}
