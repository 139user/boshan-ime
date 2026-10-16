# 博山输入法安卓键盘的拼音引擎(docs/android.md):把 fcitx5-android 预编译的 librime 静态库及其依赖链进一个小的 JNI 库 librime_jni.so,
# Kotlin 侧 RimeJni.kt 调它。预编译件由 tools/prepare-android.ts(cowork 里是 client/scripts/prepare-tools.ts android)落到 prebuilt/<库>/<ABI>/…(不入库)。
# librime 的各个组件(拼音、lua、opencc……)靠静态初始化登记自己,必须整库链入(--whole-archive),不然链接器把"没人引用"的组件扔掉。
cmake_minimum_required(VERSION 3.18)
project(boshan_rime CXX)

set(PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt")
if(NOT EXISTS "${PREBUILT}/librime/${ANDROID_ABI}/lib/librime.a")
  message(FATAL_ERROR "没有 ${PREBUILT}/librime/${ANDROID_ABI}/lib/librime.a:先跑 bun tools/prepare-android.ts(cowork 里是 client/ 下 bun scripts/prepare-tools.ts android)")
endif()

add_library(rime_jni SHARED rime_jni.cpp)
target_compile_features(rime_jni PRIVATE cxx_std_17)
target_compile_options(rime_jni PRIVATE -fexceptions -frtti -Wall)
target_include_directories(rime_jni PRIVATE "${PREBUILT}/librime/${ANDROID_ABI}/include")
# 顺序要紧:静态库只解析在它前面出现的未定义符号,依赖放在 librime 后面
target_link_libraries(rime_jni PRIVATE
  -Wl,--whole-archive "${PREBUILT}/librime/${ANDROID_ABI}/lib/librime.a" -Wl,--no-whole-archive
  "${PREBUILT}/glog/${ANDROID_ABI}/lib/libglog.a"
  "${PREBUILT}/leveldb/${ANDROID_ABI}/lib/libleveldb.a"
  "${PREBUILT}/lua/${ANDROID_ABI}/lib/liblua_static.a"
  "${PREBUILT}/marisa/${ANDROID_ABI}/lib/libmarisa.a"
  "${PREBUILT}/opencc/${ANDROID_ABI}/lib/libopencc.a"
  "${PREBUILT}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a"
  log android)
# Android 15 起新装的应用要能跑在 16KB 页的内核上
target_link_options(rime_jni PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--gc-sections")
