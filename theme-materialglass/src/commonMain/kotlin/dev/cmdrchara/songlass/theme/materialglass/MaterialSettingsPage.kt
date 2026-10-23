package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Settings owns the neutral canvas and reading width; shared navigation owns system insets. */
@Composable
internal fun MaterialSettingsPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.background), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight()) { content() }
    }
}

/** Plain back affordance and a large, naturally expanding settings title. */
@Composable
internal fun MaterialSettingsPageTitle(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape)
            .clickable(remember { MutableInteractionSource() }, MaterialRipple, role = Role.Button, onClick = onBack)
            .semantics { contentDescription = backLabel }, contentAlignment = Alignment.Center) {
            AppIcon(AppIcons.ArrowBack, null, size = 24.dp, tint = palette.label,
                modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f })
        }
        AppText(title, AppTheme.typography.title1.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
            color = palette.label, modifier = Modifier.weight(1f).semantics { heading() })
    }
}
