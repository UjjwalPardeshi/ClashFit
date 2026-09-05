package com.clashfit.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The three things the interface itself is allowed to say through the motor.
 *
 * A fight already speaks through it constantly — every rep ticks, a shallow rep thuds, a combo
 * milestone pulses — and then you finish the set, tap back into the app, and the phone goes
 * completely silent. That gap is felt long before it is noticed: the game is alive in the hand and
 * the app around it is dead in the hand.
 *
 * Three, and no more. Haptics stop meaning anything the moment everything buzzes, so this is a tap
 * for a deliberate press, a slightly firmer one for changing where you are, and a pattern reserved
 * for the moment something is *earned*. Everything else stays quiet on purpose.
 *
 * All three honour the Haptics setting, because they route through the same [com.clashfit.audio.Haptics]
 * the fight uses. The default here does nothing, so a preview, a screenshot test or a Composable
 * rendered outside the app never has to know this exists.
 */
interface UiHaptics {

    /** A deliberate press: a primary button, a card that opens something. */
    fun tap()

    /** Where you are changed: a tab, a filter, a segment. Firmer than [tap]. */
    fun select()

    /** Something was earned — a badge, a level, a voucher. The only pattern in the set. */
    fun reward()

    /** For previews, tests, and anything composed outside the app. */
    object None : UiHaptics {
        override fun tap() = Unit
        override fun select() = Unit
        override fun reward() = Unit
    }
}

val LocalUiHaptics = staticCompositionLocalOf<UiHaptics> { UiHaptics.None }
