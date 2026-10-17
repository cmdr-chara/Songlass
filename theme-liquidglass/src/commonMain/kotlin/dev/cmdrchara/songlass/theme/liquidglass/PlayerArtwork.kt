package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.foundation.LARGE_ARTWORK_PX
import dev.cmdrchara.songlass.designsystem.foundation.rememberArtworkImage
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A muted, medium-depth artwork tone, rather than a nearly black player surface. */
@Composable
internal fun rememberPlayerArtworkSurface(colors: List<Color>): Color {
    val first = colors.firstOrNull() ?: Color(0xFF656978)
    val middle = lerp(first, colors.getOrElse(1) { first }, 0.5f)
    var target = if (AppTheme.colors.isDark) lerp(Color(0xFF5C6071), middle, 0.22f)
        else lerp(middle, Color.White, 0.92f)
    if (AppTheme.colors.isDark) {
        // Keep white controls legible even when the source cover is very bright.
        repeat(5) { if (target.luminance() > 0.16f) target = lerp(target, Color.Black, 0.08f) }
    }
    val surface by animateColorAsState(
        targetValue = target,
        animationSpec = if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.SheetMillis),
        label = "player artwork surface",
    )
    return surface
}

/** Edge-to-edge cover with a tactile, finger-driven tilt; layout and controls stay fixed. */
@Composable
internal fun PlayerArtworkHero(
    artwork: Artwork,
    surface: Color,
    label: String,
    expandLabel: String,
    tapRequest: Int,
    onTap: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier,
) {
    val image = rememberArtworkImage(artwork, LARGE_ARTWORK_PX)
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val reduceTransparency = AppTheme.accessibility.reduceTransparency
    val dark = AppTheme.colors.isDark
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var dragging by remember { mutableStateOf(false) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var tapPulse by remember(artwork) { mutableStateOf(false) }
    LaunchedEffect(artwork, tapRequest, reduceMotion) {
        tapPulse = tapRequest > 0 && !reduceMotion
        if (tapPulse) {
            delay(180)
            tapPulse = false
        }
    }
    val touched = pressed || dragging || tapPulse
    val targetX = if (dragging) fingerX else if (tapPulse) 0.35f else 0f
    val targetY = if (dragging) fingerY else if (tapPulse) -0.2f else 0f
    LaunchedEffect(artwork, reduceMotion) {
        fingerX = 0f
        fingerY = 0f
        dragging = false
    }
    val motion = if (reduceMotion) snap<Float>() else spring<Float>(dampingRatio = 0.82f, stiffness = 450f)
    val tiltX by animateFloatAsState(if (touched && !reduceMotion) -targetY * 4f else 0f, motion, label = "cover vertical tilt")
    val tiltY by animateFloatAsState(if (touched && !reduceMotion) targetX * 4f else 0f, motion, label = "cover horizontal tilt")
    val lift by animateFloatAsState(if (touched && !reduceMotion) 1.035f else 1f, motion, label = "cover lift")
    val sheen by animateFloatAsState(
        if (touched && !reduceMotion && !reduceTransparency) 0.16f else 0f,
        if (reduceMotion || reduceTransparency) snap() else tween(LiquidGlassMotion.FadeMillis),
        label = "cover reflection",
    )
    val imageMotion = Modifier.graphicsLayer {
        rotationX = tiltX
        rotationY = tiltY
        translationX = tiltY / 4f * 3.dp.toPx()
        translationY = -tiltX / 4f * 3.dp.toPx()
        scaleX = lift
        scaleY = lift
        cameraDistance = size.width * 2.5f
    }
    val placeholder = artwork.colors.ifEmpty { listOf(surface, surface) }.let { if (it.size == 1) it + it else it }
    Box(
        modifier
            .clipToBounds()
            .background(Brush.verticalGradient(placeholder))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onTap,
                onLongClickLabel = expandLabel,
                onLongClick = onExpand,
            )
            .pointerInput(artwork, reduceMotion) {
                fun followFinger(position: Offset) {
                    if (size.width > 0 && size.height > 0 && !reduceMotion) {
                        fingerX = (position.x / size.width * 2f - 1f).coerceIn(-1f, 1f)
                        fingerY = (position.y / size.height * 2f - 1f).coerceIn(-1f, 1f)
                    }
                }
                detectDragGestures(
                    onDragStart = { position -> tapPulse = false; dragging = true; followFinger(position) },
                    onDragEnd = { dragging = false; fingerX = 0f; fingerY = 0f },
                    onDragCancel = { dragging = false; fingerX = 0f; fingerY = 0f },
                ) { change, _ ->
                    followFinger(change.position)
                    // Crossing touch slop cancels the sibling click/long-click gesture,
                    // keeping drag feedback separate from the deliberate cover viewer.
                    change.consume()
                }
            }
            .semantics { contentDescription = label }
            .drawWithContent {
                drawContent()
                if (sheen > 0f && !reduceMotion && !reduceTransparency) {
                    drawRect(Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = sheen), Color.Transparent),
                        center = Offset(size.width * (0.5f + tiltY / 4f * 0.28f), size.height * (0.35f - tiltX / 4f * 0.2f)),
                        radius = size.width * 0.85f,
                    ))
                }
                // Status icons and the dismiss affordance must remain readable over
                // both an all-white cover in dark mode and a black cover in light mode.
                val topScrim = if (dark) Color.Black else Color.White
                val protectedTop = (80.dp.toPx() / size.height).coerceIn(0.12f, 0.6f)
                drawRect(Brush.verticalGradient(
                    0f to topScrim.copy(alpha = 0.62f),
                    protectedTop to topScrim.copy(alpha = 0.62f),
                    (protectedTop + 0.1f).coerceAtMost(1f) to topScrim.copy(alpha = 0f),
                    1f to topScrim.copy(alpha = 0f),
                ))
                // Long eased fade: the cover dissolves over its lower half and the page color is
                // solid well before the title, so text never sits on the artwork's details.
                val fade = if (reduceTransparency) HeroFadeStopsOpaque else HeroFadeStops
                drawRect(Brush.verticalGradient(*Array(fade.size) { i -> fade[i].first to surface.copy(alpha = fade[i].second) }))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize().then(imageMotion))
        } else {
            Image(
                AppIcons.MusicNote, contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxSize(0.3f).then(imageMotion),
            )
        }
    }
}

