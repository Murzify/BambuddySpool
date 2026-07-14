package com.murzify.bambuddyspool.core.performance

/** Product situations from TECHSPEC 16.7; this is a policy, not a background health monitor. */
enum class DegradationCondition {
    ServerOffline,
    NfcUnsupported,
    NfcDisabled,
    PrinterStatusStale,
    TokenUnavailable,
    RoomCacheFailure
}

/** Explicitly allowed capabilities while a condition is active. */
data class DegradationPlan(
    val cachedViewing: Boolean,
    val localNfcRead: Boolean,
    val manualAssignment: Boolean,
    val settingsAction: Boolean,
    val assignmentMutation: Boolean,
    val rebuildCacheFromServer: Boolean
)

/**
 * A compact executable form of the graceful-degradation table. The policy only describes the current UI/action
 * boundary; it never schedules recovery, retries, polling, or a circuit breaker.
 */
object GracefulDegradationPolicy {
    fun resolve(condition: DegradationCondition): DegradationPlan = when (condition) {
        DegradationCondition.ServerOffline -> DegradationPlan(
            cachedViewing = true,
            localNfcRead = true,
            manualAssignment = false,
            settingsAction = false,
            assignmentMutation = false,
            rebuildCacheFromServer = false
        )
        DegradationCondition.NfcUnsupported -> DegradationPlan(
            cachedViewing = true,
            localNfcRead = false,
            manualAssignment = true,
            settingsAction = false,
            assignmentMutation = true,
            rebuildCacheFromServer = false
        )
        DegradationCondition.NfcDisabled -> DegradationPlan(
            cachedViewing = true,
            localNfcRead = false,
            manualAssignment = true,
            settingsAction = true,
            assignmentMutation = true,
            rebuildCacheFromServer = false
        )
        DegradationCondition.PrinterStatusStale -> DegradationPlan(
            cachedViewing = true,
            localNfcRead = true,
            manualAssignment = false,
            settingsAction = false,
            assignmentMutation = false,
            rebuildCacheFromServer = false
        )
        DegradationCondition.TokenUnavailable -> DegradationPlan(
            cachedViewing = true,
            localNfcRead = true,
            manualAssignment = false,
            settingsAction = true,
            assignmentMutation = false,
            rebuildCacheFromServer = false
        )
        DegradationCondition.RoomCacheFailure -> DegradationPlan(
            cachedViewing = false,
            localNfcRead = true,
            manualAssignment = false,
            settingsAction = false,
            assignmentMutation = false,
            rebuildCacheFromServer = true
        )
    }
}
