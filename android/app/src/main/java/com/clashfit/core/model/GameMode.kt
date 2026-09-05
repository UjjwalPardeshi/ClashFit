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
    /**
     * Three or four words for a card in a row of sixteen.
     *
     * The blurb is a good sentence and a bad tile. Sixteen cards each carrying two lines of prose
     * is a page nobody reads — it is chosen from at a glance or not at all, and a glance holds a
     * mark, a name, and a hook. The blurb keeps its place wherever there is room to read it.
     */
    val hook: String,
    val enabled: Boolean = true,
) {
    BOSS_FIGHT("Boss Fight", ModeKind.SOLO, Family.REP_CYCLE, false,
        "Phases, a health pool, and behaviour that adapts to your fatigue.", "It fights back"),
    TIME_ATTACK("Time Attack", ModeKind.SOLO, Family.REP_CYCLE, true,
        "Maximum damage in sixty seconds. Go out too hard and the back half costs you.", "Sixty seconds flat"),
    GHOST_RACE("Ghost Race", ModeKind.SOLO, Family.REP_CYCLE, false,
        "Two bars, one boss. Race a built-in pacer, yesterday's you, or a friend's ghost.", "Race yesterday"),
    SURVIVAL("Survival", ModeKind.SOLO, Family.REP_CYCLE, false,
        "Endless waves, mercy rule off. The one mode where fatigue genuinely ends the run.", "No mercy rule"),
    BOSS_RUSH("Boss Rush", ModeKind.SOLO, Family.REP_CYCLE, false,
        "Three bosses back to back, no rest.", "Three, back to back"),
    TEMPO_TRIAL("Tempo Trial", ModeKind.SOLO, Family.REP_CYCLE, false,
        "Match the metronome. It scores how you moved, not that you moved.", "Match the metronome"),

    REP_RACE("Rep Race", ModeKind.VERSUS, null, true,
        "Same clock, same movement, most reps wins. Half-reps still don't count.", "Most reps wins"),
    DUEL("Duel", ModeKind.VERSUS, Family.REP_CYCLE, true,
        "Two phones, one boss. Highest damage wins, so the deeper set beats the faster one.", "Two phones, one boss"),
    TUG_OF_WAR("Tug of War", ModeKind.VERSUS, Family.REP_CYCLE, true,
        "One bar between you. Your damage pushes it toward them.", "One bar between you"),
    MIRROR_MATCH("Mirror Match", ModeKind.VERSUS, Family.REP_CYCLE, true,
        "Both chase a target tempo. Control beats speed.", "Control beats speed"),

    RAID("Raid", ModeKind.GROUP, Family.REP_CYCLE, false,
        "One boss scaled to the room. One shared bar on every screen.", "One boss, whole room"),
    RELAY("Relay", ModeKind.GROUP, null, false,
        "Pass the phone. Thirty seconds each, boss health carries over.", "Pass the phone"),
    LAST_STANDING("Last Standing", ModeKind.GROUP, null, false,
        "Out when your measured fatigue hits GASSED, not when you run out of reps.", "Last one not gassed"),
    TEAM_VS_TEAM("Team vs Team", ModeKind.GROUP, Family.REP_CYCLE, false,
        "Two teams, two bosses. First side to put theirs down takes it.", "Two teams, two bosses"),
    CIRCUIT("Circuit", ModeKind.GROUP, null, false,
        "One sequence across four movement families, everyone moving through it together.", "Four families, one lap"),

    SIEGE("Siege", ModeKind.FAMILY, Family.ISOMETRIC_HOLD, false,
        "Your hold is the shield. Break form and a hit lands.", "Your hold is the shield"),
    PURSUIT("Pursuit", ModeKind.FAMILY, Family.CADENCE, false,
        "Something chases you. The gap closes when you slow down.", "Don't get caught"),
    BREAKER("Breaker", ModeKind.FAMILY, Family.BALLISTIC, false,
        "Height is force. A stiff landing scores but doesn't break through.", "Height is force"),
    SIGIL("Sigil", ModeKind.FAMILY, Family.POSE_MATCH, false,
        "No boss, no damage, no ranking. Each asana lights a constellation point.", "Light the constellation"),

    CLINIC_STS("30s Sit-to-Stand", ModeKind.CLINIC, Family.REP_CYCLE, true,
        "A published protocol. A count, and your own trend over time.", "A published protocol"),

    /**
     * ZOMBIE RUN — the outdoor chase.
     *
     * The constant keeps its old spelling on purpose. It is written into `sessions.mode` in the
     * database and into the `modes.OUTBREAK` key in `config/combat.json`, so renaming it would
     * orphan every session already recorded and every config file in the wild. Everything a
     * person reads says Zombie Run; only the stored token still says OUTBREAK.
     */
    OUTBREAK("Zombie Run", ModeKind.SOLO, Family.CADENCE, false,
        "They spawn around you and close when you slow down. The one mode that goes online.",
        "Outrun the horde");

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
