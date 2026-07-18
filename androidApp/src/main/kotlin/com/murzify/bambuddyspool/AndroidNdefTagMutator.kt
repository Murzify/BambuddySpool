package com.murzify.bambuddyspool

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.murzify.bambuddyspool.core.domain.SpoolId
import com.murzify.bambuddyspool.core.domain.TagMutationAppliedButUnverified
import com.murzify.bambuddyspool.core.domain.TagMutationAppliedButUnverifiedReason
import com.murzify.bambuddyspool.core.domain.TagMutationNotApplied
import com.murzify.bambuddyspool.core.domain.TagMutationNotAppliedReason
import com.murzify.bambuddyspool.core.domain.TagMutationOutcome
import com.murzify.bambuddyspool.core.domain.TagMutationOutcomeUnknown
import com.murzify.bambuddyspool.core.domain.TagMutationOutcomeUnknownReason
import com.murzify.bambuddyspool.core.domain.TagMutationSuccess
import com.murzify.bambuddyspool.core.nfc.CanonicalNfcPayloadCodec
import com.murzify.bambuddyspool.core.nfc.CommonNdefMessage
import com.murzify.bambuddyspool.core.nfc.CommonNdefRecord
import com.murzify.bambuddyspool.core.nfc.NfcPayloadParseResult
import com.murzify.bambuddyspool.core.nfc.NfcReadClassification
import com.murzify.bambuddyspool.core.nfc.NfcReadClassifier
import com.murzify.bambuddyspool.core.nfc.NfcReadFailureReason
import com.murzify.bambuddyspool.core.nfc.UnsupportedTagReason
import com.murzify.bambuddyspool.feature.tagmutation.LiveTagMutationBridge
import com.murzify.bambuddyspool.feature.tagmutation.TagMutationOperation
import com.murzify.bambuddyspool.feature.tagmutation.TagMutationRead
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-only physical NDEF write transaction.
 *
 * The caller must already have current user authorization. This primitive deliberately exposes neither tag locking
 * nor a retry loop: a returned success is possible only after a newly opened NDEF connection reads the expected
 * canonical application URI.
 */