/** A separate modal image viewer: its drag closes the cover, never the player underneath. */
@Composable
internal fun PlayerArtworkExpansion(
    artwork: Artwork,
    expanded: Boolean,
    artworkLabel: String,
    closeLabel: String,
    onClose: () -> Unit,
) {
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val currentOnClose by rememberUpdatedState(onClose)
    val image = rememberArtworkImage(artwork, LARGE_ARTWORK_PX)
    val scope = rememberCoroutineScope()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    val velocityThreshold = with(LocalDensity.current) { 900.dp.toPx() }
    LaunchedEffect(expanded, reduceMotion) {
        settleJob?.cancel()
        if (expanded) dragOffset = 0f
    }
    AnimatedVisibility(
        visible = expanded,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(if (reduceMotion) LiquidGlassMotion.ReducedMillis else LiquidGlassMotion.FadeMillis)) +
            if (reduceMotion) androidx.compose.animation.EnterTransition.None else scaleIn(tween(LiquidGlassMotion.NavigationMillis), initialScale = 0.96f),
        exit = fadeOut(tween(if (reduceMotion) LiquidGlassMotion.ReducedMillis else LiquidGlassMotion.FadeMillis)) +
            if (reduceMotion) androidx.compose.animation.ExitTransition.None else scaleOut(tween(LiquidGlassMotion.FadeMillis), targetScale = 0.98f),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(if (AppTheme.colors.isDark) Color(0xFF111115) else Color(0xFFF7F7FA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Button, onClickLabel = closeLabel,
                    onClick = { if (expanded) currentOnClose() },
                )
                .semantics { contentDescription = artworkLabel }
                .then(if (expanded) Modifier else Modifier.clearAndSetSemantics {})
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = expanded,
                    state = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceAtLeast(0f) },
                    onDragStarted = { settleJob?.cancel() },
                    onDragStopped = { velocity ->
                        if (dragOffset > threshold || velocity > velocityThreshold) currentOnClose()
                        else if (reduceMotion) dragOffset = 0f
                        else settleJob = scope.launch {
                            animate(dragOffset, 0f, initialVelocity = velocity, animationSpec = spring(dampingRatio = 1f, stiffness = 500f)) { value, _ ->
                                dragOffset = value.coerceAtLeast(0f)
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val imageModifier = Modifier.fillMaxSize().graphicsLayer {
                translationY = if (reduceMotion) 0f else dragOffset
                val shrink = if (reduceMotion || size.height == 0f) 1f else (1f - dragOffset / size.height * 0.2f).coerceIn(0.8f, 1f)
                scaleX = shrink
                scaleY = shrink
            }
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = imageModifier)
            } else {
                LiquidGlassAlbumArt(artwork, 0.dp, Modifier.fillMaxSize(0.85f))
            }
            Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
                LiquidGlassIconButton(AppIcons.Close, closeLabel, currentOnClose, false, Modifier, filled = true)
            }
        }
    }
}

/** (position, alpha) stops of the hero fade, following an ease-in-out curve. */
private val HeroFadeStops = listOf(
    0f to 0f, 0.3f to 0f, 0.4f to 0.06f, 0.5f to 0.2f, 0.6f to 0.45f,
    0.7f to 0.72f, 0.78f to 0.9f, 0.85f to 1f, 1f to 1f,
)

/** Reduce Transparency: the same curve, reaching the solid color sooner. */
private val HeroFadeStopsOpaque = listOf(
    0f to 0f, 0.25f to 0f, 0.35f to 0.15f, 0.5f to 0.55f, 0.65f to 0.9f, 0.72f to 1f, 1f to 1f,
)
