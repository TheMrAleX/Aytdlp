package con.themralex.aydlp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import con.themralex.aydlp.core.background
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeTest {
    @Test fun databasePersistsAcrossReopen() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val queue = background(context).queue()
            val id =
                queue.enqueue(
                    "https://example.com/runtime-test",
                    con.themralex.aydlp.domain
                        .Profile(),
                )
            assertEquals(id, queue.dao.get(id)?.id)
            queue.cancel(id)
        }

    @Test fun bundledRuntimeAndSolverWork() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val entry =
                dagger.hilt.android.EntryPointAccessors
                    .fromApplication(context, RuntimeEntry::class.java)
            val h = entry.engine().health()
            assertTrue(h.detail, h.ready)
        }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface RuntimeEntry {
    fun engine(): con.themralex.aydlp.domain.ExtractionEngine
}
