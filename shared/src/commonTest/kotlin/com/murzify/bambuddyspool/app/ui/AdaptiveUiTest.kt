package com.murzify.bambuddyspool.app.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveUiTest {
    @Test
    fun widthClassesMatchTheApprovedInclusiveBoundaries() {
        assertEquals(UiWidthClass.Compact, UiWidthClass(599.dp))
        assertEquals(UiWidthClass.Medium, UiWidthClass(600.dp))
        assertEquals(UiWidthClass.Medium, UiWidthClass(839.dp))
        assertEquals(UiWidthClass.Expanded, UiWidthClass(840.dp))
    }
}
