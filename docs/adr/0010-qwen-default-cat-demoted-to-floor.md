# Qwen2.5-1.5B-Instruct becomes the curated default; CAT-Translate-0.8b is demoted to the low-storage floor

**Status:** accepted, 2026-08-18. Exercises the promote trigger [ADR-0009](0009-selectable-translation-model-set.md) named ("a shortlist model proves clearly better *and* safe on the mid-range floor → promoting it to default is a separate decision"). This is that decision.

The curated default in the translation slot moves from **CAT-Translate-0.8b** to **Qwen2.5-1.5B-Instruct**. CAT-Translate-0.8b stays in the catalog as the **low-storage floor** — the selectable option for devices that cannot spare the ~1.0 GB Qwen download or the RAM to run it — never the default, never the quality pick. OPUS-MT and ML Kit remain the no-LLM floors ([ADR-0008](0008-translation-model-selection.md)); the custom-GGUF slot ([ADR-0001](0001-custom-model-permissiveness.md)) and the curated selectable set ([ADR-0009](0009-selectable-translation-model-set.md)) both stand.

This is decided on measured phone evidence, which is the condition ADR-0008 and ADR-0009 pre-registered for changing the default. It was never changeable on emulator quality alone.

## Why now — the phone-confirmation run closed the one open question

ADR-0009 kept 0.8b as default for a single reason: the bake-off leaders were ranked on the **emulator**, where latency and RAM are host artifacts, so on-device viability was unconfirmed. "The default is the one thing that must be safe on the mid-range floor, so it changes last, on phone evidence." The [#72](https://github.com/artsaraiva/yomu/issues/72) confirmation run (reference phone: Galaxy S23, 8 GB, 2026-08-18) supplied that evidence, recorded in [`eval/challenger-llm-bakeoff-84.md`](../../eval/challenger-llm-bakeoff-84.md):

| Engine | JP-residue | Coverage | Readability | Latency/page | Peak PSS | On phone |
|---|---|---|---|---|---|---|
| **Qwen2.5-1.5B** | **0.102** | 100 % (17/17) | 0.969 | **~15 s** | 2.0 GB | fits, stable |
| CAT-0.8b (was default) | 0.388 | 100 % | 0.843 | ~14 s | — | fits |
| gemma-2-2b-it | 0.010 | 100 % (11/17) | 1.122 | ~35 s | 2.75 GB | too slow |

Qwen2.5-1.5B is **~4× cleaner on Japanese residue** than 0.8b (0.102 vs 0.388), at **parity on speed** (~15 s vs ~14 s per page) and **inside the same budget tier** (2.0 GB peak on an 8 GB device). The property that made 0.8b the safe default — runs on the mid-range floor at acceptable latency — is now equally true of a model that translates decisively better. So 0.8b's only surviving advantage is its **download size** (0.5 GB vs 1.0 GB). That is a floor's advantage, not a default's.

## Considered Options

- **Keep 0.8b as default, Qwen selectable only (ADR-0009 status quo).** Rejected. ADR-0009 held 0.8b only pending phone confirmation; that confirmation now favours Qwen on every axis that matters for a default except a 0.5 GB size gap. Keeping 0.8b as the shipped default would default the majority of capable-device users — who never open a picker — into 4× the residue for no speed or budget saving. The reason to wait (no phone evidence) is spent.

- **Promote gemma-2-2b-it, the outright quality winner (residue 0.010).** Rejected as the *default*. On the phone gemma runs ~35 s/page — 2.3× Qwen's latency — and completed only 11/17 pages inside its time slice; its 2.75 GB peak is tighter on the 8 GB floor. It is also Gemma-Terms-licensed, so Yomu cannot host-redistribute it — a default must be a tier-1 hosted model (see below). gemma stays a **selectable, HF-authenticated** entry for users who accept the latency for the best quality; it is not defaultable.

- **Promote Qwen but delete 0.8b.** Rejected. 0.8b is the smallest viable LLM at 0.5 GB / lowest RAM; on a device that cannot fit Qwen's 1.0 GB download or 2.0 GB working set, 0.8b is still a real coherent-page LLM and a large step above the OPUS-MT/ML Kit per-line floors. Keeping it as the low-storage floor costs nothing (it already ships) and preserves the LLM path for the tier below Qwen's.

- **Qwen becomes default; 0.8b demoted to the low-storage floor. (Chosen.)** Captures the measured quality win for every user by default, keeps the mid-range budget and latency 0.8b guaranteed, and retains a smaller LLM fallback for constrained devices. The only cost is the extra 0.5 GB in the default download, which is the cheap side of this trade.

## Consequences

**A default must be tier-1 hostable, and Qwen is.** Qwen2.5-1.5B-Instruct is **Apache-2.0**, so it lands in ADR-0009's tier 1 (Yomu-hosted: pinned URL + checksum + size) and can be the shipped default download with no licence friction. This is not incidental — a default is downloaded for every user without a per-model consent step, so it *cannot* be a tier-2 HF-authenticated model like gemma. Licence is why the quality winner (gemma) cannot be the default and the on-device winner (Qwen) can.

**The default engine constant changes.** `TranslationEngineType.LLM` currently hardcodes the 0.8b path (`provideLlamaModelPath` → `TRANSLATION_MODEL_4BIT`). That constant now points to the Qwen GGUF. The 0.8b entry stays in the `ModelManager`/`Constants` catalog as a selectable floor row, not the default. This flip does not depend on the [#90](https://github.com/artsaraiva/yomu/issues/90) selection-layer build — the *default* moves now; the picker that lets a user choose among LLM models is still the separate #90 work.

**The new default is already measured on the shipped architecture.** The reason 0.8b's original number was distrusted (ADR-0008: a `MAX_TOKENS=64` truncation artifact on the per-line path) does not apply here. Qwen's 0.102 was scored through the [ADR-0004](0004-translation-eval-contract.md) page-level id-keyed batch path with `supportsIdKeyedBatch() = true` — the same call the app ships — so no scaffolding-artifact caveat attaches to the default this time.

**The fine-tune trigger re-anchors to Qwen.** ADR-0008's "fine-tune the default only if it measures inadequate on the shipped page-level architecture" now reads against Qwen2.5-1.5B, not 0.8b. Qwen passes the bar that condition was watching (measured-best on the phone under the budget ceiling), so no fine-tune is triggered; the trigger stays armed against the new default.

**Floors and slots are untouched.** OPUS-MT (gated on [#14](https://github.com/artsaraiva/yomu/issues/14)) and ML Kit remain the no-LLM per-line floors. The custom-GGUF sideload slot (ADR-0001) and the curated selectable shortlist (ADR-0009) stand as written — this ADR only moves which curated entry is the default and reclassifies 0.8b's role within the set.

**Supersedes in part:** ADR-0008's "the curated default is CAT-Translate-0.8b" and ADR-0009's "the 0.8b stays the safe default for every device / the default still changes only on the ADR-0008 trigger." The default is now Qwen2.5-1.5B-Instruct; 0.8b is the low-storage floor. The rest of both ADRs — the selectable-set structure, the three delivery tiers, the floors, the custom slot, the fine-tune trigger (now anchored to Qwen) — stands.
