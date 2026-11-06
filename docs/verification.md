# Verification status

Last verified commit: `ffa58167cf0a06f8d83394b890dbd25f4d14b713` on branch `actions-demo-build`. Later commits may change only documentation; the table below is evidence for that commit. All checks ran in GitHub Actions on an Android 15 emulator (x86_64) with synthetic data only.

[Android CI run 42246308952](https://github.com/TJ-90/undu-anki/actions/runs/42246308952) (manual dispatch with the AnkiDroid provider gate):

| Check | Result |
|---|---|
| Unit tests, `lintDebug`, `lintRelease`, `assembleDebug` | passed |
| Emulator suite without AnkiDroid (demo flow, live mode refuses without AnkiDroid, save migration 1→2, progress and island persistence, on-device card screening) | passed |
| AnkiDroid 2.25.3 provider gate: access, scheduler, four ratings, interrupted-review recovery, deck isolation and descendants, learn-ahead, deck and note inspection | passed |
| AnkiDroid's own `revlog` after the gate: exactly the six submitted reviews, eases 1, 2, 3, 4, 3, 1, elapsed times within 10 ms | passed |
| End-to-end live flow through the real app screens (connect, choose and inspect a deck, study three cards, earn a mission), then `revlog`: three Good reviews and three confirmed journal entries | passed |

Debug artifact `mischief-island-debug-ffa5816`: `app-debug.apk`, SHA-256 `d53fb0d50146d6c57c4866d590476fca67ecb57eaae43e0fa660f389c3baee2b` (the downloaded file matched `SHA256SUMS`). Toolchain: Java 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21. It is a disposable build (applicationId suffix `.debug`), not the family app.

Detail, observed scheduler values, and what each test covers: [anki-compatibility.md](anki-compatibility.md).

## Not verified

- **Family-signed APK and in-place upgrade (blocker):** no signing secrets are configured, and the Family workflow runs only on `main`. [Family run 42228060400](https://github.com/TJ-90/undu-anki/actions/runs/42228060400) failed early on the missing `ANDROID_KEYSTORE_BASE64`, as designed. Two signed builds with the same signer, and an install-over-install test that keeps the island, are still to do. See [signing.md](signing.md).
- **Physical device:** no phone or tablet was used. Real gameplay feel, frame rate (the plan asks for at least 30 fps over a full run), touch gestures, phone and tablet layouts, and a screenshot set are unmeasured. Emulator tests drive the runner's logic and the screens, not a child's hands.
- **Real deck:** nothing was tested against the child's actual deck. Whether its notes pass the compatibility check is unknown until the parent runs deck setup.
- **Other AnkiDroid or Android versions:** only AnkiDroid 2.25.3 on Android 15. Other releases are deliberately blocked in the app.
- **Offline use and airplane mode, API-disabled or permission-revoked mid-session, background and interruption during a real review,** and the full parent walkthrough with text scaling and large screens have no automated coverage yet. The live study screens (reveal, rate, pause message, parent resolution of an unconfirmed review) are covered only by the one happy-path flow above plus unit tests of the underlying logic.
- **Fun and learning benefit** are not claimed. These tests show correctness, not that the child enjoys it or remembers more.

Never include private deck content or signing secrets in logs, artifacts or this file.
