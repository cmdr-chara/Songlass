package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.WelcomeLayout
import dev.cmdrchara.songlass.designsystem.WelcomeModel
import dev.cmdrchara.songlass.designsystem.WelcomePages
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.SonglassMark
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.rememberWelcomeBeat
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.welcomeEnter

private val Pill = RoundedCornerShape(percent = 50)

/**
 * iOS 26 welcome: colour behind, light on top. Slow glows drift behind the app mark, which is drawn here
 * as glass bars that move like a level meter. On the last page the mark makes room for a fan of the
 * person's own covers. Choices are glass cards with a small drawing of the style they select.
 */
@Composable
internal fun LiquidWelcome(model: WelcomeModel) {
    val colors = AppTheme.colors
    val glow = colors.accent
    // The glows follow the page, so moving on feels like travelling across one surface.
    val drift by animateFloatAsState(model.step.toFloat(), spring(dampingRatio = 1f, stiffness = 40f), label = "drift")
    Box(
        Modifier.fillMaxSize().background(colors.background).drawBehind {
            val a = if (colors.isDark) 0.40f else 0.30f
            drawRect(Brush.radialGradient(listOf(glow.copy(alpha = a), Color.Transparent),
                center = Offset(size.width * (0.85f - 0.22f * drift), size.height * (0.10f + 0.04f * drift)), radius = size.width * 0.95f))
            drawRect(Brush.radialGradient(listOf(glow.copy(alpha = a * 0.6f), Color.Transparent),
                center = Offset(size.width * (0.05f + 0.2f * drift), size.height * (0.78f - 0.06f * drift)), radius = size.width * 0.9f))
        }.windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 24.dp),
    ) {
        WelcomeLayout(
            step = model.step,
            modifier = Modifier.fillMaxSize(),
            topSpacing = 16.dp,
            horizontalAlignment = Alignment.CenterHorizontally,
            hero = {
                val room = minOf(maxHeight, maxWidth, 250.dp)
                // Keep outgoing artwork until its fade finishes, even after Back clears the model.
                val coverSnapshot = model.covers.takeIf { it.size >= 3 }?.take(5)
                Crossfade(coverSnapshot, label = "hero") { covers ->
                    if (covers != null) CoverFan(covers, minOf(room / 224.dp, maxWidth / 336.dp).coerceIn(0f, 1.3f)) else GlassMark(room)
                }
            },
            content = {
                WelcomePages(model) { page ->
                    AppText(page.title, AppTheme.typography.largeTitle.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    AppText(page.body, AppTheme.typography.body, color = colors.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (page.choices.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            page.choices.forEach { choice ->
                                val shape = RoundedCornerShape(24.dp)
                                Column(
                                    Modifier.weight(1f).clip(shape)
                                        .background(if (choice.selected) colors.label.copy(alpha = 0.15f) else colors.label.copy(alpha = 0.06f))
                                        .border(BorderStroke(if (choice.selected) 1.3.dp else 0.6.dp, colors.label.copy(alpha = if (choice.selected) 0.6f else 0.2f)), shape)
                                        .selectable(choice.selected, remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = choice.onSelect)
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    choice.preview(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)))
                                    Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AppText(choice.label, AppTheme.typography.footnote.copy(fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Normal),
                                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                                        if (choice.selected) AppIcon(AppIcons.Checkmark, null, size = 14.dp, tint = colors.accent, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                    page.status?.let {
                        Spacer(Modifier.height(22.dp))
                        Box(Modifier.clip(Pill).background(colors.label.copy(alpha = 0.08f)).padding(horizontal = 16.dp, vertical = 9.dp).align(Alignment.CenterHorizontally)) {
                            AppText(it, AppTheme.typography.subheadline, color = colors.label)
                        }
                    }
                }
            },
            footer = {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.semantics { contentDescription = model.stepDescription }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(model.stepCount) { i ->
                        val width by animateFloatAsState(if (i + 1 == model.step) 22f else 8f, spring(dampingRatio = 0.8f, stiffness = 400f), label = "dot")
                        Box(Modifier.height(8.dp).width(width.dp).clip(CircleShape)
                            .background(if (i + 1 == model.step) colors.label else colors.label.copy(alpha = 0.25f)))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val onBack = model.onBack
                    val backLabel = model.backLabel
                    if (onBack != null && backLabel != null) {
                        LiquidGlassIconButton(AppIcons.ArrowBack, backLabel, onBack, selected = false,
                            modifier = Modifier, hitSize = 56.dp, filled = true)
                    }
                    Box(
                        Modifier.weight(1f).heightIn(min = 56.dp).clip(Pill)
                            .background(Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.95f), colors.accent.copy(alpha = 0.78f))))
                            .border(BorderStroke(0.8.dp, Color.White.copy(alpha = 0.45f)), Pill)
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = model.onPrimary),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(model.primaryLabel, AppTheme.typography.headline, color = if (colors.isDark) Color.Black else Color.White)
                    }
                }
                val onSecondary = model.onSecondary
                val secondary = model.secondaryLabel
                if (secondary != null && onSecondary != null) {
                    AppText(secondary, AppTheme.typography.body, color = colors.accent,
                        modifier = Modifier.padding(top = 4.dp).clip(Pill).clickable(role = Role.Button, onClick = onSecondary).padding(horizontal = 20.dp, vertical = 12.dp))
                } else Spacer(Modifier.height(8.dp))
            },
        )
    }
}

/** The app icon, large: the flat violet tile with its bars and heart. */
@Composable
private fun GlassMark(markSize: androidx.compose.ui.unit.Dp) {
    val tile = RoundedCornerShape(markSize * 0.24f)
    Box(Modifier.size(markSize).welcomeEnter().shadow(28.dp, tile, ambientColor = Color(0xFF6C3BEE), spotColor = Color(0xFF6C3BEE))
        .clip(tile).background(Color(0xFF6C3BEE)), contentAlignment = Alignment.Center) {
        SonglassMark(markSize, bars = Color.White, heart = Color(0xFFFFD23F))
    }
}

/** Five of the person's covers, fanned like cards in a hand. */
@Composable
private fun CoverFan(covers: List<Artwork>, k: Float) {
    val angles = listOf(-14f, -7f, 0f, 7f, 14f)
    val offsets = listOf(-92, -46, 0, 46, 92)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // The middle one last, so it sits on top.
        listOf(0, 4, 1, 3, 2).filter { it < covers.size }.forEach { i ->
            Box(Modifier.offset(x = (offsets[i] * k).dp, y = (kotlin.math.abs(angles[i]) * 1.5f * k).dp).welcomeEnter(if (i == 2) 0 else 1 + kotlin.math.abs(i - 2))
                .rotate(angles[i]).shadow(14.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .border(0.8.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))) {
                AlbumArt(covers[i], Modifier.size((if (i == 2) 140 else 116).dp * k), cornerRadius = 0.dp)
            }
        }
    }
}
