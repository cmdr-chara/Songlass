package dev.cmdrchara.songlass.core.permissions

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissionRecoveryTest {
    @Test
    fun firstDenialKeepsTheNormalRationaleRequestAndLaterSuppressionOffersRecovery() {
        val store = InMemoryKeyValueStore()
        val recovery = PermissionRecovery(store)
        assertEquals(PermissionRecoveryAction.RequestPermission, recovery.action(false, false))
        recovery.requestLaunched()
        assertEquals(PermissionRecoveryAction.RequestPermission, recovery.action(false, true))
        recovery.requestLaunched()
        assertEquals(PermissionRecoveryAction.OfferRecoveryChoice, recovery.action(false, false))
        // Recreation preserves a prior request, not a cached platform rationale result.
        assertEquals(PermissionRecoveryAction.OfferRecoveryChoice, PermissionRecovery(store).action(false, false))
        assertEquals(PermissionRecoveryAction.RequestPermission, PermissionRecovery(store).action(false, true))
    }

    @Test
    fun autoResetAfterAGrantOffersAChoiceWithoutPersistingAPermanentDenial() {
        val store = InMemoryKeyValueStore()
        val recovery = PermissionRecovery(store)
        recovery.requestLaunched()
        assertEquals(PermissionRecoveryAction.AlreadyGranted, recovery.action(true, false))
        assertEquals(PermissionRecoveryAction.OfferRecoveryChoice, recovery.action(false, false))
        // The platform may subsequently accept a retry, or the user may grant in Settings.
        assertEquals(PermissionRecoveryAction.RequestPermission, recovery.action(false, true))
        assertEquals(PermissionRecoveryAction.AlreadyGranted, recovery.action(true, false))
        assertEquals(mapOf("audio_permission_requested" to "true"), store.snapshot())
    }

    @Test
    fun aReinstallationDoesNotInheritTheOldInstallationsRecoveryChoice() {
        val previous = PermissionRecovery(InMemoryKeyValueStore()).apply { requestLaunched() }
        assertEquals(PermissionRecoveryAction.OfferRecoveryChoice, previous.action(false, false))
        val reinstalledStore = InMemoryKeyValueStore()
        assertEquals(PermissionRecoveryAction.RequestPermission, PermissionRecovery(reinstalledStore).action(false, false))
        assertTrue(reinstalledStore.snapshot().isEmpty())
    }

    @Test
    fun inspectingOrFailingToLaunchARequestDoesNotRecordAPermissionAttempt() {
        val store = InMemoryKeyValueStore()
        val recovery = PermissionRecovery(store)
        repeat(3) { assertEquals(PermissionRecoveryAction.RequestPermission, recovery.action(false, false)) }
        // No requestLaunched call if the platform launcher throws.
        assertTrue(store.snapshot().isEmpty())
        assertEquals(PermissionRecoveryAction.RequestPermission, PermissionRecovery(store).action(false, false))
    }
}
