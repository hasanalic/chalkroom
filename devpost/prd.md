---
doc: prd
status: approved
---

# Chalkroom — Product Requirements

Android app where a university student, the night before an exam, learns their own notes by teaching them to three AI students who each learn them wrong in their own way.
Source: `scope.md > The Unique Kernel`, `scope.md > Who It's For`.

## The Core Journey
Source: `scope.md > The Core Loop`.

1. **Arrive.** The app opens straight into the classroom. There's no onboarding or login. Mira, Pip and Juno sit at their desks, blinking and breathing. The board above the room is blank. There's one button: **Teach something**.
2. **Paste notes.** Tap *Teach something* and paste lecture notes (150–8,000 characters).
3. **Choose who you teach.** Mira, Pip and Juno each introduce themselves with a fixed line describing their own flaw, in their own voice. Pick one.
4. **Watch them read.** The student reads your notes (eyes scanning, pausing, a puzzled look). Misconceptions condense into motes over their head one by one. The count is said ("3 things don't click yet"), and the student looks up at you.
5. **Lesson.** The student speaks first, stating its current misconception in first person. You type an explanation and the student thinks.
   - **Not addressed:** a short unconvinced reaction, then a reply about what still doesn't make sense, from another angle. The count doesn't move.
   - **Addressed:** the clearing sequence. The mote dissolves, the count drops, the student writes it down and echoes your own words. Then the next misconception.
   - **Out of turns:** at most 5 explanations per misconception. If the 4th misses, the student says it's stuck and you pick **See the answer** or **One last try**. The answer appears on the app's own answer card, and **Continue** clears the mote the usual way.
6. **Closing.** When the last mote clears, the screen shows "Nothing left in Mira's head", Mira's own relief line, and **What stuck**: your sentences, one per misconception your own explanation cleared. The notebook closes.
7. **Next.** Teach this student something else, or go back to class.

You can go back at any point. The lesson pauses on that student, and nothing is lost. **Success** = you cleared every mote using your own explanations.

## Screens and Layout

### Classroom
- Teacher's point of view: you stand at the front, the students face you. It's one shared room, not a grid of cards.
- Three fixed seats, each behind its own wooden desk with a small personal prop: Mira a doodle, Pip an askew notebook, Juno a plant.
- **The board** above the room is blank when no lesson is in progress. When a lesson is paused, it shows the most recently paused one ("Mira left this here" / "3 things still don't click"). Tapping it resumes that lesson.
- A student with a paused lesson has their notebook open on the desk and motes over their head.
- Tapping a student: they look up at you with a small nod. If they have a paused lesson, it resumes.
- One primary button: **Teach something**.

### Paste Notes
- A text area for notes. For the session, it keeps the last pasted notes, so teaching the same text to another student takes two taps.
- The continue button stays disabled below ~150 characters, with a calm hint. Above 8,000, a character count shows and you trim before continuing. Nothing is cut silently.

### Choose Who You Teach
- Mira, Pip and Juno, each shown with their fixed self-introduction: a line written once per student, describing their own flaw in their own voice (like a catchphrase).
- A student with a paused lesson shows **Continue** instead of starting a new lesson.

### Reading
- Not a loading screen: the student reads your notes. There's no spinner, percentage or "generating" text.
- It can be cancelled at any time. Nothing is created, and you return to Paste Notes with your notes intact.

### Lesson
Shows, top to bottom:
- The student with motes floating around the head.
- The counter: "what's left in Mira's head: 3", with a bar under it that drains.
- The **student's current line** in one card under them.
- **Your last explanation**, quieter, beneath it.
- The input.
- When the student is stuck: two buttons, **See the answer** / **One last try**, with the input locked until you pick one.
- When the answer is shown: the **answer card**, the app's own card (not a student line) showing what's actually true, with one **Continue** button.

Earlier exchanges about the *current* misconception open in a sheet ("3 exchanges"), never as a chat log. Back is always available.

### Closing
- "Nothing left in Mira's head" (never a zero). The student settles in their own way and says their fixed relief line, written once per student, in their own voice. It never praises you.
- **What stuck:** your own sentences, one per misconception your own explanation cleared, and nothing else. Misconceptions whose answer was shown are not listed. If none were cleared by you, What stuck is left out. Tapping one shows the misconception it cleared.
- The notebook closes with the topic name on its cover and one soft haptic. Only then do the two options appear: **Teach Mira something else** (goes to Paste Notes with Mira pre-chosen) and **Back to class**.

