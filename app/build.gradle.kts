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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // 头像加载
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
}
