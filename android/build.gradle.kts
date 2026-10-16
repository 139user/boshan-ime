// AGP / Kotlin 版本跟 cowork 的安卓工程一致:ime 库原样放进 cowork 里编,两边得能用同一套插件
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.11.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
