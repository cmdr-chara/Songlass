package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.core.settings.InterfaceStyle
import dev.cmdrchara.songlass.designsystem.Welcome
import dev.cmdrchara.songlass.designsystem.WelcomeChoice
import dev.cmdrchara.songlass.designsystem.WelcomeModel
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private enum class WelcomeStep { Style, Access, Ready }

/**
 * The first-run welcome: choose a style (the phone's own is already selected), give access to the
 * music if this platform asks for it, then start. At most three pages; each look draws them its own way.
 */
@Composable
fun WelcomeFlow(graph: AppGraph, onRequestLibraryAccess: (() -> Unit)?) {
    val preferences by graph.settings.state.collectAsState()
    val hasAccess by graph.source.hasAccess.collectAsState()
    val loading by graph.isLibraryLoading.collectAsState()
    val library by graph.library.collectAsState()
    // The access page exists only where the platform asks, and only if it has not been granted already.
    val steps = remember { listOfNotNull(WelcomeStep.Style, WelcomeStep.Access.takeIf { onRequestLibraryAccess != null && !hasAccess }, WelcomeStep.Ready) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(steps.indices)]
    fun next() { index = (index + 1).coerceAtMost(steps.lastIndex) }
    PlatformBackHandler(index > 0) { index-- }
    // The permission dialog answers on its own: move on as soon as access arrives.
    LaunchedEffect(hasAccess) {
        // The app behind the welcome reads the library when access changes; until it is shown, the welcome must too.
        graph.refreshLibrary()
        if (hasAccess && step == WelcomeStep.Access) next()
    }
    SystemBarAppearance(lightIcons = AppTheme.colors.isDark)
    // A few real covers for the last page, the same ones every time this person sees it.
    val covers = remember(library, step) {
        if (step != WelcomeStep.Ready) emptyList()
        else library.tracks.map { it.artwork(library) }.filter { it.imageKey != null }.distinctBy { it.imageKey }
            .shuffled(kotlin.random.Random(11)).take(6).takeIf { it.size >= 3 }.orEmpty()
    }
    val position = index.coerceIn(steps.indices) + 1
    val common = Triple(position, steps.size, stringResource(Res.string.welcome_step, position, steps.size))
    val model = when (step) {
        WelcomeStep.Style -> WelcomeModel(
            step = common.first, stepCount = common.second, stepDescription = common.third, title = stringResource(Res.string.welcome_title),
            body = stringResource(Res.string.welcome_style_body, styleName(graph.onboarding.recommendedStyle ?: InterfaceStyle.LiquidGlass)),
            choices = InterfaceStyle.entries.map { style ->
                WelcomeChoice(styleName(style), preferences.interfaceStyle == style, { graph.settings.setInterfaceStyle(style) }) { modifier ->
                    androidx.compose.foundation.layout.Box(modifier) { StylePreview(style) }
                }
            },
            primaryLabel = stringResource(Res.string.welcome_continue), onPrimary = ::next,
        )
        WelcomeStep.Access -> WelcomeModel(
            step = common.first, stepCount = common.second, stepDescription = common.third, title = stringResource(Res.string.welcome_access_title), body = stringResource(Res.string.welcome_access_body),
            primaryLabel = stringResource(Res.string.welcome_access_allow), onPrimary = { onRequestLibraryAccess?.invoke() },
            secondaryLabel = stringResource(Res.string.welcome_not_now), onSecondary = ::next,
        )
        WelcomeStep.Ready -> WelcomeModel(
            step = common.first, stepCount = common.second, stepDescription = common.third, title = stringResource(Res.string.welcome_ready_title), body = stringResource(Res.string.welcome_ready_body),
            status = when {
                !hasAccess && onRequestLibraryAccess != null -> stringResource(Res.string.welcome_ready_no_access)
                loading -> stringResource(Res.string.welcome_ready_searching)
                library.tracks.isEmpty() -> stringResource(Res.string.welcome_ready_empty)
                else -> pluralStringResource(Res.plurals.welcome_ready_found, library.tracks.size, library.tracks.size)
            },
            covers = covers,
            primaryLabel = stringResource(Res.string.welcome_start), onPrimary = { graph.onboarding.complete() },
        )
    }
    Welcome(model)
}
