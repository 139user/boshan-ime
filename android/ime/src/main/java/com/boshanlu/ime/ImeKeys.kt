package com.boshanlu.ime

/**
 * 键盘布局(docs/android.md),照搜狗 26 键在用户手机上的截图逐个量的:坐标是"参考像素"(截图宽 706,竖屏屏宽 = 706),
 * 运行时按 ImeTheme.r() 等比缩放。y 从键区顶上(候选条 / 工具栏下沿)算起,键区高 ImeTheme.KEYS_H。
 * code > 0 是要打出来的字符;< 0 是功能键(见 Code)。hint 是键顶上的小字:上划或长按打它。
 * items:一个键里竖着排的几个符号(数字键盘左边那一列),按的位置决定打哪个。
 */
class Key(
  val label: String, val code: Int,
  val x0: Float, val y0: Float, val x1: Float, val y1: Float,
  val hint: Char? = null, val items: String? = null,
) {
  val special: Boolean get() = code < 0
}

object Code {
  const val SHIFT = -1
  const val DEL = -2
  /** 符号面板(SymbolPanel) */
  const val SYM = -3
  /** 回字母页 */
  const val ABC = -4
  const val LANG = -6
  const val SPACE = -7
  const val ENTER = -8
  /** 数字键盘 */
  const val NUM = -9
  /** 分词(中文时插音节分隔符 ',英文时当 Shift 用) */
  const val SPLIT = -10
  /** 一键多符号(items) */
  const val LIST = -11
}

enum class Page { LETTERS, NUMBERS }

object ImeKeys {
  /** 字母页四行的上下沿 */
  private val ROWS = listOf(3f to 91f, 110f to 198f, 215f to 303f, 322f to 410f)
  /** 数字键盘四行的上下沿(键比字母页高、缝更窄) */
  private val NROWS = listOf(2f to 97f, 106f to 201f, 210f to 305f, 314f to 410f)

  private fun row(r: Int, chars: String, x: Float, pitch: Float, w: Float, hints: String? = null) =
    chars.mapIndexed { i, c ->
      val l = x + i * pitch
      Key(c.toString(), c.code, l, ROWS[r].first, l + w, ROWS[r].second, hints?.getOrNull(i))
    }

  private fun k(label: String, code: Int, r: Int, x0: Float, x1: Float) = Key(label, code, x0, ROWS[r].first, x1, ROWS[r].second)

  val LETTERS: List<Key> = buildList {
    addAll(row(0, "qwertyuiop", 10f, 69.4f, 60f, "1234567890"))
    addAll(row(1, "asdfghjkl", 46f, 69.2f, 59.5f, "~!@#%“”*?"))
    add(k("分词", Code.SPLIT, 2, 10f, 96f))
    addAll(row(2, "zxcvbnm", 115f, 69.2f, 59.5f, "()-_:;、"))
    add(k("⌫", Code.DEL, 2, 609f, 696f))
    add(k("符", Code.SYM, 3, 10f, 106f))
    add(k("123", Code.NUM, 3, 114f, 186f))
    add(k(",", ','.code, 3, 196f, 256f))
    add(k("", Code.SPACE, 3, 266f, 439f))
    add(k(".", '.'.code, 3, 449f, 510f))
    add(k("中/英", Code.LANG, 3, 520f, 590f))
    add(k("↵", Code.ENTER, 3, 599f, 696f))
  }

  private fun n(label: String, code: Int, r: Int, x0: Float, x1: Float) = Key(label, code, x0, NROWS[r].first, x1, NROWS[r].second)
  private val NCOLS = listOf(124f to 270f, 279f to 425f, 435f to 581f)

  val NUMBERS: List<Key> = buildList {
    add(Key("", Code.LIST, 8f, NROWS[0].first, 116f, NROWS[2].second, items = "*/-+"))
    for (r in 0..2) for (c in 0..2) {
      val d = ('1' + r * 3 + c)
      add(n(d.toString(), d.code, r, NCOLS[c].first, NCOLS[c].second))
    }
    add(n("⌫", Code.DEL, 0, 588f, 695f))
    add(n(".", '.'.code, 1, 588f, 695f))
    add(n("@", '@'.code, 2, 588f, 695f))
    add(n("符", Code.SYM, 3, 8f, 116f))
    add(n("返回", Code.ABC, 3, 122f, 272f))
    add(n("0", '0'.code, 3, 279f, 425f))
    add(n("", Code.SPACE, 3, 433f, 582f))
    add(n("↵", Code.ENTER, 3, 589f, 696f))
  }

  fun keys(page: Page): List<Key> = when (page) { Page.LETTERS -> LETTERS; Page.NUMBERS -> NUMBERS }

  /** 中文模式下标点换成全角的;没在表里的原样 */
  private val CN_PUNCT = mapOf(
    ',' to "，", '.' to "。", '?' to "？", '!' to "！", ':' to "：", ';' to "；", '(' to "（", ')' to "）",
    '<' to "《", '>' to "》", '\\' to "、", '~' to "～", '^' to "……", '$' to "￥", '_' to "——", '[' to "【", ']' to "】", '{' to "「", '}' to "」",
  )
  /** 引号成对轮换:第一次 “ 第二次 ” */
  private var quoteOpen = true
  private var singleOpen = true

  fun chinesePunct(c: Char): String = when (c) {
    '"' -> { quoteOpen = !quoteOpen; if (quoteOpen) "”" else "“" }
    '\'' -> { singleOpen = !singleOpen; if (singleOpen) "’" else "‘" }
    else -> CN_PUNCT[c] ?: c.toString()
  }

  /** 中文模式下逗号、句号键显示成全角的 */
  fun shown(c: Char, chinese: Boolean): String = if (chinese && (c == ',' || c == '.')) CN_PUNCT[c]!! else c.toString()

  /** 非字母、非数字的可打字符(标点符号):中文模式下要先把正在组的词上屏 */
  fun isPunct(code: Int): Boolean = code > 0 && !code.toChar().isLetterOrDigit() && code != ' '.code
}
