package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import dev.cmdrchara.songlass.core.settings.LauncherIconVariant

/** Native Android icons resolve the same system resources in the launcher and in this preview. */
@Composable
internal expect fun platformLauncherIconPainter(variant: LauncherIconVariant): Painter?

@Composable
internal expect fun launcherIconHint(): String
