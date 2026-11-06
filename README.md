# Mischief Island

An Android companion to AnkiDroid. A child answers a few questions from a parent-chosen AnkiDroid deck, plays a short runner game, and spends the stars on a local island. Questions are always answered in this app and saved in AnkiDroid; the game never changes a rating.

**Status: live study works on one tested setup; it is not family-ready.** Live study with real AnkiDroid reviews has passed automated end-to-end tests on an Android 15 emulator with AnkiDroid **2.25.3** and synthetic cards. It has not been tried on a physical phone or with a real deck, and there is no signed family build yet. Do not hand it to a child until a parent has walked through it on the real device. See [docs/verification.md](docs/verification.md) for what is and is not proven.

## Get the app

Builds come from [GitHub Actions](https://github.com/TJ-90/undu-anki/actions).

- **Test build ("Mischief Island Test"):** open the latest successful **Android CI** run, download the `mischief-island-debug-…` artifact ZIP, extract `app-debug.apk` on your Android device, and open it. Android may ask you to allow installs from the app you downloaded it with. This build is disposable, installs separately from the family app, and a later test build may need the old one uninstalled first.
- **Family build ("Mischief Island"):** choose **Family APK** → **Run workflow** on `main`. This needs the signing secrets described in [docs/signing.md](docs/signing.md) and has not yet been run. Install updates over the existing app; uninstalling deletes the local island.

## Set up live study (parent)

1. Install the official **AnkiDroid 2.25.3**, open it once, and finish its first-run setup (allow its file access). Other versions are blocked until they are tested.
2. In Mischief Island, press and hold **Parent settings** on the home screen for 3 seconds. Choose **Use my AnkiDroid deck (live study)**.
3. Tap **Allow access to AnkiDroid**, then **Choose a deck**. The app checks every note in the deck and tells you if any cannot be shown. One unsupported note blocks the deck; nothing is skipped silently.
4. Tap **Use this deck**. Choose how many questions make a mission (3 to 10) and the runner difficulty here too.

Rules the deck must meet: ordinary notes with **one** forward card (question on the front, answer on the back), text and basic formatting only. Pictures, sounds, formulas, links, cloze, and reversed or multi-card note types are not supported yet.

Sync AnkiDroid normally after changing a deck; the app reads fresh content for every question. Do not sync while a question is on screen.

If an answer could not be confirmed (for example the app was closed at the wrong moment), study pauses. The parent screen explains what to check in AnkiDroid and offers "recorded" or "not recorded". Nothing is ever sent twice automatically.

## Limits

- Game progress is saved on this device only. It does not sync between a phone and a tablet, and uninstalling can delete it.
- Review history is read from AnkiDroid; this app keeps no copy of your cards.
- Demo mode (the default until a parent switches) uses sample questions and a separate demo island.

## Developer notes

JDK 17 and Android SDK 35 are required for a local build: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug`. Toolchain: Gradle 8.9 (checksum-validated wrapper), Android Gradle Plugin 8.7.3, Kotlin 2.0.21, minimum Android API 26. GitHub Actions is the canonical build.

The opt-in **AnkiDroid provider gate** (run **Android CI** manually with `anki_provider_gate` on) installs the pinned official AnkiDroid on a disposable emulator and runs the provider, rating, recovery, history, deck-selection and end-to-end live-flow tests described in [docs/anki-compatibility.md](docs/anki-compatibility.md). It never touches personal data. See `plan.md` for the product and verification contracts.
