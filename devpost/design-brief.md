# Chalkroom — Design Brief (slice 7: Design pass)

The current UI is a working stand-in. This brief is the target look. Everything is drawn in code (Compose Canvas + shapes); no image assets. Work top to bottom: tiers 1–2 are the slice; tier 3 only if time is left.

## 0. Design language (applies everywhere)

**Feel:** a warm, tidy, slightly toy-like classroom. Flat colour, thick dark outlines on characters and objects, no gradients, no blur shadows. Depth comes from one device only: the **ledge**.

**The ledge.** Buttons and cards sit on a solid, flat "extrusion" of a darker shade directly under them: same shape, offset 4dp down (cards 3dp), no blur. Pressing a button moves it down onto its ledge (ledge shrinks to 0). This is the app's signature; use it on every button and every card.

**Palette (tokens in Theme.kt):**

| Token | Hex | Use |
|---|---|---|
| page | `#FFFDF5` | screen background (cream) |
| card | `#FFFFFF` | light cards |
| ink | `#2B364B` | text, outlines, dark surfaces (slate) |
| ink2 | `#3A4560` | tracks on dark surfaces |
| gold | `#F5C522` | primary button |
| goldLedge | `#C68800` | primary button ledge |
| coral | `#F59B74` / ledge `#C85B35` | secondary button |
| lavender | `#EAE6F8` | quiet surfaces |
| lavender2 | `#B9B7E8` | ledge under dark cards |
| muted | `#6E7278` | secondary text |
| line | `#E7E2D3` | dividers, quiet borders |
| disabled | `#EDEAE0` | disabled surfaces |

Each student keeps their own colour (Mira gold-yellow, Pip teal, Juno purple), plus a darker **shade** of it for outlines, mote rings and ledges.

**Type:** Nunito (Google Fonts, OFL, bundle the TTFs): Regular for body, Bold for labels/buttons, ExtraBold for titles. Lora Medium Italic only for the user's own quoted words (What stuck, the "You:" line). Sizes: display 26, headline 22, title 19, button 17, body 15, small 13.

**Radii:** buttons 20dp, cards 24dp, pills fully round.

**Motion vocabulary:** nothing linear. Loops use ease-in-out-sine; arrivals overshoot a hair and settle (spring, low bounce); eyes dart (most of the travel in the first third, soft landing). Respect system "remove animations": no idle loops, motes still, everything else instant.

## 1. The students (tier 1, every screen)

One component, used unchanged on every screen.

- **Faces:** keep the capsule eyes; **remove the mouths**. Expression comes from eyes, eyelids, head tilt, and squash/stretch only (puzzled = one eye smaller + tilt; unconvinced = lids half down + small lean back; clearing = eyes widen).
- **Alive at rest (idle):** a slow breath (body scales ~2% on a 3–4s loop, a touch narrower as it fills); blinks on each student's own random clock (every 2–6s, ~150ms, occasionally a double blink); eyes glance around now and then, and the head follows the eyes slightly late. No two students are ever in sync.
- **Motes:** cream circles (`page` colour) with a ring in the student's shade, arranged in an arc around the head (not in a row), drifting gently while idle and holding still during a clearing. The last remaining mote is drawn 1.25× larger. In the lesson, the mote being discussed has a full ring, the others a faded ring.
- Tapping a student anywhere: they look up at you with a tiny nod.

## 2. Lesson screen (tier 1, most important)

Top to bottom, in one scrollable column above the input:

1. **Board** (edge to edge, no corners or ledge, running up under the status bar, whose icons go light on this screen): the slate holds everything about the student. At its top-left in chalk-white: "Mira", the small gold pill "3 exchanges" on the right (opens the history sheet, appears from the first exchange), and under it "what's left in Mira's head: 3". Then the stage: chalk lines on the left, the student large on the right with their motes.
2. **Student line:** a chalk speech bubble on the slate below the stage: hand-drawn chalk-white outline, chalk-white Nunito (21sp, line height 28), its notch pointing up at the student's centre; the student stands just behind it. New lines type in quickly (typewriter, ~20ms/char) and crossfade from the previous one; long lines wrap and the board grows.
3. **Your last explanation:** quieter, below it: a light card with a lavender ledge, "You" label in small muted text, the text in Lora italic.
4. **Input** pinned at the bottom: white field with a 2dp `ink` border, radius 20, and a gold "Explain" button with its ledge. While waiting, the student looks up and to the side (thinking), the field is locked; no spinner.
5. **Counter digit** doesn't tick: the old digit falls out of frame with weight, the new one drops in from above.
6. **Clearing (keep the 900ms timings exactly):** the active mote holds still, fades from its centre outward, drifts into the eye, the eyes widen, the counter drops, then the student speaks and pulls out a small pad and scribbles abstract squiggles on it (never words), under ~1.2s.
7. **Miss:** a short, per-student unconvinced reaction (Mira: tilts head and squints; Pip: quick bounce and a sideways look; Juno: slow lean back, eyes up). Never red, never a shake.
8. **Stuck / answer:** the two buttons side by side under the board: "See the answer" (gold) and "One last try" (white, ink outline). The answer card: light card, `line` border, small muted label "What's actually true", then the text, with a gold "Continue".

