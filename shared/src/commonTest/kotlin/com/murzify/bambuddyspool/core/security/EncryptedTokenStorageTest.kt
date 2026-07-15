package com.murzify.bambuddyspool.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class EncryptedTokenStorageTest {

    @Test
    fun replaceStoresOnlyCiphertextAndCanBeReadAtTheTrustedBoundary() = runBlocking {
        val blobs = FakeBlobStore()
        val storage = EncryptedTokenStorage(blobs, PrefixCipher())

        storage.replaceToken(secret("api-token"))

        assertEquals("cipher:nekot-ipa", blobs.value)
        assertFalse(requireNotNull(blobs.value).contains("api-token"))
        assertEquals("api-token", requireNotNull(storage.currentTokenForReplacement()).plainTextForTest())
        assertTrue(storage.hasToken())
    }

    @Test
    fun failedReplacementPreservesExistingEncryptedToken() = runBlocking {
        val blobs = FakeBlobStore("cipher:nekot-dlo")
        val storage = EncryptedTokenStorage(blobs, PrefixCipher(failEncryptFor = "new-token"))

        val failure = runCatching { storage.replaceToken(secret("new-token")) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("cipher:nekot-dlo", blobs.value)
        assertEquals("old-token", requireNotNull(storage.currentTokenForReplacement()).plainTextForTest())
    }

    @Test
    fun clearDeletesOnlyTheEncryptedCredential() = runBlocking {
        val blobs = FakeBlobStore("cipher:nekot")
        val storage = EncryptedTokenStorage(blobs, PrefixCipher())

        storage.clearToken()

        assertNull(blobs.value)
        assertFalse(storage.hasToken())
    }

    @Test
    fun keyInvalidationDeletesUnreadableCredentialAndRequiresReentry() = runBlocking {
        val blobs = FakeBlobStore("cipher:nekot-dlo")
        val cipher = PrefixCipher(invalidateDecrypt = true)
        val storage = EncryptedTokenStorage(blobs, cipher)

        assertNull(storage.currentTokenForReplacement())
        assertNull(blobs.value)
        assertFalse(storage.hasToken())

        cipher.invalidateDecrypt = false
        storage.replaceToken(secret("replacement-token"))
        assertTrue(storage.hasToken())
    }

    private fun secret(value: String): SecretValue = requireNotNull(SecretValue.fromPlainText(value))

    private suspend fun SecretValue.plainTextForTest(): String = useForTrustedRequestBoundary { it }
}

private class FakeBlobStore(initial: String? = null) : EncryptedTokenBlobStore {
    var value: String? = initial

    override fun read(): String? = value

    override fun write(value: String) {
        this.value = value
    }

    override fun delete() {
        value = null
    }
}

private class PrefixCipher(private val failEncryptFor: String? = null, var invalidateDecrypt: Boolean = false) :
    TokenCipher {
    override fun encrypt(plainText: String): String {
        check(plainText != failEncryptFor) { "Encryption failed." }
        return "cipher:${plainText.reversed()}"
    }

    override fun decrypt(encryptedValue: String): String {
        if (invalidateDecrypt) throw TokenKeyInvalidatedException()
        return encryptedValue.removePrefix("cipher:").reversed()
    }
}
