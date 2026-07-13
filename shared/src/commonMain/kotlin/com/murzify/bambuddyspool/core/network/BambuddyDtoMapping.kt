package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.AssignmentCommand
import com.murzify.bambuddyspool.core.domain.IncompatibleApiReason
import com.murzify.bambuddyspool.core.domain.IncompatibleApiResponse
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.Spool
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

fun parseAuthMeResponse(body: String): BambuddyMappingResult<Unit> = mapResponse {
    BambuddyJson.decodeFromString<AuthMeDto>(body)
    Unit
}

fun parsePrintersResponse(body: String): BambuddyMappingResult<List<Printer>> = mapResponse {
    BambuddyJson.decodeFromString<List<PrinterDto>>(body).map { it.toDomain() }
}

fun parsePrinterStatusResponse(body: String): BambuddyMappingResult<PrinterStatus> = mapResponse {
    BambuddyJson.decodeFromString<PrinterStatusDto>(body).toDomain()
}

fun parseSpoolsResponse(body: String): BambuddyMappingResult<List<Spool>> = mapResponse {
    BambuddyJson.decodeFromString<List<SpoolDto>>(body).map { it.toDomain() }
}

fun parseSpoolResponse(body: String): BambuddyMappingResult<Spool> = mapResponse {
    BambuddyJson.decodeFromString<SpoolDto>(body).toDomain()
}

fun parseAssignmentsResponse(body: String): BambuddyMappingResult<List<Assignment>> = mapResponse {
    BambuddyJson.decodeFromString<List<AssignmentDto>>(body).map { it.toDomain() }
}

fun parseAssignmentResponse(body: String): BambuddyMappingResult<Assignment> = mapResponse {
    BambuddyJson.decodeFromString<AssignmentDto>(body).toDomain()
}

fun validateAssignmentRequestBody(body: String): BambuddyMappingResult<Unit> = mapResponse {
    BambuddyJson.decodeFromString<AssignmentRequestDto>(body).validate()
}

fun encodeAssignmentRequest(command: AssignmentCommand): String = BambuddyJson.encodeToString(
    AssignmentRequestDto(
        printerId = command.slot.printerId.value,
        amsId = command.slot.amsId,
        trayId = command.slot.trayId,
        spoolId = command.spoolId.value
    )
)

@OptIn(ExperimentalSerializationApi::class)
private inline fun <T> mapResponse(block: () -> T): BambuddyMappingResult<T> = try {
    BambuddyMappingResult.Success(block())
} catch (_: MissingFieldException) {
    incompatible(IncompatibleApiReason.MissingRequiredField)
} catch (_: SerializationException) {
    incompatible(IncompatibleApiReason.UnexpectedShape)
} catch (_: InvalidDomainFieldException) {
    incompatible(IncompatibleApiReason.InvalidFieldValue)
} catch (_: IllegalArgumentException) {
    incompatible(IncompatibleApiReason.InvalidFieldValue)
}

private fun incompatible(reason: IncompatibleApiReason): BambuddyMappingResult.Failure =
    BambuddyMappingResult.Failure(IncompatibleApiResponse(reason))
