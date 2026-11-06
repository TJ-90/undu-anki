"""Compare AnkiDroid's own revlog (from a pulled synthetic collection) with the reviews a test submitted.

usage: check-revlog.py <evidence-dir> <collection-dir> <name> <eases> [<times-ms>]
Writes revlog-<name>.txt, deletes the copied database, and exits nonzero on any mismatch.
"""
import glob
import os
import shutil
import sqlite3
import sys
import tempfile

evidence, collection, name, eases = sys.argv[1:5]
times = sys.argv[5] if len(sys.argv) > 5 else ""
expected_eases = [int(x) for x in eases.split(",")]
expected_times = [int(x) for x in times.split(",")] if times else None

work = tempfile.mkdtemp()
for path in glob.glob(os.path.join(collection, "collection.anki2*")):
    shutil.copy(path, work)
connection = sqlite3.connect(os.path.join(work, "collection.anki2"))
rows = connection.execute("select ease, time, type, ivl from revlog order by id").fetchall()
got_eases = [row[0] for row in rows]
ok = got_eases == expected_eases
if expected_times is not None:
    ok = ok and [row[1] for row in rows] == expected_times
else:
    ok = ok and all(0 <= row[1] <= 60000 for row in rows)
lines = ["ease,time_ms,type,ivl"] + [",".join(map(str, row)) for row in rows]
lines += [f"expected_eases={expected_eases}", f"expected_times={expected_times}", f"match={ok}"]
with open(os.path.join(evidence, f"revlog-{name}.txt"), "w") as out:
    out.write("\n".join(lines) + "\n")
print("\n".join(lines))
shutil.rmtree(collection)
if not ok:
    sys.exit(f"revlog ({name}) does not match the reviews the test submitted")
