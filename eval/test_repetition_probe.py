"""Checks for the #152 repetition probe: the selection rule and the harm scorer."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import importlib

from run_eval_lib import longest_repeat_run, repeat_dominance, score_repetition_probe

generate = importlib.import_module("generate-repetition-probe")


def test_run_counts_words_characters_and_phrases():
    assert longest_repeat_run("ha ha ha ha ha!") == 5
    assert longest_repeat_run("aaaaaahh!") == 6
    assert longest_repeat_run("はははははは") == 6
    # A repeated unit can be a phrase, in either language.
    assert longest_repeat_run("what a waste, what a waste, what a waste...") == 3
    assert longest_repeat_run("もったいないもったいないもったいない") == 3
    # Repeated punctuation is not repetition.
    assert longest_repeat_run("......") == 0


def test_punctuation_splits_a_run_rather_than_being_deleted():
    # Deleting the comma would splice えええ into a run of three the bubble does not contain.
    assert longest_repeat_run("え、ええと...") == 2


def test_dominance_separates_a_repetition_bubble_from_a_sentence_containing_one():
    assert repeat_dominance("ははは") == 1.0
    assert repeat_dominance("あああっあいかわしゃん") < generate.MIN_DOMINANCE


def test_selection_takes_repetition_bubbles_from_either_side():
    assert generate.is_repetition_bubble("はははははは", "ha ha ha ha ha ha!")
    # Source carries no run; the human reference draws the scream out anyway.
    assert generate.is_repetition_bubble("おあーー!!", "aaaahhhh!!")
    assert not generate.is_repetition_bubble("なんだこりゃあ...", "what the...")


def test_collapsed_repetition_is_harm():
    # A repetition penalty ate the laugh: reference keeps nine, output keeps two.
    s = score_repetition_probe(["ha ha ha ha ha ha ha ha ha!"], ["ha ha"])
    assert s["harm"] == 1
    assert s["scored_bubbles"] == 1
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
    assert s["scored_bubbles"] == 2


def test_respaced_repetition_is_not_harm():
    # Same laugh, no spaces: counting characters as well as words keeps this off the harm count.
    s = score_repetition_probe(["ha ha ha"], ["hahahaha"])
    assert s["harm"] == 0


def test_short_reference_run_is_not_scored():
    # Nothing for a penalty to eat, so a short output run is not evidence.
    s = score_repetition_probe(["ha ha"], ["ha"])
    assert s["scored_bubbles"] == 0
    assert s["harm"] == 0


def test_missing_output_is_harm_not_a_void():
    s = score_repetition_probe(["ha ha ha!"], [])
    assert s["entries"] == 1
    assert s["harm"] == 1
