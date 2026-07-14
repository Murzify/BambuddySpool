package com.murzify.bambuddyspool.core.nfc

import com.murzify.bambuddyspool.core.domain.SpoolId

object CanonicalNfcPayloadCodec {
    const val SCHEME = "bambuddy-spool"
    const val HOST = "spool"

    private const val SEPARATOR = "://"
    private const val PATH_SEPARATOR = '/'
    private const val MAX_URI_LENGTH = 256

    fun encode(spoolId: SpoolId): String = "$SCHEME://$HOST/${spoolId.value}"

    fun parse(uri: String): NfcPayloadParseResult = when {
        uri.length > MAX_URI_LENGTH -> NfcPayloadParseResult.Malformed(MalformedNfcPayloadReason.PayloadTooLarge)
        uri.isEmpty() || uri.any { it.isWhitespace() } -> NfcPayloadParseResult.Malformed(
            MalformedNfcPayloadReason.BlankOrWhitespace
        )
        else -> parseNonBlankUri(uri)
    }

    private fun parseNonBlankUri(uri: String): NfcPayloadParseResult {
        val separatorIndex = uri.indexOf(SEPARATOR)
        return when {
            separatorIndex <= 0 -> NfcPayloadParseResult.Malformed(MalformedNfcPayloadReason.MissingSchemeSeparator)
            else -> parseScheme(uri = uri, separatorIndex = separatorIndex)
        }
    }

    private fun parseScheme(uri: String, separatorIndex: Int): NfcPayloadParseResult {
        val scheme = uri.substring(startIndex = 0, endIndex = separatorIndex)
        return when {
            !scheme.equals(SCHEME, ignoreCase = true) -> NfcPayloadParseResult.Unknown(
                UnknownNfcPayloadReason.UnsupportedScheme
            )
            else -> parseAuthority(uri.substring(startIndex = separatorIndex + SEPARATOR.length))
        }
    }

    private fun parseAuthority(authorityAndPath: String): NfcPayloadParseResult {
        val hostEnd = authorityAndPath.indexOf(PATH_SEPARATOR)
        return when {
            hostEnd < 0 -> NfcPayloadParseResult.Malformed(MalformedNfcPayloadReason.MissingIdSegment)
            authorityAndPath.substring(startIndex = 0, endIndex = hostEnd) != HOST -> NfcPayloadParseResult.Unknown(
                UnknownNfcPayloadReason.UnsupportedHost
            )
            else -> parsePath(authorityAndPath.substring(startIndex = hostEnd + 1))
        }
    }

    private fun parsePath(pathAndSuffix: String): NfcPayloadParseResult =
        validatePathAndSuffix(pathAndSuffix) ?: parseSpoolId(pathAndSuffix)

    private fun parseSpoolId(pathSegment: String): NfcPayloadParseResult {
        val spoolId = parsePositiveDecimalId(pathSegment)
        return if (spoolId == null) {
            NfcPayloadParseResult.Malformed(classifyInvalidId(pathSegment))
        } else {
            NfcPayloadParseResult.ValidSpoolPayload(
                spoolId = spoolId,
                canonicalUri = encode(spoolId)
            )
        }
    }

    private fun parsePositiveDecimalId(value: String): SpoolId? {
        var parsed = 0L
        var isInvalid = false
        for (character in value) {
            if (character !in '0'..'9') {
                isInvalid = true
            }

            if (!isInvalid) {
                val digit = character - '0'
                if (parsed > (Long.MAX_VALUE - digit) / 10L) {
                    isInvalid = true
                } else {
                    parsed = parsed * 10L + digit
                }
            }
        }

        return if (isInvalid) null else SpoolId.from(parsed)
    }

    private fun validatePathAndSuffix(value: String): NfcPayloadParseResult.Malformed? = when {
        value.indexOf('?') >= 0 -> NfcPayloadParseResult.Malformed(MalformedNfcPayloadReason.QueryNotAllowed)
        value.indexOf('#') >= 0 -> NfcPayloadParseResult.Malformed(MalformedNfcPayloadReason.FragmentNotAllowed)
        value.isEmpty() -> NfcPayloadParseResult.Malformed(MalformedNfcPayloadReason.MissingIdSegment)
        value.indexOf(
            PATH_SEPARATOR
        ) >= 0 -> NfcPayloadParseResult.Malformed(MalformedNfcPayloadReason.ExtraPathSegment)
        else -> null
    }

    private fun classifyInvalidId(value: String): MalformedNfcPayloadReason = when {
        value.firstOrNull() == '+' || value.firstOrNull() == '-' -> MalformedNfcPayloadReason.SignNotAllowed
        value.any { it.isWhitespace() } -> MalformedNfcPayloadReason.BlankOrWhitespace
        value.all { it in '0'..'9' } -> MalformedNfcPayloadReason.NonPositiveOrOverflowId
        else -> MalformedNfcPayloadReason.NonDecimalId
    }
}

sealed interface NfcPayloadParseResult {
    data class ValidSpoolPayload(val spoolId: SpoolId, val canonicalUri: String) : NfcPayloadParseResult
    data class Unknown(val reason: UnknownNfcPayloadReason) : NfcPayloadParseResult
    data class Malformed(val reason: MalformedNfcPayloadReason) : NfcPayloadParseResult
}

enum class UnknownNfcPayloadReason {
    UnsupportedScheme,
    UnsupportedHost,
    UnsupportedRecordType,
    MultipleRecords
}

enum class MalformedNfcPayloadReason {
    PayloadTooLarge,
    BlankOrWhitespace,
    MissingSchemeSeparator,
    MissingIdSegment,
    ExtraPathSegment,
    QueryNotAllowed,
    FragmentNotAllowed,
    SignNotAllowed,
    NonDecimalId,
    NonPositiveOrOverflowId
}
