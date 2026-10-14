@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package con.themralex.aydlp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@Composable fun AytdlpScreen(
    vm: MainViewModel,
    enqueue: (String, Long) -> Unit,
    startQueue: () -> Unit,
    resumeDownload: (String) -> Unit,
    locateDownloads: () -> Unit,
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
    val keyboard = LocalSoftwareKeyboardController.current
    val hidden by vm.hiddenMedia.collectAsStateWithLifecycle()
    var editingMedia by remember { mutableStateOf<DownloadRow?>(null) }
    var editedName by remember { mutableStateOf("") }
    var editedTag by remember { mutableStateOf("") }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rows by vm.downloads.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val queueingAnalysis by vm.queueingAnalysis.collectAsStateWithLifecycle()
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
    var libraryFilter by rememberSaveable { mutableIntStateOf(0) }
    val libraryRows =
        remember(rows, hidden, query, libraryFilter) {
            rows
                .filter {
                    it.state == DownloadState.COMPLETED &&
                        it.id !in hidden &&
                        (libraryFilter == 0 || ProfileJson.decode(it.profile).audio == (libraryFilter == 2)) &&
                        (it.title.contains(query, true) || it.tag.contains(query, true))
                }.sortedByDescending { it.created }
        }
    val es = LocalConfiguration.current.locales[0].language == "es"

    fun tr(
        en: String,
        sp: String,
    ) = if (es) sp else en
    BackHandler(tab != 0) { tab = 0 }
    AyTheme(settings.theme, settings.accent) {
        val t = LocalTokens.current
        Column(
            Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .background(t.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("Aytdlp", Modifier.weight(1f), size = 26, bold = true)
                if (health?.ready == false) {
                    Chip(tr("Engine", "Motor")) { tab = 3 }
                }
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
                    Chip("×") {
                        vm.message.value =
                            ""
                    }
                }
            }
            val tabs = listOf(tr("Download", "Descargar"), tr("Queue", "Cola"), tr("Library", "Biblioteca"), tr("Settings", "Ajustes"))
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(300)) { direction * it } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(300)) { -direction * it } + fadeOut(tween(240)))
                },
                label = "screen",
            ) { screen ->
                Column(Modifier.fillMaxSize()) {
                    when (screen) {
                        0 -> {
                            Box(Modifier.weight(1f)) {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 100.dp),
                                    verticalArrangement = Arrangement.spacedBy(18.dp),
                                ) {
                                    item {
                                        Section(tr("Download", "Descargar"))
                                    }
                                    item {
                                        Panel {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Chip(tr("Video", "Vídeo"), !profile.audio) { vm.edit { it.copy(audio = false, container = "mp4", formatId = "") } }
                                                Chip("Audio", profile.audio) { vm.edit { it.copy(audio = true, container = "mp3", formatId = "") } }
                                            }
                                            Field(input, { input = it }, tr("Link or YouTube search", "Enlace o búsqueda en YouTube"), lines = 2)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                AyButton(tr("Analyze", "Analizar"), Modifier.weight(1f), enabled = input.isNotBlank() && !busy) {
                                                    keyboard?.hide()
                                                    vm.analyze(input)
                                                }
                                                IconAction("import", tr("Import links", "Importar enlaces"), onClick = importLinks)
                                            }
                                        }
                                    }
                                    if (analysisError.isNotBlank()) {
                                        item {
                                            Panel(Modifier.fillMaxWidth()) {
                                                Label(tr("Couldn't analyze the link", "No se pudo analizar el enlace"), bold = true)
                                                Label(analysisError, size = 12, maxLines = 4, color = t.muted)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    AyButton(tr("Retry", "Reintentar"), enabled = !busy) { vm.analyze(input) }
                                                    AyButton(tr("Copy report", "Copiar informe"), primary = false) { copy(vm.diagnostic()) }
                                                }
                                            }
                                        }
                                    }
                                    if (busy) {
                                        item {
                                            Panel(Modifier.fillMaxWidth()) {
                                                Label(tr("Quality", "Calidad"), bold = true)
                                                QualityPresets(profile, es) { resolution -> vm.edit { it.copy(resolution = resolution, formatId = "") } }
                                                AyButton(tr("Add to queue", "Añadir a la cola"), Modifier.fillMaxWidth(), enabled = !queueingAnalysis) {
                                                    enqueue(vm.analysisInput, 0)
                                                    tab = 1
                                                }
                                            }
                                        }
                                        item { AnalysisSkeleton(tr("Cancel", "Cancelar"), vm::cancelAnalysis) }
                                    } else if (info != null) {
                                        val media = info!!
                                        item {
                                            Panel {
                                                if (media.thumbnail.isNotBlank()) {
                                                    AsyncImage(media.thumbnail, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                                }
                                                Label(media.title, size = 20, bold = true, maxLines = 3)
                                                Label(listOf(media.channel, duration(media.duration)).filter { it.isNotBlank() }.joinToString(" · "), color = t.muted, size = 12)
                                                if (media.entries.isEmpty()) {
                                                    Row(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .background(t.background)
                                                            .clickable { showProfile = true }
                                                            .padding(16.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Column(Modifier.weight(1f)) {
                                                            Label(tr("Quality", "Calidad"), size = 12, color = t.muted)
                                                            Label(profileSummary(profile), bold = true)
                                                        }
                                                        ControlIcon("chevron", t.accent)
                                                    }
                                                    AyButton(tr("Download", "Descargar"), Modifier.fillMaxWidth()) {
                                                        enqueue(media.url, 0)
                                                        tab = 1
                                                    }
                                                    var scheduling by remember { mutableStateOf(false) }
                                                    TextAction(tr("Schedule", "Programar")) { scheduling = !scheduling }
                                                    AnimatedVisibility(scheduling) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                            Field(schedule, { schedule = it }, "yyyy-MM-dd HH:mm")
                                                            AyButton(tr("Schedule", "Programar"), primary = false) {
                                                                runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { isLenient = false }.parse(schedule)!!.time }
                                                                    .onSuccess {
                                                                        enqueue(media.url, it)
                                                                        tab = 1
                                                                    }.onFailure { vm.message.value = tr("Invalid date", "Fecha inválida") }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    AyButton(tr("Queue all", "Añadir todos"), Modifier.fillMaxWidth()) {
                                                        media.entries.forEach { enqueue(it.url, 0) }
                                                        tab = 1
                                                    }
                                                }
                                            }
                                        }
                                        items(media.entries, key = { it.id + it.url }) { child ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Label(child.title, Modifier.weight(1f), maxLines = 2)
                                                IconAction("download", tr("Add", "Añadir")) { enqueue(child.url, 0) }
                                            }
                                        }
                                    }
                                }
                                AyFab("paste", tr("Paste", "Pegar"), Modifier.align(Alignment.BottomEnd).padding(20.dp), enabled = !busy) {
                                    val pasted = clipboard()
                                    if (pasted.isNotBlank()) {
                                        input = pasted
                                        keyboard?.hide()
                                        vm.analyze(pasted)
                                    }
                                }
                            }
                        }
                        1 -> {
                            val transfers = rows.filter { it.state !in setOf(DownloadState.COMPLETED, DownloadState.CANCELED) }
                            val waiting by produceState("", settings.wifi, settings.charging, transfers.any { it.state == DownloadState.QUEUED }) {
                                while (transfers.any { it.state == DownloadState.QUEUED }) {
                                    val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
                                    val network = manager.getNetworkCapabilities(manager.activeNetwork)
                                    val batteryManager = context.getSystemService(android.os.BatteryManager::class.java)
                                    value =
                                        when {
                                            network?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) != true -> tr("Waiting for network", "Esperando conexión")
                                            settings.wifi && (network.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) != true || !network.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) -> tr("Waiting for unmetered Wi-Fi", "Esperando Wi-Fi sin límite")
                                            settings.charging && !batteryManager.isCharging -> tr("Waiting for charger", "Esperando cargador")
                                            else -> ""
                                        }
                                    delay(2000)
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Label(tr("Queue", "Cola"), Modifier.weight(1f), size = 24, bold = true)
                                        IconAction("play", tr("Resume all", "Reanudar todas"), onClick = startQueue)
                                        IconAction("pause", tr("Pause all", "Pausar todas")) { vm.action { vm.queue.pauseAll() } }
                                    }
                                    if (queueingAnalysis) {
                                        Panel(Modifier.fillMaxWidth()) {
                                            Label(tr("Analyzing", "Analizando"), color = t.accent)
                                            LoadingBar()
                                            TextAction(tr("Cancel", "Cancelar"), vm::cancelAnalysis)
                                        }
                                    }
                                    if (transfers.isEmpty() && !queueingAnalysis) {
                                        Empty(tr("Queue empty", "Cola vacía"), tr("Add a link to download.", "Añade un enlace para descargar."))
                                    }
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                                        items(transfers, key = { it.id }) { row ->
                                            DownloadCard(row, es, waiting, onPause = { vm.action { vm.queue.pause(row.id) } }, onResume = {
                                                resumeDownload(row.id)
                                            }, onCancel = { vm.action { vm.queue.cancel(row.id) } }, onEdit = {
                                                editingDownload = row.id
                                                vm.profile.value = ProfileJson.decode(row.profile)
                                                showProfile = true
                                            })
                                        }
                                    }
                                }
                                AyFab("folder", tr("Locate all", "Ubicar todas"), Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                                    if (settings.destination.isBlank()) tab = 2 else locateDownloads()
                                }
                            }
                        }

                        2 -> {
                            Column(Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Section(tr("Library", "Biblioteca"))
                                Field(query, {
                                    query =
                                        it
                                }, tr("Search title or tags", "Buscar título o etiquetas"))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(tr("All", "Todo"), tr("Video", "Vídeo"), "Audio").forEachIndexed { index, name -> Chip(name, libraryFilter == index) { libraryFilter = index } }
                                }
                                if (hidden.isNotEmpty()) TextAction(tr("Undo delete", "Deshacer borrado")) { vm.undoDelete() }
                                if (libraryRows.isEmpty()) {
                                    Empty(tr("No downloads yet", "Sin descargas"), tr("Finished downloads appear here.", "Aquí aparecen las descargas completadas."))
                                }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                    items(
                                        libraryRows,
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
                            SettingsScreen(vm, es, folder, importCookies, exportBackup, restoreBackup, copy, language, battery, Modifier.weight(1f), analysisError.isNotBlank() || health?.ready == false)
                        }
                    }
                }
            }
            NavigationDock(tabs, tab, rows.count { it.state in StateMachine.active || it.state == DownloadState.QUEUED }) { tab = it }
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
                if (id != null) vm.action { vm.queue.dao.profile(id, ProfileJson.encode(vm.profile.value)) }
                editingDownload = null
            }) { showProfile = false }
        }
        if (!settings.onboarded) {
            Dialog(onDismissRequest = {
            }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().background(t.background).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Label("A", size = 80, bold = true, color = t.accent)
                        Label(tr("Video and audio", "Vídeo y audio"), size = 38, bold = true)
                        Label(
                            tr(
                                "Paste a link to start.",
                                "Pega un enlace para empezar.",
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
        AyIcon(AyGlyph.LIBRARY, color = LocalTokens.current.accent)
        Label(title, size = 20, bold = true)
        Label(caption, color = LocalTokens.current.muted)
    }
}

@Composable private fun DownloadCard(
    row: DownloadRow,
    es: Boolean,
    waiting: String,
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
                    if (row.state == DownloadState.QUEUED && waiting.isNotBlank()) waiting else downloadStateLabel(row.state, es),
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

        if (row.state in setOf(DownloadState.ANALYZING, DownloadState.POST_PROCESSING) || (row.state == DownloadState.DOWNLOADING && row.total <= 0)) LoadingBar() else ProgressBar(row.progress)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("${row.progress.toInt()}% · ${bytes(row.bytes)}", size = 12)
            if (row.state == DownloadState.DOWNLOADING) Label("${bytes(row.speed.toLong())}/s · ${duration(row.eta)}", size = 12, color = t.muted)
        }
        if (row.fragments >
            0
        ) {
            Label("${row.fragment} / ${row.fragments} fragments", size = 11, color = t.muted)
        }
        if (row.failure.isNotBlank()) Label(row.failure, size = 12, color = t.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.state ==
                DownloadState.PAUSED ||
                row.state == DownloadState.FAILED
            ) {
                AyButton(if (es) "Reanudar" else "Resume", Modifier.weight(1f), onClick = onResume)
            } else {
                AyButton(if (es) "Pausar" else "Pause", Modifier.weight(1f), primary = false, onClick = onPause)
            }
            if (row.state !in StateMachine.active) IconAction("settings", if (es) "Editar" else "Edit", onClick = onEdit)
            IconAction("close", if (es) "Cancelar" else "Cancel", onClick = onCancel)
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
    val media by vm.info.collectAsStateWithLifecycle()
    var advanced by remember { mutableStateOf(false) }
    var exactFormats by remember { mutableStateOf(false) }
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
                    Label(tr("Quality and options", "Calidad y opciones"), Modifier.weight(1f), size = 25, bold = true)
                    Chip("×", onClick = onClose)
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
                Label(profileSummary(p), color = t.accent)
                Toggle(
                    tr("Audio only", "Solo audio"),
                    p.audio,
                ) { v -> vm.edit { it.copy(audio = v, container = if (v) "mp3" else "mp4", formatId = "") } }
                if (!p.audio) {
                    Label(tr("Maximum resolution", "Resolución máxima"), color = t.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 360, 480, 720, 1080, 1440, 2160, 4320).forEach { r ->
                            Chip(
                                if (r == 0) tr("Best", "Mejor") else "${r}p",
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
                if (media?.formats?.isNotEmpty() == true) {
                    TextAction(tr("Specific format", "Formato específico")) { exactFormats = !exactFormats }
                    AnimatedVisibility(exactFormats) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            media!!.formats.filter { if (p.audio) it.videoCodec == "none" else it.height > 0 }.sortedByDescending { it.height }.forEach { format ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (p.formatId.substringBefore('+') == format.id) t.accent.copy(alpha = 0.12f) else t.background)
                                        .clickable {
                                            vm.edit { it.copy(formatId = if (!p.audio && format.audioCodec == "none") "${format.id}+ba/best" else format.id, resolution = if (p.audio) it.resolution else format.height) }
                                        }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Label(format.label, Modifier.weight(1f), size = 12)
                                    if (format.size > 0) Label(bytes(format.size), size = 11, color = t.muted)
                                }
                            }
                        }
                    }
                }
                TextAction(tr("Advanced options", "Opciones avanzadas")) { advanced = !advanced }
                AnimatedVisibility(advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Field(p.name, { v -> vm.edit { it.copy(name = v) } }, tr("Profile name", "Nombre del perfil"))
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
                        TextAction(tr("Save preset", "Guardar perfil")) { vm.saveProfile() }
                    }
                }
                AyButton(tr("Apply", "Aplicar"), Modifier.fillMaxWidth()) {
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
    attention: Boolean,
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
            Section(tr("Settings", "Ajustes"))
            Field(search, { search = it }, tr("Search settings", "Buscar ajustes"))
        }
        if (matches("Appearance Tema acento idioma")) {
            item {
                SettingsGroup(tr("Appearance", "Apariencia"), initiallyExpanded = true, forceOpen = search.isNotBlank()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                SettingsGroup(tr("Transfers", "Transferencias"), initiallyExpanded = false, forceOpen = search.isNotBlank()) {
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
                SettingsGroup(tr("Storage", "Almacenamiento"), initiallyExpanded = false, forceOpen = search.isNotBlank()) {
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
                SettingsGroup(tr("Engine", "Motor"), initiallyExpanded = false, forceOpen = search.isNotBlank()) {
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
                SettingsGroup(tr("Maintenance", "Mantenimiento"), initiallyExpanded = false, forceOpen = search.isNotBlank() || attention) {
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
                SettingsGroup(tr("Backup", "Respaldo"), initiallyExpanded = false, forceOpen = search.isNotBlank()) {
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

@Composable private fun SettingsGroup(
    title: String,
    initiallyExpanded: Boolean = false,
    forceOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Label(title, Modifier.weight(1f), size = 20, bold = true)
            Label(if (expanded || forceOpen) "−" else "+", size = 24, color = LocalTokens.current.muted)
        }
        if (expanded || forceOpen) content()
    }
}
