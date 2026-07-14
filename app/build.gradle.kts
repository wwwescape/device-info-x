import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
}

// Release signing reads from keystore.properties (gitignored, local-only). CI writes this
// same file from repository secrets before invoking the release build — see
// .github/workflows/release.yml. Signing is simply skipped if the file isn't present, so
// debug builds and PR CI runs work without a keystore.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Google Maps API key (Live Location Sharing — console) — same gitignored,
// local-only-properties-file pattern as keystore.properties above, just reusing the
// already-generated local.properties (Android Studio's own file, never checked in) instead of a
// second bespoke one. Read once here and fanned out to both the Maps SDK's required manifest
// meta-data (mapsApiKey placeholder below) and a BuildConfig field for the Directions API calls
// CalendarRepository-style code makes directly via OkHttp — see TODOS.md's "direct from client"
// decision for why this isn't proxied through the server.
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY") ?: ""

android {
    namespace = "com.wwwescape.deviceinfox"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wwwescape.deviceinfox"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.0.1"

        // In-app update check (console/data/release/) — set by hand right before every
        // `git commit --amend`/force-push of a new build, then recorded server-side via
        // `python -m app.cli set-latest-build --timestamp <this value>` right after. Deliberately
        // not derived from `git rev-parse HEAD` (no Gradle-shells-out-to-git machinery, no
        // reliance on the build environment having git available) and deliberately not the real
        // versionCode above (which Android's PackageManager enforces can never be installed lower
        // than what's already on a device — this value has no such install-time constraint, so it
        // can be freely reset if ever needed). Plain string equality against the server's
        // `latest_build`, never parsed/ordered — see the TODOS.md writeup this was built from.
        buildConfigField("String", "BUILD_TIMESTAMP", "\"2026-08-16-0017\"")
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")

        manifestPlaceholders["mapsApiKey"] = mapsApiKey

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // x86/x86_64 are emulator-only — no real phone ships on them. SQLCipher's native
            // libsqlcipher.so is ~2-4MB *per ABI*, and without this filter a universal release
            // APK/AAB bundles all 4 ABIs' copies simultaneously even though a single device only
            // ever loads one. Dropping the two nobody sideloads on cuts that in half with zero
            // effect on any real device (arm64-v8a/armeabi-v7a — every actual Android phone —
            // still ship). Release-only, not defaultConfig: debug builds keep every ABI so local
            // emulator testing (often x86_64) is unaffected.
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Needed as of Phase 11 to parse the server's ISO-8601 timestamps via java.time
        // (OffsetDateTime/Instant) on minSdk 24, which has no built-in java.time support.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room schema history (used for migration testing). Checked into version control per Room's
// own recommendation, since it's the source of truth for what changed between versions once
// migrations start landing.
kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.sqlcipher.android)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    // Shipped in both build types (tiny), gated at runtime by ApplicationInfo.FLAG_DEBUGGABLE
    // in NetworkModule rather than split by build variant — same debug-gating approach Phase 2
    // used for its temporary debug trigger, and avoids a debug/release NetworkModule split.
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.maps.utils)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
