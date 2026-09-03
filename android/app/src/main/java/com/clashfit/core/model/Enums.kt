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

enum class Phase { CALIBRATING, FIGHTING, FRAMING_LOST, REST, DEAD }

enum class Side { LEFT, RIGHT }

enum class EndReason { BOSS_DOWN, TIME, GAME_WON, GAME_LOST, STOPPED, WALKED_AWAY }

enum class CoachSource { LLM, TEMPLATE }

enum class LinkState { IDLE, ADVERTISING, SEARCHING, LINKED, LOST }

enum class GameOutcome { RUNNING, WON, LOST }

enum class ModeKind { SOLO, VERSUS, GROUP, FAMILY, CLINIC }