## Look and Feel
- **Mood:** warm, calm, picture-book classroom. Cream and paper tones, wooden desks, a board above the room.
- **Never in teaching:** red, shake or warning colors.
- **Explicitly not:** a generic AI-app look. No gradients, no chat UI.
- **Characters:** each is a single soft, flat-colored silhouette with a thick dark outline, capsule-shaped eyes and a simple mouth. They're drawn in code and shown as a bust behind a desk, and each has its own color.
  - **Mira:** a soft bean-shaped body leaning to the right, with one small tuft on top, big friendly eyes.
  - **Pip:** a wide, low, slightly lopsided body with three short pointed tufts on top, eyes tilted.
  - **Juno:** a tall pear-shaped body with a round, pale face area, and a small star mark above it.
- **One component per student,** used unchanged everywhere they appear. The student and their motes are one component.
- **Motion character:** faces change more often than bodies move. Blinking runs on each student's own clock, with small passing expressions in character. Breathing is gentle. Eyes lead and the head follows.
- **Motes:** small cloudy shapes floating around the head, one per misconception.

## Features and Behavior

### Students and Their Flaws
Source: `scope.md > The Unique Kernel`.

| Student | Personality | Flaw that decides *which* misconceptions they form |
|---|---|---|
| **Mira** | curious, plain | Simple omissions: leaves out a key step or condition. |
| **Pip** | fast, careless | Confuses similar things. |
| **Juno** | calm, questioning | Reverses cause and effect. |

- [ ] Given the same notes, Mira, Pip and Juno produce different misconceptions, and each one visibly follows that student's flaw.
- [ ] Each student's self-introduction is a fixed line describing their own flaw in their own voice.

### Reading and Misconception Creation
- 3 misconceptions per lesson. Fewer (never zero) only if the AI can't produce 3, and the spoken count says the real number ("2 things don't click yet").
- Every misconception is false as stated and contradicts the notes. A true-but-incomplete statement is never a misconception.
- Every misconception comes from the pasted notes and follows the chosen student's flaw.
- **Language:** the student speaks the language of the notes (misconceptions and replies). The interface stays English.
- [ ] Notes in another language (e.g., Turkish) produce misconceptions and replies in that language, while UI labels stay English.
- [ ] Motes appear one by one during reading, and the spoken count matches the number of motes.
- [ ] Reading ends with the student looking up and stating misconception 1 in first person.
- [ ] Notes with nothing to misunderstand: no motes are created, a plain message says there was nothing in them to misunderstand, and you're back on Paste Notes with your notes intact.

### Teaching a Misconception
- The student always speaks first. You never face a blank box.
- While a round runs, the student looks up and to the side (thinking), and the input is locked.
- The "addressed?" decision is **yes/no**. There's no partial credit.
- **Not addressed:** a short, per-student unconvinced reaction (never "wrong", no red, no shake), then their reply. The reply says what they still don't get, from another angle, and always leaves you something to answer. The counter doesn't move.
- **Addressed (the clearing sequence):** fixed timings from the moment the answer is "yes". The empty first 400ms is the point: it should read as a mind changing, not a form validating.

  | Time | What happens |
  |---|---|
  | 0–400ms | Hold: nothing happens. Soft haptic. |
  | 480ms | The mote's fog resolves. |
  | 620ms | The mote drifts into the eye. |
  | 700ms | Eyes widen. |
  | 780ms | Counter drops. Soft haptic. |
  | 900ms | The student speaks, picking up your own words ("'Makes its own food'… so it isn't waiting on the soil at all."). |

  At the same time they pull out a small pad and write it down (abstract squiggles, never words). Then the next misconception becomes their line.
- [ ] A vague explanation leaves the mote and counter unchanged and gets an in-character pushback.
- [ ] An explanation that addresses the misconception triggers the clearing sequence at the timings above, with two soft haptics (hold and drop). The counter drops by exactly 1, and the student's line reuses words from your explanation.
- [ ] The counter and bar only ever go down. They never go up or reset within a lesson.

