package con.themralex.aydlp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import con.themralex.aydlp.domain.DownloadState
import con.themralex.aydlp.domain.Profile
import con.themralex.aydlp.ui.designsystem.*

@Composable internal fun AyIcon(
    name: String,
    color: Color = LocalTokens.current.text,
) {
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24f

        fun line(
            x: Float,
            y: Float,
            a: Float,
            b: Float,
        ) = drawLine(color, Offset(x * unit, y * unit), Offset(a * unit, b * unit), 1.8f * unit, StrokeCap.Round)

        fun box(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
        ) = drawRoundRect(
            color,
            Offset(x * unit, y * unit),
            Size(w * unit, h * unit),
            androidx.compose.ui.geometry
                .CornerRadius(2f * unit),
            style = Stroke(1.8f * unit),
        )
        when (name) {
            "download", "import" -> {
                line(12f, 3f, 12f, 15f)
                line(7f, 10f, 12f, 15f)
                line(17f, 10f, 12f, 15f)
                line(4f, 17f, 4f, 21f)
                line(4f, 21f, 20f, 21f)
                line(20f, 21f, 20f, 17f)
            }
            "queue" -> {
                for (y in listOf(6f, 12f, 18f)) {
                    line(4f, y, 5f, y)
                    line(9f, y, 20f, y)
                }
            }
            "library" -> {
                box(3f, 3f, 7f, 7f)
                box(14f, 3f, 7f, 7f)
                box(3f, 14f, 7f, 7f)
                box(14f, 14f, 7f, 7f)
            }
            "settings" -> {
                line(4f, 6f, 20f, 6f)
                line(4f, 12f, 20f, 12f)
                line(4f, 18f, 20f, 18f)
                box(7f, 4f, 3f, 4f)
                box(14f, 10f, 3f, 4f)
                box(8f, 16f, 3f, 4f)
            }
            "pause" -> {
                line(8f, 5f, 8f, 19f)
                line(16f, 5f, 16f, 19f)
            }
            "play" -> {
                val p =
                    Path().apply {
                        moveTo(8f * unit, 4f * unit)
                        lineTo(20f * unit, 12f * unit)
                        lineTo(8f * unit, 20f * unit)
                        close()
                    }
                drawPath(p, color, style = Stroke(1.8f * unit))
            }
            "paste" -> {
                box(5f, 5f, 14f, 16f)
                box(9f, 2f, 6f, 5f)
                line(9f, 12f, 15f, 12f)
                line(9f, 16f, 15f, 16f)
            }
            "folder" -> {
                val p =
                    Path().apply {
                        moveTo(3f * unit, 6f * unit)
                        lineTo(10f * unit, 6f * unit)
                        lineTo(12f * unit, 9f * unit)
                        lineTo(21f * unit, 9f * unit)
                        lineTo(21f * unit, 20f * unit)
                        lineTo(3f * unit, 20f * unit)
                        close()
                    }
                drawPath(p, color, style = Stroke(1.8f * unit))
            }
            "chevron" -> {
                line(9f, 6f, 15f, 12f)
                line(15f, 12f, 9f, 18f)
            }
            else -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
        }
    }
}

@Composable internal fun IconAction(
    icon: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AyIcon(icon, LocalTokens.current.muted)
    }
}

@Composable internal fun TextAction(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Label(text, color = LocalTokens.current.accent, size = 12, bold = true)
    }
}

@Composable internal fun AyFab(
    icon: String,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    Row(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(t.accent.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AyIcon(icon, t.background)
        Label(text, bold = true, color = t.background)
    }
}

@Composable internal fun NavigationDock(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val t = LocalTokens.current
    val icons = listOf("download", "queue", "library", "settings")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(t.surface)
            .selectableGroup()
            .padding(6.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            val active = index == selected
            val background by animateColorAsState(if (active) t.accent.copy(alpha = 0.14f) else Color.Transparent, label = "tab")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(background)
                    .selectable(selected = active, role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AyIcon(icons[index], if (active) t.accent else t.muted)
                Label(title, size = 10, bold = active, color = if (active) t.accent else t.muted, maxLines = 1)
            }
        }
    }
}

@Composable internal fun AnalysisSkeleton(
    cancel: String,
    onCancel: () -> Unit,
) {
    val t = LocalTokens.current
    val animation = rememberInfiniteTransition(label = "metadata")
    val opacity by animation.animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeleton")
    Panel(Modifier.fillMaxWidth().semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(t.border.copy(alpha = opacity)),
        )
        Box(Modifier.fillMaxWidth(0.85f).height(20.dp).background(t.border.copy(alpha = opacity), RoundedCornerShape(6.dp)))
        Box(Modifier.fillMaxWidth(0.55f).height(14.dp).background(t.border.copy(alpha = opacity), RoundedCornerShape(6.dp)))
        TextAction(cancel, onCancel)
    }
}

internal fun profileSummary(profile: Profile): String = if (profile.audio) "Audio · ${profile.bitrate} kbps · ${profile.container.uppercase()}" else "${profile.resolution}p · ${profile.container.uppercase()}${if (profile.formatId.isNotBlank()) " · Manual" else ""}"

internal fun downloadStateLabel(
    state: DownloadState,
    es: Boolean,
): String =
    when (state) {
        DownloadState.QUEUED -> if (es) "En cola" else "Queued"
        DownloadState.SCHEDULED -> if (es) "Programada" else "Scheduled"
        DownloadState.ANALYZING -> if (es) "Preparando" else "Preparing"
        DownloadState.DOWNLOADING -> if (es) "Descargando" else "Downloading"
        DownloadState.PAUSED -> if (es) "Pausada" else "Paused"
        DownloadState.POST_PROCESSING -> if (es) "Procesando" else "Processing"
        DownloadState.MOVING -> if (es) "Guardando" else "Saving"
        DownloadState.COMPLETED -> if (es) "Completada" else "Completed"
        DownloadState.FAILED -> if (es) "Error" else "Failed"
        DownloadState.CANCELED -> if (es) "Cancelada" else "Canceled"
    }
