@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package con.themralex.aydlp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import con.themralex.aydlp.data.*
import con.themralex.aydlp.domain.*
import con.themralex.aydlp.ui.designsystem.*
import java.text.SimpleDateFormat
import java.util.Locale

@Composable fun AytdlpScreen(
    vm: MainViewModel,
    enqueue: (String, Long) -> Unit,
    startQueue: () -> Unit,
    importLinks: () -> Unit,
    folder: () -> Unit,
    importCookies: () -> Unit,
    exportBackup: () -> Unit,
    restoreBackup: () -> Unit,
    copy: (String) -> Unit,
    clipboard: () -> String,
    open: (DownloadRow) -> Unit,
    share: (DownloadRow) -> Unit,
    language: (String) -> Unit,
    battery: () -> Unit,
) {
    val context = LocalContext.current
    val hidden by vm.hiddenMedia.collectAsStateWithLifecycle()
    var editingMedia by remember { mutableStateOf<DownloadRow?>(null) }
    var editedName by remember { mutableStateOf("") }
    var editedTag by remember { mutableStateOf("") }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rows by vm.downloads.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val analysisError by vm.analysisError.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    var showProfile by remember { mutableStateOf(false) }
    var editingDownload by remember { mutableStateOf<String?>(null) }
    var schedule by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    var showFormats by rememberSaveable { mutableStateOf(false) }
    var libraryFilter by rememberSaveable { mutableIntStateOf(0) }
    val es = LocalConfiguration.current.locales[0].language == "es"

    fun tr(
        en: String,
        sp: String,
    ) = if (es) sp else en
    BackHandler(tab != 0) { tab = 0 }
    AyTheme(settings.theme, settings.accent) {
        val t = LocalTokens.current
        Box(Modifier.fillMaxSize().background(t.background), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).background(t.accent, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        AyIcon(AyGlyph.DOWNLOAD, color = t.background)
                    }
                    Column(Modifier.weight(1f)) {
                        Label("Aytdlp", size = 21, bold = true)
                        Label(tr("YOUR OFFLINE SPACE", "TU ESPACIO OFFLINE"), size = 9, color = t.muted)
                    }
                    val status =
                        when (health?.ready) {
                            true -> tr("Ready", "Listo")
                            false -> tr("Check engine", "Revisar motor")
                            null -> tr("Checking…", "Comprobando…")
                        }
                    Chip(status, health?.ready == true) { tab = 3 }
                }
                if (message.isNotBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .background(t.elevated, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Label(message, Modifier.weight(1f), size = 12, maxLines = 4)
                        Chip(tr("Close", "Cerrar")) {
                            vm.message.value =
                                ""
                        }
                    }
                }
                val tabs = listOf(tr("Download", "Descargar"), tr("Queue", "Cola"), tr("Library", "Biblioteca"), tr("Settings", "Ajustes"))
                when (tab) {
                    0 -> {
                        LazyColumn(
                            Modifier.weight(1f),
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Label(
                                        tr("From a link.\nTo your library.", "De un enlace.\nA tu biblioteca."),
                                        size = 32,
                                        bold = true,
                                    )
                                    Label(
                                        tr("Video or just the audio. You choose.", "Vídeo o solo el audio. Tú eliges."),
                                        color = t.muted,
                                    )
                                }
                            }
                            item {
                                Panel(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Chip(tr("Video", "Vídeo"), !profile.audio) { vm.edit { it.copy(audio = false, container = "mp4", formatId = "") } }
                                        Chip(tr("Audio", "Audio"), profile.audio) { vm.edit { it.copy(audio = true, container = "mp3", formatId = "") } }
                                        Spacer(Modifier.weight(1f))
                                        AyIcon(if (profile.audio) AyGlyph.AUDIO else AyGlyph.VIDEO, Modifier.align(Alignment.CenterVertically), color = t.muted)
                                    }
                                    Field(input, { input = it }, tr("Link or YouTube search", "Enlace o búsqueda en YouTube"), lines = 2, placeholder = "https://…")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AyButton(tr("Paste", "Pegar"), primary = false) { input = clipboard() }
                                        AyButton(if (busy) tr("Analyzing…", "Analizando…") else tr("Analyze link", "Analizar enlace"), Modifier.weight(1f), enabled = input.isNotBlank() && !busy) { vm.analyze(input) }
                                    }
                                    if (busy) {
                                        LoadingBar()
                                        Label(tr("Finding the title, cover and available qualities.", "Buscando título, carátula y calidades disponibles."), size = 12, color = t.muted)
                                        AyButton(tr("Cancel analysis", "Cancelar análisis"), primary = false) { vm.cancelAnalysis() }
                                    }
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AyButton(tr("Import .txt", "Importar .txt"), Modifier.weight(1f), primary = false, onClick = importLinks)
                                    AyButton(tr("Quality & format", "Calidad y formato"), Modifier.weight(1f), primary = false) { showProfile = true }
                                }
                                Spacer(Modifier.height(10.dp))
                                Label(if (profile.audio) "${profile.container.uppercase()} · ${profile.bitrate} kbps" else "${profile.resolution}p · ${profile.container.uppercase()}", size = 12, color = t.muted)
                            }
                            if (analysisError.isNotBlank()) {
                                item {
                                    Panel(Modifier.fillMaxWidth()) {
                                        Label(tr("We couldn't read this link", "No pudimos analizar el enlace"), bold = true, color = if (settings.theme == "light") Color(0xFFAD2929) else Color(0xFFFFA09B))
                                        Label(tr("Retry or copy the full report to investigate the cause.", "Reintenta o copia el informe completo para revisar la causa."), color = t.muted, size = 13)
                                        Label(analysisError, size = 12, maxLines = 4)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AyButton(tr("Retry", "Reintentar"), enabled = !busy) { vm.analyze(input) }
                                            AyButton(tr("Copy report", "Copiar informe"), primary = false) { copy(vm.diagnostic()) }
                                            AyButton(tr("Diagnostics", "Diagnóstico"), primary = false) { tab = 3 }
                                        }
                                    }
                                }
                            }
                            if (info != null) {
                                val media = info!!
                                item {
                                    Panel {
                                        if (media.thumbnail.isNotBlank()) {
                                            AsyncImage(
                                                media.thumbnail,
                                                media.title,
                                                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
                                                contentScale = ContentScale.Crop,
                                            )
                                        }
                                        Label(media.title, size = 21, bold = true)
                                        Label("${media.channel} · ${duration(media.duration)}", color = t.muted)
                                        if (media.live.isNotBlank() &&
                                            media.live != "not_live"
                                        ) {
                                            Label("${tr("Live / premiere status","Estado directo / estreno")}: ${media.live}", color = t.accent)
                                        }
                                        if (media.entries.isEmpty()) {
                                            Label(tr("Profile", "Perfil"), size = 11, color = t.muted)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Chip(profile.name, true) {
                                                    showProfile =
                                                        true
                                                }
                                            }
                                            AyButton(tr("Add to queue", "Añadir a cola"), Modifier.fillMaxWidth()) { enqueue(media.url, 0) }
                                            Chip(tr("Schedule for later", "Programar para después"), showSchedule) { showSchedule = !showSchedule }
                                            if (showSchedule) {
                                                Field(schedule, {
                                                    schedule =
                                                        it
                                                }, tr("Schedule: yyyy-MM-dd HH:mm (optional)", "Programar: yyyy-MM-dd HH:mm (opcional)"))
                                                if (schedule.isNotBlank()) {
                                                    AyButton(tr("Schedule download", "Programar descarga"), primary = false) {
                                                        runCatching {
                                                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
                                                                .apply {
                                                                    isLenient =
                                                                        false
                                                                }.parse(schedule)!!
                                                                .time
                                                        }.onSuccess { enqueue(media.url, it) }.onFailure {
                                                            vm.message.value =
                                                                tr("Invalid date", "Fecha inválida")
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Label("${media.entries.size} ${tr("items · choose below","elementos · selecciona abajo")}")
                                        }
                                    }
                                }
                                if (media.entries.isNotEmpty()) {
                                    item { AyButton(tr("Queue all", "Añadir todos")) { media.entries.forEach { enqueue(it.url, 0) } } }
                                    items(media.entries, key = {
                                        it.id +
                                            it.url
                                    }) { child ->
                                        Panel {
                                            Label(child.title, bold = true)
                                            AyButton(tr("Add this item", "Añadir elemento"), primary = false) { enqueue(child.url, 0) }
                                        }
                                    }
                                } else {
                                    item { AyButton(tr("Advanced formats", "Formatos avanzados") + " · ${media.formats.size}", Modifier.fillMaxWidth(), primary = false) { showFormats = !showFormats } }
                                    if (showFormats) {
                                        item {
                                            Section(
                                                tr("Available formats", "Formatos disponibles"),
                                                tr(
                                                    "All formats are downloadable. Playback depends on your device.",
                                                    "Puedes descargar todos. La reproducción depende del dispositivo.",
                                                ),
                                            )
                                        }
                                        items(media.formats.sortedByDescending { it.height }, key = { it.id }) { f ->
                                            Row(
                                                Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(14.dp)).padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Label(f.label, bold = true, size = 13)
                                                    Label(
                                                        if (f.size >
                                                            0
                                                        ) {
                                                            bytes(f.size)
                                                        } else {
                                                            tr("Size unavailable", "Tamaño no disponible")
                                                        },
                                                        size = 11,
                                                        color = t.muted,
                                                    )
                                                }
                                                Chip(
                                                    if (profile.formatId ==
                                                        f.id
                                                    ) {
                                                        "✓"
                                                    } else {
                                                        "+"
                                                    },
                                                ) { vm.edit { it.copy(formatId = if (f.videoCodec != "none" && f.audioCodec == "none") "${f.id}+ba/b" else f.id, name = f.label, audio = f.videoCodec == "none", container = if (f.videoCodec == "none") "m4a" else "mp4") } }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Label(
                                    tr("No account. No ads. No tracking.", "Sin cuenta. Sin anuncios. Sin rastreo."),
                                    size = 12,
                                    color = t.muted,
                                )
                            }
                        }
                    }

                    1 -> {
                        Column(Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Section(
                                tr("Your queue", "Tu cola"),
                                "${rows.count {it.state != DownloadState.COMPLETED && it.state != DownloadState.CANCELED}} ${tr(
                                    "transfers",
                                    "transferencias",
                                )}",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AyButton(tr("Start / resume all", "Iniciar / reanudar"), Modifier.weight(1f)) { startQueue() }
                                AyButton(tr("Pause all", "Pausar todo"), primary = false) { vm.action { vm.queue.pauseAll() } }
                            }
                            if (rows.none {
                                    it.state != DownloadState.COMPLETED && it.state != DownloadState.CANCELED
                                }
                            ) {
                                Empty(
                                    tr("A little room for something great.", "Hay espacio para algo grande."),
                                    tr("Add your first link to get started.", "Añade tu primer enlace para empezar."),
                                )
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                items(
                                    rows.filter {
                                        it.state !=
                                            DownloadState.COMPLETED &&
                                            it.state != DownloadState.CANCELED
                                    },
                                    key = { it.id },
                                ) { row ->
                                    DownloadCard(row, es, onPause = { vm.action { vm.queue.pause(row.id) } }, onResume = {
                                        vm.action {
                                            vm.queue.resume(row.id)
                                            con.themralex.aydlp.core.Transfers
                                                .start(context, true)
                                        }
                                    }, onCancel = { vm.action { vm.queue.cancel(row.id) } }, onEdit = {
                                        editingDownload = row.id
                                        vm.profile.value = ProfileJson.decode(row.profile)
                                        showProfile = true
                                    })
                                }
                            }
                        }
                    }

                    2 -> {
                        Column(Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Section(tr("Made yours", "Tu biblioteca"), tr("Your collection, on your terms.", "Tu colección, a tu manera."))
                            Field(query, {
                                query =
                                    it
                            }, tr("Search title or tags", "Buscar título o etiquetas"))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(tr("All", "Todo"), tr("Video", "Vídeo"), "Audio").forEachIndexed { index, name -> Chip(name, libraryFilter == index) { libraryFilter = index } }
                            }
                            if (hidden.isNotEmpty()) AyButton(tr("Undo delete", "Deshacer borrado"), primary = false) { vm.undoDelete() }
                            if (rows.none {
                                    it.state == DownloadState.COMPLETED
                                }
                            ) {
                                Empty(
                                    tr("Keep your first favorite.", "Guarda tu primer favorito."),
                                    tr("Finished downloads appear here.", "Aquí aparecen las descargas completadas."),
                                )
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                items(
                                    rows
                                        .filter {
                                            it.state ==
                                                DownloadState.COMPLETED && it.id !in hidden &&
                                                (libraryFilter == 0 || ProfileJson.decode(it.profile).audio == (libraryFilter == 2)) &&
                                                (it.title.contains(query, true) || it.tag.contains(query, true))
                                        }.sortedByDescending { it.created },
                                    key = { it.id },
                                ) { row ->
                                    Panel {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            AsyncImage(row.thumbnail, row.title, Modifier.size(88.dp, 64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                            Column(Modifier.weight(1f)) {
                                                Label(row.title, bold = true, maxLines = 2)
                                                Label(bytes(row.bytes), size = 12, color = t.muted)
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AyButton(tr("Play", "Reproducir"), Modifier.weight(1f)) { open(row) }
                                            AyButton(tr("Share", "Compartir"), primary = false) { share(row) }
                                            Chip(tr("More", "Más")) {
                                                editingMedia = row
                                                editedName = row.title
                                                editedTag = row.tag
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        SettingsScreen(vm, es, folder, importCookies, exportBackup, restoreBackup, copy, language, battery, Modifier.weight(1f))
                    }
                }
                NavigationBar(tabs, tab, rows.count { it.state in StateMachine.active || it.state == DownloadState.QUEUED }) { tab = it }
            }
        }
        editingMedia?.let { row ->
            Dialog(onDismissRequest = { editingMedia = null }) {
                Panel {
                    Label(tr("Manage file", "Gestionar archivo"), size = 24, bold = true)
                    Field(editedName, { editedName = it }, tr("Filename", "Nombre del archivo"))
                    AyButton(tr("Rename", "Renombrar"), primary = false) {
                        vm.action {
                            vm.library.rename(row, editedName)
                            editingMedia = null
                        }
                    }
                    Field(editedTag, { editedTag = it }, tr("Tags", "Etiquetas"))
                    AyButton(tr("Save tags", "Guardar etiquetas"), primary = false) {
                        vm.action {
                            vm.library.tag(row, editedTag)
                            editingMedia = null
                        }
                    }
                    AyButton(tr("Open with another app", "Abrir con otra app"), primary = false) {
                        runCatching {
                            context.startActivity(
                                android.content
                                    .Intent(android.content.Intent.ACTION_VIEW)
                                    .setDataAndType(android.net.Uri.parse(row.destination), context.contentResolver.getType(android.net.Uri.parse(row.destination)) ?: "video/*")
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                        }.onFailure { vm.message.value = it.message.orEmpty() }
                    }
                    AyButton(tr("Delete · 10 second undo", "Borrar · deshacer durante 10 s"), primary = false) {
                        vm.deleteLater(row)
                        editingMedia = null
                    }
                    AyButton(tr("Close", "Cerrar")) { editingMedia = null }
                }
            }
        }
        if (showProfile) {
            ProfileSheet(vm, es, onSave = {
                val id = editingDownload
                if (id != null) vm.action { vm.queue.dao.profile(id, ProfileJson.encode(vm.profile.value)) } else vm.saveProfile()
                editingDownload = null
            }) { showProfile = false }
        }
        if (!settings.onboarded) {
            Dialog(onDismissRequest = {
            }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().background(t.background).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Label("A", size = 80, bold = true, color = t.accent)
                        Label(tr("A place for\nyour favorites.", "Un lugar para\ntus favoritos."), size = 38, bold = true)
                        Label(
                            tr(
                                "Save video and audio with a private, persistent queue. Your library stays yours.",
                                "Guarda vídeo y audio con una cola privada y persistente. Tu biblioteca te pertenece.",
                            ),
                            size = 17,
                            color = t.muted,
                        )
                        Label(
                            androidx.compose.ui.res
                                .stringResource(con.themralex.aydlp.R.string.legal),
                            size = 12,
                            color = t.muted,
                        )
                        AyButton(tr("Let's begin", "Comenzar"), Modifier.fillMaxWidth()) { vm.boolean("onboarded", true) }
                    }
                }
            }
        }
    }
}

@Composable private fun Empty(
    title: String,
    caption: String,
) {
    Panel(Modifier.fillMaxWidth()) {
        Box(Modifier.size(64.dp).background(LocalTokens.current.accent.copy(alpha = .08f), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            AyIcon(AyGlyph.LIBRARY, Modifier.size(30.dp), color = LocalTokens.current.accent)
        }
        Label(title, size = 20, bold = true)
        Label(caption, color = LocalTokens.current.muted)
    }
}

@Composable private fun DownloadCard(
    row: DownloadRow,
    es: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
) {
    val t = LocalTokens.current
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(row.thumbnail, row.title, Modifier.size(88.dp, 64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Label(row.title.ifBlank { row.url }, bold = true, maxLines = 2)
                Label(
                    stateLabel(row.state, es),
                    size = 11,
                    color =
                        if (row.state ==
                            DownloadState.FAILED
                        ) {
                            Color(0xFFFF8989)
                        } else {
                            t.accent
                        },
                )
            }
        }
        if (row.state !in StateMachine.active) Chip(if (es) "Editar perfil" else "Edit profile", onClick = onEdit)
        if (row.state in setOf(DownloadState.ANALYZING, DownloadState.POST_PROCESSING) || (row.state == DownloadState.DOWNLOADING && row.total <= 0)) LoadingBar() else ProgressBar(row.progress)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(if (row.total > 0) "${row.progress.toInt()}% · ${bytes(row.bytes)}" else bytes(row.bytes), size = 12)
            if (row.state == DownloadState.DOWNLOADING) Label("${bytes(row.speed.toLong())}/s" + if (row.eta > 0) " · ${duration(row.eta)}" else "", size = 12, color = t.muted)
        }
        if (row.fragments >
            0
        ) {
            Label("${row.fragment} / ${row.fragments} ${if (es) "fragmentos" else "fragments"}", size = 11, color = t.muted)
        }
        if (row.failure.isNotBlank()) Label(row.failure, size = 12, color = t.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.state ==
                DownloadState.PAUSED ||
                row.state == DownloadState.FAILED
            ) {
                AyButton(if (es) "Reanudar" else "Resume", Modifier.weight(1f), onClick = onResume)
            } else {
                AyButton(if (es) "Pausar" else "Pause", Modifier.weight(1f), primary = false, onClick = onPause)
            }
            AyButton(if (es) "Cancelar" else "Cancel", primary = false, onClick = onCancel)
        }
    }
}

