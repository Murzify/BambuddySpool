package com.murzify.bambuddyspool

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNfcIntentAdapterDeviceTest {
    private val adapter = AndroidNfcIntentAdapter(
        fingerprintFromIntent = { "a1b2" },
        monotonicClockMillis = { 123L },
        hasPhysicalTag = { true }
    )

    @Test
    fun canonicalNdefDiscoveryStartsPlatformNeutralProcessingInput() {
        val observation = adapter.read(
            canonicalIntent("bambuddy-spool://spool/42")
        )

        assertEquals("a1b2", observation?.fingerprint)
        assertEquals("bambuddy-spool://spool/42", observation?.payload)
        assertEquals(123L, observation?.monotonicTimestampMillis)
    }

    @Test
    fun unrelatedOrNoncanonicalUrisAreIgnored() {
        assertNull(adapter.read(canonicalIntent("https://example.test/spool/42")))
        assertNull(adapter.read(canonicalIntent("bambuddy-spool://spool/042")))
        assertNull(adapter.read(Intent(Intent.ACTION_VIEW, Uri.parse("bambuddy-spool://spool/42"))))
    }

    @Test
    fun missingOrMismatchedFrameworkNdefEvidenceCannotSpoofAScan() {
        val canonical = "bambuddy-spool://spool/42"
        val missingEvidence = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED, Uri.parse(canonical))
        assertNull(adapter.read(missingEvidence))
        assertNull(AndroidNfcIntentAdapter().read(canonicalIntent(canonical)))

        val mismatched = canonicalIntent(canonical).putExtra(
            NfcAdapter.EXTRA_NDEF_MESSAGES,
            arrayOf(NdefMessage(arrayOf(NdefRecord.createUri("bambuddy-spool://spool/43"))))
        )
        assertNull(adapter.read(mismatched))
    }

    @Test
    fun manifestUsesSingleTopAndResolvesOnlyTheNfcScheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity = context.packageManager.getActivityInfo(
            android.content.ComponentName(context, MainActivity::class.java),
            0
        )

        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, activity.launchMode)
        val canonical = Intent(
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            Uri.parse("bambuddy-spool://spool/42")
        ).setPackage(context.packageName)
        val unrelated = Intent(
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            Uri.parse("https://example.test/spool/42")
        ).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(canonical, 0).isNotEmpty())
        assertTrue(context.packageManager.queryIntentActivities(unrelated, 0).isEmpty())
    }

    private fun canonicalIntent(uri: String): Intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED, Uri.parse(uri))
        .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(NdefMessage(arrayOf(NdefRecord.createUri(uri)))))
}
