#!/usr/bin/env python3
"""Generate the repetition probe set from the vendored OpenMantra dataset (#152).

Derived case metadata (aligned source/reference text) is CC BY-NC 4.0 like the source. Do not
redistribute outside the project; regenerate from vendor instead — the output is gitignored.

This set is deliberately NOT part of `translation-quality/cases/`. That gate's selection rule is
seeded-random and never targeted at a known failure mode (#44), so that "passes the gate" cannot
decay into "avoids these particular pages". These bubbles are targeted by construction: they are
every bubble in the dataset that carries real repetition, the thing a repetition penalty eats. The
probe is reported alongside the gate and never carries a pass bar of its own.

Bubble granularity, not pages: the shipped path is one call per bubble with the KV cache cleared
between, so page context is a variable only on the batch path, which is not shipped (#139).
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from run_eval_lib import PROBE_MIN_RUN, longest_repeat_run, repeat_dominance

VENDOR = Path("vendor/open-mantra-dataset")
ANNOTATION = VENDOR / "annotation.json"
OUT = Path("eval/repetition-probe/bubbles.json")

# A bubble is a repetition bubble when it carries a run of PROBE_MIN_RUN or more of the same unit —
# the same character (あああ), the same substring (もったいないもったいないもったいない), or the same
# word in the English reference (ha ha ha) — AND that run dominates the bubble. Selection uses the
# scorer's own `longest_repeat_run`, so a bubble can never be selected on a run the scorer cannot
# see; the two rules move together or not at all.
#
# The dominance clause separates a bubble whose content *is* the repetition from a sentence that
# merely contains あああっ before real dialogue: only the former is evidence about a repetition
# penalty. It is this project's addition, not #152's — the issue asks only for bubbles "carrying
# real repetition". Over the 214 vendored pages the rule selects 22 bubbles across 17 pages, none
# among the 17 gate pages; 17 of them carry a reference run the harm scorer can score. #152's own
# scan, run by hand, counted 18 bubbles across 16 pages of which 11 kept the repetition in the
# reference, so this rule is the broader of the two. It admits nothing that is not repetition, and
# the probe is never gated, so a wider set costs run time and nothing else.
MIN_DOMINANCE = 0.4


def is_repetition_bubble(source_ja: str, reference_en: str) -> bool:
    run = max(longest_repeat_run(source_ja), longest_repeat_run(reference_en))
    return run >= PROBE_MIN_RUN and repeat_dominance(source_ja) > MIN_DOMINANCE


def main() -> None:
    if not ANNOTATION.exists():
        raise SystemExit(f"Annotation not found: {ANNOTATION}; populate vendor/ first")

    data = json.loads(ANNOTATION.read_text(encoding="utf-8"))
    bubbles = []
    for book in data:
        for page in book["pages"]:
            for index, text in enumerate(page.get("text", [])):
                source = (text.get("text_ja") or "").replace("\n", " ").replace("\r", "").strip()
                reference = (text.get("text_en") or "").replace("\n", " ").replace("\r", "").strip()
                if not is_repetition_bubble(source, reference):
                    continue
                bubbles.append(
                    {
                        "book_title": book["book_title"],
                        "page_index": page["page_index"],
                        "bubble_index": index,
                        "source": source,
                        "reference": reference,
                    }
                )

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps({"bubbles": bubbles}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    pages = {(b["book_title"], b["page_index"]) for b in bubbles}
    print(f"Wrote {OUT}: {len(bubbles)} bubbles across {len(pages)} pages")


if __name__ == "__main__":
    main()
