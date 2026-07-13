import dev.detekt.gradle.extensions.DetektExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.spotless)
}

subprojects {
    pluginManager.withPlugin("dev.detekt") {
        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            basePath = rootProject.projectDir
        }
    }
}

val ktlintVersion = libs.versions.ktlint.get()

spotless {
    kotlin {
        target("androidApp/src/**/*.kt", "shared/src/**/*.kt")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("*.gradle.kts", "androidApp/*.gradle.kts", "shared/*.gradle.kts")
        ktlint(ktlintVersion)
    }
}
