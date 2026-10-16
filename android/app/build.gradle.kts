// 独立的博山输入法 App:一个输入法服务(继承 ime 库的 BoshanIme)+ 一个设置页。
// 版本号取仓库根目录 version.txt;签名:android/keystore.properties(storeFile / keyAlias / password)存在就用,否则 debug 签名。
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ver = rootProject.file("../version.txt").readText().trim()
val keystore = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> FileInputStream(f).use { load(it) } }
}

android {
    namespace = "com.boshanlu.ime.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.boshanlu.ime"
        minSdk = 24
        targetSdk = 36
        versionName = ver
        // 1.2.3 → 10203
        versionCode = ver.split('.').map { it.toInt() }.let { it[0] * 10000 + it[1] * 100 + it[2] }
        ndk { abiFilters += "arm64-v8a" }
    }
    signingConfigs {
        if (keystore.containsKey("storeFile")) {
            create("release") {
                keyAlias = keystore["keyAlias"] as String
                keyPassword = keystore["password"] as String
                storeFile = rootProject.file(keystore["storeFile"] as String)
                storePassword = keystore["password"] as String
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = if (keystore.containsKey("storeFile")) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(project(":ime"))
}
