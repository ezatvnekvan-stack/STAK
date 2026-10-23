plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val distributionStore = providers.environmentVariable("STAK_SIGNING_STORE_FILE")
val distributionStorePassword = providers.environmentVariable("STAK_SIGNING_STORE_PASSWORD")
val distributionAlias = providers.environmentVariable("STAK_SIGNING_KEY_ALIAS")
val distributionKeyPassword = providers.environmentVariable("STAK_SIGNING_KEY_PASSWORD")
val distributionReady = listOf(distributionStore, distributionStorePassword, distributionAlias, distributionKeyPassword)
    .all { it.orNull?.isNotBlank() == true }
android {
    namespace = "app.stak.nativeapp"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "app.stak.nativeapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-native-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (distributionReady) create("distribution") {
            storeFile = file(distributionStore.get())
            storePassword = distributionStorePassword.get()
            keyAlias = distributionAlias.get()
            keyPassword = distributionKeyPassword.get()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (distributionReady) signingConfig = signingConfigs.getByName("distribution")
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
// Never produce an unsigned or silently debug-signed distribution build.
tasks.matching { it.name in listOf("packageRelease", "assembleRelease", "bundleRelease") }.configureEach {
    doFirst {
        check(distributionReady) { "Permanent release signing is required. See native-android/SIGNING.md." }
        check(file(distributionStore.get()).isFile) { "Release keystore file is missing." }
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("app.rive:rive-android:10.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
