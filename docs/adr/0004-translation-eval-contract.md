# The translation eval scores one page-level call on annotation Japanese, keyed by bubble id

The translation half of the eval feeds engines OpenMantra's `text_ja` annotation — clean Japanese, not OCR output — assembles it into a page through the real `ContextAssembler`, and scores a single page-level call whose output is matched to the reference by bubble id. Engines that cannot take a page-level call are reported on a separate per-line floor line, never ranked against the gate. Failure is caught by two deterministic checks — Japanese residue and non-translation — rather than by the readability ratio, which is demoted to a diagnostic.

Before this, the harness looped `TranslationEngineSelector.translate(line)` over lines of ground-truth Japanese while `translation-quality/SCHEMA.md` claimed the input was "actual OCR output, including noise". ADR-0002 had already made the page-level call the design target, so the harness measured an architecture the project had decided against, on an input its own schema disclaimed, with metrics that scored CAT-Translate 0.000 untranslated and 0.000 artifact on a run whose outputs included a verbatim echo of the system prompt and a line of untranslated Japanese.

## Considered Options

**Score real OCR output instead of annotation Japanese.** Rejected. The eval's job here is to rank translation engines for #35; feeding them OCR output makes that ranking a function of the OCR model #34 has not chosen yet, so every number would need re-deriving once it does. It also cannot be built today — the harness has no OCR half at all, and whether it gets one is #46's open question. The cost is real and is recorded below, not hidden.

**Keep per-line scoring.** Rejected. ADR-0002 made one page-level call with panel markers, reading order and session context the design target and named the LLM the sole design target. A per-line harness cannot exercise any of that, and it cannot see the failure mode that matters most to a page-level engine: returning fewer entries than there are bubbles.

**Score only page-level, dropping ML Kit and OPUS-MT.** Rejected. `LlamaTranslationBridge.supportsBatch()` is `true` and both others are `false`, so a page-only harness would silently have nothing to say about the non-LLM engines. #26 puts the LLM path out of reach on budget-tier RAM, which makes a non-LLM fallback a live shipping path rather than a curiosity. They stay, on their own line, explicitly not comparable to the gate.

**Run the full `processPage(bitmap)` and accept detection and OCR in the loop.** Rejected — it is the previous option's problem in another form: the translation score becomes a cascade score, moving whenever the detector or OCR changes.

**Stub the detector and OCR inside `processPage`.** Rejected, and this is the one to be most careful about. This map has twice found a harness passing green while measuring nothing — #36's detector was silently stubbed at a fake 100%, and #41's translation cases were all discarded on a line-count mismatch under a summary that said so and a test that still exited 0. A stub that returns ground truth is indistinguishable, from the outside, from a stage that works.

**Add a chrF or COMET score, or an LLM judge, now.** Deferred, not rejected. Those answer "how good is this translation"; the checks below answer "did the engine translate at all". The second question is the one currently returning wrong answers, and it costs two regular expressions.

## Consequences

**The eval calls `ContextAssembler.assemble(...)` and `TranslationEngine.translate(blocks, ...)` directly.** Ground-truth boxes stand in for detections and `text_ja` stands in for OCR results; `BubbleDetector` and `OcrEngine` are not called. The distinction from stubbing is not cosmetic — an absent stage is visibly absent, whereas a stub that returns ground truth reports success. This also puts `ContextAssembler`'s panel detection and manga reading order under a score for the first time.

**Cascade quality is unmeasured, deliberately.** No number anywhere in this harness answers "how much does OCR error cost the final translation". Whether that gap is worth closing depends on #46, and it should be closed by a separate end-to-end case type rather than by contaminating this one.

**`translation-quality/SCHEMA.md` is corrected.** It has claimed OCR-noise input since it was written, and `generate-cases.py` has never produced that. The schema follows the harness, not the other way round.

**The gate is the page-level call; the per-line loop survives only as a floor.** Reported for ML Kit and OPUS-MT under a heading that states they are not ranked against the gate. A single number spanning both modes would rank an engine on a call shape it will never receive in production.

**Output is matched to the reference by bubble id, and a missing id scores zero rather than voiding the case.** The eval supplies the bubbles, so it owns the ids: bubble id *n* is line *n* of `source.txt` and `reference.txt`. Today `score_translation` returns `{"error": "line count mismatch"}` and the case scores nothing — that rule is why every run before #45 printed "No engine outputs scored." An engine that returns eight entries for twelve bubbles has failed four bubbles; that is the exact defect page-level scoring exists to catch, and it must cost score.

**Two deterministic failure metrics replace `untranslated_rate`.** *Japanese residue rate* is the fraction of output entries containing a CJK codepoint — the old metric required `source == output` exactly, so a line like `.. でも` passed clean. *Non-translation rate* is the fraction of entries that echo the instruction or return a refusal template rather than a translation; one CAT-Translate output is verbatim `Translate the following Jpn manga text into natural English. Please reply with the translation only.` and scored 0.000 on every existing metric. *Readability ratio* stays, reported, never gated: at 2.587 it flags that something is wrong without distinguishing a verbose translation from an echoed prompt.

**The first page-level numbers will be a pre-ADR-0002 baseline, and must be labelled as one.** ADR-0002 is decided but unbuilt: `TranslationEngine.translate` still carries `@Suppress("UNUSED_PARAMETER")` on `sessionContext` and never reads it, and `translateBatch` prompts `Translate these Japanese phrases to English, one per line, numbered:` — no panel markers, no bubble ids in the prompt, reading order flattened, cache still live on the per-bubble path. Candidates all run through identical scaffolding, so the *ranking* #35 needs survives; the absolute number is not ADR-0002's score and a report that lets anyone read it as such is a defect.

**Session context is not exercised by this harness.** Cases are single, non-consecutive pages, so `sessionContext` is empty everywhere and is reported as such. Cross-page referent resolution is what #30's contrastive set was chosen to measure and what ADR-0002 named as its acceptance gate; building a second, weaker version of it out of consecutive OpenMantra pages would measure the same thing worse.

**OPUS-MT is a live candidate, conditionally.** Its weights now have a download route, and `OpusMtTranslator.load()` fails with `dlopen failed: library "libdjl_tokenizer.so" not found` — a packaging defect tracked as #14. If that is a small fix it is measured on the per-line floor, where #26's RAM ceiling makes it the plausible fallback for devices the LLM does not fit. If it is not a small fix, it leaves the eval roster and #35 decides whether the engine code survives.

**Whether this case set can rank translation engines is not decided here.** #44 needed a full session, a paired sign test and a corpus-ceiling analysis to answer the same question for detection, and landed on "ranks a large gap, never a near-tie, with an 8pp separation rule". Translation has roughly 150 lines and noisier metrics, and the variance cannot be computed until the metrics and the page-level scoring path above exist. Until it is answered, no separation rule and no pass bar are set for translation.
