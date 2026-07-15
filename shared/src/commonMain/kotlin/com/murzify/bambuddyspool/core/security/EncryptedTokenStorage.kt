package com.murzify.bambuddyspool.core.security

/**
 * Minimal persistence primitive for an already encrypted token payload.
 *
 * Implementations must be private to the application sandbox and must not be backed up or migrated.
 */
interface EncryptedTokenBlobStore {
    fun read(): String?
    fun write(value: String)
    fun delete()
}

/** Encrypts and decrypts a token without exposing any key material to shared code. */
interface TokenCipher {
    @Throws(TokenKeyInvalidatedException::class)
    fun encrypt(plainText: String): String

    @Throws(TokenKeyInvalidatedException::class)
    fun decrypt(encryptedValue: String): String
}

/** The platform key can no longer decrypt the local token and the user must enter it again. */
class TokenKeyInvalidatedException(cause: Throwable? = null) : Exception(cause)

/**
 * Secure token storage with no plaintext cache.
 *
 * Encryption completes before the prior blob is replaced, so a failed replacement leaves the old credential intact.
 * A key invalidation only removes the unreadable credential blob; connection settings and the Room cache belong to
 * separate durable stores and deliberately remain untouched.
 */
class EncryptedTokenStorage(private val blobStore: EncryptedTokenBlobStore, private val cipher: TokenCipher) :
    SecureTokenStore {
    override suspend fun replaceToken(value: SecretValue) {
        value.useForTrustedRequestBoundary { plainText ->
            blobStore.write(cipher.encrypt(plainText))
        }
    }

    override suspend fun clearToken() {
        blobStore.delete()
    }

    override suspend fun hasToken(): Boolean = loadToken() != null

    override suspend fun currentTokenForReplacement(): SecretValue? = loadToken()

    private fun loadToken(): SecretValue? {
        val encryptedValue = blobStore.read() ?: return null
        return try {
            SecretValue.fromPlainText(cipher.decrypt(encryptedValue))
        } catch (_: TokenKeyInvalidatedException) {
            // The ciphertext cannot be recovered without the device-bound key. Never disturb non-secret state.
            blobStore.delete()
            null
        }
    }
}
