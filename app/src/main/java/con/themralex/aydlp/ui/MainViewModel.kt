package con.themralex.aydlp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import con.themralex.aydlp.data.*
import con.themralex.aydlp.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel class MainViewModel @Inject constructor(val queue:QueueRepository,val settingsStore:SettingsStore,val engine:ExtractionEngine,val updater:EngineUpdater,val cookies:CookieVault,val backup:Backup):ViewModel() {
    val settings=settingsStore.flow.stateIn(viewModelScope,SharingStarted.Eagerly,Settings())
    val downloads=queue.flow.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val profiles=queue.dao.profiles().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val info=MutableStateFlow<MediaInfo?>(null);val busy=MutableStateFlow(false);val message=MutableStateFlow("");val health=MutableStateFlow<EngineHealth?>(null);val profile=MutableStateFlow(Profile())
    private var analysis:Job?=null;private val cache=LinkedHashMap<String,Pair<Long,MediaInfo>>()
    init { action { health.value=engine.health();profile.value=ProfileJson.decode(settingsStore.get().lastProfile) } }
    fun action(block:suspend()->Unit){viewModelScope.launch {try {withContext(Dispatchers.IO){block()}}catch(e:CancellationException){throw e}catch(e:Exception){message.value=Errors.redact(e.message.orEmpty())}}}
    fun analyze(input:String){analysis?.cancel();analysis=viewModelScope.launch {busy.value=true;info.value=null;try {val key=Inputs.normalize(input);val hit=cache[key];val value=if(hit!=null&&System.currentTimeMillis()-hit.first<120000)hit.second else engine.analyze(key,"preview");info.value=value;cache[key]=System.currentTimeMillis() to value;if(cache.size>20)cache.remove(cache.keys.first())}catch(e:CancellationException){throw e}catch(e:Exception){message.value=Errors.classify(e.message.orEmpty()).action+"\n"+Errors.redact(e.message.orEmpty()).takeLast(300)}finally {busy.value=false}}}
    fun cancelAnalysis(){action { engine.stop("preview") };analysis?.cancel();busy.value=false}
    fun edit(update:(Profile)->Profile){profile.value=update(profile.value)}
    fun saveProfile(){action {queue.dao.saveProfile(ProfileRow(profile.value.name,ProfileJson.encode(profile.value)));message.value="Profile saved"}}
    suspend fun enqueue(input:String,schedule:Long=0):String=queue.enqueue(input,profile.value,schedule)
    fun text(key:String,value:String){action {when(key){"template"->FileNames.template(value);"clients"->require(value.matches(Regex("[a-z_,]+")))};settingsStore.text(key,value)}}
    fun number(key:String,value:Int){action {settingsStore.number(key,value)}}
    fun boolean(key:String,value:Boolean){action {settingsStore.boolean(key,value)}}
    fun diagnostic():String {val h=health.value;return Errors.redact("Aytdlp 0.1.0\nAndroid ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}\nABI ${android.os.Build.SUPPORTED_ABIS.joinToString()}\nyt-dlp: ${h?.version}\nJS: ${h?.runtime}\nEJS: ${h?.solver}\nFFmpeg: ${h?.ffmpeg}\nClient chain: ${settings.value.clients}\nHealth: ${h?.detail}\nUpdate: ${settings.value.updateReport}\nLive extraction has not been proven by the runtime self-test.")}
}
