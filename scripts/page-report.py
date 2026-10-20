#!/usr/bin/env python3
"""Translates one page through the overlay on a phone or emulator and pulls its page report (#379).

Usage:
  scripts/page-report.py <page> <out-dir>
  scripts/page-report.py --compare <report-dir> <report-dir>

<page> is a .jpg path, or the file name of a page already on the device; a path that is not on the
device yet is pushed to /sdcard/Pictures. The page opens in Google Photos as Photos last showed it,
so show or hide its toolbar first to match what you are testing. Reading must be on, sharing the
entire screen.

<out-dir> gets the unzipped report (capture.png, page.json, reply.txt) and done.png, a screenshot of
the drawn page. --compare pairs the bubbles of two pulled reports by position, since bubble ids
shift between runs. With more than one device attached, set ANDROID_SERIAL.
"""
from __future__ import annotations

import io
import json
import re
import subprocess
import sys
import time
import zipfile
from pathlib import Path

APP = "com.yomu.app"
PHOTOS = "com.google.android.apps.photos"
REPORT_BUTTON = "Report last page"
# Any error the service logs ends the page without a report; the two successes are a run and a recall.
PAGE_ENDED = re.compile(r"Pipeline complete|Page recall hit=true| E OverlayService")
PAGE_TIMEOUT_S = 300


def adb(*args: str) -> str:
    return subprocess.run(["adb", *args], check=True, capture_output=True, text=True).stdout


def adb_bytes(*args: str) -> bytes:
    return subprocess.run(["adb", *args], check=True, capture_output=True).stdout


def fail(message: str) -> None:
    sys.exit(f"page-report: {message}")


def media_id(name: str) -> str | None:
    rows = adb("shell", "content query --uri content://media/external/images/media --projection _id:_display_name")
    ids = re.findall(rf"_id=(\d+), _display_name={re.escape(name)}\s*$", rows, re.M)
    return ids[-1] if ids else None


def page_on_device(page: str) -> str:
    name = Path(page).name
    found = media_id(name)
    if found:
        return found
    if not Path(page).is_file():
        fail(f"{name} is not on the device and {page} is not a local file")
    adb("push", page, f"/sdcard/Pictures/{name}")
    # An unscanned file makes Photos open a different image.
    adb("shell", f"am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/{name}")
    time.sleep(2)
    return media_id(name) or fail(f"MediaStore never listed {name}")


def yomu_windows() -> list[str]:
    windows = adb("shell", "dumpsys window windows").split("\n  Window #")
    return [w for w in windows if re.match(r"\d+ Window\{\w+ u0 com\.yomu\.app\}", w)]


def floating_button() -> tuple[int, int]:
    """Centre of the floating button: the only square Yomu overlay window."""
    for window in yomu_windows():
        size = re.search(r"\((\d+)x(\d+)\)", window)
        frame = re.search(r" frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", window)
        if size and frame and size[1] == size[2]:
            left, top, right, bottom = map(int, frame.groups())
            return (left + right) // 2, (top + bottom) // 2
    fail("no floating button: turn reading on in Yomu first")


def page_drawn() -> bool:
    """The drawn page is the one full-screen Yomu window with a transparent surface."""
    return any("(fillxfill)" in w and "fmt=TRANSPARENT" in w for w in yomu_windows())


def node_centre(attribute: str, value: str) -> tuple[int, int] | None:
    """Centre of a node in any window; a plain uiautomator dump skips Yomu's overlays."""
    adb("shell", "uiautomator dump --windows /sdcard/page-report-ui.xml")
    ui = adb("shell", "cat /sdcard/page-report-ui.xml")
    found = re.search(rf'{attribute}="{re.escape(value)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', ui)
    if not found:
        return None
    left, top, right, bottom = map(int, found.groups())
    return (left + right) // 2, (top + bottom) // 2


def clear_overlay(button: tuple[int, int]) -> None:
    """Closes quick settings and dismisses a drawn page, either of which eats the next tap."""
    for _ in range(3):
        close = node_centre("content-desc", "Close quick settings")
        if close:
            adb("shell", f"input tap {close[0]} {close[1]}")
        elif page_drawn():
            adb("shell", f"input tap {button[0]} {button[1]}")
        else:
            return
        time.sleep(1)
    fail("quick settings or a drawn page would not go away")


def reports() -> list[str]:
    return adb("shell", f"run-as {APP} ls cache/reports").split()


