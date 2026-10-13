package con.themralex.aydlp.data

import android.content.Context
import con.themralex.aydlp.domain.Errors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

@Singleton class EngineUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val engine: NativeEngine,
        private val settings: SettingsStore,
    ) {
        private val lock = Mutex()
        private val dir get() = File(context.noBackupFilesDir, "engine").apply { mkdirs() }

        private fun fetch(
            url: String,
            target: File,
            etag: String? = null,
        ): String? {
            val uri = android.net.Uri.parse(url)
            check(
                uri.scheme == "https" &&
                    uri.host in
                    setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"),
            ) { "Untrusted update origin" }
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Aytdlp")
            if (etag != null) connection.setRequestProperty("If-None-Match", etag)
            try {
                if (connection.responseCode == 304) return "unchanged"
                check(connection.responseCode == 200) { "Update HTTP ${connection.responseCode}; wait and retry" }
                connection.inputStream.use { input ->
                    target.outputStream().use { out ->
                        val b = ByteArray(128 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(b)
                            if (n <
                                0
                            ) {
                                break
                            }
                            total += n
                            check(total <= 40_000_000) { "Update exceeds size limit" }
                            out.write(b, 0, n)
                        }
                    }
                }
                return connection.getHeaderField("ETag")
            } finally {
                connection.disconnect()
            }
        }

        suspend fun update(): String =
            lock.withLock {
                withContext(Dispatchers.IO) {
                    engine.initialize()
                    val s = settings.get()
                    check(System.currentTimeMillis() - s.lastUpdate > 60_000) { "Please wait before checking again" }
                    settings.time("lastUpdate", System.currentTimeMillis())
                    val repository =
                        when (s.channel) {
                            "nightly" -> "yt-dlp-nightly-builds"
                            "master" -> "yt-dlp-master-builds"
                            else -> "yt-dlp"
                        }
                    val metadata = File(dir, "$repository-release.json")
                    val etagFile = File(dir, "$repository-etag")
                    val etag =
                        fetch(
                            "https://api.github.com/repos/yt-dlp/$repository/releases/latest",
                            metadata,
                            etagFile.takeIf { it.exists() }?.readText(),
                        )
                    if (etag != null && etag != "unchanged") etagFile.writeText(etag)
                    val release = JSONObject(metadata.readText())
                    val assets = release.getJSONArray("assets")

                    fun asset(name: String): String {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.getString("name") ==
                                name
                            ) {
                                return a.getString("browser_download_url")
                            }
                        }
                        error("Official release lacks $name")
                    }
                    val staged = File(dir, "staged")
                    val sums = File(dir, "SHA2-256SUMS")
                    try {
                        fetch(asset("yt-dlp"), staged)
                        fetch(asset("SHA2-256SUMS"), sums)
                        val expected =
                            sums
                                .readLines()
                                .map {
                                    it.trim().split(
                                        Regex("\\s+"),
                                    )
                                }.firstOrNull { it.size == 2 && it[1].removePrefix("*") == "yt-dlp" }
                                ?.first()
                                ?: error("Missing checksum")
                        val digest = MessageDigest.getInstance("SHA-256")
                        staged.inputStream().use { input ->
                            val b = ByteArray(65536)
                            while (true) {
                                val n = input.read(b)
                                if (n <
                                    0
                                ) {
                                    break
                                }
                                digest.update(b, 0, n)
                            }
                        }
                        val actual = digest.digest().joinToString("") { "%02x".format(it) }
                        check(actual == expected) { "Update checksum mismatch" }
                        engine.verifySolver(staged)
                        engine.run("update-validation", listOf("--version"), staged)
                        engine.run(
                            "update-smoke",
                            listOf(
                                "--ignore-config",
                                "--js-runtimes",
                                "quickjs:${context.applicationInfo.nativeLibraryDir}/libqjs.so",
                                "--skip-download",
                                "--dump-single-json",
                                "--no-playlist",
                                "--socket-timeout",
                                "15",
                                "--",
                                "https://www.youtube.com/watch?v=jNQXAC9IVRw",
                            ),
                            staged,
                        )
                        val previous = File(dir, "previous")
                        if (engine.current.exists()) {
                            engine.current.copyTo(previous, true)
                            previous.setReadOnly()
                        }
                        staged.setReadOnly()
                        check(staged.renameTo(engine.current)) { "Atomic activation failed" }
                        val report =
                            "${release.getString("tag_name")} · SHA-256 verified · live smoke passed\n" +
                                release.optString("body").take(1500)
                        settings.text("updateReport", report)
                        report
                    } catch (e: Exception) {
                        staged.delete()
                        settings.text(
                            "updateReport",
                            "Update rejected; existing engine retained. " + Errors.redact(e.message.orEmpty()),
                        )
                        throw e
                    }
                }
            }

        suspend fun rollback() {
            lock.withLock {
                withContext(Dispatchers.IO) {
                    val previous = File(dir, "previous")
                    check(previous.exists())
                    val temp = File(dir, "rollback")
                    previous.copyTo(temp, true)
                    temp.setReadOnly()
                    check(temp.renameTo(engine.current))
                    settings.text("updateReport", "Rolled back to previous engine")
                }
            }
        }
    }
