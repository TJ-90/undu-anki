# AnkiDroid compatibility gate

**Passed on AnkiDroid 2.25.3 / Android 15 emulator only.** The app offers live study only for releases listed in `AnkiConnection.VERIFIED_VERSIONS`. The provider write path and rating mapping are verified on one release (below), and reconciliation of interrupted reviews is verified against the real provider. Deck isolation, learn-ahead and AnkiDroid's own review history are also verified on that release. The in-app live study path is verified end to end on that release (below). Other releases, physical devices, and offline use are not. Do not connect a child's collection or treat the synthetic tests as proof of provider compatibility.

Before enabling real reviews, validate the installed AnkiDroid version and Android version with an isolated synthetic collection. Verify package visibility, permission and API-disabled states; selected-deck and descendant isolation; scheduler selection including learn-ahead; content and template screening; and actual history and scheduler changes for each supported rating. Record version, test device, evidence, and exact observed button mapping here. Unsupported or ambiguous versions must remain blocked.

Provider `update` return values alone do not establish a completed review. The journal persists a pending attempt before a write and must not replay it after interruption. A sufficiently consistent readback can confirm it; otherwise a parent must reconcile the uncertain outcome. Neither local unit tests nor emulator-only database tests establish real-provider behavior. Never publish private cards or collection data in test logs or artifacts.

## Evidence: AnkiDroid 2.25.3 on Android 15 emulator

Runs [42237479240](https://github.com/TJ-90/undu-anki/actions/runs/42237479240) and [42238568437](https://github.com/TJ-90/undu-anki/actions/runs/42238568437) (the second also covers reconciliation below)(commits after `9a1bd74` on `actions-demo-build`; the rating test first passed in 42236881319). Official `variant-abi-AnkiDroid-2.25.3-x86_64.apk` (SHA-256 `154101f0…edac7`), fresh disposable emulator, empty synthetic collection, no personal data. The suite is opt-in and refuses to write to a nonempty collection or a non-emulator.

- **Onboarding precondition:** a fresh install blocks on an "All files access" permission screen until granted; until then the collection is not opened and every provider query returns a null cursor. The gate grants it with `appops set com.ichi2.anki MANAGE_EXTERNAL_STORAGE allow` and relaunches. A real device needs the parent to complete AnkiDroid's own onboarding first; the companion should report this as "AnkiDroid is not ready", not as a disabled API.
- **Provider access:** the companion's `READ_WRITE_DATABASE` permission and package visibility work; default deck read; unknown deck is reported as unavailable, not as an empty queue.
- **Scheduler and readback:** four synthetic Basic notes were supplied one at a time, each with `button_count` 4, ordinal 0, zero prior reps; card state read back identically before answering.
- **Ratings (write `answer_ease` 1–4 plus `time_taken` to `/schedule`):** each was confirmed by the coordinator through readback (reps +1 with a newer last-review time), with pending then confirmed journal events. Observed outcomes on a new card with default deck options:

| Rating | `answer_ease` | Observed outcome |
|---|---|---|
| Again | 1 | learning, due about 71 s out |
| Hard | 2 | learning, due about 333 s out |
| Good | 3 | learning, due about 668 s out |
| Easy | 4 | graduated to review; 5 days in one run, 3 days in another (AnkiDroid fuzzes the default 4-day interval) |

  Delays (72 / 389 / 684 s in the latest run) include a few seconds of test time and vary slightly between runs. Order and values match the default 1 m / 5.5 m / 10 m steps and confirm the button order. Only the four-button scheduler is accepted; any other `button_count` is refused.
- **Review history (AnkiDroid's own `revlog`):** the provider has no history endpoint, so on the disposable emulator the gate pulls the synthetic collection (`/storage/emulated/0/AnkiDroid/collection.anki2`, readable after `adb root`) and reads `revlog` on the runner. Run [42240534881](https://github.com/TJ-90/undu-anki/actions/runs/42240534881) found exactly six rows, matching the submitted reviews in order: ease 1, 2, 3, 4, 3, 1 with elapsed times 1500, 1500, 1500, 1500, 900, 500 ms (all type 0, first review of a new card). Learning-step intervals were stored as -60, -330, -600 seconds and Easy as 3 days. This confirms the button mapping in history, that elapsed time is recorded, and that interrupted-review recovery and an abandoned attempt wrote no extra entry. The copied database is deleted before artifacts upload; only a text summary is kept.
- **Deck selection:** an unrelated deck never leaked a card; selecting a parent deck did include a card in its child deck, so a selected deck includes its descendants on this release (`Gate::Parent` / `Gate::Parent::Child` / `Gate::Other` created through the provider).
- **Learn-ahead:** with AnkiDroid's default nonzero learn-ahead, once no new cards remained the scheduler supplied a learning card due about a minute ahead, and the companion followed it without rescheduling.

- **Interrupted-review reconciliation (real provider, real on-disk journal):**
  - *Process dies after the provider write, before local confirmation:* the coordinator ends `UNCERTAIN`, the provider holds exactly one new review, and `ReviewRecovery` (read-only; it holds no write capability) reads the card back, confirms the one clean review, and the attempt resolves. A second readback shows no extra review was written.
  - *Process dies before the provider write:* recovery reads the card unchanged, does **not** confirm or resubmit, and leaves the attempt `uncertain` for a parent. After the parent chooses "not recorded", the scheduler supplies the same card again with its state untouched.
  - Contradictory states (two reviews, count without timestamp, regression, different card, unreadable card, pre-schema-2 rows) are unit-tested and always routed to a parent, never confirmed.
  - A real schema-1 save upgrades in place to schema 2 with wallet and penguin intact; its old unresolved attempt is flagged unreadable rather than guessed at.

- **End to end through the real app screens:** run [42246308952](https://github.com/TJ-90/undu-anki/actions/runs/42246308952) wipes the disposable emulator's AnkiDroid, creates a deck with three synthetic Basic notes, and drives `MainActivity` in live mode: parent settings, connection check ("Connected to AnkiDroid 2.25.3"), deck choice, compatibility summary ("All 3 notes can be shown"), "Use this deck", then three reveal-and-Good rounds until "Continue mission" appears. Afterwards: each card has exactly one review in the provider, the app's journal has three confirmed attempts and one earned ticket, and AnkiDroid's `revlog` has three ease-3 rows with real foreground times (230, 69 and 72 ms). The copied database is deleted before upload.
- **Setup inspection against real note types:** a plain Basic deck is accepted and its fields are derived from the template; a deck containing "Basic (and reversed card)" is blocked ("2 card templates"); a deck with a note whose field holds an image is blocked and counted. These checks run on the device's regex engine, which rejected an unescaped brace that the desktop JVM accepted (fixed; guarded by an on-device test).
- **Permission precondition:** the companion's API permission is granted to the freshly installed app; revoking it, or an AnkiDroid with its API disabled, falls into the "permission needed" and "not ready" states, which are unit-tested but not exercised against a real revocation.

## Still unverified

The parent screen for resolving an unconfirmed review has no UI test (its logic is unit-tested and the real-provider recovery is verified), an AnkiDroid with the API disabled or permission revoked mid-session, the pause message and Home-while-presenting paths, other AnkiDroid or Android versions, physical devices, offline review, and any deck other than synthetic ones. Do not point the app at a child's real deck without a parent walkthrough on the real device.
