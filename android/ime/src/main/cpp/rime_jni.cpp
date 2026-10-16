// 博山输入法安卓键盘的拼音引擎(docs/android.md):librime 的 C API 包成几个 JNI 函数给 Kotlin 的 com.boshanlu.ime.RimeJni 用。
// 这里只做翻译,不管输入法逻辑:键码进去(X11 keysym,字母就是 ASCII),提交的字与当前上下文(编码、候选、页)以 JSON 字串出来。
// 一个 Kotlin 进程只开一个会话;所有函数都要在同一个线程上调(Kotlin 侧 RimeEngine 用单线程执行器保证)。
#include <jni.h>
#include <rime_api.h>

#include <cstring>
#include <string>

namespace {

RimeApi* api() { return rime_get_api(); }

std::string jstr(JNIEnv* env, jstring s) {
  if (!s) return "";
  const char* c = env->GetStringUTFChars(s, nullptr);
  std::string out = c ? c : "";
  if (c) env->ReleaseStringUTFChars(s, c);
  return out;
}

/// JSON 字串转义(UTF-8 原样放,只转义引号、反斜杠、控制字符)
void quote(std::string& out, const char* s) {
  out.push_back('"');
  for (const unsigned char* p = reinterpret_cast<const unsigned char*>(s ? s : ""); *p; ++p) {
    switch (*p) {
      case '"': out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      case '\t': out += "\\t"; break;
      default:
        if (*p < 0x20) {
          char buf[8];
          snprintf(buf, sizeof buf, "\\u%04x", *p);
          out += buf;
        } else {
          out.push_back(static_cast<char>(*p));
        }
    }
  }
  out.push_back('"');
}

std::string g_shared, g_user, g_log;

}  // namespace

