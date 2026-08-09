package com.zhy20.teleprompter.core.design

import org.junit.Assert.assertEquals
import org.junit.Test

class AppMotionTest {
    @Test
    fun `power1 out matches gsap quadratic easing`() {
        assertEquals(0f, AppMotion.Power1Out.transform(0f), 0.0001f)
        assertEquals(0.4375f, AppMotion.Power1Out.transform(0.25f), 0.0001f)
        assertEquals(0.75f, AppMotion.Power1Out.transform(0.5f), 0.0001f)
        assertEquals(1f, AppMotion.Power1Out.transform(1f), 0.0001f)
    }

    @Test
    fun `power1 in matches gsap quadratic easing`() {
        assertEquals(0f, AppMotion.Power1In.transform(0f), 0.0001f)
        assertEquals(0.25f, AppMotion.Power1In.transform(0.5f), 0.0001f)
        assertEquals(1f, AppMotion.Power1In.transform(1f), 0.0001f)
    }
}
