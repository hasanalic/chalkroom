package app.chalkroom.feature.lesson

import kotlinx.coroutines.delay

/** One beat of the clearing sequence (prd.md > Teaching a Misconception). */
enum class ClearingStep(val atMs: Long) {
    HOLD(0), // soft haptic, then nothing: a mind changing, not a form validating
    FOG_RESOLVES(480),
    DRIFT_TO_EYE(620),
    EYES_WIDEN(700),
    COUNTER_DROPS(780), // soft haptic
    SPEAK(900), // reply (none for a revealed item), pad and squiggles
}

/**
 * Runs the fixed timeline from the moment the answer is "yes". Starts only after the turn
 * response arrives, so the 400ms hold is real empty time. Timings never change; tune the haptic, not these.
 */
object ClearingChoreography {
    suspend fun run(onStep: suspend (ClearingStep) -> Unit) {
        var at = 0L
        for (step in ClearingStep.entries) {
            delay(step.atMs - at)
            at = step.atMs
            onStep(step)
        }
    }
}
