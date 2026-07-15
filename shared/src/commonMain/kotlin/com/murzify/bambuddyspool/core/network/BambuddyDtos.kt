package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.VirtualTray
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal typealias AuthMeDto = JsonObject

@Serializable
internal data class PrinterDto(val id: Long, val name: String) {
    fun toDomain(): Printer = Printer(id = id.toPrinterId(), name = name)
}

@Serializable
internal data class PrinterStatusDto(
    val id: Long,
    val name: String,
    val connected: Boolean,
    @SerialName("vt_tray") val virtualTrays: List<VirtualTrayDto>
) {
    fun toDomain(): PrinterStatus = PrinterStatus(
        printer = Printer(id = id.toPrinterId(), name = name),
        connected = connected,
        virtualTrays = virtualTrays.map { it.toDomain() }
    )
}

@Serializable
internal data class VirtualTrayDto(val id: Int, @SerialName("tray_id_name") val trayIdName: String? = null) {
    fun toDomain(): VirtualTray {
        if (id < 0) invalidDomainField()
        return VirtualTray(id = id, label = trayIdName)
    }
}

@Serializable
internal data class SpoolDto(
    val id: Long,
    val material: String,
    val subtype: String? = null,
    @SerialName("color_name") val colorName: String? = null,
    val rgba: String? = null,
    val brand: String? = null,
    @SerialName("label_weight") val labelWeight: Int,
    @SerialName("core_weight") val coreWeight: Int,
    @SerialName("weight_used") val weightUsed: Double,
    @SerialName("last_used") val lastUsed: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
) {
    fun toDomain(): Spool = Spool(
        id = id.toSpoolId(),
        name = subtype,
        manufacturer = brand,
        material = material,
        colorName = colorName,
        remainingGrams = remainingGrams()
    )

    private fun remainingGrams(): Int? {
        if (labelWeight < 0 || coreWeight < 0) invalidDomainField()
        if (!weightUsed.isFinite() || weightUsed < 0.0) invalidDomainField()
        if (weightUsed >= labelWeight) return 0
        return (labelWeight - weightUsed).roundToInt()
    }
}

@Serializable
internal data class AssignmentDto(
    @SerialName("spool_id") val spoolId: Long,
    @SerialName("printer_id") val printerId: Long,
    @SerialName("ams_id") val amsId: Int,
    @SerialName("tray_id") val trayId: Int,
    val configured: Boolean,
    @SerialName("pending_config") val pendingConfig: Boolean
) {
    fun toDomain(): Assignment = Assignment(
        spoolId = spoolId.toSpoolId(),
        slot = slotKey(printerId = printerId, amsId = amsId, trayId = trayId),
        configured = configured,
        pendingConfiguration = pendingConfig
    )
}

@Serializable
internal data class AssignmentRequestDto(
    @SerialName("printer_id") val printerId: Long,
    @SerialName("ams_id") val amsId: Int,
    @SerialName("tray_id") val trayId: Int,
    @SerialName("spool_id") val spoolId: Long
) {
    fun validate() {
        slotKey(printerId = printerId, amsId = amsId, trayId = trayId)
        spoolId.toSpoolId()
    }
}
