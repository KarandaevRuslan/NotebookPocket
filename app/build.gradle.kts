plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// One source of truth for native and embedded-web semantic version.
val webVersion = (groovy.json.JsonSlurper().parse(rootProject.file("web/package.json")) as Map<*, *>)["version"] as String

android {
    namespace = "dev.pocket.notebook"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.pocket.notebook"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 41
        versionName = webVersion
    }

    val releaseKeystore = providers.environmentVariable("NP_RELEASE_STORE_FILE").orNull
    if (!releaseKeystore.isNullOrBlank()) signingConfigs.create("release") {
        storeFile = file(releaseKeystore)
        storePassword = providers.environmentVariable("NP_RELEASE_STORE_PASSWORD").get()
        keyAlias = providers.environmentVariable("NP_RELEASE_KEY_ALIAS").get()
        keyPassword = providers.environmentVariable("NP_RELEASE_KEY_PASSWORD").get()
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.2")
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

tasks.register("verifyWebAssets") {
    doLast {
        val assets = file("src/main/assets/web")
        check(file("$assets/editor.html").isFile && file("$assets/preview.html").isFile && file("$assets/editor-boot.js").isFile && file("$assets/web-boot.js").isFile && file("$assets/preview-boot.js").isFile && (file("$assets/build-version.txt").isFile && file("$assets/build-version.txt").readText().trim() == webVersion)) {
            "Web UI missing/stale (requires v$webVersion). Run `cd web && npm install && npm run build` before building Android."
        }
    }
}
tasks.named("preBuild") { dependsOn("verifyWebAssets") }

// Versioned Room schema files will be generated during the Android KSP build.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
