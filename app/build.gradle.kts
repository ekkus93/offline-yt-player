plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val sourceRevision = providers.environmentVariable("GITHUB_SHA").orElse("local").get()
val releaseVersionName = providers.environmentVariable("OYP_VERSION_NAME").orElse("0.1.0-dev").get()
val releaseVersionCode = providers.environmentVariable("OYP_VERSION_CODE").orElse("1").get().toInt()

android {
    namespace = "com.ekkus.offlineytplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ekkus.offlineytplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        buildConfigField("String", "SOURCE_REVISION", "\"$sourceRevision\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
