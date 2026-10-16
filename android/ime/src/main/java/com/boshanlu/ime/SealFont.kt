package com.boshanlu.ime

import android.content.Context
import android.graphics.Typeface

/**
 * 博山篆字体(assets/fonts/BoshanSeal.otf,开元小篆重描,只收常用 3500 字用到的篆字,码位在 Unicode 18.0 篆书区块)。
 * 候选条、候选面板、简繁篆按钮都用它:字体里没有的字(汉字、字母、标点)系统会自动退回默认字体,所以整段文字直接用它就行。
 */
object SealFont {
  @Volatile private var cached: Typeface? = null

  fun typeface(ctx: Context): Typeface = cached ?: synchronized(this) {
    cached ?: (runCatching { Typeface.createFromAsset(ctx.applicationContext.assets, "fonts/BoshanSeal.otf") }.getOrNull()
      ?: Typeface.DEFAULT).also { cached = it }
  }
}

/**
 * 字 → 小篆对照(数据包里的 boshan_seal.txt,与 lua 那边同一份;简繁都能查,只含常用 3500 字及其繁体)。
 * 篆图要把任意一段字转成小篆时用;没有篆形的字原样留着。
 */
object SealMap {
  @Volatile private var map: Map<String, String>? = null

  private fun load(ctx: Context): Map<String, String> = map ?: synchronized(this) {
    map ?: HashMap<String, String>(20000).also { m ->
      runCatching {
        ctx.applicationContext.assets.open("rime/boshan_seal.txt").bufferedReader().useLines { lines ->
          for (line in lines) {
            if (line.startsWith("#")) continue
            val tab = line.indexOf('\t')
            if (tab > 0) m[line.substring(0, tab)] = line.substring(tab + 1)
          }
        }
      }
      map = m
    }
  }

  /** 整段转小篆:按码点逐字查,取第一个篆形 */
  fun convert(ctx: Context, text: String): String {
    val m = load(ctx)
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      val ch = String(Character.toChars(cp))
      val seal = m[ch]
      out.append(if (seal != null) String(Character.toChars(seal.codePointAt(0))) else ch)
      i += Character.charCount(cp)
    }
    return out.toString()
  }

  /** 这段字里有没有小篆(篆书区块 U+3D000..U+3FC3F) */
  fun hasSeal(text: String): Boolean = text.codePoints().anyMatch { it in 0x3D000..0x3FC3F }
}
