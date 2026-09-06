package com.clashfit.engine.summary

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The unit a gym weight is shown in, which is never the unit it is stored in.
 *
 * Every weight in the log is kilograms on disk. This converts at the edge, in one place, so that
 * switching between kilograms and pounds is a change of reading and not a reinterpretation of
 * history. The alternative — a unit column on every row — means every comparison between two sets
 * has to remember to convert, and the first one that forgets tells somebody they got weaker.
 */
enum class WeightUnit(val label: String, val perKg: Float) {
    KG("kg", 1f),
    LBS("lbs", 2.20462f),
    ;

    fun fromKg(kg: Float): Float = kg * perKg

    fun toKg(shown: Float): Float = shown / perKg

    /**
     * How the number appears in a field or a tile.
     *
     * Whole numbers lose their decimal, because a plate is twenty kilograms and not twenty point
     * zero, and everything else keeps exactly one — two decimals of a pound conversion is noise
     * from arithmetic rather than precision anybody weighed.
     */
    fun show(kg: Float): String {
        val v = fromKg(kg)
        return if (abs(v - v.roundToInt()) < 0.05f) "${v.roundToInt()}" else "%.1f".format(v)
    }

    companion object {
        fun of(lbs: Boolean): WeightUnit = if (lbs) LBS else KG
    }
}
