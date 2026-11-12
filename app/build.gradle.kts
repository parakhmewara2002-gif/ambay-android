plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

fun propOrEnv(name: String, default: String = ""): String {
    val prop = project.findProperty(name)?.toString()
    if (!prop.isNullOrBlank()) return prop
    val env = System.getenv(name)
    if (!env.isNullOrBlank()) return env
    return default
}

val runNumber = propOrEnv("GITHUB_RUN_NUMBER", "1").toIntOrNull()?.coerceAtLeast(1) ?: 1
val enableMinify = propOrEnv("AMBAY_MINIFY", "false").toBoolean()
val baseUrl = propOrEnv("AMBAY_BASE_URL", "https://ambay-registry.workers.dev")
val updateRepo = propOrEnv("AMBAY_UPDATE_REPO", "parakhmewara2002-gif/ambay-android")
val googleWebClientId = propOrEnv("AMBAY_GOOGLE_WEB_CLIENT_ID", "")

android {
    namespace = "com.ambay.billing"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ambay.billing"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"${baseUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "UPDATE_REPO", "\"${updateRepo.replace("\"", "\\\"")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("ambay-release.jks")
            storePassword = propOrEnv("AMBAY_KEYSTORE_PASS", "ambay2026release")
            keyAlias = propOrEnv("AMBAY_KEY_ALIAS", "ambay")
            keyPassword = propOrEnv("AMBAY_KEY_PASS", "ambay2026release")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = enableMinify
            isShrinkResources = enableMinify
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.lottie.compose)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.bouncycastle.prov)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
