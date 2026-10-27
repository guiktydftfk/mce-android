plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cn.mce.starlight"
    compileSdk = 35

    val mceVersionName = (project.findProperty("MCE_VERSION_NAME") as String?) ?: "0.0.1"
    val mceVersionCode = ((project.findProperty("MCE_VERSION_CODE") as String?) ?: "1").toInt()

    defaultConfig {
        applicationId = "cn.mce.starlight"
        minSdk = 26
        targetSdk = 35
        versionCode = mceVersionCode
        versionName = mceVersionName
    }

    buildFeatures {
        compose = true
    }

    /**
     * v0.3.8：图标底层「协会 logo」是整层 alpha=0 的彩蛋图层（正常显示不可见，只有解包才能看到）。
     * aapt 默认的 PNG crunch 会把全透明像素的 RGB 也抹掉（235KB → 444B），彩蛋层就没了，
     * 所以这里关闭 PNG 压缩，保留原始字节。
     */
    androidResources {
        noCompress += "png"
    }

    /**
     * v0.3.3 批 6：release 包用 debug keystore 签名（仓库内 keystore/debug.keystore），
     * 保证与历史 debug 包**同签名、可覆盖升级**；同时避免依赖用户目录下 ~/.android 的 keystore
     * （CI/沙箱环境对该路径无写权限，release 签名校验会失败）。
     */
    signingConfigs {
        create("mceRelease") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            // v0.3.3 批 6：发行包开启 R8（裁剪 + 资源压缩），并用 debug 签名便于侧载安装
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("mceRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.all {
            it.testLogging {
                events("passed", "failed", "skipped")
                showStandardStreams = true
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Discuz 移动版 HTML 解析
    implementation("org.jsoup:jsoup:1.18.1")
    // 头像 / 附件图片加载
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
