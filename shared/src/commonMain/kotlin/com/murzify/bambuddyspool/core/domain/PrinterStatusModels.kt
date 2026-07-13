package com.murzify.bambuddyspool.core.domain

data class PrinterStatus(val printer: Printer, val connected: Boolean, val virtualTrays: List<VirtualTray>)

data class VirtualTray(val id: Int, val label: String?)
