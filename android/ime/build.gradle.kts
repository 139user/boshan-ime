// 博山键盘库:拼音引擎(librime,JNI)+ 键盘界面 + 简繁篆 / 小篆提示 / 篆图 / 典籍联想。
// 资源:assets/rime(数据包)与 assets/fonts/BoshanSeal.otf 由 tools/prepare-android.ts 放进来(不入库);
// cpp/prebuilt 是 fcitx5-android 预编译的 librime 静态库,也由它下载。cowork 里同一份源码由 cowork 的 prepare-tools 准备。
import java.io.File
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.boshanlu.ime"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static", "-DANDROID_PLATFORM=android-24")
                abiFilters += "arm64-v8a"
            }
        }
    }
    // 和宿主用同一份 NDK(NDK_HOME),没设就用 AGP 默认的
    System.getenv("NDK_HOME")?.takeIf { it.isNotBlank() }?.let { ndk ->
        ndkPath = ndk
        val props = Properties().apply { File(ndk, "source.properties").takeIf { f -> f.exists() }?.inputStream()?.use { s -> load(s) } }
        props.getProperty("Pkg.Revision")?.let { ndkVersion = it.trim() }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    kotlinOptions { jvmTarget = "1.8" }
    // 数据包里的词库 / 句库是文本,压不压都行;字体与 .ocd2 不压缩,省得每次解
    androidResources { noCompress += listOf("otf", "ocd2") }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
}
