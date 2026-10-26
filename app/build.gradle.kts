plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// GitHub Actions pasa el número de compilación; en local vale 1.
val buildNumber: Int = (System.getenv("BUILD_NUMBER") ?: "1").toIntOrNull() ?: 1

// Si hay una llave de firma (secretos de GitHub), todas las versiones se firman igual
// y por eso se pueden instalar como actualización una encima de otra.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.santosxd.diario"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.santosxd.diario"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.$buildNumber"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("diario") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEY_PASSWORD")
                keyAlias = "diario"
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("diario")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
