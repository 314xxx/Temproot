plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.temproot.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.temproot.app"
        minSdk = 31
        targetSdk = 34
        versionCode = 8
        versionName = "2.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // ADB 客户端（无线调试自配对，替代 Shizuku）。
    // 关键：排除 native spake2 传递依赖（libspake2.so 曾导致 native 崩溃——
    // SIGSEGV 无法被 Java 层 CrashHandler 捕获，表现为"闪退彻底、无日志"），
    // 替换为本地内置的纯 Java 实现（Spake2Context 包名与方法签名与 native 版
    // 完全一致，二进制兼容；ADB AppControl 生态验证过，与 adbd 的 BoringSSL
    // spake2 完全互通）。LGPL-3.0，以 jar 形式静态链接，版权声明见 README。
    implementation("com.flyfishxu:kadb:1.2.1") {
        exclude(group = "com.github.flyfishxu.spake2-java")
    }
    implementation(files("libs/spake2-java-1.0.0.jar"))
}
