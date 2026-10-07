package app.duenorth.budget.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Budget-file encryption. The password stays on the phone; the server only
 * stores a salt and a test ciphertext.
 */
object BudgetCrypto {
    const val TEST_PLAINTEXT = "due-north-ok"
    private val magic = "DNENC1".toByteArray(Charsets.US_ASCII)
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 10_000
    private val random = SecureRandom()

    fun derive(
        password: String,
        salt: ByteArray,
    ): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }

    fun encrypt(
        plain: ByteArray,
        key: SecretKey,
    ): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val combined = cipher.doFinal(plain)
        return magic + iv + combined
    }

    fun decrypt(
        blob: ByteArray,
        key: SecretKey,
    ): ByteArray {
        if (blob.size < magic.size + IV_LENGTH + TAG_BITS / 8) error("short blob")
        if (!blob.copyOfRange(0, magic.size).contentEquals(magic)) error("not an encrypted budget")
        val iv = blob.copyOfRange(magic.size, magic.size + IV_LENGTH)
        val combined = blob.copyOfRange(magic.size + IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(combined)
    }

    fun encryptMessage(
        plain: ByteArray,
        key: SecretKey,
    ): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val combined = cipher.doFinal(plain)
        val tagLength = TAG_BITS / 8
        val data = combined.copyOfRange(0, combined.size - tagLength)
        val tag = combined.copyOfRange(combined.size - tagLength, combined.size)
        return SyncProto.encodeEncrypted(iv, tag, data)
    }

    fun decryptMessage(
        blob: ByteArray,
        key: SecretKey,
    ): ByteArray {
        val (iv, tag, data) = SyncProto.decodeEncrypted(blob)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(data + tag)
    }

    fun testBlob(
        password: String,
        salt: ByteArray,
    ): String = Base64.getEncoder().encodeToString(encrypt(TEST_PLAINTEXT.toByteArray(Charsets.UTF_8), derive(password, salt)))

    fun verify(
        password: String,
        saltBase64: String,
        testBase64: String,
    ): SecretKey? =
        try {
            val key = derive(password, Base64.getDecoder().decode(saltBase64))
            val plain = decrypt(Base64.getDecoder().decode(testBase64), key)
            if (plain.contentEquals(TEST_PLAINTEXT.toByteArray(Charsets.UTF_8))) key else null
        } catch (_: Exception) {
            null
        }

    fun export(key: SecretKey): String = Base64.getEncoder().encodeToString(key.encoded)

    fun import(encoded: String): SecretKey = SecretKeySpec(Base64.getDecoder().decode(encoded), "AES")

    fun newSalt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    fun saltText(salt: ByteArray): String = Base64.getEncoder().encodeToString(salt)
}
