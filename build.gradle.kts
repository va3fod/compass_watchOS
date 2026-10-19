// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9.0's built-in Kotlin is 2.2.10, which cannot parse JDK 25.
// Keep the compiler on 2.3.x so this project builds on the Android Studio JBR.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}