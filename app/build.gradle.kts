plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pico.spatialweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pico.spatialweather"
        minSdk = 35
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"
        val amapKey = System.getenv("WEATHER_AMAP_KEY")?.trim()
            ?: rootProject.file(".private/amap-key.txt").takeIf { it.isFile }?.readText()?.trim()
            ?: error("Provide WEATHER_AMAP_KEY or .private/amap-key.txt")
        require(amapKey.matches(Regex("[a-fA-F0-9]{32}"))) { "Invalid weather service credential" }
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters.add("arm64-v8a") }
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
    val signingPath = System.getenv("WEATHER_SIGNING_STORE")
    if (!signingPath.isNullOrBlank()) {
        signingConfigs {
            create("weatherRelease") {
                storeFile = file(signingPath)
                storePassword = System.getenv("WEATHER_SIGNING_PASSWORD")
                keyAlias = "weather"
                keyPassword = System.getenv("WEATHER_SIGNING_PASSWORD")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("weatherRelease")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.design)
    implementation(libs.spatial.ui.sense)
    implementation(libs.spatial.ui.tracking)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling.preview)
}

configurations.all {
    resolutionStrategy {
        exclude("androidx.compose.ui", "ui")
        exclude("androidx.compose.ui", "ui-graphics")
        exclude("androidx.compose.ui", "ui-text")
        exclude("androidx.compose.foundation", "foundation")
    }
}
