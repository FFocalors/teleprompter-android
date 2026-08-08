package com.zhy20.teleprompter.remote.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePrompterSnapshotTest {

    @Test
    fun normalizedClampsProgressIntoRange() {
        val snapshot = remotePrompterSnapshot(
            revision = 1,
            surface = RemotePrompterSurface.Playing,
            progress = 1.4f,
        )
        assertEquals(1f, snapshot.progress, 0f)

        val negative = remotePrompterSnapshot(
            revision = 2,
            surface = RemotePrompterSurface.Playing,
            progress = -0.5f,
        )
        assertEquals(0f, negative.progress, 0f)
    }

    @Test
    fun normalizedTreatsNonFiniteProgressAsZero() {
        val snapshot = remotePrompterSnapshot(
            revision = 1,
            surface = RemotePrompterSurface.Playing,
            progress = Float.NaN,
        )
        assertEquals(0f, snapshot.progress, 0f)
    }

    @Test
    fun normalizedClampsNegativeDurations() {
        val snapshot = remotePrompterSnapshot(
            revision = 1,
            surface = RemotePrompterSurface.Playing,
            elapsedTimeMillis = -100L,
            remainingTimeMillis = -5L,
            countdownSecondsRemaining = -3,
        )
        assertEquals(0L, snapshot.elapsedTimeMillis)
        assertEquals(0L, snapshot.remainingTimeMillis)
        assertEquals(0, snapshot.countdownSecondsRemaining)
    }

    @Test
    fun nearbyTextIsTruncatedToProtocolMaximum() {
        val longText = "字".repeat(500)
        val snapshot = remotePrompterSnapshot(
            revision = 1,
            surface = RemotePrompterSurface.Playing,
            nearbyText = longText,
        )
        assertEquals(140, snapshot.nearbyText?.length)
    }

    @Test
    fun speedMultiplierIsClampedToReasonableRange() {
        val snapshot = remotePrompterSnapshot(
            revision = 1,
            surface = RemotePrompterSurface.Playing,
            speedMultiplier = 99f,
        )
        assertEquals(10f, snapshot.speedMultiplier, 0f)
    }

    @Test
    fun revisionIsPreservedThroughNormalization() {
        val snapshot = remotePrompterSnapshot(
            revision = 42,
            surface = RemotePrompterSurface.Setup,
            scriptId = "7",
        )
        assertEquals(42L, snapshot.revision)
        assertEquals("7", snapshot.scriptId)
    }

    @Test
    fun helperTruncatesViaNormalizationWithoutDoubleWork() {
        val snapshot = remotePrompterSnapshot(
            revision = 1,
            surface = RemotePrompterSurface.Playing,
            nearbyText = "a".repeat(200),
        )
        assertTrue(snapshot.nearbyText!!.length <= 140)
    }
}
