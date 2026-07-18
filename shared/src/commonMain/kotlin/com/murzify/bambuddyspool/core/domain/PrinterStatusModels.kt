package com.murzify.bambuddyspool.core.domain

data class PrinterStatus(
    val printer: Printer,
    val connected: Boolean,
    val virtualTrays: List<VirtualTray>,
    /** Null means the Bambuddy status response supplied no authoritative AMS capability evidence. */
    val amsExists: Boolean? = null
)

data class VirtualTray(val id: Int, val label: String?)
