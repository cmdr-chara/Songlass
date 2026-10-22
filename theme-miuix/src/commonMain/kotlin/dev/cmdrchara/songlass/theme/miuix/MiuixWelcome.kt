package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.WelcomeModel
import dev.cmdrchara.songlass.designsystem.WelcomePages
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.SonglassMark
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.rememberWelcomeBeat
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.welcomeEnter

private val BrandBlue = Color(0xFF4B6BFF)

/**
 * HyperOS-style setup: a quiet page, a large title under the mark, native cards for the choices and
 * one full-width rounded button. The mark is a flat tile with bold bars; on the last page a tidy grid
 * of the person's own covers takes its place. HyperOS' blue marks what is selected.
 */
@Composable
internal fun MiuixWelcome(model: WelcomeModel) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        // The illustration takes whatever height the page below it leaves free.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
            val room = minOf(maxHeight, maxWidth * 0.8f, 300.dp).coerceAtLeast(96.dp)
            Crossfade(model.covers.size >= 3, label = "hero") { covers -> if (covers) CoverGrid(model, minOf((maxWidth - 20.dp) / 3, (maxHeight - 10.dp) / 2)) else BarTile(room) }
        }
        Spacer(Modifier.height(16.dp))
        WelcomePages(model) { page ->
            AppText(page.title, AppTheme.typography.largeTitle.copy(fontWeight = FontWeight.Medium), maxLines = 3)
            Spacer(Modifier.height(10.dp))
            AppText(page.body, AppTheme.typography.body, color = colors.secondaryLabel)
            if (page.choices.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    page.choices.forEach { choice ->
                        val shape = RoundedCornerShape(22.dp)
                        Column(
                            Modifier.weight(1f).clip(shape).background(colors.secondaryBackground)
                                .border(if (choice.selected) 2.dp else 0.dp, if (choice.selected) BrandBlue else Color.Transparent, shape)
                                .selectable(choice.selected, remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = choice.onSelect)
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            choice.preview(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)))
                            AppText(choice.label, AppTheme.typography.footnote.copy(fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Normal),
                                color = if (choice.selected) BrandBlue else colors.label, maxLines = 2, modifier = Modifier.heightIn(min = 34.dp))
                        }
                    }
                }
            }
            page.status?.let {
                Spacer(Modifier.height(20.dp))
                MiuixGroup({
                    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) { AppText(it, AppTheme.typography.body) }
                }, Modifier)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().semantics { contentDescription = model.stepDescription }, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            repeat(model.stepCount) { i ->
                val width by animateFloatAsState(if (i + 1 == model.step) 18f else 6f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "dot")
                Box(Modifier.height(6.dp).width(width.dp).clip(CircleShape).background(if (i + 1 == model.step) BrandBlue else colors.fill))
            }
        }
        Spacer(Modifier.height(16.dp))
        MiuixAction(model.primaryLabel, null, model.onPrimary, Modifier.fillMaxWidth().heightIn(min = 52.dp), primary = true)
        val onSecondary = model.onSecondary
        val label = model.secondaryLabel
        if (label != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            MiuixAction(label, null, onSecondary, Modifier.fillMaxWidth().heightIn(min = 52.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The app icon as a flat tile. */
@Composable
private fun BarTile(tile: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(tile).welcomeEnter().clip(RoundedCornerShape(tile * 0.24f)).background(Color(0xFF6C3BEE)), contentAlignment = Alignment.Center) {
        SonglassMark(tile, bars = Color.White, heart = Color(0xFFFFD23F))
    }
}

/** Six covers in two tidy rows. */
@Composable
private fun CoverGrid(model: WelcomeModel, cell: androidx.compose.ui.unit.Dp) {
    val covers = model.covers.take(6)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        covers.chunked(3).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEachIndexed { column, cover ->
                    Box(Modifier.size(cell).welcomeEnter(row * 3 + column).clip(RoundedCornerShape(22.dp))) {
                        AlbumArt(cover, Modifier.fillMaxSize(), cornerRadius = 0.dp)
                    }
                }
            }
        }
    }
}
