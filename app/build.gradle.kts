// ============================================================================
// Agus VR — módulo principal (app)
// Arquitetura em módulos lógicos (packages):
//   com.agusvr.runtime      → Agus VR Runtime (engine, renderer, atividades)
//   com.agusvr.input        → Agus Input (head tracking, touch, eventos)
//   com.agusvr.handtracking → Agus Hand Tracking (MediaPipe + CameraX)
//   com.agusvr.point        → Agus Point Interaction (ray + círculo)
//   com.agusvr.interaction  → Agus Interaction (hover, seleção, grab)
//   com.agusvr.ui           → Agus UI (painéis espaciais, HUD, temas)
//   com.agusvr.apps         → Agus Applications (apps VR internos)
//   com.agusvr.spatial      → Agus Spatial System (âncoras, notas, workspace)
//   com.agusvr.performance  → Agus Performance (FPS, térmica, adaptativo)
//   com.agusvr.storage      → Agus Storage (JSON persistente)
//   com.agusvr.compat       → Agus Compatibility Layer (device profiles)
//   com.agusvr.bridge       → Ponte Roblox Studio / exportação de dados
// ============================================================================
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.agusvr"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.agusvr"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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

    // Seções de debug/otimização
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*")
    }
}

dependencies {
    // AndroidX / UI 2D (launcher)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Corrotinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // CameraX (captura de câmera para hand tracking + passthrough)
    val camerax = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")

    // MediaPipe Tasks Vision — Hand Landmarker real (21 keypoints por mão)
    implementation("com.google.mediapipe:tasks-vision:0.10.11")
}
