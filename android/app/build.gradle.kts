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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
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
