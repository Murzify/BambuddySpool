package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.domain.PrinterId
import com.murzify.bambuddyspool.core.domain.SlotKey
import com.murzify.bambuddyspool.core.domain.SpoolId

internal fun Long.toPrinterId(): PrinterId = PrinterId.from(this) ?: invalidDomainField()

internal fun Long.toSpoolId(): SpoolId = SpoolId.from(this) ?: invalidDomainField()

internal fun Long.validatePositiveId() {
    if (this < 1L) invalidDomainField()
}

internal fun slotKey(printerId: Long, amsId: Int, trayId: Int): SlotKey =
    SlotKey.from(printerId.toPrinterId(), amsId, trayId) ?: invalidDomainField()

internal fun invalidDomainField(): Nothing = throw InvalidDomainFieldException()

internal class InvalidDomainFieldException : RuntimeException()
