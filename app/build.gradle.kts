import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing from keystore.properties (git-ignored, created in T14). Without it, release
// builds use the debug key and carry UNSIGNED-FOR-RELEASE in the file name so they can't be published by mistake.
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use(::load) } }
// APK names (AGP pattern <archivesName>-<abi>-<buildType>.apk): recon-0.1.0-arm64-v8a-release.apk.
// T15 matches release assets by this pattern; see README → "Which APK".
val appVersion = "0.1.0"
base.archivesName.set("recon-$appVersion")

android {
    namespace = "dev.wolzard.recon"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.wolzard.recon"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "RELEASES_REPO", "\"${providers.gradleProperty("recon.releasesRepo").get()}\"")
    }

    signingConfigs {
        if (keystoreProps != null) create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
