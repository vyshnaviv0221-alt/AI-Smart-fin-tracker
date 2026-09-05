package com.example.aismartexpensetracker.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Design tokens.
 *
 * Every spacing, radius and motion value in the app comes from here, so each
 * one is a deliberate choice that can be defended rather than a number typed
 * at the call site. Nine screens previously re-declared their own palette and
 * ad-hoc paddings; this replaces that.
 */

/** 4pt base scale. Nothing in the UI should use a raw dp that isn't here. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    /** Clearance so a scrolling list can pass under the floating action button. */
    val fabClearance = 96.dp
}

object Radius {
    val chip = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(18.dp)
    val sheet = RoundedCornerShape(28.dp)
    val bar = RoundedCornerShape(999.dp)
}

object Elevation {
    /** Resting cards: a soft lift, not a hard drop shadow. */
    val card = 1.dp
    val cardPressed = 0.dp
    val fab = 6.dp
}

/**
 * Motion.
 *
 * Apple describes springs with two designer-facing numbers -- damping ratio
 * (overshoot) and response (how fast it reaches the target) -- rather than
 * mass/stiffness/damping. Compose exposes dampingRatio and stiffness, so the
 * mapping is:
 *
 *   damping 1.0  -> DampingRatioNoBouncy   (critically damped, no overshoot)
 *   damping 0.8  -> DampingRatioLowBouncy  (a little overshoot)
 *   response ~0.4s -> StiffnessMediumLow
 *   response ~0.25s -> StiffnessMedium
 *
 * The rule that matters: default to NO bounce. Overshoot is earned only when
 * the gesture itself carried momentum -- a flick or a drag release. A menu
 * that merely appeared should not wobble.
 *
 * Springs are used rather than tween/keyframes because they animate from the
 * value currently on screen, which is what makes an animation interruptible:
 * grab a moving element and it redirects from where it actually is instead of
 * jumping to a logical start value.
 */
object Motion {
    /** The default for almost everything: settles gracefully, never bounces. */
    fun <T> standard() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Snappier, for small immediate feedback like a press scale. */
    fun <T> quick() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Earned overshoot: only after momentum, or when something lands/commits. */
    fun <T> playful() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Slow, deliberate reveal for large surfaces (charts, progress fills). */
    fun <T> gentle() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** How far a pressable surface shrinks. Small enough to feel, not to distract. */
    const val PRESS_SCALE = 0.972f
}
