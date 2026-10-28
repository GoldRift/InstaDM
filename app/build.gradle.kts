plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// scripts/build-release.sh requests one APK per ABI; ordinary debug builds stay
// a single universal APK.
val splitAbis = providers.gradleProperty("instadm.abiSplits").orNull == "true"

// CI passes these from the release tag; defaults keep local builds unchanged.
val appVersionCode = providers.gradleProperty("versionCode").orNull?.let {
    it.toIntOrNull() ?: error("versionCode must be an integer, got '$it'")
} ?: 1
val appVersionName = providers.gradleProperty("versionName").orNull ?: "1.0"

// Signing is optional locally; CI sets all four or fails before building.
val signingEnvNames = listOf(
    "INSTADM_KEYSTORE",
    "INSTADM_KEYSTORE_PASSWORD",
    "INSTADM_KEY_ALIAS",
    "INSTADM_KEY_PASSWORD",
)
val signingValues = signingEnvNames
    .mapNotNull { name ->
        providers.environmentVariable(name).orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { name to it }
    }
    .toMap()
if (signingValues.isNotEmpty() && signingValues.size != signingEnvNames.size) {
    val missing = signingEnvNames - signingValues.keys
    error("Release signing env vars must be set together; missing: ${missing.joinToString()}")
}
val signingConfigured = signingValues.size == signingEnvNames.size

android {
    namespace = "com.goldrift.instadm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.goldrift.instadm"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingConfigured) {
            create("release") {
                storeFile = rootProject.file(signingValues.getValue("INSTADM_KEYSTORE"))
                storePassword = signingValues.getValue("INSTADM_KEYSTORE_PASSWORD")
                keyAlias = signingValues.getValue("INSTADM_KEY_ALIAS")
                keyPassword = signingValues.getValue("INSTADM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                // Full-tree R8. Gradual packageScope emitted synthetic holders with
                // package-private superclasses, crashing launch with IllegalAccessError.
                enable = true
            }
        }
    }
    if (splitAbis) {
        splits {
            abi {
                isEnable = true
                reset()
                include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                isUniversalApk = false
            }
        }
    }
    packaging {
        jniLibs {
            // The Go linker stripped libgojni.so; AGP's stripper cannot.
            keepDebugSymbols += "**/libgojni.so"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(files("libs/igcore.aar"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.emoji2.emojipicker)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}