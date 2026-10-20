---
doc: spec
status: approved
---

# Chalkroom — Technical Spec

## How This Works, In Plain Language
Chalkroom has three pieces in one Gradle project:

- **The Android app (`:app`)** is everything you see: the classroom, Mira, Pip and Juno drawn in code, the motes, the clearing sequence. It also holds the lesson in memory: which misconceptions are left, how many tries you've made, what you've said. The app decides the **stage** of each turn (normal or "start from the beginning") and shows the stuck buttons and the answer card when turns run out. It never writes a student's lesson line itself, apart from the fixed per-student stuck line.
- **The server (`:server`)** is a small Ktor app on Railway. It's the only thing that talks to Gemini and the only thing that holds the API key. It owns the prompts and asks Gemini two kinds of questions:
  - *"Read these notes as Mira. What do you get wrong?"*
  - *"Did this explanation address that misconception? Yes or no, and what does Mira say next?"*
- **The shared module (`:shared`)** is the request/response shapes both sides agree on, so the app and server can't drift apart.

**The refutation travels with the lesson.** When the server creates misconceptions, Gemini also writes each one's *refutation*, meaning what's actually true according to your notes. The judge needs it. `/decks` returns it in plain JSON next to the misconception. The app keeps it in memory, renders it **only on the answer card** when turns run out, and sends it back on each turn. The student never says or hints at it. The server remembers nothing between calls, so a redeploy never breaks a paused lesson. This is a UI rule, not a cryptographic one.

**Structured output.** Gemini's answers come back as typed JSON matching a schema, not free text. So "addressed: yes/no" is a real boolean, and misconceptions are a real list.

**Evals** are JVM tests in `:server` that call the real API. They check three things:
- The judge gives the same yes/no 3 times out of 3, including borderline cases.
- Student replies obey the may/may-not-say rules.
- Mira, Pip and Juno really get the same notes wrong in different, flaw-shaped ways.

Why this shape: the key must stay off a public repo and APK, and the prompts and judging should live in one place. A small server does both, and the later web version needs it anyway. Everything else (no database, no accounts, memory-only state) stays as small as the PoC allows.

## The Core Journey Through the System
PRD ref: `prd.md > The Core Journey`.

```
 Classroom ──Teach something──▶ PasteNotes ──▶ ChooseStudent ──▶ Reading ──▶ Lesson ──▶ Closing
                                                                    │           │  ▲
                                                           POST /decks   POST /lessons/turn (each try)
                                                                    ▼           ▼  │
                                                              :server ──Google Gen AI SDK──▶ Gemini
```

1. **Arrive.** `ClassroomScreen` reads `LessonRepository` (in memory, empty on first launch). The board is blank, and Mira, Pip and Juno breathe and blink.
2. **Paste notes.** `PasteNotesScreen` keeps the text in `LessonRepository.lastNotes`. The app enforces 150–8,000 characters before continuing.
3. **Choose who you teach.** `ChooseStudentScreen` shows each student's **fixed** self-introduction from `StudentCatalog` (no network). A student with a paused lesson shows **Continue**.
4. **Watch them read.** `ReadingScreen` sends `POST /decks {notes, studentId}`.
   - **Server:** calls `GEN_MODEL` with the student's flaw prompt and a response schema, then gets back the language, topic title, 0–8 misconceptions and their refutations. It assigns ids and returns `{language, topicTitle, misconceptions: [{id, statement, refutation}]}`.
   - **App:** creates a `Lesson` in `LessonRepository`. Motes appear one by one, the count is said, and the student looks up.
5. **Lesson.** The student's current line is the current misconception's `statement`. You type an explanation, and `LessonViewModel` computes the stage and sends `POST /lessons/turn {statement, refutation, studentId, stage, language, explanation, priorExchanges}`.
   - **Server:** counts your messages (`priorExchanges.size + 1`), refuses a 6th with 409 `turn_limit` (no model call), calls `TURN_MODEL` with the refutation as judge-only context and a response schema, and returns `{addressed, reply}`. From turn 4 on, a miss returns `reply: null`.
   - **App, not addressed (turns 1–3):** the unconvinced reaction plays, then `reply` becomes the current line. The try is recorded.
   - **App, not addressed on turn 4:** the unconvinced reaction plays, then the student's **fixed stuck line** from `StudentCatalog`, and **See the answer** / **One last try** appear. The input stays locked.
   - **App, not addressed on turn 5, See the answer, or a 409:** the answer card shows the refutation. **Continue** runs `ClearingChoreography` with no spoken line and the item is recorded as revealed (not in What stuck).
   - **App, addressed (any turn):** `ClearingChoreography` runs the fixed timeline, `reply` (which echoes your words) is spoken at 900ms, the cleared item is stored with your sentence, and the next misconception becomes the line.
