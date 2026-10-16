package com.boshanlu.ime

/**
 * 拼音引擎 librime 的 JNI 入口(src/main/cpp/rime_jni.cpp,编出来是 librime_jni.so)。只做翻译,逻辑在 RimeEngine。
 * 键码是 X11 keysym:字母、数字、标点就是 ASCII;功能键见 KEY_*。
 */
object RimeJni {
  const val KEY_BACKSPACE = 0xff08
  const val KEY_RETURN = 0xff0d
  const val KEY_ESCAPE = 0xff1b
  const val KEY_PAGE_UP = 0xff55
  const val KEY_PAGE_DOWN = 0xff56
  const val KEY_SPACE = 0x20

  /** 库装进来没有(装不进来 = 这个包没带 librime_jni.so,拼音整个关掉,键盘只打英文) */
  val available: Boolean = try { System.loadLibrary("rime_jni"); true } catch (e: UnsatisfiedLinkError) { false }

  @JvmStatic external fun nativeInit(sharedDir: String, userDir: String, logDir: String, fullCheck: Boolean): Boolean
  @JvmStatic external fun nativeFinalize()
  @JvmStatic external fun nativeCreateSession(): Long
  @JvmStatic external fun nativeDestroySession(id: Long)
  @JvmStatic external fun nativeProcessKey(id: Long, keycode: Int, mask: Int): Boolean
  @JvmStatic external fun nativeCommit(id: Long): String?
  @JvmStatic external fun nativeContext(id: Long): String
  @JvmStatic external fun nativeSelect(id: Long, index: Int): Boolean
  @JvmStatic external fun nativeCandidates(id: Long, offset: Int, limit: Int): String
  @JvmStatic external fun nativeSelectAt(id: Long, index: Int): Boolean
  @JvmStatic external fun nativeClear(id: Long)
  @JvmStatic external fun nativeSetOption(id: Long, name: String, value: Boolean)
  @JvmStatic external fun nativeGetOption(id: Long, name: String): Boolean
  @JvmStatic external fun nativeVersion(): String
}
