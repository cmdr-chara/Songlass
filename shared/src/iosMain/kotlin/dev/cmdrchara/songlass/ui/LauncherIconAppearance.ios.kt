package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import dev.cmdrchara.songlass.core.settings.LauncherIconVariant
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.settings_app_icon_hint_ios
import org.jetbrains.compose.resources.stringResource

// UIKit selects bundled icons; the user controls Home Screen tint independently of the app.
// Keep the shared bundled PNG preview rather than imply an arbitrary runtime icon palette.
@Composable
internal actual fun platformLauncherIconPainter(variant: LauncherIconVariant): Painter? = null

@Composable
internal actual fun launcherIconHint(): String = stringResource(Res.string.settings_app_icon_hint_ios)
