package com.yamahamx.editor.data.repository

import com.yamahamx.editor.data.model.MultiCommonState
import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.PerformanceState
import com.yamahamx.editor.data.model.SavedPerformance
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PerformanceStorageRepositoryTest {

    private fun createSamplePerformance(name: String, tag: String): SavedPerformance {
        val parts = List(16) { idx ->
            MultiPartState(
                partIndex = idx,
                volume = 100,
                pan = 64
            )
        }
        val state = PerformanceState(
            parts = parts,
            common = MultiCommonState(performanceName = name)
        )
        return SavedPerformance.fromState(
            name = name,
            categoryTag = tag,
            state = state
        )
    }

    @Test
    fun testParsePerformancesJson_validJsonArray() {
        val p1 = createSamplePerformance("Song A", "Rock")
        val p2 = createSamplePerformance("Song B", "Pop")

        val jsonArray = JSONArray()
        jsonArray.put(p1.toJson())
        jsonArray.put(p2.toJson())
        val jsonString = jsonArray.toString()

        val parsed = PerformanceStorageRepository.parsePerformancesJson(jsonString)

        assertNotNull(parsed)
        assertEquals(2, parsed?.size)
        assertEquals("Song A", parsed?.get(0)?.name)
        assertEquals("Rock", parsed?.get(0)?.categoryTag)
        assertEquals("Song B", parsed?.get(1)?.name)
        assertEquals("Pop", parsed?.get(1)?.categoryTag)
    }

    @Test
    fun testParsePerformancesJson_invalidOrEmptyJson() {
        assertNull(PerformanceStorageRepository.parsePerformancesJson(""))
        assertNull(PerformanceStorageRepository.parsePerformancesJson("   "))
        assertNull(PerformanceStorageRepository.parsePerformancesJson("[]"))
        assertNull(PerformanceStorageRepository.parsePerformancesJson("invalid json text"))
        assertNull(PerformanceStorageRepository.parsePerformancesJson("{ \"name\": \"NotAnArray\" }"))
    }

    @Test
    fun testMergePerformances_mergeStrategy() {
        val existing1 = createSamplePerformance("Existing 1", "Geral")
        val existing2 = createSamplePerformance("Existing 2", "Geral")
        val existingList = listOf(existing1, existing2)

        val imported1 = createSamplePerformance("Imported 1", "Rock")
        val imported2 = createSamplePerformance("Imported 2", "Igreja")
        val importedList = listOf(imported1, imported2)

        val merged = PerformanceStorageRepository.mergePerformances(
            existing = existingList,
            imported = importedList,
            replaceAll = false
        )

        // Total should be 2 + 2 = 4
        assertEquals(4, merged.size)

        // Imported items are prepended
        assertEquals("Imported 1", merged[0].name)
        assertEquals("Imported 2", merged[1].name)
        assertEquals("Existing 1", merged[2].name)
        assertEquals("Existing 2", merged[3].name)

        // Ensure newly imported items received fresh UUIDs
        assertNotEquals(imported1.id, merged[0].id)
        assertNotEquals(imported2.id, merged[1].id)

        // Ensure all 4 IDs in result are completely unique
        val allIds = merged.map { it.id }.toSet()
        assertEquals(4, allIds.size)
    }

    @Test
    fun testMergePerformances_replaceAllStrategy() {
        val existing1 = createSamplePerformance("Old Performance 1", "Geral")
        val existing2 = createSamplePerformance("Old Performance 2", "Rock")
        val existing3 = createSamplePerformance("Old Performance 3", "Pop")
        val existingList = listOf(existing1, existing2, existing3)

        val imported1 = createSamplePerformance("New Preset Alpha", "Igreja")
        val imported2 = createSamplePerformance("New Preset Beta", "Ensaio")
        val importedList = listOf(imported1, imported2)

        val replaced = PerformanceStorageRepository.mergePerformances(
            existing = existingList,
            imported = importedList,
            replaceAll = true
        )

        // Replaced result should only contain the 2 imported items
        assertEquals(2, replaced.size)
        assertEquals("New Preset Alpha", replaced[0].name)
        assertEquals("New Preset Beta", replaced[1].name)

        // Old performances should be gone
        assertTrue(replaced.none { it.name.startsWith("Old Performance") })

        // Ensure newly assigned UUIDs
        assertNotEquals(imported1.id, replaced[0].id)
        assertNotEquals(imported2.id, replaced[1].id)
        assertNotEquals(replaced[0].id, replaced[1].id)
    }
}
