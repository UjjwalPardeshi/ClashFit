package com.clashfit.core.model

/**
 * Every way to play. `family == null` means any family is allowed.
 * `timed` modes run on a clock and must never let the boss die out from under them.
 */
enum class GameMode(
    val title: String,
    val kind: ModeKind,
    val family: Family?,
    val timed: Boolean,
    val blurb: String,
) {
    BOSS_FIGHT("Boss Fight", ModeKind.SOLO, Family.REP_CYCLE, false,
        "Phases, a health pool, and behaviour that adapts to your fatigue."),
    TIME_ATTACK("Time Attack", ModeKind.SOLO, Family.REP_CYCLE, true,
        "Maximum damage in sixty seconds. Go out too hard and the back half costs you."),
    GHOST_RACE("Ghost Race", ModeKind.SOLO, Family.REP_CYCLE, false,
        "Two bars, one boss. Race a shipped pacer, yesterday's you, or a friend's file."),
    SURVIVAL("Survival", ModeKind.SOLO, Family.REP_CYCLE, false,
        "Endless waves, mercy rule off. The one mode where fatigue genuinely ends the run."),
    BOSS_RUSH("Boss Rush", ModeKind.SOLO, Family.REP_CYCLE, false,
        "Three bosses back to back, no rest."),
    TEMPO_TRIAL("Tempo Trial", ModeKind.SOLO, Family.REP_CYCLE, false,
        "Match the metronome. It scores how you moved, not that you moved."),

    REP_RACE("Rep Race", ModeKind.VERSUS, null, true,
        "Same clock, same movement, most reps wins. Half-reps still don't count."),
    DUEL("Duel", ModeKind.VERSUS, Family.REP_CYCLE, true,
        "Two phones, one boss. Highest damage wins, so the deeper set beats the faster one."),
    TUG_OF_WAR("Tug of War", ModeKind.VERSUS, Family.REP_CYCLE, true,
        "One bar between you. Your damage pushes it toward them."),
    MIRROR_MATCH("Mirror Match", ModeKind.VERSUS, Family.REP_CYCLE, true,
        "Both chase a target tempo. Control beats speed."),

    RAID("Raid", ModeKind.GROUP, Family.REP_CYCLE, false,
        "One boss scaled to the room. One shared bar on every screen."),
    RELAY("Relay", ModeKind.GROUP, null, false,
        "Pass the phone. Thirty seconds each, boss health carries over."),
    LAST_STANDING("Last Standing", ModeKind.GROUP, null, false,
        "Out when your measured fatigue hits GASSED, not when you run out of reps."),
    TEAM_VS_TEAM("Team vs Team", ModeKind.GROUP, Family.REP_CYCLE, false,
        "Two teams, two bosses. First side to put theirs down takes it."),
    CIRCUIT("Circuit", ModeKind.GROUP, null, false,
        "One sequence across four movement families, everyone moving through it together."),

    SIEGE("Siege", ModeKind.FAMILY, Family.ISOMETRIC_HOLD, false,
        "Your hold is the shield. Break form and a hit lands."),
    PURSUIT("Pursuit", ModeKind.FAMILY, Family.CADENCE, false,
        "Something chases you. The gap closes when you slow down."),
    BREAKER("Breaker", ModeKind.FAMILY, Family.BALLISTIC, false,
        "Height is force. A stiff landing scores but doesn't break through."),
    SIGIL("Sigil", ModeKind.FAMILY, Family.POSE_MATCH, false,
        "No boss, no damage, no ranking. Each asana lights a constellation point."),

    CLINIC_STS("30s Sit-to-Stand", ModeKind.CLINIC, Family.REP_CYCLE, true,
        "A published protocol. A count, and your own trend over time.");

    /** Which family game a family's movements are naturally shaped for. */
    val isFamilyGame: Boolean get() = kind == ModeKind.FAMILY

    companion object {
        /** The sixteen tiles on the mode screen, in the site's order. */
        val grid: List<GameMode> = listOf(
            BOSS_FIGHT, TIME_ATTACK, REP_RACE, GHOST_RACE, DUEL, SURVIVAL, BOSS_RUSH, TEMPO_TRIAL,
            SIEGE, PURSUIT, BREAKER, SIGIL, LAST_STANDING, RELAY, CIRCUIT, CLINIC_STS,
        )

        fun familyGameFor(family: Family): GameMode = when (family) {
            Family.REP_CYCLE -> BOSS_FIGHT
            Family.ISOMETRIC_HOLD -> SIEGE
            Family.CADENCE -> PURSUIT
            Family.BALLISTIC -> BREAKER
            Family.POSE_MATCH -> SIGIL
        }
    }
}
