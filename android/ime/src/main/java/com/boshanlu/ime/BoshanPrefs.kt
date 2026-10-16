package com.boshanlu.ime

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 博山的几个开关存在 Rime 用户目录的两个小文件里(格式 key=value,lua/boshan/prefs.lua 也读写它们):
 * - boshan_defaults.txt:管理员在后台定的默认值(seal_hint=1/0)。独立 App 从博山后台拉;cowork 由主程序按 cowork 后台的设置写。
 * - boshan_prefs.txt:用户自己的选择(seal_hint、script)。键盘上长按简繁篆、设置页的开关都写这里。
 * 键盘每次弹出时按 用户选择 > 管理员默认 > 方案默认(显示) 把 seal_hint 设到引擎上(BoshanIme.applyPrefs)。
 */
object BoshanPrefs {
  const val DEFAULTS = "boshan_defaults.txt"
  const val PREFS = "boshan_prefs.txt"
  /** 方案里 boshan/seal_hint_default 的值(管理员没给、用户没选时) */
  const val SEAL_HINT_FALLBACK = true

  fun read(ctx: Context, name: String): MutableMap<String, String> {
    val f = File(RimeEngine.userDir(ctx), name)
    val out = LinkedHashMap<String, String>()
    runCatching {
      f.forEachLine { line ->
        val m = Regex("""^\s*(\w+)\s*=\s*(.*?)\s*$""").find(line) ?: return@forEachLine
        out[m.groupValues[1]] = m.groupValues[2]
      }
    }
    return out
  }

  fun write(ctx: Context, name: String, kv: Map<String, String>, header: String) {
    val dir = RimeEngine.userDir(ctx).apply { mkdirs() }
    val body = buildString {
      append("# ").append(header).append('\n')
      for ((k, v) in kv.toSortedMap()) append(k).append('=').append(v).append('\n')
    }
    val tmp = File(dir, "$name.tmp")
    tmp.writeText(body)
    tmp.renameTo(File(dir, name))
  }

  private fun truthy(v: String?): Boolean? = when (v) { null -> null; "1", "true", "yes", "on" -> true; else -> false }

  fun adminSealHint(ctx: Context): Boolean? = truthy(read(ctx, DEFAULTS)["seal_hint"])
  fun userSealHint(ctx: Context): Boolean? = truthy(read(ctx, PREFS)["seal_hint"])
  /** 现在该不该显示小篆 */
  fun effectiveSealHint(ctx: Context): Boolean = userSealHint(ctx) ?: adminSealHint(ctx) ?: SEAL_HINT_FALLBACK

  /** 用户自己选了(设置页、键盘上长按简繁篆)。null = 清掉自己的选择,跟管理员默认走 */
  fun setUserSealHint(ctx: Context, on: Boolean?) {
    val p = read(ctx, PREFS)
    // 跟默认值一样就算"没自己选"(与 lua/boshan/prefs.lua 一致),以后管理员改默认值还能跟着走
    if (on == null || on == (adminSealHint(ctx) ?: SEAL_HINT_FALLBACK)) p.remove("seal_hint") else p["seal_hint"] = if (on) "1" else "0"
    write(ctx, PREFS, p, "博山输入法:用户自己切过的开关")
  }

  /** 宿主写管理员默认值(cowork 主程序用) */
  fun setAdminSealHint(ctx: Context, on: Boolean) {
    write(ctx, DEFAULTS, mapOf("seal_hint" to if (on) "1" else "0"), "博山输入法:管理员在后台定的默认值,由应用从后台取来写入")
  }

  private const val FETCH_EVERY_MS = 12 * 3600 * 1000L

  /**
   * 从博山后台取默认设置(GET <url>,返回 {"sealHintDefault":true,…}),写进 boshan_defaults.txt;12 小时内取过就不取。
   * 后台线程里做,做完(不论成败)在调用者线程以外回调 done(changed)。
   */
  fun fetchDefaults(ctx: Context, url: String, force: Boolean = false, done: (Boolean) -> Unit = {}) {
    val app = ctx.applicationContext
    val stamp = File(RimeEngine.userDir(app), ".defaults-fetched")
    if (!force && System.currentTimeMillis() - stamp.lastModified() < FETCH_EVERY_MS) return
    thread(name = "boshan-config") {
      var changed = false
      try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val o = JSONObject(body)
        if (o.has("sealHintDefault")) {
          val v = o.getBoolean("sealHintDefault")
          changed = adminSealHint(app) != v
          if (changed) setAdminSealHint(app, v)
        }
        RimeEngine.userDir(app).mkdirs()
        stamp.writeText(System.currentTimeMillis().toString())
      } catch (e: Exception) {
        Log.w("boshan", "取后台默认设置失败:$e")
      }
      done(changed)
    }
  }
}
