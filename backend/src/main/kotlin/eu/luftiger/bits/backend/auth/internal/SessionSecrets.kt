package eu.luftiger.bits.backend.auth.internal

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Keys derived from `BITS_JWT_SECRET` for what a session keeps in Redis: the Discord refresh token is encrypted with
 * AES-GCM, the admin's password hash is kept only as a keyed fingerprint. A new secret makes both unreadable, so
 * rotating it ends every session, as it already does by invalidating the signatures of the session tokens.
 */
@Component
class SessionSecrets(
    properties: AuthProperties,
) {
    private val secret = properties.jwtSecret.toByteArray()
    private val encryptionKey = SecretKeySpec(derive(ENCRYPTION_LABEL), "AES")
    private val fingerprintKey = SecretKeySpec(derive(FINGERPRINT_LABEL), HMAC)
    private val random = SecureRandom()

    /** Encrypts [plain] with a random nonce; the result is the nonce followed by the ciphertext, in Base64. */
    fun encrypt(plain: String): String {
        val nonce = ByteArray(NONCE_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(TAG_BITS, nonce))
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plain.toByteArray()))
    }

    /** `null` if [encrypted] was not made by [encrypt] with the current secret. */
    fun decrypt(encrypted: String): String? =
        runCatching {
            val bytes = Base64.getDecoder().decode(encrypted)
            val cipher =
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_LENGTH))
                }
            String(cipher.doFinal(bytes, NONCE_LENGTH, bytes.size - NONCE_LENGTH))
        }.getOrNull()

    fun fingerprint(value: String): String =
        Base64.getEncoder().encodeToString(Mac.getInstance(HMAC).apply { init(fingerprintKey) }.doFinal(value.toByteArray()))

    /** One key per purpose, as an HMAC of the purpose's label under the secret. */
    private fun derive(label: String): ByteArray =
        Mac.getInstance(HMAC).apply { init(SecretKeySpec(secret, HMAC)) }.doFinal(label.toByteArray())

    private companion object {
        const val HMAC = "HmacSHA256"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_LENGTH = 12
        const val TAG_BITS = 128
        const val ENCRYPTION_LABEL = "bits:session:refresh-token"
        const val FINGERPRINT_LABEL = "bits:session:admin-password"
    }
}
