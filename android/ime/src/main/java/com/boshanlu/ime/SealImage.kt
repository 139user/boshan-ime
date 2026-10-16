package com.boshanlu.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

/**
 * 篆图:把一段字画成小篆图片发进当前输入框。
 * - 画:米色纸底、墨色字,用博山篆字体(没有篆形的字退回系统字体照样画出来);每行最多 8 个字,长的折行。
 * - 发:输入框声明收图片(EditorInfo 的 contentMimeTypes 含 image/png)就 commitContent 直接发;
 *   不收的(微信等大多数国内应用)放进剪贴板,提示用户长按输入框粘贴。
 * 图片写在 cacheDir/seal/,经本库清单里声明的 FileProvider(<应用包名>.boshan.seal)给出去。
 */
object SealImage {
  private const val PER_LINE = 8
  private const val GLYPH = 160f
  private const val PAD = 48f

  fun render(ctx: Context, text: String): Bitmap {
    val cps = text.codePoints().toArray().filter { !Character.isWhitespace(it) }
    val lines = cps.chunked(PER_LINE).map { line -> line.joinToString("") { String(Character.toChars(it)) } }.ifEmpty { listOf(" ") }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = SealFont.typeface(ctx)
      textSize = GLYPH
      color = Color.parseColor("#2B2118")
      textAlign = Paint.Align.CENTER
    }
    val lineW = lines.maxOf { paint.measureText(it) }
    val lineH = GLYPH * 1.25f
    val w = (lineW + PAD * 2).toInt().coerceAtLeast((GLYPH + PAD * 2).toInt())
    val h = (lineH * lines.size + PAD * 2).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F6EFDF") }
    c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), PAD / 2, PAD / 2, bg)
    val fm = paint.fontMetrics
    lines.forEachIndexed { i, line ->
      val cy = PAD + lineH * i + lineH / 2
      c.drawText(line, w / 2f, cy - (fm.ascent + fm.descent) / 2, paint)
    }
    return bmp
  }

  private fun save(ctx: Context, bmp: Bitmap): File {
    val dir = File(ctx.cacheDir, "seal").apply { mkdirs() }
    // 只留最近几张
    dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(5)?.forEach { it.delete() }
    val f = File(dir, "seal-${System.currentTimeMillis()}.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return f
  }

  /** 画好发出去;返回给用户看的一句话(直接发了返回 null) */
  fun deliver(ctx: Context, ic: InputConnection?, info: EditorInfo?, text: String): String? {
    val file = save(ctx, render(ctx, text))
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".boshan.seal", file)
    val desc = ClipDescription("小篆 $text", arrayOf("image/png"))
    val accepts = info != null && EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes("image/png", it) }
    if (ic != null && info != null && accepts) {
      val content = InputContentInfoCompat(uri, desc, null)
      if (InputConnectionCompat.commitContent(ic, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)) return null
    }
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData("小篆 $text", arrayOf("image/png"), ClipData.Item(uri)))
    return "小篆图片已复制,在输入框里长按粘贴"
  }
}

/** 篆图用的 FileProvider:单独一个子类,免得和宿主应用自己的 androidx FileProvider 在清单合并时撞名 */
class SealFileProvider : FileProvider()
