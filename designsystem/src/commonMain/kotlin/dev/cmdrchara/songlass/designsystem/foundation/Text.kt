package dev.cmdrchara.songlass.designsystem.foundation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor

/** Single-style text that defaults to the current content color. */
@Composable
fun AppText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    val resolved = if (color.isSpecified) color else LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = resolved, textAlign = textAlign ?: TextAlign.Unspecified)),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Vector icon tinted with the current content color. Pass `null` description for decorative icons. */
@Composable
fun AppIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
) {
    val resolved = if (tint.isSpecified) tint else LocalContentColor.current
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(resolved),
        modifier = modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    )
}
