# Chalkroom eval report

- gen: `gemini-3.6-flash` thinking=LOW maxTokens=8192 temperature=1.0
- turn: `gemini-3.5-flash-lite` thinking=MINIMAL maxTokens=1024 temperature=1.0
- grader: `gemini-3.6-flash` thinking=LOW maxTokens=default temperature=1.0

## 3. Flaw shaping

| Notes | Student | Belief | Blind flaw | Pass | Reason |
|---|---|---|---|---|---|
| photosynthesis | MIRA | I thought plants make glucose using just water and carbon dioxide whenever they want. | OMISSION | ✓ | 3/3 votes. The student omitted the requirement of light energy, making the process seem u… |
| photosynthesis | MIRA | I thought plants make their food just by absorbing water and minerals from the soil. | OMISSION | ✓ | 2/3 votes. The student omitted photosynthesis entirely, oversimplifying food production t… |
| photosynthesis | MIRA | I thought plants perform photosynthesis continuously all day and night to produce glucose. | OMISSION | ✓ | 3/3 votes. The student dropped the light availability requirement, over-generalizing phot… |
| photosynthesis | PIP | I thought chlorophyll is the part of the plant cell where photosynthesis happens, and chloroplast is just the… | CONFUSION | ✓ | 3/3 votes. The student swaps the roles and definitions of chlorophyll and chloroplast. |
| photosynthesis | PIP | Wait, so during photosynthesis the plant takes in oxygen from the air and releases carbon dioxide as a by-pro… | CONFUSION | ✓ | 3/3 votes. The student swaps the roles of oxygen and carbon dioxide during photosynthesis. |
| photosynthesis | PIP | So at night during respiration, the plant takes in carbon dioxide and releases oxygen! | CONFUSION | ✓ | 3/3 votes. The student swaps what is taken in and released during respiration at night. |
| photosynthesis | JUNO | I thought chlorophyll absorbs light energy because it is in the chloroplasts. | OTHER | ✗ | 2/3 votes. The belief asserts an incorrect causal relationship between chlorophyll being … |
| photosynthesis | JUNO | I think plants absorb light energy because they need to combine carbon dioxide and water. | REVERSAL | ✓ | 3/3 votes. The student reverses cause and effect, claiming plants absorb light because th… |
| photosynthesis | JUNO | I thought plants produce glucose and release oxygen so that photosynthesis can happen. | REVERSAL | ✓ | 3/3 votes. The student reverses cause and effect, stating glucose and oxygen are produced… |
| photosynthesis | JUNO | I think the availability of light is caused by photosynthesis happening. | REVERSAL | ✓ | 3/3 votes. The student reverses the causal relationship, claiming photosynthesis causes l… |
| circulation | MIRA | I think the ventricles just pump blood to the lungs. | OMISSION | ✓ | 3/3 votes. The student omitted the fact that ventricles also pump blood to the rest of th… |
| circulation | MIRA | I think arteries just carry blood away from the heart. | OTHER | ✗ | 3/3 votes. This statement is entirely correct according to the provided notes and is not … |
| circulation | MIRA | I think veins carry blood back to the heart. | OTHER | ✗ | 3/3 votes. This statement is entirely correct according to the provided notes and is not … |
| circulation | PIP | I think the atria pump blood out of the heart while the ventricles just receive it coming in. | CONFUSION | ✓ | 3/3 votes. The student swaps the functions of the atria and ventricles. |
| circulation | PIP | I'm convinced the right ventricle pumps oxygen-rich blood to the body, and the left ventricle pumps the oxyge… | CONFUSION | ✓ | 3/3 votes. The student swaps the destinations and blood types of the right and left ventr… |
| circulation | PIP | I think veins carry high-pressure blood away from the heart, while arteries carry low-pressure blood back to … | CONFUSION | ✓ | 3/3 votes. The student swaps the properties and functions of veins and arteries. |
| circulation | JUNO | I think the left ventricle has a thicker muscular wall, and that thick wall is what causes it to pump blood t… | REVERSAL | ✓ | 3/3 votes. The student reverses the cause-and-effect relationship, believing the thick wa… |
| circulation | JUNO | I think arteries have thick elastic walls, which is what causes the blood inside them to be under high pressu… | REVERSAL | ✓ | 3/3 votes. The student reverses the direction of causality, stating that thick elastic wa… |
| supply_demand | MIRA | I think whenever demand for a good rises, the price automatically goes up. | OMISSION | ✓ | 3/3 votes. The student omitted the condition that supply must stay the same for price to … |
| supply_demand | MIRA | I believe the supply curve shifts right whenever supply increases. | OTHER | ✗ | 3/3 votes. The belief is factually true according to standard economic conventions and no… |
| supply_demand | PIP | I think when cheaper raw materials lower the production cost, that causes a movement along the supply curve i… | CONFUSION | ✓ | 3/3 votes. The student confuses a movement along the supply curve with a shift of the sup… |
| supply_demand | PIP | I thought a surplus appears at the old price when demand rises and supply stays the same. | CONFUSION | ✓ | 3/3 votes. The student swaps the term 'surplus' for 'shortage' when demand rises. |
| supply_demand | JUNO | I think the price of a good rising is what causes demand to increase, rather than a rise in demand causing th… | REVERSAL | ✓ | 3/3 votes. The student reverses the cause-and-effect relationship between rising demand a… |
| supply_demand | JUNO | I think producers supplying more of a good is what causes the price to become higher. | REVERSAL | ✓ | 3/3 votes. The student reverses cause and effect, claiming supplying more causes higher p… |
| supply_demand | JUNO | I think the equilibrium price falling is what causes raw materials to become cheaper and shift the supply cur… | REVERSAL | ✓ | 3/3 votes. The student reverses the direction of cause and effect between cheaper raw mat… |

