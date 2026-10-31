package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Identity, then two quiet rows; the detailed data-use text and the licenses live one level down. */
@Composable
fun AboutScreen(graph: dev.cmdrchara.songlass.AppGraph, onBack: () -> Unit, contentPadding: PaddingValues, searchFocus: SettingsFocusRequest? = null) {
    val information = rememberAppInformation()
    val mainScroll = rememberLazyListState()
    val licensesScroll = rememberLazyListState()
    // Subpages share this owner: returning must not reread the installed version
    // or temporarily show an unrelated loading/recovery presentation.
    var version by remember(information) { mutableStateOf<VersionRead>(VersionRead.Loading) }
    var versionAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(information, versionAttempt) {
        version = try {
            val installed = information.readVersion()
            if (installed.name == null && installed.build == null) VersionRead.Unavailable else VersionRead.Available(installed)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { VersionRead.Unavailable }
    }
    var page by rememberSaveable { mutableIntStateOf(AboutPage.Main.ordinal) }
    var selectedLicenseId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedLicense = information.licenses.firstOrNull { it.name == selectedLicenseId }
    val current = AboutPage.entries[page]
    PlatformBackHandler(current != AboutPage.Main || selectedLicense != null) {
        if (selectedLicense != null) selectedLicenseId = null else page = AboutPage.Main.ordinal
    }
    LocalGlassLook.current.SettingsPage {
        when {
            selectedLicense != null -> LicenseReader(information, selectedLicense, contentPadding, onBack = { selectedLicenseId = null })
            current == AboutPage.Privacy -> AboutPrivacy(graph, contentPadding) { page = AboutPage.Main.ordinal }
            current == AboutPage.Licenses -> AboutLicenses(information, contentPadding, licensesScroll, { selectedLicenseId = it.name }) { page = AboutPage.Main.ordinal }
            else -> AboutMain(graph, contentPadding, searchFocus, mainScroll, version, { versionAttempt++ }, onBack, { page = it.ordinal })
        }
    }
}

private enum class AboutPage { Main, Privacy, Licenses }

@Composable
private fun AboutMain(
    graph: dev.cmdrchara.songlass.AppGraph, contentPadding: PaddingValues,
    searchFocus: SettingsFocusRequest?, listState: LazyListState, version: VersionRead,
    onRetryVersion: () -> Unit, onBack: () -> Unit, open: (AboutPage) -> Unit,
) {
    val searchReveal = SettingsSearchScroll(searchFocus, listState, if (searchFocus?.target == SettingsTarget.About) 1 else null)
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).then(searchReveal),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { AboutTitle(stringResource(Res.string.about_page_title), stringResource(if (searchFocus == null) Res.string.settings_title else Res.string.settings_search_results), onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(if (AppTheme.colors.isDark) Res.drawable.songlass_brand_dark else Res.drawable.songlass_brand_light),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)),
                )
                Spacer(Modifier.height(16.dp))
                SettingsSearchAnchor(SettingsTarget.About, searchFocus) {
                    AppText("Songlass", AppTheme.typography.largeTitle,
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                        textAlign = TextAlign.Center)
                }
                AppText(stringResource(Res.string.about_tagline), AppTheme.typography.body, color = AppTheme.colors.secondaryLabel, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                AboutVersion(version, onRetry = onRetryVersion)
            }
        }
        // Only shown when an interrupted tag edit left recovery copies behind.
        graph.tagEditor?.let { editor -> item(key = "tag_recovery") { TagRecoveryPanel(editor) } }
        item {
            LocalGlassLook.current.SettingsDestinationGroup(Modifier.padding(horizontal = pageInset)) {
                LocalGlassLook.current.SettingsDestination(stringResource(Res.string.about_data_title),
                    stringResource(Res.string.about_data_summary), AppIcons.InfoCircle, false) { open(AboutPage.Privacy) }
                LocalGlassLook.current.SettingsDestination(stringResource(Res.string.about_open_source),
                    stringResource(Res.string.about_open_source_summary), AppIcons.Bookmark, false) { open(AboutPage.Licenses) }
            }
        }
    }
}

