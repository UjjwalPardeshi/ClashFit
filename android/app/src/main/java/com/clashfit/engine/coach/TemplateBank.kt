package com.clashfit.engine.coach

import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.CoachSource
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.SetTelemetry

/** Deterministic template bank, keyed on (fatigue_band, worst_reason, trend). */
object TemplateBank {
    private data class CoachTemplate(
        val band: FatigueBand,
        val reason: String,
        val trend: SetTelemetry.Trend,
        val line: String,
    )

    private val COACH = listOf(
        CoachTemplate(FatigueBand.FRESH, "*", SetTelemetry.Trend.IMPROVING, "Clean — {combo_reps} in a row. It hasn't felt anything yet."),
        CoachTemplate(FatigueBand.FRESH, "depth", SetTelemetry.Trend.IMPROVING, "You're strong enough to go lower. Reset and drive deeper."),
        CoachTemplate(FatigueBand.FRESH, "tempo", SetTelemetry.Trend.IMPROVING, "You're rushing the way down. Give it a full second."),
        CoachTemplate(FatigueBand.FRESH, "alignment", SetTelemetry.Trend.IMPROVING, "Watch the knee track on rep {worst_index}. Everything else was clean."),
        CoachTemplate(FatigueBand.FRESH, "*", SetTelemetry.Trend.FLAT, "Holding at {form_mean_pct} percent across {reps} reps. Keep it there."),

        CoachTemplate(FatigueBand.WORKING, "depth", SetTelemetry.Trend.DECLINING, "Depth is slipping — {depth_drop_cm} centimetres short of your first reps. Reset and go lower."),
        CoachTemplate(FatigueBand.WORKING, "depth", SetTelemetry.Trend.IMPROVING, "Consistent depth across {reps} reps. That's the hard part."),
        CoachTemplate(FatigueBand.WORKING, "rom", SetTelemetry.Trend.IMPROVING, "Range is down {rom_loss_pct} percent. Shorten the set before the form goes."),
        CoachTemplate(FatigueBand.WORKING, "tempo", SetTelemetry.Trend.IMPROVING, "You're speeding up as you tire. Slow the eccentric back down."),
        CoachTemplate(FatigueBand.WORKING, "alignment", SetTelemetry.Trend.IMPROVING, "Knees drifted on the last two. Push them out as you stand."),
        CoachTemplate(FatigueBand.WORKING, "*", SetTelemetry.Trend.IMPROVING, "That got better as you went — {form_last3_pct} percent on the last three."),
        CoachTemplate(FatigueBand.WORKING, "*", SetTelemetry.Trend.FLAT, "{reps} reps at {form_mean_pct} percent. Velocity is down {velocity_loss_pct} percent."),

        CoachTemplate(FatigueBand.FADING, "depth", SetTelemetry.Trend.IMPROVING, "Real fatigue now — {depth_drop_cm} centimetres of depth gone."),
        CoachTemplate(FatigueBand.FADING, "rom", SetTelemetry.Trend.IMPROVING, "Range is down {rom_loss_pct} percent since rep one. Finish it."),
        CoachTemplate(FatigueBand.FADING, "tempo", SetTelemetry.Trend.IMPROVING, "You're dropping into the bottom instead of controlling it. Control the descent."),
        CoachTemplate(FatigueBand.FADING, "alignment", SetTelemetry.Trend.IMPROVING, "Form is going before your strength is. Keep it honest."),
        CoachTemplate(FatigueBand.FADING, "*", SetTelemetry.Trend.FLAT, "Velocity is down {velocity_loss_pct} percent — that's the set talking."),

        CoachTemplate(FatigueBand.GASSED, "depth", SetTelemetry.Trend.IMPROVING, "You've lost {depth_drop_cm} centimetres and that's honest. Finish it."),
        CoachTemplate(FatigueBand.GASSED, "*", SetTelemetry.Trend.FLAT, "That's real fatigue, not weakness. Four more reps ends this."),
        CoachTemplate(FatigueBand.GASSED, "*", SetTelemetry.Trend.DECLINING, "Velocity is down {velocity_loss_pct} percent and you've done the work."),
    )

    private val BOSS = listOf(
        "Your knees are negotiating. I do not negotiate.",
        "{depth_drop_cm} centimetres. That is the distance between us.",
        "You kept the pace. Briefly.",
        "I have counted every one of them. So has the floor.",
        "Your first three reps were a different person.",
        "Slower, and again. I have time.",
        "Range is a promise you stopped keeping.",
        "You are getting quicker. That is not the same as getting better.",
        "Rest. I will still be here at {boss_hp_pct} percent.",
        "That one counted. Barely.",
        "Your tempo is drifting. Mine does not.",
        "Something in you gave up at rep {worst_index}. Find it.",
        "You are tired and I am a machine. Guess how this ends.",
        "Fine. That one hurt.",
    )

