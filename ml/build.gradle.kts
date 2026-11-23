plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yomu.ml"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cFlags += "-O2"
                cppFlags += "-std=c++17 -O2"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
        // CI passes -Pyomu.abis=arm64-v8a on PRs to compile llama.cpp once instead of twice.
        val abis = (findProperty("yomu.abis") as String?)?.split(",") ?: listOf("arm64-v8a", "x86_64")
        ndk { abiFilters += abis }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.32.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
