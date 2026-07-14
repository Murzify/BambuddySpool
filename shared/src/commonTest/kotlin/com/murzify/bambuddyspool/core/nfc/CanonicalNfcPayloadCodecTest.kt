package com.murzify.bambuddyspool.core.nfc

import com.murzify.bambuddyspool.core.domain.SpoolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CanonicalNfcPayloadCodecTest {

    @Test
    fun encodeWritesCanonicalPayload() {
        assertEquals(
            expected = "bambuddy-spool://spool/42",
            actual = CanonicalNfcPayloadCodec.encode(spoolId(42L))
        )
    }

    @Test
    fun parseAcceptsCanonicalPayloadAndRoundTrips() {
        val parsed = assertIs<NfcPayloadParseResult.ValidSpoolPayload>(
            CanonicalNfcPayloadCodec.parse("bambuddy-spool://spool/42")
        )

        assertEquals(spoolId(42L), parsed.spoolId)
        assertEquals("bambuddy-spool://spool/42", parsed.canonicalUri)
        assertEquals(parsed.canonicalUri, CanonicalNfcPayloadCodec.encode(parsed.spoolId))
    }

    @Test
    fun parseAcceptsCaseInsensitiveSchemeOnly() {
        val parsed = assertIs<NfcPayloadParseResult.ValidSpoolPayload>(
            CanonicalNfcPayloadCodec.parse("BAMBUDDY-SPOOL://spool/7")
        )

        assertEquals(spoolId(7L), parsed.spoolId)
        assertEquals("bambuddy-spool://spool/7", parsed.canonicalUri)
    }

    @Test
    fun parseCanonicalizesLeadingZeros() {
        val parsed = assertIs<NfcPayloadParseResult.ValidSpoolPayload>(
            CanonicalNfcPayloadCodec.parse("bambuddy-spool://spool/00000042")
        )

        assertEquals(spoolId(42L), parsed.spoolId)
        assertEquals("bambuddy-spool://spool/42", parsed.canonicalUri)
    }

    @Test
    fun parseRejectsUnknownSchemeOrHostWithoutRecoveringId() {
        assertEquals(
            UnknownNfcPayloadReason.UnsupportedScheme,
            assertIs<NfcPayloadParseResult.Unknown>(
                CanonicalNfcPayloadCodec.parse("https://spool/42")
            ).reason
        )
        assertEquals(
            UnknownNfcPayloadReason.UnsupportedHost,
            assertIs<NfcPayloadParseResult.Unknown>(
                CanonicalNfcPayloadCodec.parse("bambuddy-spool://Spool/42")
            ).reason
        )
    }

    @Test
    fun parseRejectsMalformedStructure() {
        mapOf(
            "" to MalformedNfcPayloadReason.BlankOrWhitespace,
            " bambuddy-spool://spool/42" to MalformedNfcPayloadReason.BlankOrWhitespace,
            "bambuddy-spool:/spool/42" to MalformedNfcPayloadReason.MissingSchemeSeparator,
            "bambuddy-spool://spool" to MalformedNfcPayloadReason.MissingIdSegment,
            "bambuddy-spool://spool/" to MalformedNfcPayloadReason.MissingIdSegment,
            "bambuddy-spool://spool/42/extra" to MalformedNfcPayloadReason.ExtraPathSegment,
            "bambuddy-spool://spool/42?debug=true" to MalformedNfcPayloadReason.QueryNotAllowed,
            "bambuddy-spool://spool/42#fragment" to MalformedNfcPayloadReason.FragmentNotAllowed
        ).forEach { (input, reason) ->
            assertEquals(
                reason,
                assertIs<NfcPayloadParseResult.Malformed>(CanonicalNfcPayloadCodec.parse(input)).reason
            )
        }
    }

    @Test
    fun parseRejectsOversizedPayloadBeforeFurtherProcessing() {
        val result = CanonicalNfcPayloadCodec.parse("bambuddy-spool://spool/" + "1".repeat(234))

        assertEquals(
            MalformedNfcPayloadReason.PayloadTooLarge,
            assertIs<NfcPayloadParseResult.Malformed>(result).reason
        )
    }

    @Test
    fun parseRejectsInvalidIdForms() {
        mapOf(
            "bambuddy-spool://spool/0" to MalformedNfcPayloadReason.NonPositiveOrOverflowId,
            "bambuddy-spool://spool/0000" to MalformedNfcPayloadReason.NonPositiveOrOverflowId,
            "bambuddy-spool://spool/+42" to MalformedNfcPayloadReason.SignNotAllowed,
            "bambuddy-spool://spool/-42" to MalformedNfcPayloadReason.SignNotAllowed,
            "bambuddy-spool://spool/4 2" to MalformedNfcPayloadReason.BlankOrWhitespace,
            "bambuddy-spool://spool/42 " to MalformedNfcPayloadReason.BlankOrWhitespace,
            "bambuddy-spool://spool/4.2" to MalformedNfcPayloadReason.NonDecimalId,
            "bambuddy-spool://spool/٤٢" to MalformedNfcPayloadReason.NonDecimalId,
            "bambuddy-spool://spool/9223372036854775808" to
                MalformedNfcPayloadReason.NonPositiveOrOverflowId
        ).forEach { (input, reason) ->
            assertEquals(
                reason,
                assertIs<NfcPayloadParseResult.Malformed>(CanonicalNfcPayloadCodec.parse(input)).reason
            )
        }
    }

    private fun spoolId(value: Long): SpoolId = assertNotNull(SpoolId.from(value))
}
