package con.themralex.aydlp.ui

import android.Manifest
import android.app.LocaleManager
import android.content.*
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import con.themralex.aydlp.core.Transfers
import con.themralex.aydlp.domain.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val folder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri !=
                null
            ) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                vm.text("destination", uri.toString())
            }
        }
    private val imports =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri !=
                null
            ) {
                vm.action {
                    val text = contentResolver.openInputStream(uri)!!.use { String(it.readBounded(1_048_577)) }
                    for (input in Inputs.batch(text))vm.enqueue(input)
                    withContext(Dispatchers.Main) { Transfers.start(this@MainActivity, true) }
                    vm.message.value =
                        "Batch added"
                }
            }
        }
    private val cookies =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri !=
                null
            ) {
                vm.action {
                    val bytes = contentResolver.openInputStream(uri)!!.use { it.readBounded(2_000_001) }
                    vm.cookies.import(bytes)
                    vm.message.value =
                        "Cookies encrypted; excluded from backups and reports"
                }
            }
        }
    private val export =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri !=
                null
            ) {
                vm.action {
                    contentResolver.openOutputStream(uri)!!.use { it.write(vm.backup.export().toByteArray()) }
                    vm.message.value =
                        "Backup exported without cookies"
                }
            }
        }
    private val restore =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri !=
                null
            ) {
                vm.action {
                    val raw = contentResolver.openInputStream(uri)!!.use { String(it.readBounded(2_000_001)) }
                    vm.backup.restore(raw)
                    vm.message.value =
                        "Backup restored; start the queue when ready"
                }
            }
        }

    override fun attachBaseContext(newBase: Context) {
        val locale = newBase.getSharedPreferences("locale", MODE_PRIVATE).getString("language", "").orEmpty()
        if (Build.VERSION.SDK_INT < 33 && locale.isNotBlank()) {
            val config = android.content.res.Configuration(newBase.resources.configuration)
            config.setLocales(LocaleList.forLanguageTags(locale))
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AytdlpScreen(
                vm,
                enqueue = { input, schedule ->
                    val selectedProfile = vm.profile.value
                    requestNotifications()
                    vm.action {
                        vm.enqueue(input, schedule, selectedProfile)
                        withContext(Dispatchers.Main) {
                            if (schedule <=
                                System.currentTimeMillis()
                            ) {
                                Transfers.start(this@MainActivity, true)
                            } else {
                                Transfers.wake(
                                    this@MainActivity,
                                    schedule - System.currentTimeMillis(),
                                )
                            }
                        }
                    }
                },
                startQueue = {
                    requestNotifications()
                    vm.action {
                        vm.queue.resumeAll()
                        withContext(Dispatchers.Main) { Transfers.start(this@MainActivity, true) }
                    }
                },
                locateDownloads = {
                    runCatching {
                        val destination = vm.settings.value.destination
                        val intent =
                            if (destination.isBlank()) {
                                Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                            } else {
                                val tree = Uri.parse(destination)
                                val document = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
                                Intent(Intent.ACTION_VIEW).setDataAndType(document, "vnd.android.document/directory").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        startActivity(intent)
                    }.onFailure { vm.message.value = it.message.orEmpty() }
                },
                resumeDownload = { id ->
                    vm.action {
                        vm.queue.resume(id)
                        withContext(Dispatchers.Main) { Transfers.start(this@MainActivity, true) }
                    }
                },
                importLinks = {
                    imports.launch(arrayOf("text/plain"))
                },
                folder = { folder.launch(null) },
                importCookies = { cookies.launch(arrayOf("text/plain")) },
                exportBackup = { export.launch("aytdlp-backup.json") },
                restoreBackup = { restore.launch(arrayOf("application/json", "text/plain")) },
                copy = { text ->
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Aytdlp", text))
                    vm.message.value =
                        "Copied"
                },
                clipboard = {
                    getSystemService(ClipboardManager::class.java)
                        .primaryClip
                        ?.getItemAt(0)
                        ?.coerceToText(this)
                        ?.toString()
                        .orEmpty()
                },
                open = { row -> openMedia(row.destination) },
                share = { row -> share(row.destination) },
                language = ::updateLanguage,
                battery = { startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            )
        }
    }

    private fun updateLanguage(value: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(value)
        } else {
            getSharedPreferences("locale", MODE_PRIVATE).edit().putString("language", value).apply()
            recreate()
        }
        vm.text("locale", value)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openMedia(uri: String) {
        runCatching { startActivity(Intent(this, PlayerActivity::class.java).putExtra("uri", uri)) }.onFailure {
            vm.message.value =
                it.message.orEmpty()
        }
    }

    private fun share(uri: String) {
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(
                        Intent.ACTION_SEND,
                    ).setType(
                        contentResolver.getType(Uri.parse(uri)) ?: "video/*",
                    ).putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "Share",
                ),
            )
        }.onFailure {
            vm.message.value =
                it.message.orEmpty()
        }
    }
}

@AndroidEntryPoint class ShareActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val raw =
            when (intent.action) {
                Intent.ACTION_SEND, "con.themralex.aydlp.QUEUE" -> intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                Intent.ACTION_VIEW -> intent.dataString.orEmpty()
                else -> ""
            }
        val input =
            runCatching { Inputs.normalize(raw) }.getOrElse {
                finish()
                return
            }
        setContent {
            ShareSheet(input, vm, onDownload = {
                vm.action {
                    vm.enqueue(input)
                    withContext(Dispatchers.Main) {
                        Transfers.start(this@ShareActivity, true)
                        finish()
                    }
                }
            }, onClose = { finish() })
        }
    }
}

private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
