# VaniFlow Privacy Policy & Audio Architecture

## 1. Core Principle: Local-First & Zero Audio Upload

VaniFlow is engineered from the ground up as a **privacy-first, on-device English learning application**. We adhere strictly to the principle that your voice is your private biometric data.

### Non-Negotiable Privacy Guarantees:
1. **Zero Voice Upload**: Microphone audio recorded during conversational practice is processed entirely in memory on your Android device. It is **never** uploaded to cloud servers, remote analytics, or third-party AI APIs.
2. **Ephemeral Memory Processing**: Audio PCM buffers captured by `AudioRecord` are analyzed by on-device Speech-to-Text (STT) and Voice Activity Detection (VAD) engines and discarded immediately after transcription. No raw audio files (`.wav`, `.mp3`, `.pcm`) are written to permanent storage without explicit user export action.
3. **Zero Mandatory Account or Login**: VaniFlow MVP functions completely without requiring an account, email address, phone number, or cloud profile. All session history and progress metrics reside in an encrypted local Room database on your device.
4. **No Hidden Trackers or Audio Telemetry**: The application does not bundle third-party tracking SDKs or audio telemetry collectors.

---

## 2. Permissions Transparency

### `android.permission.RECORD_AUDIO`
- **Purpose**: Capturing real-time speech during active speaking scenarios.
- **When Active**: Microphone capture is initialized **only** when the user enters an active practice session and taps the microphone button or engages in conversational speaking.
- **When Inactive**: When navigating the home dashboard, character selection, progress history, or backgrounding the app, audio capture is completely torn down and hardware resources are freed.

---

## 3. Data Retention & Deletion

All user data is stored locally in your private application sandbox:
- User profile name & skill level
- Practice session history and scoring estimates
- Saved vocabulary terms
- Grammatical mistake trends

### 1-Tap Data Reset
Users can wipe all stored progress, vocabulary, and session data at any time via **Profile → Danger Zone → Reset Progress**. Uninstalling the app permanently deletes all associated local databases and models from the device.
