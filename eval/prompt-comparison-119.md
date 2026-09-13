# Qwen prompt comparison — 2026-09-09

Decision: retain translation-only prompting as the default. Keep current-capture context
experimental and off by default. This is a formatting improvement, not proof that translation
quality is adequate or that fine-tuning is unnecessary.

> **Superseded in part by [#193](https://github.com/artsaraiva/yomu/issues/193) (2026-09-13), deciding [#144](https://github.com/artsaraiva/yomu/issues/144).** "Keep current-capture context experimental" no longer holds: this comparison measured it worse and slower than translation-only, and the page-level batch call ([ADR-0013](../docs/adr/0013-grammar-constrained-page-level-batch.md)) made it redundant, so the mode and its Settings switch are deleted. The prompt benchmark now runs two arms. The translation-only decision and the measurements below stand.

## Reproduction

User-run artifact: `eval/results/prompt-20260909-092531/` (local, generated).
17 OpenMantra pages, 147 nonblank entries, three prompt modes, one run per mode in fixed order.
Android arm64 emulator `sdk_gphone16k_arm64`, Android release 17. Runtime: 200.877 seconds;
instrumentation completed successfully. Timings are emulator measurements, not phone claims.
Qwen GGUF SHA-256: `1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370`.
Source baseline: `3f033e943e59cc8819258934207a359fb238d1e2`, with the instrumentation/runner diff
stored in the artifact. Scorer: sacrebleu 2.5.1; reviewed clarification detection added afterward.
No new inference was performed for rescoring.

## Reviewed results

| Prompt | Mean bubble chrF2 | Japanese residue | Non-translation | Median ms/page | Max sampled PSS KiB |
|---|---:|---:|---:|---:|---:|
| Old model-card | 23.565 | 33/147 | 12/147 | 3365 | 2121924 |
| Translation-only | 26.088 | 0/147 | 1/147 | 3104 | 2126500 |
| Current capture | 26.083 | 5/147 | 0/147 | 4525 | 2131748 |

All modes completed 17/17 pages with nonempty output for every entry. Coverage includes source
fallbacks. PSS is sampled after each page, not continuous peak memory. No mode passes the reviewed
output gate. The original translation-only PASS was a scorer blind spot: a request for more
Japanese text on a punctuation-only target was not recognized as non-translation.

## Failure review

Capture-context residue, zero-based bubble IDs:

- `balloon-p28`, IDs 2 and 3: complete source fallbacks.
- `bourei-p04`, ID 5: partly translated name/occupation caption.
- `rasetugari-p19`, ID 5: partly transliterated name.
- `tencho-p21`, ID 0: complete source fallback.

A spot-check of the two balloon dialogue pages shows remaining meaning errors in both new
modes: incorrect personal names, switched speaker/subject, a price explanation changed to a
claim of perfection, and loss of the film-winding/shutter details. Context also turns a bare
question mark into invented dialogue. This review is diagnostic, not a bilingual human rating.

The old prompt produces explanatory wrappers in raw output. The new prompt substantially reduces
verbosity, but one rendered clarification remains. Runtime fallback guards and scoring are
conservative heuristics, not guarantees. Follow-up: #120.

## Validation and limits

Translation-engine, ML, and app unit suites passed; the native prompt-budget executable passed;
the app and instrumentation APKs built. The user completed the real three-mode emulator run.
The settings toggle was visually inspected, persisted when enabled, and restored off.

This benchmark uses ground-truth Japanese and boxes, bypassing detection/OCR. It does not establish
full overlay correctness, semantic accuracy, thermal stability, or physical-phone performance.
The same fixed corpus was retained to isolate prompting. Expand independent held-out data before
making a model-selection or training decision.