extern "C" {

/// 初始化并部署(第一次或词库变了要几十秒,Kotlin 在后台线程调;full 为真做完整检查)。返回部署有没有成功开始
JNIEXPORT jboolean JNICALL Java_com_boshanlu_ime_RimeJni_nativeInit(JNIEnv* env, jclass, jstring shared, jstring user, jstring log, jboolean full) {
  g_shared = jstr(env, shared);
  g_user = jstr(env, user);
  g_log = jstr(env, log);
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = g_shared.c_str();
  traits.user_data_dir = g_user.c_str();
  traits.distribution_name = "boshan";
  traits.distribution_code_name = "boshan";
  traits.distribution_version = "1.0";
  traits.app_name = "rime.boshan";
  traits.min_log_level = 1;
  traits.log_dir = g_log.c_str();
  RimeApi* r = api();
  r->setup(&traits);
  r->initialize(&traits);
  // 部署在它自己的线程里跑,这里等它完;没东西要部署时 start_maintenance 返回假,直接可用
  if (r->start_maintenance(full ? True : False)) r->join_maintenance_thread();
  return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_boshanlu_ime_RimeJni_nativeFinalize(JNIEnv*, jclass) {
  api()->finalize();
}

JNIEXPORT jlong JNICALL Java_com_boshanlu_ime_RimeJni_nativeCreateSession(JNIEnv*, jclass) {
  return static_cast<jlong>(api()->create_session());
}

JNIEXPORT void JNICALL Java_com_boshanlu_ime_RimeJni_nativeDestroySession(JNIEnv*, jclass, jlong id) {
  api()->destroy_session(static_cast<RimeSessionId>(id));
}

/// 引擎处理了这个键返回真;假 = 引擎不要(没在组词时按了空格之类),由键盘自己处理
JNIEXPORT jboolean JNICALL Java_com_boshanlu_ime_RimeJni_nativeProcessKey(JNIEnv*, jclass, jlong id, jint keycode, jint mask) {
  return api()->process_key(static_cast<RimeSessionId>(id), keycode, mask) ? JNI_TRUE : JNI_FALSE;
}

/// 已经定下来要上屏的字(取一次就清掉);没有返回 null
JNIEXPORT jstring JNICALL Java_com_boshanlu_ime_RimeJni_nativeCommit(JNIEnv* env, jclass, jlong id) {
  RIME_STRUCT(RimeCommit, commit);
  if (!api()->get_commit(static_cast<RimeSessionId>(id), &commit)) return nullptr;
  jstring out = commit.text ? env->NewStringUTF(commit.text) : nullptr;
  api()->free_commit(&commit);
  return out;
}

/// 当前上下文:{"preedit":"ni hao","cursor":6,"input":"nihao","page":0,"last":false,"highlight":0,"candidates":[["你好","注释"],…]}
/// 没在组词时 preedit 为空、candidates 为空数组
JNIEXPORT jstring JNICALL Java_com_boshanlu_ime_RimeJni_nativeContext(JNIEnv* env, jclass, jlong id) {
  RIME_STRUCT(RimeContext, ctx);
  std::string out = "{";
  RimeSessionId sid = static_cast<RimeSessionId>(id);
  if (api()->get_context(sid, &ctx)) {
    out += "\"preedit\":";
    quote(out, ctx.composition.preedit);
    out += ",\"cursor\":" + std::to_string(ctx.composition.cursor_pos);
    out += ",\"input\":";
    quote(out, api()->get_input(sid));
    out += ",\"page\":" + std::to_string(ctx.menu.page_no);
    out += ",\"last\":" + std::string(ctx.menu.is_last_page ? "true" : "false");
    out += ",\"highlight\":" + std::to_string(ctx.menu.highlighted_candidate_index);
    out += ",\"candidates\":[";
    for (int i = 0; i < ctx.menu.num_candidates; ++i) {
      if (i) out.push_back(',');
      out.push_back('[');
      quote(out, ctx.menu.candidates[i].text);
      out.push_back(',');
      quote(out, ctx.menu.candidates[i].comment);
      out.push_back(']');
    }
    out += "]";
    api()->free_context(&ctx);
  } else {
    out += "\"preedit\":\"\",\"cursor\":0,\"input\":\"\",\"page\":0,\"last\":true,\"highlight\":0,\"candidates\":[]";
  }
  out += "}";
  return env->NewStringUTF(out.c_str());
}

/// 选当前页上的第 index 个候选(0 起)。整句选完会产生 commit;只选了一部分则上下文更新成剩下的编码
JNIEXPORT jboolean JNICALL Java_com_boshanlu_ime_RimeJni_nativeSelect(JNIEnv*, jclass, jlong id, jint index) {
  return api()->select_candidate_on_current_page(static_cast<RimeSessionId>(id), static_cast<size_t>(index)) ? JNI_TRUE : JNI_FALSE;
}

/// 从第 offset 个起取最多 limit 个候选(不分页,跟引擎的一页 9 个无关):{"items":[["你好","注释"],…],"more":true}。
/// 候选条横着一路滑、展开面板都靠它;more = 后面还有
JNIEXPORT jstring JNICALL Java_com_boshanlu_ime_RimeJni_nativeCandidates(JNIEnv* env, jclass, jlong id, jint offset, jint limit) {
  RimeCandidateListIterator it = {};
  std::string out = "{\"items\":[";
  bool more = false;
  if (api()->candidate_list_from_index(static_cast<RimeSessionId>(id), &it, offset)) {
    int n = 0;
    while (api()->candidate_list_next(&it)) {
      if (n == limit) { more = true; break; }
      if (n++) out.push_back(',');
      out.push_back('[');
      quote(out, it.candidate.text);
      out.push_back(',');
      quote(out, it.candidate.comment);
      out.push_back(']');
    }
    api()->candidate_list_end(&it);
  }
  out += "],\"more\":";
  out += more ? "true}" : "false}";
  return env->NewStringUTF(out.c_str());
}

/// 选第 index 个候选(从头数,不分页)
JNIEXPORT jboolean JNICALL Java_com_boshanlu_ime_RimeJni_nativeSelectAt(JNIEnv*, jclass, jlong id, jint index) {
  return api()->select_candidate(static_cast<RimeSessionId>(id), static_cast<size_t>(index)) ? JNI_TRUE : JNI_FALSE;
}

/// 丢掉正在组的词(切英文、收起键盘、换输入框)
JNIEXPORT void JNICALL Java_com_boshanlu_ime_RimeJni_nativeClear(JNIEnv*, jclass, jlong id) {
  api()->clear_composition(static_cast<RimeSessionId>(id));
}

JNIEXPORT void JNICALL Java_com_boshanlu_ime_RimeJni_nativeSetOption(JNIEnv* env, jclass, jlong id, jstring name, jboolean value) {
  api()->set_option(static_cast<RimeSessionId>(id), jstr(env, name).c_str(), value ? True : False);
}

JNIEXPORT jboolean JNICALL Java_com_boshanlu_ime_RimeJni_nativeGetOption(JNIEnv* env, jclass, jlong id, jstring name) {
  return api()->get_option(static_cast<RimeSessionId>(id), jstr(env, name).c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_com_boshanlu_ime_RimeJni_nativeVersion(JNIEnv* env, jclass) {
  const char* v = api()->get_version();
  return env->NewStringUTF(v ? v : "");
}

}  // extern "C"
