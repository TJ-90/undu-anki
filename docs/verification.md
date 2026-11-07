# Verification status

Last verified commit: `de14a4feea474a0d97f28e1821590f649afd8bb6` on branch `actions-demo-build`. Later commits may change only docs. Table = evidence for that commit. All CI checks ran in GitHub Actions on Android 15 emulator (x86_64), synthetic data only.

[Android CI run 42345222879](https://github.com/TJ-90/undu-anki/actions/runs/42345222879) (manual dispatch, AnkiDroid provider gate on). All 3 jobs pass:

| Check | Result |
|---|---|
| Unit tests, `lintDebug`, `lintRelease`, `assembleDebug` | pass |
| Emulator suite without AnkiDroid (demo flow, live mode refuses without AnkiDroid, save migration 1→2, progress + island persistence, on-device card screening incl. numeric apostrophe codes) | pass |
| AnkiDroid 2.25.3 provider gate: access, scheduler, four ratings, interrupted-review recovery, deck isolation + descendants, learn-ahead, deck + note inspection | pass |
| AnkiDroid own `revlog` after gate: exactly six submitted reviews, eases 1, 2, 3, 4, 3, 1, elapsed within 10 ms | pass |
| End-to-end live flow through real app screens (connect, choose + inspect deck, study 3 cards, earn mission), then `revlog`: three Good reviews, three confirmed journal entries | pass |

Debug artifact `mischief-island-debug-de14a4f`: `app-debug.apk`, SHA-256 `219c32d5e1245b7cae647fe46dd699e81f42011ee456c415f9300e246580b4ca` (downloaded file matched `SHA256SUMS`). Toolchain: Java 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21. Disposable build (applicationId suffix `.debug`), not family app. Each CI debug build has own throwaway signing key: old test build must be uninstalled before new one installs.

Earlier code-verified commit `ffa5816` (run 42246308952): same checks pass; artifact SHA-256 `d53fb0d50146d6c57c4866d590476fca67ecb57eaae43e0fa660f389c3baee2b`.

Detail, observed scheduler values, test coverage: [anki-compatibility.md](anki-compatibility.md).

## Real phone check (2026-11-07, manual, over adb)

- Phone: model I2019, Android 14. Had AnkiDroid 2.21.1. App refused it (only 2.25.3 verified). Parent updated AnkiDroid to 2.25.3; app then showed "Connected to AnkiDroid 2.25.3".
- Child deck inspected through the app: one Basic note type, 331 notes. First build rejected 85 of 331. Cause: **screening bug in this app**, not the deck. All 85 had an apostrophe stored as `&#x27;`; screening allowed only `&#39;`. Old message wrongly blamed images/sounds/links.
- Fix in `4ac5fa1` (report now names real reason per note, counts only, no card text) and `de14a4f` (accept numeric codes for ordinary printable characters; invalid, surrogate, control, unknown named codes like `&rarr;` still rejected).
- Result on phone with `de14a4f` build: "All 331 notes can be shown: Basic (331, Front / Back)". Deck saved in app settings.
- **No review submitted. No card shown on screen.** AnkiDroid scheduling untouched (deck counts same before and after).

## Not verified

- **Family-signed APK + in-place upgrade (blocker):** no signing secrets configured, Family workflow runs only on `main`. [Family run 42228060400](https://github.com/TJ-90/undu-anki/actions/runs/42228060400) failed early on missing `ANDROID_KEYSTORE_BASE64`, as designed. Two signed builds same signer + install-over-install keeping island still to do. See [signing.md](signing.md).
- **First real review on phone:** how real cards render (apostrophes, formatting), real rating written once to real collection, confirm in AnkiDroid. Not done. Ratings on real collection hard to undo: parent rate one card first, check AnkiDroid.
- **Physical device play:** frame rate (plan asks ≥ 30 fps full run), touch, phone/tablet layouts, screenshots unmeasured. Emulator tests drive logic and screens, not child hands.
- **Other AnkiDroid or Android versions:** only AnkiDroid 2.25.3 (emulator Android 15, phone Android 14 for screening only). Other releases blocked in app on purpose.
- **Offline / airplane mode, API disabled or permission revoked mid-session, background or interruption during real review,** parent walkthrough with text scaling + large screens: no automated coverage. Live study screens (reveal, rate, pause message, parent resolution of unconfirmed review) covered only by one happy-path flow plus unit tests of logic.
- **Fun and learning benefit** not claimed. Tests show correctness, not that child enjoys it or remembers more.

Never put private deck content or signing secrets in logs, artifacts or this file.