    private val ASYM = mapOf(
        "coach" to "Your {weaker_side} side moved through {asymmetry_pct} percent less range than the other, across the whole set.",
        "boss" to "One of your sides is carrying the other. I noticed.",
    )

    private val SPECIAL = mapOf(
        "zero" to mapOf(
            "coach" to "Nothing counted that time. Check your framing and go again.",
            "boss" to "I felt nothing.",
        ),
        "first" to mapOf(
            "coach" to "Baseline set. {reps} reps at {form_mean_pct} percent — that's the number everything else is measured against.",
            "boss" to "Noted. I will remember that number.",
        ),
        "lowHp" to mapOf(
            "coach" to "It's at {boss_hp_pct} percent. One more set.",
            "boss" to "You are closer than I would like.",
        ),
    )

    /** Deterministic template selection based on telemetry. Boss line rotates by set index + reps. */
    fun templateFor(t: SetTelemetry): CoachOutput {
        if (t.reps == 0) {
            return out(SPECIAL["zero"]!!["coach"]!!, SPECIAL["zero"]!!["boss"]!!, t)
        }

        val tired = t.fatigueBand == FatigueBand.FADING || t.fatigueBand == FatigueBand.GASSED

        // A consistent bilateral lean outranks a depth or tempo nudge. Depth you can fix next rep; a
        // side carrying the other is the thing that leads somewhere worse, and it is the observation a
        // physiotherapist would actually want. It still yields to real fatigue, which is more urgent.
        if (!tired && t.asymmetryPct != null && t.asymmetryPct >= 10) {
            return out(ASYM["coach"]!!, ASYM["boss"]!!, t)
        }

        if (!tired && t.sessionSetIndex == 1 && t.fatigueBand == FatigueBand.FRESH) {
            return out(SPECIAL["first"]!!["coach"]!!, pickBoss(t), t)
        }
        if (!tired && t.bossHpPct <= 20) {
            return out(SPECIAL["lowHp"]!!["coach"]!!, SPECIAL["lowHp"]!!["boss"]!!, t)
        }

        val reason = t.worstRep?.reason ?: "*"
        val match = COACH.find { it.band == t.fatigueBand && it.reason == reason && it.trend == t.trend }
            ?: COACH.find { it.band == t.fatigueBand && it.reason == reason }
            ?: COACH.find { it.band == t.fatigueBand && it.reason == "*" && it.trend == t.trend }
            ?: COACH.find { it.band == t.fatigueBand && it.reason == "*" }
            ?: COACH.last()

        return out(match.line, pickBoss(t), t)
    }

    private fun pickBoss(t: SetTelemetry): String {
        val usable = BOSS.filter { fillable(it, t) }
        return if (usable.isNotEmpty()) {
            usable[(t.sessionSetIndex + t.reps) % usable.size]
        } else {
            BOSS[0]
        }
    }

    private fun out(coachLine: String, bossLine: String, t: SetTelemetry): CoachOutput {
        return CoachOutput(
            coachLine = fill(coachLine, t) ?: "Good set. Go again when you're ready.",
            bossLine = fill(bossLine, t) ?: "Again.",
            source = CoachSource.TEMPLATE,
        )
    }

    private fun fillable(line: String, t: SetTelemetry): Boolean {
        val placeholders = Regex("\\{(\\w+)\\}").findAll(line).map { it.groupValues[1] }.toList()
        return placeholders.all { resolve(it, t) != null }
    }

    /** Fills placeholders in a template line. Returns null if any placeholder cannot be resolved. */
    fun fill(line: String, t: SetTelemetry): String? {
        if (!fillable(line, t)) return null
        var result = line
        var canFill = true
        result = result.replace(Regex("\\{(\\w+)\\}")) { match ->
            val key = match.groupValues[1]
            val value = resolve(key, t)
            if (value == null) {
                canFill = false
                match.value
            } else {
                value.toString()
            }
        }
        return if (canFill) result else null
    }

    private fun resolve(key: String, t: SetTelemetry): Any? {
        return when (key) {
            "reps" -> t.reps
            "form_mean" -> t.formMean
            "form_last3" -> t.formLast3
            "form_first3" -> t.formFirst3
            "form_mean_pct" -> t.formMeanPct
            "form_last3_pct" -> t.formLast3Pct
            "form_first3_pct" -> t.formFirst3Pct
            "depth_cm" -> t.depthCm
            "depth_drop_cm" -> t.depthDropCm
            "velocity_loss_pct" -> t.velocityLossPct
            "rom_loss_pct" -> t.romLossPct
            "combo_reps" -> t.comboReps
            "boss_hp_pct" -> t.bossHpPct
            "worst_index" -> t.worstRep?.index
            "best_index" -> t.bestRep?.index
            "asymmetry_pct" -> t.asymmetryPct
            "weaker_side" -> t.weakerSide
            else -> null
        }.let { v ->
            if (v == null || (v is Float && v.isNaN())) null else v
        }
    }
}
