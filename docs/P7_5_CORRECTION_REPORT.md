# VaniFlow P7.5 — English Correction & Retry Loop Acceptance Report

## 1. Targeted Error Acceptance Matrix

The engine was evaluated against 6 classic Indian English learner errors and 4 correct control sentences:

| Test Sentence | Expected Diagnosis | Suggested Correction | Engine Verdict | Result |
|---|---|---|---|---|
| *"Yesterday I meet my friend"* | Past Tense Inflection | *"Yesterday I met my friend"* | Flagged: `TENSE` | **PASS** |
| *"I am living here since 5 years"* | Present Perfect Continuous + Duration Prep | *"I have been living here for 5 years"* | Flagged: `TENSE / PREPOSITIONS` | **PASS** |
| *"He arrived on morning"* | Time Preposition | *"He arrived in the morning"* | Flagged: `PREPOSITIONS` | **PASS** |
| *"She don't like coffee"* | Subject-Verb Agreement (3rd Person Singular) | *"She doesn't like coffee"* | Flagged: `SUBJECT_VERB_AGREEMENT` | **PASS** |
| *"We discussed about the plan"* | Transitive Verb Preposition Redundancy | *"We discussed the plan"* | Flagged: `PREPOSITIONS` | **PASS** |
| *"What is your good name?"* | Indian English Phrasing / Natural English | *"What is your name?"* | Flagged: `NATURAL_PHRASING` | **PASS** |
| *"I have been living in Jaipur for two years"* | Valid Sentence | *None* | Untouched | **PASS (No FP)** |
| *"She doesn't want to come with us"* | Valid Sentence | *None* | Untouched | **PASS (No FP)** |
| *"We discussed the marketing plan yesterday"* | Valid Sentence | *None* | Untouched | **PASS (No FP)** |
| *"He arrived in the morning by train"* | Valid Sentence | *None* | Untouched | **PASS (No FP)** |

---

## 2. Spoken Pedagogical Correction Style

When an important correction is triggered, VaniFlow speaks like a supportive human tutor:

> *"Almost. A more natural way to say that is: 'Yesterday I met my friend'. Can you try saying that?"*

### Spoken Phrasing Rules:
1. Empathic acknowledgement (*"Almost"*, *"Good effort"*, *"Close!"*).
2. Clear model sentence (*"A more natural way to say that is..."*).
3. Explicit practice invitation (*"Can you try it once?"*, *"Give it a try!"*).

---

## 3. Retry Loop State Machine

```
[User Utterance with Important Error]
               │
               ▼
   [TutorDecisionEngine] -> Action: ASK_RETRY
   [TutorState] -> WAITING_FOR_RETRY
   [Spoken Correction Output] -> "Almost. Try saying..."
               │
               ▼
   [User Speaks Again (Retry Utterance)]
               │
       ┌───────┴────────────────────────┐
       ▼                                ▼
[Correct Retry]                 [Incorrect / Struggle]
- Tutor acknowledges success     - Tutor models sentence again
- Action: PRAISE_AND_CONTINUE    - Transitions to struggle backoff
- TutorState -> NORMAL           - TutorState -> NORMAL
- Normal conversation resumes    - Avoids frustrating learner loop
```\n