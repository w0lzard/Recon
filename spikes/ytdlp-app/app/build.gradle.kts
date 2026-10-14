// Throwaway S3 spike app (D12). x86_64 split is for the gate AVD only. Flavors: "with" = youtubedl-android, "base" = empty baseline for APK delta.
plugins { id("com.android.application") }
android {
    namespace = "spike.ytdlp"
    compileSdk = 36
    defaultConfig { applicationId = "spike.ytdlp"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1" }
    flavorDimensions += "lib"
    productFlavors { create("with") { dimension = "lib" }; create("base") { dimension = "lib"; applicationIdSuffix = ".base" } }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") } }
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "armeabi-v7a", "x86_64"); isUniversalApk = false } }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { "withImplementation"("io.github.junkfood02.youtubedl-android:library:0.18.1") }
