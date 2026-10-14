package con.themralex.aydlp.domain

import java.net.URI
import kotlin.math.min
import kotlin.random.Random

enum class DownloadState { QUEUED, SCHEDULED, ANALYZING, DOWNLOADING, PAUSED, POST_PROCESSING, MOVING, COMPLETED, FAILED, CANCELED }

object StateMachine {
    private val edges =
        mapOf(
            DownloadState.QUEUED to setOf(DownloadState.SCHEDULED, DownloadState.ANALYZING, DownloadState.PAUSED, DownloadState.CANCELED),
            DownloadState.SCHEDULED to setOf(DownloadState.QUEUED, DownloadState.PAUSED, DownloadState.CANCELED),
            DownloadState.ANALYZING to setOf(DownloadState.DOWNLOADING, DownloadState.PAUSED, DownloadState.FAILED, DownloadState.CANCELED),
            DownloadState.DOWNLOADING to
                setOf(
                    DownloadState.POST_PROCESSING,
                    DownloadState.MOVING,
                    DownloadState.PAUSED,
                    DownloadState.FAILED,
                    DownloadState.CANCELED,
                ),
            DownloadState.POST_PROCESSING to
                setOf(
                    DownloadState.MOVING,
                    DownloadState.PAUSED,
                    DownloadState.FAILED,
                    DownloadState.CANCELED,
                ),
            DownloadState.MOVING to setOf(DownloadState.COMPLETED, DownloadState.PAUSED, DownloadState.FAILED, DownloadState.CANCELED),
            DownloadState.PAUSED to setOf(DownloadState.QUEUED, DownloadState.CANCELED),
            DownloadState.FAILED to setOf(DownloadState.QUEUED, DownloadState.CANCELED),
        )

    fun allows(
        from: DownloadState,
        to: DownloadState,
    ) = to in edges[from].orEmpty()

    fun requireTransition(
        from: DownloadState,
        to: DownloadState,
    ) {
        require(allows(from, to)) { "Invalid transition: $from -> $to" }
    }

    val active = setOf(DownloadState.ANALYZING, DownloadState.DOWNLOADING, DownloadState.POST_PROCESSING, DownloadState.MOVING)
}

data class MediaFormat(
    val id: String,
    val extension: String,
    val height: Int,
    val videoCodec: String,
    val audioCodec: String,
    val size: Long,
    val bitrate: Double,
) {
    val label get() = if (height > 0) "${height}p · $extension · $videoCodec" else "Audio · $extension · ${bitrate.toInt()} kbps"
}

data class MediaInfo(
    val id: String,
    val url: String,
    val title: String,
    val channel: String,
    val thumbnail: String,
    val duration: Long,
    val formats: List<MediaFormat>,
    val entries: List<MediaInfo> = emptyList(),
    val live: String = "",
    val size: Long = 0,
)

data class Profile(
    val name: String = "Video · best",
    val audio: Boolean = false,
    val resolution: Int = 1080,
    val codec: String = "",
    val container: String = "mp4",
    val bitrate: Int = 192,
    val subtitles: String = "",
    val autoSubs: Boolean = false,
    val embedSubs: Boolean = false,
    val metadata: Boolean = true,
    val cover: Boolean = false,
    val sponsor: String = "",
    val trim: String = "",
    val custom: String = "",
    val formatId: String = "",
    val chapters: Boolean = true,
    val liveFromStart: Boolean = false,
)

data class Progress(
    val percent: Float?,
    val downloaded: Long = 0,
    val total: Long = 0,
    val speed: Double = 0.0,
    val eta: Long = 0,
    val fragment: Int = 0,
    val fragments: Int = 0,
    val phase: String = "",
)

data class EngineHealth(
    val ready: Boolean,
    val version: String,
    val runtime: String,
    val solver: String,
    val ffmpeg: String,
    val detail: String,
)

interface ExtractionEngine {
    suspend fun health(): EngineHealth

    suspend fun analyze(
        input: String,
        token: String,
    ): MediaInfo

    suspend fun download(
        input: String,
        token: String,
        directory: java.io.File,
        profile: Profile,
        onProgress: suspend (Progress) -> Unit,
    ): List<java.io.File>

    suspend fun stop(token: String)
}

class ProcessOutputBuffer(
    private val limit: Int = 2_000_000,
) {
    private val text = StringBuilder()

    fun append(line: String) {
        if (line.length >= limit) {
            text.clear()
            text.append(line.takeLast(limit - 1)).append('\n')
            return
        }
        val excess = text.length + line.length + 1 - limit
        if (excess > 0) text.delete(0, excess)
        text.appendLine(line)
    }

    override fun toString(): String = text.toString()
}

object Inputs {
    private val url = Regex("https?://[^\\s<>]+")

    fun normalize(value: String): String {
        val trimmed = value.trim()
        val raw = if (trimmed.startsWith("ytsearch1:")) trimmed else url.find(trimmed)?.value ?: trimmed
        require(raw.length in 1..4096) { "Empty or oversized input" }
        if (!raw.startsWith("http")) {
            val query = raw.removePrefix("ytsearch1:")
            require(query.isNotBlank() && !query.startsWith("-") && query.none { it.isISOControl() }) { "Invalid search" }
            return "ytsearch1:$query"
        }
        val parsed = URI(raw)
        require(parsed.scheme in listOf("https", "http") && !parsed.host.isNullOrBlank() && parsed.userInfo == null) { "Invalid URL" }
        require(
            parsed.host != "localhost" && !parsed.host.matches(Regex("(?:127|10|192\\.168)\\..*")),
        ) { "Local addresses are not supported" }
        return parsed.toASCIIString()
    }

