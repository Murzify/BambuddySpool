package com.murzify.bambuddyspool

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.SystemClock
import com.murzify.bambuddyspool.core.nfc.CanonicalNfcPayloadCodec
import com.murzify.bambuddyspool.core.nfc.NfcPayloadParseResult
import com.murzify.bambuddyspool.core.platform.NfcObservation

/**
 * Converts only Android's canonical NDEF-discovery entry into the shared NFC hand-off.
 *
 * Android intents and tags stop here. The URI is admitted only when the framework-provided tag and NDEF message
 * independently contain the same single canonical record. This rejects ordinary explicit/view intents, which can
 * otherwise imitate the NDEF action and data URI without a physical scan.
 */
internal class AndroidNfcIntentAdapter(
    private val fingerprintFromIntent: (Intent) -> String? = AndroidNfcIntentAdapter::tagFingerprint,
    private val monotonicClockMillis: () -> Long = SystemClock::elapsedRealtime,
    private val ndefMessageFromIntent: (Intent) -> NdefMessage? = AndroidNfcIntentAdapter::ndefMessage,
    private val hasPhysicalTag: (Intent) -> Boolean = AndroidNfcIntentAdapter::hasPhysicalTag
) {
    fun read(intent: Intent?): NfcObservation? = intent
        ?.takeIf { it.action == NfcAdapter.ACTION_NDEF_DISCOVERED }
        ?.dataString
        ?.let(CanonicalNfcPayloadCodec::parse)
        ?.let { it as? NfcPayloadParseResult.ValidSpoolPayload }
        ?.takeIf { parsed -> parsed.canonicalUri == intent.dataString }
        ?.let { parsed ->
            intent.verifiedCanonicalNdefUri()
                ?.takeIf { it == parsed.canonicalUri }
                ?.let {
                    fingerprintFromIntent(intent)
                }
                ?.takeIf(String::isNotBlank)
                ?.let { fingerprint ->
                    NfcObservation(
                        fingerprint = fingerprint,
                        payload = parsed.canonicalUri,
                        monotonicTimestampMillis = monotonicClockMillis()
                    )
                }
        }

    private fun Intent.verifiedCanonicalNdefUri(): String? {
        // EXTRA_TAG cannot be synthesized by a normal caller: it is framework-owned Parcelable scan evidence.
        if (!hasPhysicalTag(this)) return null
        return ndefMessageFromIntent(this)?.let(AndroidNdefMessageCodec::canonicalUri)
    }

    private companion object {
        @Suppress("DEPRECATION")
        fun tagFingerprint(intent: Intent): String? = (intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag)
            ?.id
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

        @Suppress("DEPRECATION")
        fun hasPhysicalTag(intent: Intent): Boolean = (intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag)
            ?.id
            ?.isNotEmpty() == true

        @Suppress("DEPRECATION")
        fun ndefMessage(intent: Intent): NdefMessage? = intent
            .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            ?.singleOrNull() as? NdefMessage
    }
}
