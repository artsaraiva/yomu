# Translation quality case schema

Each case is a directory: `cases/<case-id>/`. Cases are generated from OpenMantra by
`generate-cases.py`; the boxes live in the parallel `bubble-detection/cases/<case-id>/expected.json`
under the same case id.

The eval feeds engines **annotation Japanese** (`text_ja`), not OCR output, assembles it into a page
through the real `ContextAssembler`, and scores a single **page-level call** whose output is matched
to the reference **by bubble id** (ADR-0004). Cascade quality — how much OCR error costs the final
translation — is deliberately unmeasured here; that ranking would otherwise be a function of the OCR
model #34 has not picked.

## Required files

### `source.txt`
Japanese `text_ja` annotation, one bubble per line. **Bubble id `n` is line `n`** (0-based), the same
order as the boxes in `expected.json`. A blank line is a bubble with no text; it carries no score.

### `reference.txt`
Acceptable English translation, one per line, aligned to `source.txt` by line index. Used to
adjudicate Japanese residue: a CJK codepoint in the output is only residue where the reference for
that id has none (so a legitimately-kept onomatopoeia is not charged).

## Optional files

### `page.jpg`
Source manga page, for visual context.

## How scoring works

The on-device `EngineBenchmarkTest` builds bubbles from the boxes + `text_ja`, calls
`ContextAssembler.assemble(...)` then `TranslationEngine.translate(blocks)` **once per page**, and
writes a `context_assembly` and a `translation` record per arm/case into the run directory — see
[`../SCHEMA.md`](../SCHEMA.md).

A translation record carries `requested_ids` and the **raw** `{bubble_id, text}` results, *before*
`TranslationEngine` substitutes source text for an id the model never answered. The scorer projects
them onto the source lines: a requested id with no result becomes `""` and fails coverage, and a
non-requested id (a punctuation-only bubble the engine keeps verbatim) is filled with its source,
which is what production renders. Scoring the post-substitution list is how #137 shipped a page
reporting 100% coverage that the model never answered.

There is no `actual/<engine>.json` any more. Sharing output files across runs is how #58 shipped.

## Metrics and pass bars (ADR-0004 + #52)

The set **catches failure modes and gates regressions; it does not rank translation quality** — there
is no continuous quality metric, so no separation rule. Adequacy/fluency ranking is deferred to #30's
contrastive set and a possible future COMET-or-judge metric.

| Metric | Bar |
| --- | --- |
| Non-translation rate — output echoes the instruction or a refusal template | **gate: 0** |
| Japanese-residue rate — output has a CJK codepoint the reference lacks | **gate: 0** |
| Bubble coverage — fraction of ids the engine returned | **gate: 100%** |
| Output shape — no missing, extra, or duplicate returned id | **gate: pass** |
| Readability ratio — output words / reference words | diagnostic, no bar |
| Mean bubble chrF2 — lexical overlap | **explicitly ungated** diagnostic |
| Semantic accuracy — wrong names, flipped subjects, dropped negations | not scored here |

Every bar in this table scores the form of the output. Semantic errors pass all of them; they are
reviewed by hand and recorded separately in `eval/semantic-review-120.md`, and a gate PASS says the
output is shaped like a translation, not that it says what the source said.

The **LLM** page-level call is the gate. **ML Kit** and **OPUS-MT** cannot take a page-level call, so
they translate per bubble inside the same call and are reported on a **floor**, never ranked against
the gate. #35 selects among gate-passing engines on #26's axes (RAM / latency / size / licence).

The first page-level numbers are a **pre-ADR-0002 baseline**: `sessionContext` is still unread and
`translateBatch` still prompts a bare numbered list (#47), so the ranking survives but the absolute
number is not ADR-0002's.