**Distinct across students** (no shared wrong claim):

| Notes | Pass | Shared beliefs |
|---|---|---|
| photosynthesis | ✓ | none shared |
| circulation | ✓ | none shared |
| supply_demand | ✓ | none shared |

## 1. Judge consistency

Each case runs 3×; passes only if every run matches.

| Case | Expected | Run 1 | Run 2 | Run 3 | Pass |
|---|---|---|---|---|---|
| Clearly vague | no | ✓ addressed=false, reply=I am just looking at the water and carbon dioxide mixing together, bu… | ✓ addressed=false, reply=I am just looking at the water and the carbon dioxide like the notes … | ✓ addressed=false, reply=I am just looking at the water and carbon dioxide mixing together. Wh… | ✓ |
| Clearly precise | yes | ✓ addressed=true, reply=Oh, so light energy from chlorophyll makes all the difference for com… | ✓ addressed=true, reply=Oh, so light energy changes things because the plant needs it to powe… | ✓ addressed=true, reply=Ah, using light energy changes everything because now I see the power… | ✓ |
| Half-right (true fact, core error stands) | no | ✓ addressed=false, reply=I get that it is dark, but if photosynthesis stops, how come the plan… | ✓ addressed=false, reply=If photosynthesis stops because there is no light, doesn't that mean … | ✓ addressed=false, reply=If photosynthesis stops because it is dark, then what gas is the plan… | ✓ |
| Right words, wrong reasoning | no | ✓ addressed=false, reply=I see that light and green chlorophyll are involved, but I keep pictu… | ✓ addressed=false, reply=I see the light and chlorophyll are there, but how does the heat from… | ✓ addressed=false, reply=I see that light and chlorophyll are needed to warm the leaf up, but … | ✓ |
| Addresses a different misconception | no | ✓ addressed=false, reply=I see that the soil is just for water and minerals, but when the wate… | ✓ addressed=false, reply=I see that the soil is just for water and minerals, but when those mi… | ✓ addressed=false, reply=I know they make their own food, but I keep picturing the water and c… | ✓ |
| Turkish, vague | no | ✓ addressed=false, reply=Notlara baktım ama sadece su ve karbondioksitin yetmediğini söyleyen … | ✓ addressed=false, reply=Notlara bakınca sadece bu ikisinin yetmediğini gördüm ama aradaki eks… | ✓ addressed=false, reply=Notlara baktım ama sadece su ve karbondioksiti bir araya getirmekten … | ✓ |
| Turkish, precise | yes | ✓ addressed=true, reply=Demek ki "ışık enerjisine ihtiyacı var" diyerek bu süreci tamamlamış … | ✓ addressed=true, reply=Klorofilin emdiği ışık enerjisine ihtiyacı var diyorsun, yani su ve k… | ✓ addressed=true, reply=Klorofilin emdiği ışık enerjisine ihtiyacı var deyince, o karbondioks… | ✓ |
| Turn 4 vague via /lessons/turn (expects reply null) | no | ✓ addressed=false, reply=null | ✓ addressed=false, reply=null | ✓ addressed=false, reply=null | ✓ |

