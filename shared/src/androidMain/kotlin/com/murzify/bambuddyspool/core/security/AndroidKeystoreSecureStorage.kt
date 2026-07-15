package com.murzify.bambuddyspool.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.murzify.bambuddyspool.core.platform.SecureStorage
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val TOKEN_KEY_ALIAS = "bambuddy_api_token_v1"
private const val PREFERENCES_NAME = "bambuddy_secure_token"
private const val ENCRYPTED_TOKEN_KEY = "encrypted_token"
private const val GCM_IV_LENGTH_BYTES = 12
private const val GCM_TAG_LENGTH_BITS = 128

/** Creates Android's device-bound, Keystore-encrypted storage for the sole Bambuddy API token. */
fun createAndroidSecureStorage(context: Context): SecureStorage = AndroidKeystoreSecureStorage(
    EncryptedTokenStorage(
        blobStore = SharedPreferencesTokenBlobStore(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        ),
        cipher = AndroidKeystoreTokenCipher()
    )
)

private class AndroidKeystoreSecureStorage(private val delegate: SecureTokenStore) : SecureStorage {
    override suspend fun replaceToken(value: SecretValue) = delegate.replaceToken(value)

    override suspend fun clearToken() = delegate.clearToken()

    override suspend fun hasToken(): Boolean = delegate.hasToken()

    override suspend fun currentTokenForReplacement(): SecretValue? = delegate.currentTokenForReplacement()
}

private class SharedPreferencesTokenBlobStore(private val preferences: SharedPreferences) : EncryptedTokenBlobStore {
    override fun read(): String? = preferences.getString(ENCRYPTED_TOKEN_KEY, null)

    override fun write(value: String) {
        check(preferences.edit().putString(ENCRYPTED_TOKEN_KEY, value).commit()) {
            "Unable to persist encrypted token."
        }
    }

    override fun delete() {
        check(preferences.edit().remove(ENCRYPTED_TOKEN_KEY).commit()) {
            "Unable to delete encrypted token."
        }
    }
}

private class AndroidKeystoreTokenCipher : TokenCipher {
    override fun encrypt(plainText: String): String = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.encodeToByteArray())
        encode(cipher.iv + encrypted)
    } catch (exception: KeyPermanentlyInvalidatedException) {
        throw TokenKeyInvalidatedException(exception)
    } catch (exception: UnrecoverableKeyException) {
        throw TokenKeyInvalidatedException(exception)
    }

    override fun decrypt(encryptedValue: String): String = try {
        val payload = decode(encryptedValue)
        require(payload.size > GCM_IV_LENGTH_BYTES) { "Malformed encrypted token." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.copyOfRange(0, GCM_IV_LENGTH_BYTES))
        )
        cipher.doFinal(payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)).decodeToString()
    } catch (exception: KeyPermanentlyInvalidatedException) {
        throw TokenKeyInvalidatedException(exception)
    } catch (exception: UnrecoverableKeyException) {
        throw TokenKeyInvalidatedException(exception)
    } catch (exception: AEADBadTagException) {
        throw TokenKeyInvalidatedException(exception)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(TOKEN_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    TOKEN_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
