package con.themralex.aydlp.core

import android.app.*
import android.app.job.*
import android.content.*
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import con.themralex.aydlp.R
import con.themralex.aydlp.data.*
import con.themralex.aydlp.domain.DownloadState
import con.themralex.aydlp.ui.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class Notifications(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW))
    }

    fun notification(row: DownloadRow? = null): Notification {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val b =
            NotificationCompat
                .Builder(context, "downloads")
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(
                    row?.title?.ifBlank {
                        "Aytdlp"
                    } ?: "Aytdlp",
                ).setContentText(
                    row?.let {
                        "${it.state} · ${it.progress.toInt()}%"
                    } ?: "Preparing your downloads",
                ).setContentIntent(
                    open,
                ).setOnlyAlertOnce(
                    true,
                ).setOngoing(row == null || row.state in con.themralex.aydlp.domain.StateMachine.active)
                .setGroup("queue")
                .setGroupSummary(row == null)
                .setProgress(
                    100,
                    row?.progress?.toInt() ?: 0,
                    row == null || (row.state in con.themralex.aydlp.domain.StateMachine.active && row.total == 0L),
                ).setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (row != null && row.state !in setOf(DownloadState.COMPLETED, DownloadState.CANCELED)) {
            for (action in listOf(if (row.state in setOf(DownloadState.PAUSED, DownloadState.FAILED)) "resume" else "pause", "cancel")) {
                val intent = Intent(context, ActionReceiver::class.java).setAction(action).putExtra("id", row.id)
                b.addAction(
                    0,
                    action,
                    PendingIntent.getBroadcast(
                        context,
                        (row.id + action).hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 36 && row != null && row.state in con.themralex.aydlp.domain.StateMachine.active) {
            b.setStyle(
                NotificationCompat
                    .ProgressStyle()
                    .addProgressSegment(NotificationCompat.ProgressStyle.Segment(100))
                    .setProgress(row.progress.toInt())
                    .setProgressIndeterminate(row.total == 0L),
            )
            b.setRequestPromotedOngoing(true).setShortCriticalText("${row.progress.toInt()}%")
        }
        return b.build()
    }

    fun update(row: DownloadRow) {
        if (row.state == DownloadState.CANCELED) {
            manager.cancel(row.id.hashCode())
            return
        }
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(row.id.hashCode(), notification(row))
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundEntry {
    fun queue(): QueueRepository

    fun settings(): SettingsStore

    fun updater(): EngineUpdater

    fun engine(): con.themralex.aydlp.domain.ExtractionEngine
}

fun background(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, BackgroundEntry::class.java)

object Transfers {
    private val owners =
        java.util.concurrent.atomic
            .AtomicInteger(0)
    val active get() = owners.get() > 0

    fun attached() {
        owners.incrementAndGet()
    }

    fun detached() {
        owners.decrementAndGet()
    }

    suspend fun start(
        context: Context,
        userInitiated: Boolean,
    ) {
        if (active) return
        val s = background(context).settings().get()
        if (userInitiated && !s.wifi && !s.charging) {
            ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java))
            return
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val network =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .apply {
                        if (s.wifi) {
                            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                            addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        }
                    }.build()
            val job =
                JobInfo
                    .Builder(
                        410,
                        ComponentName(context, TransferJobService::class.java),
                    ).setRequiredNetwork(network)
                    .setRequiresCharging(s.charging)
                    .apply {
                        if (userInitiated) setUserInitiated(true) else setPersisted(true)
                    }.build()
            val result =
                runCatching {
                    context
                        .getSystemService(
                            JobScheduler::class.java,
                        ).schedule(job)
                }.getOrDefault(JobScheduler.RESULT_FAILURE)
            if (result != JobScheduler.RESULT_SUCCESS) wake(context)
        } else if (userInitiated) {
            ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java))
        } else {
            wake(context)
        }
    }

    fun wake(
        context: Context,
        delay: Long = 1000,
    ) {
        WorkManager
            .getInstance(
                context,
            ).enqueueUniqueWork(
                "queue-wake",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<QueueWakeWorker>()
                    .setInitialDelay(
                        delay.coerceAtLeast(1000),
                        TimeUnit.MILLISECONDS,
                    ).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
    }

    fun maintenance(
        context: Context,
        enabled: Boolean,
    ) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            wm.enqueueUniquePeriodicWork(
                "engine-maintenance",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(
                    24,
                    TimeUnit.HOURS,
                ).setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                ).build(),
            )
        } else {
            wm.cancelUniqueWork("engine-maintenance")
        }
    }
}

@AndroidEntryPoint class TransferJobService : JobService() {
    @Inject lateinit var queue: QueueRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var task: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        Transfers.attached()
        val notices = Notifications(this)
        if (Build.VERSION.SDK_INT >= 34 &&
            params.isUserInitiatedJob
        ) {
            setNotification(params, 410, notices.notification(), JOB_END_NOTIFICATION_POLICY_REMOVE)
        }
        task =
            scope.launch {
                try {
                    queue.drain { notices.update(it) }
                    val scheduled =
                        queue.dao
                            .all()
                            .filter { it.state == DownloadState.SCHEDULED }
                            .minOfOrNull { it.scheduled }
                    if (scheduled !=
                        null
                    ) {
                        Transfers.wake(this@TransferJobService, scheduled - System.currentTimeMillis())
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.Main) {
                        Transfers.detached()
                        jobFinished(params, false)
                        if (queue.dao.all().any { it.state == DownloadState.QUEUED }) Transfers.wake(this@TransferJobService)
                    }
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.launch {
            queue.yieldRunning()
            task?.cancel()
        }
        return true
    }

    override fun onDestroy() {
        scope.launch {
            task?.cancelAndJoin()
            scope.cancel()
        }
        super.onDestroy()
    }
}

@AndroidEntryPoint class TransferService : Service() {
    @Inject lateinit var queue: QueueRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var task: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (task?.isActive == true) return START_NOT_STICKY
        Transfers.attached()
        val n = Notifications(this)
        startForeground(410, n.notification())
        task =
            scope.launch {
                try {
                    queue.drain { n.update(it) }
                } finally {
                    withContext(NonCancellable + Dispatchers.Main) {
                        Transfers.detached()
                        val pending = queue.dao.all().filter { it.state == DownloadState.SCHEDULED || it.state == DownloadState.QUEUED }
                        pending.minOfOrNull { it.scheduled }?.let { Transfers.wake(this@TransferService, it - System.currentTimeMillis()) }
                        stopSelf()
                    }
                }
            }
        return START_NOT_STICKY
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        scope.launch {
            queue.stopRunning()
            Transfers.wake(this@TransferService)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.launch {
            task?.cancelAndJoin()
            scope.cancel()
        }
        super.onDestroy()
    }
}

class QueueWakeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 34) {
            Transfers.start(applicationContext, false)
        } else {
            val notices = Notifications(applicationContext)
            setForeground(ForegroundInfo(410, notices.notification()))
            Transfers.attached()
            try {
                background(applicationContext).queue().drain { notices.update(it) }
            } finally {
                Transfers.detached()
            }
        }
        return Result.success()
    }
}

class MaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            background(applicationContext).updater().update()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val id =
                    intent.getStringExtra("id") ?: return@launch
                val q = background(context).queue()
                when (intent.action) {
                    "pause" -> {
                        q.pause(id)
                    }

                    "cancel" -> {
                        q.cancel(id)
                    }

                    "resume" -> {
                        q.resume(id)
                        Transfers.start(context, true)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action ==
            Intent.ACTION_BOOT_COMPLETED
        ) {
            Transfers.wake(context)
        }
    }
}
