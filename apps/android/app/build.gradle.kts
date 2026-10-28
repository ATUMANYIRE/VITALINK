plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.google.devtools.ksp")
}
val debugApiBaseUrl = providers.gradleProperty("apiBaseUrl")
    .orElse("http://10.0.2.2:8087/api/v1").get()
require(debugApiBaseUrl.startsWith("http://") || debugApiBaseUrl.startsWith("https://")) {
    "apiBaseUrl must be an explicit HTTP(S) URL for the debug build"
}
val debugApiLiteral = "\"" + debugApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
android {
    namespace = "org.vitalink.app"
    compileSdk = 35
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "org.vitalink.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-research"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE_URL", debugApiLiteral)
            manifestPlaceholders["allowCleartext"] = "true"
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"https://localhost/api/v1\"")
            manifestPlaceholders["allowCleartext"] = "false"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(project(":domain"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.5.0")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20",
            "com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.28",
            "com.squareup:javapoet:1.13.0",
            "androidx.test.espresso:espresso-idling-resource:3.6.1",
            "com.google.code.findbugs:jsr305:3.0.2",
            "androidx.test:core:1.6.1",
        )
    }
}
