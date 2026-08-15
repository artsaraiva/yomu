# The OCR eval scores whole-box readings on padded ground-truth crops, and its first job is measuring the incumbent

The OCR half of the eval crops each OpenMantra text box out of the page — padded by 4% of page width, per ADR-0003 — hands it to `OcrEngine`, and asks whether the string that comes back equals the annotated `text_ja`. The gate is **box exact match**; character error rate is reported beside it as a diagnostic. The detector is not in the loop, and is not stubbed: ground-truth boxes stand in for detections, exactly as ADR-0004 has `text_ja` stand in for OCR output on the translation side.

Before this there was no OCR half at all (#41): `eval/` had only `bubble-detection` and `translation-quality` case types, and `OcrEngine` was referenced nowhere under `eval/` or `app/src/androidTest/`. #34 has to choose between manga-ocr and manga-ocr-mobile and had no metric to choose on — and, per #28, no published accuracy figure exists for the manga-ocr the app already ships. The number this harness produces first is not a comparison; it is the first evidence that the shipping OCR reads manga at all.

## Considered Options

**No OCR metric — decide #34 on size, latency and downstream translation quality.** Rejected. Building it is unusually cheap here: the boxes and the reference text are the same annotation entries `generate-cases.py` already reads, the engine already loads and runs on device through `ModelManager`, and the harness already has a case-generation and scoring shape to copy. There is no annotation work and no provisioning work, which is not true of any other measurement this map has added.

**Feed OCR the detector's own boxes.** Rejected. ADR-0003 measured the incumbent detector at 0.724 containment, so roughly a quarter of the annotated text is outside the crops it produces — OCR would be charged for characters that were never in the image it was given. Crop quality is already scored, by containment recall, and scoring it a second time through a character metric couples the OCR number to #33's outcome for no new information.

**Gate on CER rather than exact match.** Rejected as the gate, kept as the diagnostic. The median annotated line is 8 characters and the 90th percentile is 18, so a single wrong kana is a large CER on one box and a small one on another, and neither reflects what the mistake costs: the line goes to the translator wrong either way. Exact match asks the question the pipeline cares about. CER answers "how far off" once a box has failed.

**Macro-average the CER.** Rejected. It lets a 2-character box swing as hard as a 30-character one. Micro-averaging — total edit distance over total reference characters — weights by text volume, and exact match is already carrying the per-box view.

**Report per-class scores for vertical text, furigana and SFX.** Rejected, on the same grounds #44 deleted the detection set's class rows: OpenMantra has no class field, and any labels would be fabricated by heuristic. Vertical is not a subset to break out in any case — 97.6% of the 1592 annotated boxes are taller than wide, so it is the corpus.

**Set an absolute pass bar from manga-ocr-mobile's published figures** (~7.4% CER, ~73% exact match on Manga109-s, per #28). Rejected. Different corpus, different crop convention, different normalization. Those numbers cannot be compared to a number measured here, and a bar built from them would look grounded while being arbitrary.

**Fix the two `OcrEngine` integration defects before measuring.** Rejected — measure first, with samples. See the consequences below.

**Charge the decoder's injected spaces as character errors.** Rejected. `decodeTokens` puts a space before every non-`##` token, so nearly every output would differ from a reference that contains no spaces at all, and the incumbent would score near zero for a detokenizer artefact rather than a reading failure.

**Give OCR its own `eval/ocr-accuracy/` case type.** Rejected. It would put a third copy of every page image in the tree. The box and the text inside it come from one annotation entry and belong in one file.

## Consequences

**Cases carry text on the box.** `generate-cases.py` writes each box's `text_ja` into `bubble-detection/cases/*/expected.json` alongside `x/y/w/h`, and the OCR scorer reads that directory. The alternative — boxes from `bubble-detection`, text from `translation-quality/source.txt` — would make an OCR score depend on two directories staying index-aligned, and this map has been burned repeatedly by couplings that hold silently until they do not.

**Crops are padded by 4% of page width, per ADR-0003.** The annotation is tight to the glyphs (verified visually in #38), and a tight crop clips edge strokes. More to the point, a padded crop is what `CropBoundsCalculator` will hand OCR once ADR-0003's build work lands, so a raw-box score would measure a crop the pipeline never produces.

**Both sides are normalized: NFKC, then all whitespace stripped.** This is part of the metric, stated in the schema, and it exists because of the defect below rather than because whitespace is uninteresting.

**Two known `OcrEngine` defects are measured, not pre-fixed, and the report must print raw samples.** `MAX_LENGTH = 32` caps generation while the longest annotated line is 36 characters, and `decodeTokens` (`OcrEngine.kt:223`) inserts spaces into Japanese. Normalization hides the spacing from the score, which means an aggregate number cannot reveal it — so the report prints raw output beside reference for a sample of boxes and for the worst N failures. This map has twice found a harness passing green while measuring nothing (#36's stubbed detector, #41's silently discarded translation cases); an output reading `こ っ ち に ゃ` is invisible in a normalized CER and obvious in three printed lines. Both defects are build work on the app path, where the injected spaces reach the translator.

**Failure modes are shown, not classified.** In place of fabricated class rows, the report dumps the N worst boxes verbatim — crop, reference, output. That exposes what OCR is failing on without inventing annotation.

**A blank-crop probe is reported, never gated.** #28 records that manga-ocr hallucinates on blank input, and #44 measured 26 false-positive detections on the story set — each of which becomes a blank crop in production, a hallucinated line, and a garbage entry addressed to a bubble id under ADR-0002. Scoring annotated boxes alone cannot see this, because every such crop contains text by construction. The harness samples crops from page regions with no annotated text and reports the fraction producing non-empty output. It is the only place in the eval where this failure is visible at all.

**Cover pages are scored and reported separately, never gated** — the #44 convention. The gate denominator is every annotated box on story pages, in one bucket.

**No absolute pass bar is set here.** There is no external anchor for "good enough" on this corpus, and inventing one before the first measurement is guesswork dressed as a criterion. The first measured value becomes the regression baseline, which is the treatment detection got.

**#34's separation rule: paired, on identical boxes, and the incumbent wins ties.** Both models read the same crops, and the comparison is a sign or McNemar test over the discordant boxes only — #44's power problem was largely an unpaired-comparison problem, and pairing resolves far smaller gaps on the same corpus. manga-ocr-mobile is preferred only if it is not statistically worse (p ≥ 0.05); "not worse" is the bar rather than #33's symmetric ≥8pp, because the incumbent is already integrated and running.

**Correcting #28 on footprint: the ONNX export is ~141MB, not ~400MB.** `l0wgear/manga-ocr-2025-onnx` serves `encoder_model.onnx` at 22.4MB and `decoder_model.onnx` at 118.1MB. The ~400MB figure in #28 is the PyTorch original, not what `ModelManager` downloads. Against manga-ocr-mobile's ~10M parameters the prize is roughly 110MB — and it is bought with a second inference runtime (TFLite beside ONNX Runtime) that is permanent, not a one-off. That is why the incumbent wins ties.

**Ranking requires a manga-ocr-mobile path that does not exist, and that cost may decide #34 by itself.** It is filed as its own ticket blocking #34, not folded in here. If it is judged too expensive, this harness still measures and gates the incumbent, the separation rule above never fires, and #34 decides on the incumbent's measured accuracy, published third-party figures, and footprint.

**Cascade quality stays unmeasured.** ADR-0004 left "how much does OCR error cost the final translation" open and hanging on this decision; it stays open. It is only answerable once #34 has picked a model, and a high exact-match rate would make it close to moot.

**One instrumented run.** `OcrEngine` is ONNX Runtime on Android, so this is a device run like the detection half, emitting detection, OCR and translation artifacts together — no separate session, no second model download.
