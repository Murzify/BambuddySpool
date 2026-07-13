package com.murzify.bambuddyspool

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommonArchitectureTest {

    @Test
    fun commonMainRejectsPlatformImports() {
        val fixture = checkNotNull(javaClass.getResource("/architecture/forbidden-android-import.kt.txt"))
            .readText()
        assertTrue(hasForbiddenPlatformImport(fixture))

        val commonMain = kotlinFiles(File("src/commonMain/kotlin"))
        assertTrue(commonMain.isNotEmpty())
        assertFalse(commonMain.any { hasForbiddenPlatformImport(it.readText()) })
    }

    @Test
    fun domainRejectsFrameworkImports() {
        val fixture = checkNotNull(javaClass.getResource("/architecture/forbidden-domain-import.kt.txt"))
            .readText()
        assertTrue(hasForbiddenDomainImport(fixture))

        val domainFiles = kotlinFiles(File("src/commonMain/kotlin/com/murzify/bambuddyspool/core/domain"))
        assertFalse(domainFiles.any { hasForbiddenDomainImport(it.readText()) })
    }

    @Test
    fun featuresRejectCrossFeatureImports() {
        val fixture = checkNotNull(javaClass.getResource("/architecture/forbidden-cross-feature-import.kt.txt"))
            .readText()
        assertTrue(hasCrossFeatureImport(fixture, "home"))

        val featureRoot = File("src/commonMain/kotlin/com/murzify/bambuddyspool/feature")
        val featureFiles = kotlinFiles(featureRoot)
        assertTrue(featureFiles.isNotEmpty())
        assertFalse(featureFiles.any { file ->
            val owner = file.relativeTo(featureRoot).invariantSeparatorsPath.substringBefore('/')
            hasCrossFeatureImport(file.readText(), owner)
        })
    }

    @Test
    fun androidShellRejectsDomainAndFeatureImports() {
        val shellFiles = kotlinFiles(File("../androidApp/src/main/kotlin"))
        assertTrue(shellFiles.isNotEmpty())
        assertFalse(shellFiles.any { source ->
            source.readText().lineSequence().any { line ->
                line.startsWith("import com.murzify.bambuddyspool.core.domain.") ||
                    line.startsWith("import com.murzify.bambuddyspool.core.application.") ||
                    line.startsWith("import com.murzify.bambuddyspool.feature.")
            }
        })
    }

    @Test
    fun commonMainRejectsLargeExpectServices() {
        val commonMain = kotlinFiles(File("src/commonMain/kotlin"))
        assertFalse(commonMain.any { file ->
            file.readText().contains(Regex("\\bexpect\\s+(class|interface|object)\\b"))
        })
    }

    private fun hasForbiddenPlatformImport(source: String): Boolean = source.lineSequence().any { line ->
        line.startsWith("import android.") ||
            line.startsWith("import java.") ||
            line.startsWith("import platform.")
    }

    private fun hasForbiddenDomainImport(source: String): Boolean = source.lineSequence().any { line ->
        FORBIDDEN_DOMAIN_IMPORTS.any(line::startsWith)
    }

    private fun hasCrossFeatureImport(source: String, owner: String): Boolean = source.lineSequence().any { line ->
        val importedFeature = FEATURE_IMPORT.find(line)?.groupValues?.get(1)
        importedFeature != null && importedFeature != owner
    }

    private fun kotlinFiles(root: File): List<File> = if (root.exists()) {
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    } else {
        emptyList()
    }

    private companion object {
        val FORBIDDEN_DOMAIN_IMPORTS = listOf(
            "import android.",
            "import androidx.",
            "import com.arkivanov.decompose.",
            "import dev.zacsweers.metro.",
            "import io.ktor.",
            "import java.",
            "import platform.",
        )
        val FEATURE_IMPORT = Regex("^import com\\.murzify\\.bambuddyspool\\.feature\\.([^.]+)\\.")
    }
}
