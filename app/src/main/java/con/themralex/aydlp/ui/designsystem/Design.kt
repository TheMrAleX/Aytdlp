package con.themralex.aydlp.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
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
        Tokens(Color(0xFF080A0C), Color(0xFF12171A), Color(0xFF1B2328), Color(0xFFF4F7F5), Color(0xFF919EA5), Color(0xFF2A3439), Accents[0])
    }
val Manrope = FontFamily(Font(R.font.manrope, FontWeight.Normal), Font(R.font.manrope, FontWeight.Bold))

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
                        Color(0xFF080A0C)
                    },
                accent = Accents[accent.coerceIn(0, 7)],
            )
        }
    ;CompositionLocalProvider(LocalTokens provides t) { content() }
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
            ).background(
                Brush.verticalGradient(listOf(t.elevated.copy(alpha = 0.8f), t.surface)),
            ).border(1.dp, t.border, RoundedCornerShape(24.dp))
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
            ).clickable(role = Role.Button, onClick = onClick)
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
) {
    val t = LocalTokens.current
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
                .border(1.dp, t.border, RoundedCornerShape(14.dp))
                .padding(14.dp),
            textStyle = TextStyle(color = t.text, fontFamily = Manrope, fontSize = 15.sp),
            singleLine =
                lines == 1,
            maxLines = lines,
            cursorBrush = SolidColor(t.accent),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Label(hint, color = t.muted, size = 14)
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
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).clickable(role = Role.Switch) { onChange(!checked) }.semantics {
            stateDescription =
                if (checked) "On" else "Off"
        },
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
