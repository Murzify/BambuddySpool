import java.util.Properties
import org.gradle.api.initialization.resolve.RepositoriesMode

val projectMetadata = Properties().apply {
    load(
        providers.fileContents(layout.settingsDirectory.file("config/project-metadata.xcconfig"))
            .asText.get().reader()
    )
}

projectMetadata.forEach { key, value ->
    gradle.extensions.extraProperties[key.toString()] = value.toString()
}

fun metadata(name: String): String =
    requireNotNull(projectMetadata.getProperty(name)) { "Missing project metadata: $name" }

rootProject.name = metadata("BAMBUDDY_PRODUCT_NAME")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")
