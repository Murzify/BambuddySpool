package com.murzify.bambuddyspool

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
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
    private val adapter = AndroidNfcIntentAdapter { "a1b2" }

    @Test
    fun canonicalNdefDiscoveryStartsPlatformNeutralProcessingInput() {
        val observation = adapter.read(
            Intent(NfcAdapter.ACTION_NDEF_DISCOVERED, Uri.parse("bambuddy-spool://spool/42"))
        )

        assertEquals("a1b2", observation?.fingerprint)
        assertEquals("bambuddy-spool://spool/42", observation?.payload)
    }

    @Test
    fun unrelatedOrNoncanonicalUrisAreIgnored() {
        assertNull(adapter.read(Intent(NfcAdapter.ACTION_NDEF_DISCOVERED, Uri.parse("https://example.test/spool/42"))))
        assertNull(adapter.read(Intent(NfcAdapter.ACTION_NDEF_DISCOVERED, Uri.parse("bambuddy-spool://spool/042"))))
        assertNull(adapter.read(Intent(Intent.ACTION_VIEW, Uri.parse("bambuddy-spool://spool/42"))))
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
}
