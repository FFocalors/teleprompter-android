package com.zhy20.teleprompter.core.design

import android.animation.ValueAnimator
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/**
 * App-wide motion values mapped from the GSAP skill defaults.
 *
 * Motion is limited to transforms and opacity. Android's animator scale setting is respected so
 * users who disable system animations do not receive decorative transitions.
 */
object AppMotion {
    const val DefaultDurationMillis = 500
    const val PageExitDurationMillis = 90
    const val PageEnterDurationMillis = 180
    const val PageEnterDelayMillis = PageExitDurationMillis
    const val PressedScale = 0.97f
    const val PageEnterInitialScale = 0.985f

    /** GSAP power1.out: 1 - (1 - t)^2. */
    val Power1Out = Easing { fraction ->
        1f - (1f - fraction) * (1f - fraction)
    }

    /** GSAP power1.in: t^2. */
    val Power1In = Easing { fraction -> fraction * fraction }

    fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

    fun <T> defaultSpec(easing: Easing = Power1Out): FiniteAnimationSpec<T> =
        if (animationsEnabled()) {
            tween(durationMillis = DefaultDurationMillis, easing = easing)
        } else {
            snap()
        }

    fun <T> pageExitSpec(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) {
            tween(durationMillis = PageExitDurationMillis, easing = Power1In)
        } else {
            snap()
        }

    fun <T> pageEnterSpec(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) {
            tween(
                durationMillis = PageEnterDurationMillis,
                delayMillis = PageEnterDelayMillis,
                easing = Power1Out,
            )
        } else {
            snap()
        }
}
