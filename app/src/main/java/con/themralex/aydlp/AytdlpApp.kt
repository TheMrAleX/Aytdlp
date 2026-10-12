package con.themralex.aydlp

import android.app.Application
import android.os.StrictMode
import androidx.room.Room
import con.themralex.aydlp.data.*
import con.themralex.aydlp.domain.ExtractionEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp class AytdlpApp :
    Application(),
    androidx.work.Configuration.Provider {
    override val workManagerConfiguration: androidx.work.Configuration
        get() =
            androidx.work.Configuration
                .Builder()
                .setJobSchedulerJobIdRange(10_000, 20_000)
                .build()

    @Inject lateinit var queue: QueueRepository

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy
                    .Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler {
            thread,
            error,
            ->
            runCatching {
                File(
                    noBackupFilesDir,
                    "crash.log",
                ).writeText(
                    con.themralex.aydlp.domain.Errors
                        .redact(error.stackTraceToString()),
                )
            }
            previous?.uncaughtException(thread, error)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { queue.reconcile() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(
        @ApplicationContext context: android.content.Context,
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "queue.db").build()

    @Provides @Singleton
    fun engine(engine: NativeEngine): ExtractionEngine = engine
}
