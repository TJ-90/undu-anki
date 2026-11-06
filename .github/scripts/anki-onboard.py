"""Drive a freshly installed AnkiDroid through first-run onboarding on a disposable emulator.

A fresh install blocks on an "All files access" permission screen; until it is granted the collection
is not opened and every provider query returns a null cursor. Never run this against a personal device.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.ichi2.anki"
evidence = Path(sys.argv[1])
evidence.mkdir(parents=True, exist_ok=True)


def adb(*args, check=False, capture=False):
    return subprocess.run(["adb", *args], check=check, capture_output=capture,
                          stdout=None if capture else subprocess.DEVNULL, stderr=None if capture else subprocess.DEVNULL)


def dump(name):
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    xml = subprocess.run(["adb", "exec-out", "cat", "/sdcard/window.xml"], capture_output=True).stdout
    (evidence / name).write_bytes(xml)
    try:
        return ET.fromstring(xml)
    except ET.ParseError:
        return None


def tap_center(node):
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not bounds:
        return False
    x1, y1, x2, y2 = map(int, bounds.groups())
    subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)], check=True)
    return True


def launch():
    adb("shell", "monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")


launch()
started = False
for _ in range(60):
    time.sleep(2)
    root = dump("onboarding.xml")
    if root is None:
        continue
    for node in root.iter("node"):
        if node.get("resource-id", "").endswith(":id/get_started") and tap_center(node):
            started = True
            break
    if started:
        break
if not started:
    raise SystemExit("AnkiDroid Get started screen not found; see onboarding.xml")

adb("shell", "appops", "set", PACKAGE, "MANAGE_EXTERNAL_STORAGE", "allow", check=True)
launch()
reached = False
for _ in range(40):
    time.sleep(3)
    root = dump("after-get-started.xml")
    if root is None:
        continue
    nodes = list(root.iter("node"))
    if any(n.get("resource-id", "").endswith(":id/deck_picker_content_wrapper") for n in nodes):
        reached = True
        break
    for node in nodes:
        if node.get("resource-id", "").endswith(":id/continue_button") and node.get("enabled") == "true":
            reached = tap_center(node)
            break
    if reached:
        break
if not reached:
    raise SystemExit("AnkiDroid never reached the deck picker; see after-get-started.xml")
time.sleep(20)
dump("after-continue.xml")
