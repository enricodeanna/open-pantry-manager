import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "eu.studiodeanna.openpantrymanager.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "eu.studiodeanna.openpantrymanager"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 6
        versionName = "0.6.0"
    }

    buildTypes {
        release {
            // R8 keeps only the code and resources the app reaches, out of the libraries it uses.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        // The app speaks English and Italian; the libraries' texts in other languages would go unused.
        localeFilters += listOf("en", "it")
    }

    // ONNX Runtime, which reads receipts, is a native library of 25–40 MB for each kind of processor: one
    // APK for each kind, rather than one with all four. App bundles split by themselves.
    splits {
        abi {
            isEnable = true
            reset()
            include(*ABIS.keys.toTypedArray())
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            // Compressed in the APK, to about a third of the download; Android unpacks them on install.
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }
}

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

// Each APK's version code ends in its kind of processor, so that stores can tell them apart.
androidComponents {
    onVariants { variant ->
        for (output in variant.outputs) {
            val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier ?: continue
            output.versionCode.set(output.versionCode.get()!! * 10 + ABIS.getValue(abi))
        }
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.androidx.activity.compose)
    // Voice commands: Vosk hears them on the phone. JNA, which Vosk calls its native library with, comes
    // as an Android library with its own native part, not as the plain jar.
    implementation(libs.vosk.android)
    implementation("${libs.jna.get()}@aar")
}

/** The kinds of processor an APK is built for, with the digit its version code ends in. */
val ABIS get() = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)
