# VaniFlow Offline Model Troubleshooting Guide

**Classification:** VERIFIED  

---

## 1. Model Lifecycle States

1. `NOT_INSTALLED`: Model is not present in app storage.
2. `DOWNLOADING`: Download stream in progress via OkHttp.
3. `VERIFYING`: SHA-256 checksum and byte size validation.
4. `READY`: Validated and available for instant local inference.
5. `CORRUPTED`: Checksum mismatch. File is quarantined and deleted.
6. `FAILED`: Network or storage error. UI presents "Retry" and "Cancel" buttons.

---

## 2. Troubleshooting Steps

- **Insufficient Storage:**
  - VaniFlow Lite requires 500MB + 50MB safety margin.
  - The app displays *"Not enough storage to install VaniFlow Lite."*
- **Network Drops during Download:**
  - OkHttp auto-retries on connection failure. If network is lost, state transitions to `FAILED` and user can tap **Retry**.
- **Model Persistence Across App Restarts:**
  - Downloaded models are stored in `context.filesDir/models/`.
  - On app launch, `DefaultModelManager` auto-detects existing verified models and marks them `READY`.