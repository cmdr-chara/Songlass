package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.HomeHighlight

/** Preserve the Liquid/Material page composition; looks still own each individual card. */
@Composable
fun GlassHomePage(title: String, trailing: @Composable () -> Unit, contentPadding: PaddingValues, modifier: Modifier, content: LazyListScope.() -> Unit) {
    val look = LocalGlassLook.current
    LazyColumn(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = contentPadding) {
        item(key = "home-title") { look.PageTitle(title, trailing, Modifier) }
        content()
    }
}

@Composable
fun GlassHomeHighlights(highlights: List<HomeHighlight>, itemWidth: Dp, modifier: Modifier) {
    val look = LocalGlassLook.current
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(highlights, key = { it.key }) { pick ->
            look.PosterCard(pick.label, pick.title, pick.subtitle, pick.artwork, pick.onClick, Modifier.width(itemWidth))
        }
    }
}

/** Preserve the existing two-row detail heading in the glass styles. */
@Composable
fun GlassBackPageTitle(text: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) {
    val look = LocalGlassLook.current
    Column(modifier) {
        look.BackButton(backLabel, onBack, Modifier.windowInsetsPadding(WindowInsets.statusBars))
        AppText(text, AppTheme.typography.largeTitle,
            modifier = Modifier.padding(horizontal = look.contentHorizontalInset, vertical = 8.dp).semantics { heading() })
    }
}
