package dev.cmdrchara.songlass.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/** One of the interface styles offered on the first step; [selected] is the one in use. */
@Stable
data class WelcomeChoice(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/**
 * One page of the first-run welcome. The screen owns the steps, the permission and the clock;
 * each look decides how a page is drawn, so the three styles are three different designs.
 */
@Stable
data class WelcomeModel(
    val step: Int,
    val stepCount: Int,
    val title: String,
    val body: String,
    val primaryLabel: String,
    val onPrimary: () -> Unit,
    val secondaryLabel: String? = null,
    val onSecondary: (() -> Unit)? = null,
    /** Offered on the first step only. */
    val choices: List<WelcomeChoice> = emptyList(),
    /** A live line, such as the number of songs found. */
    val status: String? = null,
    /** "Step 1 of 3", for screen readers. */
    val stepDescription: String = "",
    /** The app's own mark, supplied by the screen because the looks cannot reach app resources. */
    val brand: @Composable (Modifier) -> Unit,
)

@Composable
fun Welcome(model: WelcomeModel) = LocalUiStyle.current.Welcome(model)
