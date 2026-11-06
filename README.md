# Mischief Island

**Status: synthetic demo only.** The app offers sample questions, runner gameplay, and a separate local demo island save. Real AnkiDroid study and the family build are not verified. Do not use it for a family trial.

The planned distribution is [GitHub Actions](https://github.com/TJ-90/undu-anki/actions). The debug build is disposable and installs separately as “Mischief Island Test”; it is not a durable family installation. Once implemented and verified, choose **Family APK** → **Run workflow** on `main`, download the artifact ZIP from the completed run, extract it on Android and open the APK. Android may ask you to allow installs from that download source. Install updates over the existing family app; uninstalling can delete its local island data. Game saves are local and do not sync through AnkiWeb.

Family builds require repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Keep a secure backup of the same keystore: losing it prevents signed in-place updates. The family version code is 1000 plus the workflow run number; keep this formula stable.

Developer fallback: JDK 17 and Android SDK 35 are required. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug`. Toolchain: Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, SDK 35, minimum Android API 26. The wrapper validates the Gradle distribution SHA-256.

AnkiDroid integration, content compatibility, real-device verification, stable signed upgrade proof, and an Actions-produced APK remain unverified and are not yet delivered. See `plan.md` for the product and verification contracts.
