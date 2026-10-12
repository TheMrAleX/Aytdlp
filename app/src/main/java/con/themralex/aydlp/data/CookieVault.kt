package con.themralex.aydlp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class CookieVault
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val file get() = File(context.noBackupFilesDir, "cookies.enc")

        private fun key(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            return (ks.getKey("cookies", null) as? SecretKey)
                ?: KeyGenerator
                    .getInstance("AES", "AndroidKeyStore")
                    .apply {
                        init(
                            KeyGenParameterSpec
                                .Builder(
                                    "cookies",
                                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                                ).setBlockModes("GCM")
                                .setEncryptionPaddings("NoPadding")
                                .build(),
                        )
                    }.generateKey()
        }

        fun import(bytes: ByteArray) {
            require(bytes.size <= 2_000_000 && String(bytes).contains("Netscape")) { "Use a Netscape cookies.txt file" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val temp =
                File(file.path + ".new")
            temp.writeBytes(cipher.iv + cipher.doFinal(bytes))
            check(temp.renameTo(file))
        }

        fun materialize(directory: File): File? {
            if (!file.exists()) return null
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            return File(directory, "cookies.txt").apply {
                writeBytes(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
                setReadable(false, false)
                setReadable(true, true)
                setWritable(false, false)
                setWritable(true, true)
            }
        }

        fun clear() {
            file.delete()
        }

        fun exists() = file.exists()
    }