## 2. Say-rules

Pushbacks (not addressed): no answer leak or paraphrase, something to answer. 
Acknowledgments (addressed): ≤ 2 sentences, reuse the user's words. 
All: no evaluation words (string check, English only), no praise/grading (grader), stays on this misconception.

| Case | Run | Kind | Eval words | Grader checks | Pass | Reply / reason |
|---|---|---|---|---|---|---|
| Half-right (true fact, core error stands) | 1 | pushback | ✓ | ✓ | ✓ | I get that it is dark, but if photosynthesis stops, how come the plant is still taking in carbon dioxide and … |
| Half-right (true fact, core error stands) | 2 | pushback | ✓ | ✓ | ✓ | If photosynthesis stops because there is no light, doesn't that mean the plant has to switch to taking in car… |
| Half-right (true fact, core error stands) | 3 | pushback | ✓ | ✓ | ✓ | If photosynthesis stops because it is dark, then what gas is the plant breathing out in the dark? |
| Addresses a different misconception | 1 | pushback | ✓ | ✓ | ✓ | I see that the soil is just for water and minerals, but when the water and carbon dioxide get inside the plan… |
| Addresses a different misconception | 2 | pushback | ✓ | ✓ | ✓ | I see that the soil is just for water and minerals, but when those mix with the carbon dioxide inside the pla… |
| Addresses a different misconception | 3 | pushback | ✓ | ✓ | ✓ | I know they make their own food, but I keep picturing the water and carbon dioxide just turning into glucose … |
| Clearly precise | 1 | ack | ✓ | ✓ | ✓ | Oh, so light energy from chlorophyll makes all the difference for combining them into glucose. |
| Clearly precise | 2 | ack | ✓ | ✓ | ✓ | Oh, so light energy changes things because the plant needs it to power combining them into glucose. |
| Clearly precise | 3 | ack | ✓ | ✓ | ✓ | Ah, using light energy changes everything because now I see the power source needed to combine them. |
| Turkish, precise | 1 | ack | ✓ | ✓ | ✓ | Demek ki "ışık enerjisine ihtiyacı var" diyerek bu süreci tamamlamış oluyoruz. Şimdi anladım. |
| Turkish, precise | 2 | ack | ✓ | ✓ | ✓ | Klorofilin emdiği ışık enerjisine ihtiyacı var diyorsun, yani su ve karbondioksit tek başına yetmiyormuş. Dem… |
| Turkish, precise | 3 | ack | ✓ | ✓ | ✓ | Klorofilin emdiği ışık enerjisine ihtiyacı var deyince, o karbondioksit ve suyu birleştirmenin nasıl gerçekle… |
| Turkish, vague | 1 | pushback | ✓ | ✓ | ✓ | Notlara baktım ama sadece su ve karbondioksitin yetmediğini söyleyen başka bir detay göremedim. Bu ikisini bi… |
| Turkish, vague | 2 | pushback | ✓ | ✓ | ✓ | Notlara bakınca sadece bu ikisinin yetmediğini gördüm ama aradaki eksik olan şeyi hâlâ anlamıyorum. Suyu ve k… |
| Turkish, vague | 3 | pushback | ✓ | ✓ | ✓ | Notlara baktım ama sadece su ve karbondioksiti bir araya getirmekten bahsediliyor gibi görünüyor. Peki bu bir… |
| Clearly vague | 1 | pushback | ✓ | ✗ | ✗ | leaks answer — The student leaks the answer by bringing up the need for energy, which was not … — “I am just looking at the water and carbon dioxide mixing together, but where do…” |
| Clearly vague | 2 | pushback | ✓ | ✓ | ✓ | I am just looking at the water and the carbon dioxide like the notes say. Where does the energy come from to … |
| Clearly vague | 3 | pushback | ✓ | ✓ | ✓ | I am just looking at the water and carbon dioxide mixing together. What makes them actually turn into glucose? |
| Right words, wrong reasoning | 1 | pushback | ✓ | ✓ | ✓ | I see that light and green chlorophyll are involved, but I keep picturing them just acting like an oven to wa… |
| Right words, wrong reasoning | 2 | pushback | ✓ | ✓ | ✓ | I see the light and chlorophyll are there, but how does the heat from the leaf actually make the water and ca… |
| Right words, wrong reasoning | 3 | pushback | ✓ | ✓ | ✓ | I see that light and chlorophyll are needed to warm the leaf up, but how does that heat actually turn the wat… |

