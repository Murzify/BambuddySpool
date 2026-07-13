package com.murzify.bambuddyspool.core.security

/**
 * In-memory secret wrapper that deliberately has no public value property.
 *
 * The wrapper may hand the token to a narrow request/validation boundary, but it never reveals the
 * token through `toString`, copying, DataStore models, or navigation-friendly state.
 */
class SecretValue private constructor(private val value: String) {

    internal suspend fun <T> useForTrustedRequestBoundary(block: suspend (String) -> T): T = block(value)

    override fun toString(): String = REDACTED

    companion object {
        private const val REDACTED = "[redacted secret]"

        fun fromPlainText(value: String): SecretValue? = value
            .takeIf { it.isNotBlank() }
            ?.let(::SecretValue)
    }
}
