plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val stakCloudUrl = providers.environmentVariable("STAK_CLOUD_URL").orElse("").get()
val stakCloudKey = providers.environmentVariable("STAK_CLOUD_PUBLIC_KEY").orElse("").get()
require(stakCloudKey.isEmpty() || stakCloudKey.startsWith("sb_publishable_")) { "Use a Supabase publishable key, never an admin/service key." }
fun configString(value:String)="\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","").replace("\r","")+"\""
android {
    namespace = "app.stak.nativeapp"
    compileSdk = 35
    // The official update key stays outside source control. Contributors can build
    // with Android's local debug key; only the private key can update official APKs.
    val privateSigningFile = file(providers.environmentVariable("STAK_KEYSTORE_PATH").orElse("../signing/stak-preview.keystore").get())
    signingConfigs {
        getByName("debug") {
            if (privateSigningFile.isFile) {
                storeFile = privateSigningFile
                storePassword = providers.environmentVariable("STAK_KEYSTORE_PASSWORD").orElse("android").get()
                keyAlias = providers.environmentVariable("STAK_KEY_ALIAS").orElse("androiddebugkey").get()
                keyPassword = providers.environmentVariable("STAK_KEY_PASSWORD").orElse("android").get()
            }
        }
        create("release") {
            if (privateSigningFile.isFile) {
                storeFile = privateSigningFile
                storePassword = providers.environmentVariable("STAK_KEYSTORE_PASSWORD").orElse("android").get()
                keyAlias = providers.environmentVariable("STAK_KEY_ALIAS").orElse("androiddebugkey").get()
                keyPassword = providers.environmentVariable("STAK_KEY_PASSWORD").orElse("android").get()
            }
        }
    }
    defaultConfig {
        applicationId = "app.stak.nativeapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 86
        versionName = "1.5.10"
        buildConfigField("String", "STAK_CLOUD_URL", configString(stakCloudUrl))
        buildConfigField("String", "STAK_CLOUD_PUBLIC_KEY", configString(stakCloudKey))
        manifestPlaceholders["appLabel"] = "STAK"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            // Install alongside the old port while comparing fresh-install states.
            applicationIdSuffix = ".parity"
            versionNameSuffix = "-preview"
            manifestPlaceholders["appLabel"] = "STAK"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".parity"
            versionNameSuffix = ""
            manifestPlaceholders["appLabel"] = "STAK"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isShrinkResources = true
            matchingFallbacks += listOf("release")
        }

    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("dev.chrisbanes.haze:haze:1.1.1")
    implementation("com.materialkolor:material-kolor:2.0.0")
    implementation("com.airbnb.android:lottie-compose:6.6.2")
    implementation("com.github.skydoves:flexible-bottomsheet-material3:0.1.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

