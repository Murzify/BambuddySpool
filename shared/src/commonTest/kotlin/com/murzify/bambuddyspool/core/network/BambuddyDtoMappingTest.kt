package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.AssignmentCommand
import com.murzify.bambuddyspool.core.domain.AssignmentSource
import com.murzify.bambuddyspool.core.domain.IncompatibleApiReason
import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SnapshotGeneration
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.SpoolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class BambuddyDtoMappingTest {

    @Test
    fun printerModelAndAuthoritativeAmsCapabilityMapAsTopologyEvidence() {
        val printer = assertSuccess(
            parsePrintersResponse("""[{"id":1,"name":"Synthetic","model":"A1"}]""")
        ).single()
        val status = assertSuccess(
            parsePrinterStatusResponse(
                """{"id":1,"name":"Synthetic","connected":true,"ams_exists":false,"vt_tray":[{"id":17}]}"""
            )
        )

        assertEquals("A1", printer.model)
        assertEquals(false, status.amsExists)
    }

    @Test
    fun assignmentRequestUsesExactLogicalJsonShape() {
        val command = AssignmentCommand.from(
            spoolId = spoolId(3),
            slot = SlotKey(printerId = printerId(1), amsId = 255, trayId = 0),
            source = AssignmentSource.NfcScan,
            expectedSnapshotGeneration = SnapshotGeneration.from(0) ?: error("invalid generation")
        )

        assertEquals(
            """{"printer_id":1,"ams_id":255,"tray_id":0,"spool_id":3}""",
            encodeAssignmentRequest(command)
        )
        assertIs<BambuddyMappingResult.Success<Unit>>(validateAssignmentRequestBody(encodeAssignmentRequest(command)))
    }

    @Test
    fun invalidAssignmentRequestFieldReturnsIncompatibleApiResponse() {
        val result = validateAssignmentRequestBody(
            """{"printer_id":1,"ams_id":255,"tray_id":0,"spool_id":"not-an-integer"}"""
        )

        val failure = assertIs<BambuddyMappingResult.Failure>(result)
        assertEquals(IncompatibleApiReason.UnexpectedShape, failure.error.reason)
    }

    @Test
    fun assignmentResponsePreservesConfiguredState() {
        val result = parseAssignmentResponse(
            """
            {
              "spool_id": 3,
              "printer_id": 1,
              "ams_id": 255,
              "tray_id": 0,
              "configured": true,
              "pending_config": false,
              "synthetic_additive_field": "ignored"
            }
            """.trimIndent()
        )

        val assignment = assertSuccess<Assignment>(result)
        assertEquals(spoolId(3), assignment.spoolId)
        assertEquals(SlotKey(printerId(1), amsId = 255, trayId = 0), assignment.slot)
        assertEquals(true, assignment.configured)
        assertEquals(false, assignment.pendingConfiguration)
    }

    @Test
    fun assignmentResponsePreservesPendingConfigurationState() {
        val result = parseAssignmentResponse(
            """
            {
              "spool_id": 3,
              "printer_id": 1,
              "ams_id": 255,
              "tray_id": 0,
              "configured": false,
              "pending_config": true
            }
            """.trimIndent()
        )

        val assignment = assertSuccess<Assignment>(result)
        assertEquals(false, assignment.configured)
        assertEquals(true, assignment.pendingConfiguration)
    }

    @Test
    fun unknownAdditiveFieldsAreIgnoredWhileMappingSpools() {
        val result = parseSpoolResponse(
            """
            {
              "id": 3,
              "material": "PLA",
              "subtype": null,
              "color_name": "Synthetic Blue",
              "rgba": "0000FFFF",
              "brand": "Synthetic Brand",
              "label_weight": 1000,
              "core_weight": 250,
              "weight_used": 100.0,
              "last_used": null,
              "archived_at": null,
              "created_at": "2000-01-01T00:00:00Z",
              "updated_at": "2000-01-01T00:00:00Z",
              "synthetic_additive_field": "ignored"
            }
            """.trimIndent()
        )

        val spool = assertSuccess<Spool>(result)
        assertEquals(spoolId(3), spool.id)
        assertEquals("PLA", spool.material)
        assertEquals("Synthetic Brand", spool.manufacturer)
        assertEquals("Synthetic Blue", spool.colorName)
        assertEquals(900, spool.remainingGrams)
    }

    @Test
    fun finiteOverConsumptionIsClampedToEmptyInsteadOfRejectingTheSpool() {
        val spool = assertSuccess<Spool>(parseSpoolResponse(spoolJson(weightUsed = "1000.5")))

        assertEquals(0, spool.remainingGrams)
    }

    @Test
    fun negativeOrNonFiniteSourceWeightsRemainIncompatible() {
        listOf(
            spoolJson(labelWeight = "-1"),
            spoolJson(coreWeight = "-1"),
            spoolJson(weightUsed = "-0.1")
        ).forEach { body ->
            val failure = assertIs<BambuddyMappingResult.Failure>(parseSpoolResponse(body))
            assertEquals(IncompatibleApiReason.InvalidFieldValue, failure.error.reason)
        }

        assertIs<BambuddyMappingResult.Failure>(parseSpoolResponse(spoolJson(weightUsed = "NaN")))
    }

    @Test
    fun missingRequiredSpoolWeightFieldReturnsIncompatibleApiResponse() {
        val result = parseSpoolResponse(
            """
            {
              "id": 3,
              "material": "PLA",
              "created_at": "2000-01-01T00:00:00Z",
              "updated_at": "2000-01-01T00:00:00Z"
            }
            """.trimIndent()
        )

        val failure = assertIs<BambuddyMappingResult.Failure>(result)
        assertEquals(IncompatibleApiReason.MissingRequiredField, failure.error.reason)
    }

    @Test
    fun missingRequiredPrinterStatusTrayListReturnsIncompatibleApiResponse() {
        val result = parsePrinterStatusResponse(
            """
            {
              "id": 1,
              "name": "Synthetic Printer",
              "connected": true
            }
            """.trimIndent()
        )

        val failure = assertIs<BambuddyMappingResult.Failure>(result)
        assertEquals(IncompatibleApiReason.MissingRequiredField, failure.error.reason)
    }

    @Test
    fun missingRequiredAssignmentFieldReturnsIncompatibleApiResponse() {
        val result = parseAssignmentResponse(
            """
            {
              "spool_id": 3,
              "printer_id": 1,
              "ams_id": 255,
              "tray_id": 0,
              "configured": false
            }
            """.trimIndent()
        )

        val failure = assertIs<BambuddyMappingResult.Failure>(result)
        assertEquals(IncompatibleApiReason.MissingRequiredField, failure.error.reason)
    }

    @Test
    fun invalidSlotCoordinateReturnsIncompatibleApiResponse() {
        val result = parseAssignmentResponse(
            """
            {
              "spool_id": 3,
              "printer_id": 1,
              "ams_id": 256,
              "tray_id": 0,
              "configured": false,
              "pending_config": false
            }
            """.trimIndent()
        )

        val failure = assertIs<BambuddyMappingResult.Failure>(result)
        assertEquals(IncompatibleApiReason.InvalidFieldValue, failure.error.reason)
    }

    private inline fun <reified T> assertSuccess(result: BambuddyMappingResult<T>): T {
        val success = assertIs<BambuddyMappingResult.Success<T>>(result)
        return success.value
    }

    private fun printerId(value: Long): PrinterId = assertNotNull(PrinterId.from(value))

    private fun spoolId(value: Long): SpoolId = assertNotNull(SpoolId.from(value))

    private fun spoolJson(
        labelWeight: String = "1000",
        coreWeight: String = "250",
        weightUsed: String = "100.0"
    ): String =
        """
        {
          "id": 3,
          "material": "PLA",
          "label_weight": $labelWeight,
          "core_weight": $coreWeight,
          "weight_used": $weightUsed,
          "created_at": "2000-01-01T00:00:00Z",
          "updated_at": "2000-01-01T00:00:00Z"
        }
        """.trimIndent()
}
