// 앱 모듈 빌드 스크립트.
// - Compose, Room(KSP), Retrofit, kotlinx-serialization, DataStore, WorkManager 등 사용.
// - local.properties 의 GEMINI_API_KEY 를 BuildConfig 로 주입하여 코드에서 안전하게 참조.

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// local.properties 에서 Gemini API 키를 읽어옴.
// 키가 없을 때도 빌드는 가능하도록 빈 문자열 fallback. (실제 호출 시에는 fallback 코칭 멘트 사용)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val geminiApiKey: String = (localProps.getProperty("GEMINI_API_KEY") ?: "").trim()

android {
    namespace = "com.groomteam2.dopamind"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.groomteam2.dopamind"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // BuildConfig 에 API 키 주입. 코드에서는 BuildConfig.GEMINI_API_KEY 로 접근.
        buildConfigField("String", "GEMINI_API_KEY", "\"" + geminiApiKey + "\"")
        // Gemini 모델명. 모델이 바뀔 경우 여기서만 수정.
        buildConfigField("String", "GEMINI_MODEL", "\"gemini-1.5-flash\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    // ── 안드로이드 코어 ────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // ── Compose UI ───────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // XML 테마(Theme.Material3.DayNight.NoActionBar) 제공용 — themes.xml parent 에서 사용.
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // ── 데이터 저장 (Room + DataStore) ─────────────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── 백그라운드 작업 ────────────────────────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // ── 네트워크 (Gemini API 호출) ─────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    // ── 코루틴 ────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ── 디버깅용 ──────────────────────────────────────────────────
    debugImplementation("androidx.compose.ui:ui-tooling")
}
