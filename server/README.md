# VaniFlow AI Gateway

Secure, high-performance Node.js proxy and rate-limiting gateway for **VaniFlow: Personal AI English Tutor**.

## Overview
- **Zero Secrets on Client**: The Android client application communicates exclusively with this gateway using application tokens. Provider API keys (Groq, Gemini, DeepSeek) are stored solely in this server environment.
- **Provider Support**: Primary: **Groq** (`llama-3.1-8b-instant`), Secondary: **Google Gemini** (`gemini-1.5-flash`).
- **Rate Limiting**: Integrated sliding-window rate limiting per client IP/session.
- **Response Format**: Normalized `TutorAIResponse` JSON format and SSE token streaming.

## Quick Start

### 1. Install Dependencies
```bash
npm install
```

### 2. Configure Environment
```bash
cp .env.example .env
# Edit .env and paste your Groq API key:
# GROQ_API_KEY=gsk_...
```

### 3. Start Gateway Server
```bash
npm start
```
The gateway will start on `http://localhost:8080`.

### 4. Test Health Endpoint
```bash
curl http://localhost:8080/health
```

### 5. Android Emulator Connection
When running the VaniFlow Android application in the Android Studio emulator:
- Gateway Base URL: `http://10.0.2.2:8080/v1/chat`
- Physical Device (LAN): `http://<your-machine-lan-ip>:8080/v1/chat`
- Production: `https://gateway.vaniflow.com/v1/chat`
