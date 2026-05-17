import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
    id("com.google.dagger.hilt.android")
}

// 加载 keystore 配置
val keystoreProperties = Properties().apply {
    try {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        if (keystorePropertiesFile.exists()) {
            load(keystorePropertiesFile.reader())
        } else {
            logger.warn("keystore.properties file not found. Using default signing config.")
        }
    } catch (e: Exception) {
        logger.error("Failed to load keystore.properties: ${e.message}")
    }
}

// 加载本地配置（local.properties 不入库）
val localProperties = Properties().apply {
    try {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            load(localPropertiesFile.reader())
        }
    } catch (e: Exception) {
        logger.error("Failed to load local.properties: ${e.message}")
    }
}

fun readSecret(name: String, defaultValue: String = ""): String {
    val valueFromLocal = localProperties.getProperty(name)?.trim().orEmpty()
    if (valueFromLocal.isNotEmpty()) return valueFromLocal

    val valueFromEnv = System.getenv(name)?.trim().orEmpty()
    if (valueFromEnv.isNotEmpty()) return valueFromEnv

    return defaultValue
}

fun escapeBuildConfig(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

android {
    namespace = "com.example.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.chat"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // API 配置通过本地配置/环境变量注入，避免硬编码密钥
        buildConfigField(
            "String",
            "PETCHAT_API_KEY",
            "\"${escapeBuildConfig(readSecret("petchat.apiKey"))}\""
        )
        buildConfigField(
            "String",
            "PETCHAT_BASE_URL",
            "\"${escapeBuildConfig(readSecret("petchat.baseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1"))}\""
        )
        buildConfigField(
            "String",
            "PETCHAT_MODEL",
            "\"${escapeBuildConfig(readSecret("petchat.model", "deepseek-v3"))}\""
        )
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties.getProperty("keyAlias", "")
            keyPassword = keystoreProperties.getProperty("keyPassword", "")
            storeFile = keystoreProperties.getProperty("storeFile", "")?.let { file(it) }
            storePassword = keystoreProperties.getProperty("storePassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime:2.10.0")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui-tooling:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation(libs.kotlinx.serialization.json)
    implementation("androidx.compose.ui:ui-graphics:1.7.6")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
}
