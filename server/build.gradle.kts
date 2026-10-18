plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("app.chalkroom.server.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.google.genai)
    implementation(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.server.test.host)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Local runs and evals read GEMINI_API_KEY (and optional model overrides) from the gitignored
// root .env. Real environment variables win, so Railway's env is never overridden.
val dotEnv: Map<String, String> = rootProject.file(".env").takeIf { it.exists() }
    ?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    ?.associate { line ->
        val (k, v) = line.split("=", limit = 2)
        k.trim().removePrefix("export ").trim() to v.trim().removeSurrounding("\"").removeSurrounding("'")
    }
    ?.filterKeys { System.getenv(it) == null }
    ?: emptyMap()

tasks.named<JavaExec>("run") {
    environment(dotEnv)
}

tasks.test {
    useJUnitPlatform()
    environment(dotEnv)
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Evals hit a live model; rerun them every time instead of trusting Gradle's up-to-date check.
    outputs.upToDateWhen { false }
}
