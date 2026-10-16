package com.boshanlu.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * 拼音引擎(博山方案 + librime,见博山仓库 docs/android.md)。整个进程一份、一个会话。
 *
 * 数据包打在 APK 的 assets/rime 里(博山 tools/prepare-android.ts 放的),第一次用或版本变了拷到 filesDir/rime/shared,
 * 然后交给 librime 部署(编译成二进制索引,放 filesDir/rime/user/build,要几十秒到一两分钟,在后台线程做,
 * 期间 state 是 PREPARING,键盘退回直接打字母)。用户词库(自学习的词)也在 user 目录里,升级不清。
 *
 * 部署完以后的按键处理都在主线程上做(很快,几毫秒),不用再切线程;部署没完成前不碰会话。
 */
object RimeEngine {
  enum class State { IDLE, PREPARING, READY, FAILED }

  class Ctx(val preedit: String, val input: String, val candidates: List<Pair<String, String>>, val page: Int, val last: Boolean, val highlight: Int) {
    val composing: Boolean get() = preedit.isNotEmpty() || input.isNotEmpty()
  }
  val EMPTY = Ctx("", "", emptyList(), 0, true, 0)

  @Volatile var state = State.IDLE; private set
  /** FAILED 时的原因 */
  @Volatile var message = ""; private set
  private var session = 0L
  private val listeners = CopyOnWriteArrayList<() -> Unit>()
  private val main = Handler(Looper.getMainLooper())
  /** 状态另写一份到文件:键盘在自己的进程(:ime)里,主应用的设置页读它(reported) */
  @Volatile private var stateFile: File? = null

  fun onChange(l: () -> Unit) { listeners.add(l) }
  fun removeListener(l: () -> Unit) { listeners.remove(l) }
  private fun set(s: State, msg: String = "") {
    state = s; message = msg
    stateFile?.let { f -> runCatching { f.parentFile?.mkdirs(); f.writeText("${s.name}\n$msg") } }
    main.post { for (l in listeners) l() }
  }

  /** 键盘进程报的状态(主应用设置页用):没报过就是 IDLE */
  fun reported(ctx: Context): Pair<String, String> {
    val t = runCatching { File(ctx.filesDir, STATE_FILE).readText() }.getOrNull() ?: return "idle" to ""
    return t.substringBefore('\n').lowercase() to t.substringAfter('\n', "")
  }
  private const val STATE_FILE = "rime/state.txt"

  /** 准备引擎(幂等):没准备过就在后台线程拷词库、部署、建会话 */
  @Synchronized fun ensureStarted(ctx: Context) {
    if (state != State.IDLE && state != State.FAILED) return
    stateFile = File(ctx.applicationContext.filesDir, STATE_FILE)
    if (!RimeJni.available) { set(State.FAILED, "这个安装包没带拼音引擎"); return }
    set(State.PREPARING)
    val app = ctx.applicationContext
    thread(name = "rime-prepare") {
      try {
        val root = File(app.filesDir, "rime")
        val shared = File(root, "shared")
        val user = File(root, "user")
        val log = File(app.cacheDir, "rime-log").apply { mkdirs() }
        val fresh = syncAssets(app, shared, user)
        user.mkdirs()
        RimeJni.nativeInit(shared.absolutePath, user.absolutePath, log.absolutePath, fresh)
        val id = RimeJni.nativeCreateSession()
        if (id == 0L) throw IllegalStateException("建不出引擎会话(词库部署失败?看 ${log.absolutePath})")
        main.post {
          session = id
          set(State.READY)
        }
      } catch (e: Throwable) {
        Log.e("boshan", "拼音引擎准备失败", e)
        set(State.FAILED, e.message ?: e.toString())
      }
    }
  }

  /**
   * APK 里的词库和本地那份版本不一致就重拷(先清掉旧的 shared 与 user/build,免得旧文件混着)。
   * 返回有没有重拷(重拷过要做完整部署)
   */
  private fun syncAssets(app: Context, shared: File, user: File): Boolean {
    val am = app.assets
    val want = am.open("rime/version.txt").bufferedReader().use { it.readText() }.trim()
    val have = runCatching { File(shared, "version.txt").readText().trim() }.getOrDefault("")
    if (want == have && want.isNotEmpty()) return false
    shared.deleteRecursively()
    File(user, "build").deleteRecursively()
    copyAssetDir(app, "rime", shared)
    return true
  }

  private fun copyAssetDir(app: Context, path: String, dest: File) {
    val names = app.assets.list(path) ?: return
    if (names.isEmpty()) {
      // 是文件(assets.list 对文件返回空数组)
      dest.parentFile?.mkdirs()
      app.assets.open(path).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
      return
    }
    dest.mkdirs()
    for (n in names) copyAssetDir(app, "$path/$n", File(dest, n))
  }

  val ready: Boolean get() = state == State.READY && session != 0L

  /** 引擎处理了这个键返回真;假 = 引擎不要,键盘自己处理 */
  fun processKey(keycode: Int, mask: Int = 0): Boolean = if (ready) RimeJni.nativeProcessKey(session, keycode, mask) else false

  /** 取走已经定下来要上屏的字;没有为 null */
  fun takeCommit(): String? = if (ready) RimeJni.nativeCommit(session) else null

  fun context(): Ctx {
    if (!ready) return EMPTY
    val o = JSONObject(RimeJni.nativeContext(session))
    val arr = o.optJSONArray("candidates")
    val cands = ArrayList<Pair<String, String>>(arr?.length() ?: 0)
    if (arr != null) for (i in 0 until arr.length()) {
      val c = arr.getJSONArray(i)
      cands.add(c.getString(0) to c.optString(1, ""))
    }
    return Ctx(o.optString("preedit"), o.optString("input"), cands, o.optInt("page"), o.optBoolean("last", true), o.optInt("highlight"))
  }

  fun select(index: Int): Boolean = ready && RimeJni.nativeSelect(session, index)

  /** 一个候选:字与注释(小篆提示、典籍出处、篆模式下的今字) */
  class Cand(val text: String, val comment: String)

  /** 从第 offset 个候选起取最多 limit 个(不分页);second = 后面还有 */
  fun candidates(offset: Int, limit: Int): Pair<List<Cand>, Boolean> {
    if (!ready) return emptyList<Cand>() to false
    val o = JSONObject(RimeJni.nativeCandidates(session, offset, limit))
    val arr = o.optJSONArray("items")
    val list = ArrayList<Cand>(arr?.length() ?: 0)
    if (arr != null) for (i in 0 until arr.length()) arr.getJSONArray(i).let { list.add(Cand(it.getString(0), it.optString(1, ""))) }
    return list to o.optBoolean("more")
  }

  fun getOption(name: String): Boolean = ready && RimeJni.nativeGetOption(session, name)
  fun setOption(name: String, value: Boolean) { if (ready) RimeJni.nativeSetOption(session, name, value) }

  /** 用户目录(用户词库、boshan_prefs.txt、boshan_defaults.txt 都在这里);键盘没起来时也能算出来 */
  fun userDir(ctx: Context): File = File(ctx.applicationContext.filesDir, "rime/user")
  /** 共享目录(APK 里拷出来的数据包) */
  fun sharedDir(ctx: Context): File = File(ctx.applicationContext.filesDir, "rime/shared")

  /** 选第 index 个候选(从头数) */
  fun selectAt(index: Int): Boolean = ready && RimeJni.nativeSelectAt(session, index)
  fun clear() { if (ready) RimeJni.nativeClear(session) }
  fun version(): String = if (RimeJni.available) RimeJni.nativeVersion() else ""
}
