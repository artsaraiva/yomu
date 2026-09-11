"""Checks for the #152 repetition-probe harm scorer."""

from run_eval_lib import longest_repeat_run, score_repetition_probe


def test_run_counts_words_and_characters():
    assert longest_repeat_run("ha ha ha ha ha!") == 5
    assert longest_repeat_run("aaaaaahh!") == 6
    assert longest_repeat_run("はははははは") == 6
    # Repeated punctuation is not repetition.
    assert longest_repeat_run("......") == 0


def test_collapsed_repetition_is_harm():
    # A repetition penalty ate the laugh: reference keeps nine, output keeps two.
    s = score_repetition_probe(["ha ha ha ha ha ha ha ha ha!"], ["ha ha"])
    assert s["harm"] == 1
    assert s["scored"] == 1
    assert s["bubbles"][0] == {
        "id": 0,
        "reference_run": 9,
        "output_run": 2,
        "scored": True,
        "harm": True,
    }


def test_preserved_repetition_is_not_harm():
    s = score_repetition_probe(["ha ha ha ha!", "aaaaaahh!"], ["ha ha ha ha ha!", "aaaaaaahh!"])
    assert s["harm"] == 0
    assert s["scored"] == 2


def test_short_reference_run_is_not_scored():
    # Nothing for a penalty to eat, so a short output run is not evidence.
    s = score_repetition_probe(["ha ha"], ["ha"])
    assert s["scored"] == 0
    assert s["harm"] == 0


def test_missing_output_is_harm_not_a_void():
    s = score_repetition_probe(["ha ha ha!"], [])
    assert s["entries"] == 1
    assert s["harm"] == 1
