package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Identity and a plain-language description of the app's current data use. */
@Composable
fun AboutScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BackLink(stringResource(Res.string.settings_title), onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(if (AppTheme.colors.isDark) Res.drawable.songlass_brand_dark else Res.drawable.songlass_brand_light),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)),
                )
                Spacer(Modifier.height(16.dp))
                AppText("Songlass", AppTheme.typography.largeTitle, modifier = Modifier.semantics { heading() })
                AppText(stringResource(Res.string.about_tagline), AppTheme.typography.body, color = AppTheme.colors.secondaryLabel, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        item { AboutSection(stringResource(Res.string.about_privacy_title), stringResource(Res.string.about_privacy_body)) }
        item {
            AboutSection(stringResource(Res.string.about_lyrics_title), stringResource(Res.string.about_lyrics_body)) {
                ActionButton(stringResource(Res.string.about_lyrics_source), {
                    uriHandler.openUri("https://github.com/amll-dev/amll-ttml-db")
                })
            }
        }
        item { AboutSection(stringResource(Res.string.about_autoeq_title), stringResource(Res.string.about_autoeq_body)) }
        item { AboutSection(stringResource(Res.string.about_tags_title), stringResource(Res.string.about_tags_body)) }
    }
}

@Composable
private fun AboutSection(title: String, body: String, footer: @Composable (() -> Unit)? = null) {
    SettingsGroup(Modifier.padding(horizontal = pageInset)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(title, AppTheme.typography.headline, modifier = Modifier.semantics { heading() })
            AppText(body, AppTheme.typography.body, color = AppTheme.colors.secondaryLabel)
            footer?.invoke()
        }
    }
}
