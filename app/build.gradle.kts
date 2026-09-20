plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val sourceRevision = providers.environmentVariable("GITHUB_SHA").orElse("local").get()
val releaseVersionName = providers.environmentVariable("OYP_VERSION_NAME").orElse("0.1.0-dev").get()
val releaseVersionCode = providers.environmentVariable("OYP_VERSION_CODE").orElse("1").get().toInt()
val supportedAndroidAbis = listOf("arm64-v8a")
val rustAndroidTarget = "aarch64-linux-android"
val rustNativeLibrary = rootProject.layout.projectDirectory.file("target/$rustAndroidTarget/debug/liboffline_yt_core.so")
val generatedJniLibsDir = layout.buildDirectory.dir("generated/rustJniLibs").get().asFile
val generatedUniffiKotlinDir = layout.buildDirectory.dir("generated/uniffiKotlin").get().asFile

val prepareRustJniLibs by tasks.registering(Copy::class) {
    from(rustNativeLibrary)
    into(generatedJniLibsDir.resolve(supportedAndroidAbis.single()))
    doFirst {
        check(rustNativeLibrary.asFile.isFile) {
            "Missing Rust Android native library ${rustNativeLibrary.asFile}; build offline-yt-core for $rustAndroidTarget first"
        }
    }
}

val generateUniffiKotlinBindings by tasks.registering(Exec::class) {
    workingDir = rootProject.projectDir
    outputs.dir(generatedUniffiKotlinDir)
    inputs.files(
        rootProject.fileTree("core/src") { include("**/*.rs") },
        rootProject.file("core/Cargo.toml"),
        rootProject.file("core/uniffi.toml"),
        rootProject.file("Cargo.lock"),
    )
    commandLine(
        "bash",
        "-lc",
        "cargo build -p offline-yt-core && rm -rf '${generatedUniffiKotlinDir.absolutePath}' && cargo run -p uniffi-bindgen -- generate target/debug/liboffline_yt_core.so --language kotlin --out-dir '${generatedUniffiKotlinDir.absolutePath}'",
    )
}

val verifyUniffiKotlinBindings by tasks.registering {
    dependsOn(generateUniffiKotlinBindings)
    doLast {
        val generatedFiles = generatedUniffiKotlinDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        check(generatedFiles.isNotEmpty()) {
            "UniFFI generated no Kotlin files under $generatedUniffiKotlinDir"
        }
        check(generatedFiles.any { file -> file.readText().contains("package com.ekkus.offlineytplayer.core") }) {
            "Generated UniFFI Kotlin files do not use the expected Android package"
        }
    }
}

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
        ndk {
            abiFilters += supportedAndroidAbis
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("main").jniLibs.srcDir(generatedJniLibsDir)
    sourceSets.getByName("main").java.srcDir(generatedUniffiKotlinDir)

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

tasks.matching { it.name == "mergeDebugJniLibFolders" || it.name == "mergeReleaseJniLibFolders" }.configureEach {
    dependsOn(prepareRustJniLibs)
}

tasks.matching { it.name.matches(Regex("compile.*Kotlin")) }.configureEach {
    dependsOn(generateUniffiKotlinBindings)
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
    implementation(libs.jna)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
