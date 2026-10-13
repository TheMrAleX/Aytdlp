package con.themralex.aydlp.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import con.themralex.aydlp.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class NativeEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settings: SettingsStore,
        private val cookies: CookieVault,
    ) : ExtractionEngine {
        private val initialization = Mutex()
        private val launches = Mutex()
        private val healthChecks = Mutex()
        private val processes = ConcurrentHashMap<String, Process>()
        private val groups = ConcurrentHashMap<String, Int>()
        private var initialized = false
        val base get() = File(context.noBackupFilesDir, "youtubedl-android")
        val bundled get() = File(context.noBackupFilesDir, "bundled-yt-dlp")
        val current get() = File(context.noBackupFilesDir, "engine/current")
        private val native get() = File(context.applicationInfo.nativeLibraryDir)
        private val root get() = File(base, "packages")
        private val launcher get() = File(context.noBackupFilesDir, "runner.py")

        suspend fun initialize() =
            initialization.withLock {
                if (!initialized) {
                    withContext(Dispatchers.IO) {
                        YoutubeDL.init(context)
                        FFmpeg.init(context)
                        if (!bundled.exists()) {
                            context.assets.open("yt-dlp").use { input ->
                                bundled.outputStream().use { input.copyTo(it) }
                            }
                            bundled.setReadOnly()
                        }
                        launcher.setWritable(true, true)
                        launcher.writeText(
                            context.assets
                                .open("runner.py")
                                .bufferedReader()
                                .use { it.readText() },
                        )
                        launcher.setReadOnly()
                    }
                    initialized = true
                }
            }

        private fun builder(args: List<String>): ProcessBuilder =
            ProcessBuilder(args).apply {
                environment().apply {
                    put("LD_LIBRARY_PATH", "$root/python/usr/lib:$root/ffmpeg/usr/lib")
                    put("PYTHONHOME", "$root/python/usr")
                    put("SSL_CERT_FILE", "$root/python/usr/etc/tls/cert.pem")
                    put("TMPDIR", context.cacheDir.path)
                    put("PATH", "${native.path}:${System.getenv("PATH").orEmpty()}")
                }
            }

        suspend fun run(
            token: String,
            options: List<String>,
            selected: File = if (current.exists()) current else bundled,
            callback: suspend (String) -> Unit = {
            },
        ): String =
            withContext(Dispatchers.IO) {
                initialize()
                val process =
                    launches.withLock {
                        check(!processes.containsKey(token)) { "Process already running" }
                        val child =
                            builder(listOf(File(native, "libpython.so").path, launcher.path, selected.path, token) + options)
                                .redirectErrorStream(true)
                                .start()
                        processes[token] = child
                        child
                    }
                // Cancellation must terminate the process even while readLine blocks.
                val cancellationGuard =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable + Dispatchers.IO) {
                                if (process.isAlive) stop(token)
                            }
                        }
                    }
                val buffer = ProcessOutputBuffer()
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            currentCoroutineContext().ensureActive()
                            if (line.startsWith("AYTDLP_PID:")) {
                                val pid = line.substringAfter(':').toInt()
                                groups[token] = pid
                                File(context.noBackupFilesDir, "pid-$token").writeText("$pid")
                            } else {
                                callback(line)
                                buffer.append(line)
                            }
                        }
                    }
                    val code = process.waitFor()
                    if (code != 0) error(Errors.redact(buffer.toString()).ifBlank { "Extractor exited with code $code without diagnostic output" })
                    buffer.toString()
                } finally {
                    withContext(NonCancellable) {
                        cancellationGuard.cancelAndJoin()
                        process.outputStream.close()
                    }
                    processes.remove(token)
                    groups.remove(token)
                    File(context.noBackupFilesDir, "pid-$token").delete()
                }
            }

        override suspend fun stop(token: String) =
            withContext(Dispatchers.IO) {
                val process = processes[token] ?: return@withContext
                val pid = groups[token]
                if (pid != null) runCatching { Os.kill(-pid, OsConstants.SIGTERM) } else process.destroy()
                if (!process.waitFor(2500, TimeUnit.MILLISECONDS)) {
                    if (pid != null) runCatching { Os.kill(-pid, OsConstants.SIGKILL) }
                    process.destroyForcibly()
                    process.waitFor(1000, TimeUnit.MILLISECONDS)
                }
            }

        suspend fun reconcile() =
            withContext(Dispatchers.IO) {
                context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("pid-") }?.forEach { file ->
                    val pid = file.readText().toIntOrNull()
                    if (pid != null) {
                        val cmd = runCatching { File("/proc/$pid/cmdline").readText() }.getOrDefault("")
                        if (cmd.contains(launcher.path) &&
                            cmd.contains(file.name.removePrefix("pid-"))
                        ) {
                            runCatching { Os.kill(-pid, OsConstants.SIGKILL) }
                        }
                    }
                    file.delete()
                }
                context.noBackupFilesDir
                    .walkTopDown()
                    .filter { it.name == "cookies.txt" }
                    .forEach { it.delete() }
            }

        override suspend fun health(): EngineHealth =
            healthChecks.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        initialize()
                        val runtime =
                            builder(
                                listOf(File(native, "libqjs.so").path, "-e", "console.log(6*7)"),
                            ).redirectErrorStream(true).start()
                        val text =
                            runtime.inputStream
                                .bufferedReader()
                                .readText()
                                .trim()
                        check(runtime.waitFor() == 0 && text == "42") { "QuickJS self-test failed: $text" }
                        val version = run("health", listOf("--version")).trim()
                        val chosen = if (current.exists()) current else bundled
                        val solver = verifySolver(chosen)
                        val ff = builder(listOf(File(native, "libffmpeg.so").path, "-version")).redirectErrorStream(true).start()
                        val ffVersion =
                            ff.inputStream
                                .bufferedReader()
                                .readLines()
                                .firstOrNull()
                                .orEmpty()
                        check(ff.waitFor() == 0)
                        EngineHealth(
                            true,
                            version,
                            "QuickJS · arithmetic passed",
                            solver,
                            ffVersion,
                            "Runtime checks passed. Live extraction requires a separate smoke test.",
                        )
                    } catch (
                        e: Exception,
                    ) {
                        EngineHealth(false, "Unavailable", "Check runtime", "Check solver", "Check FFmpeg", Errors.redact(e.message.orEmpty()))
                    }
                }
            }

        suspend fun verifySolver(selected: File): String {
            val result = run("solver-${selected.name}", listOf("--aytdlp-verify-solver"), selected).trim()
            check(result == "AYTDLP_EJS_OK") { "Bundled EJS solver verification failed" }
            return "Bundled EJS · Python ZIP verified"
        }

        private suspend fun baseArgs(): List<String> {
            val s = settings.get()
            val args =
                mutableListOf(
                    "--ignore-config",
                    "--no-cache-dir",
                    "--no-colors",
                    "--socket-timeout",
                    "30",
                    "--retries",
                    "3",
                    "--fragment-retries",
                    "3",
                    "--js-runtimes",
                    "quickjs:$native/libqjs.so",
                    "--ffmpeg-location",
                    "$native/libffmpeg.so",
                )
            require(s.clients.matches(Regex("[a-z_,]+"))) { "Invalid client chain" }
            args += listOf("--extractor-args", "youtube:player_client=${s.clients}")
            if (s.proxy.isNotBlank()) {
                require(s.proxy.startsWith("http://") || s.proxy.startsWith("https://") || s.proxy.startsWith("socks5://"))
                args +=
                    listOf("--proxy", s.proxy)
            }
            if (s.userAgent.isNotBlank()) args += listOf("--user-agent", s.userAgent)
            return args
        }

        override suspend fun analyze(
            input: String,
            token: String,
        ): MediaInfo {
            val args = baseArgs() + listOf("--dump-single-json", "--skip-download", "--flat-playlist", "--", Inputs.normalize(input))
            val directory = File(context.noBackupFilesDir, "analysis/$token").apply { mkdirs() }
            val cookie = cookies.materialize(directory)
            val output =
                try {
                    // Analyze with the same authorized session as the download.
                    val authenticated = if (cookie == null) args else listOf("--cookies", cookie.path) + args
                    run(token, authenticated)
                } finally {
                    directory.deleteRecursively()
                }
            val json = JSONObject(output.lineSequence().first { it.startsWith("{") })
            return parse(json, input)
        }

        private fun parse(
            j: JSONObject,
            url: String,
        ): MediaInfo {
            val formats = j.optJSONArray("formats")
            val list =
                buildList {
                    if (formats !=
                        null
                    ) {
                        for (i in 0 until formats.length()) {
                            val f = formats.getJSONObject(i)
                            add(
                                MediaFormat(
                                    f.optString("format_id"),
                                    f.optString("ext"),
                                    f.optInt("height"),
                                    f.optString("vcodec"),
                                    f.optString("acodec"),
                                    f.optLong("filesize", f.optLong("filesize_approx")),
                                    f.optDouble("abr", f.optDouble("tbr", 0.0)),
                                ),
                            )
                        }
                    }
                }
            val entries = j.optJSONArray("entries")
            val children =
                buildList {
                    if (entries !=
                        null
                    ) {
                        for (i in 0 until entries.length().coerceAtMost(500)) {
                            val child =
                                entries.optJSONObject(i) ?: continue
                            add(parse(child, child.optString("webpage_url", child.optString("url"))))
                        }
                    }
                }
            return MediaInfo(
                j.optString("id"),
                j.optString("webpage_url", url),
                j.optString("title", "Untitled"),
                j.optString("uploader", j.optString("channel")),
                j.optString("thumbnail"),
                j.optLong("duration"),
                list,
                children,
                j.optString("live_status"),
                j.optLong("filesize_approx"),
            )
        }

        override suspend fun download(
            input: String,
            token: String,
            directory: File,
            profile: Profile,
            onProgress: suspend (Progress) -> Unit,
        ): List<File> {
            directory.mkdirs()
            val s = settings.get()
            val args = baseArgs().toMutableList()
            args +=
                listOf(
                    "--continue",
                    "--no-playlist",
                    "--no-simulate",
                    "--newline",
                    "--no-overwrites",
                    "--keep-video",
                    "-N",
                    s.fragments.coerceIn(1, 16).toString(),
                    "-P",
                    directory.path,
                    "-o",
                    FileNames.template(s.template),
                    "--print",
                    "after_move:AYTDLP_FILE:%(filepath)s",
                    "--progress-template",
                    "download:AYTDLP_PROGRESS:{\"downloaded\":%(progress.downloaded_bytes)j,\"total\":%(progress.total_bytes,progress.total_bytes_estimate)j,\"speed\":%(progress.speed)j,\"eta\":%(progress.eta)j,\"fragment\":%(progress.fragment_index)j,\"fragments\":%(progress.fragment_count)j}",
                )
            if (s.rate.isNotBlank()) {
                require(s.rate.matches(Regex("[0-9]+[KMGkmg]?")))
                args += listOf("--limit-rate", s.rate)
            }
            if (profile.audio) {
                args += listOf("-f", profile.formatId.ifBlank { "bestaudio/best" }, "-x", "--audio-format", profile.container, "--audio-quality", "${profile.bitrate}K")
            } else {
                val codec = if (profile.codec.isBlank()) "" else "[vcodec^=${profile.codec}]"
                args +=
                    listOf(
                        "-f",
                        profile.formatId.ifBlank {
                            "bv*[height<=${profile.resolution}]$codec+ba/b[height<=${profile.resolution}]/best"
                        },
                        "--merge-output-format",
                        profile.container,
                    )
            }
            if (profile.metadata) args += "--embed-metadata"
            if (profile.chapters) args += "--embed-chapters"
            if (profile.cover) args += "--embed-thumbnail"
            if (profile.subtitles.isNotBlank()) {
                args += listOf("--write-subs", "--sub-langs", profile.subtitles)
                if (profile.autoSubs) {
                    args +=
                        "--write-auto-subs"
                }
                if (profile.embedSubs) args += "--embed-subs"
            }
            if (profile.sponsor.isNotBlank()) {
                args +=
                    listOf("--sponsorblock-${if (profile.sponsor == "mark") "mark" else "remove"}", "sponsor")
            }
            if (profile.trim.isNotBlank()) {
                require(profile.trim.matches(Regex("[0-9:.]+-[0-9:.]+")))
                args +=
                    listOf("--download-sections", "*${profile.trim}", "--force-keyframes-at-cuts")
            }
            if (profile.liveFromStart) args += "--live-from-start"
            args += AdvancedArguments.parse(profile.custom)
            val cookie = cookies.materialize(directory)
            if (cookie != null) args += listOf("--cookies", cookie.path)
            args += listOf("--", Inputs.normalize(input))
            val files = mutableListOf<File>()
            var last = 0L
            try {
                run(token, args) { line ->
                    if (line.startsWith("AYTDLP_FILE:")) {
                        val f = File(line.substringAfter(':')).canonicalFile
                        check(
                            f.path.startsWith(directory.canonicalPath + File.separator),
                        )
                        files += f
                    } else if (line.startsWith("AYTDLP_PROGRESS:") && System.currentTimeMillis() - last >= 250) {
                        last = System.currentTimeMillis()
                        val j = JSONObject(line.substringAfter(':'))
                        val bytes = j.optLong("downloaded")
                        val total = j.optLong("total")
                        onProgress(
                            Progress(
                                if (total >
                                    0
                                ) {
                                    (bytes.toDouble() / total * 100).toFloat().coerceIn(0f, 99f)
                                } else {
                                    null
                                },
                                bytes,
                                total,
                                j.optDouble("speed", 0.0),
                                j.optLong("eta"),
                                j.optInt("fragment"),
                                j.optInt("fragments"),
                            ),
                        )
                    } else if (line.startsWith("[Merger]") ||
                        line.startsWith("[ExtractAudio]") ||
                        line.startsWith("[Embed")
                    ) {
                        onProgress(Progress(null, phase = "merge"))
                    }
                }
            } finally {
                cookie?.delete()
            }
            check(files.isNotEmpty() && files.all { it.exists() && it.length() > 0 }) { "No complete output was produced" }
            return files
        }
    }
