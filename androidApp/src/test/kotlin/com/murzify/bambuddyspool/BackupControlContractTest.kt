package com.murzify.bambuddyspool

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupControlContractTest {

    @Test
    fun manifestAndBothBackupPoliciesExcludeEverySupportedStorageDomain() {
        val projectRoot = File(requireNotNull(System.getProperty("user.dir")))
        val manifest = projectRoot.resolve("src/main/AndroidManifest.xml").readText()
        val legacyRules = projectRoot.resolve("src/main/res/xml/backup_rules.xml").readText()
        val extractionRules = projectRoot.resolve("src/main/res/xml/data_extraction_rules.xml").readText()

        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))

        requiredDomains.forEach { domain ->
            assertTrue(legacyRules.contains("domain=\"$domain\" path=\".\""))
            assertTrue(extractionRules.contains("domain=\"$domain\" path=\".\""))
        }
    }

    @Test
    fun releaseManifestDoesNotReenableBackup() {
        val releaseSource = File(requireNotNull(System.getProperty("user.dir"))).resolve("src/release")

        assertFalse(
            releaseSource.walkTopDown().any { file ->
                file.isFile && file.readText().contains("allowBackup=\"true\"")
            }
        )
    }

    private companion object {
        val requiredDomains = listOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref"
        )
    }
}
