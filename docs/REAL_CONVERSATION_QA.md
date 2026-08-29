# Real Conversation QA — Human Beta Script

This script is for a **human tester** on a physical Android device. Automated tests cannot
speak into a microphone, so live audio, STT, TTS, and multi-turn dialogue must be validated by
a person. Every check is marked by the tester as **PASS / FAIL / N/A** with a note.

**Prerequisites**
- Signed release build installed (AAB from Milestone 15).
- Headphones recommended (TTS + mic).
- A quiet room.
- Device screen set to stay on (Settings → Display → Screen timeout → 30 min) to avoid the
  `homeScreenRendersWithinTimeout` flake.

**Devices under test (minimum)**
- Samsung Galaxy S9+ (SM-G965F, Android 10) — primary.
- One newer device (Android 12+) — secondary.

---

## A. First Launch & Onboarding (5 checks)
- [ ] App installs and opens to Home without crash.
- [ ] Cold launch feels < 5 s to interactive home (target; measured 2860 ms debug / ~800 ms release).
- [ ] Character list shows Raya, Rudra, Adwaita, Shub.
- [ ] Scenario list shows the 6 scenarios (Order Coffee, Airport Check-in, Job Interview,
      Project Standup, Meeting Someone, Workplace Discussion).
- [ ] No "raw" error text or stack traces visible anywhere.

## B. Permission Flow (4 checks)
- [ ] First mic tap prompts the microphone permission.
- [ ] Deny → friendly "Microphone access is needed for speaking practice." (no raw RECORD_AUDIO).
- [ ] Deny permanently → a "settings" dialog appears that deep-links to app settings.
- [ ] Grant → conversation begins listening.

## C. Live Conversation — English (primary path) (10 checks)
Pick **Raya / Order Coffee**.
- [ ] Tapping mic starts listening (UI shows listening state).
- [ ] Speaking produces a partial transcript (live text) then a final transcript.
- [ ] AI responds with a spoken reply (TTS audio audible) AND a transcript bubble.
- [ ] The AI reply is contextually relevant to "ordering coffee" (not random).
- [ ] Interrupting the AI (tap mic mid-TTS) stops TTS and returns to listening.
- [ ] Multi-turn: 3+ exchanges stay on topic and reference earlier turns.
- [ ] Corrections (if any) appear as gentle, non-judgmental hints.
- [ ] End session → summary screen appears with a score and focus area.
- [ ] From summary, "back to home" returns cleanly (no leftover transcript).
- [ ] Session appears in history and re-opens to the same conversation.

## D. Each Character (≥1 conversation each) (4 checks)
- [ ] Rudra (workplace/professional tone) responds in-character.
- [ ] Adwaita (patient/beginner-friendly) responds in-character.
- [ ] Shub (casual/friendly) responds in-character.
- [ ] Voice/style differences are noticeable.

## E. Each Scenario (≥1 conversation each) (6 checks)
- [ ] Airport Check-in stays in travel context.
- [ ] Job Interview stays in interview context.
- [ ] Project Standup stays in work-status context.
- [ ] Meeting Someone stays in social context.
- [ ] Workplace Discussion stays in professional context.
- [ ] Order Coffee stays in café context.

## F. Edge & Stress (6 checks)
- [ ] Say nothing for 10 s after tapping mic → it times out gracefully (no crash, friendly hint).
- [ ] Background the app mid-conversation and return → state recovers (no duplicate turns).
- [ ] Rapidly tap mic 5× → no crash, no duplicate listening sessions.
- [ ] Speak with heavy background noise → transcript degrades gracefully (no crash).
- [ ] Very short utterance ("Hi") → AI still responds coherently.
- [ ] Rotate / resize window (tablet) → layout holds, no crash.

## G. Model / Storage (3 checks)
- [ ] Trigger model download with < 50 MB free → friendly "Not enough storage" error; no crash.
- [ ] Model download success → progress shown, then usable.
- [ ] No model present → app does not crash; falls back to offline engine with a clear notice.

## H. Battery / Thermal (1 check, long)
- [ ] 20-minute continuous conversation → device warm but usable; no shutdown; no runaway
      battery drain beyond normal. (This is the only true battery validation.)

## I. Privacy Spot-Check (2 checks)
- [ ] Airplane mode on → app still functions offline (no "must connect" wall).
- [ ] No network indicator / no outbound requests while conversing (optional: monitor with a
      packet sniffer / NetGuard).

---

## Reporting
Testers file: device model, Android version, build hash, and each failed check with a screenshot
or logcat snippet. A check is **FAIL** if it crashes, shows raw errors, loses the conversation,
or breaks offline behaviour.

**Beta GO/NO-GO:** Requires all A–E PASS on the primary device and H PASS, with no critical
(security/privacy/data-loss) failures on any device.
