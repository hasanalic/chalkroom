---
doc: scope
status: approved
---

# Chalkroom

Android app where you learn by teaching: AI students learn your notes wrong, and you explain until they get it.

## The Unique Kernel
An AI student reads *your* source text and learns it **wrong**: it states one specific, believable misconception in its own voice. It changes its mind only when your explanation actually addresses that misconception. Otherwise it stays puzzled and pushes back. This happens in a **classroom with characters**, not a chat with a bot. Students sit at desks, have personalities and react visibly (think, get puzzled, "aha", take notes). It never fails or grades you.

Personality decides **which** misconception a student picks, not just how it talks. Each student has a characteristic thinking flaw: **Mira** leaves out a key step or condition, **Pip** confuses similar things, and **Juno** reverses cause and effect. Given the same notes, three students hold three different misconceptions.

## Who It's For
A university student the night before an exam, with their own lecture notes on one topic (e.g., a biology chapter). They've read it twice and think they know it. Today they reread or highlight. Teaching it to the students shows them where they don't actually know it.

## The Core Loop
1. In the classroom, tap "Teach something" and paste the notes.
2. Choose who you teach: Mira, Pip or Juno. The student "reads" the notes (you watch it read while the AI works), and its misconceptions appear as motes.
3. The student states its first misconception in its own voice, e.g., "I think a plant gets its food from the soil."
4. Type an explanation. The student thinks.
   - If the explanation addresses the misconception, its face clears, it has a small "aha" and writes in its notebook.
   - If not, it stays puzzled and pushes back with a follow-up in its own voice. Try again. There's no counter, penalty or score.
   - Each misconception allows at most 5 explanations. If the 4th still misses, the student says it's stuck, and you choose: see the answer, or one last try. When turns run out, the app (never the student) shows the answer, and the mote clears without counting as something you explained.
5. Repeat for each misconception (1–8, depending on the notes), one at a time.
6. When all are cleared, the closing shows: the student's relief and "What stuck" (your own sentences, only for misconceptions your explanations cleared). Then back to class. You can leave at any time; the lesson pauses, and nothing counts as failed.

## Inspiration & Identity
A classroom, not a chat window. Roleplay: each student is a character with its own personality and its own way of misunderstanding. Characters are *designed*, not avatars. They're drawn in code (simple shapes) with expressions, breathing, blinking and smooth expression transitions. The tone is warm and safe, with no judgment.

## Why This Matters to the Learner
Learning: "how to make the AI behave reliably inside a product, not in a chat." That means a specific, believable misconception as structured output, a consistent "was it addressed?" judgment that never grades the user, and small evals instead of eyeballing. Secondary: keeping the agent on a slice-by-slice plan.

## What "Working" Looks Like
A one-minute demo:
- The classroom with three students at their desks.
- Tap one and paste real notes (a short biology paragraph).
- The student thinks, raises its hand and states a misconception the AI produced from that text.
- A vague explanation keeps it puzzled, and it pushes back in its own voice.
- A better explanation clears its face and it notes the point down.
- A second misconception gets cleared the same way.
- The closing shows: "Nothing left in its head", its relief, and "What stuck".

All lines come from the AI live, nothing scripted. **"Oh, that's cool" beats:**
1. The same student refuses the vague explanation and accepts the precise one.
2. Same text, different student, different misconception, because each student's thinking flaw shapes what it gets wrong.

## The POC Boundary
- One classroom screen with three students. All are teachable and the flow is the same for each. They differ by look and by personality. Each has a characteristic thinking flaw that determines which misconception it forms from the text.
- Characters are drawn in code (shapes), not image assets.
- Four expression states: idle, thinking, puzzled, got it. Code-driven motion (breathing, blinking, transitions, the mote clearing sequence). No frame-by-frame animation. Details in `prd.md`.
- Source text comes in by paste. Explanations are typed.
- 1–8 AI-generated misconceptions per text (about 3 in the demo), handled one at a time.
- AI judges whether each explanation addresses the current misconception and generates the pushback. The student speaks the language of the notes; the UI stays English.
- A small eval set (~10 cases) for misconception generation (including per-flaw differences) and the "addressed?" judgment.
- Android only.

## Build Order & Time
About two weeks until the deadline, so the learner keeps this scope. Order: **the AI part first** (misconception generation, the "addressed?" judgment, evals), **then the characters and classroom**.

## Later
Voice input, PDF/file upload, saving sessions and history, multiple topics, students talking to each other, new students arriving, progression or levels, accounts, a web version.

## Explicitly Cut
- **Scores, grades, attempt counters, "done" tallies, failure states.** (A descending "what's left" count is allowed.) These break the core rule that the app never grades the user.
- **Hardcoded or scripted lesson lines** (including in the demo). Every student line in the lesson must come from the AI, or the kernel is fake. Exception: each student's fixed self-introduction, stuck line and closing relief line, written once like a catchphrase.
- **Image/sprite assets and frame-by-frame animation.** Too much art work for 2–4 hours. Characters drawn in code with swapped poses prove the idea.
