# Yomu

On-device manga translation for Android. A page is captured on manual trigger, its speech bubbles are detected and read, and the text is translated locally without leaving the device.

## Language

### Models

**Curated model**:
A model Yomu selects, hosts a download URL for, and supports. The only kind of model allowed in the detection and OCR slots, and the shipped default in the translation slot.
_Avoid_: bundled model, official model, default model

**Custom model**:
A GGUF the user supplies from their own storage for the translation slot, alongside its curated default. Permitted but explicitly unsupported, and labelled as such wherever its output appears.
_Avoid_: sideloaded model, user model, BYO model, third-party model

**Floor engine**:
A translation engine that can only be asked one bubble at a time and so cannot run the page-level context architecture — OPUS-MT and ML Kit. Kept for devices that cannot run the LLM default; selectable, never the default, never scored against the LLM (it produces the per-line floor).
_Avoid_: fallback engine, legacy engine, secondary model

### Detection quality

**Containment recall**:
The fraction of ground-truth text regions that a single detection covers at least 95% of, without that detection also covering another region's centre. The gate the bubble-detection eval passes or fails on, because it is the precondition for OCR reading a whole sentence.
_Avoid_: recall, IoU recall, accuracy

**Localisation recall**:
The fraction of ground-truth text regions whose centre falls inside a detection, matched one-to-one so a merged detection localises only one of the regions it swallows. Reported, never gated — it distinguishes a detector that cannot find text from one that finds it and frames it badly.
_Avoid_: centroid recall, hit rate

**Merged detection**:
One detection covering the centres of two or more text regions. Counts as a hit for none of them: the crop hands OCR two speakers' lines fused into one bubble id.
_Avoid_: overlapping box, greedy box

**Crop pad**:
The margin added on every side of a detection before the page is cropped for OCR, expressed as a fraction of page width. Applied at crop time only — never to the box used for overlay placement or panel grouping.
_Avoid_: dilation, box expansion, margin

### OCR quality

**Box exact match**:
Whether the string OCR returns for a text region equals its annotated Japanese, after both are NFKC-normalised and stripped of whitespace. The gate the OCR eval passes or fails on: a line reaches the translator whole or wrong, and a partly-correct reading is not partly useful.
_Avoid_: accuracy, line accuracy, match rate

**Character error rate**:
Total edit distance between OCR output and annotated Japanese, over total annotated characters, summed across the whole set before dividing. Reported beside the gate to say how far a failed reading is from the text — never gated, because a single wrong character means one thing on a 2-character region and another on a 30-character one.
_Avoid_: CER score, accuracy, error rate

**Blank-crop probe**:
A crop taken from a page region holding no annotated text, fed to OCR to see whether it invents one. Reported as a rate, never gated. It stands in for the false-positive detections that reach OCR in production, which scoring annotated regions alone cannot see.
_Avoid_: negative case, empty test, noise case

### Translation quality

**Japanese residue**:
Japanese characters left in an engine's output. The evidence that a bubble was not translated — replacing the older test of whether output was byte-identical to its source, which a single edit to the text defeated.
_Avoid_: untranslated output, passthrough, copy

**Non-translation**:
An output that answers with something other than a translation — the instruction echoed back, a refusal, an apology. Distinct from a bad translation: the engine did not attempt the task.
_Avoid_: hallucination, garbage, refusal

**Readability ratio**:
Output word count over reference word count. Reported as a symptom, never gated: it flags that an engine is producing too much text without saying whether that text is a verbose translation or an echoed prompt.
_Avoid_: verbosity score, length penalty

**Per-line floor**:
The score of an engine that can only be asked one bubble at a time. Reported beside the gate and never ranked against it, because the gate scores a whole page in a single call.
_Avoid_: baseline, fallback score

### Coherence quality

**Contrastive minimal pair**:
A source whose subject is elided and resolvable only from the previous page, a correct English target, and a corrupted target that flips only the disputed referent. The unit the coherence gate scores. Authored by hand from OpenMantra — the correct target is its English annotation, the corruption is a competent annotator's single-referent flip, never a heuristic.
_Avoid_: test case, contrastive example, negative pair

**Contrastive accuracy**:
The fraction of minimal pairs where the model assigns higher probability to the correct target than to the corrupted one. Scored off device on desktop llama.cpp, which exposes log-probabilities the on-device path does not — the production path stays generation-only.
_Avoid_: pair accuracy, coherence score, referent accuracy

**Coherence gate**:
The directional check that contrastive accuracy with session context beats accuracy with it blanked, on the same pairs. Passing means session context provably helps referent resolution; it is a direction, not an absolute bar, because the corpus is too small to power one.
_Avoid_: coherence bar, context gate, pronoun gate

### Translation context

**Panel**:
A group of bubbles inferred to belong to the same comic frame. Used to order bubbles and to mark grouping inside the page prompt — never to split a page into multiple model calls.
_Avoid_: conversation block, chunk, frame

**Session context**:
The previous page's source/translation pairs, carried into the next page's prompt so pronouns, names, and register stay consistent across a reading session. Held by the caller, cleared when the session ends.
_Avoid_: history, memory, conversation history
