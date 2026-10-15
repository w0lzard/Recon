import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing from keystore.properties (git-ignored, created in T14). Without it, release APKs are unsigned
// (recon-0.4.0-arm64-v8a-release-unsigned.apk): what F-Droid builds and then checks byte for byte against ours.
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use(::load) } }
// APK names (AGP pattern <archivesName>-<abi>-<buildType>.apk): recon-0.1.0-arm64-v8a-release.apk.

android {
    namespace = "dev.wolzard.recon"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.wolzard.recon"
        minSdk = 26
        targetSdk = 37
        // Literal values: F-Droid reads both with a regex. Each ABI APK gets versionCode * 10 + its ABI digit (below).
        versionCode = 5
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "RELEASES_REPO", "\"${providers.gradleProperty("recon.releasesRepo").get()}\"")
    }

    signingConfigs {
        if (keystoreProps != null) create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Low-end gate build (AVD): release config with R8, debug-signed, x86_64 split allowed.
        create("gate") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    packaging { jniLibs { useLegacyPackaging = true } } // youtubedl-android extracts its native Python

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }   // D23: the exported schemas are the record of each version; MigrationTest builds v1 from 1.json

// T15 matches release assets by this pattern; see README → "Which APK".
base.archivesName.set("recon-${android.defaultConfig.versionName}")

// F-Droid's ABI order, lowest digit of the versionCode: 0.4.0 (5) is 51 for armeabi-v7a, 52 for arm64-v8a.
val abiDigit = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

// x86_64 is for the emulator only (debug + gate); release ships arm64-v8a and armeabi-v7a.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { out ->
            val abi = out.filters.firstOrNull { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
            abi?.let { out.versionCode.set(android.defaultConfig.versionCode!! * 10 + abiDigit.getValue(it)) }
            if (variant.buildType == "release" && abi == "x86_64") out.enabled.set(false)
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.compose.ui.tooling)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.okhttp)
    implementation(libs.androidx.browser)
    implementation(libs.youtubedl)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}

// Robolectric reflects into JDK internals (FileDescriptor for SDK 36 shared memory): JDK 21 toolchain + java.io / jdk.internal.access opened.
kotlin { jvmToolchain(21) } // JDK pinned via toolchain (README → Building)

tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
