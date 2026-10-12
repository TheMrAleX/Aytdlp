package con.themralex.aydlp.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import con.themralex.aydlp.domain.FileNames
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaLibrary
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val queue: QueueRepository,
    ) {
        suspend fun rename(
            row: DownloadRow,
            name: String,
        ) = withContext(Dispatchers.IO) {
            val safe = FileNames.sanitize(name)
            val uri = Uri.parse(row.destination)
            val renamed = runCatching { context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, safe) }, null, null) > 0 }.getOrDefault(false)
            if (!renamed) check(DocumentFile.fromSingleUri(context, uri)?.renameTo(safe) == true) { "Destination does not support renaming" }
            queue.dao.get(row.id)?.let { queue.dao.update(it.copy(title = safe)) }
        }

        suspend fun delete(row: DownloadRow) =
            withContext(Dispatchers.IO) {
                val uri = Uri.parse(row.destination)
                val removed = runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
                if (!removed) check(DocumentFile.fromSingleUri(context, uri)?.delete() == true) { "File could not be deleted" }
                queue.dao.delete(row.id)
            }

        suspend fun tag(
            row: DownloadRow,
            value: String,
        ) {
            require(value.length <= 256)
            queue.dao.get(row.id)?.let { queue.dao.update(it.copy(tag = value)) }
        }
    }
