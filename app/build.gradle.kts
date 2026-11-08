// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "local.torrenttv"
    compileSdk = 37

    defaultConfig {
        applicationId = "local.torrenttv"
        minSdk = 28
        targetSdk = 37
        versionCode = 6
        versionName = "0.2.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-39")
    runtimeOnly("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39")
    runtimeOnly("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39")
    runtimeOnly("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-39")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
