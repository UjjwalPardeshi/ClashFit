package com.clashfit.engine.coach

/** Loads prompt texts from assets for the LLM inference. */
data class PromptPack(
    val system: String,
    val coach: String,
    val boss: String,
) {
    companion object {
        /**
         * Loads prompts from asset files using a provided reader function.
         * The reader function takes a filename and returns its contents, or null if not found.
         * Filenames are relative to the prompts/ directory: "system.txt", "coach.txt", "boss.txt".
         */
        fun load(reader: (String) -> String?): PromptPack? {
            val system = reader("prompts/system.txt") ?: return null
            val coach = reader("prompts/coach.txt") ?: return null
            val boss = reader("prompts/boss.txt") ?: return null
            return PromptPack(system, coach, boss)
        }
    }
}