internal class AndroidNdefTagMutator(
    private val fingerprintOf: (Tag) -> String? = ::tagFingerprint,
    private val ndefOf: (Tag) -> Ndef? = Ndef::get,
    private val formatableOf: (Tag) -> NdefFormatable? = NdefFormatable::get
) {
    suspend fun write(
        tag: Tag,
        expectedFingerprint: String,
        expectedCanonicalUri: String,
        spoolId: SpoolId?
    ): TagMutationOutcome = withContext(Dispatchers.IO) {
        AndroidNdefWritePreflight.fingerprintMismatch(fingerprintOf(tag), expectedFingerprint)?.let {
            return@withContext it
        }
        if (!isCanonicalApplicationUri(expectedCanonicalUri)) {
            return@withContext TagMutationNotApplied(TagMutationNotAppliedReason.PreconditionsFailed)
        }

        val message = AndroidNdefMessageCodec.singleUriMessage(expectedCanonicalUri)
        val ndef = ndefOf(tag)
        val formatable = formatableOf(tag)
        when {
            ndef != null -> writeNdef(
                ndef,
                tag,
                message,
                { message, _ -> AndroidNdefMessageCodec.canonicalUri(message) == expectedCanonicalUri },
                spoolId
            )
            formatable != null -> formatAndVerify(
                formatable,
                tag,
                message,
                { message, _ -> AndroidNdefMessageCodec.canonicalUri(message) == expectedCanonicalUri },
                spoolId
            )
            else -> TagMutationNotApplied(TagMutationNotAppliedReason.UnsupportedTag)
        }
    }

    /** Clears to a normalized empty NDEF message without exposing an irreversible tag-locking API. */
    suspend fun clear(tag: Tag, expectedFingerprint: String): TagMutationOutcome = withContext(Dispatchers.IO) {
        AndroidNdefWritePreflight.fingerprintMismatch(fingerprintOf(tag), expectedFingerprint)?.let {
            return@withContext it
        }
        val message = AndroidNdefMessageCodec.normalizedEmptyMessage()
        val ndef = ndefOf(tag)
        val formatable = formatableOf(tag)
        when {
            ndef != null -> writeNdef(
                ndef,
                tag,
                message,
                { reread, writable -> writable && AndroidNdefMessageCodec.isNormalizedEmpty(reread) },
                null
            )
            formatable != null -> formatAndVerify(
                formatable,
                tag,
                message,
                { reread, writable -> writable && AndroidNdefMessageCodec.isNormalizedEmpty(reread) },
                null
            )
            else -> TagMutationNotApplied(TagMutationNotAppliedReason.UnsupportedTag)
        }
    }

    @Suppress("ReturnCount")
    private fun writeNdef(
        ndef: Ndef,
        tag: Tag,
        message: NdefMessage,
        verification: (NdefMessage?, Boolean) -> Boolean,
        spoolId: SpoolId?
    ): TagMutationOutcome {
        try {
            ndef.connect()
            AndroidNdefWritePreflight.capabilityFailure(
                AndroidNdefWriteCapability.WritableNdef(ndef.isWritable, ndef.maxSize),
                message.byteArrayLength
            )?.let { return it }
        } catch (_: TagLostException) {
            return TagMutationNotApplied(TagMutationNotAppliedReason.TagRemovedBeforeWrite)
        } catch (_: IOException) {
            return TagMutationNotApplied(TagMutationNotAppliedReason.UnsupportedTag)
        } finally {
            ndef.closeQuietly()
        }

        try {
            ndef.connect()
            ndef.writeNdefMessage(message)
        } catch (_: TagLostException) {
            return TagMutationOutcomeUnknown(TagMutationOutcomeUnknownReason.TagRemovedAfterWriteStarted)
        } catch (_: IOException) {
            return TagMutationOutcomeUnknown(TagMutationOutcomeUnknownReason.PlatformResultUnavailable)
        } catch (_: FormatException) {
            return TagMutationOutcomeUnknown(TagMutationOutcomeUnknownReason.PlatformResultUnavailable)
        } finally {
            ndef.closeQuietly()
        }
        return rereadAndVerify(tag, verification, spoolId)
    }

    @Suppress("ReturnCount")
    private fun formatAndVerify(
        formatable: NdefFormatable,
        tag: Tag,
        message: NdefMessage,
        verification: (NdefMessage?, Boolean) -> Boolean,
        spoolId: SpoolId?
    ): TagMutationOutcome {
        try {
            formatable.connect()
        } catch (_: TagLostException) {
            return TagMutationNotApplied(TagMutationNotAppliedReason.TagRemovedBeforeWrite)
        } catch (_: IOException) {
            return TagMutationNotApplied(TagMutationNotAppliedReason.UnsupportedTag)
        } finally {
            formatable.closeQuietly()
        }

        try {
            formatable.connect()
            // Android does not expose capacity before a tag is formatted. format() rejects an oversized message.
            formatable.format(message)
        } catch (_: TagLostException) {
            return TagMutationOutcomeUnknown(TagMutationOutcomeUnknownReason.TagRemovedAfterWriteStarted)
        } catch (_: IOException) {
            return TagMutationOutcomeUnknown(TagMutationOutcomeUnknownReason.PlatformResultUnavailable)
        } catch (_: FormatException) {
            return TagMutationOutcomeUnknown(TagMutationOutcomeUnknownReason.PlatformResultUnavailable)
        } finally {
            formatable.closeQuietly()
        }
        return rereadAndVerify(tag, verification, spoolId)
    }

    @Suppress("ReturnCount")
    private fun rereadAndVerify(
        tag: Tag,
        verification: (NdefMessage?, Boolean) -> Boolean,
        spoolId: SpoolId?
    ): TagMutationOutcome {
        val reread = ndefOf(tag) ?: return TagMutationAppliedButUnverified(
            TagMutationAppliedButUnverifiedReason.RereadFailed
        )
        val message = try {
            reread.connect()
            reread.cachedNdefMessage
        } catch (_: TagLostException) {
            return TagMutationAppliedButUnverified(TagMutationAppliedButUnverifiedReason.VerificationInterrupted)
        } catch (_: IOException) {
            return TagMutationAppliedButUnverified(TagMutationAppliedButUnverifiedReason.RereadFailed)
        } finally {
            reread.closeQuietly()
        }
        return if (verification(message, reread.isWritable)) {
            TagMutationSuccess(spoolId)
        } else {
            TagMutationAppliedButUnverified(TagMutationAppliedButUnverifiedReason.VerificationMismatch)
        }
    }

    private fun isCanonicalApplicationUri(uri: String): Boolean =
        (CanonicalNfcPayloadCodec.parse(uri) as? NfcPayloadParseResult.ValidSpoolPayload)?.canonicalUri == uri

    private fun Ndef.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
            // The transaction outcome is determined by the explicit write/read calls, not close bookkeeping.
        }
    }

    private fun NdefFormatable.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
            // The transaction outcome is determined by the explicit format/read calls, not close bookkeeping.
        }
    }

    private companion object {
        fun tagFingerprint(tag: Tag): String? = tag.id
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

/** Small deterministic policy seam for the capabilities Android reports before a physical write begins. */
internal sealed interface AndroidNdefWriteCapability {
    data class WritableNdef(val writable: Boolean, val maxSize: Int) : AndroidNdefWriteCapability
    data object NdefFormatable : AndroidNdefWriteCapability
    data object Unsupported : AndroidNdefWriteCapability
}

internal object AndroidNdefWritePreflight {
    fun fingerprintMismatch(actual: String?, expected: String): TagMutationNotApplied? =
        if (actual == expected) null else TagMutationNotApplied(TagMutationNotAppliedReason.DifferentTagDetected)

    fun capabilityFailure(capability: AndroidNdefWriteCapability, messageSize: Int): TagMutationNotApplied? =
        when (capability) {
            is AndroidNdefWriteCapability.WritableNdef -> when {
                !capability.writable -> TagMutationNotApplied(TagMutationNotAppliedReason.ReadOnlyTag)
                messageSize > capability.maxSize ->
                    TagMutationNotApplied(TagMutationNotAppliedReason.InsufficientCapacity)
                else -> null
            }
            AndroidNdefWriteCapability.NdefFormatable -> null
            AndroidNdefWriteCapability.Unsupported -> TagMutationNotApplied(TagMutationNotAppliedReason.UnsupportedTag)
        }
}

/** Conversion is deliberately tiny so platform NDEF objects never leave Android code. */
internal object AndroidNdefMessageCodec {
    fun singleUriMessage(canonicalUri: String): NdefMessage = NdefMessage(arrayOf(NdefRecord.createUri(canonicalUri)))

    /** Android rejects a zero-record [NdefMessage], so a single TNF_EMPTY record is our normalized empty form. */
    fun normalizedEmptyMessage(): NdefMessage = NdefMessage(
        arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, ByteArray(0), ByteArray(0), ByteArray(0)))
    )

    fun canonicalUri(message: NdefMessage?): String? = message
        ?.takeUnless(::isNormalizedEmpty)
        ?.let { ndefMessage ->
            val common = CommonNdefMessage(
                records = ndefMessage.records.map { record ->
                    record.takeIf { it.isWellKnownUri() }
                        ?.toUri()
                        ?.toString()
                        ?.let(CommonNdefRecord::Uri)
                        ?: CommonNdefRecord.Unknown(record.tnf.toString(), record.payload)
                }
            )
            (NfcReadClassifier.classify(common) as? NfcReadClassification.ValidSpoolPayload)?.canonicalUri
        }

    fun isNormalizedEmpty(message: NdefMessage?): Boolean = message?.records?.let { records ->
        records.size == 1 && records.single().tnf == NdefRecord.TNF_EMPTY
    } == true

    private fun NdefRecord.isWellKnownUri(): Boolean =
        tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_URI)
}

