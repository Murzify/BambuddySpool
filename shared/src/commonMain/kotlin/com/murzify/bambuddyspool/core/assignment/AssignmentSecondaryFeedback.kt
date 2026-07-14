package com.murzify.bambuddyspool.core.assignment

import com.murzify.bambuddyspool.core.domain.AssignmentSuccess
import com.murzify.bambuddyspool.core.platform.ClipboardService
import com.murzify.bambuddyspool.core.platform.HapticsService

/**
 * Best-effort platform feedback after a verified result. It has no authority to change the assignment outcome.
 * Clipboard copying is user-triggered by a presentation surface; it deliberately never runs automatically.
 */
class AssignmentSecondaryFeedback(private val haptics: HapticsService, private val clipboard: ClipboardService) {
    fun reportVerifiedSuccess(@Suppress("UNUSED_PARAMETER") success: AssignmentSuccess) {
        runCatching { haptics.success() }
    }

    fun copyRedactedDiagnostic(text: String): Boolean = runCatching { clipboard.copyRedacted(text) }.getOrDefault(false)
}
