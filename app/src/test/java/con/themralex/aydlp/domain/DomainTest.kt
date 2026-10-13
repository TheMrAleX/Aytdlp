package con.themralex.aydlp.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DomainTest {
    @Test fun terminalStatesCannotReopen() {
        for (next in DownloadState.entries) {
            assertFalse(StateMachine.allows(DownloadState.COMPLETED, next))
            assertFalse(StateMachine.allows(DownloadState.CANCELED, next))
        }
    }

    @Test fun completionRequiresPublishing() {
        assertFalse(StateMachine.allows(DownloadState.DOWNLOADING, DownloadState.COMPLETED))
        assertTrue(StateMachine.allows(DownloadState.MOVING, DownloadState.COMPLETED))
    }

    @Test fun pauseResumeIsExplicit() {
        assertTrue(StateMachine.allows(DownloadState.DOWNLOADING, DownloadState.PAUSED))
        assertTrue(StateMachine.allows(DownloadState.PAUSED, DownloadState.QUEUED))
        assertFalse(StateMachine.allows(DownloadState.PAUSED, DownloadState.DOWNLOADING))
    }

    @Test fun forbiddenIsNotAutomaticallyPoToken() {
        assertEquals(FailureKind.FORBIDDEN, Errors.classify("HTTP Error 403: Forbidden").kind)
        assertFalse(Errors.classify("HTTP Error 403: Forbidden").action.contains("PO Token"))
        assertTrue(Errors.classify("403 PO Token required").action.contains("PO Token"))
    }

    @Test fun errorsHaveMeaningfulActions() {
        assertEquals(FailureKind.DISK, Errors.classify("ENOSPC no space left").kind)
        assertEquals(FailureKind.JS_RUNTIME, Errors.classify("No supported JavaScript runtime").kind)
        assertEquals(FailureKind.RATE_LIMIT, Errors.classify("HTTP 429").kind)
    }

    @Test fun rejectsExecutableArguments() {
        for (value in listOf(
            "--exec 'id'",
            "--config-location /tmp/x",
            "--output /etc/passwd",
            "--cookies /private/key",
            "--proxy http://bad",
        )) {
            assertThrows(IllegalStateException::class.java) { AdvancedArguments.parse(value) }
        }
    }

    @Test fun validatesQuotedValues() {
        assertEquals(listOf("--sub-langs", "en,es", "--write-subs"), AdvancedArguments.parse("--sub-langs 'en,es' --write-subs"))
    }

    @Test fun inputCannotInjectAnOption() {
        assertThrows(IllegalArgumentException::class.java) { Inputs.normalize("--exec=id") }
        assertThrows(IllegalArgumentException::class.java) { Inputs.normalize("https://user:pass@example.com") }
    }

    @Test fun sharedTextExtractsUrl() {
        assertEquals("https://example.com/video", Inputs.normalize("Look: https://example.com/video"))
    }

    @Test fun queuedSearchRemainsStableThroughAnalysisAndDownload() {
        val normalized = Inputs.normalize("música en español")
        assertEquals("ytsearch1:música en español", normalized)
        assertEquals(normalized, Inputs.normalize(Inputs.normalize(normalized)))
        assertEquals(listOf(normalized), Inputs.batch(normalized + "\n" + normalized))
    }

    @Test fun normalizedSearchRejectsEmptyOrUnsafeQueries() {
        for (value in listOf("ytsearch1:", "ytsearch1:--exec=id", "ytsearch1:two\rqueries")) {
            assertThrows(IllegalArgumentException::class.java) { Inputs.normalize(value) }
        }
    }

    @Test fun filenamesRespectUtf8Boundary() {
        val output = FileNames.sanitize("😀".repeat(100))
        assertTrue(output.toByteArray().size <= 220)
        assertFalse(output.endsWith("\uFFFD"))
        assertFalse(FileNames.sanitize("../a:b").contains('/'))
    }

    @Test fun templatesRejectTraversal() {
        assertThrows(IllegalArgumentException::class.java) { FileNames.template("../%(title)s") }
        assertEquals("%(title).160B [%(id)s].%(ext)s", FileNames.template("%(title).160B [%(id)s].%(ext)s"))
    }

    @Test fun redactionRemovesSessionsAndSignedUrls() {
        val s = Errors.redact("cookie=secret authorization=Bearer https://video.example/v?token=secret")
        assertFalse(s.contains("secret"))
        assertFalse(s.contains("video.example"))
    }

    @Test fun backoffIsBoundedAndGrows() {
        assertTrue(Errors.backoff(0, Random(1)) < Errors.backoff(3, Random(1)))
        assertTrue(Errors.backoff(100, Random(1)) <= 3_610_000)
    }

    @Test fun cookiesRedactEveryValueOnHeaderLine() {
        val report = Errors.redact("Cookie: SID=first; HSID=second\nHTTP 403")
        assertFalse(report.contains("first"))
        assertFalse(report.contains("second"))
        assertTrue(report.contains("HTTP 403"))
    }
}
