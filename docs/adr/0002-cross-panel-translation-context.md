# Translation context is one page-level LLM call carrying panel structure and session memory

Yomu translates a page in a single call to a large-context LLM. The call carries every bubble on the page at once, grouped by panel and ordered in manga reading order, plus the previous page's source/translation pairs as session memory. Bubbles are addressed by their real detector id in both directions, so a malformed response loses one bubble instead of shifting the rest.

The LLM path is the design target. Small-window NMT engines (OPUS-MT class) remain in the codebase as legacy engines, not as a second architecture: when one of them is active, context collapses to per-line translation and none of this applies. No fallback context architecture is designed for them.

## Considered Options

- **Keep the current design (per-bubble calls, or 4-bubble blocks).** Rejected. It was never actually a context architecture: both paths in `TranslationEngine` flatten blocks into individual lines, and the batch prompt is a bare numbered list with no panel, order, or speaker information. The `sessionContext` parameter was declared on `TranslationPipeline.processPage` and `TranslationEngine.translate` (marked `@Suppress("UNUSED_PARAMETER")`) but never read, and `OverlayService` never passed it. There is no tuning available on something not wired.
- **Tune the existing block size and panel thresholds.** Rejected for the same reason: block size only controls how bubbles are grouped before being flattened, so changing 4 to 8 changes nothing the model sees.
- **Chunk by panel, one call per panel.** Rejected. Chunking exists to fit small context windows; the models surveyed in #29 offer 262K-token windows against a manga page of a few hundred tokens. Multiple calls per page reintroduce the exact cross-panel blindness this decision removes, and multiply model latency by the panel count.

## Consequences

**One call per page, whole page in the prompt.** Panel grouping appears as plain markers between bubble groups, not as call boundaries; reading order is the order the bubbles appear in. Panels remain useful as a signal that a topic changed, which a flat list of lines destroys.

**`ContextAssembler` is demoted to ordering plus grouping.** The fixed 4-bubble `ConversationBlock` chunking goes away — it cut across panel boundaries at arbitrary points, splitting conversations for a constraint that no longer exists. Panel detection stays as-is for now: it is a bubble-box gap heuristic rather than true panel-border detection, and its accuracy is bounded by detection quality (see below), so tuning it before that is settled optimizes against noise.

**Bubbles are addressed by detector id, not by position.** Prompt lines and response lines are both tagged `[<bubbleId>]`. The current parser matches `^\d+\.` against a positional index, so an LLM that drops, merges, or prefaces a single line misaligns every bubble after it — a failure that renders as plausible text in the wrong balloons, which is worse than no translation. Ids that do not come back fall back to their source text. JSON was rejected: it costs tokens and small local models break its syntax more often than they break `[7] text`.

**Session context is the previous page's source/translation pairs, held by the caller.** `OverlayService` keeps an in-memory `List<Pair<ja, en>>` (roughly 6–12 pairs, one page's worth) and passes it into `processPage`; `TranslationEngine` stays stateless. The existing `sessionManager.saveTranslation` blob (`"[id] src → tgt"`, one string per page) is a display artifact and is not re-parsed to reconstruct pairs — persistence format stays untouched. The list clears when the session ends or the user switches manga.

**`release()` stops clearing bridge memory between pages.** `OverlayService` currently calls `translationPipeline.release()` after every page, which calls `translationBridge.clearMemory()` and destroys any model-side continuity each trigger. Memory is released when the session ends, not per page.

**The line-keyed translation cache is bypassed on the LLM path.** Its key is `sha256(engineId:modelId:normalized-source)`, which by construction cannot represent context: the same Japanese line must be allowed to translate differently in different scenes, and a cache hit would silently defeat the whole decision. The cache stays for deterministic per-line legacy engines. A prompt-keyed cache was rejected as pointless — no two pages produce the same prompt.

**Only bubbles with non-empty OCR text enter the prompt, and gaps are not marked.** This is already the effective behaviour of `textByBubbleId`. Placeholders for undetected or unread bubbles were rejected: the model cannot repair a box that was never detected, and an explicit "something is missing here" marker invites invented dialogue that reads as confidently as the real thing.

