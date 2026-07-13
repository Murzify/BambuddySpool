package com.murzify.bambuddyspool.core.security

/** Device-bound storage boundary for the single Bambuddy API token. */
interface SecureTokenStore {
    suspend fun replaceToken(value: SecretValue)
    suspend fun clearToken()
    suspend fun hasToken(): Boolean
}
