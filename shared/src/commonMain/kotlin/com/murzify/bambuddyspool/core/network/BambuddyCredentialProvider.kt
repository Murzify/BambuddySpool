package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.security.SecretValue

fun interface BambuddyCredentialProvider {
    suspend fun loadApiToken(): SecretValue?
}