def translate(page: str, out: Path) -> None:
    page_id = page_on_device(page)
    adb("shell", f"am force-stop {PHOTOS}")
    adb("shell", f"am start -a android.intent.action.VIEW -d content://media/external/images/media/{page_id} -p {PHOTOS}")
    time.sleep(4)

    x, y = floating_button()
    clear_overlay((x, y))
    # logcat -T takes the device's own clock in this format, not an epoch.
    since = adb("shell", "date '+%m-%d %H:%M:%S.000'").strip()
    adb("shell", f"input tap {x} {y}")
    deadline = time.time() + PAGE_TIMEOUT_S
    ended = []
    while not ended:
        if time.time() > deadline:
            fail(f"the page did not finish within {PAGE_TIMEOUT_S} s")
        time.sleep(2)
        log = adb("logcat", "-d", "-T", since, "-s", "OverlayService:*")
        ended = [line for line in log.splitlines() if PAGE_ENDED.search(line)]
    print(ended[-1])
    if " E OverlayService" in ended[-1]:
        fail("the page failed, so it has no report")

    time.sleep(3)
    out.mkdir(parents=True, exist_ok=True)
    (out / "done.png").write_bytes(adb_bytes("exec-out", "screencap", "-p"))

    # While a page is drawn, the first long-press only dismisses it; the next opens quick settings.
    button = None
    for _ in range(3):
        adb("shell", f"input swipe {x} {y} {x} {y} 1500")
        time.sleep(1)
        button = node_centre("text", REPORT_BUTTON)
        if button:
            break
    if not button:
        fail("quick settings never showed the report button")
    before = set(reports())
    adb("shell", f"input tap {button[0]} {button[1]}")
    for _ in range(10):
        time.sleep(1)
        new = [name for name in reports() if name not in before]
        if new:
            break
    else:
        fail("no new report appeared")
    adb("shell", "input keyevent KEYCODE_BACK")
    time.sleep(1)
    clear_overlay((x, y))

    report = adb_bytes("exec-out", "run-as", APP, "cat", f"cache/reports/{new[-1]}")
    zipfile.ZipFile(io.BytesIO(report)).extractall(out)
    print(f"{new[-1]} -> {out}")
    print_bubbles(load(out))


def load(report_dir: Path) -> dict:
    return json.loads((report_dir / "page.json").read_text())


def describe(bubble: dict | None) -> str:
    if bubble is None:
        return "-"
    # Reports from before #366 have no confidence.
    confidence = bubble.get("ocrConfidence")
    score = "" if confidence is None else f" p={confidence:.3f}" + (" SKIPPED" if bubble["skipped"] else "")
    dropped = " (dropped)" if bubble.get("ocrDropped") else ""
    return f"#{bubble['id']} {bubble['ocrText']!r}{score}{dropped} -> {bubble['translation']!r}"


def bounds(bubble: dict) -> str:
    return "[" + ",".join(str(int(v)) for v in bubble["bounds"]) + "]"


def print_bubbles(page: dict) -> None:
    print(f"recalled={page['recalled']} bubbles={len(page['bubbles'])}")
    for bubble in page["bubbles"]:
        print(f"{bounds(bubble):24} {describe(bubble)}")


def centre(bubble: dict) -> tuple[float, float]:
    left, top, right, bottom = bubble["bounds"]
    return (left + right) / 2, (top + bottom) / 2


def compare(first: Path, second: Path) -> None:
    a, b = load(first)["bubbles"], load(second)["bubbles"]
    unmatched = list(b)
    print(f"A={first} ({len(a)} bubbles)  B={second} ({len(b)} bubbles)")
    for bubble in a:
        ax, ay = centre(bubble)
        twin = min(unmatched, key=lambda o: abs(centre(o)[0] - ax) + abs(centre(o)[1] - ay), default=None)
        if twin is not None and abs(centre(twin)[0] - ax) + abs(centre(twin)[1] - ay) > 16:
            twin = None
        if twin is not None:
            unmatched.remove(twin)
        if twin is None:
            status = "ONLY IN A"
        else:
            status = "same OCR" if twin["ocrText"] == bubble["ocrText"] else "OCR CHANGED"
        print(f"{bounds(bubble):24} {status}\n  A {describe(bubble)}\n  B {describe(twin)}")
    for bubble in unmatched:
        print(f"{bounds(bubble):24} ONLY IN B\n  A -\n  B {describe(bubble)}")


def main(args: list[str]) -> None:
    if len(args) == 3 and args[0] == "--compare":
        compare(Path(args[1]), Path(args[2]))
    elif len(args) == 2 and not args[0].startswith("-"):
        translate(args[0], Path(args[1]))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
