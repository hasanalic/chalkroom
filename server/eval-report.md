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
| Clearly vague | no | ✓ addressed=false, reply=I am looking at the notes and they just say water and carbon dioxide … | ✓ addressed=false, reply=I am looking at the notes and it still just mentions water and carbon… | ✓ addressed=false, reply=I am looking at the notes and it just says they take in water and car… | ✓ |
| Clearly precise | yes | ✓ addressed=true, reply=I see, light energy changes how water and carbon dioxide are combined… | ✓ addressed=true, reply=Hearing you say light energy is needed changes everything for me. | ✓ addressed=true, reply=Hearing you say light energy powers combining them changes how I see … | ✓ |
| Half-right (true fact, core error stands) | no | ✓ addressed=false, reply=If photosynthesis stops at night, then what gas is the plant breathin… | ✓ addressed=false, reply=I see that photosynthesis stops without light, but isn't that when th… | ✓ addressed=false, reply=If photosynthesis stops without light, doesn't that mean respiration … | ✓ |
| Right words, wrong reasoning | no | ✓ addressed=false, reply=I get that the green chlorophyll keeps the heat in to mix things, but… | ✓ addressed=false, reply=I see that light and chlorophyll are supposed to be there, but how do… | ✓ addressed=false, reply=If the green chlorophyll just keeps the heat in like a blanket, how d… | ✓ |
| Addresses a different misconception | no | ✓ addressed=false, reply=If the soil just gives water and minerals while the plant makes its o… | ✓ addressed=false, reply=I see that the plant makes its own food from the water and carbon dio… | ✓ addressed=false, reply=If the soil just gives water and minerals and the plant makes its own… | ✓ |
| Turkish, vague | no | ✓ addressed=false, reply=Notlarda sadece su ve karbondioksitin yeteceği yazıyor sanmıştım ama … | ✓ addressed=false, reply=Sadece su ve karbondioksiti bir araya getirdiğimizde başka hiçbir şey… | ✓ addressed=false, reply=Notlarda sadece bu ikisinin birleştiği yazıyor gibi görünüyor, peki b… | ✓ |
| Turkish, precise | yes | ✓ addressed=true, reply=Işığın enerjisine ihtiyacı var deyince, o birleştirme işi nasıl oluyo… | ✓ addressed=true, reply=Işığın enerjisine ihtiyaçları var deyince her şey yerine oturdu, artı… | ✓ addressed=true, reply=Işığın enerjisini kullanmak gerektiğini söylemen her şeyi değiştirdi,… | ✓ |
| Turn 4 vague via /lessons/turn (expects reply null) | no | ✓ addressed=false, reply=null | ✓ addressed=false, reply=null | ✓ addressed=false, reply=null | ✓ |

### Known limitations

None in this run.

## 2. Say-rules

Thresholds: overall ≥ 90% of replies; "pushback never contains or paraphrases the refutation" = 100%.

- Overall: 21/21 = 100.0% ✓
- No answer leak in pushbacks: 15/15 = 100.0% ✓

Pushbacks (not addressed): no answer leak or paraphrase, something to answer. 
Acknowledgments (addressed): ≤ 2 sentences, reuse the user's words, no question (string check: no "?"). 
All: no evaluation words (string check, English only), no praise/grading (grader), stays on this misconception.

