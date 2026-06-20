package dev.wolzard.recon.data.secret

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** D3: AES-GCM key in AndroidKeyStore; prefs hold base64(iv ‖ ciphertext). Values are never logged. */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized override fun get(key: Secret): String? {
        val stored = prefs.getString(key.name, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (_: Exception) {
            prefs.edit().remove(key.name).apply() // corrupted or key lost (e.g. restored backup): ask again
            null
        }
    }

    @Synchronized override fun put(key: Secret, value: String?) {