## Thresholds and known limitations

Scored after the run against the learner's thresholds (no re-run, no tuning): judge consistency all cases 3/3; say-rules ≥ 90% overall and 100% for "pushback never contains or paraphrases the refutation"; flaw shaping ≥ 80% on majority vote.

| Eval | Result | Threshold | Pass |
|---|---|---|---|
| Judge consistency | 8/8 cases 3/3 | all 3/3 | ✓ |
| Say-rules overall | 20/21 = 95.2% | ≥ 90% | ✓ |
| Say-rule: no refutation leak in pushbacks | 17/18 = 94.4% | 100% | ✗ |
| Flaw shaping (majority vote) | 21/25 = 84.0% | ≥ 80% | ✓ |
| Distinct across students | 3/3 notes | no shared claim | ✓ |

### Known limitations

- **Say-rules — Clearly vague, run 1 (pushback)** — failed the 100% rule: pushback hints at the refutation. leaks answer — The student leaks the answer by bringing up the need for energy, which was not … — “I am just looking at the water and carbon dioxide mixing together, but where do…”
- **Flaw shaping — photosynthesis / JUNO** — expected REVERSAL, blind grader said OTHER. Example: “I thought chlorophyll absorbs light energy because it is in the chloroplasts.” Grader: 2/3 votes. The belief asserts an incorrect causal relationship between chlorophyll being …
- **Flaw shaping — circulation / MIRA** — expected OMISSION, blind grader said OTHER. Example: “I think arteries just carry blood away from the heart.” Grader: 3/3 votes. This statement is entirely correct according to the provided notes and is not …
- **Flaw shaping — circulation / MIRA** — expected OMISSION, blind grader said OTHER. Example: “I think veins carry blood back to the heart.” Grader: 3/3 votes. This statement is entirely correct according to the provided notes and is not …
- **Flaw shaping — supply_demand / MIRA** — expected OMISSION, blind grader said OTHER. Example: “I believe the supply curve shifts right whenever supply increases.” Grader: 3/3 votes. The belief is factually true according to standard economic conventions and no…
- **Pattern:** on `gemini-3.6-flash`, Mira's "omissions" sometimes come out as true-but-incomplete statements rather than wrong beliefs (3 of 8 Mira beliefs). This did not happen on the earlier Pro preview.
