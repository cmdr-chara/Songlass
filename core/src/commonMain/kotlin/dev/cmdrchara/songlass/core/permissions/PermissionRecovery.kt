package dev.cmdrchara.songlass.core.permissions

import dev.cmdrchara.songlass.core.storage.KeyValueStore

/** A recovery choice is not proof of permanent denial: Android may also reset a permission. */
enum class PermissionRecoveryAction { AlreadyGranted, RequestPermission, OfferRecoveryChoice }

/**
 * Installation-local request history, backed by a platform-only preference store. Rationale is
 * sampled anew for each explicit user action; it is never persisted as a permanent-denial flag.
 * A recovery choice must retain an explicit retry action as well as an app-settings action.
 */
class PermissionRecovery(private val store: KeyValueStore) {
    fun action(granted: Boolean, shouldShowRationale: Boolean): PermissionRecoveryAction = when {
        granted -> PermissionRecoveryAction.AlreadyGranted
        store.getString(REQUESTED_KEY) != "true" || shouldShowRationale -> PermissionRecoveryAction.RequestPermission
        else -> PermissionRecoveryAction.OfferRecoveryChoice
    }

    /** Call only after the platform accepted the launch, not when merely displaying recovery. */
    fun requestLaunched() { store.putString(REQUESTED_KEY, "true") }

    private companion object { const val REQUESTED_KEY = "audio_permission_requested" }
}
