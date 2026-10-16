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
    // 小篆提示照原大小(太小看不清笔画),出处之类的文字注释小一号
    val size = if (SealMap.hasSeal(c.comment) && !c.comment.startsWith("〔")) 0.9f else 0.6f
    sb.setSpan(RelativeSizeSpan(size), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.setSpan(ForegroundColorSpan(theme.textMuted), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return sb
  }
}
