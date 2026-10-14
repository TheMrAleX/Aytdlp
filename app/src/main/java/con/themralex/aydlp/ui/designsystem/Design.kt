package con.themralex.aydlp.ui.designsystem

import androidx.compose.animation.core.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import con.themralex.aydlp.R

val Accents =
    listOf(
        Color(0xFFC6FA73),
        Color(0xFF82CBFF),
        Color(0xFFBEA1FF),
        Color(0xFFFFA8CB),
        Color(0xFFFFB76B),
        Color(0xFF66E5D4),
        Color(0xFFFFDF70),
        Color(0xFF94ABFF),
    )

data class Tokens(
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
)

val LocalTokens =
    staticCompositionLocalOf {
        Tokens(Color(0xFF0B0D10), Color(0xFF14171C), Color(0xFF1C2027), Color(0xFFF3F5F7), Color(0xFF9DA6B4), Color(0xFF2A3039), Accents[0])
    }

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Manrope =
    FontFamily(
        Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

@Composable fun AyTheme(
    theme: String,
    accent: Int,
    content: @Composable () -> Unit,
) {
    val light =
        theme == "light"
    val t =
        if (light) {
            Tokens(
                Color(0xFFF1F3F0),
                Color.White,
                Color(0xFFE7EBE6),
                Color(0xFF111813),
                Color(0xFF5C675F),
                Color(0xFFD0D7CF),
                Accents[accent.coerceIn(0, 7)].copy(alpha = 1f).let {
                    Color(it.red * 0.43f, it.green * 0.43f, it.blue * 0.43f)
                },
            )
        } else {
            LocalTokens.current.copy(
                background =
                    if (theme ==
                        "amoled"
                    ) {
                        Color.Black
                    } else {
                        Color(0xFF0B0D10)
                    },
                accent = Accents[accent.coerceIn(0, 7)],
            )
        }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
    CompositionLocalProvider(LocalTokens provides t) { content() }
}

@Composable fun Label(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    bold: Boolean = false,
    color: Color = LocalTokens.current.text,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text,
        modifier,
        style =
            TextStyle(
                color = color,
                fontSize = size.sp,
                fontFamily = Manrope,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                lineHeight =
                    (
                        size *
                            1.4
                    ).sp,
                fontFeatureSettings = "tnum",
            ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    Column(
        modifier
            .clip(
                RoundedCornerShape(24.dp),
            ).background(t.surface)
            .border(1.dp, t.border, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable fun AyButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    Box(
        modifier
            .defaultMinSize(
                minHeight = 48.dp,
            ).clip(
                RoundedCornerShape(16.dp),
            ).background(
                if (primary) t.accent.copy(alpha = if (enabled) 1f else 0.35f) else t.elevated,
            ).border(
                1.dp,
                if (primary) t.accent.copy(alpha = 0.3f) else t.border,
                RoundedCornerShape(16.dp),
            ).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, bold = true, color = if (primary) t.background else t.text)
    }
}

@Composable fun Chip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    Box(
        Modifier
            .defaultMinSize(
                minHeight = 48.dp,
            ).clip(
                RoundedCornerShape(14.dp),
            ).background(
                if (selected) t.accent.copy(alpha = 0.14f) else t.surface,
            ).border(
                1.dp,
                if (selected) t.accent else t.border,
                RoundedCornerShape(14.dp),
            ).semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Label(text, color = if (selected) t.accent else t.muted, size = 12, bold = selected)
    }
}

@Composable fun Field(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    lines: Int = 1,
    placeholder: String = "",
) {
    val t = LocalTokens.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Label(hint, size = 12, color = t.muted)
        BasicTextField(
            value,
            onChange,
            Modifier
                .fillMaxWidth()
                .defaultMinSize(
                    minHeight = 52.dp,
                ).clip(RoundedCornerShape(14.dp))
                .background(t.background)
                .border(1.dp, if (focused) t.accent else t.border, RoundedCornerShape(14.dp))
                .onFocusChanged { focused = it.isFocused }
                .padding(16.dp),
            textStyle = TextStyle(color = t.text, fontFamily = Manrope, fontSize = 15.sp),
            singleLine =
                lines == 1,
            maxLines = lines,
            cursorBrush = SolidColor(t.accent),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Label(placeholder, color = t.muted, size = 14)
                    inner()
                }
            },
        )
    }
}

@Composable fun Toggle(
    text: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(text, Modifier.weight(1f))
        Box(
            Modifier
                .size(46.dp, 28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (checked) t.accent else t.border)
                .padding(4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).background(if (checked) t.background else t.muted, RoundedCornerShape(20.dp)))
        }
    }
}

