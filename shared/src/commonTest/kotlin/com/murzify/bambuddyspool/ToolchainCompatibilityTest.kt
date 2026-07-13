package com.murzify.bambuddyspool

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Entity
private data class ToolchainRoomEntity(@PrimaryKey val id: Long)

@Serializable
private data class ToolchainPayload(val value: String)

class ToolchainCompatibilityTest {

    @Test
    fun pinnedCommonDependenciesCompileAndExecute() {
        val payload = ToolchainPayload("compatible")
        val encoded = Json.encodeToString(payload)
        val decoded = Json.decodeFromString<ToolchainPayload>(encoded)
        val preferenceKey = stringPreferencesKey("toolchain-smoke")
        val entity = ToolchainRoomEntity(id = 23)
        val client = HttpClient()

        assertEquals(payload, decoded)
        assertEquals("toolchain-smoke", preferenceKey.name)
        assertEquals(23, entity.id)
        client.close()
    }
}
