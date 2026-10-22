package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.WelcomeModel
import dev.cmdrchara.songlass.designsystem.WelcomePages
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.rememberWelcomeBeat
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.welcomeBar
import dev.cmdrchara.songlass.designsystem.welcomeEnter

/**
 * Material 3 Expressive welcome: a big shape that turns slowly and changes with each page, the mark
 * inside it moving like a level meter, left-aligned text, choices as tonal cards with a small drawing
 * of the style. On the last page the shape gives way to three of the person's covers, each in its own shape.
 */
@Composable
internal fun MaterialWelcome(model: WelcomeModel, palette: SeaPalette) {
    Column(Modifier.fillMaxSize().background(palette.background).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        // The illustration takes whatever height the page below it leaves free.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val room = minOf(maxHeight, maxWidth * 1.15f, 340.dp).coerceAtLeast(110.dp)
            Crossfade(model.covers.size >= 3, label = "hero") { covers ->
                if (covers) CoverShapes(model, room, maxWidth) else ExpressiveMark(model.step, palette, room)
            }
        }
        Spacer(Modifier.height(12.dp))
        WelcomePages(model) { page ->
            AppText(page.title, AppTheme.typography.largeTitle.copy(fontWeight = FontWeight.Medium), color = palette.label)
            Spacer(Modifier.height(10.dp))
            AppText(page.body, AppTheme.typography.body, color = palette.secondaryLabel)
            if (page.choices.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    page.choices.forEach { choice ->
                        val shape = RoundedCornerShape(26.dp)
                        Column(
                            Modifier.weight(1f).clip(shape)
                                .background(if (choice.selected) palette.primaryContainer else palette.containerHigh)
                                .selectable(choice.selected, remember { MutableInteractionSource() }, MaterialRipple, role = Role.RadioButton, onClick = choice.onSelect)
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            choice.preview(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)))
                            Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (choice.selected) AppIcon(AppIcons.Checkmark, null, size = 14.dp, tint = palette.onPrimaryContainer, modifier = Modifier.padding(end = 4.dp))
                                AppText(choice.label, AppTheme.typography.footnote.copy(fontWeight = FontWeight.Medium),
                                    color = if (choice.selected) palette.onPrimaryContainer else palette.label, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                            }
                        }
                    }
                }
            }
            page.status?.let {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(palette.primaryContainer).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    AppText(it, AppTheme.typography.subheadline, color = palette.onPrimaryContainer)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.semantics { contentDescription = model.stepDescription }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(model.stepCount) { i ->
                val current = i + 1 == model.step
                val width by animateFloatAsState(if (current) 32f else 8f, spring(dampingRatio = 0.75f, stiffness = 450f), label = "dot")
                Box(Modifier.height(8.dp).width(width.dp).clip(CircleShape).background(if (current) palette.primary else palette.containerHigh))
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape).background(palette.primary)
                .clickable(remember { MutableInteractionSource() }, MaterialRipple, role = Role.Button, onClick = model.onPrimary),
            contentAlignment = Alignment.Center,
        ) { AppText(model.primaryLabel, AppTheme.typography.headline, color = palette.onPrimary) }
        val onSecondary = model.onSecondary
        val secondary = model.secondaryLabel
        if (secondary != null && onSecondary != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppText(secondary, AppTheme.typography.body.copy(fontWeight = FontWeight.Medium), color = palette.primary,
                    modifier = Modifier.padding(top = 4.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, MaterialRipple, role = Role.Button, onClick = onSecondary)
                        .padding(horizontal = 24.dp, vertical = 14.dp))
            }
        } else Spacer(Modifier.height(14.dp))
    }
}

/** A large shape, a different one on each page, turning slowly behind the five moving bars. */
@Composable
private fun ExpressiveMark(step: Int, palette: SeaPalette, room: androidx.compose.ui.unit.Dp) {
    val beat = rememberWelcomeBeat()
    val animated = !AppTheme.accessibility.reduceMotion
    val turn = if (animated) rememberInfiniteTransition(label = "turn").animateFloat(0f, 360f,
        infiniteRepeatable(tween(36000, easing = LinearEasing), RepeatMode.Restart), label = "angle").value else 0f
    val shapes = ExpressiveShapes.sequence
    Box(Modifier.size(room).welcomeEnter(), contentAlignment = Alignment.Center) {
        Crossfade(step, label = "shape") { page ->
            Box(Modifier.size(room * 0.95f).graphicsLayer { rotationZ = turn }.clip(shapes[(page - 1).coerceAtLeast(0) % shapes.size]).background(palette.primaryContainer))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp).size(room * 0.27f).graphicsLayer { rotationZ = -turn * 1.5f }
            .clip(shapes[(step + 1) % shapes.size]).background(palette.vivid.copy(alpha = 0.9f)))
        Box(Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp, start = 4.dp).size(room * 0.2f).graphicsLayer { rotationZ = turn * 2f }
            .clip(shapes[(step + 3) % shapes.size]).background(palette.containerHigh))
        Canvas(Modifier.size(room * 0.46f)) {
            val u = size.width / 56f
            val width = 6.4f * u
            val gap = 3.8f * u
            val startX = size.width / 2 - (5 * width + 4 * gap) / 2
            for (i in 0 until 5) {
                val h = 50f * u * welcomeBar(i, beat.value, animated)
                drawRoundRect(palette.primary, Offset(startX + i * (width + gap), size.height / 2 - h / 2), Size(width, h), CornerRadius(width / 2))
            }
        }
    }
}

/** Three of the person's covers, each cut into an expressive shape. */
@Composable
private fun CoverShapes(model: WelcomeModel, room: androidx.compose.ui.unit.Dp, width: androidx.compose.ui.unit.Dp) {
    val covers = model.covers
    val shapes: List<Shape> = listOf(ExpressiveShapes.Cookie, ExpressiveShapes.Arch, ExpressiveShapes.Clover)
    Box(Modifier.size(room * 1.12f, room)) {
        Box(Modifier.align(Alignment.Center).offset(y = (-room * 0.02f)).size(room * 0.78f).welcomeEnter(0).clip(shapes[0])) {
            AlbumArt(covers[0], Modifier.fillMaxSize(), cornerRadius = 0.dp)
        }
        Box(Modifier.align(Alignment.CenterStart).offset(y = room * 0.26f).size(room * 0.46f).welcomeEnter(1).clip(shapes[1])) {
            AlbumArt(covers[1], Modifier.fillMaxSize(), cornerRadius = 0.dp)
        }
        Box(Modifier.align(Alignment.CenterEnd).offset(y = -(room * 0.3f)).size(room * 0.42f).welcomeEnter(2).clip(shapes[2])) {
            AlbumArt(covers[2], Modifier.fillMaxSize(), cornerRadius = 0.dp)
        }
        if (covers.size > 3) Box(Modifier.align(Alignment.BottomEnd).offset(y = -(room * 0.01f)).size(room * 0.32f).welcomeEnter(3).clip(ExpressiveShapes.Flower)) {
            AlbumArt(covers[3], Modifier.fillMaxSize(), cornerRadius = 0.dp)
        }
    }
}
