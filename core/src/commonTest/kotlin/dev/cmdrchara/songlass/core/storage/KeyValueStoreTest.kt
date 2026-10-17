package dev.cmdrchara.songlass.core.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyValueStoreTest {

    @Test
    fun stringListRoundTripsIncludingSpacesAndCommas() {
        val store = InMemoryKeyValueStore()
        store.putStringList("k", listOf("a b", "c,d", "42"))
        assertEquals(listOf("a b", "c,d", "42"), store.getStringList("k"))
    }

    @Test
    fun emptyAndMissingListsAreEmpty() {
        val store = InMemoryKeyValueStore()
        assertEquals(emptyList(), store.getStringList("missing"))
        store.putStringList("k", emptyList())
        assertEquals(emptyList(), store.getStringList("k"))
    }

    @Test
    fun puttingNullRemoves() {
        val store = InMemoryKeyValueStore()
        store.putString("k", "v")
        store.putString("k", null)
        assertNull(store.getString("k"))
    }
}
