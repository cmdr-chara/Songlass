package dev.cmdrchara.songlass.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowLayoutTest {
    private val viewport = WindowRect(0f, 0f, 800f, 900f)

    @Test fun unspecifiedGeometryPreservesTheExistingLayout() {
        assertNull(WindowLayout().panes(viewport, paired = true))
        assertNull(WindowLayout(Float.NaN, 900f).panes(viewport, paired = false))
        assertNull(WindowLayout(800f, Float.POSITIVE_INFINITY).panes(viewport, paired = false))
    }

    @Test fun bookFoldKeepsRelatedViewsOnSeparateSides() {
        val region = WindowReservedRegion(390f, -20f, 410f, 950f, WindowRegionKind.Division)
        val plan = WindowLayout(800f, 900f, regions = listOf(region)).panes(viewport, paired = true)!!
        assertEquals(WindowDivision.Vertical, plan.division)
        assertEquals(WindowRect(0f, 0f, 390f, 900f), plan.first)
        assertEquals(WindowRect(410f, 0f, 800f, 900f), plan.second)
        assertNull(plan.first.intersection(region.bounds))
        assertNull(plan.second!!.intersection(region.bounds))
    }

    @Test fun laptopFoldKeepsControlsBelowTheReservedStrip() {
        val region = WindowReservedRegion(-10f, 400f, 900f, 430f, WindowRegionKind.Division)
        val plan = WindowLayout(800f, 900f, regions = listOf(region)).panes(viewport, paired = true)!!
        assertEquals(WindowDivision.Horizontal, plan.division)
        assertEquals(WindowRect(0f, 0f, 800f, 400f), plan.first)
        assertEquals(WindowRect(0f, 430f, 800f, 900f), plan.second)
    }

    @Test fun singlePaneUsesTheLargerUnobstructedSide() {
        val layout = WindowLayout(800f, 900f, regions = listOf(WindowReservedRegion(200f, 0f, 220f, 900f, WindowRegionKind.Division)))
        val plan = layout.panes(viewport, paired = false)!!
        assertFalse(plan.paired)
        assertEquals(WindowRect(220f, 0f, 800f, 900f), plan.first)
    }

    @Test fun partialCameraFrameIsClippedAndDoesNotReceiveExtraMargins() {
        val camera = WindowReservedRegion(740f, -15f, 820f, 70f)
        val plan = WindowLayout(800f, 900f, regions = listOf(camera)).panes(viewport, paired = false)!!
        assertNull(plan.first.intersection(camera.bounds))
        assertEquals(WindowRect(0f, 0f, 740f, 900f), plan.first)
    }

    @Test fun allOcclusionsRemainOutsideTheChosenPane() {
        val regions = listOf(WindowReservedRegion(0f, 0f, 70f, 60f), WindowReservedRegion(750f, 0f, 800f, 100f))
        val plan = WindowLayout(800f, 900f, regions = regions).panes(viewport, paired = false)!!
        regions.forEach { assertNull(plan.first.intersection(it.bounds)) }
    }

    @Test fun playerOffsetsTranslateTheSameWindowRegionsIntoItsLocalSpace() {
        val layout = WindowLayout(800f, 900f, safeTop = 20f, safeBottom = 30f,
            regions = listOf(WindowReservedRegion(390f, 0f, 410f, 900f, WindowRegionKind.Division)))
        val player = WindowRect(24f, 120f, 776f, 860f)
        val plan = layout.panes(player, paired = true)!!
        assertEquals(WindowRect(0f, 0f, 366f, 740f), plan.first)
        assertEquals(WindowRect(386f, 0f, 752f, 740f), plan.second)
    }

    @Test fun fullyOpenedAndNarrowResizedWindowsDoNotRetainTheOldFold() {
        val open = WindowLayout(800f, 700f).panes(WindowRect(0f, 0f, 800f, 700f), paired = true, allowWidePair = true)!!
        assertTrue(open.paired)
        val narrow = WindowLayout(340f, 700f).panes(WindowRect(0f, 0f, 340f, 700f), paired = true, allowWidePair = true)!!
        assertFalse(narrow.paired)
        assertEquals(340f, narrow.first.width)
    }

    @Test fun invalidAndNonoverlappingRegionsCannotMoveContent() {
        val layout = WindowLayout(800f, 900f, regions = listOf(
            WindowReservedRegion(Float.NaN, 0f, 200f, 300f),
            WindowReservedRegion(900f, 0f, 1000f, 100f),
            WindowReservedRegion(200f, 100f, 100f, 200f),
        ))
        assertEquals(viewport, layout.panes(viewport, paired = false)!!.first)
    }

    @Test fun nativeWireFormatRejectsNonfiniteMalformedOrUnknownRegions() {
        val regions = WindowLayout.decodeRegions("d,390,0,410,900;o,-20,-10,40,50;o,NaN,0,100,20;x,0,0,5,5;o,1,2")
        assertEquals(2, regions.size)
        assertEquals(WindowRegionKind.Division, regions[0].kind)
        assertEquals(WindowReservedRegion(-20f, -10f, 40f, 50f), regions[1])
    }

    @Test fun sideBarsFollowSemanticEdgesWithoutOverlappingContent() {
        for (rtl in listOf(false, true)) for (edge in listOf(WindowBarEdge.Leading, WindowBarEdge.Trailing)) {
            val plan = WindowLayout(800f, 900f, barEdge = edge).chrome(viewport, rtl = rtl)!!
            val rail = plan.rail!!
            assertNull(rail.intersection(plan.content))
            if ((edge == WindowBarEdge.Leading) != rtl) assertTrue(rail.right < plan.content.left)
            else assertTrue(rail.left > plan.content.right)
        }
    }

    @Test fun cameraAndFoldNeverOverlapEitherNavigationOrContent() {
        val regions = listOf(WindowReservedRegion(0f, 0f, 40f, 80f),
            WindowReservedRegion(390f, 0f, 410f, 900f, WindowRegionKind.Division))
        val plan = WindowLayout(800f, 900f, barEdge = WindowBarEdge.Trailing, regions = regions).chrome(viewport)!!
        regions.forEach {
            assertNull(it.bounds.intersection(plan.content))
            assertNull(it.bounds.intersection(plan.rail!!))
        }
    }

    @Test fun laterOcclusionCannotDiscardTheOnlyRemainingFreePane() {
        val layout = WindowLayout(100f, 100f, regions = listOf(
            WindowReservedRegion(40f, 0f, 60f, 100f), WindowReservedRegion(0f, 0f, 40f, 100f),
        ))
        assertEquals(WindowRect(60f, 0f, 100f, 100f), layout.panes(WindowRect(0f, 0f, 100f, 100f), paired = false)!!.first)
    }

    @Test fun transformedPlayerUsesTheInverseViewScaleForTheWindowFold() {
        val native = WindowLayout(800f, 900f, regions = listOf(WindowReservedRegion(390f, 0f, 410f, 900f, WindowRegionKind.Division)))
        val local = native.inLocalCoordinates(800f, 900f) { x, y -> WindowPoint((x - 20f) * 2f, y * 2f) }
        assertEquals(740f, local.regions.single().left)
        assertEquals(780f, local.regions.single().right)
        assertEquals(40f, local.regions.single().bounds.width)
    }

    @Test fun centeredCameraKeepsTheFullWidthBelowIt() {
        val camera = WindowReservedRegion(380f, 0f, 420f, 60f)
        val plan = WindowLayout(800f, 900f, regions = listOf(camera)).panes(viewport, paired = false)!!
        assertEquals(WindowRect(0f, 60f, 800f, 900f), plan.first)
    }

    @Test fun ordinaryPortraitGeometryDoesNotChangeThePlayerMorph() {
        assertFalse(WindowLayout(400f, 850f, safeTop = 55f, safeBottom = 30f,
            regions = listOf(WindowReservedRegion(160f, 0f, 240f, 50f))).requiresAdaptivePlayer)
        assertTrue(WindowLayout(800f, 600f).requiresAdaptivePlayer)
    }
}
