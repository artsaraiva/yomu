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


def test_clarification_requests_are_not_translations():
    assert is_non_translation("Could you please provide the target Japanese manga text?")
    assert is_non_translation("The English translation of the given Japanese text is: Hello")
    assert is_non_translation('The Japanese text "こんにちは" translates to "Hello" in English.')
    assert not is_non_translation("Please provide the sword tomorrow.")


def test_translation_introduction_is_not_dialogue():
    assert is_non_translation("Here's the English translation: Hello")
    assert not is_non_translation("Here is the sword you wanted.")


def test_punctuation_target_clarification_is_a_non_translation():
    # #120: a lone "?" bubble drew "please provide the text" replies from Qwen. The engine now
    # passes such targets through, and a clarification that still reaches the scorer fails the gate.
    s = score_translation(
        ["\uff1f", "\u3042\u308a\u304c\u3068\u3046"],
        ["?", "Thank you."],
        ["Please provide the Japanese manga text you want translated.", "Thank you."],
    )
    assert s["non_translation"] == 1


def test_punctuation_target_passed_through_is_clean():
    s = score_translation(["\uff1f", "\u2026\u2026"], ["?", "..."], ["\uff1f", "\u2026\u2026"])
    assert s["non_translation"] == 0
    assert s["residue"] == 0
    assert s["bubble_coverage"] == 1.0


def test_unrun_pages_cannot_pass():
    import json
    import tempfile
    from pathlib import Path
    import run_eval_lib as lib
    with tempfile.TemporaryDirectory() as tmp:
        previous = lib.TRANS_CASES
        lib.TRANS_CASES = Path(tmp)
        try:
            for name in ("one", "two"):
                case = Path(tmp) / name
                case.mkdir()
                (case / "source.txt").write_text("ありがとう\n")
                (case / "reference.txt").write_text("Thank you.\n")
            actual = Path(tmp) / "one" / "actual"
            actual.mkdir()
            (actual / "qwen.json").write_text(json.dumps({"translations": ["Thank you."]}))
            summary = lib.run_translation_quality(False)["summary"]["engines"]["qwen"]
            assert not summary["gate_pass"]
            assert summary["completed_cases"] == 1
            assert summary["expected_cases"] == 2
        finally:
            lib.TRANS_CASES = previous


def test_reference_similarity_rewards_correct_translation():
    good = score_translation(["ありがとう"], ["Thank you."], ["Thank you."])
    bad = score_translation(["ありがとう"], ["Thank you."], ["Blue chairs fly."])
    assert good["mean_chrf"] == 100.0
    assert bad["mean_chrf"] < good["mean_chrf"]


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print(f"ok {name}")
    print("all passed")
