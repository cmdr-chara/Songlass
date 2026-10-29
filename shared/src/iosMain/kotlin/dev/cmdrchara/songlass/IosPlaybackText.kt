@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.cmdrchara.songlass

import platform.Foundation.NSBundle

internal fun iosPlaybackText(key: String, fallback: String): String =
    NSBundle.mainBundle.localizedStringForKey(key, fallback, "Localizable")
