package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AutoEqCatalogTest {
    private val revision = "7ae0f56d53074872b028649617a22bbb4232feb7"
    private val index = "# revision=$revision\n" +
        "Sennheiser HD 600\toratory1990\toratory1990/over-ear/Sennheiser%20HD%20600\n" +
        "Sony WH-1000XM4 (ANC On)\tRtings\tRtings/over-ear/Sony%20WH-1000XM4%20%28ANC%20On%29\n" +
        "Sony WH-1000XM4 (ANC Off)\tRtings\tRtings/over-ear/Sony%20WH-1000XM4%20%28ANC%20Off%29\n"
    private val body = "Preamp: -6.3 dB\nFilter 1: ON LSC Fc 105 Hz Gain 6.5 dB Q 0.70\nFilter 2: ON PK Fc 125 Hz Gain -2.7 dB Q 0.55\n"
    private fun entry() = AutoEqCatalog.parse(index).entries.first()
    private fun correction() = AutoEqCorrection(entry(), AutoEq.parse(body).filters)

    @Test fun searchesAcrossPunctuationAndKeepsVariantsDistinct() {
        val catalog = AutoEqCatalog.parse(index)
        assertEquals("Sennheiser HD 600", catalog.search("hd600").single().name)
        assertEquals(2, catalog.search("SONY wh1000xm4").size)
        assertTrue(catalog.search("xm4 ANC OFF").single().name.endsWith("Off)"))
        assertTrue(catalog.search("missing model").isEmpty())
        assertTrue(entry().profileUrl.endsWith("Sennheiser%20HD%20600/Sennheiser%20HD%20600%20ParametricEQ.txt"))
    }

    @Test fun rejectsPathsThatCouldEscapeThePinnedResults() {
        for (path in listOf("https://evil.test/x", "../over-ear/HD600", "a/b/%2e%2e", "a/b/%2Fsecret", "a/b/HD 600")) {
            assertFailsWith<IllegalArgumentException> { AutoEqCatalog.parse("# revision=$revision\nX\tSource\t$path") }
        }
        assertFailsWith<IllegalArgumentException> { AutoEqCatalog.parse(index + index.substringAfter('\n')) }
    }

    @Test fun cachesValidProfilesAcrossRepositoryInstances() = runTest {
        val store = InMemoryKeyValueStore()
        var downloads = 0
        val first = AutoEqRepository({ index }, { downloads++; body }, store)
        assertEquals(2, first.correction(entry()).filters.size)
        val offline = AutoEqRepository({ index }, { error("Must work offline") }, store)
        assertEquals(first.correction(entry()), offline.correction(entry()))
        assertEquals(1, downloads)
    }

    @Test fun rejectsPartialProfilesAndAllowsRetry() = runTest {
        var good = false
        val repo = AutoEqRepository({ index }, { if (good) body else body + "Filter 3: ON NOTCH Fc 500 Hz" }, InMemoryKeyValueStore())
        assertFailsWith<IllegalArgumentException> { repo.correction(entry()) }
        for (invalid in listOf(
            "Filter 1: ON PK Fc 25000 Hz Gain 1 dB Q 1", "Filter 1: ON PK Fc 100 Hz Gain 25 dB Q 1",
            "Filter 1: ON PK Fc 100 Hz Gain 1 dB Q 11", "Filter 1: ON PK Fc 100 Hz Gain 1 dB Q broken",
        )) {
            val strict = AutoEqRepository({ index }, { invalid }, InMemoryKeyValueStore())
            assertFailsWith<IllegalArgumentException> { strict.correction(entry()) }
        }
        good = true
        assertEquals(2, repo.correction(entry()).filters.size)
        assertFailsWith<IllegalArgumentException> { repo.correction(entry().copy(path = "evil/a/b")) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun cancelledDownloadDoesNotPopulateCache() = runTest {
        val store = InMemoryKeyValueStore()
        var waiting = true
        var calls = 0
        val repo = AutoEqRepository({ index }, { calls++; if (waiting) awaitCancellation() else body }, store)
        val job = launch { repo.correction(entry()) }
        runCurrent()
        job.cancel()
        job.join()
        waiting = false
        assertEquals(2, repo.correction(entry()).filters.size)
        assertEquals(2, calls)
    }

    @Test fun previewDoesNotAffectEqAndApplyingIsIdempotent() = runTest {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store)
        eq.setGain(1, 2.5)
        val before = eq.state.value
        val preview = AutoEqRepository({ index }, { body }, store).correction(entry())
        assertEquals(before, eq.state.value)
        assertTrue(eq.applyCatalogProfile(preview))
        assertTrue(eq.state.value.enabled)
        assertEquals(2.5, eq.state.value.gains[1])
        eq.applyCatalogProfile(preview)
        assertEquals(1, eq.state.value.profiles.size)
        assertEquals(eq.state.value.profile, Equalizer(store).state.value.profile)
    }

    @Test fun installedCorrectionStaysBrowsableWithoutDownloadCache() = runTest {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store)
        eq.applyCatalogProfile(correction())
        val repo = AutoEqRepository({ index }, { error("Installed correction must work offline") }, store, eq::catalogCorrection)
        assertEquals(correction(), repo.correction(entry()))
    }

    @Test fun outputBindingPreservesManualCurveAndRejectsStaleRoute() {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store)
        eq.applyPreset(EqPreset.LessBass)
        val headphones = EqOutput("bt:headphones", "Headphones")
        eq.setOutput(headphones)
        assertFalse(eq.applyCatalogProfile(correction(), EqOutput("bt:old", "Old headphones")))
        assertTrue(eq.state.value.profiles.isEmpty())
        assertTrue(eq.applyCatalogProfile(correction(), headphones))
        assertTrue(eq.state.value.usesOutputProfile)
        assertTrue(eq.state.value.enabled)
        assertNotNull(eq.state.value.profile)
        eq.setOutput(null)
        assertFalse(eq.state.value.enabled)
        assertNull(eq.state.value.profile)
        assertEquals(EqPreset.LessBass.gains, eq.state.value.gains)
        eq.setOutput(headphones)
        assertNotNull(eq.state.value.profile)
        eq.deleteProfile(eq.state.value.profile!!.id)
        assertNull(eq.state.value.outputProfile?.profileId)
    }
}