@Composable private fun ProfileSheet(
    vm: MainViewModel,
    es: Boolean,
    onSave: () -> Unit = { vm.saveProfile() },
    onClose: () -> Unit,
) {
    val p by vm.profile.collectAsStateWithLifecycle()
    val saved by vm.profiles.collectAsStateWithLifecycle()
    val t = LocalTokens.current

    fun tr(
        en: String,
        sp: String,
    ) = if (es) sp else en
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(top = 60.dp), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    ).background(
                        t.surface,
                    ).verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(tr("Make it yours", "A tu medida"), Modifier.weight(1f), size = 25, bold = true)
                    Chip(tr("Close", "Cerrar"), onClick = onClose)
                }
                if (saved.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        saved.forEach { row ->
                            Chip(row.name) {
                                vm.profile.value =
                                    ProfileJson.decode(row.json)
                            }
                        }
                    }
                }
                Field(p.name, { v -> vm.edit { it.copy(name = v) } }, tr("Profile name", "Nombre del perfil"))
                Toggle(
                    tr("Audio only", "Solo audio"),
                    p.audio,
                ) { v -> vm.edit { it.copy(audio = v, container = if (v) "mp3" else "mp4", formatId = "") } }
                if (!p.audio) {
                    Label(tr("Maximum resolution", "Resolución máxima"), color = t.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(360, 480, 720, 1080, 1440, 2160, 4320).forEach { r ->
                            Chip(
                                "${r}p",
                                p.resolution == r,
                            ) { vm.edit { it.copy(resolution = r, formatId = "") } }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("" to "Auto", "avc1" to "H.264", "vp9" to "VP9", "av01" to "AV1").forEach { (c, n) ->
                            Chip(n, p.codec == c) { vm.edit { it.copy(codec = c, formatId = "") } }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (p.audio) listOf("mp3", "m4a", "opus", "flac") else listOf("mp4", "mkv", "webm")).forEach { c ->
                        Chip(
                            c.uppercase(),
                            p.container == c,
                        ) { vm.edit { it.copy(container = c) } }
                    }
                }
                if (p.audio) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(128, 192, 256, 320).forEach { b ->
                            Chip("$b kbps", p.bitrate == b) { vm.edit { it.copy(bitrate = b) } }
                        }
                    }
                }
                Field(
                    p.subtitles,
                    { v -> vm.edit { it.copy(subtitles = v) } },
                    tr("Subtitle languages (es,en)", "Idiomas de subtítulos (es,en)"),
                )
                Toggle(tr("Auto-generated subtitles", "Subtítulos automáticos"), p.autoSubs) { v -> vm.edit { it.copy(autoSubs = v) } }
                Toggle(tr("Embed subtitles", "Incrustar subtítulos"), p.embedSubs) { v -> vm.edit { it.copy(embedSubs = v) } }
                Toggle(tr("Embed metadata", "Incrustar metadatos"), p.metadata) { v -> vm.edit { it.copy(metadata = v) } }
                Toggle(tr("Use thumbnail as cover", "Miniatura como carátula"), p.cover) { v -> vm.edit { it.copy(cover = v) } }
                Toggle(tr("Include chapters", "Incluir capítulos"), p.chapters) { v -> vm.edit { it.copy(chapters = v) } }
                Toggle(
                    tr("Live: capture from start if available", "Directos: desde el inicio si está disponible"),
                    p.liveFromStart,
                ) { v ->
                    vm.edit {
                        it.copy(liveFromStart = v)
                    }
                }
                Label("SponsorBlock", color = t.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "" to tr("Off", "No"),
                        "mark" to tr("Mark", "Marcar"),
                        "remove" to tr("Remove", "Omitir"),
                    ).forEach { (value, label) ->
                        Chip(label, p.sponsor == value) { vm.edit { it.copy(sponsor = value) } }
                    }
                }
                Field(p.trim, { v -> vm.edit { it.copy(trim = v) } }, tr("Trim: 00:10-01:20", "Recorte: 00:10-01:20"))
                Field(p.custom, { v ->
                    vm.edit {
                        it.copy(custom = v)
                    }
                }, tr("Advanced arguments (validated)", "Argumentos avanzados (validados)"), lines = 4)
                AyButton(tr("Save profile", "Guardar perfil"), Modifier.fillMaxWidth()) {
                    runCatching { AdvancedArguments.parse(p.custom) }
                        .onSuccess {
                            onSave()
                            onClose()
                        }.onFailure {
                            vm.message.value =
                                it.message.orEmpty()
                        }
                }
            }
        }
    }
}

