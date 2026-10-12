package con.themralex.aydlp.data

import androidx.room.*
import con.themralex.aydlp.domain.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Entity(tableName = "downloads", indices = [Index("url")])
data class DownloadRow(
    @PrimaryKey val id: String,
    val url: String,
    val title: String = "",
    val thumbnail: String = "",
    val channel: String = "",
    val profile: String = "{}",
    val state: DownloadState = DownloadState.QUEUED,
    val progress: Float = 0f,
    val bytes: Long = 0,
    val total: Long = 0,
    val speed: Double = 0.0,
    val eta: Long = 0,
    val fragment: Int = 0,
    val fragments: Int = 0,
    val destination: String = "",
    val failure: String = "",
    val attempt: Int = 0,
    val created: Long = System.currentTimeMillis(),
    val priority: Int = 0,
    val scheduled: Long = 0,
    val charging: Boolean = false,
    val tag: String = "",
    val playbackPosition: Long = 0,
)

@Entity(tableName = "profiles")
data class ProfileRow(
    @PrimaryKey val name: String,
    val json: String,
)

@Entity(tableName = "archive")
data class ArchiveRow(
    @PrimaryKey val mediaId: String,
    val url: String,
    val completed: Long,
)

class Converters {
    @TypeConverter fun state(value: String): DownloadState = DownloadState.valueOf(value)

    @TypeConverter fun state(value: DownloadState): String = value.name
}

@Dao interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY priority DESC, created ASC")
    fun observe(): Flow<List<DownloadRow>>

    @Query("SELECT * FROM downloads WHERE id=:id")
    suspend fun get(id: String): DownloadRow?

    @Query("SELECT * FROM downloads ORDER BY priority DESC, created ASC")
    suspend fun all(): List<DownloadRow>

    @Insert suspend fun insert(row: DownloadRow)

    @Query("UPDATE downloads SET title=:title, thumbnail=:thumbnail, channel=:channel WHERE id=:id AND state='ANALYZING'")
    suspend fun metadata(
        id: String,
        title: String,
        thumbnail: String,
        channel: String,
    )

    @Query("UPDATE downloads SET destination=:uri, progress=100, bytes=:size, total=:size WHERE id=:id AND state='MOVING'")
    suspend fun published(
        id: String,
        uri: String,
        size: Long,
    )

    @Query("UPDATE downloads SET attempt=attempt+1, scheduled=:time WHERE id=:id AND state='FAILED'")
    suspend fun retry(
        id: String,
        time: Long,
    )

    @Query("UPDATE downloads SET profile=:profile WHERE id=:id AND state IN ('QUEUED','SCHEDULED','PAUSED','FAILED')")
    suspend fun profile(
        id: String,
        profile: String,
    ): Int

    @Update suspend fun update(row: DownloadRow)

    @Query("UPDATE downloads SET state=:next, failure=:failure WHERE id=:id AND state=:previous")
    suspend fun transition(
        id: String,
        previous: DownloadState,
        next: DownloadState,
        failure: String = "",
    ): Int

    @Query(
        "UPDATE downloads SET progress=:percent, bytes=:bytes, total=:total, speed=:speed, eta=:eta, fragment=:fragment, fragments=:fragments WHERE id=:id AND state IN ('DOWNLOADING','POST_PROCESSING','MOVING')",
    )
    suspend fun progress(
        id: String,
        percent: Float,
        bytes: Long,
        total: Long,
        speed: Double,
        eta: Long,
        fragment: Int,
        fragments: Int,
    )

    @Query("SELECT * FROM downloads WHERE url=:url AND state NOT IN ('CANCELED','FAILED') LIMIT 1")
    suspend fun duplicate(url: String): DownloadRow?

    @Query("DELETE FROM downloads WHERE id=:id AND state IN ('COMPLETED','CANCELED','FAILED')")
    suspend fun delete(id: String)

    @Query("SELECT * FROM profiles ORDER BY name")
    fun profiles(): Flow<List<ProfileRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: ProfileRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun archive(row: ArchiveRow)

    @Query("SELECT * FROM archive")
    suspend fun archiveRows(): List<ArchiveRow>
}

@Database(entities = [DownloadRow::class, ProfileRow::class, ArchiveRow::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
}

object ProfileJson {
    fun encode(p: Profile): String =
        JSONObject()
            .apply {
                put("name", p.name)
                put("audio", p.audio)
                put("resolution", p.resolution)
                put("codec", p.codec)
                put("container", p.container)
                put("bitrate", p.bitrate)
                put("subtitles", p.subtitles)
                put("autoSubs", p.autoSubs)
                put("embedSubs", p.embedSubs)
                put("metadata", p.metadata)
                put("cover", p.cover)
                put("sponsor", p.sponsor)
                put("trim", p.trim)
                put("custom", p.custom)
                put("formatId", p.formatId)
                put("chapters", p.chapters)
                put("liveFromStart", p.liveFromStart)
            }.toString()

    fun decode(raw: String): Profile {
        val j = JSONObject(raw)
        return Profile(
            j.optString("name", "Video · best"),
            j.optBoolean("audio"),
            j.optInt("resolution", 1080),
            j.optString("codec"),
            j.optString("container", "mp4"),
            j.optInt("bitrate", 192),
            j.optString("subtitles"),
            j.optBoolean("autoSubs"),
            j.optBoolean("embedSubs"),
            j.optBoolean("metadata", true),
            j.optBoolean("cover"),
            j.optString("sponsor"),
            j.optString("trim"),
            j.optString("custom"),
            j.optString("formatId"),
            j.optBoolean("chapters", true),
            j.optBoolean("liveFromStart"),
        )
    }
}
