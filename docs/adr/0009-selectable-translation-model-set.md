# Yomu offers a small curated set of selectable translation models, gated on device capability, revising ADR-0008's single-default rule

The [#84](https://github.com/artsaraiva/yomu/issues/84) bake-off supplied the measured ranking [ADR-0008](0008-translation-model-selection.md) said did not exist, and it changes the answer. On the [ADR-0004](0004-translation-eval-contract.md) page-level path, several models beat the shipped CAT-Translate-0.8b, and the winners are **not** the CAT siblings that ADR-0008 and [#72](https://github.com/artsaraiva/yomu/issues/72) presumed — a general 2B instruct model leaves ~12× less Japanese residue than the 1.4b specialist (see [`eval/challenger-llm-bakeoff-84.md`](../../eval/challenger-llm-bakeoff-84.md)).

So Yomu moves from **one curated default** to **one curated default plus a small curated shortlist the user may select**, gated on device capability. The 0.8b stays the safe default for every device; the shortlist surfaces measured-better alternatives only where the device budget allows and only for models confirmed on the reference hardware. This is a deliberate, evidence-driven revision of ADR-0008's "one curated default, not a per-tier model ladder" — not a reversal of the reasoning that produced it. That rule was set explicitly because the eval *could not rank engines* ([#58](https://github.com/artsaraiva/yomu/issues/58)); now it can, which is the precise condition ADR-0008 named for reopening it.

This does not touch [ADR-0001](0001-custom-model-permissiveness.md): the open custom-GGUF slot stays. The shortlist is curated models Yomu supports; the custom slot remains the unsupported escape hatch for everything else.

## What the bake-off measured (reference: emulator quality, phone latency where noted)

| Model | JP-residue (gate 0) | Readability | Q4 size | Note |
|---|---|---|---|---|
| gemma-2-2b-it | 0.027 | 1.11 | 1.7 GB | Best quality; fits the 8 GB budget |
| Qwen2.5-1.5B-Instruct | 0.102 | 1.01 | 1.0 GB | Excellent; smallest/fastest |
| TranslateGemma-4B | 0.028 | 1.08 | 2.5 GB | Great quality but ~52 s/page — likely too slow on-device |
| CAT-Translate-1.4b | 0.32 | 0.83 | 0.9 GB | Best CAT sibling; now outclassed |
| CAT-Translate-0.8b | 0.39 | 0.84 | 0.5 GB | Shipped default / baseline |

No model passes the gate (residue is never exactly 0), so "pass the ADR-0004 gate" cannot be the promotion bar as #72 wrote it — the bar becomes **measured-best on the phone under a device-budget ceiling**, not a hard gate pass.

## Considered Options

- **Keep one curated default; alternatives only via the ADR-0001 custom slot (status quo).** Rejected. It throws away a measured, large-margin quality win (residue 0.027 vs the default's 0.39) for the majority of capable-device users, who will never hand-sideload a GGUF. The reason the ladder was refused — no measured ranking — no longer holds.

- **Ship the new best model as the *new single default*, no picker.** Rejected for now. The winners are ranked on emulator *quality*; on-device *latency and RAM* are unconfirmed, and the reference phone could not even load a 7B. Promoting a new default before phone confirmation repeats the mistake ADR-0008 warned against (deciding on the wrong scale). The default is the one thing that must be safe on the mid-range floor, so it changes last, on phone evidence.

- **Full open ladder / per-tier auto-selection by RAM.** Rejected. That is the [#32](https://github.com/artsaraiva/yomu/issues/32)-closed "Standard/Enhanced/Premium" ladder in a new costume: it multiplies the support surface and makes the shipped experience non-deterministic across devices. A *short, curated, user-chosen* shortlist is not that — the set is small, every entry is Yomu-supported, and the default is unchanged.

- **A small curated selectable set, default unchanged, gated on device budget. (Chosen.)** Captures the win, bounds the support surface to a handful of vetted models, and keeps the default safe. The picker is opt-in; picking nothing keeps the 0.8b.

## How models reach the user — three tiers

The selectable set is not one delivery mechanism but three, chosen by licence and support burden:

1. **Hosted (Yomu-served).** Permissively-licensed models Yomu downloads and supports directly — the existing `ModelManager` flow (pinned URL + checksum + size). Only licences that let Yomu redistribute freely qualify: **Qwen2.5-1.5B is Apache-2.0** and fits here; the CAT-Translate default is already here. This is the default download path for a curated entry whose licence allows it.

2. **Yomu-tested, HuggingFace-authenticated.** Models Yomu has measured and recommends but *cannot* freely redistribute — chiefly **Gemma** (Gemma Terms of Use). The user signs into their own HF account (see the ADR-0001 revision) and Yomu pulls the GGUF through the HF API under the user's credentials. Because the user's own account accepted the model's gate, Yomu is a conduit, not a redistributor, and the licence-pass-through burden that would otherwise fall on Yomu's ToS moves to where the gate was accepted. Yomu still shows a generic "governed by its own licence" notice — cheap insurance, not a redistribution obligation.

3. **Open, unsupported.** Any GGUF the user pulls via HF auth (or the local file picker), labelled "Custom — unsupported" per [ADR-0001](0001-custom-model-permissiveness.md). This is ADR-0001's slot, now extended from local-file-only to also allow the HF-authenticated remote source. Not curated, not tested, the user owns the outcome.

The first two are the curated selectable set this ADR is about; the third is the enthusiast escape hatch. A given curated model lands in tier 1 or 2 purely by whether its licence lets Yomu host it.

## Consequences

**The shortlist is evidence-gated, not aspirational.** A model earns a slot only after it is confirmed on the reference phone (loads within the RAM budget, page-level latency acceptable, quality holds). Today that means: 0.8b (default), and the leaders **pending phone confirmation** — likely `gemma-2-2b-it` and `Qwen2.5-1.5B`. `TranslateGemma-4B`'s ~52 s/page (emulator) will probably exclude it; `CAT-1.4b` stays as a fallback. The final set is decided by the confirmation run in [#72](https://github.com/artsaraiva/yomu/issues/72), not by this ADR.

**Each shortlist entry needs a per-model capability gate.** ADR-0001 already parses GGUF footprint and warns on OOM risk; the shortlist reuses that math to *hide or disable* an entry the device cannot run, rather than merely warn — a curated entry that silently kills the app is worse than an absent one. The 0.8b default is never gated out.

**Licence sorts a model into a tier; it does not exclude it.** Making a model "downloadable in the UI" means either Yomu redistributes it (tier 1) or Yomu fetches it under the user's HF credentials (tier 2). Qwen2.5 is Apache-2.0 → tier 1 (hosted). **Gemma** carries the Gemma Terms of Use (constrains redistribution) → tier 2 (HF-authenticated), which is the point of the three-tier split: a licence Yomu cannot redistribute no longer means "not offered," it means "offered through the user's own HF account." CAT-Translate's licence (cyberagent) needs the same check to place it. The blocking prerequisite is *classification*, not clearance — no entry ships in tier 1 until its licence is confirmed to permit Yomu redistribution; anything that does not goes to tier 2.

**Tier 2 needs HF auth infrastructure.** OAuth against HuggingFace, secure token storage (Android Keystore), a resumable download with integrity check (HF LFS `oid` = sha256), and graceful handling of a gated-model 401 (prompt the user to accept the model's terms on HF). This is real work — the "download manager burden" [ADR-0001](0001-custom-model-permissiveness.md) originally cited — tracked in the build ticket, not this ADR. It is shared with tier 3 (same auth + download path, different curation).

**Selection must exist in code first — it does not today.** `TranslationEngineType` is `ML_KIT / OPUS_MT / LLM`, and the `LLM` branch hardcodes the 0.8b path (`provideLlamaModelPath` → `TRANSLATION_MODEL_4BIT`). There is no mechanism to pick among LLM models. The `ModelManager`/`Constants` catalog entries added during the #84 bake-off surface as download rows but are inert — not selectable, not loaded. Wiring a per-model selection layer under `LLM` is the build work, tracked separately.

**The default still changes only on the ADR-0008 trigger.** This ADR adds a *picker*; it does not promote a new default. If a shortlist model later proves clearly better *and* safe on the mid-range floor, promoting it to default is a separate decision under ADR-0008's fine-tune/promote trigger.

**Supersedes in part:** ADR-0008's "Yomu ships **one** curated default, not a per-tier model ladder" clause. The rest of ADR-0008 (0.8b as default, OPUS-MT/ML Kit as floors, the fine-tune trigger) stands.