@Composable
private fun AboutTitle(title: String, backLabel: String, onBack: () -> Unit) =
    LocalGlassLook.current.SettingsPageTitle(title, backLabel, onBack, Modifier)

/** The facts about what stays on the device and what is sent, kept intact but off the first screen. */
@Composable
private fun AboutPrivacy(graph: dev.cmdrchara.songlass.AppGraph, contentPadding: PaddingValues, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var linkFailed by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { AboutTitle(stringResource(Res.string.about_data_title), stringResource(Res.string.about_page_title), onBack) }
        item { AboutSection(stringResource(Res.string.about_privacy_title), stringResource(Res.string.about_privacy_body)) }
        item {
            AboutSection(stringResource(Res.string.about_lyrics_title), stringResource(Res.string.about_lyrics_body)) {
                ActionButton(stringResource(Res.string.about_lyrics_source), {
                    linkFailed = runCatching { uriHandler.openUri("https://github.com/amll-dev/amll-ttml-db") }.isFailure
                })
                if (linkFailed) AboutStatus(stringResource(Res.string.about_link_unavailable))
            }
        }
        item { AboutSection(stringResource(Res.string.artwork_online_title), stringResource(Res.string.feedback_cover_privacy)) }
        item { AboutSection(stringResource(Res.string.about_autoeq_title), stringResource(Res.string.about_autoeq_body)) }
        if (graph.tagEditor != null) item { AboutSection(stringResource(Res.string.about_tags_title), stringResource(Res.string.about_tags_body)) }
    }
}

