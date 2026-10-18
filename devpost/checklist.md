---
doc: checklist
status: approved
---

# Build Checklist

Build mode: fast

## Slices

- [x] **1. A notes paragraph comes back as Mira's misconceptions**
  Becomes usable: `curl POST /decks` against the local server returns a real Gemini-generated deck (language, topicTitle, 0–8 first-person misconceptions with refutations) for Mira, Pip or Juno.
  Why now: The riskiest unknowns live here (Gen AI Java SDK from Kotlin, `responseSchema`, current model ids, safety-block shape). Bootstrapping the Gradle project and `:shared` DTOs rides along. Proves the kernel's first half with runnable evidence.
  PRD ref: `prd.md > Reading and Misconception Creation`, `prd.md > Students and Their Flaws`
  Spec ref: `spec.md > Shared Contract (:shared)`, `spec.md > Server: Deck Endpoint (POST /decks)`, `spec.md > Server: Misconception Generator`, `spec.md > Server: Student Prompts`, `spec.md > Server: AI Error Mapping`, `spec.md > Stack`, `spec.md > File Structure`
  Build: Gradle wrapper + version catalog with `:shared` and `:server`; all DTOs/enums/`TurnLimits`/`ErrorBody` in `:shared`; `Config.kt`, `GeminiClient.kt` (SDK wrapper, finish-reason/empty/parse → `AiUnavailable`), `StudentPrompts.kt`, `MisconceptionGenerator.kt`, `Application.kt` + `Routes.kt` with `/decks` (150–8,000 validation, ids assigned, 502 mapping), per-IP rate limits.
  Verify (mechanical): `./gradlew :server:build` passes; with `GEMINI_API_KEY` set, `./gradlew :server:run`, then curl `/decks` with a short biology paragraph for MIRA returns 200 with ≥1 first-person misconception; 100-char notes → 400 `notes_length`; bad key → 502 `ai_unavailable`.
  Learner check: Start the server with your key, curl `/decks` with your own notes for two students, and read whether the misconceptions feel believable and flaw-shaped.
  Commit: `Add shared DTOs and /decks misconception generator`

- [x] **2. An explanation gets judged yes/no with an in-character reply**
  Becomes usable: `curl POST /lessons/turn` returns `{addressed, reply}`; vague explanations get pushback, precise ones an acknowledgment that echoes your words; turn 4+ misses return `reply: null`; a 6th turn is 409.
  Why now: Second half of the kernel. Must exist before evals can test it.
  PRD ref: `prd.md > Teaching a Misconception`, `prd.md > What the Student May and May Not Say`, `prd.md > Getting Unstuck`
  Spec ref: `spec.md > Server: Turn Endpoint (POST /lessons/turn)`, `spec.md > Server: Turn Judge`, `spec.md > Server: Abuse Protection`, `spec.md > App ↔ Server API`
  Build: `TurnJudge.kt` (Flash, low temperature, `{addressed, reply}` schema, NORMAL/SIMPLIFY/judge-only prompt rules), `/lessons/turn` route with length caps, turn counting, 409 `turn_limit` without model call, server-side null-out of judge-only misses.
  Verify (mechanical): `./gradlew :server:build`; curl a vague explanation → `addressed:false` + reply; precise → `addressed:true` + reply; 3 prior exchanges + vague → `reply:null`; 5 prior → 409; 2,001-char explanation → 400.
  Learner check: Curl one vague and one precise explanation for the deck from slice 1 and judge whether the replies stay in character and never leak the answer.
  Commit: `Add /lessons/turn judge with turn limits`

- [x] **3. Evals report judge consistency, say-rules and flaw shaping**
  Becomes usable: `./gradlew :server:test` runs ~10 real-API cases and writes `server/build/eval-report.md` with a ✓/✗ table per case × run.
  Why now: The learner's main learning goal; tells us whether Flash holds as judge before any UI is built on top of it.
  PRD ref: `prd.md > AI Quality Evals`, `prd.md > What the Student May and May Not Say`
  Spec ref: `spec.md > Evals (:server tests)`, `spec.md > File Structure`
  Build: JUnit 5 setup; `evals/cases/*.json`; `JudgeConsistencyEval` (6 cases × 3 runs incl. Turkish + turn-4 null-reply via the route), `SayRulesEval` (string check + `Grader`), `FlawShapingEval` (3 notes × 3 students), `EvalReport`. Gated by `assumeTrue`. Tune prompts / `TURN_MODEL` if cases flake.
  Verify (mechanical): Without key: tests skip, build green. With key: all cases pass 3/3 and the report file exists with the per-run table.
  Learner check: Open `server/build/eval-report.md` and check whether the borderline cases and reasons match your own judgment.
  Commit: `Add Gemini evals with per-run report`

