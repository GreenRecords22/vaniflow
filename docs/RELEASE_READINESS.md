# VaniFlow MVP Release Candidate Readiness & Signing Guide

## 1. Release Packaging Summary

- **Release APK**: `app/build/outputs/apk/release/app-release-unsigned.apk`
- **Release Size**: **1.88 MB** (`1,884,168 bytes`)
- **Optimization**: R8 whole-program optimization + Resource Shrinking + ProGuard Rules.
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk` (20.9 MB).
- **Unit Test Coverage**: **103 / 103 passing across 26 test suites (100% pass rate)**.
- **Security**: 0 hardcoded secrets; cleartext traffic disabled; HTTPS-only policy.

---

## 2. Release Signing Instructions (For Production Deployment)

Production keystores must never be committed to Git. To sign the unsigned Release Candidate APK for Google Play Store distribution:

1. **Generate Secure Release Keystore** (Outside source repository):
   ```bash
   keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias vaniflow-key
   ```

2. **Align and Sign APK with `zipalign` and `apksigner`**:
   ```bash
   zipalign -v -p 4 app-release-unsigned.apk vaniflow-aligned.apk
   apksigner sign --ks my-release-key.jks --out vaniflow-release.apk vaniflow-aligned.apk
   ```

3. **Verify Signature**:
   ```bash
   apksigner verify --verbose vaniflow-release.apk
   ```
