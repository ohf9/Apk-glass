plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.fahad.liquidglass"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.fahad.liquidglass"
        minSdk = 28          // ImageDecoder; blur needs 31+, shaders 33+ (S24 Ultra has both)
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    // Fixed key committed to the repo so every CI build installs as an update over the last one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("glass-debug.keystore")
            storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    // On-device subject cut-out for the depth effect (model is fetched by Play services)
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
}
