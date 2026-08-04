package com.zhy20.teleprompter.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrompterLayoutTest {
    @Test
    fun statusBandIsExcludedFromContentViewportAndGuide() {
        val metrics = PrompterLayoutCalculator.calculate(
            viewportWidthPx = 1_000f,
            viewportHeightPx = 2_000f,
            statusBandHeightPx = 120f,
            textMeasuredHeightPx = 600f,
            guidePosition = .25f,
        )

        assertEquals(120f, metrics.contentTopPx, 0f)
        assertEquals(1_880f, metrics.contentViewportHeightPx, 0f)
        assertEquals(590f, metrics.guidePositionPx, 0.01f)
        assertEquals(1_541.6f, metrics.initialOffsetPx, 0.01f)
        assertTrue(metrics.requiresScrolling)
    }

    @Test
    fun longTextUsesOnlyContentViewportForStartAndFinishOffsets() {
        val metrics = PrompterLayoutCalculator.calculate(
            viewportWidthPx = 1_000f,
            viewportHeightPx = 2_000f,
            statusBandHeightPx = 100f,
            textMeasuredHeightPx = 2_000f,
            guidePosition = .25f,
        )

        assertTrue(metrics.requiresScrolling)
        assertEquals(1_558f, metrics.initialOffsetPx, 0.01f)
        assertEquals(-727f, metrics.finalOffsetPx, 0.01f)
        assertEquals(1_273f, metrics.finalOffsetPx + metrics.textMeasuredHeightPx, 0.01f)
        assertTrue(metrics.guidePositionPx >= metrics.contentTopPx)
    }
}