**Context quality is capped by detection.** #36 measured the incumbent detector at 35.9% recall@0.5. Panel grouping, reading order, and cross-page memory are all computed from detected boxes, so this decision cannot be evaluated on its merits until #33 settles detection. The architecture is still correct to record now; its measured benefit will move when detection does.

**Requirement carried to #33: bubble class labels.** `Bubble` has no class field — speech / narration / sfx exist only in eval expected data, never at runtime. Marking narration and sfx distinctly in the prompt is a real gain (narration is not dialogue and should not be read as a character speaking), but it is detector work. #33 must decide whether the chosen detector exposes per-box class, and this ADR records the dependency rather than inventing a field here.

**Acceptance gate: the JA-EN zero-pronoun contrastive set from #30.** Line-by-line scoring cannot distinguish this architecture from the flat list it replaces — both produce fluent-looking English. The contrastive set is what makes the difference measurable. It does not exist yet, and the eval harness's translation half has not been verified to actually run the engine (the detection half was found stubbed in #36); both are tracked separately. Until the gate exists, this decision is unmeasured, not unmade.

## Amendment (#53): the acceptance gate now has a contract

The gate above was a pointer to a research note; its shape is now fixed by [ADR-0006](0006-coherence-gate-contract.md). The set is hand-authored **minimal pairs** (correct target vs. a single-referent-flipped corruption) scored by **contrastive accuracy** — the model assigns higher probability to the correct target. Passing is **directional**: accuracy with session context must beat accuracy with it blanked, on the same pairs (paired, chance floor 0.5), not an absolute bar. Scoring runs **off device** on desktop llama.cpp, so **this ADR owes no new native work** — the production path stays generation-only. The corpus and runner are post-map build work; the gate is defined now but cannot run until this ADR's architecture is built (`sessionContext` is still unread today, per #47).

## Amendment (#68): page-level single call is not viable on the curated 0.8b model

**Status:** accepted, amends the "one call per page" decision.

This ADR assumed one page-level LLM call could carry every bubble, each addressed by its detector id, and return every translation in one structured reply. Built and measured in #68, `CAT-Translate-0.8b` (ADR-0008's curated default) **cannot produce id-keyed batch output**: given a page of `[id]`-tagged lines it echoes the instruction and returns the source (Japanese-residue 0.93, gate FAIL on SM-S911B / 147 ids). Removing the `MAX_TOKENS = 64` cap did not move residue — the #58 baseline residue was **not** purely a truncation artifact. The same model translates correctly **one line at a time** once invoked in its trained form (single-text user turn, no system prompt): per-line residue 0.03.

**What stands:** the *goals* — cross-panel context, session continuity, per-id mapping with per-bubble fallback, empty-OCR omission, no per-box class labels.

**What changes:** "exactly one model call per page" is relaxed. Cross-panel context may instead be delivered by giving the model the surrounding page as **context** while it translates **one target bubble per call**, results mapped by id (#65 child, path 2b). The reader still triggers once per page; panels remain markers, never call boundaries in the reader's sense.

**Model escalation:** the single-call id-keyed design remains valid for a **more capable model**. A larger CAT-Translate sibling (1.4b / 3.3b) that can emit id-keyed batches may run the original design on capable devices (#65 child, path 2a; [ADR-0008](0008-translation-model-selection.md)). The `TranslationEngine` batch path is retained for that reason.

**Native fix carried by this finding:** the JNI layer injected a system prompt the model was never trained with, which made even per-line calls echo/refuse (non-translation 0.48). CAT-Translate's model card uses no system prompt and the exact user turn `Translate the following {src} text into {tgt}.\n\n{text}`; `apply_chat_template` in `llama_jni.cpp` was corrected to match.

**Evidence:** #68 gate runs (SM-S911B, 147 ids, entry-weighted): batch residue 0.93; per-line residue 0.03; per-line non-translation 0.48 with the injected system prompt, dropping sharply once the prompt matched the model card.
