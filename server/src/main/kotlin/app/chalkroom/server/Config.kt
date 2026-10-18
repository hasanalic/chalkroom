package app.chalkroom.server

/**
 * Server configuration from environment variables. Locally, Gradle loads the gitignored root `.env`
 * into the `run` and `test` tasks; on Railway these are real env vars.
 */
data class Config(
    val geminiApiKey: String,
    val port: Int,
    val genModel: String,
    val turnModel: String,
    val graderModel: String,
) {
    companion object {
        // Pinned ids (verified against the models list on 2026-10-18), not `-latest` aliases,
        // so eval results stay comparable across runs.
        const val DEFAULT_PRO_MODEL = "gemini-3.1-pro-preview"
        const val DEFAULT_FLASH_MODEL = "gemini-3.8-flash"

        fun fromEnv(env: Map<String, String> = System.getenv()): Config = Config(
            geminiApiKey = env["GEMINI_API_KEY"].orEmpty(),
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            genModel = env["GEN_MODEL"] ?: DEFAULT_PRO_MODEL,
            turnModel = env["TURN_MODEL"] ?: DEFAULT_FLASH_MODEL,
            graderModel = env["GRADER_MODEL"] ?: DEFAULT_PRO_MODEL,
        )
    }
}
