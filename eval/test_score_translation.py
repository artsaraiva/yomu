"""Checks for the ADR-0004 translation scorer: id-keyed matching + the four #52 metrics."""

from run_eval_lib import has_cjk, is_non_translation, score_translation


def test_clean_page_passes_every_gate():
    s = score_translation(["こんにちは", "バカ"], ["Hello", "Idiot"], ["Hello", "Idiot"])
    assert s["bubble_coverage"] == 1.0
    assert s["non_translation_rate"] == 0.0
    assert s["japanese_residue_rate"] == 0.0
    assert s["readability_ratio"] == 1.0


def test_japanese_residue_is_caught():
    s = score_translation(["a", "バカ"], ["Hello", "Idiot"], ["Hello", "バカ"])
    assert s["residue"] == 1
    assert s["japanese_residue_rate"] == 0.5


def test_residue_is_reference_adjudicated():
    # SFX kept verbatim: the reference also carries the CJK, so the output is not charged.
    s = score_translation(["ドン"], ["ドン"], ["ドン"])
    assert s["residue"] == 0
    assert s["japanese_residue_rate"] == 0.0


def test_instruction_echo_is_non_translation():
    s = score_translation(
        ["a", "b"],
        ["Hello", "Idiot"],
        ["Translate the following Jpn manga text into natural English.", "Idiot"],
    )
    assert s["non_translation"] == 1
    assert s["non_translation_rate"] == 0.5


def test_missing_id_scores_zero_coverage_not_a_void():
    # A short output list is the "returned fewer entries than bubbles" failure, not a discard.
    s = score_translation(["a", "b"], ["Hello", "Idiot"], ["Hello"])
    assert s["entries"] == 2
    assert s["covered"] == 1
    assert s["bubble_coverage"] == 0.5
    assert "error" not in s


def test_blank_source_line_leaves_every_denominator():
    s = score_translation(["Hi", ""], ["Hello", ""], ["Hello", ""])
    assert s["entries"] == 1
    assert s["bubble_coverage"] == 1.0


def test_helpers():
    assert has_cjk("バカ") and has_cjk("東京") and not has_cjk("Tokyo")
    assert is_non_translation("I cannot translate this") and not is_non_translation("Hello there")


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print(f"ok {name}")
    print("all passed")
