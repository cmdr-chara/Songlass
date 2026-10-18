package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EqualizerTest {
    private fun assertNear(expected: Double, actual: Double, tolerance: Double = 0.1) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun peakingFilterHasItsGainAtTheCentreAndNoneFarAway() {
        val biquad = EqFilter(FilterType.Peaking, 1_000.0, 6.0, 1.41).coefficients(48_000)
        assertNear(6.0, biquad.responseDb(1_000.0, 48_000))
        assertNear(0.0, biquad.responseDb(20.0, 48_000))
    }

    @Test
    fun shelvesBoostTheirSideOnly() {
        val low = EqFilter(FilterType.LowShelf, 100.0, 5.0, 0.71).coefficients(44_100)
        assertNear(5.0, low.responseDb(20.0, 44_100), 0.3)
        assertNear(0.0, low.responseDb(10_000.0, 44_100))
        val high = EqFilter(FilterType.HighShelf, 8_000.0, -4.0, 0.71).coefficients(44_100)
        assertNear(-4.0, high.responseDb(19_000.0, 44_100), 0.3)
        assertNear(0.0, high.responseDb(100.0, 44_100))
    }

    @Test
    fun headroomKeepsTheLoudestBoostAtZero() {
        val chain = EqChain.withHeadroom(GraphicEq.filters(EqPreset.MoreBass.gains))
        assertTrue(chain.preampDb < -5.0)
        assertNear(0.0, chain.curve(256).max(), 0.05)
        // Cuts need no headroom.
        assertEquals(0.0, EqChain.withHeadroom(GraphicEq.filters(EqPreset.LessBass.gains)).preampDb)
        assertEquals(EqChain.Flat, EqChain.withHeadroom(GraphicEq.filters(GraphicEq.flat)))
    }

    @Test
    fun parsesAutoEqParametricFiles() {
        val text = """
            Preamp: -6.4 dB
            Filter 1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
            Filter 2: ON PK Fc 2084 Hz Gain -3.1 dB Q 1.41
            Filter 3: OFF PK Fc 5000 Hz Gain 2.0 dB Q 2.00
            Filter 4: ON HP Fc 20 Hz
            Filter 5: ON HSC Fc 10000 Hz Gain 1.2 dB Q 0.70
        """.trimIndent()
        val parsed = AutoEq.parse(text)
        assertEquals(
            listOf(
                EqFilter(FilterType.LowShelf, 105.0, 5.5, 0.7),
                EqFilter(FilterType.Peaking, 2084.0, -3.1, 1.41),
                EqFilter(FilterType.HighShelf, 10_000.0, 1.2, 0.7),
            ),
            parsed.filters,
        )
        assertEquals(2, parsed.skipped)
        assertEquals("Sennheiser HD 600", AutoEq.nameFromFile("Sennheiser HD 600 ParametricEQ.txt"))
    }

    @Test
    fun bandsPresetsAndProfilesPersist() {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store) { 7 }
        eq.applyPreset(EqPreset.Voice)
        assertEquals(EqPreset.Voice, eq.state.value.preset)
        eq.setGain(0, 13.3)
        assertNull(eq.state.value.preset)
        assertEquals(12.0, eq.state.value.gains[0])
        val profile = eq.importProfile("My Phones ParametricEQ.txt", "Filter 1: ON PK Fc 100 Hz Gain -2 dB Q 1")!!

        val restored = Equalizer(store).state.value
        assertTrue(restored.enabled)
        assertEquals(12.0, restored.gains[0])
        assertEquals(profile, restored.profile)
        assertEquals("My Phones", restored.profile?.name)
        // Flat bands are left out: 8 bands with gain plus the profile filter.
        assertEquals(9, restored.chain.filters.size)
    }

    @Test
    fun offIsFlatAndDeletingTheProfileDeselectsIt() {
        val eq = Equalizer()
        eq.applyPreset(EqPreset.MoreTreble)
        assertEquals(EqChain.Flat, eq.state.value.chain)
        assertNull(eq.importProfile("empty.txt", "Preamp: 0 dB"))
        val profile = eq.importProfile("a.txt", "Filter 1: ON PK Fc 100 Hz Gain -2 dB Q 1")!!
        eq.deleteProfile(profile.id)
        assertNull(eq.state.value.profileId)
        assertTrue(eq.state.value.profiles.isEmpty())
    }
}
