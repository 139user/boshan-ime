package com.boshanlu.ime

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan

/** 候选的显示:字 + 小一号的灰色注释(小篆提示、〔荀子·正名〕出处、篆模式下的今字)。整段用博山篆字体(SealFont)显示 */
object CandText {
  fun format(c: RimeEngine.Cand, theme: ImeTheme): CharSequence {
    if (c.comment.isEmpty()) return c.text
    val sb = SpannableStringBuilder(c.text).append(' ')
    val start = sb.length
    sb.append(c.comment)
    // 小篆提示照原大小、颜色深一些(太小太淡看不清笔画);出处之类的文字注释小一号、灰色
    val seal = SealMap.hasSeal(c.comment) && !c.comment.startsWith("〔")
    sb.setSpan(RelativeSizeSpan(if (seal) 1f else 0.6f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.setSpan(ForegroundColorSpan(if (seal) theme.icon else theme.textMuted), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return sb
  }
}
