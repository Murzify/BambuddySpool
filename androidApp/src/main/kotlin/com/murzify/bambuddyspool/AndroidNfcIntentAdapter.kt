package com.murzify.bambuddyspool

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.SystemClock
import com.murzify.bambuddyspool.core.nfc.CanonicalNfcPayloadCodec
import com.murzify.bambuddyspool.core.nfc.NfcPayloadParseResult
import com.murzify.bambuddyspool.core.platform.NfcObservation

/**
 * Converts only Android's canonical NDEF-discovery entry into the shared NFC hand-off.
 *
 * Android intents and tags stop here. A missing tag identity is rejected because later NFC coordination relies on a
 * physical-tag fingerprint and must not accidentally coalesce different tags.
 */
internal class AndroidNfcIntentAdapter(
    private val fingerprintFromIntent: (Intent) -> String? = AndroidNfcIntentAdapter::tagFingerprint,
    private val monotonicClockMillis: () -> Long = SystemClock::elapsedRealtime
) {
    fun read(intent: Intent?): NfcObservation? = intent
        ?.takeIf { it.action == NfcAdapter.ACTION_NDEF_DISCOVERED }
        ?.dataString
        ?.let(CanonicalNfcPayloadCodec::parse)
        ?.let { it as? NfcPayloadParseResult.ValidSpoolPayload }
        ?.takeIf { parsed -> parsed.canonicalUri == intent.dataString }
        ?.let { parsed ->
            fingerprintFromIntent(intent)?.let { fingerprint ->
                NfcObservation(
                    fingerprint = fingerprint,
                    payload = parsed.canonicalUri,
                    monotonicTimestampMillis = monotonicClockMillis()
                )
            }
        }

    private companion object {
        @Suppress("DEPRECATION")
        fun tagFingerprint(intent: Intent): String? = (intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag)
            ?.id
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
