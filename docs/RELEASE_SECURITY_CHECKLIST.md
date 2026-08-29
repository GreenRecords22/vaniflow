# VaniFlow Release Security Checklist

## 1. Automated Secret Audit Results

- [x] **No Embedded API Keys**: No API keys found across entire repository (`*.kt`, `*.xml`, `*.gradle.kts`, `*.properties`, `*.json`).
- [x] **No Hardcoded Tokens**: Zero bearer tokens, private keys, or passwords committed.
- [x] **No Service Accounts**: Zero GCP/Firebase service account JSON files.
- [x] **Cleartext Traffic Disabled**: `android:usesCleartextTraffic="false"` explicitly enforced in `AndroidManifest.xml`.
- [x] **Network Security Configuration**: `network_security_config.xml` restricts connections to HTTPS only.
- [x] **Cloud Opt-in Only**: Cloud AI providers are disabled by default.
- [x] **Cache Privacy Enforcement**: Sensitive phrases, credentials, and passwords are never cached (`CacheCategory.DO_NOT_CACHE`).
- [x] **Safe Reset Progress**: Resetting progress purges local SQLite databases while strictly isolating downloaded offline AI models.

## 2. Release Signing (Milestone 13)

- [x] **Signing config wired in `app/build.gradle.kts`** — `signingConfigs.release` reads `app/keystore.properties` (gitignored).
- [x] **`keystore.properties` gitignored** — added to `app/.gitignore`; never committed. Contains only local milestone keystore passwords.
- [x] **Keystore generated locally** — `app/vaniflow-release.keystore` (RSA 2048, validity 10000 days), gitignored (`*.keystore`).
- [x] **Release bundle signed** — `app-release.aab` contains `META-INF/VANIFLOW.RSA` + `VANIFLOW.SF` + `MANIFEST.MF`.
- [x] **`*.keystore` / `*.jks` / `keystore.properties` excluded from VCS** — no key material committed.
- [ ] **Production keystore** — for Play Store distribution, generate a dedicated upload/release keystore with strong, unique passwords stored in a secrets manager; do NOT reuse the milestone keystore.

> [!IMPORTANT]
> The milestone keystore password lives only in the gitignored `app/keystore.properties`. It is a development artifact. For any real distribution, rotate to a production keystore and never commit the passwords.

