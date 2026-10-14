package con.themralex.aydlp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import con.themralex.aydlp.data.*
import con.themralex.aydlp.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel class MainViewModel
    @Inject
    constructor(
        val queue: QueueRepository,
        val settingsStore: SettingsStore,
        val engine: ExtractionEngine,
        val updater: EngineUpdater,
        val cookies: CookieVault,
        val backup: Backup,
        val library: MediaLibrary,
    ) : ViewModel() {
        val settings = settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
        val downloads = queue.flow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        val profiles = queue.dao.profiles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        val hiddenMedia = MutableStateFlow<Set<String>>(emptySet())
        private val deleting = mutableMapOf<String, Job>()

        fun deleteLater(row: DownloadRow) {
            hiddenMedia.value += row.id
            message.value = "File hidden. Use Undo within 10 seconds."
            deleting[row.id] =
                viewModelScope.launch {
                    delay(10_000)
                    try {
                        withContext(Dispatchers.IO) { library.delete(row) }
                    } catch (e: Exception) {
                        hiddenMedia.value -= row.id
                        message.value = e.message.orEmpty()
                    } finally {
                        deleting.remove(row.id)
                    }
                }
        }

        fun undoDelete() {
            deleting.values.forEach { it.cancel() }
            deleting.clear()
            hiddenMedia.value = emptySet()
            message.value = "Restored"
        }

        val info = MutableStateFlow<MediaInfo?>(null)
        val busy = MutableStateFlow(false)
        val message = MutableStateFlow("")
        val analysisError = MutableStateFlow("")
        private val lastAnalysisReport = MutableStateFlow("")
        val health = MutableStateFlow<EngineHealth?>(null)
        val profile = MutableStateFlow(Profile())
        private var analysis: Job? = null
        private var analysisToken = "preview"
        private val cache = LinkedHashMap<String, Pair<Long, MediaInfo>>()

        init {
            action {
                profile.value = ProfileJson.decode(settingsStore.get().lastProfile)
                health.value = engine.health()
            }
        }

        fun action(block: suspend () -> Unit) {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { block() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message.value =
                        Errors.redact(e.message.orEmpty())
                }
            }
        }

        fun analyze(input: String) {
            val previousToken = analysisToken
            val token = "preview-${java.util.UUID.randomUUID()}"
            analysisToken = token
            val previous = analysis
            previous?.cancel()
            analysis =
                viewModelScope.launch {
                    engine.stop(previousToken)
                    previous?.join()
                    busy.value = true
                    info.value = null
                    profile.value = profile.value.copy(formatId = "")
                    analysisError.value = ""
                    try {
                        val key = Inputs.normalize(input)
                        val hit = cache[key]
                        val value =
                            if (hit !=
                                null &&
                                System.currentTimeMillis() - hit.first < 120000
                            ) {
                                hit.second
                            } else {
                                engine.analyze(key, token)
                            }
                        info.value = value
                        cache[key] =
                            System.currentTimeMillis() to value
                        if (cache.size >
                            20
                        ) {
                            cache.remove(cache.keys.first())
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastAnalysisReport.value = Errors.redact(e.stackTraceToString())
                        analysisError.value =
                            Errors
                                .redact(e.message.orEmpty())
                                .lineSequence()
                                .lastOrNull { it.isNotBlank() }
                                .orEmpty()
                    } finally {
                        busy.value =
                            false
                    }
                }
        }

        fun cancelAnalysis() {
            val token = analysisToken
            action { engine.stop(token) }
            analysis?.cancel()
            busy.value = false
        }

        fun edit(update: (Profile) -> Profile) {
            profile.value = update(profile.value)
        }

        fun saveProfile() {
            action {
                queue.dao.saveProfile(ProfileRow(profile.value.name, ProfileJson.encode(profile.value)))
                message.value =
                    "Profile saved"
            }
        }

        suspend fun enqueue(
            input: String,
            schedule: Long = 0,
        ): String {
            val normalized = Inputs.normalize(input)
            val selected =
                info.value?.let { media ->
                    if (Inputs.normalize(media.url) == normalized) media else media.entries.firstOrNull { Inputs.normalize(it.url) == normalized }
                }
            return queue.enqueue(input, profile.value, schedule, info = selected)
        }

        fun text(
            key: String,
            value: String,
        ) {
            action {
                when (key) {
                    "template" -> FileNames.template(value)
                    "clients" -> require(value.matches(Regex("[a-z_,]+")))
                }
                settingsStore.text(key, value)
            }
        }

        fun number(
            key: String,
            value: Int,
        ) {
            action { settingsStore.number(key, value) }
        }

        fun boolean(
            key: String,
            value: Boolean,
        ) {
            action { settingsStore.boolean(key, value) }
        }

        fun diagnostic(): String {
            val h = health.value
            return Errors.redact(
                "Aytdlp ${con.themralex.aydlp.BuildConfig.VERSION_NAME}\nAndroid ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}\nABI ${android.os.Build.SUPPORTED_ABIS.joinToString()}\nyt-dlp: ${h?.version}\nJS: ${h?.runtime}\nEJS: ${h?.solver}\nFFmpeg: ${h?.ffmpeg}\nClient chain: ${settings.value.clients}\nHealth: ${h?.detail}\nUpdate: ${settings.value.updateReport}\nLast analysis error:\n${lastAnalysisReport.value}\nLive extraction has not been proven by the runtime self-test.",
            )
        }
    }