- [x] **4. The classroom opens and Mira reads your notes on the emulator**
  Becomes usable: On the emulator: classroom with Mira, Pip and Juno drawn in code (breathing, blinking) → Teach something → paste notes (150–8,000 limits) → choose student with fixed intros → Reading screen calls the local server, motes appear one by one, count spoken, student looks up. Empty deck and failure states work; Back cancels.
  Why now: First visible app; bootstraps `:app` (Compose, Koin, Navigation, Ktor client, theme) and the one `StudentFigure` every later screen reuses.
  PRD ref: `prd.md > The Core Journey` (steps 1–4), `prd.md > Screens and Layout > Classroom / Paste Notes / Choose Who You Teach / Reading`, `prd.md > Look and Feel`, `prd.md > States and Boundaries`
  Spec ref: `spec.md > Look and Feel`, `spec.md > App: Student Catalog`, `spec.md > App: Student Figure (character component)`, `spec.md > App: Chalkroom API Client`, `spec.md > App: Lesson Repository`, `spec.md > App: Screens`, `spec.md > App: Navigation`, `spec.md > Where It Runs and How Someone Tries It`
  Build: `:app` module (minSdk 26, `BuildConfig.API_BASE_URL` from `local.properties`), `ChalkroomTheme`, `StudentCatalog`, `StudentShapes`/`StudentMotion`/`StudentFigure` with motes, `ChalkroomApi`, `LessonRepository` + `LessonLogic.startLesson`, Classroom/PasteNotes/ChooseStudent/Reading MVI screens, routes.
  Verify (mechanical): `./gradlew :app:assembleDebug` passes; install on `emulator-5554`, drive the flow with `adb -e` input + screenshots against the local server; confirm motes count equals deck size, a 100-char paste keeps Continue disabled, server stopped → "lost the thread" + Try again.
  Learner check: Run the app on the emulator, paste your notes, pick a student and watch them read. Say whether the classroom and characters feel like the picture-book room you pictured.
  Commit: `Add classroom, paste, choose and reading screens`

- [x] **5. You can teach a misconception until it clears, through to closing**
  Becomes usable: Full core journey on the emulator: the student states misconception 1; vague → unconvinced reaction + reply; precise → clearing choreography at fixed timings with two haptics, counter + bar drop, reply echoes your words, next misconception; after the last one, Closing with "Nothing left in [Name]'s head", relief line, What stuck, notebook closes with topicTitle, then Teach [Name] something else / Back to class.
  Why now: This is the kernel in the product; everything after it is unstuck/pause polish.
  PRD ref: `prd.md > Screens and Layout > Lesson / Closing`, `prd.md > Teaching a Misconception`, `prd.md > Closing and What Stuck`
  Spec ref: `spec.md > App: Clearing Choreography`, `spec.md > App: Screens`, `spec.md > Data Model`, `spec.md > The Core Journey Through the System`
  Build: `LessonLogic` (`recordTry`, `clearCurrent`, `finish`, `stageFor`) + unit tests; Lesson screen (current-line card, last explanation, input lock, thinking look, unconvinced reaction, failure → text returns + Try again); `ClearingChoreography`; Closing screen.
  Verify (mechanical): `./gradlew :app:testDebugUnitTest` (lesson logic) passes; on emulator drive a 2–3 misconception deck: a vague try leaves counter unchanged, precise try drops it by 1, closing shows What stuck with the sentences sent.
  Learner check: Teach a student your notes from start to finish, including one deliberately vague explanation, and tell me how the clearing moment feels.
  Commit: `Add lesson loop, clearing sequence and closing`

- [x] **6. Getting unstuck and pausing work like the PRD says**
  Becomes usable: Attempt 3 asks you to start from the beginning (placeholder from try 4); missed 4th → fixed stuck line + See the answer / One last try; answer card → Continue clears without entering What stuck; never a 6th try. Back pauses; classroom board shows the most recent paused lesson; tapping the student/board/Continue resumes exactly (incl. stuck/answer card); several paused at once; closing returns the desk to idle.
  Why now: Depends on the lesson loop; completes every must-have behavior.
  PRD ref: `prd.md > Getting Unstuck`, `prd.md > Pausing and Resuming`, `prd.md > Screens and Layout > Classroom`
  Spec ref: `spec.md > Data Model` (rules, stage table, turn limit), `spec.md > App: Lesson Repository`, `spec.md > App: Screens`
  Build: `markStuck`, `chooseLastTry`, `revealAnswer`, `clearRevealed`, `pause` + unit tests; stuck/answer-card UI; 409 → answer card; board, paused desks (open notebook + motes), resume paths.
  Verify (mechanical): Unit tests for the full stage/turn-limit table and reveal-vs-clear rules pass; on emulator: 4 vague tries → stuck buttons, See the answer → card → Continue → counter −1 and item absent from What stuck; Back mid-lesson → board shows it, resume restores same line/count/phase.
  Learner check: Get a student stuck on purpose, try both buttons, then leave mid-lesson and come back from the board.
  Commit: `Add stuck flow, answer card, pause and resume`

