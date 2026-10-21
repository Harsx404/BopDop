import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dopdop.bopdop"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dopdop.bopdop"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.3.0-beta.5"

    }

    // One release key for every build, here and on GitHub, so updates install over each other.
    // From local.properties (release.storeFile, release.storePassword, release.keyAlias,
    // release.keyPassword) or the environment (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
    // RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD). The keystore itself is never committed.
    val local = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun cfg(prop: String, env: String) = local.getProperty(prop) ?: System.getenv(env)
    val releaseStore = cfg("release.storeFile", "RELEASE_STORE_FILE")?.let { rootProject.file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseStore != null) create("release") {
            storeFile = releaseStore
            storePassword = cfg("release.storePassword", "RELEASE_STORE_PASSWORD")
            keyAlias = cfg("release.keyAlias", "RELEASE_KEY_ALIAS")
            keyPassword = cfg("release.keyPassword", "RELEASE_KEY_PASSWORD")
        }
    }
    // On CI a release without the real key would install over nobody's BopDop: stop instead.
    if (releaseStore == null && System.getenv("CI") == "true") error("Release key missing: set the RELEASE_KEYSTORE_B64 and RELEASE_* secrets")

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // One APK per processor alongside the universal one; the in-app updater picks the right one.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.palette:palette-ktx:1.0.0")

    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-cast:$media3")
    implementation("androidx.mediarouter:mediarouter:1.8.1")
    // Already pulled in by mediarouter; declared to open its Cast dialogs from the player menu.
    implementation("androidx.appcompat:appcompat:1.7.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    // Brand logos come from Simple Icons as SVG.
    implementation("io.coil-kt.coil3:coil-svg:3.6.3")

    implementation("dev.chrisbanes.haze:haze:2.0.1")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.1")
    implementation("dev.chrisbanes.haze:haze-glass:2.0.1")

    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")

    // QR codes: encoder for the styled share codes, embedded camera scanner to read them.
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM tests (the Android stub throws).
    testImplementation("org.json:json:20240303")
}
