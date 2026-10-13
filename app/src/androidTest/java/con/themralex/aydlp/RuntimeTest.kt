package con.themralex.aydlp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import con.themralex.aydlp.core.background
import con.themralex.aydlp.data.AppDatabase
import con.themralex.aydlp.data.DownloadRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeTest {
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
