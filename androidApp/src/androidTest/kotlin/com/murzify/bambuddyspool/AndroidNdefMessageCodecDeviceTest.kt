package com.murzify.bambuddyspool

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.murzify.bambuddyspool.core.domain.TagMutationNotAppliedReason
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNdefMessageCodecDeviceTest {
    @Test
    fun readCapabilityPassesOnlyBlankFormattableTagsToTheExplicitLinkPath() {
        assertEquals(
            AndroidNdefReadCapability.Ndef,
            AndroidNdefReadCapability.select(hasNdef = true, hasNdefFormatable = true)
        )
        assertEquals(
            AndroidNdefReadCapability.FormattableEmpty,
            AndroidNdefReadCapability.select(hasNdef = false, hasNdefFormatable = true)
        )
        assertEquals(
            AndroidNdefReadCapability.Unsupported,
            AndroidNdefReadCapability.select(hasNdef = false, hasNdefFormatable = false)
        )
    }

    @Test
    fun generatedMessageContainsExactlyOneCanonicalUriRecord() {
        val message = AndroidNdefMessageCodec.singleUriMessage("bambuddy-spool://spool/42")

        assertEquals(1, message.records.size)
        assertEquals("bambuddy-spool://spool/42", message.records.single().toUri()?.toString())
        assertEquals("bambuddy-spool://spool/42", AndroidNdefMessageCodec.canonicalUri(message))
    }

    @Test
    fun canonicalVerificationAcceptsUriIdentifierCodeCompression() {
        val compressed = NdefMessage(arrayOf(NdefRecord.createUri("bambuddy-spool://spool/42")))

        assertTrue(compressed.byteArrayLength > 0)
        assertEquals("bambuddy-spool://spool/42", AndroidNdefMessageCodec.canonicalUri(compressed))
    }

    @Test
    fun canonicalVerificationRejectsMultipleAndNonUriRecordsButNormalizesReadUri() {
        val multiple = NdefMessage(
            arrayOf(
                NdefRecord.createUri("bambuddy-spool://spool/42"),
                NdefRecord.createUri("bambuddy-spool://spool/43")
            )
        )
        val leadingZero = NdefMessage(arrayOf(NdefRecord.createUri("bambuddy-spool://spool/042")))
        val text = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", "bambuddy-spool://spool/42")))
        val absoluteUri = NdefMessage(
            arrayOf(
                NdefRecord(
                    NdefRecord.TNF_ABSOLUTE_URI,
                    "bambuddy-spool://spool/42".encodeToByteArray(),
                    ByteArray(0),
                    ByteArray(0)
                )
            )
        )

        assertNull(AndroidNdefMessageCodec.canonicalUri(multiple))
        assertEquals("bambuddy-spool://spool/42", AndroidNdefMessageCodec.canonicalUri(leadingZero))
        assertNull(AndroidNdefMessageCodec.canonicalUri(text))
        assertNull(AndroidNdefMessageCodec.canonicalUri(absoluteUri))
    }

    @Test
    fun generatedMessagePreflightRejectsDifferentReadOnlyAndTooSmallTagsWithoutWrite() {
        val message = AndroidNdefMessageCodec.singleUriMessage("bambuddy-spool://spool/42")

        assertEquals(
            TagMutationNotAppliedReason.DifferentTagDetected,
            AndroidNdefWritePreflight.fingerprintMismatch("other", "expected")?.reason
        )
        assertEquals(
            TagMutationNotAppliedReason.ReadOnlyTag,
            AndroidNdefWritePreflight.capabilityFailure(
                AndroidNdefWriteCapability.WritableNdef(writable = false, maxSize = message.byteArrayLength),
                message.byteArrayLength
            )?.reason
        )
        assertEquals(
            TagMutationNotAppliedReason.InsufficientCapacity,
            AndroidNdefWritePreflight.capabilityFailure(
                AndroidNdefWriteCapability.WritableNdef(writable = true, maxSize = message.byteArrayLength - 1),
                message.byteArrayLength
            )?.reason
        )
        assertNull(
            AndroidNdefWritePreflight.capabilityFailure(
                AndroidNdefWriteCapability.NdefFormatable,
                message.byteArrayLength
            )
        )
    }

    @Test
    fun androidNormalizedEmptyNdefMessageUsesOnlyTheStandardEmptyRecord() {
        val empty = AndroidNdefMessageCodec.normalizedEmptyMessage()
        val uri = AndroidNdefMessageCodec.singleUriMessage("bambuddy-spool://spool/42")

        assertTrue(AndroidNdefMessageCodec.isNormalizedEmpty(empty))
        assertEquals(NdefRecord.TNF_EMPTY, empty.records.single().tnf)
        assertTrue(!AndroidNdefMessageCodec.isNormalizedEmpty(uri))
    }
}