/** Android-owned process-local hand-off for the shared link workflow. */
internal class AndroidLiveTagMutationBridge(
    private val mutator: AndroidNdefTagMutator = AndroidNdefTagMutator()
) : LiveTagMutationBridge {
    @Volatile private var armed = false
    @Volatile private var liveTag: Tag? = null
    @Volatile var onRead: ((TagMutationRead) -> Unit)? = null
    /** Activity uses this only to toggle Android foreground reader mode for an active link/retry flow. */
    @Volatile var onArmedChanged: ((Boolean) -> Unit)? = null
    val isArmed: Boolean get() = armed

    override fun beginRead() {
        liveTag = null
        armed = true
        onArmedChanged?.invoke(true)
    }

    override fun cancelRead() {
        armed = false
        liveTag = null
        onArmedChanged?.invoke(false)
    }

    /** Reads only while the shared workflow is awaiting a physical tag; this method never writes. */
    fun accept(tag: Tag) {
        if (!armed) return
        val fingerprint = tagFingerprint(tag) ?: return
        val classification = tag.readClassification()
        liveTag = tag
        onRead?.invoke(TagMutationRead(fingerprint, classification))
    }

    override suspend fun mutate(expectedFingerprint: String, operation: TagMutationOperation): TagMutationOutcome {
        val tag = liveTag ?: return TagMutationNotApplied(TagMutationNotAppliedReason.DifferentTagDetected)
        armed = false
        liveTag = null
        onArmedChanged?.invoke(false)
        return when (operation) {
            is TagMutationOperation.Link -> mutator.write(tag, expectedFingerprint, operation.canonicalUri, operation.spoolId)
            TagMutationOperation.Clear -> mutator.clear(tag, expectedFingerprint)
        }
    }
}

