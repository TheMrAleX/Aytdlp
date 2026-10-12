package con.themralex.aydlp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferences by preferencesDataStore("settings")
data class Settings(
    val concurrency: Int = 2, val fragments: Int = 4, val wifi: Boolean = false, val charging: Boolean = false,
    val rate: String = "", val clients: String = "android_vr,web_safari,web_embedded", val proxy: String = "", val userAgent: String = "",
    val destination: String = "", val theme: String = "dark", val accent: Int = 0, val locale: String = "", val channel: String = "stable",
    val template: String = "%(title).160B [%(id)s].%(ext)s", val onboarded: Boolean = false, val autoUpdate: Boolean = false,
    val lastUpdate: Long = 0, val updateReport: String = "", val lastProfile: String = "{}"
)
@Singleton class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    val flow: Flow<Settings> = context.preferences.data.map { p -> Settings(
        p[intPreferencesKey("concurrency")]?:2,p[intPreferencesKey("fragments")]?:4,p[booleanPreferencesKey("wifi")]?:false,p[booleanPreferencesKey("charging")]?:false,
        p[stringPreferencesKey("rate")]?:"",p[stringPreferencesKey("clients")]?:"android_vr,web_safari,web_embedded",p[stringPreferencesKey("proxy")]?:"",p[stringPreferencesKey("userAgent")]?:"",
        p[stringPreferencesKey("destination")]?:"",p[stringPreferencesKey("theme")]?:"dark",p[intPreferencesKey("accent")]?:0,p[stringPreferencesKey("locale")]?:"",p[stringPreferencesKey("channel")]?:"stable",
        p[stringPreferencesKey("template")]?:"%(title).160B [%(id)s].%(ext)s",p[booleanPreferencesKey("onboarded")]?:false,p[booleanPreferencesKey("autoUpdate")]?:false,
        p[longPreferencesKey("lastUpdate")]?:0,p[stringPreferencesKey("updateReport")]?:"",p[stringPreferencesKey("lastProfile")]?:"{}"
    ) }
    suspend fun get(): Settings = flow.first()
    suspend fun text(key: String, value: String) { context.preferences.edit { it[stringPreferencesKey(key)]=value } }
    suspend fun number(key: String, value: Int) { context.preferences.edit { it[intPreferencesKey(key)]=value } }
    suspend fun boolean(key: String, value: Boolean) { context.preferences.edit { it[booleanPreferencesKey(key)]=value } }
    suspend fun time(key: String, value: Long) { context.preferences.edit { it[longPreferencesKey(key)]=value } }
}