@Composable private fun SettingsScreen(
    vm: MainViewModel,
    es: Boolean,
    folder: () -> Unit,
    importCookies: () -> Unit,
    exportBackup: () -> Unit,
    restoreBackup: () -> Unit,
    copy: (String) -> Unit,
    language: (String) -> Unit,
    battery: () -> Unit,
    modifier: Modifier,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val h by vm.health.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }

    fun tr(
        en: String,
        sp: String,
    ) = if (es) sp else en

    fun matches(text: String) = search.isBlank() || text.contains(search, true)
    LazyColumn(modifier, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Section(tr("Every detail, yours.", "Cada detalle, tuyo."))
            Field(search, { search = it }, tr("Search settings", "Buscar ajustes"))
        }
        if (matches("Appearance Tema acento idioma")) {
            item {
                Panel {
                    Section(tr("Appearance", "Apariencia"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("dark", "light", "amoled").forEach { theme ->
                            Chip(
                                when (theme) {
                                    "dark" -> tr("Dark", "Oscuro")
                                    "light" -> tr("Light", "Claro")
                                    else -> "AMOLED"
                                },
                                s.theme == theme,
                            ) { vm.text("theme", theme) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Accents.forEachIndexed { index, color ->
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color)
                                    .border(
                                        if (s.accent ==
                                            index
                                        ) {
                                            3.dp
                                        } else {
                                            0.dp
                                        },
                                        t.text,
                                        RoundedCornerShape(16.dp),
                                    ).semantics { contentDescription = tr("Accent", "Acento") + " ${index + 1}" }
                                    .clickable { vm.number("accent", index) },
                            ) {}
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(
                            "Español",
                            s.locale == "es",
                        ) { language("es") }
                        Chip("English", s.locale == "en") { language("en") }
                    }
                }
            }
        }
        if (matches("Queue Cola red wifi concurrencia")) {
            item {
                Panel {
                    Section(tr("Transfers", "Transferencias"))
                    Label(tr("Simultaneous downloads", "Descargas simultáneas"), color = t.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..6).forEach { n -> Chip(n.toString(), s.concurrency == n) { vm.number("concurrency", n) } }
                    }
                    Label(tr("Concurrent fragments", "Fragmentos simultáneos"), color = t.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 4, 8, 16).forEach { n ->
                            Chip(
                                n.toString(),
                                s.fragments == n,
                            ) { vm.number("fragments", n) }
                        }
                    }
                    Toggle(tr("Unmetered networks only", "Solo redes no medidas"), s.wifi) { vm.boolean("wifi", it) }
                    Toggle(tr("Only while charging", "Solo cargando"), s.charging) { vm.boolean("charging", it) }
                    SettingField(vm, "rate", s.rate, tr("Rate limit (e.g. 2M)", "Límite de velocidad (ej. 2M)"))
                    AyButton(
                        tr("Battery optimization help", "Ayuda de optimización de batería"),
                        primary = false,
                        onClick = battery,
                    )
                    Label(
                        "${android.os.Build.MANUFACTURER}: " +
                            tr(
                                "If transfers stop, allow background activity in system battery settings. No exemption is required to use the app.",
                                "Si se detienen las descargas, permite actividad en segundo plano en los ajustes de batería. No se exige una excepción para usar la app.",
                            ),
                        size = 12,
                        color = t.muted,
                    )
                }
            }
        }
        if (matches("Storage Almacenamiento carpeta plantilla")) {
            item {
                Panel {
                    Section(tr("Storage", "Almacenamiento"))
                    Label(
                        if (s.destination.isBlank()) {
                            tr(
                                "MediaStore (Android 10+) / private library",
                                "MediaStore (Android 10+) / biblioteca privada",
                            )
                        } else {
                            tr("Selected SAF folder", "Carpeta SAF seleccionada")
                        },
                        color = t.muted,
                    )
                    AyButton(tr("Choose folder / SD card", "Elegir carpeta / tarjeta SD"), primary = false, onClick = folder)
                    SettingField(vm, "template", s.template, tr("Filename template", "Plantilla de nombre"))
                    AyButton(tr("Use default destination", "Usar destino predeterminado"), primary = false) { vm.text("destination", "") }
                }
            }
        }
        if (matches("Engine Motor YouTube proxy user agent clientes cookies")) {
            item {
                Panel {
                    Section(tr("Engine", "Motor"))
                    SettingField(vm, "clients", s.clients, tr("YouTube client chain", "Cadena de clientes YouTube"))
                    SettingField(vm, "proxy", s.proxy, "Proxy")
                    SettingField(vm, "userAgent", s.userAgent, "User-Agent")
                    Label(
                        tr(
                            "Cookies grant access to your session. Import only your own cookies. They are encrypted and never exported.",
                            "Las cookies dan acceso a tu sesión. Importa solo las tuyas. Se cifran y nunca se exportan.",
                        ),
                        size = 12,
                        color = t.muted,
                    )
                    AyButton(tr("Import cookies.txt", "Importar cookies.txt"), primary = false, onClick = importCookies)
                    AyButton(tr("Delete cookies", "Eliminar cookies"), primary = false) {
                        vm.action {
                            vm.cookies.clear()
                            vm.message.value =
                                tr("Cookies deleted", "Cookies eliminadas")
                        }
                    }
                }
            }
        }
        if (matches("Updates Actualización diagnóstico")) {
            item {
                Panel {
                    Section(tr("Maintenance", "Mantenimiento"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("stable", "nightly", "master").forEach { channel ->
                            Chip(
                                channel,
                                s.channel == channel,
                            ) { vm.text("channel", channel) }
                        }
                    }
                    Toggle(tr("Check daily on unmetered network", "Comprobar cada día en red no medida"), s.autoUpdate) { value ->
                        vm.boolean("autoUpdate", value)
                        con.themralex.aydlp.core.Transfers
                            .maintenance(context, value)
                    }
                    AyButton(tr("Verify update and smoke-test", "Verificar actualización y probar"), Modifier.fillMaxWidth()) {
                        vm.action {
                            vm.message.value =
                                tr("Checking official release…", "Comprobando release oficial…")
                            vm.message.value = vm.updater.update()
                            vm.health.value =
                                vm.engine.health()
                        }
                    }
                    if (s.updateReport.isNotBlank()) Label(s.updateReport, size = 12, color = t.muted, maxLines = 8)
                    AyButton(tr("Rollback engine", "Revertir motor"), primary = false) {
                        vm.action {
                            vm.updater.rollback()
                            vm.health.value =
                                vm.engine.health()
                        }
                    }
                    Label(
                        "yt-dlp: ${h?.version ?: "…"}\n${h?.runtime.orEmpty()}\n${h?.solver.orEmpty()}\n${h?.ffmpeg.orEmpty()}",
                        size = 12,
                        color = t.muted,
                    )
                    Label(h?.detail.orEmpty(), size = 12, color = t.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AyButton(tr("Run health check", "Comprobar motor"), Modifier.weight(1f), primary = false) {
                            vm.action {
                                vm.health.value =
                                    vm.engine.health()
                            }
                        }
                        AyButton(tr("Copy report", "Copiar informe"), primary = false) { copy(vm.diagnostic()) }
                    }
                }
            }
        }
        if (matches("Backup Respaldo exportar importar")) {
            item {
                Panel {
                    Section(tr("Backup", "Respaldo"))
                    AyButton(tr("Export settings and queue", "Exportar ajustes y cola"), primary = false, onClick = exportBackup)
                    AyButton(tr("Restore backup", "Restaurar respaldo"), primary = false, onClick = restoreBackup)
                }
            }
        }
        item {
            Label(
                androidx.compose.ui.res
                    .stringResource(con.themralex.aydlp.R.string.legal),
                size = 12,
                color = t.muted,
            )
            Label("Aytdlp ${con.themralex.aydlp.BuildConfig.VERSION_NAME} · GPL-3.0 · yt-dlp / FFmpeg / QuickJS", size = 11, color = t.muted)
        }
    }
}