    fun batch(text: String): List<String> {
        require(text.toByteArray().size <= 1_048_576)
        return text
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .map(::normalize)
            .distinct()
            .take(500)
            .toList()
    }
}

object AdvancedArguments {
    private val flags =
        setOf(
            "--write-thumbnail",
            "--write-subs",
            "--write-auto-subs",
            "--embed-subs",
            "--embed-thumbnail",
            "--embed-metadata",
            "--embed-chapters",
            "--no-playlist",
            "--live-from-start",
        )
    private val values =
        setOf(
            "--playlist-items",
            "--sub-langs",
            "--sub-format",
            "--sponsorblock-remove",
            "--sponsorblock-mark",
            "--download-sections",
            "--audio-quality",
            "--format-sort",
        )

    fun parse(text: String): List<String> {
        require(text.length < 8192)
        val result = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        for (c in text) {
            when {
                c == '\u0000' || c == '\n' || c == '\r' -> {
                    error("Control characters are forbidden")
                }

                quote != null -> {
                    if (c == quote) quote = null else token.append(c)
                }

                c == '\'' || c == '"' -> {
                    quote = c
                }

                c.isWhitespace() -> {
                    if (token.isNotEmpty()) {
                        result += token.toString()
                        token.clear()
                    }
                }

                else -> {
                    token.append(c)
                }
            }
        }
        require(quote == null) { "Unclosed quote" }
        if (token.isNotEmpty()) result += token.toString()
        var i = 0
        while (i < result.size) {
            val arg = result[i++]
            when {
                arg in flags -> {
                    Unit
                }

                arg in values -> {
                    require(i < result.size && !result[i].startsWith("--")) { "Missing value: $arg" }
                    i++
                }

                else -> {
                    error("Unsupported or unsafe argument: $arg")
                }
            }
        }
        return result
    }
}

object FileNames {
    fun sanitize(
        raw: String,
        maxBytes: Int = 220,
    ): String {
        val clean =
            raw
                .replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_")
                .trim()
                .trim('.')
                .ifBlank { "media" }
        val output = StringBuilder()
        clean.codePoints().forEachOrdered { cp ->
            val next = String(Character.toChars(cp))
            if ((output.toString() + next).toByteArray().size <=
                maxBytes
            ) {
                output.append(next)
            }
        }
        return output.toString().ifBlank { "media" }
    }

    fun template(value: String): String {
        require(value.length in 1..180 && '/' !in value && '\\' !in value && ".." !in value && '\u0000' !in value)
        require(
            Regex("%\\(([^)]+)\\)[^a-zA-Z]*[a-zA-Z]").findAll(value).all {
                it.groupValues[1] in
                    setOf("title", "id", "ext", "uploader", "upload_date", "playlist_index")
            },
        )
        return value
    }
}

enum class FailureKind(
    val retryable: Boolean,
) {
    NETWORK(
        true,
    ),
    FORBIDDEN(
        true,
    ),
    RATE_LIMIT(
        true,
    ),
    FORMAT(false),
    PRIVATE(false),
    AGE(false),
    GEO(false),
    JS_RUNTIME(false),
    SOLVER(true),
    DISK(false),
    EXTRACTOR(true),
    UNKNOWN(false),
}

data class Failure(
    val kind: FailureKind,
    val action: String,
)

object Errors {
    fun classify(log: String): Failure {
        val s = log.lowercase()
        return when {
            "no space left" in s || "enospc" in s -> {
                Failure(FailureKind.DISK, "Free space and resume")
            }

            "javascript runtime" in s || "no supported javascript" in s -> {
                Failure(FailureKind.JS_RUNTIME, "Check the bundled JavaScript runtime")
            }

            "challenge" in s || "ejs" in s || "nsig" in s -> {
                Failure(FailureKind.SOLVER, "Update yt-dlp and its bundled solver")
            }

            "429" in s || "too many requests" in s -> {
                Failure(FailureKind.RATE_LIMIT, "Wait before retrying; reduce concurrency")
            }

            "403" in s || "forbidden" in s -> {
                Failure(
                    FailureKind.FORBIDDEN,
                    if ("po token" in s ||
                        "po_token" in s
                    ) {
                        "Review client/PO Token requirements"
                    } else {
                        "Refresh extraction; check network and client"
                    },
                )
            }

            "private video" in s || "sign in" in s -> {
                Failure(FailureKind.PRIVATE, "Import cookies for content you can access")
            }

            "age" in s && "restrict" in s -> {
                Failure(FailureKind.AGE, "Import cookies from your authorized session")
            }

            "country" in s || "geo" in s -> {
                Failure(FailureKind.GEO, "Content is unavailable in this region")
            }

            "requested format" in s -> {
                Failure(FailureKind.FORMAT, "Analyze again and choose an available format")
            }

            "timed out" in s || "network" in s || "unable to download" in s || "connection" in s -> {
                Failure(FailureKind.NETWORK, "Check connection and resume")
            }

            "extractor" in s || "unable to extract" in s -> {
                Failure(FailureKind.EXTRACTOR, "Update yt-dlp and retry")
            }

            else -> {
                Failure(FailureKind.UNKNOWN, "Open diagnostics for details")
            }
        }
    }

    fun backoff(
        attempt: Int,
        random: Random = Random.Default,
    ): Long = min(3_600_000L, 15_000L * (1L shl attempt.coerceIn(0, 8))) + random.nextLong(0, 10_000)

    fun redact(text: String): String =
        text
            .replace(Regex("(?im)(cookie|authorization|po_token|token|password)([=: ]+)[^\\n\\r]*"), "$1$2[REDACTED]")
            .replace(Regex("https?://[^\\s]+"), "[URL]")
            .takeLast(32_768)
}
