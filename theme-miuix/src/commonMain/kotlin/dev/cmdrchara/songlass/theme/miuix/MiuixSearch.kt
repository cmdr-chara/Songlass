package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A dedicated search page keeps its query after IME submission or loss of focus. */
@Composable
internal fun MiuixSearch(query: String, onQueryChange: (String) -> Unit, placeholder: String, clearLabel: String, modifier: Modifier) {
    val colors = AppTheme.colors
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var expanded by remember { mutableStateOf(false) }
    // InputField's collapse clears a focused query. Collapse only an empty, unfocused
    // field; a submitted search stays visible while browsing results or switching tabs.
    LaunchedEffect(focused, query.isEmpty()) {
        if (!focused && query.isEmpty()) expanded = false
    }
    InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { focusManager.clearFocus(); keyboard?.hide() },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = placeholder },
        label = placeholder,
        textStyle = AppTheme.typography.body.copy(fontSize = 17.sp),
        color = colors.fill,
        interactionSource = interaction,
        leadingIcon = {
            AppIcon(AppIcons.Search, null, modifier = Modifier.padding(start = 16.dp, end = 10.dp),
                size = 20.dp, tint = colors.secondaryLabel)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.padding(end = 4.dp).semantics { contentDescription = clearLabel; role = Role.Button },
                    backgroundColor = Color.Transparent,
                    minWidth = 48.dp,
                    minHeight = 48.dp,
                    cornerRadius = 24.dp,
                ) {
                    Box(Modifier.size(22.dp).background(colors.secondaryLabel.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        AppIcon(AppIcons.Close, null, size = 14.dp, tint = colors.secondaryLabel)
                    }
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
        },
    )
}

/**
 * Equal-width contour tabs for short exclusive choices. Same tonal selection as [MiuixFilters];
 * the native control insets its contour by 5 dp, so it is widened to keep the visible edges on the column.
 */
@Composable
internal fun MiuixContourSegments(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold)
    val contourInset = 5.dp
    val spacing = 8.dp
    val labelWidth = remember(options, labelStyle, density, textMeasurer) {
        with(density) { (options.maxOfOrNull { textMeasurer.measure(it, labelStyle, maxLines = 1).size.width } ?: 0).toDp() + 28.dp }
    }
    val height = with(density) { maxOf(48.dp, labelStyle.fontSize.toDp() + 24.dp) } + contourInset * 2
    BoxWithConstraints(modifier.layout { measurable, constraints ->
        val extra = (contourInset * 2).roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
    }) {
        val equal = (maxWidth - contourInset * 2 - spacing * (options.size - 1)) / options.size
        // Labels that do not fit an equal share keep their width and the row scrolls.
        val tabWidth = maxOf(equal, labelWidth)
        TabRowWithContour(
            tabs = options,
            selectedTabIndex = selectedIndex,
            onTabSelected = onSelect,
            modifier = Modifier.fillMaxWidth(),
            colors = TabRowDefaults.tabRowColors(
                backgroundColor = Color.Transparent,
                contentColor = colors.secondaryLabel,
                selectedBackgroundColor = colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.10f)
                    .compositeOver(colors.background),
                selectedContentColor = colors.accent,
            ),
            minWidth = tabWidth,
            maxWidth = tabWidth,
            height = height,
            cornerRadius = 18.dp,
            itemSpacing = spacing,
        )
    }
}

/** Native contour tabs: one tonal selection, no opaque strip behind the other filters. */
@Composable
internal fun MiuixFilters(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold)
    // Long translations and larger system text scroll instead of clipping labels.
    val minWidth = remember(options, labelStyle, density, textMeasurer) {
        with(density) {
            val labelWidth = options.maxOfOrNull { textMeasurer.measure(it, labelStyle, maxLines = 1).size.width } ?: 0
            maxOf(64.dp, labelWidth.toDp() + 28.dp)
        }
    }
    // Native 0.9.4 adds 5 dp on every side: preserve 48 dp targets and align their
    // visible edges with the same 16 dp content column as the search field.
    val contourInset = 5.dp
    val height = with(density) { maxOf(48.dp, labelStyle.fontSize.toDp() + 24.dp) } + contourInset * 2
    TabRowWithContour(
        tabs = options,
        selectedTabIndex = selectedIndex,
        onTabSelected = onSelect,
        modifier = modifier.padding(horizontal = 16.dp - contourInset),
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = Color.Transparent,
            contentColor = colors.secondaryLabel,
            selectedBackgroundColor = colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.10f)
                .compositeOver(colors.background),
            selectedContentColor = colors.accent,
        ),
        minWidth = minWidth,
        maxWidth = maxOf(180.dp, minWidth),
        height = height,
        cornerRadius = 14.dp,
        itemSpacing = 4.dp,
    )
}
