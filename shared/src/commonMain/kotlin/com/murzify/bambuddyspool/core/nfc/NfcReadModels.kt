package com.murzify.bambuddyspool.core.nfc

import com.murzify.bambuddyspool.core.domain.SpoolId

data class CommonNdefMessage(val records: List<CommonNdefRecord>) {
    companion object {
        val Empty: CommonNdefMessage = CommonNdefMessage(records = emptyList())
    }
}

sealed interface CommonNdefRecord {
    data class Uri(val uri: String) : CommonNdefRecord
    data class Text(val text: String) : CommonNdefRecord
    data class Mime(val mediaType: String, val payload: ByteArray) : CommonNdefRecord {
        override fun equals(other: Any?): Boolean = other is Mime &&
            mediaType == other.mediaType &&
            payload.contentEquals(other.payload)

        override fun hashCode(): Int = 31 * mediaType.hashCode() + payload.contentHashCode()
    }

    data class Unknown(val type: String, val payload: ByteArray) : CommonNdefRecord {
        override fun equals(other: Any?): Boolean = other is Unknown &&
            type == other.type &&
            payload.contentEquals(other.payload)

        override fun hashCode(): Int = 31 * type.hashCode() + payload.contentHashCode()
    }
}

sealed interface NfcReadClassification {
    data object Empty : NfcReadClassification
    data class ValidSpoolPayload(val spoolId: SpoolId, val canonicalUri: String) : NfcReadClassification
    data class UnknownPayload(val reason: UnknownNfcPayloadReason) : NfcReadClassification
    data class MalformedNdef(val reason: MalformedNfcPayloadReason) : NfcReadClassification
    data class UnsupportedTag(val reason: UnsupportedTagReason) : NfcReadClassification
    data class ReadFailure(val reason: NfcReadFailureReason) : NfcReadClassification
}

enum class UnsupportedTagReason {
    NdefUnavailable,
    UnsupportedTechnology
}

enum class NfcReadFailureReason {
    TagRemoved,
    PlatformError
}

object NfcReadClassifier {
    fun classify(message: CommonNdefMessage): NfcReadClassification = when {
        message.records.isEmpty() -> NfcReadClassification.Empty
        message.records.size != 1 -> NfcReadClassification.UnknownPayload(UnknownNfcPayloadReason.MultipleRecords)
        else -> when (val record = message.records.single()) {
            is CommonNdefRecord.Uri -> when (val parseResult = CanonicalNfcPayloadCodec.parse(record.uri)) {
                is NfcPayloadParseResult.ValidSpoolPayload -> NfcReadClassification.ValidSpoolPayload(
                    spoolId = parseResult.spoolId,
                    canonicalUri = parseResult.canonicalUri
                )
                is NfcPayloadParseResult.Unknown -> NfcReadClassification.UnknownPayload(parseResult.reason)
                is NfcPayloadParseResult.Malformed -> NfcReadClassification.MalformedNdef(parseResult.reason)
            }
            is CommonNdefRecord.Text,
            is CommonNdefRecord.Mime,
            is CommonNdefRecord.Unknown -> NfcReadClassification.UnknownPayload(
                UnknownNfcPayloadReason.UnsupportedRecordType
            )
        }
    }
}