6. **Closing.** The queue is empty, so `ClosingScreen` shows "Nothing left in Mira's head", Mira's **fixed** relief line from `StudentCatalog`, and What stuck (your sentences, only for items your explanations cleared; left out if there are none). The notebook closes with `topicTitle` on its cover and a haptic, then the two options appear. The lesson is removed from the repository.
7. **Leave anytime.** Back from Reading cancels the request and creates nothing. Back from a lesson leaves the `Lesson` in `LessonRepository`, marked paused, and sets `lastPausedStudentId`.

## Stack
| Piece | Choice | Docs |
|---|---|---|
| Android UI | Kotlin, Jetpack Compose (Material 3 only as a base; custom look on top) | https://developer.android.com/develop/ui/compose |
| Architecture | MVI per screen: `Contract` (UIState/UIEvent/UIEffect), ViewModel with a single `onEvent`, `Screen` + stateless `ScreenContent` with preview *(learner's convention)* | — |
| Navigation | Navigation Compose with type-safe `@Serializable` routes *(learner choice, not Navigation 3)* | https://developer.android.com/guide/navigation/design/type-safety |
| DI | Koin *(learner choice)* | https://insert-koin.io/docs/quickstart/android-compose |
| App HTTP | Ktor client (OkHttp engine) + ContentNegotiation + kotlinx.serialization | https://ktor.io/docs/client-create-new-application.html |
| Server | Ktor server (Netty) + ContentNegotiation + kotlinx.serialization + RateLimit plugin | https://ktor.io/docs/server-rate-limit.html |
| Gemini | Google Gen AI Java SDK `com.google.genai:google-genai`, structured output via `responseMimeType("application/json")` + `responseSchema` *(learner choice)* | https://github.com/googleapis/java-genai · https://ai.google.dev/gemini-api/docs/structured-output |
| Shared models | `:shared`, a plain Kotlin/JVM library with kotlinx.serialization | https://github.com/Kotlin/kotlinx.serialization |
| Evals | JUnit 5 tests in `:server`, skipped when `GEMINI_API_KEY` is unset | — |
| Hosting | Railway, Docker image of the Ktor fat JAR | https://docs.railway.com |

Models (learner choice, revised during the build; defaults in `Config.kt`, every value env-overridable via `<ROLE>_MODEL`, `<ROLE>_THINKING`, `<ROLE>_MAX_TOKENS`, `<ROLE>_TEMPERATURE` with ROLE = `GEN`, `TURN`, `GRADER`):

| Role | Model | Thinking | Max output tokens | Temperature |
|---|---|---|---|---|
| **Reading** (`/decks`, `GEN`) | `gemini-3.6-flash` | LOW | 8192 | 1 |
| **Rounds** (`/lessons/turn`, judge + reply in one call, `TURN`) | `gemini-3.5-flash-lite` | MINIMAL | 1024 | 1 |
| **Eval grader** (`GRADER`, evals only) | `gemini-3.6-flash` | LOW | default | 1 |

**The evals decide:** if flash-lite's judge consistency fails, the learner decides the change (model or settings, via env).

**Verify early in the build:**
- **Exact library versions:** pin the latest stable when you start.
- **Exact Gemini model ids** for the current Pro and Flash models.
- **The Gen AI Java SDK's `Schema` builder from Kotlin:** build `responseSchema` with `Schema.builder()` and parse the JSON text with kotlinx.serialization.
- **Safety blocks:** how a blocked response surfaces (`finishReason` such as `SAFETY`, empty candidates). Treat it as an AI failure.
- **Railway's current Docker deploy flow.**

## Where It Runs and How Someone Tries It
- **The app** runs on an Android emulator (on-device checks are emulator-only, `adb -e` / `emulator-5554`), minSdk 26.
- **The server** runs locally with `./gradlew :server:run`, or on Railway.
- **Server environment variables:**
  - `GEMINI_API_KEY`
  - `PORT`: Railway sets it, default 8080.
  - `GEN_*`, `TURN_*`, `GRADER_*` (`_MODEL`, `_THINKING`, `_MAX_TOKENS`, `_TEMPERATURE`), optional. Defaults in the Stack table.
- **App config:** `local.properties` has `chalkroom.apiBaseUrl` (gitignored), injected as `BuildConfig.API_BASE_URL`. Use `http://10.0.2.2:8080` for the emulator against the local server, or the Railway URL.
- **Start locally:**
  1. `export GEMINI_API_KEY=…`
  2. `./gradlew :server:run`
  3. Run `:app` from Android Studio.
- **Run evals:** `./gradlew :server:test -Pevals` (opt-in; needs `GEMINI_API_KEY`). Plain `test` runs only the offline unit tests.
- **Deploy (learner choice: Railway):**
  1. `server/Dockerfile` builds the fat JAR with `./gradlew :server:buildFatJar`.
  2. Railway builds from the Dockerfile. Set the env vars in Railway.
  3. In Google AI Studio / Cloud: keep quota and rate limits tight for the key's project, and set a budget alert.
- **For the demo video:** record the emulator against the deployed Railway server. Use short biology notes that yield about 3 misconceptions, then show Mira and a second student on the same notes.
- **Submission:** a short demo video plus a public GitHub repo. A debug APK built against the deployed server is attached to a GitHub Release, so judges can try it without their own key; the README also covers running everything locally.

## Look and Feel
Carries forward `prd.md > Look and Feel`. Settled. These are build-ready values.

- **Theme:** a custom `ChalkroomTheme` (Compose `CompositionLocal` tokens), not stock Material colors. No gradients anywhere.
- **Palette.** These are starting values the learner can tune. The constraint isn't: no red, orange-red or warning colors in teaching.

  | Role | Color |
  |---|---|
  | Paper (background) | `#F4EBD9` |
  | Paper deep | `#E9DCC2` |
  | Wood | `#B9875A` / `#8E6440` |
  | Board | `#3F4A3C` with chalk `#EDE8DA` |
  | Outline / ink | `#2A2420` |
  | Mira | warm ochre `#E2B04A` |
  | Pip | teal `#4FA3A0` |
  | Juno | lavender `#9C8CC4` |
  | Juno's face area | `#F7F1E3` |
  | Motes | soft grey-cream fog `#D8D2C4` at 70% alpha |

- **Typography:** rounded, friendly and bookish. One rounded sans for UI (e.g., Nunito via downloadable Google Fonts) and the same font for student lines, with a slightly larger size. *Agent suggestion, not established; swap freely.*
- **Copy tone:** calm, plain, never evaluative. UI strings are English. Lesson lines are in the notes' language.
- **Density:** spacious. One student and one line in focus at a time.
- **No chat UI:** the current line is a single card, your last explanation sits quieter beneath it, and earlier exchanges are in a bottom sheet.

## Components

### Shared Contract (`:shared`)
`@Serializable` DTOs:
- `CreateDeckRequest(notes: String, studentId: StudentId)`
- `CreateDeckResponse(language: String, topicTitle: String, misconceptions: List<MisconceptionDto>)` (empty list means nothing to misunderstand)
- `MisconceptionDto(id: String, statement: String, refutation: String)`
- `TurnRequest(statement: String, refutation: String, studentId: StudentId, stage: Stage, language: String, explanation: String, priorExchanges: List<Exchange>)`
- `Exchange(explanation: String, reply: String?)` (`null` for a judge-only miss)
- `TurnResponse(addressed: Boolean, reply: String?)` (`null` on a miss from turn 4 on)
- `enum StudentId { MIRA, PIP, JUNO }`
- `enum Stage { NORMAL, SIMPLIFY }`
- `object TurnLimits { const val MAX_TURNS = 5; const val JUDGE_ONLY_FROM_TURN = 4 }` (shared so app and server agree)
- `ErrorBody(code: String)`

PRD ref: `prd.md > Teaching a Misconception`, `prd.md > Reading and Misconception Creation`.

### Server: Deck Endpoint (`POST /decks`)
Validates 150–8,000 characters (400 otherwise). Calls `MisconceptionGenerator`, assigns ids, and returns `CreateDeckResponse`.
PRD ref: `prd.md > Reading and Misconception Creation`, `prd.md > Students and Their Flaws`.

### Server: Turn Endpoint (`POST /lessons/turn`)
Validates lengths (400 `explanation_length`). Computes `turn = priorExchanges.size + 1` (your messages so far plus this one).
- `turn > MAX_TURNS` → 409 `turn_limit`, no model call.
- Otherwise calls `TurnJudge` and returns `TurnResponse`.
- `turn >= JUDGE_ONLY_FROM_TURN` and not addressed → `reply` is set to `null` by the server, whatever the model wrote. A hit still returns the acknowledgment.
PRD ref: `prd.md > Teaching a Misconception`, `prd.md > Getting Unstuck`, `prd.md > What the Student May and May Not Say`.

### Server: Misconception Generator
One `GEN_MODEL` call with `responseMimeType = application/json` and a `responseSchema`:
`{language: string (BCP-47), topicTitle: string, nothingToLearn: boolean, misconceptions: [{flaw, statement: string, refutation: string}] (5..8)}`.
- **System prompt:** the shared student rules plus the chosen student's persona and flaw from `StudentPrompts`.
- **Instructions:**
  - Form misconceptions only from the notes, shaped by the flaw.
  - Use the first person, in the student's voice, in the notes' language.
  - Every belief is false as stated and contradicts the notes (never true-but-incomplete).
  - Write 5 candidates; set `nothingToLearn` when the text has no content (the server then returns an empty list).
  - The server keeps the first 3 own-flaw items, fills from the others, and calls once more if it still has fewer than 3. Zero after the retry is an AI failure (502).
  - Write `topicTitle` in the notes' language.
- **Config:** the `GEN` settings (Stack table). Tune only if the evals show it's needed.

### Server: Turn Judge
One `TURN_MODEL` call with a JSON `responseSchema` `{addressed: boolean, reply: string}`.
- **Input:** the persona, the misconception, the **refutation (judge-only)**, the prior exchanges, the new explanation, the stage and the language.
- **Rules in the prompt:**
  - `addressed` is yes/no only. Yes when the explanation corrects *this* misconception's core error.
  - **If addressed:** `reply` is an acknowledgment that reuses words from the explanation.
  - **If not addressed:** `reply` says what still confuses the student, from a new angle, and ends with something to answer.
  - **SIMPLIFY:** the reply asks the user to explain it from the very start, as simply as possible.
  - **Judge-only turns (4 and 5):** the prompt says only `addressed` matters on a miss; the endpoint drops any miss reply.
  - Always: never state or hint at the refutation, never evaluate, never mention another misconception.
- **Config:** the `TURN` settings (Stack table). Judge stability is measured by the evals, not assumed from temperature.

### Server: Student Prompts
`StudentPrompts.kt` holds the shared rules block plus three persona/flaw blocks:
- **Mira** (curious, plain): omits a key step or condition.
- **Pip** (fast, careless): confuses similar things.
- **Juno** (calm, questioning): picks the wrong cause (a side effect taken for the cause, the wrong cause, or two things that only happen together); never the result as its own cause.

Kept stable and placed first in the prompt, so implicit caching can help. Treat it as a bonus, not a requirement.

### Server: Abuse Protection
- **Ktor RateLimit, per IP:** `/decks` 20 per hour, `/lessons/turn` 150 per hour *(agent-proposed numbers; adjust)*.
- **Input caps:** notes ≤ 8,000 characters, explanation ≤ 2,000 characters *(agent-proposed)*, `priorExchanges` ≤ 4 (more is the 409 `turn_limit` above).
- **Spend:** AI Studio quota and rate limits on the key's project, plus a budget alert *(learner choice)*.

### Server: AI Error Mapping
A timeout, 429, 5xx, safety block, empty candidates or schema-parse failure returns 502 `ai_unavailable`. The app treats every non-2xx response the same calm way. PRD ref: `prd.md > States and Boundaries`.

### App: Lesson Repository
A Koin singleton holding `StateFlow<ClassState>`. See **Data Model**. It's the only source of truth across screens.

Pure, unit-testable lesson logic:
- `startLesson`
- `recordTry`
- `markStuck` (turn 4 missed)
- `chooseLastTry` (One last try)
- `revealAnswer` (See the answer, turn 5 missed, or 409)
- `clearCurrent(sentence, reply)`
- `clearRevealed` (Continue on the answer card)
- `pause`
- `finish`
- `stageFor(lesson)`

PRD ref: `prd.md > Pausing and Resuming`, `prd.md > Getting Unstuck`.

### App: Chalkroom API Client
`ChalkroomApi` (Ktor client) with `createDeck(...)` and `turn(...)`. It returns `Result`-style outcomes (`Success` / `Failure`) with no exceptions leaking to the UI, and has a 30-second timeout. Cancelling Reading cancels the coroutine.

### App: Student Catalog
`StudentCatalog` holds, per `StudentId`: name, color, the **fixed self-introduction**, **fixed stuck line** (invites either option, judges nothing) and **fixed relief line** (English, written once in each voice), and the desk prop.
PRD ref: `prd.md > Students and Their Flaws`, `prd.md > Closing and What Stuck`.

### App: Student Figure (character component)
`StudentFigure(studentId, expression, motes, look, modifier)` is **one composable used unchanged everywhere**: classroom, choose, reading, lesson, closing. It's drawn with Canvas `Path`s:
- **Mira:** a soft bean body leaning right, one small tuft on top.
- **Pip:** a wide, low, slightly lopsided body with three short pointed tufts.
- **Juno:** a tall pear body, a round pale face area and a small star mark.

All are flat color with a thick ink outline, capsule eyes and a simple mouth.

- `Expression`: `IDLE`, `THINKING` (eyes up and to the side), `PUZZLED`, `GOT_IT` (eyes wide).
- **Motion:**
  - Breathing via `rememberInfiniteTransition` (scale 1.0↔1.015).
  - Blinking on its own random clock (each student a different 2.5–6s range).
  - Expression changes are short tweens.
- **Motes** are drawn by the same composable as fog-blob shapes orbiting the head. Each mote has a `clearProgress` (0→1) driven by the choreography.
- **Unconvinced reaction:** a short per-student pose (Mira tilts, Pip squints lopsidedly, Juno narrows its eyes), then `PUZZLED`. No shake, no red.

PRD ref: `prd.md > Look and Feel`.

### App: Clearing Choreography
`ClearingChoreography` is a suspend function driven by constants that match `prd.md > Teaching a Misconception`:

| ms | Step |
|---|---|
| 0 | Haptic (soft), hold |
| 480 | Fog resolves |
| 620 | Mote drifts into the eye |
| 700 | Eyes widen (`GOT_IT`) |
| 780 | Counter drops, haptic (soft) |
| 900 | Reply shown (none for a revealed item), pad-and-squiggles starts |

Haptics use `View.performHapticFeedback(HapticFeedbackConstants.CONFIRM)` on API 30+ and `CLOCK_TICK` below. Verify the feel on the emulator (or leave to the learner) and tune the constant choice, not the timings.

The choreography starts only after the turn response arrives. The thinking look covers the network wait, and the 400ms hold is real empty time after the decision.

### App: Screens
Each screen has a Contract, a ViewModel and a Screen + ScreenContent.

- **Classroom** (`prd.md > Screens and Layout > Classroom`)
  - Room with three seats and desks.
  - The board shows the most recently paused lesson ("Mira left this here" / "N things still don't click") and taps resume it.
  - Tapping a student plays a nod, then resumes their lesson if paused.
  - A **Teach something** button.
  - Open notebook and motes on desks with paused lessons.
- **PasteNotes** (`> Paste Notes`)
  - Text field prefilled from `lastNotes`.
  - Calm hint under 150 characters, live count over 8,000, continue disabled outside the range.
  - When reached from "Teach Mira something else", Mira is pre-chosen and the screen skips to Reading.
- **ChooseStudent** (`> Choose Who You Teach`)
  - Three `StudentFigure`s with fixed intros. **Continue** on students with a paused lesson.
- **Reading** (`> Reading`)
  - Scanning-eyes loop while `/decks` runs. Motes appear one by one, the count is spoken ("3 things don't click yet"), and the student looks up.
  - On an empty list: the "nothing to misunderstand" message, then back to PasteNotes.
  - On failure: "Mira lost the thread. Your notes are still here." and **Try again**.
  - Back cancels.
- **Lesson** (`> Lesson`)
  - Student with motes, the counter "what's left in Mira's head: N", a draining bar, the current-line card, your last explanation (quieter) and the input.
  - Input locked during a round. Placeholder "start from the beginning" from the 4th try (after the attempt-3 reply).
  - **Stuck** (turn 4 missed): the fixed stuck line as the current line, **See the answer** / **One last try** buttons, input locked until one is picked.
  - **Answer card** (`REVEALED`): the app's own card with the refutation (not a student line, not in the student's voice) and **Continue**, which runs the clearing sequence.
  - An "N exchanges" sheet *(stand-in tier 4)*.
