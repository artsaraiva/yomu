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

import itertools
import json
import re
from pathlib import Path

VENDOR = Path("vendor/open-mantra-dataset")
ANNOTATION = VENDOR / "annotation.json"
OUT = Path("eval/repetition-probe/bubbles.json")

# Characters that carry no repetition signal: ellipsis and dashes repeat in most bubbles in the
# dataset (a run of "." is punctuation, not a verbal tic), and the sokuon っ is a phonetic stop
# inside はははっ rather than part of the run.
SKIP = "…。、.,!?！？ー－〜～・_-*'\"ｰっッ 　\n\r"

# A bubble is a repetition bubble when it carries a run of 3 or more of the same unit — the same
# character (あああ), the same substring (もったいないもったいないもったいない), or, in the human
# English reference, the same word (ha ha ha) — AND that run spans more than 40% of the bubble's
# non-punctuation characters. The dominance clause is what separates a bubble whose content *is*
# the repetition from a sentence that happens to contain あああっ before real dialogue: only the
# former is evidence about a repetition penalty. Over the 214 vendored pages this selects 18
# bubbles across 15 pages, none of which is among the 17 gate pages.
MIN_RUN = 3
MIN_DOMINANCE = 0.4


def strip_punctuation(text: str) -> str:
    return "".join(c for c in text or "" if c not in SKIP)


def longest_char_run(text: str) -> int:
    """Longest run of one repeated character, punctuation ignored."""
    runs = (len(list(g)) for c, g in itertools.groupby(text or "") if c not in SKIP)
    return max(runs, default=0)


def longest_word_run(text: str) -> int:
    """Longest run of one repeated word. Only meaningful on the English reference."""
    tokens = re.findall(r"[a-z0-9']+", (text or "").lower())
    return max((len(list(g)) for _, g in itertools.groupby(tokens)), default=0)


def longest_phrase_run(text: str) -> tuple[int, int]:
    """Longest run of one repeated substring: (repeat count, unit length)."""
    stripped = strip_punctuation(text)
    best, best_len = 1, 1
    for length in range(2, len(stripped) // 2 + 1):
        for start in range(len(stripped) - 2 * length + 1):
            unit = stripped[start : start + length]
            count = 1
            while stripped[start + count * length : start + (count + 1) * length] == unit:
                count += 1
            if count > best:
                best, best_len = count, length
    return best, best_len


def is_repetition_bubble(source_ja: str, reference_en: str) -> bool:
    char_run = longest_char_run(source_ja)
    phrase_run, unit_len = longest_phrase_run(source_ja)
    if max(char_run, phrase_run, longest_word_run(reference_en)) < MIN_RUN:
        return False
    span = max(char_run, phrase_run * unit_len)
    length = len(strip_punctuation(source_ja))
    return bool(length) and span / length > MIN_DOMINANCE


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
