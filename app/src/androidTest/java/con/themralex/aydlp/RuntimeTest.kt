package con.themralex.aydlp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import con.themralex.aydlp.core.background
import con.themralex.aydlp.data.AppDatabase
import con.themralex.aydlp.data.DownloadRow
import con.themralex.aydlp.data.NativeEngine
import con.themralex.aydlp.data.Publisher
import con.themralex.aydlp.data.QueueRepository
import con.themralex.aydlp.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RuntimeTest {
    @Test fun queuedPreviewIsReusedAndPauseResumeKeepsMetadata(): Unit =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val entry = background(context)
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            val settings = entry.settings()
            val previousProfile = settings.get().lastProfile
            val started = Channel<Profile>(Channel.UNLIMITED)
            val fake =
                object : ExtractionEngine {
                    override suspend fun health(): EngineHealth = error("Not used")

                    override suspend fun analyze(
                        input: String,
                        token: String,
                    ): MediaInfo = error("Preview must be reused")

                    override suspend fun stop(token: String) = Unit

                    override suspend fun download(
                        input: String,
                        token: String,
                        directory: File,
                        profile: Profile,
                        onProgress: suspend (Progress) -> Unit,
                    ): List<File> {
                        started.send(profile)
                        awaitCancellation()
                    }
                }
            val queue = QueueRepository(context, db, fake, entry.engine() as NativeEngine, settings, Publisher(context, settings))
            val preview = CompletableDeferred<MediaInfo>()
            val url = "https://example.com/preview-handoff"
            val id = queue.enqueue(url, Profile(resolution = 480), analysis = preview)
            val drain = launch { queue.drain {} }
            try {
                withTimeout(10000) {
                    while (db.downloads().get(id)?.state != DownloadState.ANALYZING) delay(20)
                }
                assertTrue(started.tryReceive().isFailure)
                preview.complete(MediaInfo("fixture", url, "Preview title", "Channel", "", 10, emptyList()))
                withTimeout(10000) { assertEquals(480, started.receive().resolution) }
                assertEquals("Preview title", db.downloads().get(id)?.title)
                withTimeout(5000) { assertTrue(queue.pause(id)) }
                assertEquals(DownloadState.PAUSED, db.downloads().get(id)?.state)
                drain.join()
                queue.resume(id)
                val resumed = launch { queue.drain {} }
                withTimeout(10000) { assertEquals(480, started.receive().resolution) }
                queue.cancel(id)
                resumed.join()
                assertEquals(DownloadState.CANCELED, db.downloads().get(id)?.state)
            } finally {
                drain.cancelAndJoin()
                queue.stopRunning()
                db.close()
                settings.text("lastProfile", previousProfile)
            }
        }

    @Test fun cancelSilentExtractorReleasesProcess(): Unit =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val engine = background(context).engine() as NativeEngine
            val fixture = File(context.noBackupFilesDir, "silent-fixture.py")
            fixture.writeText("import time\nprint('READY', flush=True)\ntime.sleep(60)\n")
            val ready = CompletableDeferred<Unit>()
            try {
                val job = launch { engine.run("cancel-test", emptyList(), fixture) { if (it == "READY") ready.complete(Unit) } }
                withTimeout(15000) { ready.await() }
                withTimeout(5000) { job.cancelAndJoin() }
                assertFalse(File(context.noBackupFilesDir, "pid-cancel-test").exists())
                // Token reuse proves cancellation completed and bookkeeping was released.
                assertTrue(engine.run("cancel-test", listOf("--version")).isNotBlank())
            } finally {
                engine.stop("cancel-test")
                fixture.delete()
            }
        }

    @Test fun databasePersistsAcrossReopen(): Unit =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "persist-test.db"
            context.deleteDatabase(name)
            var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            db.downloads().insert(DownloadRow("persistent", "https://example.com/runtime-test"))
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals("persistent", db.downloads().get("persistent")?.id)
            db.close()
            context.deleteDatabase(name)
        }

    @Test fun bundledRuntimeAndSolverWork(): Unit =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val h = background(context).engine().health()
            assertTrue(h.detail, h.ready)
        }
}
