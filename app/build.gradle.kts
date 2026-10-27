plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.enginesense.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.enginesense.app"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        // Lets Robolectric unit tests see library-module assets (dtc-database-android's
        // bundled dtc_codes.db) — needed to validate DtcRepository against the real
        // database without a physical device or emulator.
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Robolectric downloads its "android-all" SDK jars at TEST RUNTIME via its own artifact
// fetcher, in a forked test JVM that doesn't inherit this environment's custom trust store
// the way Gradle's own build-time HTTPS calls do (those get it via -Djavax.net.ssl.trustStore
// on the gradle command itself) — without this, that fetch fails with an SSL handshake error.
tasks.withType<Test>().configureEach {
    System.getProperty("javax.net.ssl.trustStore")?.let { systemProperty("javax.net.ssl.trustStore", it) }
}

// googleid:1.1.1 transitively requests exact old kotlin-stdlib-jdk7/jdk8 versions
// (1.9.0 / 1.8.0) whose POMs aren't reachable in this environment; newer compatible
// versions are already cached, so force those instead of re-resolving the old ones.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
    }
}

dependencies {
    implementation(project(":dtc-database-android"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Schedules the reservation-expiry warning (fires minutes after a slot reservation starts,
    // even if the app is backgrounded) — survives process death unlike a plain Handler/Coroutine
    // delay, and needs no special alarm permission on API 31+ unlike AlarmManager's exact alarms.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // QR code rendering for the "Publish Web Report" flow (encodes the shareable report URL) —
    // zxing-core only, no Android-specific scanning/camera layer needed since this app only
    // generates codes, never scans them.
    implementation("com.google.zxing:core:3.5.3")

    // Only HTTP client in this project — used by RemoteAiMechanicChatService to call the
    // ai-mechanic backend, and now also as Coil's network engine below (one HTTP stack, not two).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // First remote-image need in this app (expert profile photos in the consultation feature —
    // every other image so far has been a local file, e.g. dealer logos). Deliberately Coil 2.x,
    // not the newer 3.x line: 3.6.3's transitive AndroidX deps need compileSdk 35-37 / AGP
    // 8.6-9.1 (confirmed by a real build failure), well past this project's compileSdk 34 / AGP
    // 8.5.2 — upgrading those just for an image library isn't worth the blast radius. 2.7.0 is
    // Coil 2's long-stable release, compatible with this project's existing toolchain as-is.
    implementation("io.coil-kt:coil-compose:2.7.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Real Google Sign-In via the modern Credential Manager API — see GOOGLE_WEB_CLIENT_ID
    // in LoginScreen for the one piece that still needs a real Google Cloud project.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // Only used to validate DtcRepository against the real bundled SQLite database (it needs
    // a real android.database.sqlite.* implementation) — no device/emulator available in this
    // environment, and the existing plain-JUnit AI Mechanic tests don't use this.
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    // MockWebServer: tests RemoteAiMechanicChatService's actual HTTP/parsing behavior (success,
    // timeout, non-2xx, malformed JSON) against a local test server, not a real network call.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
