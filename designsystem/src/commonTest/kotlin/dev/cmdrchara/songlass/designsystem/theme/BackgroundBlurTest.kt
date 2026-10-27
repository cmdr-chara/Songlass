package dev.cmdrchara.songlass.designsystem.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackgroundBlurTest {
    @Test
    fun defaultKeepsTheReferenceRadiiExactly() {
        assertEquals(24.dp, BlurStrength.Default.scale(24.dp))
        assertEquals(16.dp, BlurStrength.Default.scale(16.dp))
        assertEquals(1f, BlurStrength.Default.radiusScale)
        assertTrue(BlurStrength.Default.blurred)
    }

    @Test
    fun lowAndHighOnlyScaleTheRadius() {
        assertEquals(8.dp, BlurStrength.Low.scale(16.dp))
        assertEquals(12.dp, BlurStrength.Low.scale(24.dp))
        assertEquals(24.dp, BlurStrength.High.scale(16.dp))
        assertEquals(36.dp, BlurStrength.High.scale(24.dp))
    }

    @Test
    fun offDisablesTheBlurLayerInsteadOfUsingAZeroRadius() {
        assertFalse(BlurStrength.Off.blurred)
        assertEquals(0.dp, BlurStrength.Off.scale(24.dp))
        assertEquals(0.dp, BlurStrength.Off.scale(16.dp))
    }

    @Test
    fun onlyOffDisablesTheBackdropAndTheScaleIsStable() {
        assertEquals(listOf(BlurStrength.Off), BlurStrength.entries.filterNot { it.blurred })
        assertEquals(listOf(0f, 0.5f, 1f, 1.5f), BlurStrength.entries.map { it.radiusScale })
        assertEquals(BlurStrength.Default, BlurStrength.entries.first { it.radiusScale == 1f })
    }
}
