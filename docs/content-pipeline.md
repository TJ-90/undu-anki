# Updating deck content

The companion is intended to read compatible notes after an ordinary AnkiDroid sync; it does not own the scheduler or replace the collection. Live reading and review are not yet implemented or verified. Keep using Anki and AnkiDroid as the source of truth until the compatibility checks described in [verification.md](verification.md) have passed.

The existing question and answer fields need no bulk migration. A separate publishing process may optionally use `ContentId`, `SourceUrl`, `AsOfDate`, and `Explanation` fields and topic tags. Update an existing note by stable identity when its meaning remains the same, and add genuinely new notes instead of deleting and recreating the deck; this preserves review history. Date current-affairs questions and check their sources. A materially different fact may need a new dated note.

If the publisher uses AnkiConnect, the desktop running Anki must be available for publishing. It is not intended to be needed during offline review or gameplay. Sync through AnkiDroid normally after publishing. Structural note-type changes may require a one-way sync: resolve such prompts through Anki's own workflow, never through automatic destructive conflict resolution.
