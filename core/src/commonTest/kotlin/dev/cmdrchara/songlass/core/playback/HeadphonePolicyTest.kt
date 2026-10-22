package dev.cmdrchara.songlass.core.playback

import kotlin.test.*

class HeadphonePolicyTest {
    private val wired = HeadphoneConnection(1, HeadphoneKind.Wired)
    private val bluetooth = HeadphoneConnection(2, HeadphoneKind.Bluetooth)

    @Test fun initialDevicesAndRepeatedCallbacksAreNotConnections() {
        val connections = HeadphoneConnections()
        assertTrue(connections.added(setOf(wired, bluetooth)).isEmpty())
        assertTrue(connections.added(setOf(bluetooth)).isEmpty())
        connections.removed(setOf(2))
        assertEquals(setOf(HeadphoneKind.Bluetooth), connections.added(setOf(bluetooth)))
    }
    @Test fun emptyInitialEnumerationStillAllowsTheFirstRealConnection() {
        val connections = HeadphoneConnections()
        assertTrue(connections.added(emptySet()).isEmpty())
        assertEquals(setOf(HeadphoneKind.Wired), connections.added(setOf(wired)))
    }
    @Test fun batchesDeduplicateKindsAndHandleNewOrReusedSessionIds() {
        val connections = HeadphoneConnections(); connections.added(emptySet())
        assertEquals(setOf(HeadphoneKind.Wired, HeadphoneKind.Bluetooth),
            connections.added(setOf(wired, bluetooth, bluetooth.copy(id = 3))))
        connections.removed(setOf(1, 2, 3))
        assertEquals(setOf(HeadphoneKind.Bluetooth), connections.added(setOf(bluetooth.copy(id = 4))))
        assertEquals(setOf(HeadphoneKind.Wired), connections.added(setOf(wired)))
    }
    @Test fun independentOptionsSelectTheArrivingKind() {
        val wiredOnly = HeadphoneSettings(resumeWired = true)
        val bluetoothOnly = HeadphoneSettings(resumeBluetooth = true)
        assertTrue(wiredOnly.resumes(HeadphoneKind.Wired)); assertFalse(wiredOnly.resumes(HeadphoneKind.Bluetooth))
        assertFalse(bluetoothOnly.resumes(HeadphoneKind.Wired)); assertTrue(bluetoothOnly.resumes(HeadphoneKind.Bluetooth))
        assertTrue(setOf(HeadphoneKind.Wired, HeadphoneKind.Bluetooth).any(bluetoothOnly::resumes))
    }
    @Test fun changingConnectionPolicyDoesNotTurnAnOrdinaryPauseIntoAResume() {
        val settings = HeadphoneSettings(resumeWired = true, resumeBluetooth = true)
        val resume = OutputResume({ 100L })
        assertFalse(resume.onOutputConnected(settings.resumes(HeadphoneKind.Wired)))
        resume.onPausedByDisconnect(); resume.cancel()
        assertFalse(resume.onOutputConnected(settings.resumes(HeadphoneKind.Bluetooth)))
    }
    @Test fun aDisabledKindConsumesTheDisconnectRatherThanStartingOnALaterUnrelatedConnection() {
        val settings = HeadphoneSettings(resumeWired = true)
        val resume = OutputResume({ 100L }); resume.onPausedByDisconnect()
        assertFalse(resume.onOutputConnected(settings.resumes(HeadphoneKind.Bluetooth)))
        assertFalse(resume.onOutputConnected(settings.resumes(HeadphoneKind.Wired)))
        resume.onPausedByDisconnect()
        assertTrue(resume.onOutputConnected(setOf(HeadphoneKind.Bluetooth, HeadphoneKind.Wired).any(settings::resumes)))
    }
    @Test fun filterIsOffByDefaultAndClockAloneDoesNotArmIt() {
        var now = 10L; val filter = BluetoothCommandFilter { now }
        filter.connected(); assertFalse(filter.shouldIgnore())
        filter.configure(1000); assertFalse(filter.shouldIgnore())
        now += 100; assertFalse(filter.shouldIgnore())
    }
    @Test fun offDoesNotInvalidateGesturesAndDisablingReleasesDeferredControllerRequests() {
        var now = 0L; val filter = BluetoothCommandFilter { now }
        val original = filter.revision
        filter.connected(); assertEquals(original, filter.revision)
        filter.configure(1000); val captured = filter.revision; filter.connected()
        assertFalse(filter.acceptsControllerRequest("com.android.bluetooth", captured))
        filter.configure(0)
        assertTrue(filter.acceptsControllerRequest("com.android.bluetooth", captured))
        val disabled = filter.revision; now++; filter.connected()
        assertEquals(disabled, filter.revision); assertFalse(filter.shouldIgnore())
    }
    @Test fun elapsedWindowExpiresAcrossSleepAndFreshKeyEventsAreNotMistakenForQueuedOnes() {
        var elapsed = 1000L; val filter = BluetoothCommandFilter { elapsed }; filter.configure(1000); filter.connected()
        elapsed = 23000 // Device slept: uptime only advanced from 1000 to 3000.
        assertFalse(filter.shouldIgnore())
        assertFalse(filter.shouldIgnore(BluetoothCommandFilter.elapsedKeyTime(3000, 3000, elapsed)))
        assertEquals(23000L, BluetoothCommandFilter.elapsedKeyTime(3000, 3000, elapsed))
        // A key queued while awake retains its original age on delayed dispatch.
        elapsed = 25000; filter.connected(); elapsed = 27000
        val queued = BluetoothCommandFilter.elapsedKeyTime(5100, 7000, elapsed)
        assertEquals(25100L, queued); assertTrue(filter.shouldIgnore(queued))
        assertNull(BluetoothCommandFilter.elapsedKeyTime(-1, 7000, elapsed))
        assertNull(BluetoothCommandFilter.elapsedKeyTime(7001, 7000, elapsed))
        assertNull(BluetoothCommandFilter.elapsedKeyTime(0, Long.MAX_VALUE, 1))
    }
    @Test fun filterUsesAHalfOpenMonotonicWindow() {
        var now = 100L; val filter = BluetoothCommandFilter { now }; filter.configure(1000); filter.connected()
        assertTrue(filter.shouldIgnore())
        now = 1099; assertTrue(filter.shouldIgnore())
        now = 1100; assertFalse(filter.shouldIgnore())
    }
    @Test fun lateDispatchStillDropsEventsGeneratedInsideTheWindow() {
        var now = 100L; val filter = BluetoothCommandFilter { now }; filter.configure(1000); filter.connected()
        now = 5000
        assertTrue(filter.shouldIgnore(100)); assertTrue(filter.shouldIgnore(1099))
        assertFalse(filter.shouldIgnore(99)); assertFalse(filter.shouldIgnore(1100)); assertFalse(filter.shouldIgnore(9000))
    }
    @Test fun changingDurationClearsWithoutRearmingAndNextConnectionUsesTheNewDuration() {
        var now = 100L; val filter = BluetoothCommandFilter { now }; filter.configure(1000); filter.connected()
        filter.configure(1000); assertTrue(filter.shouldIgnore())
        filter.configure(0); assertFalse(filter.shouldIgnore(100))
        filter.configure(2000); assertFalse(filter.shouldIgnore())
        filter.connected(); now += 1999; assertTrue(filter.shouldIgnore())
        now++; assertFalse(filter.shouldIgnore())
    }
    @Test fun reconnectExtendsTheWindowAndInvalidatesAnOldQueuedGestureEvenAfterExpiry() {
        var now = 0L; val filter = BluetoothCommandFilter { now }; filter.configure(1000); filter.connected()
        val gesture = filter.revision
        now = 900; filter.connected()
        assertNotEquals(gesture, filter.revision)
        now = 1500; assertTrue(filter.shouldIgnore())
        now = 2000; assertFalse(filter.shouldIgnore())
        assertNotEquals(gesture, filter.revision)
    }
    @Test fun duplicatePlatformCallbacksDoNotExtendTheWindow() {
        var now = 0L; val filter = BluetoothCommandFilter { now }; filter.configure(1000)
        val connections = HeadphoneConnections(); connections.added(emptySet())
        if (HeadphoneKind.Bluetooth in connections.added(setOf(bluetooth))) filter.connected()
        now = 900
        if (HeadphoneKind.Bluetooth in connections.added(setOf(bluetooth))) filter.connected()
        now = 1000; assertFalse(filter.shouldIgnore())
    }
    @Test fun backwardClockClearsTheWindowWithoutRevivalAndOverflowDoesNotMatch() {
        var now = 100L; val filter = BluetoothCommandFilter { now }; filter.configure(1000); filter.connected()
        now = 99; assertFalse(filter.shouldIgnore())
        now = 100; assertFalse(filter.shouldIgnore())
        now = Long.MIN_VALUE; filter.connected(); now = Long.MAX_VALUE
        assertFalse(filter.shouldIgnore())
    }
    @Test fun onlyKnownBluetoothControllersAreSubjectToControllerFiltering() {
        var now = 0L; val filter = BluetoothCommandFilter { now }; filter.configure(1000); filter.connected()
        val revision = filter.revision
        for (pkg in listOf("com.android.bluetooth", "com.google.android.bluetooth")) {
            assertTrue(BluetoothCommandFilter.isBluetoothController(pkg))
            assertFalse(filter.acceptsControllerRequest(pkg, revision))
            now = 1000; assertTrue(filter.acceptsControllerRequest(pkg, revision)); now = 0
        }
        for (pkg in listOf(null, "dev.cmdrchara.songlass", "com.android.systemui", "android", "example.bluetooth")) {
            assertFalse(BluetoothCommandFilter.isBluetoothController(pkg))
            assertTrue(filter.acceptsControllerRequest(pkg, revision))
        }
    }
    @Test fun deferredBluetoothRequestsCannotOutliveAConnectionEvenWhenItsWindowHasExpired() {
        var now = 0L; val filter = BluetoothCommandFilter { now }; filter.configure(1000)
        val captured = filter.revision
        filter.connected(); now = 2000
        assertFalse(filter.acceptsControllerRequest("com.android.bluetooth", captured))
        assertTrue(filter.acceptsControllerRequest("com.android.bluetooth", filter.revision))
        assertTrue(filter.acceptsControllerRequest("com.android.systemui", captured))
    }
}
