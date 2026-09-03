# VaniFlow P7 Cloud AI Gateway Setup & Configuration Guide

## 1. Prerequisites
- Node.js (v18+ or v20+ LTS)
- npm or yarn

---

## 2. Server Installation & Configuration

### 2.1 Navigate to Gateway Directory
```bash
cd server
npm install
```

### 2.2 Configure Server Environment Variables
Copy the template configuration file:
```bash
cp .env.example .env
```

Edit `.env` with your preferred editor:
```env
PORT=8080
NODE_ENV=development
VANIFLOW_APP_ID=com.vaniflow.app

# Primary Provider: Groq (Recommended for low-latency conversational English)
# Get a free key at https://console.groq.com/keys
GROQ_API_KEY=gsk_your_groq_api_key_here
DEFAULT_MODEL=llama-3.1-8b-instant

# Secondary Provider: Google Gemini (Optional fallback)
# Get a key at https://aistudio.google.com/app/apikey
GEMINI_API_KEY=
```

### 2.3 Start the Gateway
```bash
npm start
```
The server will bind to `http://localhost:8080`.

---

## 3. Connecting the Android App

### 3.1 Android Studio Emulator
The Android emulator routes localhost traffic via `10.0.2.2`:
- Default development endpoint: `http://10.0.2.2:8080/v1/chat`

### 3.2 Physical Android Device (over Wi-Fi)
1. Ensure your Android device and development PC are connected to the same Wi-Fi network.
2. Find your PC's LAN IP (e.g. `192.168.1.50`).
3. Set the endpoint in `ApiConfigStore` or runtime config:
   `http://192.168.1.50:8080/v1/chat`

---

## 4. Security & Key Rotation Procedures
- **Never commit `.env` to Git**: `.env` is listed in `.gitignore`.
- **Key Rotation**: To rotate a provider key, simply update `GROQ_API_KEY` or `GEMINI_API_KEY` in the server environment and restart the Node.js process. No Android app update or APK recompilation is required.
- **Offline / Cloud Disabled Mode**: If no key is set or network is disabled, VaniFlow seamlessly continues using on-device Qwen2.5-0.5B and offline rule-based dialogue.
