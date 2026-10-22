package dev.cmdrchara.songlass.core.onboarding

import dev.cmdrchara.songlass.core.settings.InterfaceStyle

/** The kind of phone Songlass runs on, as far as choosing a first interface style is concerned. */
enum class DeviceFamily { Apple, Pixel, Xiaomi, OtherAndroid }

/**
 * What the platform tells about itself. Manufacturer and brand are compared case-insensitively;
 * they are only ever used to pick a starting style, never to unlock or lock features.
 */
data class DeviceInfo(val isApple: Boolean, val manufacturer: String = "", val brand: String = "") {
    val family: DeviceFamily
        get() {
            if (isApple) return DeviceFamily.Apple
            val names = setOf(manufacturer.trim().lowercase(), brand.trim().lowercase())
            return when {
                "google" in names -> DeviceFamily.Pixel
                names.any { it in XIAOMI_NAMES } -> DeviceFamily.Xiaomi
                else -> DeviceFamily.OtherAndroid
            }
        }

    companion object {
        val Apple = DeviceInfo(isApple = true)
        fun android(manufacturer: String?, brand: String?) = DeviceInfo(false, manufacturer.orEmpty(), brand.orEmpty())

        // Xiaomi sells under three names; all of them run HyperOS (or MIUI before it).
        private val XIAOMI_NAMES = setOf("xiaomi", "redmi", "poco")
    }
}

/** The style a phone starts with: its own platform's design language, changeable at any time. */
object StyleRecommendation {
    fun forDevice(device: DeviceInfo): InterfaceStyle = when (device.family) {
        DeviceFamily.Apple -> InterfaceStyle.LiquidGlass
        DeviceFamily.Xiaomi -> InterfaceStyle.Miuix
        DeviceFamily.Pixel, DeviceFamily.OtherAndroid -> InterfaceStyle.MaterialGlass
    }
}
