// Root build file. Plugins are declared here with `apply false` and applied
// per-module in app/build.gradle.kts, per standard Android Gradle plugin
// convention. Do not add dependencies or android {} blocks at this level.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}
