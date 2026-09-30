import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the run number so every release has a strictly larger versionCode;
// Android refuses to "update" to an equal or smaller one.
val joVersionCode = (findProperty("joVersionCode") as String?)?.toInt() ?: 1
val releaseKeystore: String? = System.getenv("JO_KEYSTORE_PATH")

android {
    namespace = "com.teneeduu.jo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.teneeduu.jo"
        minSdk = 26
        targetSdk = 35
        versionCode = joVersionCode
        versionName = "1.0.$joVersionCode"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "pkcs12"
                storePassword = System.getenv("JO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JO_KEY_ALIAS") ?: "jo"
                keyPassword = System.getenv("JO_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Local builds without the key fall back to the debug key. CI refuses
            // to (see the workflow): a release signed with any other key cannot
            // update an installed copy.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-session:1.5.0")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")

    testImplementation("junit:junit:4.13.2")
}
