import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "eu.studiodeanna.openpantrymanager.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Desktop; for now it runs the tests, and keeps the shared code free of Android.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Android and desktop share java.io file storage, and ONNX Runtime's Java API to read text with.
        val javaMain = create("javaMain") {
            dependsOn(commonMain.get())
            dependencies {
                // Each platform brings its own build of the same API.
                compileOnly(libs.onnxruntime.jvm)
            }
        }
        androidMain.get().dependsOn(javaMain)
        jvmMain.get().dependsOn(javaMain)

        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.onnxruntime.android)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.java)
            implementation(libs.onnxruntime.jvm)
        }
    }
}

// The text reader's tests read photos with the networks the Android app ships.
tasks.withType<Test>().configureEach {
    systemProperty("ocr.models", rootProject.file("androidApp/src/main/assets/ocr").absolutePath)
}
