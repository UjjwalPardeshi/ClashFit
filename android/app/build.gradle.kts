import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// Cloud keys come from local.properties (git-ignored) or the environment, never from source.
// With none present the app runs with on-device accounts and no network; see cloud/CloudConfig.kt.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String = "\"" + (localProps.getProperty(key) ?: System.getenv(key) ?: "") + "\""

android {
    namespace = "com.clashfit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.clashfit"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        buildConfigField("String", "FIREBASE_API_KEY", secret("FIREBASE_API_KEY"))
        buildConfigField("String", "FIREBASE_APP_ID", secret("FIREBASE_APP_ID"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", secret("FIREBASE_PROJECT_ID"))
    }

    signingConfigs {
        // Release signing comes from the environment (CI or a release machine). Without it the
        // release build still produces an installable APK, signed with the debug key, so
        // assembleRelease is always a valid check of R8 and resource shrinking.
        val debugSigning = getByName("debug")
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            if (keystoreFile != null && keystorePassword != null) {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                keyAlias = System.getenv("KEY_ALIAS") ?: "clashfit"
                keyPassword = System.getenv("KEY_PASSWORD") ?: keystorePassword
            } else {
                initWith(debugSigning)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")

            // Phones only.
            //
            // Measured on the 117 MB release apk: x86_64 native code was 49.5 MB of it, 42 per
            // cent, and no phone will ever execute a byte of it. The MediaPipe LLM engine alone
            // ships 28.8 MB for x86_64 and 25.4 MB for arm64. Dropping the one nothing runs takes
            // the download to roughly seventy megabytes, which is the difference between a quick
            // sideload and standing at a table waiting.
            //
            // Debug keeps both, so an emulator still works.
            //
            // Done at packaging rather than with ndk.abiFilters: a build type's abiFilters are
            // unioned with defaultConfig's rather than replacing them, so setting it there changed
            // nothing and the release still carried both architectures.
            packaging { jniLibs { excludes += "lib/x86_64/**" } }
        }
        debug {
            isMinifyEnabled = false
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // MediaPipe models are memory-mapped; keep them uncompressed in the APK.
    androidResources {
        noCompress += listOf("task", "tflite")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
        // MediaPipe and CameraX both ship libc++_shared; one copy per ABI is enough.
        jniLibs.pickFirsts += setOf("lib/arm64-v8a/libc++_shared.so", "lib/x86_64/libc++_shared.so")
        jniLibs.useLegacyPackaging = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

// Screenshot tests render every screen on the JVM through Robolectric; the PNGs land in
// app/screenshots so a reviewer can see the app without a phone.
roborazzi {
    outputDir.set(layout.projectDirectory.dir("screenshots"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
}

/**
 * Compose compiler metrics, on demand.
 *
 * Run with `-PcomposeMetrics` to write two reports next to the build: which composables are
 * skippable and restartable, and which parameters are unstable. An unstable parameter means the
 * compiler cannot prove a composable's inputs are unchanged, so it recomposes on every parent
 * recomposition whether anything changed or not — which is the usual cause of a Compose app being
 * slow for no visible reason.
 *
 * Off by default: it slows every build, and it is a diagnostic rather than a setting.
 */
if (project.hasProperty("composeMetrics")) {
    val dir = layout.buildDirectory.dir("compose-metrics").get().asFile.absolutePath
    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$dir",
                "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$dir",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

/**
 * The permission set, locked.
 *
 * Ujjwal added this as a check that INTERNET never appears, which was right for the app as it
 * stood: everything ran on-device and a transitive Google telemetry library was quietly asking
 * for the network. Accounts and cloud leaderboards arrived after that, and they genuinely need
 * INTERNET — so a deny-list of one permission would now fail every build while saying nothing
 * about the other twenty-three.
 *
 * The check it becomes is stronger, not weaker. Every permission in the merged manifest must
 * appear in this list, and every permission in this list must appear in the merged manifest.
 * A library cannot widen what the app may do without failing the build, and a permission a
 * feature depends on cannot silently disappear either — which is the failure mode that
 * reversing this check would otherwise have shipped: strip INTERNET, and sign-in, the
 * leaderboard and friends all stop working with no build error anywhere.
 *
 * Two of these are not ours. Play services adds READ_GSERVICES and androidx adds a signature-level
 * receiver permission scoped to our own package. Both are listed because the point of a lock file
 * is that nothing is unaccounted for, not that the list is short.
 *
 * Adding a line here means saying why on the line.
 */
val PERMISSION_ALLOW_LIST: Map<String, String> = mapOf(
    // The referee.
    "android.permission.CAMERA" to "the pose model reads frames; none are stored or sent",
    "android.permission.RECORD_AUDIO" to "offline voice commands during a set",

    // Sign-in and the leaderboard, and nothing else.
    "android.permission.INTERNET" to "Firebase Auth and the Firestore leaderboard",
    "android.permission.ACCESS_NETWORK_STATE" to "tells you the board is stale rather than empty",

    // Feedback in the hand and out loud.
    "android.permission.VIBRATE" to "a counted rep, confirmed in the hand",
    "android.permission.WAKE_LOCK" to "the screen stays up through a set",
    "android.permission.POST_NOTIFICATIONS" to "the run tracker and the wake-up alarm",

    // The run tracker.
    "android.permission.ACCESS_FINE_LOCATION" to "the route, kept on the phone",
    "android.permission.ACCESS_COARSE_LOCATION" to "the same, degraded, if fine is refused",
    "android.permission.FOREGROUND_SERVICE" to "tracking continues with the screen off",
    "android.permission.FOREGROUND_SERVICE_LOCATION" to "the typed form of the above, from API 34",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" to "the coach keeps speaking mid-set",

    // Two phones in a room, with no server between them.
    "android.permission.BLUETOOTH_SCAN" to "finding the other phone for a duel",
    "android.permission.BLUETOOTH_CONNECT" to "pairing it",
    "android.permission.BLUETOOTH_ADVERTISE" to "being found by it",
    "android.permission.NEARBY_WIFI_DEVICES" to "the hotspot transport, from API 33",
    "android.permission.ACCESS_WIFI_STATE" to "the same transport on older phones",
    "android.permission.CHANGE_WIFI_STATE" to "the same",

    // The wake-up alarm.
    "android.permission.SCHEDULE_EXACT_ALARM" to "an alarm that rings at the time it says",
    "android.permission.USE_EXACT_ALARM" to "the same, granted by default from API 33",
    "android.permission.USE_FULL_SCREEN_INTENT" to "it shows over the lock screen",
    "android.permission.RECEIVE_BOOT_COMPLETED" to "alarms survive a restart",

    // Not ours, and listed anyway.
    "com.google.android.providers.gsf.permission.READ_GSERVICES" to
        "added by Google Play services under firebase-auth; not requested by any of our code",
    "com.clashfit.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" to
        "added by androidx core; signature-level and scoped to this package",
)

abstract class CheckPermissionAllowList : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val allowed: SetProperty<String>

    @TaskAction
    fun check() {
        val text = mergedManifest.get().asFile.readText()
        val found = Regex("""<uses-permission\b[^>]*?android:name="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text).map { it.groupValues[1] }.toSortedSet()
        val expected = allowed.get().toSortedSet()

        val added = found - expected
        val missing = expected - found
        if (added.isEmpty() && missing.isEmpty()) {
            logger.lifecycle("Permissions match the allow-list (${found.size}): ok")
            return
        }
        throw GradleException(
            buildString {
                appendLine("The merged manifest does not match the permission allow-list.")
                if (added.isNotEmpty()) {
                    appendLine()
                    appendLine("Present in the build but not in the list:")
                    added.forEach { appendLine("  + $it") }
                    appendLine("  Something widened what this app may do. Find out what:")
                    appendLine("  build/outputs/logs/manifest-merger-*-report.txt names the library.")
                    appendLine("  Then either remove the dependency, strip it with tools:node=\"remove\",")
                    appendLine("  or add it to PERMISSION_ALLOW_LIST with the reason it is acceptable.")
                }
                if (missing.isNotEmpty()) {
                    appendLine()
                    appendLine("In the list but not in the build:")
                    missing.forEach { appendLine("  - $it") }
                    appendLine("  A feature that depends on one of these will fail at runtime with no")
                    appendLine("  other warning. Restore it, or drop it from PERMISSION_ALLOW_LIST.")
                }
            },
        )
    }
}

androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register<CheckPermissionAllowList>("checkPermissions$cap") {
            group = "verification"
            description = "Fails if the merged $cap manifest declares any permission not on the allow-list, or is missing one that is."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowed.set(PERMISSION_ALLOW_LIST.keys)
        }
        tasks.matching { it.name == "assemble$cap" || it.name == "package$cap" }.configureEach { dependsOn(check) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material3.windowsize)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // The boss is a real 3D model, rendered by Filament. Chosen over SceneView because SceneView
    // 4.x is a multiplatform rewrite with no Compose entry point in its core artifact, while
    // Filament's Android API is stable and its ModelViewer takes a TextureView, which is what
    // lets the boss composite over the live camera with a transparent background.
    // Every entry point is guarded: if the engine will not start on a device, the app falls back
    // to the Compose-drawn boss and the fight still happens.
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)
    implementation(libs.filament.utils)

    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.play.services.nearby)
    implementation(libs.play.services.location)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
