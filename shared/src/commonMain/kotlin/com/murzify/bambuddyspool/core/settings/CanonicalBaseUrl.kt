package com.murzify.bambuddyspool.core.settings

enum class UrlScheme(val wireName: String, val defaultPort: Int) {
    Http("http", 80),
    Https("https", 443);

    companion object {
        fun parse(value: String): UrlScheme? = entries.firstOrNull { it.wireName == value.lowercase() }
    }
}

data class UrlOrigin(val scheme: UrlScheme, val host: String, val effectivePort: Int) {
    init {
        require(host.isNotBlank()) { "Origin host must not be blank." }
        require(effectivePort in MIN_PORT..MAX_PORT) { "Origin port is out of range." }
    }

    val canonical: String = buildString {
        append(scheme.wireName)
        append("://")
        append(host.forUrlAuthority())
        if (effectivePort != scheme.defaultPort) {
            append(':')
            append(effectivePort)
        }
    }
}

data class CanonicalBaseUrl(val scheme: UrlScheme, val host: String, val explicitPort: Int?, val basePath: String) {
    init {
        require(host.isNotBlank()) { "Base URL host must not be blank." }
        require(explicitPort == null || explicitPort in MIN_PORT..MAX_PORT) { "Base URL port is out of range." }
        require(basePath.isEmpty() || basePath.startsWith('/')) { "Base path must be empty or absolute." }
        require(!basePath.endsWith('/') || basePath == "/") { "Base path must be canonical." }
    }

    val effectivePort: Int = explicitPort ?: scheme.defaultPort
    val origin: UrlOrigin = UrlOrigin(scheme = scheme, host = host, effectivePort = effectivePort)
    val canonical: String = buildString {
        append(origin.canonical)
        if (basePath.isNotEmpty() && basePath != "/") {
            append(basePath)
        }
    }
}

sealed interface BaseUrlParseResult {
    data class Success(val value: CanonicalBaseUrl) : BaseUrlParseResult
    data class Failure(val reason: BaseUrlParseFailureReason) : BaseUrlParseResult
}

enum class BaseUrlParseFailureReason {
    MissingScheme,
    UnsupportedScheme,
    MissingHost,
    UserInfoNotAllowed,
    QueryNotAllowed,
    FragmentNotAllowed,
    InvalidPort,
    InvalidCharacters
}

@Suppress("ReturnCount")
fun parseCanonicalBaseUrl(input: String): BaseUrlParseResult {
    val trimmed = input.trim()
    if (trimmed.any(Char::isWhitespace)) return BaseUrlParseResult.Failure(BaseUrlParseFailureReason.InvalidCharacters)
    if ('?' in trimmed) return BaseUrlParseResult.Failure(BaseUrlParseFailureReason.QueryNotAllowed)
    if ('#' in trimmed) return BaseUrlParseResult.Failure(BaseUrlParseFailureReason.FragmentNotAllowed)

    val schemeSeparator = trimmed.indexOf("://")
    if (schemeSeparator <= 0) return BaseUrlParseResult.Failure(BaseUrlParseFailureReason.MissingScheme)

    val scheme = UrlScheme.parse(trimmed.substring(0, schemeSeparator))
        ?: return BaseUrlParseResult.Failure(BaseUrlParseFailureReason.UnsupportedScheme)
    val afterScheme = trimmed.substring(schemeSeparator + 3)
    val pathStart = afterScheme.indexOf('/').let { if (it == -1) afterScheme.length else it }
    val authority = afterScheme.substring(0, pathStart)
    val rawPath = afterScheme.substring(pathStart)

    if ('@' in authority) return BaseUrlParseResult.Failure(BaseUrlParseFailureReason.UserInfoNotAllowed)
    if (authority.isEmpty()) return BaseUrlParseResult.Failure(BaseUrlParseFailureReason.MissingHost)

    val hostAndPort = parseHostAndPort(authority)
        ?: return BaseUrlParseResult.Failure(BaseUrlParseFailureReason.InvalidPort)
    val host = hostAndPort.host.takeIf { it.isNotBlank() }
        ?: return BaseUrlParseResult.Failure(BaseUrlParseFailureReason.MissingHost)
    val canonicalPath = canonicalizeBasePath(rawPath)
        ?: return BaseUrlParseResult.Failure(BaseUrlParseFailureReason.InvalidCharacters)

    return BaseUrlParseResult.Success(
        CanonicalBaseUrl(
            scheme = scheme,
            host = host.lowercase(),
            explicitPort = hostAndPort.port,
            basePath = canonicalPath
        )
    )
}

private data class ParsedHostAndPort(val host: String, val port: Int?)

@Suppress("ReturnCount")
private fun parseHostAndPort(authority: String): ParsedHostAndPort? {
    if (authority.startsWith('[')) {
        val closingBracket = authority.indexOf(']')
        if (closingBracket <= 1) return null
        val host = authority.substring(1, closingBracket)
        val remainder = authority.substring(closingBracket + 1)
        val port = when {
            remainder.isEmpty() -> null
            remainder.startsWith(':') -> remainder.substring(1).toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }
                ?: return null
            else -> return null
        }
        return ParsedHostAndPort(host = host, port = port)
    }

    if (authority.count { it == ':' } > 1) return null
    val separator = authority.lastIndexOf(':')
    if (separator == -1) return ParsedHostAndPort(host = authority, port = null)

    val host = authority.substring(0, separator)
    val port = authority.substring(separator + 1).toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT } ?: return null
    return ParsedHostAndPort(host = host, port = port)
}

@Suppress("ReturnCount")
private fun canonicalizeBasePath(rawPath: String): String? {
    if (rawPath.isEmpty() || rawPath == "/") return ""
    if (!rawPath.startsWith('/')) return null
    return rawPath.trimEnd('/')
}

private fun String.forUrlAuthority(): String = if (':' in this) "[$this]" else this

private const val MIN_PORT = 1
private const val MAX_PORT = 65535
