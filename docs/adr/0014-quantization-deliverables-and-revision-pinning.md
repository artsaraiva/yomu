# The curated catalog lists deliverables, not models; it pins bytes, not commits; and it has one tier, not three

> **Supersedes in part [ADR-0009](0009-selectable-translation-model-set.md)** (the three-tier delivery split) and **retracts the HuggingFace-authenticated carve-out in [ADR-0001](0001-custom-model-permissiveness.md)**. ADR-0009's selectable-set core — a curated shortlist, a default that is never gated out, evidence-gated membership, the open custom slot — stands unchanged.

[#140](https://github.com/artsaraiva/yomu/issues/140) asked whether the curated catalog gains a quantization matrix and a pinned revision, following koharu's descriptor (2–7 quantizations per model, plus `repository` and a `revision` git sha). Four rules answer it, and a fifth falls out of them.

## Quantization is lossy, so the matrix exists to avoid *nothing*, not to get something for free

Q4_K_M → Q3_K_M is roughly a 20% size cut and a real quality cut; it is not the same model in less RAM. The case the catalog loses today is narrower than "offer every quant": Hunyuan-7B's Q4 is OOM-killed on an 8 GB phone while its Q3 loads, so `canRunOnDevice`'s boolean turns "worse but usable" into "absent". The choice being preserved is *degraded or nothing*, never *free*.

This bounds how much the feature is ever worth. A smaller model at a healthy quant frequently beats a larger model at a desperate one, and the [#84](https://github.com/artsaraiva/yomu/issues/84) ranking already puts Qwen2.5-1.5B at Q4 near the top of the shortlist. A quant ladder is a tool for the specific model whose good quant does not fit, not a general quality dial.

## An entry is a deliverable, not a model

`LlmModelOption` keeps naming exactly one GGUF at one quantization. A model published at several quantizations becomes several sibling entries sharing an optional `familyId` — **not** a `quants: List<Quant>` field on one entry.

Rejected: the koharu-shaped matrix. It changes `LlmModelOption`'s shape, forces the persisted model id and the download flow to both name a quant, and reworks `ModelManager`, all to express a relation the flat list already expresses with one nullable field. Multi-quant is a *selection rule over the list*, not a new data shape.

A consequence worth stating: a model that is redistributable at one quantization and gated at another — the case #140 flagged as homeless — stops being a modelling problem. Two licences means two deliverables means two rows, each carrying its own `licence`.

## The fit budget is measured, not parsed

`sizeBytes + RESIDENT_OVERHEAD_BYTES ≤ totalMem × USABLE_RAM_FRACTION` stays. The `ponytail:` note offering ADR-0001's exact GGUF math as the upgrade is **retargeted**: that math parses a GGUF header, which requires the file already on disk — exactly what is absent when deciding whether to offer a download. It cannot serve this gate at any resolution.

`sizeBytes` is already exact and already per-quantization, so the only imprecision is the 800 MB / 0.6 pair, and imprecise constants are fixed by measuring devices, not by parsing files. The named upgrade is a per-entry `peakRssBytes` recorded from a real load, with the formula as the fallback for entries that have never been measured.

`canRunOnDevice` becomes selection rather than a predicate: offer the largest-fitting sibling of a family. When no sibling fits, the family shows **disabled with the reason**, not hidden. ADR-0009 chose hide-*or*-disable to stop a curated entry killing the app; silently hiding satisfies that letter while producing the confusion that opened #140. The default is still never gated out.

## Pinning is content-addressed, with an immutable URL

`ModelEntity` keeps its pinned URL plus sha256 checksum (the HuggingFace LFS `oid` **is** the file's sha256). A revision sha pins which commit; a content sha256 pins which bytes, and is strictly stronger.

What changes is that the URLs say `/resolve/main/`, which is mutable: a re-upload turns into a checksum failure with no diagnosable cause. Each becomes `/resolve/<revision-sha>/`, verified against the recorded checksum through the HF API before it is committed. A mismatch means the recorded checksum is *already* stale and is its own fix, never a silent overwrite.

Rejected: structured `repository` / `revision` / `filename` fields. Nothing but the downloader would read them; the settings screen already recovers a model page by splitting the URL on `/resolve/`.

## There is one tier, and the HF-authenticated path is deleted

ADR-0009's tier 2 — Yomu-tested models pulled under the user's own HuggingFace credentials — has no member, no identified candidate, and never worked. There is no licence-clean gated GGUF for Gemma or TranslateGemma at a usable size: the gated repo ships only a 10.5 GB f32 file, and the small quants live on *ungated* re-hosts where the gate, hence the entire "the user's own account accepted the terms" premise, cannot apply. And `yomu://auth/callback` has no `intent-filter` in the manifest, so the OAuth redirect could never reach the app: this is unfinished code, not working code kept warm.

So `translation/hf/`, `ModelManager.downloadHfModel`, the settings tier-2 branch, and `LlmModelTier.HF_AUTH` are deleted. The registered OAuth client stays, and so revival is a lookup rather than an excavation: the path was deleted in `16011a3f3246c0b92b073370a3da72951ecc83fe` (#187, squash-merged as #208). Check out its parent for the auth manager, token store and downloader. `LlmModelTier.HF_AUTH` itself outlived that commit and went with the tier collapse (#189).

One tier remains: hosted and redistributable. **A deliverable Yomu cannot redistribute is not curated.**

**The cost, stated plainly:** ADR-0001's revision note relaxed "no URL loading" *specifically* for the HF-authenticated path, so deleting it returns the custom slot to local-file-picker-only. A gated GGUF now reaches the device by manual sideload. This was weighed and accepted: the users who sideload a custom GGUF are the users who can `adb push`, and keeping an OAuth flow alive to serve the enthusiast hatch costs more than it returns. Reviving the path for a real gated model later would mean finishing it against that model's actual gate anyway.

## What gets built

The selection rule above has no user today: no family in the catalog has two quantizations. **`familyId` and largest-fitting-sibling selection are not built until one does** — this ADR records the rule so that its absence reads as deliberate, not as a missing implementation.

What does get built, as separate issues: the HF deletion, the revision-sha pinning, and a `ModelManager` prune. On the last: there are ten `ModelType.LLM` rows against three catalog entries, and that shadow catalog is how "Gemma is offered but unreachable" became possible. Rows exist to serve catalog entries. The benchmark-only models' URLs and checksums move into an androidTest fixture beside `EngineBenchmarkTest`, which selects candidates by filename and never read those ids.

Evidence the rule is already real rather than hypothetical: `Constants.HUNYUAN_MT_MODEL` is `hunyuan_mt_7b_q3_k_m.gguf`. The downgrade this ADR describes was made by hand, silently, in a constant — which is exactly the decision a catalog should be making in the open.
