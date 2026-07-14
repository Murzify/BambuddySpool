package com.murzify.bambuddyspool.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CanonicalBaseUrlTest {

    @Test
    fun canonicalizesOriginAndBasePathWithoutJvmUrlTypes() {
        val result = assertIs<BaseUrlParseResult.Success>(
            parseCanonicalBaseUrl("HTTPS://Example.Local:443/reverse/proxy/")
        )

        assertEquals(UrlScheme.Https, result.value.scheme)
        assertEquals("example.local", result.value.host)
        assertEquals(443, result.value.explicitPort)
        assertEquals(443, result.value.effectivePort)
        assertEquals("/reverse/proxy", result.value.basePath)
        assertEquals("https://example.local/reverse/proxy", result.value.canonical)
        assertEquals("https://example.local", result.value.origin.canonical)
    }

    @Test
    fun rejectsUnsupportedAndSecretBearingUrlForms() {
        assertEquals(
            BaseUrlParseFailureReason.UnsupportedScheme,
            assertIs<BaseUrlParseResult.Failure>(parseCanonicalBaseUrl("ftp://example.local")).reason
        )
        assertEquals(
            BaseUrlParseFailureReason.UserInfoNotAllowed,
            assertIs<BaseUrlParseResult.Failure>(parseCanonicalBaseUrl("https://user:pass@example.local")).reason
        )
        assertEquals(
            BaseUrlParseFailureReason.QueryNotAllowed,
            assertIs<BaseUrlParseResult.Failure>(parseCanonicalBaseUrl("https://example.local/api?token=value")).reason
        )
        assertEquals(
            BaseUrlParseFailureReason.FragmentNotAllowed,
            assertIs<BaseUrlParseResult.Failure>(parseCanonicalBaseUrl("https://example.local/api#frag")).reason
        )
        assertEquals(
            BaseUrlParseFailureReason.MissingHost,
            assertIs<BaseUrlParseResult.Failure>(parseCanonicalBaseUrl("https://")).reason
        )
    }

    @Test
    fun rejectsOversizedOrUnsafeHostMetadata() {
        assertEquals(
            BaseUrlParseFailureReason.TooLong,
            assertIs<BaseUrlParseResult.Failure>(parseCanonicalBaseUrl("https://" + "a".repeat(2_041))).reason
        )
        assertEquals(
            BaseUrlParseFailureReason.InvalidCharacters,
            assertIs<BaseUrlParseResult.Failure>(parseCanonicalBaseUrl("https://example.local%2Fevil")).reason
        )
        assertEquals(
            BaseUrlParseFailureReason.InvalidCharacters,
            assertIs<BaseUrlParseResult.Failure>(parseCanonicalBaseUrl("https://-invalid.example")).reason
        )
        assertEquals(
            BaseUrlParseFailureReason.InvalidCharacters,
            assertIs<BaseUrlParseResult.Failure>(parseCanonicalBaseUrl("https://[::::]")).reason
        )
    }

    @Test
    fun supportsCustomPortsAndIpv6Literals() {
        val result = assertIs<BaseUrlParseResult.Success>(parseCanonicalBaseUrl("http://[::1]:8080/base"))

        assertEquals("::1", result.value.host)
        assertEquals(8080, result.value.effectivePort)
        assertEquals("http://[::1]:8080/base", result.value.canonical)
        assertEquals("http://[::1]:8080", result.value.origin.canonical)
    }
}
