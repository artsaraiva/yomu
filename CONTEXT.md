# Yomu

On-device manga translation for Android. A page is captured on manual trigger, its speech bubbles are detected and read, and the text is translated locally without leaving the device.

## Language

### Models

**Curated model**:
A model Yomu selects, hosts a download URL for, and supports. The only kind of model allowed in the detection and OCR slots.
_Avoid_: bundled model, official model, default model

**Custom model**:
A GGUF the user supplies from their own storage for the translation slot. Permitted but explicitly unsupported, and labelled as such wherever its output appears.
_Avoid_: sideloaded model, user model, BYO model, third-party model

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

### Translation context

**Panel**:
A group of bubbles inferred to belong to the same comic frame. Used to order bubbles and to mark grouping inside the page prompt — never to split a page into multiple model calls.
_Avoid_: conversation block, chunk, frame

**Session context**:
The previous page's source/translation pairs, carried into the next page's prompt so pronouns, names, and register stay consistent across a reading session. Held by the caller, cleared when the session ends.
_Avoid_: history, memory, conversation history
