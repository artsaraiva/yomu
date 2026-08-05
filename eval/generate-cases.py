#!/usr/bin/env python3
"""Generate eval cases from the vendored OpenMantra dataset.

Derived case metadata (boxes and aligned text) is CC BY-NC 4.0 like the source.
Do not redistribute outside the project; regenerate from vendor instead.
"""

import json
import shutil
from pathlib import Path

from PIL import Image

VENDOR = Path("vendor/open-mantra-dataset")
ANNOTATION = VENDOR / "annotation.json"
IMAGES = VENDOR / "images"

SELECTED = [
    # (book_title, page_index, case_id)
    ("tojime_no_siora", 1, "tojime-sparse-title"),
    ("tojime_no_siora", 5, "tojime-dense-action"),
    ("tojime_no_siora", 29, "tojime-narration"),
    ("balloon_dream", 10, "balloon-dense-dialogue"),
    ("balloon_dream", 4, "balloon-mixed-dialogue"),
    ("boureisougi", 12, "bourei-dense-emotional"),
    ("boureisougi", 1, "bourei-sparse-single"),
    ("rasetugari", 5, "rasetugari-action-fantasy"),
]


def label_for(text: str) -> str:
    if text is None:
        return "speech"
    lowered = text.lower()
    if "..." in text or len(text) > 60 or any(w in lowered for w in ("narration", " exposition")):
        return "narration"
    if any(w in lowered for w in ("bang", "boom", "crash", "ド", "バ", "ガ")):
        return "sfx"
    return "speech"


def main() -> None:
    if not ANNOTATION.exists():
        raise SystemExit(f"Annotation not found: {ANNOTATION}; populate vendor/ first")

    data = json.loads(ANNOTATION.read_text(encoding="utf-8"))
    book_index = {b["book_title"]: b["pages"] for b in data}

    bd_root = Path("eval/bubble-detection/cases")
    tq_root = Path("eval/translation-quality/cases")
    bd_root.mkdir(parents=True, exist_ok=True)
    tq_root.mkdir(parents=True, exist_ok=True)

    for book, page_idx, case_id in SELECTED:
        pages = book_index[book]
        page = next((p for p in pages if p["page_index"] == page_idx), None)
        if page is None:
            raise SystemExit(f"Page {page_idx} not found in {book}")

        rel_img = page["image_paths"]["ja"]
        src_img = VENDOR / rel_img
        texts = page.get("text", [])

        with Image.open(src_img) as im:
            width, height = im.size

        expected = {
            "image_width": width,
            "image_height": height,
            "boxes": [
                {
                    "x": t["x"],
                    "y": t["y"],
                    "w": t["w"],
                    "h": t["h"],
                    "label": label_for(t.get("text_ja", "")),
                }
                for t in texts
            ],
        }

        src_lines = [t.get("text_ja", "") for t in texts]
        ref_lines = [t.get("text_en", "").replace("\n", " ").replace("\r", "") for t in texts]

        bd_case = bd_root / case_id
        tq_case = tq_root / case_id
        bd_case.mkdir(parents=True, exist_ok=True)
        tq_case.mkdir(parents=True, exist_ok=True)

        shutil.copy2(src_img, bd_case / "page.jpg")
        (bd_case / "expected.json").write_text(
            json.dumps(expected, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

        shutil.copy2(src_img, tq_case / "page.jpg")
        (tq_case / "source.txt").write_text(
            "\n".join(src_lines) + "\n",
            encoding="utf-8",
        )
        (tq_case / "reference.txt").write_text(
            "\n".join(ref_lines) + "\n",
            encoding="utf-8",
        )

        print(f"Created {case_id}: {len(texts)} bubbles, {width}x{height}")


if __name__ == "__main__":
    main()
