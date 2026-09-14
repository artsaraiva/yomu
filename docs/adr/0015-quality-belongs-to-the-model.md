# Quality belongs to the model; Yomu tests speed and plumbing

**Status:** accepted, 2026-09-15. Decides [#228](https://github.com/artsaraiva/yomu/issues/228). Supersedes [ADR-0003](0003-detection-hit-criterion.md), [ADR-0004](0004-translation-eval-contract.md) and its amendments, [ADR-0005](0005-ocr-eval-contract.md) and [ADR-0006](0006-coherence-gate-contract.md). Supersedes in part [ADR-0013](0013-grammar-constrained-page-level-batch.md).

Yomu does not measure translation, OCR or detection quality. How well a model reads or translates manga is a property of the model, and it is judged when the model is chosen, by a person looking at output — not by a harness in this repository. What Yomu tests is its own code: that pages are cropped, assembled, prompted, parsed, fallen back and rendered correctly, and how fast that happens on a real phone.

- **Unit tests in CI cover plumbing only.** No test runs a model and scores its output. A test may use a fake slot or a fixed model reply to pin parsing, fallback and assembly behaviour.
- **On-device speed benchmarks are report-only.** They print latency and memory for the shipped path. Nothing gates on them, and no decision reverts automatically on a number.
- **Choosing a model is a short ADR.** It weighs licence, download and RAM size, the speed benchmark's numbers, and a manual look at output on the fixture pages. It records what was looked at and why the model won; it does not report a quality score.

## Why

The eval grew into the largest subsystem in the repository and still could not rank engines on the thing that matters. A 17-page corpus has no statistical power for a continuous quality metric; the deterministic gates (Japanese residue, non-translation) were reinterpreted by adjudication in almost every ADR that met them; and each adjudication needed quoted hit texts that repeatedly did not survive the run. The gate's verdicts were decided by reading output, so the reading was the real instrument. This decision keeps the reading and drops the scaffolding around it.

Two gates were never built at all — the OCR eval (ADR-0005) and the coherence gate (ADR-0006) — and a third, the detection criterion (ADR-0003), last decided anything when ADR-0007 held the incumbent detector.

## Considered Options

- **Keep the eval, trim it to the translation gate.** Rejected. The translation gate is the part that needed adjudication every time it ran, so trimming to it keeps the cost and the ambiguity.
- **Replace the metrics with an LLM judge or COMET.** Rejected. It adds a model, a host dependency and a new calibration question to answer the same question a person answers faster on a handful of pages.
- **Stop testing quality; test plumbing and speed. (Chosen.)**

## Consequences

**Superseded ADRs stay in place.** ADR-0003, 0004, 0005 and 0006 carry a status line pointing here and are otherwise unedited; they record why the eval was shaped as it was.

**ADR-0013's architecture stands; its measurement clauses do not.** The grammar-constrained page-level batch call, the mandatory grammar, the per-line fallback and the withdrawal of session context all stand. Its latency-falsification rule, its residue-gap adjudication, and the adjudication in its #203 and #214 amendments are superseded: none of them is a condition the shipped architecture still has to meet.

**ADR-0010's decision stands.** Qwen2.5-1.5B-Instruct stays the curated default. Its bake-off numbers are historical evidence for that choice, not a bar a future model must beat.

**Runtime guards are behaviour, not eval.** `deadTranslationReason` and the non-translation patterns stay because they decide what the app renders. Their scorer twins in `eval/` go with the eval code, and after that nothing needs keeping in sync.

**The eval code is deleted separately.** This ADR changes docs and comments only; removing `eval/` and its instrumentation is its own ticket under #228.
