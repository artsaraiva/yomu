# A detection counts as a hit when its crop contains the whole text, not when its box overlaps

The bubble-detection eval judges a detection by whether the region it hands to `OcrEngine` contains every glyph of the ground-truth text region. A ground-truth box is a hit when a single detection covers at least 95% of its area, and that detection covers no other ground-truth box's centre. Box overlap (IoU) is dropped as a gate: it penalises a detection for being generous, and a generous crop is harmless to this pipeline right up to the point where it swallows a neighbouring bubble — which is a separate check, made separately.

Alongside the gate, the harness reports **localisation recall** — the fraction of ground-truth boxes whose centre falls inside some detection — which is not gated. Two numbers are needed because one cannot answer the question #33 asks. IoU@0.5 scored the incumbent at 35.9% while it was in fact locating 96.2% of the text, and "the detector is blind" and "the detector is badly framed" point in opposite directions: the first says replace it, the second says fix post-processing.

## Considered Options

- **Keep IoU@0.5.** Rejected. It conflates missing the text with drawing a tighter box. #36 measured detection centres a median 1px from ground-truth centres while boxes disagreed in extent, so the 35.9% was scoring convention agreement, not detection.
- **Lower the IoU threshold to 0.3.** Rejected. It lifts the incumbent to 71.8% and reads better, but it is the same metric with a looser bar: still symmetric, still penalising generous boxes, and still unable to say whether a crop truncates text. It changes the number without changing what the number means.
- **Centroid containment alone.** Rejected as the gate, kept as the reported second number. It scores the incumbent at 85.9%, but a detection whose centre-hit crop clips half the sentence passes it. It answers "did we find it", which is worth knowing and is not what the gate is for.
- **Re-annotate the case set to the detector's box convention.** Rejected, and this is the option worth being most careful about. The OpenMantra annotation is tight to the glyphs — verified by rendering ground truth and detections over the pages — so re-annotating to the boxes the incumbent already draws would raise every score without anything improving, and would make the gate structurally unable to see text truncation, the one failure that costs OCR characters.

## Consequences

**The gate is containment recall.** For each ground-truth box, the fraction of its area covered by the matched detection must be ≥ 0.95, under one-to-one matching. Not 1.00: the annotation is hand-drawn and a one-pixel slip would fail a crop holding every glyph. Not 0.90: at a median 80×110px box, 10% of the area is a plausible partial character.

**A detection that covers two ground-truth boxes' centres is a hit for neither.** A crop spanning two bubbles hands OCR two fused sentences, and ADR-0002 addresses bubbles by detector id — so a merged box means one id carries two speakers' lines while the other id never appears at all. Scoring it as a hit for one of the two would let a detector trade merges for recall, which is the trade that damages reading most. This costs the incumbent 84.6% → **71.8%**, and that gap is the measured price of the defect.

**Crops are padded before they reach OCR, by 4% of page width on every side.** `TranslationPipeline` currently crops `bubble.boundingBox` raw; `CropBoundsCalculator.clamp` only clamps to the bitmap edges. The incumbent's boxes sit inset inside the text region by a median 15px left, 14px right, 10px top, 9px bottom on 830px-wide pages — a roughly constant pixel inset, not a scale factor, which is why #36's multiplicative 1.6×/1.15× dilation plateaued early. The pad is applied in `CropBoundsCalculator` before clamping, not in `BubbleDetector`: `boundingBox` is also the overlay's draw position and the input to `ContextAssembler`'s panel-gap heuristic, so dilating it would draw fat overlay boxes and shrink the gaps that define panels.

**The 4% constant is fit on the same eight cases it is scored against.** With n=78 boxes it is a working constant, not a measured property of the detector, and it is expressed as a fraction of page width because eval pages are 830×1170 while device captures are not. A detector whose boxes do not need the pad is the better outcome; this makes the incumbent usable meanwhile.

**Padding does not cause merges.** Detections containing two or more ground-truth centres number 10 of 72 at zero pad and are still 10 at 6% of page width, rising only at 10%. The merge defect is in the detector's raw output — #33 inherits it as a real cost of the incumbent, and it is a defect IoU@0.5 was also hiding.

**`Bubble.textRegion` is deleted.** It is assigned the identical `RectF` as `boundingBox` and never read. The padded crop does not go there — crop bounds already exist as `CropBounds`, and a second home for them invites the two drifting apart.

**The incumbent's recorded baseline, for #33 to rank against:** containment recall **0.718**, localisation recall **0.859**, 10 merging detections, on 78 boxes across 8 cases. The breakdown at a 4% pad is 66/78 cleanly cropped, 9/78 found but merged with a neighbour, 3/78 genuinely not found.

**No absolute pass bar is set here.** Two of the three unfound boxes are wide horizontal title and credit text, unenclosed rather than balloons and outside what a balloon detector is trained for. A bar near 1.0 would therefore be unreachable by any detector of this class, and setting one would silently decide whether Yomu translates title text. That is a product question, and #33 should answer it holding a real candidate rather than have this ADR guess it in advance.

**Unenclosed text stays in the denominator.** Excluding those boxes would make the same decision by omission. They are kept and flagged so #33 can see which candidates handle text outside a balloon.
