package dev.cmdrchara.songlass.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Liquid Glass shows Search as its own circle by default (the iOS 26 reference). When enabled,
 * Search becomes a tab inside the bar so a single glass surface covers the whole navigation row.
 * Material and Miuix already keep Search in their bar and ignore this flag.
 */
val LocalSearchInNavigationBar = staticCompositionLocalOf { false }