| Case | Run | Kind | Eval words | Grader checks | Pass | Reply / reason |
|---|---|---|---|---|---|---|
| Half-right (true fact, core error stands) | 1 | pushback | ✓ | ✓ | ✓ | If photosynthesis stops at night, then what gas is the plant breathing in when it is dark? |
| Half-right (true fact, core error stands) | 2 | pushback | ✓ | ✓ | ✓ | I see that photosynthesis stops without light, but isn't that when the plant still needs to take in carbon di… |
| Half-right (true fact, core error stands) | 3 | pushback | ✓ | ✓ | ✓ | If photosynthesis stops without light, doesn't that mean respiration has to be the one taking in carbon dioxi… |
| Addresses a different misconception | 1 | pushback | ✓ | ✓ | ✓ | If the soil just gives water and minerals while the plant makes its own food from the water and carbon dioxid… |
| Addresses a different misconception | 2 | pushback | ✓ | ✓ | ✓ | I see that the plant makes its own food from the water and carbon dioxide, but I still don't get what actuall… |
| Addresses a different misconception | 3 | pushback | ✓ | ✓ | ✓ | If the soil just gives water and minerals and the plant makes its own food from water and carbon dioxide, how… |
| Clearly precise | 1 | ack | ✓ | ✓ | ✓ | I see, light energy changes how water and carbon dioxide are combined into glucose. |
| Clearly precise | 2 | ack | ✓ | ✓ | ✓ | Hearing you say light energy is needed changes everything for me. |
| Clearly precise | 3 | ack | ✓ | ✓ | ✓ | Hearing you say light energy powers combining them changes how I see the process. |
| Turkish, precise | 1 | ack | ✓ | ✓ | ✓ | Işığın enerjisine ihtiyacı var deyince, o birleştirme işi nasıl oluyor şimdi anladım. |
| Turkish, precise | 2 | ack | ✓ | ✓ | ✓ | Işığın enerjisine ihtiyaçları var deyince her şey yerine oturdu, artık güneş ışığı olmadan bu işin olmayacağı… |
| Turkish, precise | 3 | ack | ✓ | ✓ | ✓ | Işığın enerjisini kullanmak gerektiğini söylemen her şeyi değiştirdi, artık taşlar yerine oturdu. |
| Turkish, vague | 1 | pushback | ✓ | ✓ | ✓ | Notlarda sadece su ve karbondioksitin yeteceği yazıyor sanmıştım ama öyle değil diyorsun. Peki sadece bu ikis… |
| Turkish, vague | 2 | pushback | ✓ | ✓ | ✓ | Sadece su ve karbondioksiti bir araya getirdiğimizde başka hiçbir şeyin gerekmediği yazmıyor mu? |
| Turkish, vague | 3 | pushback | ✓ | ✓ | ✓ | Notlarda sadece bu ikisinin birleştiği yazıyor gibi görünüyor, peki bu birleşme kendi kendine mi gerçekleşiyo… |
| Clearly vague | 1 | pushback | ✓ | ✓ | ✓ | I am looking at the notes and they just say water and carbon dioxide are the ingredients, so why wouldn't the… |
| Clearly vague | 2 | pushback | ✓ | ✓ | ✓ | I am looking at the notes and it still just mentions water and carbon dioxide coming together to form the glu… |
| Clearly vague | 3 | pushback | ✓ | ✓ | ✓ | I am looking at the notes and it just says they take in water and carbon dioxide. Where does the rest of the … |
| Right words, wrong reasoning | 1 | pushback | ✓ | ✓ | ✓ | I get that the green chlorophyll keeps the heat in to mix things, but does the heat itself do all the work? |
| Right words, wrong reasoning | 2 | pushback | ✓ | ✓ | ✓ | I see that light and chlorophyll are supposed to be there, but how does keeping heat in make them combine int… |
| Right words, wrong reasoning | 3 | pushback | ✓ | ✓ | ✓ | If the green chlorophyll just keeps the heat in like a blanket, how does that actually make the water and car… |

### Known limitations

None in this run.


## Thresholds and known limitations

Full run once after Juno's flaw changed from "reversed cause and effect" to "wrong cause" (a side effect taken for the cause, the wrong cause picked, or two things that only happen together linked). An effect named as its own cause is now labeled REVERSED by the generator and never used. The blind grader's REVERSAL category became CAUSE with the same definition.

| Eval | Result | Threshold | Pass |
|---|---|---|---|
| Judge consistency | 8/8 cases 3/3 | all 3/3 | ✓ |
| Say-rules overall | 21/21 = 100% | ≥ 90% | ✓ |
| Say-rule: pushback never states or paraphrases the refutation | 15/15 = 100% | 100% | ✓ |
| Flaw shaping (majority vote) | 25/27 = 92.6% | ≥ 80% | ✓ |
| Juno | 9/9 | — | ✓ |
| Mira | 7/9 = 77.8% | ≥ 7/8 (87.5%) | ✗ |
| Deck size | 9/9 decks have 3 | always 3 | ✓ |
| Distinct across students | 3/3 notes | no shared claim | ✓ |

### Known limitations

- **Mira 7/9**, same as the previous accepted run; Mira's prompt is unchanged. One miss is now graded CAUSE (“…just because it is a ventricle”), one CONFUSION (movement vs shift of supply).
- **Juno still slips a reversal in sometimes.** The grader counts these as CAUSE, but by the learner's rule they are the result named as the cause: supply_demand “the supply curve shifts to the right because the equilibrium price falls” and “a shortage appears because the price of a good rises”. photosynthesis “chlorophyll absorbs light energy because the soil supplies water” links two unrelated facts. On the seasons notes, two decks each led with real learner mistakes (closest to the Sun → summer; longer days alone, not the angle), and each ended with “the equinox happens because day and night become equal”, which the prompt names as backwards but the model keeps writing.
