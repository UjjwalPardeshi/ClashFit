package com.clashfit.core.model

/** Five detector families. Adding an exercise is a config record; adding a family is one class. */
enum class Family { REP_CYCLE, ISOMETRIC_HOLD, POSE_MATCH, CADENCE, BALLISTIC }

/** Latched fatigue bands. `FADING` is shown to the player as HEAVY, matching the site. */
enum class FatigueBand(val label: String) {
    FRESH("FRESH"), WORKING("WORKING"), FADING("HEAVY"), GASSED("GASSED");
}

enum class Verdict { CLEAN, OK, SHALLOW }

enum class Framing { OK, TOO_CLOSE, TOO_FAR, PARTIAL, NONE }

enum class CalibState { SEARCHING, PARTIAL, TOO_CLOSE, TOO_FAR, HOLDING, READY }

enum class Phase { CALIBRATING, FIGHTING, FRAMING_LOST, DEAD }

enum class Side { LEFT, RIGHT }

enum class EndReason { BOSS_DOWN, TIME, GAME_WON, GAME_LOST, DEFEATED, STOPPED, WALKED_AWAY }

/** Who spoke: the on-device model, the opt-in cloud model, or the template bank. Shown as a badge. */
enum class CoachSource { LLM, CLOUD, TEMPLATE }

enum class LinkState { IDLE, ADVERTISING, SEARCHING, LINKED, LOST }

enum class GameOutcome { RUNNING, WON, LOST }

enum class ModeKind {
    SOLO, VERSUS, GROUP, FAMILY, CLINIC,

    /**
     * The gym log, which is not a way to play and must never appear in a list of them.
     *
     * It has its own kind rather than borrowing SOLO precisely so that it cannot be swept into a
     * battle screen by a filter somebody writes later. Every screen that lists battles iterates
     * [battles] instead of every entry, so leaving this one out is a fact about the type rather
     * than a rule each screen has to remember.
     */
    WORKOUT,
    ;

    companion object {
        /** The kinds that are ways to play. Battle screens list these and only these. */
        val battles: List<ModeKind> = listOf(SOLO, VERSUS, GROUP, FAMILY, CLINIC)
    }
}
