package app.chalkroom.server

import com.google.genai.types.ThinkingLevel

/** One model role's call settings. Every field is env-overridable via `<ROLE>_MODEL`, `_THINKING`, `_MAX_TOKENS`, `_TEMPERATURE`. */
data class ModelSettings(
    val model: String,
    val thinking: ThinkingLevel.Known?,
    val maxOutputTokens: Int?,
    val temperature: Float?,
)

/**
 * Server configuration from environment variables. Locally, Gradle loads the gitignored root `.env`
 * into the `run` and `test` tasks; on Railway these are real env vars.
 */
data class Config(
    val geminiApiKey: String,
    val port: Int,
    val gen: ModelSettings,
    val turn: ModelSettings,
    val grader: ModelSettings,
) {
    companion object {
        // Pinned ids (verified against the models list on 2026-10-18), not `-latest` aliases,
        // so eval results stay comparable across runs.
        val DEFAULT_GEN = ModelSettings("gemini-3.6-flash", ThinkingLevel.Known.LOW, 8192, 1f)
        val DEFAULT_TURN = ModelSettings("gemini-3.5-flash-lite", ThinkingLevel.Known.MINIMAL, 1024, 1f)
        val DEFAULT_GRADER = ModelSettings("gemini-3.6-flash", ThinkingLevel.Known.LOW, null, 1f)

        fun fromEnv(env: Map<String, String> = System.getenv()): Config = Config(
            geminiApiKey = env["GEMINI_API_KEY"].orEmpty(),
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            gen = settings(env, "GEN", DEFAULT_GEN),
            turn = settings(env, "TURN", DEFAULT_TURN),
            grader = settings(env, "GRADER", DEFAULT_GRADER),
        )

        private fun settings(env: Map<String, String>, role: String, default: ModelSettings) = ModelSettings(
            model = env["${role}_MODEL"] ?: default.model,
            thinking = env["${role}_THINKING"]?.let { ThinkingLevel.Known.valueOf(it.uppercase()) } ?: default.thinking,
            maxOutputTokens = env["${role}_MAX_TOKENS"]?.toIntOrNull() ?: default.maxOutputTokens,
            temperature = env["${role}_TEMPERATURE"]?.toFloatOrNull() ?: default.temperature,
        )
    }
}