@Composable
private fun AboutLicenses(information: AppInformation, contentPadding: PaddingValues, listState: LazyListState, onOpen: (BundledLicense) -> Unit, onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { AboutTitle(stringResource(Res.string.about_open_source), stringResource(Res.string.about_page_title), onBack) }
        item {
            SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                Column {
                    information.licenses.forEach { license ->
                        ListRow(
                            title = licenseTitle(license),
                            subtitle = licenseSummary(license),
                            fullText = true,
                            trailing = { AppIcon(AppIcons.ChevronRight, null, tint = AppTheme.colors.tertiaryLabel) },
                            onClick = { onOpen(license) },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface VersionRead {
    data object Loading : VersionRead
    data object Unavailable : VersionRead
    data class Available(val version: InstalledAppVersion) : VersionRead
}

@Composable
private fun AboutVersion(status: VersionRead, onRetry: () -> Unit) {
    val label = when (status) {
        VersionRead.Loading -> stringResource(Res.string.about_version_loading)
        VersionRead.Unavailable -> stringResource(Res.string.about_version_unavailable)
        is VersionRead.Available -> when {
            status.version.name != null && status.version.build != null ->
                stringResource(Res.string.about_version_with_build, status.version.name, status.version.build)
            status.version.name != null -> stringResource(Res.string.about_version_name, status.version.name)
            else -> stringResource(Res.string.about_version_build, status.version.build.orEmpty())
        }
    }
    AppText(label, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
        textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    if (status == VersionRead.Unavailable) ActionButton(stringResource(Res.string.about_release_retry), onRetry)
}

private sealed interface LicenseRead {
    data object Loading : LicenseRead
    data object Unavailable : LicenseRead
    data class Available(val documents: List<LicenseDocument>) : LicenseRead
}

/** A local subpage: Back returns to the same About scroll position, without changing app routes. */
@Composable
private fun LicenseReader(information: AppInformation, license: BundledLicense, contentPadding: PaddingValues, onBack: () -> Unit) {
    var content by remember(information, license) { mutableStateOf<LicenseRead>(LicenseRead.Loading) }
    var attempt by remember(license) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val title = licenseTitle(license)
    LaunchedEffect(information, license, attempt) {
        content = LicenseRead.Loading
        content = try {
            val documents = information.readLicense(license)
            if (documents.isEmpty()) LicenseRead.Unavailable else LicenseRead.Available(documents)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LicenseRead.Unavailable
        }
    }
    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(
        handleColor = AppTheme.colors.accent, backgroundColor = AppTheme.colors.accent.copy(alpha = 0.28f),
    )) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).semantics { paneTitle = title },
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") { AboutTitle(title, stringResource(Res.string.about_open_source), onBack) }
            when (val current = content) {
                LicenseRead.Loading -> item(key = "loading") {
                    AboutStatus(stringResource(Res.string.about_license_loading), Modifier.padding(horizontal = pageInset))
                }
                LicenseRead.Unavailable -> item(key = "error") {
                    AboutSection(title, stringResource(Res.string.about_license_unavailable), announceBody = true) {
                        ActionButton(stringResource(Res.string.about_release_retry), { attempt++ })
                    }
                }
                is LicenseRead.Available -> current.documents.forEachIndexed { documentIndex, document ->
                    item(key = "document-$documentIndex") {
                        AppText(licenseTextTitle(document.kind), AppTheme.typography.headline,
                            modifier = Modifier.padding(horizontal = pageInset).padding(top = 12.dp).semantics { heading() })
                    }
                    items(document.paragraphs.size, key = { paragraphIndex -> "document-$documentIndex-$paragraphIndex" }) { paragraphIndex ->
                        SelectionContainer {
                            AppText(document.paragraphs[paragraphIndex], AppTheme.typography.body,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = pageInset))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutStatus(text: String, modifier: Modifier = Modifier) {
    AppText(text, AppTheme.typography.body, color = AppTheme.colors.secondaryLabel,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun licenseTitle(license: BundledLicense): String = stringResource(when (license) {
    BundledLicense.Songlass -> Res.string.about_license_songlass
    BundledLicense.AutoEq -> Res.string.about_license_autoeq
    BundledLicense.Miuix -> Res.string.about_license_miuix
    BundledLicense.TagLib -> Res.string.about_license_taglib
    BundledLicense.OtherOpenSource -> Res.string.about_license_other
})

@Composable
private fun licenseSummary(license: BundledLicense): String = when (license) {
    BundledLicense.Songlass -> licenseTextTitle(LicenseTextKind.PolyFormPerimeter)
    BundledLicense.AutoEq -> licenseTextTitle(LicenseTextKind.Mit)
    BundledLicense.Miuix -> licenseTextTitle(LicenseTextKind.Apache2)
    BundledLicense.TagLib -> stringResource(Res.string.about_license_taglib_summary)
    BundledLicense.OtherOpenSource -> stringResource(Res.string.about_license_other_summary)
}

@Composable
private fun licenseTextTitle(kind: LicenseTextKind): String = stringResource(when (kind) {
    LicenseTextKind.Notice -> Res.string.about_license_notice
    LicenseTextKind.PolyFormPerimeter -> Res.string.about_license_polyform_perimeter
    LicenseTextKind.Mit -> Res.string.about_license_mit
    LicenseTextKind.Apache2 -> Res.string.about_license_apache
    LicenseTextKind.Lgpl21 -> Res.string.about_license_lgpl
    LicenseTextKind.Mpl11 -> Res.string.about_license_mpl
    LicenseTextKind.Bsd3 -> Res.string.about_license_bsd3
    LicenseTextKind.Boost10 -> Res.string.about_license_boost
})

@Composable
private fun AboutSection(title: String, body: String, announceBody: Boolean = false, footer: @Composable (() -> Unit)? = null) {
    SettingsGroup(Modifier.padding(horizontal = pageInset)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(title, AppTheme.typography.headline, modifier = Modifier.semantics { heading() })
            AppText(body, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel,
                modifier = if (announceBody) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
            footer?.invoke()
        }
    }
}