- [ ] **7. Design pass**
  Becomes usable: The whole app wears the brief's look: cream/slate/gold palette, Nunito + Lora, the "ledge" on every button and card; mouthless students that breathe, blink and glance on their own clocks with arc-arranged motes; the lesson as a student card + dark notched line card + "You" card + pinned gold input; the classroom as one room (wall, board, shelf, clock, bin, wooden floor, angled desks with props and chairs); closing, paste, choose, reading and error states restyled. Not droppable.
  Why now: All must-have behavior works end to end; the look and feel gets its own pass before the optional upgrades and the deploy.
  PRD ref: `prd.md > Screens and Layout`; `devpost/design-brief.md`
  Spec ref: `spec.md > Look and Feel`, `spec.md > App: Student Figure (character component)`; `devpost/design-brief.md`
  Build: Tier order from the brief (tier 1, then 2, tier 3 only if time is left), in sub-slices, each checked on the emulator with before/after screenshots in `devpost/screenshots/` and committed: (a) design tokens + students (§0, §1); (b) lesson (§2) — then pause for the learner to review screenshots; (c) classroom (§3); (d) closing (§4); (e) paste, choose, reading, errors (§5); (f) tier 3 extras if time is left (§6). Lesson logic and the 900ms clearing timings stay untouched.
  Verify (mechanical): `./gradlew :app:assembleDebug` + unit tests pass after each sub-slice; before/after emulator screenshots of each screen saved; core journey still completes; system "remove animations" stops idle loops.
  Learner check: Review the lesson before/after screenshots after sub-slice (b); at the end, walk the full core journey on the emulator and compare each screen with the brief.
  Commit: one per sub-slice, e.g. `Design pass: tokens and students`, `Design pass: lesson screen`, …
  Polish items (learner review of the lesson sub-slice, fix later, not now):
  - [ ] Unconvinced face reads as angry; make it doubtful (flat half-down lids, slight lean back).
  - [ ] Scroll-down button overlaps the input field on the lesson screen.
  - [ ] Student ~15–20% larger on the lesson stage.

- [ ] **8. Stand-in upgrades: whatever slice 7 left of reading words, ambient life, eyes-lead, history sheet and props**
  Becomes usable: Any of these not already delivered by the design pass (the brief covers eyes-lead, room life, desk props, reading words and the exchanges pill): words drift from notes into the head during Reading; one small ambient action per student every 12–20s; eyes lead and head follows; "N exchanges" sheet; desk props (doodle, askew notebook, plant).
  Why now: PRD orders these after the must-haves; each is optional if time runs short.
  PRD ref: `prd.md > What We're Building` (stand-ins 1–4)
  Spec ref: `spec.md > App: Student Figure (character component)`, `spec.md > App: Screens`
  Build: Upgrades 1→4 in PRD order, each kept behind the same `StudentFigure` API.
  Verify (mechanical): `./gradlew :app:assembleDebug` + unit tests pass; emulator screenshots show each upgrade; core journey still completes.
  Learner check: Sit in the classroom for a minute and run one lesson; say whether the extra life helps or distracts.
  Commit: `Add stand-in upgrades for reading, ambient life and history`

- [ ] **9. Judges can install the APK and try it without a key**
  Becomes usable: Server deployed on Railway via `server/Dockerfile`; debug APK built against the Railway URL, attached to a GitHub Release; README with what it is, install-the-APK steps, and full local-run steps (server env vars, `local.properties`, evals).
  Why now: Needs the finished app and server; judges' path to trying it.
  PRD ref: `prd.md > The Core Journey`
  Spec ref: `spec.md > Where It Runs and How Someone Tries It`, `spec.md > External Services and Dependencies > Railway`
  Build: `server/Dockerfile` (fat JAR → JRE); README; Railway deploy (learner sets env vars in the dashboard); `assembleDebug` with `chalkroom.apiBaseUrl` = Railway URL; GitHub Release with the APK (learner approves publishing).
  Verify (mechanical): `./gradlew :server:buildFatJar` passes; curl the Railway `/decks` returns 200; the release APK installed on `emulator-5554` completes Reading against Railway.
  Learner check: Install the APK from the Release on a fresh emulator and teach one misconception against the deployed server.
  Commit: `Add Dockerfile, README and release setup`

## Hands-on Checkpoints

- [x] Early usable behavior explored — after slice 2 (curl `/decks` + `/lessons/turn` with your own notes; shapes prompts before evals lock them in), and after slice 5 (first full teach on the emulator)
- [ ] Final kick-the-tires exploration and feedback completed

## Final Review

- [ ] Final review complete — feedback resolved and learner confirms ready to ship