## 3. Classroom (tier 1)

One room, not a list. Fill the whole screen; no empty middle.

- **Wall** (top ~45%): warm off-white, a little warmer near the floor; a thin skirting line where it meets the floor.
- **Board** centred on the wall: dark green-slate surface, wooden frame, flat drop shadow, a ledge under it with a stick of chalk and an eraser, and two or three faint erased arcs on the slate. With a paused lesson it reads in chalk-white handwriting style: "Mira left this here." / "3 things still don't click.", plus a short chalk stroke in that student's colour; the whole board is tappable and gives a little under a finger (no chevron). With nothing paused it hangs blank.
- **Wall details** either side of the board, cut by the screen edges: on the left, a narrow shelf with a few books (one leaning) and a small plant on top; on the right, a plain clock (no numerals, ten past ten) and two pinned notes; in the right corner on the floor, a wire-mesh bin with crumpled paper (one ball missed, on the floor).
- **Floor** (bottom ~55%): wooden boards running towards the back in slight perspective (narrowing towards the top), three close honey tones in staggered lengths, fading into the page colour under the button so room and UI are one surface.
- **Desks:** seen slightly from above, gold wood with a couple of grain lines, a deeper front panel with a small nameplate showing the name, the back of a slate chair behind each student, a soft flat shadow under the legs. Each desk sits a few degrees off true, fixed per student. Personal props on the left third of the desk (Mira: doodle; Pip: askew notebook; Juno: plant). A paused lesson's notebook lies open in the middle of the desk.
- **Layout:** two desks in the back row, one in front, filling the width; students sized so the room feels inhabited.
- **Room life:** about every 12–20s, one student (never two at once) does one small thing of their own, never the same thing twice running: Mira looks up at the motes or adds to the doodle; Pip bounces or fidgets with the notebook; Juno gazes up or watches the plant. Faces change more often than bodies.
- **Entering:** a short stagger on cold start: board, desks, then motes.
- Bottom: one large gold "Teach something" button with its ledge.

## 4. Closing (tier 2)

- Title "Nothing left in Mira's head" (display, ExtraBold), the student settled, relaxed eyes, no motes.
- Relief line in the same dark notched speech card as the lesson.
- "What stuck": small muted label, then each sentence as a light card with a lavender ledge, text in Lora italic. Tapping one flips it to show the belief it cleared (muted, regular).
- The notebook: closed cover in the student's colour, title on the cover, a bookmark ribbon peeking out, turned a few degrees; one soft haptic as it shuts. The buttons appear only after it closes (they are already pinned at the bottom).
- Buttons: "Teach Mira something else" (gold), "Back to class" (white, ink outline), both with ledges.

## 5. Other screens (tier 2)

- **Paste Notes:** the field drawn as one sheet of paper (white, faint ruled lines, slight shadow ledge) in the middle of the page; a line under it says how much is there as a fact about a person ("About one sitting"), with the exact character count small beneath it. Gold "Continue".
- **Choose:** title "Who should learn this?". Each student on their own card with a ledge in their colour: the student at their desk on the left (idle, blinking), name in ExtraBold, their self-intro in their own voice. Tapping a card presses it down onto its ledge.
- **Reading:** the student at a desk with the notes drifting faintly behind them; eyes scan left-right, pause, go back, a puzzled look, a misguided small nod. When the deck arrives, a few words from the notes float toward the head and condense into motes one by one around the head, faster as they go; then "3 things don't click yet." and they look up at you. No spinner, no percentage.
- **Error states:** calm, in the student's voice where possible ("Mira lost the thread. Your notes are still here."), with a gold "Try again".

## 6. Tier 3 (only if time is left)

- Chalk dust puff when the board is tapped.
- A paused notebook on the desk shows a small bookmark in the student's colour.
- When the room is fully clear, resting faces are slightly more relaxed.
- App icon: the three students' silhouettes peeking over a desk edge on a gold field.

## Rules

- Don't change the 900ms clearing timings or any lesson logic.
- The counter never shows 0; at the end it is removed.
- No red, no shake, no praise, no score anywhere.
- One student component everywhere; screens never restyle it.
- Check each screen on the emulator and save before/after screenshots to `devpost/screenshots/` (useful for the video and submission).
