# Yomu ships CAT-Translate-0.8b as the curated default; OPUS-MT and ML Kit stay as optional floor engines; fine-tuning is deferred behind a measurement trigger

> **Revised in part by [ADR-0009](0009-selectable-translation-model-set.md).** The "one curated default, not a per-tier model ladder" clause below was set because the eval could not rank engines ([#58](https://github.com/artsaraiva/yomu/issues/58)). The [#84](https://github.com/artsaraiva/yomu/issues/84) bake-off supplied that ranking, so ADR-0009 adds a small curated *selectable* set (default unchanged). The rest of this ADR — 0.8b as default, OPUS-MT/ML Kit as floors, the fine-tune trigger — stands.

The curated default in the translation slot is the **CAT-Translate-0.8b** LLM — the only engine that runs [ADR-0002](0002-cross-panel-translation-context.md)'s page-level context architecture. OPUS-MT and ML Kit remain in the codebase as **floor engines**: selectable on devices that cannot run the LLM, never the default, never scored against it. Yomu ships **one** curated default, not a per-tier model ladder ([ADR-0001](0001-custom-model-permissiveness.md), [#32](https://github.com/artsaraiva/yomu/issues/32)). Fine-tuning is not done now.

This is decided as a policy on a pre-registered rule, not on a live measured ranking. The translation eval cannot rank engines today ([#58](https://github.com/artsaraiva/yomu/issues/58)): no engine passes all [ADR-0004](0004-translation-eval-contract.md) gates, and the LLM fails one on a scaffolding artifact, not on model quality (see Consequences). This is the same position [#33](https://github.com/artsaraiva/yomu/issues/33)/[#34](https://github.com/artsaraiva/yomu/issues/34) resolved from — the challenger comparison cannot fire, so the already-integrated incumbent holds.

## Considered Options

- **Bring up a challenger LLM now (HY-MT2, Qwen3 from [#29](https://github.com/artsaraiva/yomu/issues/29)).** Rejected. Their 262K context beats CAT-Translate's 8K on paper, but neither has an on-device GGUF path built, and the eval cannot rank them against the incumbent (#58) — a bake-off would be built on a broken scale. CAT-Translate-0.8b is already integrated, runs through llama.cpp, and fits the budget. Challenger LLMs stay reachable to enthusiasts through the ADR-0001 custom-model slot; if the incumbent later measures inadequate, one becomes the candidate then, on a scale that works.

- **Ship a fast NMT (OPUS-MT / ML Kit) as the default and make the LLM opt-in.** Rejected. It defaults every user out of the cross-panel coherence ADR-0002 was built to deliver. Speed is not worth defaulting the product's whole reason for the redesign to off.

- **Fine-tune CAT-Translate now.** Rejected. The number that looked like "CAT-Translate is bad at manga" was largely harness misuse: a missing chat template and `parse_special=false` (fixed in #35's grilling) moved readability 4.149 → 2.587 and latency 3.5× from prompt formatting alone, and the residual is still measured **per line**, never through ADR-0002's page-level call. Fine-tuning against that would train the model to fix a measurement artifact. Deferred, not abandoned — see the trigger below.

- **Delete OPUS-MT.** Rejected. The engine is fully built — ONNX encoder/decoder with KV-cache and a DJL Marian tokenizer, landed 2026-08-05 — self-contained at ~115MB with no Play Services dependency, and firsthand it translates better than ML Kit. The only thing between it and running is one missing native library ([#14](https://github.com/artsaraiva/yomu/issues/14): DJL ships no `arm64-v8a` `libdjl_tokenizer.so`). Deleting better-than-ML-Kit code over one `.so` is the wrong trade.

## Consequences

**The curated default is CAT-Translate-0.8b.** 0.8b fits the mid-range 6–8 GB budget from [#26](https://github.com/artsaraiva/yomu/issues/26); above ~1.5B params, weights alone consume the entire budget-tier RAM. The default targets that tier — enthusiasts with more headroom sideload a larger GGUF into the translation slot (ADR-0001), so the default does not have to serve them.

**The default is contingent on the ADR-0002 batch build.** #58's pre-ADR-0002 baseline has CAT-Translate **failing the ADR-0004 gate at 0.952 Japanese-residue** — but that is scaffolding, not the model: `LlamaTranslationBridge.MAX_TOKENS=64` (our constant; `n_ctx` is 2048) truncates the whole-page numbered prompt on 11 of 17 pages, and the parser substitutes the Japanese source for every line it cannot read back. Shipping this default assumes the ADR-0002 batch build removes that artifact. If, after that build lands and the page-level and [ADR-0006](0006-coherence-gate-contract.md) contrastive gates actually run, CAT-Translate-0.8b still measures inadequate, the fallback is already defined: fine-tune (below) or promote a challenger LLM from the sideload slot.

**ML Kit is the always-available floor.** Play Services JA→EN, zero app footprint, readability 0.937 (#58 floor, unmeasured on the #58 device only because the Play model was not downloaded there). It is the floor that works on any device with no setup.

**OPUS-MT is the preferred self-contained floor, gated on #14.** Better than ML Kit firsthand, no Play Services dependency, ~115MB. It becomes selectable once #14 provides the `arm64-v8a` tokenizer library. Until then it cannot load — and Settings currently **recommends** it in the LLM panel, which is a live bug (it points users at a non-loadable engine). Both floors run the per-line floor only; neither ever competes with the LLM default.

**Fine-tuning has a named trigger.** Fine-tune the default **only if**, after the ADR-0002 batch build and once the page-level and ADR-0006 gates run, CAT-Translate-0.8b measures inadequate on the shipped architecture. Not now (the current number is an artifact), not never (the honest measurement does not exist yet).

**Post-map build contract.** This is the last decision in the [#25](https://github.com/artsaraiva/yomu/issues/25) map; resolving it records no new tickets but hands the build effort a named list, in order:

1. **ADR-0002 batch build** — critical path. Token budget (`MAX_TOKENS`), structured id-keyed output, and a parse that does not silently substitute source. The default cannot pass its gates until this lands.
2. **Pull OPUS-MT's Settings recommendation** — it cannot load today; recommending it is a live bug.
3. **#14** — `arm64-v8a` tokenizer `.so`, to bring OPUS-MT up as the preferred floor.
4. **Confirmatory page-level measurement** — run the built default through ADR-0004's page-level call and ADR-0006's contrastive gate; the result feeds the fine-tune trigger.
5. **`product-spec.md:475–510` rewrite** — the "Standard / Enhanced / Premium translation model" ladder is stale (overturned by #32); outside this map.
