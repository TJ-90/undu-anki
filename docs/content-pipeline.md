# Updating deck content

The companion is intended to read compatible notes after an ordinary AnkiDroid sync; it does not own the scheduler or replace the collection. The app reads fresh note content for every question and re-checks the card layout each time, so a changed note type or field is refused instead of guessed. Live study is verified only on one setup (see [verification.md](verification.md)); AnkiDroid stays the source of truth for cards and scheduling.

The existing question and answer fields need no bulk migration. A separate publishing process may optionally use `ContentId`, `SourceUrl`, `AsOfDate`, and `Explanation` fields and topic tags. Update an existing note by stable identity when its meaning remains the same, and add genuinely new notes instead of deleting and recreating the deck; this preserves review history. Date current-affairs questions and check their sources. A materially different fact may need a new dated note.

If the publisher uses AnkiConnect, the desktop running Anki must be available for publishing. It is not intended to be needed during offline review or gameplay. Sync through AnkiDroid normally after publishing. Structural note-type changes may require a one-way sync: resolve such prompts through Anki's own workflow, never through automatic destructive conflict resolution.
