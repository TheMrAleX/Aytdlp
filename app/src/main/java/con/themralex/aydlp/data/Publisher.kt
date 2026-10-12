package con.themralex.aydlp.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import con.themralex.aydlp.domain.FileNames
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class Publisher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settings: SettingsStore,
    ) {
        suspend fun publish(
            file: File,
            progress: suspend (Long, Long) -> Unit,
        ): String =
            withContext(Dispatchers.IO) {
                val s = settings.get()
                val name = FileNames.sanitize(file.name, 250)
                val mime =
                    android.webkit.MimeTypeMap
                        .getSingleton()
                        .getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
                if (s.destination.isNotBlank()) {
                    val tree = DocumentFile.fromTreeUri(context, Uri.parse(s.destination)) ?: error("Destination is unavailable")
                    val temporary = tree.createFile(mime, ".$name.pending") ?: error("Cannot create destination file")
                    try {
                        context.contentResolver.openOutputStream(temporary.uri, "w")!!.use { output ->
                            file.inputStream().use { input ->
                                val bytes =
                                    ByteArray(256 * 1024)
                                ;var done = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(bytes)
                                    if (n <
                                        0
                                    ) {
                                        break
                                    }
                                    output.write(bytes, 0, n)
                                    done += n
                                    progress(done, file.length())
                                }
                                output.flush()
                            }
                        }
                        check(temporary.length() == file.length()) { "Destination size mismatch" }
                        var candidate = name
                        var index = 1
                        while (tree.findFile(candidate) !=
                            null
                        ) {
                            candidate = "${file.nameWithoutExtension} (${index++}).${file.extension}"
                        }
                        check(temporary.renameTo(candidate)) { "Destination could not finalize file" }
                        temporary.uri.toString()
                    } catch (e: Exception) {
                        temporary.delete()
                        throw e
                    }
                } else if (Build.VERSION.SDK_INT >= 29) {
                    val collection =
                        if (mime.startsWith("audio")) {
                            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else if (mime.startsWith("video")) {
                            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        }
                    val values =
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                            put(MediaStore.MediaColumns.MIME_TYPE, mime)
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                if (mime.startsWith("audio")) {
                                    "Music/Aytdlp"
                                } else if (mime.startsWith("video")) {
                                    "Movies/Aytdlp"
                                } else {
                                    "Download/Aytdlp"
                                },
                            )
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    val uri = context.contentResolver.insert(collection, values) ?: error("Storage permission or space unavailable")
                    try {
                        context.contentResolver.openFileDescriptor(uri, "w")!!.use { fd ->
                            FileOutputStream(fd.fileDescriptor).use { output ->
                                file.inputStream().use { input ->
                                    val bytes =
                                        ByteArray(256 * 1024)
                                    ;var done = 0L
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val n = input.read(bytes)
                                        if (n <
                                            0
                                        ) {
                                            break
                                        }
                                        output.write(bytes, 0, n)
                                        done += n
                                        progress(done, file.length())
                                    }
                                    output.fd.sync()
                                }
                            }
                        }
                        context.contentResolver
                            .openFileDescriptor(
                                uri,
                                "r",
                            )!!
                            .use { check(it.statSize == file.length()) { "Destination size mismatch" } }
                        context.contentResolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                            null,
                            null,
                        )
                        uri.toString()
                    } catch (e: Exception) {
                        context.contentResolver.delete(uri, null, null)
                        throw e
                    }
                } else {
                    val dir = File(context.filesDir, "library").apply { mkdirs() }
                    var target = File(dir, name)
                    var i = 1
                    while (target.exists()) {
                        target =
                            File(dir, "${file.nameWithoutExtension} (${i++}).${file.extension}")
                    }
                    val temp = File(dir, target.name + ".pending")
                    file.inputStream().use { input ->
                        FileOutputStream(temp).use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                    check(temp.length() == file.length())
                    check(temp.renameTo(target))
                    androidx.core.content.FileProvider
                        .getUriForFile(context, "${context.packageName}.files", target)
                        .toString()
                }
            }
    }