### What the Student May and May Not Say
These apply to every AI-generated student line in the lesson.
- Never states or hints at the correct answer (the refutation). The app shows it only on the answer card when turns run out, never in the student's voice.
- Never evaluates you: no "almost", "great" or "wrong".
- Never leaves the current misconception or brings up another one.
- May say what still confuses them, never what the answer is.
- Every reply leaves you something to answer.
- **No student line in the lesson is hardcoded.** Misconceptions, replies and acknowledgments come from the AI live (`scope.md > Explicitly Cut`). The only fixed student lines are each student's self-introduction, closing relief line and one exception inside the lesson: the per-student **stuck line**.
- [ ] Across the eval cases, no student line contains the correct answer, an evaluation word, or a different misconception.

### Getting Unstuck
Counted per misconception. At most **5 explanations** per misconception; the turn count is never shown.
- **3rd try:** the student's reply asks you to explain it from the very start, as simply as you can. From then on (4th try onward), the input placeholder reads "start from the beginning".
- **4th and 5th try:** only judged. A hit clears the mote as usual; a miss gets no AI reply.
- **4th try missed:** the student is stuck. They play their unconvinced reaction and say their own **fixed stuck line** (one per student, written once in their voice). It invites either option and judges nothing. Two buttons appear: **See the answer** / **One last try**. The input stays locked until one is picked.
  - **See the answer:** nothing is sent. The answer card appears.
  - **One last try:** the input opens for the 5th try. A miss there shows the answer card too.
- **The answer card** shows the refutation, on the app's own card, never said by the student. **Continue** clears that mote with the same clearing sequence and the counter drops, but the misconception does not go into What stuck, because your own words didn't clear it. Seeing the answer is never a failure.
- [ ] A miss on the 4th try shows the student's fixed stuck line and the two buttons, with the input locked; there is no AI reply.
- [ ] **See the answer** sends nothing and shows the answer card. **One last try** opens the input once; a miss there shows the answer card.
- [ ] **Continue** on the answer card runs the clearing sequence and drops the counter by 1, and that misconception is absent from What stuck.
- [ ] A misconception never receives a 6th explanation.

### Pausing and Resuming
- Each student can hold one lesson. Backing out pauses it: the notebook stays open and the motes stay over their head.
- Several students can have paused lessons at once (e.g., Mira paused while you teach Pip).
- Resume by tapping the student in the classroom, by **Continue** on Choose Who You Teach, or by tapping the board (most recently paused lesson).
- [ ] After backing out of a lesson and resuming it, the same misconception, count and current line are shown, including a stuck student with their two buttons or an open answer card.

### Closing and What Stuck
- [ ] After the last mote clears, the closing shows "Nothing left in [Name]'s head", the student's fixed relief line, and one of your own sentences per misconception your explanation cleared (none for revealed ones).
- [ ] The notebook closes with the topic name on its cover and a soft haptic before the two options appear.
- [ ] Tapping a "What stuck" sentence shows the misconception it cleared.
- [ ] After closing, that student's desk returns to its idle state (no open notebook, no motes).

### AI Quality Evals
Source: `scope.md > The POC Boundary`, `scope.md > Why This Matters to the Learner`.
- About 10 cases covering: misconception generation per student flaw (same notes give three different, flaw-shaped misconceptions), the yes/no "addressed?" judgment (vague vs. precise explanations), the "may and may not say" rules, and the turn limit (a missed 4th try returns no reply).
- [ ] The eval set runs repeatably and reports pass/fail per case.

## States and Boundaries
- **First launch / empty classroom:** blank board, students idle, *Teach something* available.
- **Notes too short:** continue is disabled with a calm hint.
- **Notes too long:** a character count shows, and you trim before continuing.
- **Nothing to misunderstand:** no motes, a plain message, and back to Paste Notes with the notes intact.
- **AI/network failure during reading:** no motes are faked. A calm message ("Mira lost the thread. Your notes are still here.") and one tap to try again.
- **AI/network failure during a round:** the student says nothing. Your text goes back into the input, the misconception stays on screen, and the round doesn't count (including toward the try count). A calm "Try again".
- **Out of turns:** the stuck line and two buttons after a missed 4th try; the answer card after **See the answer** or a missed 5th try.
- **Reading cancelled:** nothing is created, and you're back with your notes.
- **Lesson paused:** the notebook is open on the desk, motes are over the head, and the board shows the most recent paused lesson.
- **Persistence:** everything lives only while the app is open. Nothing is saved after the app closes.