@Composable private fun SettingField(
    vm: MainViewModel,
    key: String,
    value: String,
    title: String,
) {
    var text by remember(value) { mutableStateOf(value) }
    Field(text, {
        text =
            it
    }, title)
    if (text != value) AyButton("Save", primary = false) { vm.text(key, text) }
}

@Composable fun ShareSheet(
    input: String,
    vm: MainViewModel,
    onDownload: () -> Unit,
    onClose: () -> Unit,
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    AyTheme(s.theme, s.accent) {
        val t = LocalTokens.current
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.BottomCenter) {
            Panel(Modifier.fillMaxWidth().padding(12.dp).windowInsetsPadding(WindowInsets.navigationBars)) {
                Label("Aytdlp", size = 26, bold = true)
                Label(input, color = t.muted, maxLines = 3)
                Label(profile.name, color = t.accent)
                if (message.isNotBlank()) Label(message, size = 12)
                AyButton("Download / Descargar", Modifier.fillMaxWidth(), onClick = onDownload)
                AyButton("Close / Cerrar", Modifier.fillMaxWidth(), primary = false, onClick = onClose)
            }
        }
    }
}

private fun bytes(value: Long): String =
    when {
        value >= 1_073_741_824 -> {
            "%.1f GB".format(value / 1_073_741_824.0)
        }

        value >= 1_048_576 -> {
            "%.1f MB".format(value / 1_048_576.0)
        }

        value

            >= 1024 -> {
            "%.0f KB".format(value / 1024.0)
        }

        else -> {
            "$value B"
        }
    }

private fun duration(value: Long): String =
    if (value >=
        3600
    ) {
        "%d:%02d:%02d".format(value / 3600, value / 60 % 60, value % 60)
    } else {
        "%d:%02d".format(value / 60, value % 60)
    }

private fun stateLabel(
    state: DownloadState,
    es: Boolean,
): String =
    when (state) {
        DownloadState.QUEUED -> if (es) "En cola" else "Queued"
        DownloadState.SCHEDULED -> if (es) "Programada" else "Scheduled"
        DownloadState.ANALYZING -> if (es) "Analizando" else "Analyzing"
        DownloadState.DOWNLOADING -> if (es) "Descargando" else "Downloading"
        DownloadState.PAUSED -> if (es) "En pausa" else "Paused"
        DownloadState.POST_PROCESSING -> if (es) "Procesando archivo" else "Processing file"
        DownloadState.MOVING -> if (es) "Guardando archivo" else "Saving file"
        DownloadState.COMPLETED -> if (es) "Completada" else "Completed"
        DownloadState.FAILED -> if (es) "Necesita atención" else "Needs attention"
        DownloadState.CANCELED -> if (es) "Cancelada" else "Canceled"
    }
