package dev.cmdrchara.songlass.designsystem

import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerUtilityLayoutTest {
    private fun weights(badge: String? = "Track end"): List<Float> = List(5) { index ->
        PlayerUtilityAction("Action $index", AppIcons.Timer, false, {}, badge = badge.takeIf { index == 0 }).layoutWeight
    }

    @Test
    fun normalPlayerKeepsAllFiveActionsTogetherIncludingTheLiveTimer() {
        for (spacing in listOf(3f, 4f, 8f)) for (badge in listOf(null, "1 min", "Track end", "Fine brano")) {
            assertEquals(listOf(5), playerUtilityRowSizes(weights(badge), 327f, spacing, 48f))
        }
    }

    @Test
    fun narrowingThePlayerBalancesRowsInsteadOfLeavingALoneLastAction() {
        assertEquals(listOf(3, 2), playerUtilityRowSizes(weights(), 240f, 8f, 48f))
        assertEquals(listOf(2, 3), playerUtilityRowSizes(weights(), 160f, 8f, 48f))
    }

    @Test
    fun largeTextMovesTheWiderTimerIntoTheShorterRow() {
        assertEquals(listOf(2, 3), playerUtilityRowSizes(weights(), 327f, 8f, 96f))
        assertEquals(listOf(3, 2), playerUtilityRowSizes(weights(null), 327f, 8f, 96f))
    }

    @Test
    fun bothLiveBadgeSpaceAndTheActualThemeGapsAffectWhetherARowFits() {
        assertEquals(listOf(5), playerUtilityRowSizes(weights(null), 296f, 8f, 48f))
        assertEquals(listOf(5), playerUtilityRowSizes(weights(), 296f, 3f, 48f))
        assertEquals(listOf(3, 2), playerUtilityRowSizes(weights(), 296f, 8f, 48f))
    }

    @Test
    fun everyActionStaysInOrderAndEachWeightedCellHasItsRequiredWidth() {
        for (badge in listOf(null, "90 min", "Fine brano")) for (width in listOf(180f, 224f, 272f, 327f, 480f)) {
            for (fontScale in listOf(1f, 1.3f, 1.5f, 2f)) for (spacing in listOf(3f, 4f, 8f)) {
                val actionWeights = weights(badge)
                val minimumCellWidth = 48f * fontScale
                val rows = playerUtilityRowSizes(actionWeights, width, spacing, minimumCellWidth)
                val indices = mutableListOf<Int>()
                var start = 0
                for (size in rows) {
                    assertTrue(size > 0)
                    val row = actionWeights.subList(start, start + size)
                    val unitWidth = (width - spacing * (size - 1)) / row.sum()
                    assertTrue(unitWidth + 0.001f >= minimumCellWidth,
                        "A weighted target must fit at width=$width, fontScale=$fontScale, spacing=$spacing: $rows")
                    indices += start until start + size
                    start += size
                }
                assertEquals(actionWeights.indices.toList(), indices)
            }
        }
    }

    @Test
    fun balancedAlternativesAvoidSingletonRowsForLongerActionListsToo() {
        for (count in 4..9) {
            val rows = playerUtilityRowSizes(List(count) { 1f }, 160f, 8f, 48f)
            assertTrue(rows.none { it == 1 }, "A balanced alternative fits for $count actions: $rows")
            assertTrue(rows.max() - rows.min() <= 1)
            assertEquals(count, rows.sum())
        }
    }

    @Test
    fun emptyAndSingleActionListsRemainComplete() {
        assertEquals(emptyList(), playerUtilityRowSizes(emptyList(), 327f, 8f, 48f))
        assertEquals(listOf(1), playerUtilityRowSizes(listOf(1f), 48f, 8f, 48f))
    }
}
