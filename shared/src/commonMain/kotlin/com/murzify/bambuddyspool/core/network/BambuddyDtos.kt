package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.domain.Assignment
import com.murzify.bambuddyspool.core.domain.Printer
import com.murzify.bambuddyspool.core.domain.PrinterStatus
import com.murzify.bambuddyspool.core.domain.Spool
import com.murzify.bambuddyspool.core.domain.VirtualTray
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AuthMeDto(
    val id: Long,
    val username: String,
    val role: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("is_admin") val isAdmin: Boolean,
    @SerialName("created_at") val createdAt: String
)

@Serializable
internal data class PrinterDto(
    val id: Long,
    val name: String,
    val model: String? = null,
    @SerialName("is_active") val isActive: Boolean
) {
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
        if (labelWeight < 0 || coreWeight < 0 || weightUsed < 0.0) invalidDomainField()
        return (labelWeight - weightUsed).roundToInt().takeIf { it >= 0 } ?: invalidDomainField()
    }
}

@Serializable
internal data class AssignmentDto(
    val id: Long,
    @SerialName("spool_id") val spoolId: Long,
    @SerialName("printer_id") val printerId: Long,
    @SerialName("ams_id") val amsId: Int,
    @SerialName("tray_id") val trayId: Int,
    @SerialName("created_at") val createdAt: String,
    val configured: Boolean,
    @SerialName("pending_config") val pendingConfig: Boolean,
    @SerialName("ams_label") val amsLabel: String? = null
) {
    fun toDomain(): Assignment {
        id.validatePositiveId()
        return Assignment(
            spoolId = spoolId.toSpoolId(),
            slot = slotKey(printerId = printerId, amsId = amsId, trayId = trayId),
            configured = configured,
            pendingConfiguration = pendingConfig
        )
    }
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