- **Closing** (`> Closing`)
  - "Nothing left in Mira's head", the fixed relief line and What stuck (only `cleared`, never `revealedIds`; hidden when empty; tap reveals the misconception).
  - The notebook closes with `topicTitle`, a haptic, then **Teach Mira something else** / **Back to class**.

### App: Navigation
`@Serializable` routes:
- `Classroom`
- `PasteNotes(preselected: StudentId?)`
- `ChooseStudent`
- `Reading(studentId)`
- `Lesson(studentId)`
- `Closing(studentId)`

Each screen reads its data from `LessonRepository` by `studentId`. Routes carry no lesson data.

### Evals (`:server` tests)
PRD ref: `prd.md > AI Quality Evals`. About 10 cases, defined as data in `evals/cases/*.json` with fixed notes, misconceptions and refutations (so judge cases don't depend on generation).

- **Judge consistency.** About 6 cases. Each runs the real `TurnJudge` 3 times, and **passes only if all 3** match the expected `addressed`. They must include:
  - Clearly vague (expect no).
  - Clearly precise (expect yes).
  - Half-right (expect no).
  - Right words with wrong reasoning (expect no).
  - Addresses a *different* misconception (expect no).
  - A Turkish-language pair, to test the language rule.
  - **Turn limit:** a vague explanation sent as turn 4 (3 prior exchanges) through the turn endpoint. Expects `addressed = false` and `reply = null` on all 3 runs.
- **Say-rules.** Applied to every non-null `reply` produced above:
  - (a) A string check for evaluation words ("great", "wrong", "almost", "correct", "exactly", case-insensitive). These words are English-only; for non-English cases, rely on the grader.
  - (b) An LLM grader (`GRADER_MODEL`) with a schema of `{leaksAnswer, changesMisconception, leavesSomethingToAnswer}`, given the refutation as context.
- **Flaw shaping.** About 3 cases, each one set of notes sent through `MisconceptionGenerator` for Mira, Pip and Juno. The grader checks that each misconception matches its student's flaw, and that the three are pairwise different.
- **Output:** a printed table per case × run (✓/✗ plus a short reason), written to `server/build/eval-report.md`. Flakiness is visible, not just a total.
- **Gating:** `assumeTrue(System.getenv("GEMINI_API_KEY") != null)`.
- **Cost:** roughly ~60 small calls per full run. Expected well under $1, unverified, so check usage after the first run.

## Data Model
All app state is **in memory only** (`prd.md > States and Boundaries > Persistence`), so it's all gone when the app process dies.

```kotlin
data class ClassState(
    val lastNotes: String = "",
    val lessons: Map<StudentId, Lesson> = emptyMap(),   // at most one per student
    val lastPausedStudentId: StudentId? = null,         // drives the board
)

data class Lesson(
    val studentId: StudentId,
    val language: String,
    val topicTitle: String,
    val totalCount: Int,                    // motes at start; counter = queue.size
    val queue: List<MoteItem>,              // head = current misconception
    val tries: Int,                         // failed tries on current item (= your messages sent)
    val exchanges: List<Exchange>,          // for current item only; sent as priorExchanges
    val phase: ItemPhase,                   // state of the current item
    val cleared: List<ClearedItem>,         // cleared by your words → What stuck
    val revealedIds: List<String>,          // cleared via the answer card → not in What stuck
    val paused: Boolean,
)
enum class ItemPhase { TEACHING, STUCK, REVEALED } // STUCK: buttons shown; REVEALED: answer card shown
data class MoteItem(val id: String, val statement: String, val refutation: String, val currentLine: String) // refutation: rendered only on the answer card
data class ClearedItem(val statement: String, val yourSentence: String)
```

| Data | Lives in | Updated when | Leave and come back |
|---|---|---|---|
| Notes text | `ClassState.lastNotes` | Typing on PasteNotes | Prefilled for the session; gone after app close |
| Lesson per student | `ClassState.lessons` | Deck created, each successful turn, stuck, last try, reveal, finish | Paused lesson resumes exactly (same line, count, tries, phase) |
| Refutation | `MoteItem.refutation` in memory, **rendered only on the answer card** | Set by `/decks`; sent back on each turn | Paused lesson keeps it; survives server redeploys (stateless) |
| Board | Derived from `lastPausedStudentId` + that lesson | On pause or finish | Shows the most recently paused lesson |

**Rules:**
- `counter = queue.size` and only decreases. Both `clearCurrent` and `clearRevealed` remove the head; only `clearCurrent` adds to `cleared`, and `clearRevealed` adds the id to `revealedIds`. Both reset `tries`, `exchanges` and `phase` for the next item.
- A failed network turn changes nothing; the explanation returns to the input.
- **Stage** for the attempt being sent (n = tries + 1), per `prd.md > Getting Unstuck`:

  | Attempt n | Stage |
  |---|---|
  | 1, 2, 4, 5 | `NORMAL` |
  | 3 | `SIMPLIFY`: the reply asks you to explain it from the very start, as simply as you can |

  The "start from the beginning" placeholder appears after the reply to attempt 3, from the **4th** try on.
- **Turn limit** (`TurnLimits`): attempts 4 and 5 are judge-only (miss → `reply = null`).
  - Attempt 4 missed → `markStuck`: `phase = STUCK`, the stuck line becomes `currentLine`, the input is locked.
  - **One last try** → `chooseLastTry`: `phase = TEACHING`, the input opens for attempt 5.
  - **See the answer**, attempt 5 missed, or a 409 `turn_limit` → `revealAnswer`: `phase = REVEALED`, the answer card shows.
  - The app never sends attempt 6.

## File Structure
```
chalkroom/
├── settings.gradle.kts            # includes :app, :server, :shared
├── build.gradle.kts
├── gradle/libs.versions.toml      # version catalog
├── local.properties               # gitignored: sdk.dir, chalkroom.apiBaseUrl
├── shared/                        # DTOs used by app + server
│   └── src/main/kotlin/app/chalkroom/shared/
│       ├── Dtos.kt                # Create/Deck/Turn requests & responses, Exchange
│       └── Enums.kt               # StudentId, Stage
├── server/
│   ├── Dockerfile                 # fat JAR → JRE image, for Railway
│   └── src/
│       ├── main/kotlin/app/chalkroom/server/
│       │   ├── Application.kt     # Ktor setup: serialization, rate limits, routes
│       │   ├── Routes.kt          # POST /decks, POST /lessons/turn
│       │   ├── Config.kt          # env vars: GEMINI_API_KEY, model ids
│       │   ├── ai/
│       │   │   ├── GeminiClient.kt            # Google Gen AI SDK wrapper + error mapping
│       │   │   ├── MisconceptionGenerator.kt  # Pro call, deck responseSchema
│       │   │   ├── TurnJudge.kt               # Flash call, {addressed, reply} responseSchema
│       │   │   └── StudentPrompts.kt          # shared rules + Mira/Pip/Juno flaw prompts
│       └── test/
│           ├── kotlin/app/chalkroom/server/
│           │   └── evals/
│           │       ├── JudgeConsistencyEval.kt
│           │       ├── SayRulesEval.kt        # string checks + LLM grader
│           │       ├── FlawShapingEval.kt
│           │       ├── Grader.kt              # Gemini Pro grader, schema output
│           │       └── EvalReport.kt          # per case × run table → build/eval-report.md
│           └── resources/evals/cases/*.json   # fixed notes/misconceptions/refutations/explanations
├── app/
│   └── src/main/java/app/chalkroom/
│       ├── ChalkroomApp.kt        # Application, Koin start
│       ├── MainActivity.kt        # NavHost
│       ├── di/AppModule.kt        # Koin: Ktor client, ChalkroomApi, LessonRepository, ViewModels
│       ├── data/
│       │   ├── ChalkroomApi.kt    # Ktor client calls → Success/Failure
│       │   ├── LessonRepository.kt# StateFlow<ClassState> + lesson operations
│       │   └── LessonLogic.kt     # pure functions: stageFor, markStuck, revealAnswer, clearCurrent, clearRevealed…
│       ├── students/
│       │   ├── StudentCatalog.kt  # names, colors, fixed intro + stuck + relief lines, props
│       │   ├── StudentFigure.kt   # THE character composable (+ motes)
│       │   ├── StudentShapes.kt   # Mira/Pip/Juno Paths
│       │   ├── StudentMotion.kt   # breathing, blinking, expression tweens
│       │   └── ClearingChoreography.kt  # fixed timeline + haptics
│       ├── ui/theme/              # ChalkroomTheme: palette, type, shapes
│       ├── navigation/Routes.kt   # @Serializable routes
│       └── feature/
│           ├── classroom/         # ClassroomContract, ViewModel, Screen(+Content)
│           ├── pastenotes/
│           ├── choosestudent/
│           ├── reading/
│           ├── lesson/
│           └── closing/
├── devpost/                       # Devpost learning workspace
└── README.md                      # what it is, run steps, env vars, demo
```

## External Services and Dependencies

### Gemini API (via Google Gen AI Java SDK)
- **Call:** `client.models.generateContent(model, contents, config)`, where `config` is a `GenerateContentConfig` with `systemInstruction`, `responseMimeType("application/json")` and a `responseSchema`. Read the response's JSON text and parse it with kotlinx.serialization. Check the finish reason and candidates first, and treat safety blocks as a failure.
- **Auth:** `GEMINI_API_KEY` env on the server only.
- **Cost:** not verified here. Check current Pro and Flash pricing and free-tier limits before the first eval run. A lesson is about one Pro call plus a few Flash calls.
- **Limits:** project quota and rate limits in AI Studio, plus a budget alert.
- **Docs:**
  - SDK: https://github.com/googleapis/java-genai
  - Structured output: https://ai.google.dev/gemini-api/docs/structured-output
  - Models and ids: https://ai.google.dev/gemini-api/docs/models
  - Pricing: https://ai.google.dev/gemini-api/docs/pricing

### Railway
Docker deploy from the repo. Env vars are set in the dashboard. Costs follow Railway's current plan pricing; check before deploying. Docs: https://docs.railway.com

### App ↔ Server API
**`POST /decks`**
- Request: `{"notes": "...", "studentId": "MIRA"}`
- 200: `{"language": "en", "topicTitle": "Photosynthesis", "misconceptions": [{"id": "m1", "statement": "I think a plant gets its food from the soil.", "refutation": "Plants make their own food by photosynthesis; soil supplies water and minerals, not food."}]}`
- 400: `{"code": "notes_length"}`
- 429: rate limited.
- 502: `{"code": "ai_unavailable"}`

**`POST /lessons/turn`**
- Request: `{"statement": "...", "refutation": "...", "studentId": "MIRA", "stage": "NORMAL", "language": "en", "explanation": "...", "priorExchanges": [{"explanation": "...", "reply": "..."}]}` (`reply` may be `null` in prior exchanges)
- 200, turns 1–3: `{"addressed": false, "reply": "..."}`
- 200, turns 4–5, missed: `{"addressed": false, "reply": null}`
- 200, any turn, addressed: `{"addressed": true, "reply": "..."}`
- 400: `explanation_length`.
- 409: `{"code": "turn_limit"}` when `priorExchanges` already holds `MAX_TURNS` (5) messages. No model call.
- 429, 502: same as above.

## Important Failure Modes
- **AI or network fails during reading** → no motes are faked. "Mira lost the thread. Your notes are still here." and **Try again**. The notes are intact (`prd.md > States and Boundaries`).
- **AI or network fails during a turn** → the student says nothing. Your text returns to the input, the misconception stays, the try count is unchanged, and a calm **Try again** appears.
- **Turn 4 or 5 fails on the network** → same as above: nothing changes, no stuck line or answer card, your text returns.
- **409 `turn_limit`** (should never happen, since the app stops at 5) → not an error state: the app shows the answer card.
- **Model writes a reply on a judge-only miss** → the endpoint drops it (`reply = null`), so it can never reach the student. The turn-limit eval checks this.
- **Judge inconsistency on borderline explanations** → caught by the evals (3/3 rule). The fix is to tune the prompt or move rounds to Gemini Pro via `TURN_MODEL`.
- **Rate limit hit (429)** → the same calm error state. No special UI.

## What Was Simplified and Why
- **Memory-only state** instead of a database. The PRD says nothing persists after close. A database would mean Room plus migrations for no demo value.
- **Refutation in plain JSON, kept in app memory and rendered only on the answer card** instead of encrypted tokens or server sessions *(learner choice)*. The server stays stateless and redeploys can't break paused lessons. Tradeoff: a curious user could read the refutation from network traffic. The fuller version would seal it server-side (AES-GCM) or keep server sessions.
- **Per-IP rate limits plus a spend cap** instead of accounts or App Check. Enough for a public demo with no logins. The fuller version would need auth or Play Integrity.
- **`:shared` as a JVM library** instead of Kotlin Multiplatform. Android and the server are both JVM. The web version later would convert it to KMP (`commonMain`), a small change since it's only DTOs.
- **Fixed self-introductions, stuck lines and relief lines** instead of AI-generated ones. A learner decision, `prd.md > Product Decisions`, which saves a call and an error state.
- **Characters drawn in code with tweens**, with the stand-in tiers from `prd.md > What We're Building` built after the must-haves.

## Decisions and Open Issues

**Learner decisions:**
- **Gemini via the Google Gen AI Java SDK:** the current Pro model for reading, Flash for rounds (`TURN_MODEL`), Pro as the grader. Exact ids verified at build time. The evals decide whether Flash holds.
- **Server:** a Ktor backend in the same Gradle project, deployed to Railway via Docker. The key exists only in the server env.
- **The server owns all AI logic:** prompts, refutation generation and the yes/no decision. The app shows only wording, replies and reactions.
- **No sealed tokens.** `/decks` returns the refutation in plain JSON. The app keeps it in memory, renders it only on the answer card when turns run out, and sends it back each turn. The student never says it. The server stays stateless. This is a UI rule.
- **The app decides the stage.** The AI only writes the words. The server enforces the turn limit too (`MAX_TURNS = 5`, `JUDGE_ONLY_FROM_TURN = 4`).
- **Language:** `/decks` detects it and the topic title comes back in that language. The app sends the language on each turn.
- **A turn sends** only this misconception's prior exchanges.
- **Abuse protection:** per-IP rate limit, 8,000-character cap, AI Studio quota/rate limits and a budget alert.
- **Conventions:** MVI, Koin, type-safe Navigation Compose, Ktor client/server + kotlinx.serialization, memory-only repositories with StateFlow.
- **Stage timing:** attempts 1, 2, 4 and 5 are NORMAL. Attempt 3 is SIMPLIFY. The placeholder shows from the 4th try.
- **Turn limit:** at most 5 messages per misconception. A missed 4th shows the stuck line and **See the answer** / **One last try**. A missed 5th, See the answer, or a 409 shows the answer card. A revealed misconception clears but never enters What stuck.
- **Evals:**
  - 3/3 consistency with borderline cases, plus the turn-4 no-reply case.
  - String checks plus a Gemini Pro grader for say-rules.
  - A cross-student flaw-shaping check.
  - A pass/fail table per case × run.

**Derived by the agent from those decisions (implementation detail):**
- The file layout, DTO names, and the Choreography/Figure split.
- The palette hex values and rounded-sans font suggestion (tunable).
- Rate limit numbers (20/h decks, 150/h turns) and a 2,000-character explanation cap. Adjust freely.

**Learner uncertainty explored: "How do I judge consistently without grading the user?"**
- **What clarified it:** the judge is a yes/no structured field grounded in a hidden refutation. The user never sees it as a verdict, only as the student's reaction.
- **How consistency gets checked during the build:** the 3-run, all-must-agree rule on about 6 judge cases, including borderline ones. If a case flakes, the per-run table shows it, and the fix is the prompt or the model, not the UI.

**Carried over from `prd.md > Open Questions`:** the topic name comes from `/decks` (`topicTitle`, in the notes' language). Resolved.

**To verify early in the build** (no live lookup was done while writing this):
- Current library versions.
- Current Gemini Pro and Flash model ids and pricing.
- The Gen AI Java SDK `responseSchema` API used from Kotlin, and how safety blocks surface.
- The Railway Docker flow and pricing.
- The haptic constants' feel on a real device.

## Revisions
- **Model setup changed** (learner decision): decks `gemini-3.6-flash` (thinking LOW, 8192 max tokens), rounds `gemini-3.5-flash-lite` (thinking MINIMAL, 1024 max tokens, judge + reply still one call), grader `gemini-3.6-flash` (thinking LOW); temperature 1 for all. Replaces the Pro/Flash/Pro plan; the earlier Pro preview hit `429 RESOURCE_EXHAUSTED` during evals.
- **Generator self-labels each misconception's flaw** (`flaw` enum generated before `statement`); the server drops items whose label isn't the student's flaw. Evals showed prompting alone let Mira drift into Pip's confusion flaw. `/decks` response shape is unchanged.
- **Flaw-shaping grader votes 3× and takes the majority per belief**: single grader calls classified the same kind of belief differently between runs.
- **Decks are 3, from 5 candidates** (learner decision, replaces "1–8 decided by the notes"): schema `minItems 5 / maxItems 8`, server keeps 3 (own flaw first), one retry when short, never zero unless `nothingToLearn`. The flaw filter alone could leave 2, and the model sometimes returned only 2.
- **Every belief must be false as stated** (all students; Mira's omission must make the claim wrong): on `gemini-3.6-flash` Mira wrote true-but-incomplete beliefs ("arteries carry blood away from the heart").
- **Say-rule grader narrowed:** a pushback leaks only if it states or paraphrases the refutation; asking about the missing part is allowed (learner decision). The student prompt still forbids hinting.