private fun Tag.readClassification(): NfcReadClassification {
    val ndef = Ndef.get(this)
    if (AndroidNdefReadCapability.select(ndef != null, NdefFormatable.get(this) != null) ==
        AndroidNdefReadCapability.FormattableEmpty
    ) {
        return NfcReadClassification.Empty
    }
    ndef ?: return NfcReadClassification.UnsupportedTag(UnsupportedTagReason.NdefUnavailable)
    return try {
        ndef.connect()
        val message = ndef.cachedNdefMessage ?: return NfcReadClassifier.classify(CommonNdefMessage.Empty)
        NfcReadClassifier.classify(
            CommonNdefMessage(message.records.map { record ->
                record.takeIf { it.tnf == NdefRecord.TNF_WELL_KNOWN && it.type.contentEquals(NdefRecord.RTD_URI) }
                    ?.toUri()?.toString()?.let(CommonNdefRecord::Uri)
                    ?: CommonNdefRecord.Unknown(record.tnf.toString(), record.payload)
            })
        )
    } catch (_: TagLostException) {
        NfcReadClassification.ReadFailure(NfcReadFailureReason.TagRemoved)
    } catch (_: IOException) {
        NfcReadClassification.ReadFailure(NfcReadFailureReason.PlatformError)
    } finally {
        try {
            ndef.close()
        } catch (_: IOException) {
            // A close failure cannot turn a completed read into authorization.
        }
    }
}

/**
 * Decides only whether Android can inspect an NDEF payload or format a blank tag. It deliberately does not turn
 * non-NDEF technologies into writable tags: only [FormattableEmpty] reaches the explicit link confirmation.
 */
internal enum class AndroidNdefReadCapability {
    Ndef,
    FormattableEmpty,
    Unsupported;

    companion object {
        fun select(hasNdef: Boolean, hasNdefFormatable: Boolean): AndroidNdefReadCapability = when {
            hasNdef -> Ndef
            hasNdefFormatable -> FormattableEmpty
            else -> Unsupported
        }
    }
}

private fun tagFingerprint(tag: Tag): String? = tag.id.takeIf { it.isNotEmpty() }
    ?.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
