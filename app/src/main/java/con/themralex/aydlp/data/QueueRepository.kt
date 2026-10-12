package con.themralex.aydlp.data

import android.content.Context
import android.os.StatFs
import androidx.room.withTransaction
import con.themralex.aydlp.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class QueueRepository @Inject constructor(@ApplicationContext private val context:Context,private val db:AppDatabase,private val engine:ExtractionEngine,private val native:NativeEngine,private val settings:SettingsStore,private val publisher:Publisher) {
    val dao=db.downloads();val flow=dao.observe();private val claim=Mutex();private val running=ConcurrentHashMap<String,Job>()
    suspend fun enqueue(input:String,profile:Profile,schedule:Long=0,charging:Boolean=false):String = db.withTransaction {
        val url=Inputs.normalize(input);dao.duplicate(url)?.let { return@withTransaction it.id }
        val id=UUID.randomUUID().toString();dao.insert(DownloadRow(id,url,profile=ProfileJson.encode(profile),state=if(schedule>System.currentTimeMillis())DownloadState.SCHEDULED else DownloadState.QUEUED,scheduled=schedule,charging=charging));settings.text("lastProfile",ProfileJson.encode(profile));id
    }
    suspend fun transition(id:String,next:DownloadState,failure:String=""):Boolean {
        val row=dao.get(id)?:return false;if(!StateMachine.allows(row.state,next))return false
        return dao.transition(id,row.state,next,failure)==1
    }
    suspend fun pause(id:String) { if(transition(id,DownloadState.PAUSED)){engine.stop(id);engine.stop("analysis-$id");running[id]?.cancel()} }
    suspend fun resume(id:String) { transition(id,DownloadState.QUEUED) }
    suspend fun cancel(id:String) { if(transition(id,DownloadState.CANCELED)){engine.stop(id);engine.stop("analysis-$id");running[id]?.cancel();File(context.noBackupFilesDir,"staging/$id").deleteRecursively()} }
    suspend fun pauseAll() { dao.all().filter { it.state in StateMachine.active || it.state in setOf(DownloadState.QUEUED,DownloadState.SCHEDULED) }.forEach { pause(it.id) } }
    suspend fun resumeAll() { dao.all().filter { it.state in setOf(DownloadState.PAUSED,DownloadState.FAILED) }.forEach { resume(it.id) } }
    suspend fun reconcile() { native.reconcile();dao.all().filter { it.state in StateMachine.active }.forEach { transition(it.id,DownloadState.PAUSED,"Interrupted safely; resume to continue") } }
    suspend fun drain(onChanged:suspend(DownloadRow)->Unit) = coroutineScope {
        while(isActive) {
            val s=settings.get();val rows=dao.all()
            rows.filter { it.state==DownloadState.SCHEDULED && it.scheduled<=System.currentTimeMillis() }.forEach { transition(it.id,DownloadState.QUEUED) }
            val queued=rows.filter { it.state==DownloadState.QUEUED }
            val capacity=(s.concurrency.coerceIn(1,6)-running.size).coerceAtLeast(0)
            queued.take(capacity).forEach { row ->
                if(claim.withLock { transition(row.id,DownloadState.ANALYZING) }) {
                    val job=launch(start=CoroutineStart.LAZY) { try { execute(row.id,onChanged) } finally { running.remove(row.id) } }
                    running[row.id]=job;job.start()
                }
            }
            if(running.isEmpty() && queued.isEmpty())break
            delay(350)
        }
    }
    private suspend fun execute(id:String,onChanged:suspend(DownloadRow)->Unit) {
        val directory=File(context.noBackupFilesDir,"staging/$id").apply { mkdirs() }
        try {
            val row=dao.get(id)?:return
            val info=engine.analyze(row.url,"analysis-$id")
            val estimate=(info.size.takeIf { it>0 }?:300_000_000L)*2+100_000_000L
            check(StatFs(directory.path).availableBytes>=estimate) { "No space left for download and merge" }
            val current=dao.get(id)?:return;if(current.state!=DownloadState.ANALYZING)return
            dao.metadata(id,info.title,info.thumbnail,info.channel)
            if(!transition(id,DownloadState.DOWNLOADING))return
            val files=engine.download(row.url,id,directory,ProfileJson.decode(row.profile)) { progress ->
                if(progress.phase=="merge")transition(id,DownloadState.POST_PROCESSING)
                dao.progress(id,progress.percent?:dao.get(id)?.progress?:0f,progress.downloaded,progress.total,progress.speed,progress.eta,progress.fragment,progress.fragments)
                dao.get(id)?.let { onChanged(it) }
            }
            if(!transition(id,DownloadState.MOVING))return
            val outputs=mutableListOf<String>()
            for(file in files) outputs+=publisher.publish(file){done,total -> dao.progress(id,(done.toDouble()/total*100).toFloat().coerceAtMost(99f),done,total,0.0,0,0,0);dao.get(id)?.let { onChanged(it) } }
            val latest=dao.get(id)?:return;if(latest.state!=DownloadState.MOVING)return
            dao.published(id,outputs.first(),files.sumOf { it.length() })
            if(transition(id,DownloadState.COMPLETED)){dao.archive(ArchiveRow(info.id,row.url,System.currentTimeMillis()));directory.deleteRecursively();dao.get(id)?.let { onChanged(it) }}
        } catch(e:CancellationException){withContext(NonCancellable){engine.stop(id);engine.stop("analysis-$id");transition(id,DownloadState.PAUSED)};throw e}
        catch(e:Exception) {
            val failure=Errors.classify(e.message.orEmpty());transition(id,DownloadState.FAILED,"${failure.kind}: ${failure.action}")
            val row=dao.get(id)
            if(row!=null && failure.kind.retryable && row.attempt<3){val whenRetry=System.currentTimeMillis()+Errors.backoff(row.attempt);dao.retry(id,whenRetry);if(transition(id,DownloadState.QUEUED))transition(id,DownloadState.SCHEDULED)}
            dao.get(id)?.let { onChanged(it) }
        }
    }
    suspend fun stopRunning() { running.keys.toList().forEach { pause(it) } }
}
