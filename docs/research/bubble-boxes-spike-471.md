# Bubble boxes: what the detector's `bubble` class could buy (spike prep for #471)

Desk research and a desktop check, 2026-11-25, to prepare spike [#471](https://github.com/artsaraiva/yomu/issues/471). It collects what is known before any code is written: what the `bubble` class covers, what Yomu does with detection output today, how `StaticLayout.Builder.setIndents` behaves, and how four open-source translators use balloon shapes. It ends with a measurement plan for the issue's acceptance criteria.

**What was run, and on what.**

- **Nothing ran on a phone or an emulator.** The numbers in [§2](#2-desktop-check-on-the-fixture-pages) come from a desktop replica: Python, onnxruntime 1.32.0, the app's pinned `scripts/speed-fixtures/models/vision/rtdetr_v4s_int8.onnx`, a plain stretch to 640×640 and `orig_target_sizes = [w, h]` as ADR-0020 prescribes, the reader thresholds (0.25, with a 0.5 floor for `text_free`) and Yomu's NMS (IoU 0.80, containment 0.90). It ran on the five pages in `scripts/speed-fixtures/pages/` at their file resolution (830×1170 and 1896×1380), not on the 1280×2856 emulator captures the app sees. A numpy port of `findBubbleInterior` and the #88 reach rule gave the flood-fill column. The scripts were scratch work and are not in the repo. The method is described in §2 so the spike can redo it.
- **Yomu code is cited at `main` `c31b2d1`.** Other repositories are cited at the commit that was cloned on 2026-11-25.
- **Earlier findings are linked, not repeated:** [2d-text-structure.md §4](2d-text-structure.md#4-asides) (asides and the satellite/`text_free` touch rules on OpenMantra), [jev-bubble-class-hints.md](jev-bubble-class-hints.md) (why no class mark reaches the prompt), [tech-scan-2026-11.md §2 and §6](tech-scan-2026-11.md) (where #471 came from, Fumeto's self-description, balloon-mask models) and [ADR-0020](../adr/0020-rtdetr-text-detector.md) (the detector, its thresholds and the open question).

## Summary

- **The `bubble` box is the whole balloon, outline included, and it also covers rectangular caption boxes.** The model card defines nothing beyond the names `bubble`, `text_bubble (text inside bubbles)` and `text_free (text outside bubbles)`. Per-class accuracy, the training images and the annotation guide are not published. On the five fixture pages every one of the 66 `text_bubble` boxes sat entirely inside exactly one `bubble` box, with one bubble per text box, no empty bubbles, and the bubble box about 2× the text box's area (1.4–4.4×).
- **Today's render box rarely adds room.** In the desktop replica the flood fill returned no interior for 4 of 70 kept text boxes and was overruled by the #88 reach rule for 13. For 28 more it found an interior whose 0.707 rectangle was no bigger than the glyph box, so the glyph box was kept. Only 25 of 70 render boxes came out larger than the detector box. The cause is geometric: the largest rectangle inside an ellipse covers 50% of its bounding box, and the Japanese glyph box already covers about half the balloon.
- **The bubble boxes must stay out of the NMS that text boxes go through.** Each text box sits ≥ 0.90 inside its bubble box, so `nonMaxSuppressDetections`'s containment rule would suppress one of the pair, and its #370 swap step would put the bubble box in place of the text box.

### Preliminary take per use

| Use | What the desk check says | Take before the spike |
|---|---|---|
| **1. Fallback render box** | 3 of the 4 no-interior boxes have a host bubble box. Of the 13 reach cases, 11 have a host bubble box that itself overlaps a neighbour's text box (conjoined or touching balloons), so it would fail the same #88 test. | **Small gain as a pure fallback.** The more promising variant bounds the flood fill with the bubble box (window = bubble box, a leak = touching its edge) and falls back to an ellipse inscribed in it. That is what the detector's author does in comic-translate ([§4](#4-prior-art-in-source-code)). Worth one spike arm. |
| **2. Grouping and order** | No bubble box holds two kept text boxes on any fixture page. Two of five `text_free` boxes lie 4–5 px from a bubble. | **Little to gain on the fixtures.** The detector already boxes a balloon's text as one block, so "text boxes in one bubble = one utterance" has nothing to merge here. Aside order is the only open part, and #428 found it rare (4 of 73 `text_free` boxes on OpenMantra). Measure it on #428's aside pages, not just the fixtures. |
| **3. Shaped lines** | The inscribed rectangle is the bottleneck. An ellipse covers 78.5% of its bounding box against the rectangle's 50%. | **The use most likely to pay.** Prototype it first, on the bubble box or the flood-fill interior's bounds. It needs no new API level (minSdk is 26 and `setIndents` is API 23), and Yomu's renderer already centres each line. |

## 1. The detector's `bubble` class

### What the publisher states

- **Card.** "RT-DETR-v2 r50vd model fine-tuned on about 11k Manga, Webtoon, Manhua and Western Comic style Images for text and speech bubble detection." Training size 640, "Training Images were resized, not cropped. Tall Webtoons were split vertically." Classes are `0: bubble`, `1: text_bubble (text inside bubbles)`, `2: text_free (text outside bubbles)`. Licence apache-2.0. That is the entire card ([README.md @16e8a62](https://huggingface.co/ogkalu/comic-text-and-bubble-detector/blob/16e8a622f91fabc6b5b65c96d32d1183f8843546/README.md)).
- **`config.json`:** `RTDetrV2ForObjectDetection`, `id2label {0: bubble, 1: text_bubble, 2: text_free}` ([config.json @16e8a62](https://huggingface.co/ogkalu/comic-text-and-bubble-detector/blob/16e8a622f91fabc6b5b65c96d32d1183f8843546/config.json)).
- **`preprocessor_config.json`:** `do_resize` to 640×640, `do_rescale` 1/255, `do_normalize: false`, `do_pad: false`. It lists ImageNet mean and std, but they are switched off. This matches ADR-0020's "RGB/255, with no mean or std" ([preprocessor_config.json @16e8a62](https://huggingface.co/ogkalu/comic-text-and-bubble-detector/blob/16e8a622f91fabc6b5b65c96d32d1183f8843546/preprocessor_config.json)).
- **Revision.** The Hugging Face API still reports `sha 16e8a62`, last modified 2026-05-15, so the pinned revision is the current one. Files: `detector-v4-s_int8.onnx`, `detector.onnx`, `detector_int8.onnx`, `model.safetensors` (42.9M parameters, which is the r50vd model, not v4-s) ([API](https://huggingface.co/api/models/ogkalu/comic-text-and-bubble-detector), read 2026-11-25).
- **Output format, as Yomu runs it.** Inputs `images` [1,3,640,640] and `orig_target_sizes` int64 [[width, height]]. Outputs `labels`, `boxes` (xyxy, page pixels) and `scores`, 300 per page (`ml/src/main/java/com/yomu/ml/OnnxRuntime.kt:124-153`, ADR-0020 "How it runs"). The model emits duplicates, so NMS stays.

### What is not published

- **No class definition beyond the names.** Nothing says whether `bubble` includes the tail, whether a narration or caption box counts as a bubble, how a conjoined balloon is boxed (one box or one per lobe), or how open and borderless balloons are treated.
- **No accuracy, per class or overall.** The training set and annotation guide are not public (ADR-0020's licence section).
- **The author's GitHub path moved.** `github.com/ogkalu/comic-translate` returns "not found". The app lives at `ogkalu2/comic-translate`, whose README names this model as its "Speech Bubble Detection" step (`README.md:141-143` @[`8977b91`](https://github.com/ogkalu2/comic-translate/tree/8977b91a4f7a40c3917c5a268e9e7d78e1d818da)). That the two accounts belong to one author is *inferred* from the shared name and the README link.

### What the fixture pages show (observed, five pages)

Looked at by eye on boxes drawn over each page:

- **Whole balloon, outline included.** The blue box hugs the outer edge of the outline on round, oval and spiky balloons.
- **Rectangular captions are `bubble` too.** On `bourei-p04`, the square narration box `「視える」人間ってヤツでして` and both name cards (`葬儀依頼人 桜野すずめ 19歳`, `烏丸葬儀社社長…`) are `bubble` + `text_bubble`. The chapter title on a grey gradient is `text_free`.
- **Thought-bubble trails are left out.** On `bourei-p04`, the box around `死んでるんですよねぇ` stops at the oval and does not include its small trailing circles.
- **Short tails appear to be included.** On `tojime-dense-action`, the `あっ` balloon's box reaches the tip of its short tail. This rests on one or two instances, so treat it as *unconfirmed*. None of the fixtures has a long tail.
- **Open balloons cut by a panel border are boxed to their visible part** (`tojime-dense-action` top corners, `お前もしかして`). An ellipse fitted to such a box is the wrong shape.
- **Burst balloons with no closed outline are boxed** (`balloon-dense-dialogue` `こいつ絶対バカだし`, drawn as radiating lines). The flood fill is likely to leak on these, which makes them a natural case for use 1.
- **Conjoined or touching balloons get one box each, and the boxes overlap their neighbours** (`balloon-dense-dialogue` `あっ` against `安かったので…`; several pairs on the two spreads).

## 2. Desktop check on the fixture pages

Method: raw outputs at the reader's thresholds; `text_bubble` ≥ 0.25 and `text_free` ≥ 0.5 through Yomu's NMS; `bubble` ≥ 0.25 through NMS among bubbles only. "Host" means a bubble box holding ≥ 0.90 of a text box's area.

| Page | Bubbles (raw ≥ 0.25 / after NMS) | Text kept (`text_bubble` + `text_free`) | Bubbles holding >1 text box | Bubbles with no text | `text_free` within 10 px of a bubble |
|---|---|---|---|---|---|
| balloon-dense-dialogue | 16 / 16 | 16 + 1 | 0 | 0 | 1 (4 px) |
| bourei-p04 | 7 / 7 | 7 + 1 | 0 | 0 | 0 |
| tojime-dense-action | 17 / 17 | 17 + 0 | 0 | 0 | 0 |
| wide-furigana-spread | 14 / 14 | 14 + 0 | 0 | 0 | 0 |
| wide-title-8col-spread | 12 / 12 | 12 + 2 | 0 | 0 | 1 (5 px) |
| **Total** | **66** | **66 + 4** | **0** | **0** | **2** |

- Every `text_bubble` box had containment 1.00 in its host. Bubble/text area ratio: 1.4–4.4, typically about 2.
- Bubble scores were 0.72–0.96, except one at 0.39 on `wide-furigana-spread`.

**The flood fill today, replicated** (port of `findBubbleInterior` plus the reach rule in `bubbleRenderBoxes`):

| Outcome | Boxes (of 70) | With a host bubble box |
|---|---|---|
| Interior found, render box larger than the glyph box | 25 | 24 |
| Interior found, but its 0.707 rectangle ≤ glyph box, so the glyph box is kept | 28 | 27 |
| No enclosed interior (`findBubbleInterior` returns null) | 4 | 3 (`balloon-dense-dialogue` `あっ` [269,458], `tojime-dense-action` `あ?` [596,539], `wide-furigana-spread` [1492,1320] at the page edge) |
| Grown box reaches another text box (#88), so the glyph box is kept | 13 | 12. For 11 of them the host bubble box also overlaps that neighbour's text box |

The 0.707 rectangle of the *bubble box* was smaller than the glyph box for 29 of 66 hosted boxes (median ratio 1.03). Swapping the flood-fill bounds for the bubble box and keeping the rectangle rule therefore buys little. The shape is what has to change.

## 3. Yomu today (`main` `c31b2d1`)

### Detection and filtering

- `parseRtDetrDetections` keeps class ids 1 and 2 through `RT_DETR_TEXT_CLASSES` and drops class 0. `text_free` gets a 0.5 floor (`ml/src/main/java/com/yomu/ml/OnnxRuntime.kt:222-240`).
- `BubbleDetector.detect` runs `keptDetections`: the confidence filter, then `nonMaxSuppressDetections` with IoU 0.80 and containment 0.90, then the #370 step. In that step, a dropped box that swallows a kept one at ≥ 0.90 replaces it (`pipeline/src/main/java/com/yomu/pipeline/bubble/BubbleDetector.kt:46-73, 82-130`). **That is why bubble boxes cannot simply be added to `RT_DETR_TEXT_CLASSES`.** They need their own list, NMS'd only against each other.
- Each kept detection becomes a `Bubble(id, boundingBox, confidence, textRegion)` whose `textRegion` is a copy of the box (`BubbleDetector.kt:8-13, 64-72`). ADR-0007 lists `Bubble.textRegion` among fields that "looked wired and were not". A bubble box needs a new, named field, not that one.
- Boxes centred in ignored regions are dropped next (`TranslationPipeline.kt:163`, `BubbleDetector.kt:187`).

### Render box (`BubbleRenderBox.kt`)

- `bubbleRenderBoxes` grows every glyph box, then reverts any grown box that intersects another bubble's glyph box (#88) (`pipeline/src/main/java/com/yomu/pipeline/bubble/BubbleRenderBox.kt:31-37`).
- The search window is the glyph box plus a margin of `min(max(w, h), 0.25 × page width)` (`:39-55`).
- "Fillable" means luminance > 235, minus a 2-px halo around every darker pixel, so light screentone (~220) is not fillable (`:9-13, 77-88`).
- Seeds are the fillable pixels in the glyph box ±3 px. A 4-connected BFS labels the regions. Any region touching the window edge counts as leaked. The non-leaked region holding the most seeds wins, and **`findBubbleInterior` returns null when every seeded region leaked or no seed was fillable** (`:62-64, 96-142`). On null the glyph box is kept (`:54`).
- The interior's bounds shrink by `INSCRIBED_FRACTION = 0.707` per side, centred, and are then unioned with the glyph box (`:17-18, 66-73`). So the render box is never smaller than the glyph box, and it is often equal to it ([§2](#2-desktop-check-on-the-fixture-pages)).
- A balloon clipped by the screen edge counts as leaked (`:132-134`).

### Grouping and prompt order

- `ContextAssembler.assemble` cuts the page into tiers and panels at paper gutters, using the *glyph* boxes. Tiers run top to bottom and panels right to left. Inside a panel, rows of vertically overlapping boxes are each read right to left (`pipeline/src/main/java/com/yomu/pipeline/context/ContextAssembler.kt:38-127`). `readingOrder` is the bubble ids in that order (`:129-148`).
- `TranslationEngine.project` turns each block's `readingOrder` into the page's ordered list. Panels are prompt markers, never separate calls (`pipeline/src/main/java/com/yomu/pipeline/translation/TranslationEngine.kt:184-198`; ADR-0002, ADR-0013).
- No class or aside mark reaches the prompt. ADR-0007's "no per-box class label" still holds there (ADR-0020). A grouping change may only change order or merge lines, never add marks ([jev-bubble-class-hints.md](jev-bubble-class-hints.md) found that marks leak into replies).

### Typesetting and drawing

- Render boxes are computed before translation, from the glyph boxes, and each translated bubble is typeset as it arrives (`pipeline/src/main/java/com/yomu/pipeline/TranslationPipeline.kt:222-251`).
- `Typesetter.typeset` takes the render box, applies `PADDING = 0.1` per side (so text fills 0.8 × 0.707 ≈ 0.57 of the interior per side), and binary-searches a font size from 10 to 36. A size fits when greedy-wrapped lines × `fontSize × 1.5` fit the height and the longest word fits the width (`pipeline/src/main/java/com/yomu/pipeline/typesetting/Typesetter.kt:21, 28-38, 49-101`).
- A box narrower than its longest word at 20 px is widened (`:30-32, 60-63`). Overflow past 3× the box height is ellipsized (`:160-181`). `grownBounds` re-centres a box that the text outgrew (`:114-120`).
- **There is no `StaticLayout` anywhere.** Wrapping is a hand-written greedy word wrap over `Paint.measureText`, which falls back to per-character breaks for over-long words (`:122-158`).
- The overlay paints a translucent white `drawRoundRect` over the whole render box (corner radius 0.12 × the short side), clips to it, and draws each line **centred horizontally** with `drawText`, the block centred vertically (`app/src/main/java/com/yomu/app/overlay/TranslationRenderOverlay.kt:186-226`). Symmetric per-line widths therefore need no change to how lines are drawn. The background shape still needs one if the render box grows to the balloon ([§6](#6-risks-and-open-questions)).
- Typesetter tests run under Robolectric 4.18 with no `@GraphicsMode` annotation (`pipeline/src/test/java/com/yomu/pipeline/typesetting/TypesetterTest.kt:12`, `pipeline/build.gradle.kts:24`). Robolectric introduced native graphics in 4.10 as opt-in via `@GraphicsMode(NATIVE)` ([4.10 alpha-1 notes](https://github.com/robolectric/robolectric/discussions/8054)). I could not confirm whether 4.18's default is still LEGACY ("no-ops and fakes", [GraphicsMode.Mode](https://robolectric.org/javadoc/latest/org/robolectric/annotation/GraphicsMode.Mode.html)). Check that before trusting `StaticLayout` line breaks in a unit test.

## 4. Prior art in source code

| Project (commit) | Where the balloon comes from | Shaping | Grouping |
|---|---|---|---|
| **comic-translate**, detector author's app ([`8977b91`](https://github.com/ogkalu2/comic-translate/tree/8977b91a4f7a40c3917c5a268e9e7d78e1d818da)) | This same ogkalu RT-DETR. Class 0 goes to a bubble list; classes 1 and 2 are pooled as text (`modules/detection/rtdetr_v2_onnx.py:91-105`) | Uses the bubble box only for vertical target text: render area = bubble box shrunk to 70% (`modules/rendering/render.py:228-245`, `modules/detection/utils/geometry.py:284-320`) | Re-derives `text_bubble`/`text_free` from geometry: a text box fully inside, or IoU ≥ 0.2 with, a bubble box takes the first such bubble (`modules/detection/base.py:48-130`, `geometry.py:39-83`). Order is a row sort by centre (`modules/utils/textblock.py:114-137`). Text is not merged per bubble. |
| comic-translate, cleanup mask | Bubble box as a bound for a flood fill | `build_bubble_clip_mask`: inside the bubble-box crop, finds the dominant grey in the text seed (±20, so a flat grey balloon still fills), takes the 4-connected components the seed touches, calls it leaked if it covers ≥ 50% of the bubble box's border, and **falls back to the ellipse inscribed in the bubble box** (`modules/utils/image_utils.py:11-191`) | — |
| **manga-image-translator** ([`89d7a8e`](https://github.com/zyddnys/manga-image-translator/tree/89d7a8ead7fc6f9b21236a6c51d3854ccfe0acbf)) | No detector class. Canny edges, contours and a flood fill around the enlarged text region (`manga_translator/rendering/ballon_extractor.py:31-103`) | Only in the opt-in `manga2eng` renderer (default is `default`, `manga_translator/config.py:59-62, 159`). `layout_lines_aligncenter` grows a centre line from the mask centroid word by word while both ends stay inside the mask, then stacks lines above and below with the same test (`manga_translator/rendering/text_render_eng.py:158-335, 425-470`) | Text lines merged into regions by geometry (`manga_translator/textline_merge/__init__.py:110-184`), not by balloon |
| **BallonsTranslator** ([`b59f003`](https://github.com/dmMaze/BallonsTranslator/tree/b59f0031df85e5144bea13e7a6879a0bdbbc7699)) | Instance masks from the Koharu RF-DETR layout model (classes text/onomatopoeia/bubble/panel), traced into a few cubic segments that ignore tails and spikes (`ballontranslator/modules/textdetector/detector_koharu.py:303-361`, `ballontranslator/utils/bubble_shape.py:544-560`). It also offers the ogkalu model as detector `ctbd`, but `detector_ctbd.py` is missing from this commit; only its test and UI strings remain (`tests/test_ctbd_detector.py:8`). | Per line: the band's usable width is the *intersection* of the shape's scanline spans across the line's ink height (`ui/text_engine/bubble_layout.py:24-83`). **Line count is unknown, so it tries N = 1, 2, … max.** For each N it centres N bands, measures them, re-breaks the text into exactly those widths with a balanced breaker, and keeps the first N where the text fills exactly N lines without overflow (`ui/text_engine/layout.py:396-446`, `bubble_layout.py:198-241`). Font size uses coarse descending probes "to avoid assuming monotonic fits in tapered bubbles", then bisection (`bubble_layout.py:294-345`). | A text joins the smallest bubble holding ≥ 90% of its mask. Several texts in one bubble **split** the bubble into cells at its necks; they are not merged (`detector_koharu.py:337-360`, `bubble_shape.py:199`). |
| **Fumeto Reader Plus** ([`fecfd2b`](https://github.com/fumetodev/FumetoReaderPlus/tree/fecfd2b8960ec49688a92436c7e01452ff32111a), GPL-3.0; approach described, no code taken) | RTMDet balloon instance masks traced to polygons (`src-tauri/gen/android/.../layout/RtmdetMasks.kt`). For box-only detectors it synthesizes a rounded-rectangle contour with radius 0.25 × the short side (`src/lib/detection/candidates/rtdetr-detector.ts:1-40`). It does not use the ogkalu model (no match in the tree). | A Svelte/Tauri app laid out with the Pretext library, not `StaticLayout`. Each row's width is the intersection of the polygon's scanline intervals, sampled at every polygon vertex inside the row band (`src/lib/overlay-layout/geometry.ts:167-222`). It lays out with the most rows the height allows, **re-centres the stack using the realized line count**, then tries shorter stacks when a word was cut in the narrow top row (`src/lib/overlay-layout/layout-service.ts:2399-2550`). | Its notes: "One balloon is one utterance". OCR line components well inside one balloon merge regardless of gaps, and a component only leaning into a balloon never joins (`CLAUDE.md:98`). A balloon shared by two speakers is carved by usable area (`CLAUDE.md:76`). This works on PP-OCR *line* components, finer than Yomu's block-level text boxes. |

What carries over to Yomu:

- **Use 1:** comic-translate's leak-test-plus-ellipse design maps directly onto `findBubbleInterior`: window = bubble box, a leak = touching the bubble box's edge, ellipse as fallback.
- **Use 2:** both mask-based projects group at a finer level than Yomu's detector boxes. With block-level `text_bubble` boxes, "merge by balloon" mostly has nothing to merge ([§2](#2-desktop-check-on-the-fixture-pages)). When it does, BallonsTranslator and Fumeto *split* the balloon among the texts rather than merging the texts.
- **Use 3:** BallonsTranslator and Fumeto agree on two points. Each line's width is the narrowest chord across that line's band. The line count is found by trying counts (ascending N, or the maximum and then a re-centre), not computed in closed form.

## 5. `StaticLayout.Builder.setIndents`

**Reference** ([StaticLayout.Builder](https://developer.android.com/reference/android/text/StaticLayout.Builder)):

- `setIndents(int[] leftIndents, int[] rightIndents)`, API 23. "Arguments are arrays holding an indent amount, one per line, measured in pixels. For lines past the last element in the array, the last element repeats." Either array may be null.
- `obtain(source, start, end, paint, width)`: width in pixels.
- `setBreakStrategy` (API 23, default `BREAK_STRATEGY_SIMPLE`) and `setHyphenationFrequency` (API 23, default `HYPHENATION_FREQUENCY_NONE`). The reference says hyphenation with BALANCED or HIGH_QUALITY "improves the structure of text layout however has performance impact".
- `setJustificationMode`: API 26.
- `setLineSpacing(spacingAdd, spacingMult)`: defaults 0 and 1.
- `setIncludePad`: default true.
- `setUseLineSpacingFromFallbacks`: API 28, default false, "strongly recommended" true.
- `setMaxLines` and `setEllipsize`: API 23.
- `setLineBreakConfig`: API 33.

**AOSP source** (`frameworks/base` main, read 2026-11-25):

- **Indents narrow the line breaker's width per line.** `StaticLayout.generate` sums left and right into one total per line and passes it to `LineBreaker.Builder.setIndents` ([StaticLayout.java:787-801, 825-830](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/text/StaticLayout.java)). `LineBreaker` documents that total as "the sum of both left and right indentations", with the last element repeating ([LineBreaker.java:236-245](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/text/LineBreaker.java)). Hyphenation, break strategy and justification all run inside that per-line width, because they are options of the same `LineBreaker` (`StaticLayout.java:823-830`).
- **Line numbers are absolute across paragraphs.** Each paragraph is broken with `computeLineBreaks(measured, constraints, mLineCount)`, where the third argument is "a line number of this paragraph" (`StaticLayout.java:943-947`, `LineBreaker.java:571-580`). Index i in the array is the layout's line i, even after a `\n`.
- **Drawing offsets use the separate arrays.** `getIndentAdjust` returns the left indent for ALIGN_LEFT, minus the right for ALIGN_RIGHT, and `(left − right) >> 1` for ALIGN_CENTER, clamping each array to its last element (`StaticLayout.java:1527-1556`). `Layout` applies it when drawing and measuring (`Layout.java:754-765, 1184-1211`).
- **Pitfall: unequal array lengths.** When building the breaker's totals, `generate` copies each array only up to its own length and does not repeat the shorter one's last element (`StaticLayout.java:792-798`). `getIndentAdjust` and `getTotalInsets` do repeat it (`:1395-1402, 1528-1550`). Lines past the shorter array would then break at one width and draw at another. *Pass two arrays of equal length.*
- **Pitfall: line height is font metrics, not Yomu's 1.5 ratio.** `StaticLayout` line tops come from ascent and descent, scaled by `setLineSpacing`. A band calculation that assumes `fontSize × 1.5` (`Typesetter.kt:21`) must either set spacing to match or read `getLineTop` / `getLineBottom` after the first build (*inferred*).

**Computing ellipse indents when the line count is unknown** (*my derivation*, matching what the two shaping projects do):

1. Take the balloon ellipse: centre (cx, cy), semi-axes a and b, from the bubble box or the interior's bounds, minus padding. For line height L and a candidate count N, centre the block vertically: line i spans y from `cy − N·L/2 + i·L` to that value plus L.
2. The usable half-width of a line is the ellipse's at the band edge *farther* from cy: `hw_i = a·sqrt(1 − (d_i/b)²)` with `d_i = max(|top_i − cy|, |bottom_i − cy|)`. If `d_i ≥ b`, the line does not fit at this N.
3. Indent on each side = `a − hw_i` (plus padding), as `IntArray`s of length exactly N, identical left and right. ALIGN_CENTER keeps every line on the axis.
4. Build the layout. Accept N when `lineCount == N` and no word was force-broken. Otherwise try the next N. BallonsTranslator goes up from N = 1. Fumeto starts at the maximum, then re-centres on the count it actually got. Because the last element repeats, `lineCount > N` means overflow at the last (narrow) width, which is the signal to try N + 1.
5. Wrap that in the font-size search. Fit is not monotonic in a tapered shape (BallonsTranslator's note above). The integer sizes 10–36 are only 27 candidates, so a descending linear scan is cheap and safe.

Yomu does not need `StaticLayout` to try this. `wrapText` could take a per-line width list, and the renderer already centres each line (`TranslationRenderOverlay.kt:218-221`). Hyphenation (#463) is the argument for moving to `StaticLayout`/`LineBreaker`. The two should not be tangled in this spike.

## 6. Risks and open questions

- **Painting over the outline and art.** The overlay paints the whole render box (`TranslationRenderOverlay.kt:199-201`). A render box grown to the bubble box covers the outline, a tail, and art outside an oval. Shaped lines want an oval background inset from the outline (or a background limited to the glyph area) rather than a larger round rectangle.
- **Not every bubble box is an ellipse.** Caption boxes are rectangles, open balloons are cut by a panel border, burst balloons are spiky. Ellipse indents in a rectangle waste the corners; in an open balloon they centre on the wrong point. A cheap shape test is open: for example, flood-fill interior area ÷ its bounding box (an ellipse gives about π/4 ≈ 0.79, a rectangle about 1).
- **Conjoined balloons.** Bubble boxes of touching balloons overlap their neighbours' glyph boxes (11 of 66 on the fixtures). The #88 guard would reject them as they are. Clipping the bubble box away from neighbours, or keeping the flood fill for these, is a spike decision.
- **Vertical-to-horizontal mismatch.** Japanese balloons are tall and narrow; English wants width. In a tall ellipse the top and bottom lines are very short, so the gain shows up in font size only if the middle lines carry most of the text. Measure it; do not assume it.
- **What "touching" means for asides.** #428 used "touching". On the fixtures the two candidates were 4 and 5 px away. The spike must fix a tolerance, scaled to page size since emulator captures are about 1.5× the fixture files, and report how many titles, signs and SFX it also catches (#428: most `text_free` boxes are not asides).
- **Merging two text boxes into one utterance** would leave one translation for two render boxes. Neither mask-based project merges. They split the balloon. The fixtures have no such case, so this may not need an answer yet.
- **Panels from bubble boxes.** `ContextAssembler` cuts at gutters using glyph boxes. Bubble boxes of open balloons can cross a panel border. Keep grouping on glyph boxes unless the spike measures otherwise.
- **Threshold for bubble boxes.** Should they follow the reader's threshold or a fixed one? Scores were mostly ≥ 0.72 on the fixtures, with one at 0.39.
- **Robolectric graphics mode** for any `StaticLayout`-based unit test (§3).
- **OpenMantra pages are not in the main checkout.** `vendor/open-mantra-dataset` is absent on 2026-11-25. The #428 aside pages (tencho 010/012/029, bourei 011/021) need it fetched again for a use-2 measurement.
- **Not known:** per-class accuracy; whether long tails are included; how the class behaves on webtoon and colour pages; and whether the boxes shift between captures, as #393 found for glyph boxes (half a pixel).

## 7. Measurement plan for #471's acceptance criteria

Fixtures: `scripts/speed-fixtures/pages/` (`balloon-dense-dialogue.jpg`, `bourei-p04.jpg`, `tojime-dense-action.jpg`, `wide-furigana-spread.jpg`, `wide-title-8col-spread.jpg`). Run on the `Yomu_16G` emulator with `scripts/page-report.py <page.jpg> <out-dir>` on `main` and on the spike build, then `scripts/page-report.py --compare <before> <after>` (`scripts/page-report.py:1-16, 197-217`).

**Gap in the tooling:** `page.json` carries each bubble's detector `bounds`, OCR text, confidence and translation, in detection order. It carries no render box, font size, line list, bubble box or prompt order (`app/src/main/java/com/yomu/app/service/PageReport.kt:11-24, 76-90`), and `--compare` prints only OCR and translation. For the spike, add to the report (spike code only): per text box, the host bubble box, the flood-fill outcome (`grown` / `unchanged` / `none` / `reach`), the render box, the typeset font size and line count, and the block reading order.

1. **Bubble boxes kept, text boxes unchanged.** Parse class 0 into its own list with bubble-only NMS. Check: per page, the kept text boxes' bounds and count in the spike's `page.json` match `main`'s (pair them with `--compare`).
2. **Use 1, render-box fallback.** Per page, count text boxes by flood-fill outcome. For `none` and `reach`, count how many get a usable box from the bubble box. Usable means larger than the glyph box and not intersecting another glyph box, the #88 rule. Report both variants: (a) bubble box as a plain fallback (0.707 rectangle or inscribed ellipse), and (b) bubble box as the flood fill's window and leak test, with an ellipse fallback. Desk expectation: 4 `none` (3 hosted) and 13 `reach` (11 with overlapping hosts) out of 70.
3. **Use 2, grouping.** Count bubble boxes holding ≥ 2 text boxes (desk: 0), and `text_free` boxes within the chosen tolerance of a bubble (desk: 2). For each, say whether moving the aside right after its host would change the prompt order, compared with `ConversationBlock.readingOrder`. Repeat on #428's OpenMantra aside pages, where the order is known to be wrong (tencho 012).
4. **Use 3, shaped lines.** For round balloons only (pass the shape test, not a caption, not open), typeset the *same* translation from the `main` report twice: today's rectangle, and ellipse lines inside the bubble box or the interior's bounds (same padding, line height and font range). Record font size reached and lines used per bubble, and give the median and the share that gain at least 2 px. Doing this offline in a unit test is cheaper than an emulator loop, provided the graphics-mode question is settled; then confirm on the emulator.
5. **Before/after page reports** for all five pages on the emulator, with `done.png` side by side.
6. **No worse for ordinary balloons.** For boxes whose outcome on `main` is `grown`, check in `done.png`: no clipped or ellipsized text that was whole before, no overlap with a neighbour, no background over the balloon outline or art, and a font size not smaller than on `main`.
7. **Speed.** No new model, so the speed benchmark is not required. Note whether the per-size N loop adds measurable typesetting time per page; it should be sub-millisecond (*unmeasured*).
8. **Publish.** Tag `spike-471`, post the results with a recommendation per use, file an issue for each use worth building, and answer ADR-0020's open question.

## Sources

- Yomu at `c31b2d1`: files and lines as cited. ADRs 0002, 0007, 0013, 0020. Research notes linked at the top.
- Detector: [ogkalu/comic-text-and-bubble-detector @16e8a62](https://huggingface.co/ogkalu/comic-text-and-bubble-detector/tree/16e8a622f91fabc6b5b65c96d32d1183f8843546) (`README.md`, `config.json`, `preprocessor_config.json`); [Hugging Face API](https://huggingface.co/api/models/ogkalu/comic-text-and-bubble-detector).
- Android: [StaticLayout.Builder reference](https://developer.android.com/reference/android/text/StaticLayout.Builder); AOSP `frameworks/base` main: [StaticLayout.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/text/StaticLayout.java), [LineBreaker.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/text/LineBreaker.java), [Layout.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/text/Layout.java).
- Robolectric: [GraphicsMode.Mode](https://robolectric.org/javadoc/latest/org/robolectric/annotation/GraphicsMode.Mode.html); [4.10 alpha-1 notes](https://github.com/robolectric/robolectric/discussions/8054).
- [ogkalu2/comic-translate@8977b91](https://github.com/ogkalu2/comic-translate/tree/8977b91a4f7a40c3917c5a268e9e7d78e1d818da) (Apache-2.0).
- [zyddnys/manga-image-translator@89d7a8e](https://github.com/zyddnys/manga-image-translator/tree/89d7a8ead7fc6f9b21236a6c51d3854ccfe0acbf) (GPL-3.0).
- [dmMaze/BallonsTranslator@b59f003](https://github.com/dmMaze/BallonsTranslator/tree/b59f0031df85e5144bea13e7a6879a0bdbbc7699) (GPL-3.0).
- [fumetodev/FumetoReaderPlus@fecfd2b](https://github.com/fumetodev/FumetoReaderPlus/tree/fecfd2b8960ec49688a92436c7e01452ff32111a) (GPL-3.0; read only, no code copied).
