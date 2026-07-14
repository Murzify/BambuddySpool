import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
}

val packageName = gradle.extensions.extraProperties["BAMBUDDY_PACKAGE_NAME"] as String
val versionCodeValue = (gradle.extensions.extraProperties["BAMBUDDY_VERSION_CODE"] as String).toInt()
val versionNameValue = gradle.extensions.extraProperties["BAMBUDDY_VERSION_NAME"] as String
val jvmTargetVersion = libs.versions.jvm.target.get()

detekt {
    source.setFrom(files("src/main/kotlin"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(jvmTargetVersion)
    }
}
dependencies {
    implementation(projects.shared)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
}

android {
    namespace = packageName
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = packageName
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionCodeValue
        versionName = versionNameValue
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
        targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
    }
    lint {
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        htmlReport = true
        sarifReport = true
        // TECHSPEC 18.6 keeps warnings visible while errors remain blocking.
        warningsAsErrors = false
    }
}
