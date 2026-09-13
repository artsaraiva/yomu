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

**Deliverable**:
One model at one quantization — the unit the curated catalog lists, gates on device fit, and downloads. Two quantizations of the same model are two deliverables, each with its own size, checksum and licence.
_Avoid_: model variant, build, quant entry, artefact

**Fit budget**:
The RAM a deliverable must fit inside to be offered on a given device — its file size plus a fixed resident-overhead estimate, against a fraction of the device's total memory. Estimated from measurement, never from parsing a file Yomu has not downloaded.
_Avoid_: RAM gate, memory limit, device tier

**Floor engine**:
A translation engine that can only be asked one bubble at a time and so cannot run the page-level context architecture — OPUS-MT and ML Kit. This is a prompt-shape floor, unrelated to the low-storage model option or a device's hardware floor.
_Avoid_: fallback engine, legacy engine, secondary model

**Gate engine**:
A translation engine that takes the page-level call and so runs the page-level context architecture — the LLM. The counterpart of a floor engine; the eval scores it against the gate and reports the floors separately (ADR-0004).
_Avoid_: primary engine, main model, context engine

**Model family**:
The set of deliverables that are the same model at different quantizations. Grouped only so device fit can offer the largest one that fits; a family is never itself selectable.
_Avoid_: quant matrix, model group, variant set

**Translation slot**:
The selected component that accepts one geometry-free page and owns every model-specific translation decision. Exactly one floor engine or LLM adapter fills it at a time.
_Avoid_: translation bridge, selected engine wrapper

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

### Eval instrument

**Arm**:
One configuration measured over the whole case set — a detector, an engine, or an engine at one parameter value. The unit the eval declares in advance, reports on, and invalidates: arms are scored independently, so one broken arm never suppresses another's numbers.
_Avoid_: engine, candidate, variant, run

**Run record**:
One terminal JSON line describing a single arm/case/stage invocation on device, written to the run's own directory. The eval's only input about what happened: it is never reconstructed from logcat, and never shared between runs.
_Avoid_: log line, trace, telemetry, event

**Invalid arm**:
An arm whose records contradict the run manifest — a missing or duplicated stage, an unknown outcome, observed model metadata that differs from what was declared, or a model that never loaded. No aggregate is printed for it and the command exits nonzero, because a harness that prints a mean for something it did not measure is the failure this instrument exists to prevent.
_Avoid_: failed run, skipped engine, error

**Measured outcome**:
An engine answering badly — blank, timed out, overflowed, errored, or zero boxes detected. A result, not a fault: the arm stays valid and every expected bubble id stays in the denominator.
_Avoid_: failure, error case, miss

### Coherence quality

**Contrastive minimal pair**:
A source whose subject is elided and resolvable only from the previous page, a correct English target, and a corrupted target that flips only the disputed referent. The unit the coherence gate scores. Authored by hand from OpenMantra — the correct target is its English annotation, the corruption is a competent annotator's single-referent flip, never a heuristic.
_Avoid_: test case, contrastive example, negative pair

**Contrastive accuracy**:
The fraction of minimal pairs where the model assigns higher probability to the correct target than to the corrupted one. Scored off device on desktop llama.cpp, which exposes log-probabilities the on-device path does not — the production path stays generation-only.
_Avoid_: pair accuracy, coherence score, referent accuracy

**Coherence gate**:
The directional check that contrastive accuracy with the rest of the page in the prompt beats accuracy with it blanked, on the same pairs. Passing means page context provably helps referent resolution; it is a direction, not an absolute bar, because the corpus is too small to power one. ADR-0013 withdrew cross-page session context, so the gate measures intra-page coherence only.
_Avoid_: coherence bar, context gate, pronoun gate

### Translation context

**Panel**:
A group of bubbles inferred to belong to the same comic frame. Used to order bubbles and to mark grouping inside the page prompt — never to split a page into multiple model calls.
_Avoid_: conversation block, chunk, frame

**Session context** (withdrawn):
The previous page's source/translation pairs, carried into the next page's prompt so pronouns, names, and register stay consistent across a reading session. ADR-0013 withdrew it on measurement — the production-shaped payload overflowed the prompt cap on 4 of 17 pages and no arm showed a quality gain — and the plumbing is deleted, not dormant. The term is kept here because the ADRs that decided and undid it still use it; nothing in the code does.
_Avoid_: history, memory, conversation history

## Visual system

**Chrome**:
The app's own managed screens — Home, History, Settings — rendered in Compose. The surface the paper-mâché visual system governs.
_Avoid_: main UI, app screens, the app

**Overlay control**:
An interactive element drawn over live manga to operate translation — floating button, quick-settings popup, close zone, status toast. Governed by the paper-mâché visual system, but its legibility over arbitrary artwork is a constraint, not a cosmetic choice.
_Avoid_: overlay UI, HUD, widget

**Typeset bubble**:
The translated text laid into a bubble over the live page, coloured for legibility against the artwork underneath (today dark ink on a light fill). Outside the visual system: its appearance answers to readability, never to the paper aesthetic.
_Avoid_: translation overlay, rendered bubble, text box

**Paper surface**:
A visual layer in the [[chrome]] evoking cut or layered paper through warm colour, shape, border and offset shadow — never a photographic paper texture. The building block of the paper-mâché language.
_Avoid_: card, panel, texture
