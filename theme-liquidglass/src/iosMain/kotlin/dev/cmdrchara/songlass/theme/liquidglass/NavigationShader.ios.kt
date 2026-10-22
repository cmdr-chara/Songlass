package dev.cmdrchara.songlass.theme.liquidglass

// Preserve the existing Haze material until the custom renderer is validated on Skia/iOS.
internal actual val unifiedNavigationSupported: Boolean = false
internal actual fun createNavigationShader(): NavigationShader? = null
