package com.murzify.bambuddyspool.core.nfc

import com.murzify.bambuddyspool.core.domain.SpoolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class NfcReadClassifierTest {

    @Test
    fun emptyMessageClassifiesAsEmpty() {
        assertEquals(
            expected = NfcReadClassification.Empty,
            actual = NfcReadClassifier.classify(CommonNdefMessage.Empty)
        )
    }

    @Test
    fun singleSupportedUriRecordClassifiesAsValidSpoolPayload() {
        val classification = assertIs<NfcReadClassification.ValidSpoolPayload>(
            NfcReadClassifier.classify(
                CommonNdefMessage(records = listOf(CommonNdefRecord.Uri("bambuddy-spool://spool/00042")))
            )
        )

        assertEquals(spoolId(42L), classification.spoolId)
        assertEquals("bambuddy-spool://spool/42", classification.canonicalUri)
    }

    @Test
    fun malformedUriRecordClassifiesAsMalformedNdef() {
        val classification = assertIs<NfcReadClassification.MalformedNdef>(
            NfcReadClassifier.classify(
                CommonNdefMessage(records = listOf(CommonNdefRecord.Uri("bambuddy-spool://spool/42?x=1")))
            )
        )

        assertEquals(MalformedNfcPayloadReason.QueryNotAllowed, classification.reason)
    }

    @Test
    fun unsupportedUriClassifiesAsUnknownPayload() {
        val classification = assertIs<NfcReadClassification.UnknownPayload>(
            NfcReadClassifier.classify(
                CommonNdefMessage(records = listOf(CommonNdefRecord.Uri("bambuddy-spool://other/42")))
            )
        )

        assertEquals(UnknownNfcPayloadReason.UnsupportedHost, classification.reason)
    }

    @Test
    fun emptyTextRecordIsUnknownPayloadNotEmpty() {
        val classification = assertIs<NfcReadClassification.UnknownPayload>(
            NfcReadClassifier.classify(CommonNdefMessage(records = listOf(CommonNdefRecord.Text(""))))
        )

        assertEquals(UnknownNfcPayloadReason.UnsupportedRecordType, classification.reason)
    }

    @Test
    fun mimeAndUnknownRecordsAreUnknownPayloadsNotEmpty() {
        listOf(
            CommonNdefRecord.Mime(mediaType = "text/plain", payload = byteArrayOf()),
            CommonNdefRecord.Unknown(type = "external", payload = byteArrayOf(1, 2))
        ).forEach { record ->
            val classification = assertIs<NfcReadClassification.UnknownPayload>(
                NfcReadClassifier.classify(CommonNdefMessage(records = listOf(record)))
            )

            assertEquals(UnknownNfcPayloadReason.UnsupportedRecordType, classification.reason)
        }
    }

    @Test
    fun multipleRecordsAreUnsupportedEvenWhenOneRecordIsValid() {
        val classification = assertIs<NfcReadClassification.UnknownPayload>(
            NfcReadClassifier.classify(
                CommonNdefMessage(
                    records = listOf(
                        CommonNdefRecord.Uri("bambuddy-spool://spool/42"),
                        CommonNdefRecord.Text("")
                    )
                )
            )
        )

        assertEquals(UnknownNfcPayloadReason.MultipleRecords, classification.reason)
    }

    @Test
    fun allTechnicalReadClassificationsAreModeled() {
        val classifications: List<NfcReadClassification> = listOf(
            NfcReadClassification.Empty,
            NfcReadClassification.ValidSpoolPayload(
                spoolId = spoolId(1L),
                canonicalUri = "bambuddy-spool://spool/1"
            ),
            NfcReadClassification.UnknownPayload(UnknownNfcPayloadReason.UnsupportedRecordType),
            NfcReadClassification.MalformedNdef(MalformedNfcPayloadReason.NonDecimalId),
            NfcReadClassification.UnsupportedTag(UnsupportedTagReason.UnsupportedTechnology),
            NfcReadClassification.ReadFailure(NfcReadFailureReason.TagRemoved)
        )

        assertEquals(6, classifications.size)
    }

    private fun spoolId(value: Long): SpoolId = assertNotNull(SpoolId.from(value))
}
