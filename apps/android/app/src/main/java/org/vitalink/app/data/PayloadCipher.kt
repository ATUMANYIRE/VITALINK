package org.vitalink.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AEAD for all clinical payloads. Room stores only opaque IDs and delivery metadata in clear. */
class PayloadCipher(private val alias: String = "vitalink-payload-v1") {
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun encrypt(plain: String, context: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        c.updateAAD(context.toByteArray(Charsets.UTF_8))
        return byteArrayOf(1) + c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
    }
    fun decrypt(blob: ByteArray, context: String): String {
        require(blob.size >= 30 && blob[0] == 1.toByte()) { "Unsupported encrypted payload" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(1, 13)))
        c.updateAAD(context.toByteArray(Charsets.UTF_8))
        return c.doFinal(blob.copyOfRange(13, blob.size)).toString(Charsets.UTF_8)
    }
}
