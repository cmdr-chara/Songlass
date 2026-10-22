package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.storage.KeyValueStore

enum class HeadphoneKind { Wired, Bluetooth }
data class HeadphoneConnection(val id: Int, val kind: HeadphoneKind)

data class HeadphoneSettings(
    val resumeWired: Boolean = false,
    val resumeBluetooth: Boolean = false,
    val bluetoothCommandDelayMs: Long = 0,
) {
    init { require(bluetoothCommandDelayMs in delayChoicesMs) }
    fun resumes(kind: HeadphoneKind): Boolean = when (kind) {
        HeadphoneKind.Wired -> resumeWired
        HeadphoneKind.Bluetooth -> resumeBluetooth
    }
    companion object {
        const val LEGACY_KEY = "settings.resume_on_reconnect"
        const val WIRED_KEY = "settings.resume_wired"
        const val BLUETOOTH_KEY = "settings.resume_bluetooth"
        const val COMMAND_DELAY_KEY = "settings.bluetooth_command_delay_ms"
        val keys = setOf(WIRED_KEY, BLUETOOTH_KEY, COMMAND_DELAY_KEY)
        val delayChoicesMs = listOf(0L, 1_000L, 2_000L, 3_000L, 5_000L, 10_000L, 20_000L)
        fun read(store: KeyValueStore): HeadphoneSettings {
            val legacy = store.getString(LEGACY_KEY)?.toBooleanStrictOrNull() ?: false
            fun resume(key: String) = store.getString(key)?.let { it.toBooleanStrictOrNull() ?: false } ?: legacy
            return HeadphoneSettings(resume(WIRED_KEY), resume(BLUETOOTH_KEY),
                store.getString(COMMAND_DELAY_KEY)?.toLongOrNull()?.takeIf { it in delayChoicesMs } ?: 0)
        }
    }
}

/** Initial enumeration and repeated device callbacks must not look like new physical connections. */
class HeadphoneConnections {
    private var initialized = false
    private val connected = mutableSetOf<HeadphoneConnection>()
    fun added(devices: Set<HeadphoneConnection>): Set<HeadphoneKind> {
        val fresh = devices - connected
        connected.addAll(devices)
        if (!initialized) { initialized = true; return emptySet() }
        return fresh.map { it.kind }.toSet()
    }
    fun removed(ids: Set<Int>) { connected.removeAll { it.id in ids } }
}

/** A transient, monotonic-clock window. Never stored or restored with user preferences. */
class BluetoothCommandFilter(private val clock: () -> Long) {
    private var durationMs = 0L
    private var connectedAt: Long? = null
    var revision = 0L
        private set
    fun configure(durationMs: Long) {
        require(durationMs in HeadphoneSettings.delayChoicesMs)
        if (this.durationMs == durationMs) return
        this.durationMs = durationMs
        connectedAt = null
        revision++
    }
    fun connected() {
        if (durationMs == 0L) return
        connectedAt = clock(); revision++
    }
    fun clear() { connectedAt = null; revision++ }
    fun shouldIgnore(eventTimeMs: Long? = null): Boolean {
        if (durationMs == 0L) return false
        val at = connectedAt ?: return false
        val now = clock()
        if (now < at) { clear(); return false }
        fun inside(time: Long) = time >= at && time - at in 0 until durationMs
        // Event timestamps reject a key queued during the window even if dispatch was delayed.
        return inside(now) || (eventTimeMs != null && eventTimeMs <= now && inside(eventTimeMs))
    }
    fun acceptsControllerRequest(packageName: String?, capturedRevision: Long): Boolean =
        durationMs == 0L || !isBluetoothController(packageName) || (capturedRevision == revision && !shouldIgnore())

    companion object {
        /** Android key events use uptime; the connection window uses elapsed time including sleep.
         * Estimate an event's elapsed timestamp by its age. Fresh events after sleep stay fresh.
         */
        fun elapsedKeyTime(eventUptimeMs: Long, nowUptimeMs: Long, nowElapsedMs: Long): Long? {
            if (eventUptimeMs < 0 || nowUptimeMs < eventUptimeMs || nowElapsedMs < 0) return null
            val age = nowUptimeMs - eventUptimeMs
            return (nowElapsedMs - age).takeIf { it >= 0 }
        }
        /** Android's standard Bluetooth media-controller packages; other clients stay usable. */
        fun isBluetoothController(packageName: String?): Boolean =
            packageName == "com.android.bluetooth" || packageName == "com.google.android.bluetooth"
    }
}
