package com.murzify.bambuddyspool.core.network

import com.murzify.bambuddyspool.core.domain.IncompatibleApiResponse

sealed interface BambuddyMappingResult<out T> {
    data class Success<T>(val value: T) : BambuddyMappingResult<T>
    data class Failure(val error: IncompatibleApiResponse) : BambuddyMappingResult<Nothing>
}
