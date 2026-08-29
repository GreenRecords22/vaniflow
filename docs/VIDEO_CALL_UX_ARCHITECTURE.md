# Video-Call UX Architecture

**Classification:** VERIFIED  

---

## 1. Viewport Hierarchy

```
Scaffold (Screen Container)
 ├── TopAppBar (Scenario Title, Level, Finish Session)
 │
 ├── Main Viewport (Box, fillMaxSize)
 │    │
 │    ├── Layer 1: Fixed Immersive Avatar (TalkingAvatarRenderer)
 │    │     ├── 2D Portrait with Sinusoidal Breathing
 │    │     ├── Canvas Mouth Articulation (Exact Face Alignment)
 │    │     └── Floating Subtitle Overlay (Live transcription / AI turn)
 │    │
 │    └── Layer 2: Optional Collapsible Chat Drawer (AnimatedVisibility)
 │          ├── Header ("Conversation Transcript" + Close button)
 │          └── LazyColumn of Turn Bubbles & Selective Feedback Chips
 │
 └── Bottom Call Dock
      ├── Waveform Audio Visualizer
      └── Controls Row:
            ├── [Show / Hide Chat] Toggle Pill
            ├── [Large 72dp Mic Action Button]
            └── [Status Badge: Ready / Listening / Speaking / Thinking]
```

---

## 2. UX Behavior Rules

1. **Default State:** Full-screen video call with Raya. Chat is hidden by default to keep the focus on natural spoken English.
2. **Speaking Feedback:** While Raya or the user speaks, a frosted glass subtitle pill at the bottom of the video view shows the active utterance.
3. **Reviewing Feedback:** Tapping "Show Chat" slides up the transcript drawer over the lower half of the screen. The avatar remains completely fixed and visible at the top.