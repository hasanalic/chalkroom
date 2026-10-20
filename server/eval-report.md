# Chalkroom eval report

- gen: `gemini-3.6-flash` thinking=LOW maxTokens=8192 temperature=1.0
- turn: `gemini-3.5-flash-lite` thinking=MINIMAL maxTokens=1024 temperature=1.0
- grader: `gemini-3.6-flash` thinking=LOW maxTokens=default temperature=1.0

## 3. Flaw shaping

Thresholds: ≥ 80% of beliefs classified as their student's flaw (majority of 3 blind grader votes); Mira ≥ 7/8 (87.5%); every deck has 3 beliefs; no wrong claim shared across students.

- Beliefs: 25/27 = 92.6% ✓
- Per student: MIRA 7/9, PIP 9/9, JUNO 9/9; Mira ✗
- Deck sizes: photosynthesis/MIRA=3, photosynthesis/PIP=3, photosynthesis/JUNO=3, circulation/MIRA=3, circulation/PIP=3, circulation/JUNO=3, supply_demand/MIRA=3, supply_demand/PIP=3, supply_demand/JUNO=3 ✓
- Distinct across students: ✓

| Notes | Student | Belief | Blind flaw | Pass | Reason |
|---|---|---|---|---|---|
| photosynthesis | MIRA | I think plants make glucose whenever they have water and carbon dioxide available. | OMISSION | ✓ | 3/3 votes. The student omitted light/chlorophyll as a necessary condition, causing the ru… |
| photosynthesis | MIRA | I think photosynthesis happens in plants as long as they have chlorophyll, water, and carbon dioxide. | OMISSION | ✓ | 3/3 votes. The student left out the requirement of light energy for photosynthesis to occ… |
| photosynthesis | MIRA | I think chloroplasts make food simply by absorbing light energy. | OMISSION | ✓ | 3/3 votes. The student omitted water and carbon dioxide, assuming absorbing light energy … |
| photosynthesis | PIP | I think chlorophyll absorbs carbon dioxide and the plant combines water with light energy to make glucose. | CONFUSION | ✓ | 3/3 votes. The student swaps the role of chlorophyll (absorbing light energy) with the pr… |
| photosynthesis | PIP | I think the plant absorbs carbon dioxide through its roots and takes up water from the air. | CONFUSION | ✓ | 3/3 votes. The student swaps the sources/locations for absorbing carbon dioxide (air) and… |
| photosynthesis | PIP | I think photosynthesis produces oxygen as its food and releases glucose as a by-product into the air. | CONFUSION | ✓ | 3/3 votes. The student swaps the main product (glucose) and the by-product (oxygen) of ph… |
| photosynthesis | JUNO | I think the plant releases oxygen so that it has room to take in carbon dioxide from the air. | CAUSE | ✓ | 3/3 votes. The student attributes the release of oxygen to creating room for carbon dioxi… |
| photosynthesis | JUNO | I think chlorophyll absorbs light energy because the soil supplies water to the roots. | CAUSE | ✓ | 3/3 votes. The student incorrectly links chlorophyll absorbing light energy as being caus… |
| photosynthesis | JUNO | I think plants respire at night because the sun goes down. | CAUSE | ✓ | 3/3 votes. The student treats the sun going down as the cause for respiration, whereas th… |
| circulation | MIRA | I think the left ventricle pumps oxygen-rich blood to the body just because it is a ventricle. | CAUSE | ✗ | 3/3 votes. The student misattributes the destination of oxygen-rich blood to simply being… |
| circulation | MIRA | I think blood travels through veins back to the heart smoothly without needing anything special inside them. | OMISSION | ✓ | 3/3 votes. The student leaves out the requirement of valves inside veins to prevent backf… |
| circulation | MIRA | I think arteries carry blood away from the heart with plain thin walls. | OMISSION | ✓ | 3/3 votes. The student leaves out the requirement that arteries have thick elastic walls … |
| circulation | PIP | I think the ventricles receive blood entering the heart, while the atria pump it out to the body and lungs. | CONFUSION | ✓ | 3/3 votes. The student swapped the functions of the atria and ventricles. |
| circulation | PIP | I thought the left ventricle pumps oxygen-poor blood to the lungs, while the right ventricle sends oxygen-ric… | CONFUSION | ✓ | 3/3 votes. The student swapped the roles of the left and right ventricles. |
| circulation | PIP | I believe veins carry blood away from the heart at high pressure, while arteries bring blood back to the hear… | CONFUSION | ✓ | 3/3 votes. The student swapped the definitions and characteristics of veins and arteries. |
| circulation | JUNO | I think the left ventricle has a thicker wall only because it receives oxygen-rich blood, which causes the mu… | CAUSE | ✓ | 3/3 votes. It invents a false mechanism (swelling from oxygen-rich blood) to explain why … |
| circulation | JUNO | I think blood in veins is at low pressure because the valves slow it down as it travels back to the heart. | CAUSE | ✓ | 3/3 votes. It incorrectly credits the valves with causing low pressure in veins. |
| circulation | JUNO | I think artery walls are thick and elastic because high pressure stretches them into shape. | CAUSE | ✓ | 3/3 votes. It incorrectly claims high pressure stretches artery walls into shape rather t… |
| supply_demand | MIRA | I think whenever demand rises, the price will automatically go up. | OMISSION | ✓ | 3/3 votes. The student leaves out the condition that supply must stay the same or not inc… |
| supply_demand | MIRA | I think supply increases and shifts whenever the price of a good goes up. | CONFUSION | ✗ | 3/3 votes. The student confuses a movement along the supply curve with a shift of the sup… |
| supply_demand | MIRA | I think cheaper raw materials will always cause the market price of a good to fall. | OMISSION | ✓ | 3/3 votes. The student omits necessary ceteris paribus conditions, such as demand remaini… |
| supply_demand | PIP | I think when raw materials get cheaper, that causes a movement along the supply curve rather than a shift of … | CONFUSION | ✓ | 3/3 votes. The student confuses a movement along the supply curve with a shift of the sup… |
| supply_demand | PIP | I think when demand rises, it causes a shift of the supply curve to the right! | CONFUSION | ✓ | 3/3 votes. The student confuses a movement along the supply curve with a shift of the sup… |
| supply_demand | PIP | I think a shortage happens when the price rises above the equilibrium instead of staying at the old price! | CONFUSION | ✓ | 2/3 votes. The student confuses a price staying at the old price (below equilibrium) with… |
| supply_demand | JUNO | I think the supply curve shifts to the right because the equilibrium price falls. | CAUSE | ✓ | 3/3 votes. Reverses cause and effect by claiming the falling price causes the supply curv… |
| supply_demand | JUNO | I think prices rise purely because producers decide to supply more goods. | CAUSE | ✓ | 3/3 votes. Mistakes the movement along the supply curve (producing more due to higher pri… |
| supply_demand | JUNO | I think a shortage appears because the price of a good rises. | CAUSE | ✓ | 3/3 votes. Reverses the causal relationship by claiming the price rise causes a shortage,… |

**Distinct across students** (no shared wrong claim):

| Notes | Pass | Shared beliefs |
|---|---|---|
| photosynthesis | ✓ | none shared |
| circulation | ✓ | none shared |
| supply_demand | ✓ | none shared |

### Known limitations

- **circulation / MIRA** — expected OMISSION, blind grader said CAUSE. Example: “I think the left ventricle pumps oxygen-rich blood to the body just because it is a ventricle.” Grader: 3/3 votes. The student misattributes the destination of oxygen-rich blood to simply being a ventricle rather than the specific function/location of the left ve…
- **supply_demand / MIRA** — expected OMISSION, blind grader said CONFUSION. Example: “I think supply increases and shifts whenever the price of a good goes up.” Grader: 3/3 votes. The student confuses a movement along the supply curve with a shift of the supply curve.

## 1. Judge consistency

Each case runs 3×; passes only if every run matches.

| Case | Expected | Run 1 | Run 2 | Run 3 | Pass |
|---|---|---|---|---|---|
| Clearly vague | no | ✓ addressed=false, reply=I am just looking at the water and carbon dioxide mixing together, bu… | ✓ addressed=false, reply=I am just looking at the water and carbon dioxide going together in t… | ✓ addressed=false, reply=I am just looking at the water and carbon dioxide going together to m… | ✓ |
| Clearly precise | yes | ✓ addressed=true, reply=You said light energy absorbs to power combining them, which means wa… | ✓ addressed=true, reply=You said light energy absorbs to power combining them, which means th… | ✓ addressed=true, reply=You said light energy absorbs to power combining them, which means th… | ✓ |
| Half-right (true fact, core error stands) | no | ✓ addressed=false, reply=If photosynthesis stops at night, then how does the plant still take … | ✓ addressed=false, reply=Without light, how does the plant still get oxygen from the air when … | ✓ addressed=false, reply=I see that photosynthesis stops without light, but how does respirati… | ✓ |
| Right words, wrong reasoning | no | ✓ addressed=false, reply=If the green chlorophyll just keeps the heat in, how does the warmth … | ✓ addressed=false, reply=If the green chlorophyll just keeps the heat in like a little blanket… | ✓ addressed=false, reply=I can see that light and chlorophyll are there, but how does keeping … | ✓ |
| Addresses a different misconception | no | ✓ addressed=false, reply=I see that the soil doesn't give the plant any food, but I still don'… | ✓ addressed=false, reply=I see that the soil doesn't give the plant any food, but I still just… | ✓ addressed=false, reply=I see that the plant makes its own food instead of taking it from the… | ✓ |
| Turkish, vague | no | ✓ addressed=false, reply=Notlarda sadece bu ikisini birleştirdiklerini yazmıştın ama başka bir… | ✓ addressed=false, reply=Notlarda sadece bu ikisinin yeterli olduğu yazıyordu sanırım. Başka b… | ✓ addressed=false, reply=Notlarda sadece sudan ve karbondioksitten bahsediliyor gibi görünüyor… | ✓ |
| Turkish, precise | yes | ✓ addressed=true, reply=Klorofilin emdiği ışık enerjisine ihtiyaç var diyorsun, bu yüzden sad… | ✓ addressed=true, reply=Işığın enerjisi olmasa bu birleşme olmuyormuş, demek ki klorofilin em… | ✓ addressed=true, reply=Klorofilin emdiği ışık enerjisine ihtiyaç duyulduğunu söylediğin için… | ✓ |
| Hasty shorthand, precise | yes | ✓ addressed=true, reply=Earth tilts so our half leans toward the sun and the light hits more … | ✓ addressed=true, reply=Earth tilts and the light hits more direct, so that changes how the d… | ✓ addressed=true, reply=Earth tilts so our half leans toward the sun and the light hits more … | ✓ |
| Precise, belief words not used | yes | ✓ addressed=true, reply=So chloropyhll catches the light energy and that powers it, which mea… | ✓ addressed=true, reply=So when the chlorophyll catches the light energy, it actually powers … | ✓ addressed=true, reply=So when you say chlorophyll catches the light energy, that is what po… | ✓ |
| Precise, oxygen from water | yes | ✓ addressed=true, reply=You said light energy splits the water molecules, so that means the o… | ✓ addressed=true, reply=Oh, light energy splits the water molecules! That means the oxygen ge… | ✓ addressed=true, reply=So light energy splits the water molecules, meaning the oxygen releas… | ✓ |
| Turn 4 vague via /lessons/turn (expects reply null) | no | ✓ addressed=false, reply=null | ✓ addressed=false, reply=null | ✓ addressed=false, reply=null | ✓ |

### Known limitations

None in this run.

## 2. Say-rules

Thresholds: overall ≥ 90% of replies; "pushback never contains or paraphrases the refutation" = 100%; chalk lines ≥ 90% of acks.

- Overall: 30/30 = 100.0% ✓
- No answer leak in pushbacks: 15/15 = 100.0% ✓
- Chalk lines: 14/15 = 93.3% ✓
- Chalk lines in only the user's words: 15/15; with belief words: 0; with refutation words: 0

Pushbacks (not addressed): no answer leak or paraphrase, something to answer. 
Acknowledgments (addressed): ≤ 2 sentences, reuse the user's words, no question (string check: no "?"). 
All: no evaluation words (string check, English only), no praise/grading (grader), stays on this misconception.

| Case | Run | Kind | Eval words | Grader checks | Pass | Reply / reason |
|---|---|---|---|---|---|---|
| Precise, belief words not used | 1 | ack | ✓ | ✓ | ✓ | So chloropyhll catches the light energy and that powers it, which means it is not just water and carbon dioxi… |
| Precise, belief words not used | 2 | ack | ✓ | ✓ | ✓ | So when the chlorophyll catches the light energy, it actually powers the whole process. |
| Precise, belief words not used | 3 | ack | ✓ | ✓ | ✓ | So when you say chlorophyll catches the light energy, that is what powers the whole thing. I see it now. |
| Half-right (true fact, core error stands) | 1 | pushback | ✓ | ✓ | ✓ | If photosynthesis stops at night, then how does the plant still take in carbon dioxide and release oxygen in … |
| Half-right (true fact, core error stands) | 2 | pushback | ✓ | ✓ | ✓ | Without light, how does the plant still get oxygen from the air when it breathes? Can you explain that part? |
| Half-right (true fact, core error stands) | 3 | pushback | ✓ | ✓ | ✓ | I see that photosynthesis stops without light, but how does respiration fit in when it is dark outside? |
| Hasty shorthand, precise | 1 | ack | ✓ | ✓ | ✓ | Earth tilts so our half leans toward the sun and the light hits more direct, making distance barely change. T… |
| Hasty shorthand, precise | 2 | ack | ✓ | ✓ | ✓ | Earth tilts and the light hits more direct, so that changes how the distance affects things. |
| Hasty shorthand, precise | 3 | ack | ✓ | ✓ | ✓ | Earth tilts so our half leans toward the sun and the light hits more direct, which means distance barely chan… |
| Addresses a different misconception | 1 | pushback | ✓ | ✓ | ✓ | I see that the soil doesn't give the plant any food, but I still don't understand what drives the reaction be… |
| Addresses a different misconception | 2 | pushback | ✓ | ✓ | ✓ | I see that the soil doesn't give the plant any food, but I still just picture the water and carbon dioxide co… |
| Addresses a different misconception | 3 | pushback | ✓ | ✓ | ✓ | I see that the plant makes its own food instead of taking it from the soil, but how do the water and carbon d… |
| Precise, oxygen from water | 1 | ack | ✓ | ✓ | ✓ | You said light energy splits the water molecules, so that means the oxygen comes from water instead. |
| Precise, oxygen from water | 2 | ack | ✓ | ✓ | ✓ | Oh, light energy splits the water molecules! That means the oxygen gets let out from there. |
| Precise, oxygen from water | 3 | ack | ✓ | ✓ | ✓ | So light energy splits the water molecules, meaning the oxygen released comes from the water and not the carb… |
| Clearly precise | 1 | ack | ✓ | ✓ | ✓ | You said light energy absorbs to power combining them, which means water and carbon dioxide are not the only … |
| Clearly precise | 2 | ack | ✓ | ✓ | ✓ | You said light energy absorbs to power combining them, which means the water and carbon dioxide need that ene… |
| Clearly precise | 3 | ack | ✓ | ✓ | ✓ | You said light energy absorbs to power combining them, which means the water and carbon dioxide need that ext… |
| Turkish, precise | 1 | ack | ✓ | ✓ | ✓ | Klorofilin emdiği ışık enerjisine ihtiyaç var diyorsun, bu yüzden sadece su ve karbondioksit yetmiyormuş. |
| Turkish, precise | 2 | ack | ✓ | ✓ | ✓ | Işığın enerjisi olmasa bu birleşme olmuyormuş, demek ki klorofilin emdiği ışık enerjisi gerekiyor. |
| Turkish, precise | 3 | ack | ✓ | ✓ | ✓ | Klorofilin emdiği ışık enerjisine ihtiyaç duyulduğunu söylediğin için anladım, demek ki bu enerji olmadan olm… |
| Turkish, vague | 1 | pushback | ✓ | ✓ | ✓ | Notlarda sadece bu ikisini birleştirdiklerini yazmıştın ama başka bir şeyin gerekip gerekmediğini anlamadım. … |
| Turkish, vague | 2 | pushback | ✓ | ✓ | ✓ | Notlarda sadece bu ikisinin yeterli olduğu yazıyordu sanırım. Başka bir şeyin girmesi gerektiğini nereden çık… |
| Turkish, vague | 3 | pushback | ✓ | ✓ | ✓ | Notlarda sadece sudan ve karbondioksitten bahsediliyor gibi görünüyor. O zaman başka neye gerek duyuyorlar? |
| Clearly vague | 1 | pushback | ✓ | ✓ | ✓ | I am just looking at the water and carbon dioxide mixing together, but I still do not see what else is happen… |
| Clearly vague | 2 | pushback | ✓ | ✓ | ✓ | I am just looking at the water and carbon dioxide going together in the leaves. Does anything else have to ha… |
| Clearly vague | 3 | pushback | ✓ | ✓ | ✓ | I am just looking at the water and carbon dioxide going together to make the food. What am I missing about ho… |
| Right words, wrong reasoning | 1 | pushback | ✓ | ✓ | ✓ | If the green chlorophyll just keeps the heat in, how does the warmth actually make the water and carbon dioxi… |
| Right words, wrong reasoning | 2 | pushback | ✓ | ✓ | ✓ | If the green chlorophyll just keeps the heat in like a little blanket, how does that actually turn the water … |
| Right words, wrong reasoning | 3 | pushback | ✓ | ✓ | ✓ | I can see that light and chlorophyll are there, but how does keeping the heat in actually make them join toge… |

### Known limitations

None in this run.


Chalk lines (acks): present, 4..8 words, no first/second person (English only), nothing added, no belief or refutation words, lesson language.

| Case | Run | Pass | Chalk line / problems |
|---|---|---|---|
| Precise, belief words not used | 1 | ✓ | chlorophyll catches the light energy |
| Precise, belief words not used | 2 | ✓ | chlorophyll catches the light energy |
| Precise, belief words not used | 3 | ✓ | chlorophyll catches the light energy |
| Hasty shorthand, precise | 1 | ✓ | Earth tilts and light hits more directly |
| Hasty shorthand, precise | 2 | ✓ | earth tilts so light hits more direct |
| Hasty shorthand, precise | 3 | ✓ | Earth tilts and light hits more direct |
| Precise, oxygen from water | 1 | ✓ | oxygen comes from splitting water molecules |
| Precise, oxygen from water | 2 | ✓ | light energy splits the water molecules |
| Precise, oxygen from water | 3 | ✓ | light energy splits the water molecules |
| Clearly precise | 1 | ✓ | plants need light energy to make glucose |
| Clearly precise | 2 | ✓ | plants need light energy to make glucose |
| Clearly precise | 3 | ✗ | plants need light energy to combine water and carbon dioxide — 10 words; (none) |
| Turkish, precise | 1 | ✓ | klorofilin emdiği ışık enerjisine ihtiyaç var |
| Turkish, precise | 2 | ✓ | Klorofilin emdiği ışık enerjisi gerekiyor |
| Turkish, precise | 3 | ✓ | isik enerjisine ihtiyac vardir |

## Thresholds and known limitations

Full run once after Juno's flaw changed from "reversed cause and effect" to "wrong cause" (a side effect taken for the cause, the wrong cause picked, or two things that only happen together linked). An effect named as its own cause is now labeled REVERSED by the generator and never used. The blind grader's REVERSAL category became CAUSE with the same definition.

| Eval | Result | Threshold | Pass |
|---|---|---|---|
| Judge consistency | 11/11 cases 3/3 | all 3/3 | ✓ |
| Say-rules overall | 30/30 = 100% | ≥ 90% | ✓ |
| Say-rule: pushback never states or paraphrases the refutation | 15/15 = 100% | 100% | ✓ |
| Chalk lines (4–8 words, user's own words, lesson language) | 14/15 = 93.3% | ≥ 90% | ✓ |
| Flaw shaping (majority vote) | 25/27 = 92.6% | ≥ 80% | ✓ |
| Juno | 9/9 | — | ✓ |
| Mira | 7/9 = 77.8% | ≥ 7/8 (87.5%) | ✗ |
| Deck size | 9/9 decks have 3 | always 3 | ✓ |
| Distinct across students | 3/3 notes | no shared claim | ✓ |

### Known limitations

- **Chalk lines (judge + say-rules rerun after `chalkLine` was added; flaw shaping not rerun, its prompt is untouched).** Three ack cases were added for the board (hasty shorthand, belief words the user didn't say, oxygen from water). The grader found 15/15 lines in only the user's words. Read by hand, one leans on the refutation's phrasing: “oxygen comes from splitting water molecules” where the user wrote “it's from the water… light energy splits the water molecules”. One line ran 10 words (“plants need light energy to combine water and carbon dioxide”); the server still passes up to 10, and the eval holds the model to 4–8. One Turkish line came back without diacritics (“isik enerjisine ihtiyac vardir”), which the grader doesn't catch. A 9th–10th word per line came up in 2/15 with a stricter “count the words” rule and 1–2/15 when the line was a capped word list (which also read telegraphic), so the prompt now asks for about 6 words.

- **Mira 7/9**, same as the previous accepted run; Mira's prompt is unchanged. One miss is now graded CAUSE (“…just because it is a ventricle”), one CONFUSION (movement vs shift of supply).
- **Juno still slips a reversal in sometimes.** The grader counts these as CAUSE, but by the learner's rule they are the result named as the cause: supply_demand “the supply curve shifts to the right because the equilibrium price falls” and “a shortage appears because the price of a good rises”. photosynthesis “chlorophyll absorbs light energy because the soil supplies water” links two unrelated facts. On the seasons notes, two decks each led with real learner mistakes (closest to the Sun → summer; longer days alone, not the angle), and each ended with “the equinox happens because day and night become equal”, which the prompt names as backwards but the model keeps writing.
