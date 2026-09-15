plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

fun escapeForBuildConfig(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

val localPropMap: Map<String, String> = run {
    val f = rootProject.file("local.properties")
    if (!f.exists()) return@run emptyMap()
    val map = linkedMapOf<String, String>()
    f.forEachLine { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        val idx = line.indexOf('=')
        if (idx <= 0) return@forEachLine
        map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
    }
    map
}
val remoteEndpoint = localPropMap["REMOTE_AI_ENDPOINT"].orEmpty()
val remoteApiKey = localPropMap["REMOTE_AI_API_KEY"].orEmpty()
val remoteModel = localPropMap["REMOTE_AI_MODEL"].orEmpty()

android {
    namespace = "com.craznail.flashnote"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.craznail.flashnote"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "0.1.19"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "IS_PREMIUM", "false")
        // Optional build defaults from local.properties; prefs override at runtime.
        // Keys must never be committed — local.properties is gitignored.
        buildConfigField(
            "String",
            "REMOTE_AI_ENDPOINT",
            "\"${escapeForBuildConfig(remoteEndpoint)}\""
        )
        buildConfigField(
            "String",
            "REMOTE_AI_API_KEY",
            "\"${escapeForBuildConfig(remoteApiKey)}\""
        )
        buildConfigField(
            "String",
            "REMOTE_AI_MODEL",
            "\"${escapeForBuildConfig(remoteModel)}\""
        )
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.core:core-ktx:1.12.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("io.coil-kt:coil-compose:2.5.0")

    // On-device OCR (Chinese + Latin)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