## Product Decisions
- **One student per lesson**, not the whole class on one text. This replaced the earlier chalkboard-for-everyone idea. The paste screen keeps the last notes, so "same notes, different student" is two taps.
- **Personality decides which misconceptions are formed**, not just the voice. This makes beat 2 of the demo possible.
- **Self-introductions, stuck lines and relief lines are fixed per student**, like a character's catchphrase. This saves an AI call and an error state. Every other *lesson* line stays AI-only.
- **The student speaks the language of the notes.** The interface stays English.
- **The clearing sequence has fixed timings** with a 400ms empty hold, so it reads as a mind changing, not a form validating.
- **The only progress shown is what's left**, a single number with a draining bar that never goes up or resets. There's never a bar of what's done.
- **No end-of-lesson summary from the student.** The closing reflects *your* sentences ("What stuck") plus the student's relief.
- **The "addressed?" decision is yes/no.** There's no partial credit, which keeps grading out.
- **Errors erase nothing.** A failed round never happened. Your text returns to the input.
- **At most 5 explanations per misconception, then the answer.** After a missed 4th try you choose the answer or one last try. The answer comes from the app's card, never from the student, and a revealed misconception never counts as something you explained. Seeing the answer is never a failure.
- **One lesson per student, paused on exit.** Leaving never discards.
- **3 misconceptions per lesson**, fewer only if the AI can't produce 3; the count always says the real number.
- **Session-only memory.** Nothing persists after the app closes.

## What We're Building
**Must be in for the demo:**
- Classroom with Mira, Pip and Juno drawn in code, with blinking and breathing everywhere.
- Paste Notes with length limits. Choose Who You Teach with fixed self-introductions.
- Reading with scanning eyes and motes appearing one by one.
- Lesson: motes, the descending counter and draining bar, current line, last explanation, and input. The thinking look while a round runs. The per-student unconvinced reaction.
- The clearing sequence at its fixed timings with two haptics, plus the pad-and-squiggles note moment.
- Getting Unstuck (3rd-try ask, 4th-try stuck line with See the answer / One last try, the answer card).
- Pausing and resuming, with the board showing the most recently paused lesson.
- Closing with "Nothing left in [Name]'s head", the fixed relief line, What stuck, and the notebook closing with the topic name and a haptic.
- All AI rules in *What the Student May and May Not Say*.
- The error and empty states above.
- About 10 eval cases.

**Stand-ins first, upgraded in this order if time allows:**
1. Reading: words drifting from the notes into the head (stand-in: scanning eyes and motes appearing).
2. Ambient classroom life: one small per-student action every 12–20 seconds, never two at once (stand-in: blink and breathe only).
3. Eyes lead, head follows (stand-in: simple look).
4. "N exchanges" history sheet and desk props (stand-in: none).

## Deferred From the POC
- **Saved sessions and history:** would need storage across launches. Session-only memory is enough to prove the kernel.
- **Accounts:** nothing is saved, so there's nothing to own.
- **Voice input:** typing proves the loop. Voice is a separate input problem.
- **PDF/file upload:** paste covers the demo.

## Possible Later Enhancements
Multiple topics per session, students talking to each other, new students arriving, progression or levels, a web version.

## Non-Goals
- **Grading, scores, attempt counts, "done" tallies, failure states:** these break the never-grade rule.
- **Hardcoded or scripted lesson lines,** even for the demo: these would fake the kernel. (Fixed self-introductions, stuck lines and relief lines are the deliberate exception.)
- **Image/sprite assets and frame-by-frame animation:** characters are drawn in code.
- **A chat-style interface or chat log:** Chalkroom is a classroom, not a chat.
- **The student giving or hinting at the answer:** you have to do the explaining. (The app's answer card, after turns run out, is not the student.)

## Open Questions
- **Topic name on the notebook cover.** *Assumption:* the topic name is derived from the notes during reading (e.g., a short title the AI returns alongside the misconceptions). Doesn't block `4-spec`, but `4-spec` should settle where it comes from.

Resolved: "Teach Mira something else" goes to Paste Notes with Mira pre-chosen. The stand-in upgrades are part of this build, after the must-haves.
