package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.designsystem.theme.UiAccessibility

/** Observes the platform preferences while the application UI is in composition. */
@Composable
expect fun rememberSystemAccessibility(): UiAccessibility