@Composable fun ProgressBar(
    value: Float,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val animated by animateFloatAsState(value.coerceIn(0f, 100f) / 100f, spring(), label = "progress")
    Box(
        modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)).background(t.border).semantics {
            progressBarRangeInfo =
                ProgressBarRangeInfo(value.coerceIn(0f, 100f), 0f..100f)
        },
    ) {
        Box(
            Modifier
                .fillMaxWidth(
                    animated,
                ).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(t.accent.copy(alpha = 0.5f), t.accent))),
        )
    }
}

@Composable fun Section(
    title: String,
    caption: String = "",
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Label(title, size = 24, bold = true)
        if (caption.isNotBlank()) Label(caption, color = LocalTokens.current.muted)
    }
}

// One consistent, dependency-free 24dp line icon family.
enum class AyGlyph { DOWNLOAD, QUEUE, LIBRARY, SETTINGS, LINK, AUDIO, VIDEO, CHECK, CLOSE }

@Composable fun AyIcon(
    glyph: AyGlyph,
    modifier: Modifier = Modifier,
    color: Color = LocalTokens.current.text,
) {
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f

        fun line(
            x: Float,
            y: Float,
            x2: Float,
            y2: Float,
        ) = drawLine(color, Offset(x * u, y * u), Offset(x2 * u, y2 * u), 1.7f * u, StrokeCap.Round)

        fun rect(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
        ) = drawRoundRect(
            color,
            Offset(x * u, y * u),
            androidx.compose.ui.geometry
                .Size(w * u, h * u),
            androidx.compose.ui.geometry
                .CornerRadius(2 * u),
            style = Stroke(1.7f * u),
        )
        when (glyph) {
            AyGlyph.DOWNLOAD -> {
                line(12f, 3f, 12f, 15f)
                line(7f, 10f, 12f, 15f)
                line(17f, 10f, 12f, 15f)
                line(4f, 16f, 4f, 21f)
                line(4f, 21f, 20f, 21f)
                line(20f, 21f, 20f, 16f)
            }
            AyGlyph.QUEUE -> {
                line(9f, 6f, 21f, 6f)
                line(9f, 12f, 21f, 12f)
                line(9f, 18f, 17f, 18f)
                for (y in listOf(6f, 12f, 18f)) drawCircle(color, u, Offset(4 * u, y * u))
            }
            AyGlyph.LIBRARY -> {
                rect(3f, 5f, 6f, 16f)
                rect(13f, 5f, 7f, 16f)
                line(5f, 2f, 18f, 2f)
            }
            AyGlyph.SETTINGS -> {
                for ((y, x) in listOf(5f to 8f, 12f to 16f, 19f to 10f)) {
                    line(3f, y, x - 3f, y)
                    line(x + 3f, y, 21f, y)
                    drawCircle(color, 2.5f * u, Offset(x * u, y * u), style = Stroke(1.7f * u))
                }
            }
            AyGlyph.LINK -> {
                rect(2f, 8f, 9f, 8f)
                rect(13f, 8f, 9f, 8f)
                line(8f, 12f, 16f, 12f)
            }
            AyGlyph.AUDIO -> {
                line(9f, 17f, 9f, 5f)
                line(9f, 5f, 20f, 3f)
                line(20f, 3f, 20f, 15f)
                drawCircle(color, 3 * u, Offset(6 * u, 18 * u), style = Stroke(1.7f * u))
                drawCircle(color, 3 * u, Offset(17 * u, 16 * u), style = Stroke(1.7f * u))
            }
            AyGlyph.VIDEO -> {
                rect(2f, 5f, 20f, 14f)
                line(10f, 9f, 15f, 12f)
                line(15f, 12f, 10f, 15f)
                line(10f, 15f, 10f, 9f)
            }
            AyGlyph.CHECK -> {
                line(5f, 12f, 10f, 17f)
                line(10f, 17f, 20f, 6f)
            }
            AyGlyph.CLOSE -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
        }
    }
}

@Composable fun NavigationBar(
    titles: List<String>,
    selected: Int,
    queueCount: Int,
    onSelect: (Int) -> Unit,
) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth().background(t.surface).padding(horizontal = 8.dp, vertical = 8.dp)) {
        titles.forEachIndexed { index, title ->
            val active = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) t.accent.copy(alpha = .09f) else Color.Transparent)
                    .semantics { this.selected = active }
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box {
                    AyIcon(AyGlyph.entries[index], color = if (active) t.accent else t.muted)
                    if (index == 1 && queueCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-4).dp)
                                .size(8.dp)
                                .background(t.accent, RoundedCornerShape(8.dp)),
                        )
                    }
                }
                Label(title, size = 11, bold = active, color = if (active) t.accent else t.muted, maxLines = 1)
            }
        }
    }
}

@Composable fun LoadingBar(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val transition = rememberInfiniteTransition(label = "analysis")
    val position by transition.animateFloat(initialValue = -0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "indeterminate")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(t.border)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        drawLine(t.accent, Offset(size.width * position, size.height / 2), Offset(size.width * (position + .4f), size.height / 2), size.height, StrokeCap.Round)
    }
}