## Code Tour and App Map

- [ ] Learning activity complete — guided route, focused alternative, prior practice connected, or brief recap
- [ ] Optional edit and transfer reflection addressed — offered/declined/already covered/not applicable as appropriate
- [ ] `devpost/app-map.html` generated from finished code, checked, and shown, including a project-grounded practice to reuse

Activity and evidence: 
Route and stops: 
Edit outcome: 
Reflection: 
Activity mode: 

## Revisions

- Debug APK now distributed via GitHub Releases, pointing at the deployed server (spec said "APK is not distributed") — learner ground rule: judges must try the app without their own key.
- On-device checks and demo recording use the Android emulator only (spec mentioned a device/scrcpy and checking haptic feel on a device) — learner ground rule; haptic feel is judged on the emulator or left for the learner.
- Model setup changed to `gemini-3.6-flash` (decks, grader) and `gemini-3.5-flash-lite` (rounds), temperature 1, per-role thinking/max-token settings — learner decision after the Pro preview hit quota (429) during evals; see `spec.md > Revisions`.
- Generator now self-labels each misconception's flaw and the server keeps only the student's own flaw; grader votes 3× per belief — evals showed flaw drift (Mira → confusion) and single-call grader noise.
- Slice 3 committed against the learner's thresholds (judge 3/3 all cases; say-rules ≥ 90% with the no-refutation-leak rule at 100%; flaw shaping ≥ 80% majority vote) instead of all-must-pass. The no-leak rule failed (17/18) on the single allowed run; recorded as a known limitation in `server/eval-report.md`, not tuned per learner instruction.
- `/decks` tops up to 3 with non-matching items when the flaw filter leaves fewer (`selectMisconceptions`, unit-tested). It can't add items the model never returned: a smoke call on `gemini-3.6-flash` returned only 2.
- Decks fixed at 3 from 5 candidates with one retry; every belief must be false as stated; say-rule leak check narrowed to stating/paraphrasing (re-scored: 18/18). Flaw shaping re-run once: 9/9 decks of 3, true-but-incomplete beliefs gone, overall 85.2%, but Mira 7/9 (target 7/8) and one Mira/Pip overlap on supply_demand. Recorded in `server/eval-report.md`, not tuned.
- Slice 4 build setup: AGP 9.4.1 needs Gradle ≥ 9.6, so the wrapper moved to 9.8.1; the current AndroidX/Compose releases need compileSdk 37 (targetSdk 37). The UI font is the system sans for now (Nunito was only a suggestion; stand-in for slice 7). Reading lands on a minimal Lesson view (student, counter, misconception 1) that slice 5 turns into the teaching screen.
- Slice 5 pulled the stuck flow forward: all `LessonLogic` steps (`markStuck`, `chooseLastTry`, `revealAnswer`, `clearRevealed`, `pause`) plus the stuck buttons, answer card and 409 → answer card are built and tested, so a hands-on lesson can never dead-end at turn 4. Slice 6 is left with pause/resume: board, paused desks and the resume paths. Verified on emulator: vague try keeps the counter, precise try clears at the fixed timeline with pad squiggles, failed turn returns your words, attempt 3 asks to start over, missed 4th shows the stuck line, See the answer → Continue drops the counter and stays out of What stuck, closing hides What stuck when nothing was cleared by you.
- After the slice 5 hands-on check (learner feedback): acknowledgments after a clear never ask a question (turn prompt + say-rule string check, no "?" in a hit reply); a deck's beliefs must each be about a different fact (deck prompt); closing options pinned at the bottom with What stuck scrolling above, and the closing scrolls the notebook into view before it closes. Smoke-checked with one `/decks` and one `/lessons/turn` call; no full eval run, per learner instruction.
- Slice 6: `lastPausedStudentId` is now derived from a `pauseOrder` list, so when the board's lesson is resumed the board falls back to the next most recently paused one instead of going blank while other lessons are still paused. The lesson pauses when its screen is left (ViewModel cleared) and resumes when it opens; a lesson left during its last clearing opens straight into the closing. Verified on emulator: Back mid-lesson → board + open notebook + motes; resume from board, desk tap and Choose → Continue restores the same line and count, including the stuck buttons and the answer card; Mira and Pip paused at once; closing returns the desk to idle and the board falls back to Mira.
- Checklist reordered by the learner: new slice 7 "Design pass" (not droppable, built from a design brief the learner will send); stand-in upgrades move to slice 8 (references to "slice 7" above for stand-ins now mean slice 8); deploy becomes slice 9.
- Slice 7 filled from `devpost/design-brief.md` (learner's brief): tiered sub-slices with before/after screenshots, a learner review stop after the lesson sub-slice. The brief also covers eyes-lead, room life, desk props, reading words and the exchanges pill, so slice 8 shrinks to whatever slice 7 leaves.
