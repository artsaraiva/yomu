# The curated catalog gains an Experimental tier, a ~9B ceiling, Jinja-rendered prompts and per-deliverable sampling

**Status:** accepted, 2026-09-17. Decides [#280](https://github.com/artsaraiva/yomu/issues/280), recording the decisions of spec [#279](https://github.com/artsaraiva/yomu/issues/279). Revises in part [ADR-0001](0001-custom-model-permissiveness.md), [ADR-0009](0009-selectable-translation-model-set.md), [ADR-0014](0014-quantization-deliverables-and-revision-pinning.md) and [ADR-0015](0015-quality-belongs-to-the-model.md).

The curated catalog grows from 3 deliverables to 19, up to about 9B parameters. Every new deliverable joins as **Experimental**: it is chosen on desk research, shown to the reader with an Experimental tag, and stays in that state until a per-deliverable phone check either confirms it, switches it to per-line, or removes it. The evidence is [`docs/research/phone-translation-llm-candidates.md`](../research/phone-translation-llm-candidates.md) (desk research, 2026-09-16, with a 9B-tier and uncensored addendum). Nothing in it was run on a phone, and every vendor benchmark it quotes is self-reported.

Eight rules follow.

## Experimental is a temporary state with three exits

A deliverable is Experimental when Yomu has not yet run it on the reference phone. The tag says exactly that to the reader: not "beta", not "may be removed", but "Yomu has not tested this on a phone".

The graduation rule has three outcomes, one of which must be taken:

- **Confirmed.** The phone check shows it loads, translates and is not pathologically slow. The tag comes off.
- **Switched to per-line.** It does not hold the [ADR-0013](0013-grammar-constrained-page-level-batch.md) grammar-constrained page schema. It keeps its place with `idKeyedBatch = false` and the tag comes off.
- **Removed.** It fails to load, refuses, or produces garbage. The entry and its registry row are deleted.

Each check is its own ticket ([#291](https://github.com/artsaraiva/yomu/issues/291)–[#295](https://github.com/artsaraiva/yomu/issues/295)) and runs the existing report-only speed benchmark, per ADR-0015. Nothing passes or fails on the numbers; a person reads output and takes one of the three exits.

The default deliverable is never Experimental. A fresh install keeps the behaviour Yomu has tested.

## The ceiling is about 9B, and the existing fit gate is what enforces it

The catalog admits nothing above about 9B parameters. Nothing new is written to enforce that: for every entry but the default, `LlmModelCatalog.canRunOnDevice` already computes `sizeBytes + RESIDENT_OVERHEAD_BYTES + kvCacheBytesPerToken × contextTokens ≤ totalMem / 100 × percent`, at `RESIDENT_OVERHEAD_BYTES = 800 MiB` and `DEFAULT_FIT_BUDGET_PERCENT = 60`. At that budget a 9B Q4_K_M deliverable needs about 6.2 GiB, which no 8 GiB phone passes and a 12 GiB phone does. The ceiling is therefore a curation rule for what gets a catalog entry, and the device rule stays the one gate the app already has.

**The gate has one exemption: the default.** `canRunOnDevice` returns early for the default, which must stay usable on the mid-range floor, so the ceiling binds the default through curation alone. That is not a new risk — the default is the one entry that is never Experimental and never picked without a phone check.

There used to be a second one. `ModelSlotSelection.fits` short-circuited to `true` when `budget.totalMemBytes <= 0` (`ModelManager.deviceTotalMemBytes()` returns `0` when `ActivityManager` is unavailable), which was harmless on a 3-deliverable catalog but was the one path that could hand a 12 GiB-class deliverable to an 8 GB phone. [#298](https://github.com/artsaraiva/yomu/issues/298) closed it: an unreadable memory figure now leaves only the default pickable, so a device Yomu cannot measure still has a working translator and is never offered a deliverable [ADR-0014](0014-quantization-deliverables-and-revision-pinning.md)'s disabled-with-a-reason rule would refuse.

Rejected: a device-tier enum, or a "Recommended for this phone" badge. Both invent a ranking Yomu does not have. The fit gate is a boolean about memory, and until phone checks produce a measured ranking there is nothing to recommend from.

The gate does not count Qwen3.5's DeltaNet recurrent state (about 19 MiB at 0.8B/2B, 50 MiB at 4B/9B). That underestimate is accepted; it is small against the 800 MiB overhead term.

## Apache-2.0 or MIT only

A curated deliverable must be Apache-2.0 or MIT. This is stricter than ADR-0014's "a deliverable Yomu cannot redistribute is not curated", and it is stricter on purpose: the licences that fail are the ones with conditions Yomu would pass on to the reader — a "Built with X" notice, a Notice file, an acceptable-use policy, a MAU clause, registration for commercial use, or Chinese governing law.

This is what rules out Llama 3.2, GLM-Edge and TranslateGemma, and it is what lets **Gemma back in**: Gemma 4 is released under Apache-2.0, not the Gemma Terms of Use that excluded every earlier Gemma.

## Uncensored deliverables are curated, labelled, and listed normally

Five huihui-ai abliterated deliverables (Qwen3.5 2B/4B/9B, Gemma 4 E2B/E4B) join the catalog labelled "Uncensored". They appear in the normal picker, not behind a switch or a warning dialog.

A reader translating mature manga hits models that refuse or soften the text, and the only alternative Yomu offers today is the unsupported custom slot (ADR-0001). Curating the deliverable means it is pinned, checksummed and downloadable like any other; labelling it means nobody picks one by accident.

They are Experimental like everything else here, and their phone check is the strictest: every huihui card says the method is "a crude, proof-of-concept implementation to remove refusals from an LLM model", and none publishes a quality evaluation. [#295](https://github.com/artsaraiva/yomu/issues/295) compares each against its base deliverable on both axes — it is kept only if it refuses less *and* translates as well.

## The speed label is derived from file size, and Slow asks first

Each deliverable shows Fast, Medium or Slow, computed from its size: Fast at 1.5 GB or less, Medium at 3.5 GB or less, Slow above. Picking a Slow deliverable that fits the device shows a confirmation before the download starts; cancelling leaves the current selection.

The thresholds are the same on every device, and the label is derived rather than stored so it cannot drift from the size it describes. File size is a poor proxy for tokens per second — it ignores architecture, and Qwen3.5's hybrid attention is exactly the case it will get wrong — but it is the only per-deliverable number Yomu has before a phone check, and the alternative is downloading gigabytes to find out. A reader who has waited through a Slow page should be able to tell a long wait from a hang.

Rejected: no label until the phone checks measure one. That leaves the reader with nothing on the decision that costs them the most, and the checks are per-deliverable tickets that land over time.

The warning is a confirmation, not a block. A reader may always continue.

## Sampling defaults belong to the deliverable; the reader's changes are global and win

`GenerationParams` gains a presence penalty, off by default. Each catalog entry carries its maker's recommended sampling as a `GenerationParams` value. The effective profile is the selected deliverable's defaults with each field the reader has saved in Advanced settings laid over the top. `GenerationProfileStore`'s "absent means the built-in default" rule becomes "absent means the deliverable's default", so Reset returns every deliverable to its maker's values.

**This reverses [#139](https://github.com/artsaraiva/yomu/issues/139)'s "no per-model override before a measurement forces it".** The measurement is the catalog itself: the makers' recommendations for these 19 deliverables span temperature 1.0 to "below 0.1", top-k 20 to 64, and presence penalty 2.0 to none. A single global profile cannot express any of them, so shipping one means shipping most of the catalog mis-sampled. The rule was set when the catalog was three models whose recommendations were close enough not to matter.

The reader's settings stay **global, not per model**. Their change applies to every deliverable, so switching models never silently undoes it. A per-model settings screen is out of scope; nothing has yet shown a reader wants to tune two models differently.

## Prompts are rendered by llama.cpp's Jinja engine, render-only, with thinking explicitly off

Prompt formatting moves from `llama_chat_apply_template` — the heuristic, name-guessing path — to the `common/chat` Jinja engine, which renders each GGUF's own `tokenizer.chat_template`.

The heuristic path cannot serve this catalog. It does not recognise Gemma 4's `<|turn>` format, so Yomu would prompt it in CAT-Translate's format. It misdetects Hy-MT2 as `hunyuan-vl`. It has no way to turn Qwen3.5's thinking off. It gives Ministral 3 no system message, and Ministral 3's own template then injects Mistral's Le Chat assistant prompt.

**Yomu uses the engine to render only.** No tools, no JSON schema, no output parsing, and none of the handlers' own grammars. [ADR-0013](0013-grammar-constrained-page-level-batch.md)'s grammar-constrained page call is untouched: Yomu's GBNF still constrains generation, and Yomu still parses the reply. The engine produces a prompt string and nothing else.

**`enable_thinking` is set to `false` explicitly.** The vendored `common/chat.h` defaults it to `true`, and both Qwen3.5 and Gemma 4 open a thinking block when it is. Hidden reasoning inside a page call costs latency and breaks the grammar.

If a GGUF carries no template, or rendering fails, the existing CAT-format fallback stays. The render step is extracted into a unit with no JNI dependency, so a template regression is caught by a host test against a fixture template string rather than by a phone.

The one cost is binary size: linking `common` into the native library grows it, and that increase is measured before the switch commits.

## A catalog entry may carry a system message

An entry gains an optional system message, empty by default. When it is empty, the prompt is what it is today; when it is set, it is rendered as the template's system turn.

Ministral 3 3B and 8B set it, because their template injects Mistral's chat-assistant prompt when nothing is sent. No other entry does: CAT-Translate and Hy-MT2 were trained without one, and the general instruct models take Yomu's instructions in the user turn.

Rejected: one system message for every deliverable. That changes the prompt for the models Yomu has already tested, including the default, to fix one family's template.

## Consequences

**The default does not move, and CAT-Translate is not removed.** Qwen2.5-1.5B-Instruct stays the default under [ADR-0010](0010-qwen-default-cat-demoted-to-floor.md). Whether a newer deliverable takes its place, and whether the CAT entries go, is decided by phone evidence in a later ADR. Self-reported benchmarks do not move a default.

**Qwen2.5 and CAT-Translate readers see no change.** The Jinja switch must render byte-identical prompts for both, and that is a host test, not a hope.

**The registry grows one LLM row per new entry.** The invariant that LLM rows are exactly the catalog entries holds; each row is pinned to a Hugging Face commit with a sha256, per ADR-0014, so a re-upload fails loudly.

**Three ADR conflicts are resolved here rather than left implicit.** ADR-0015's "choosing a model is a short ADR, weighing a manual look at output" is revised: membership now precedes the look, and the Experimental tag is what makes that honest to the reader. ADR-0009's evidence-gated membership and ADR-0014's licence rule are revised as set out above. ADR-0001's custom slot stays open and unsupported, but its prompting changes with everything else's: a sideloaded GGUF carrying a template is now rendered in its own format instead of guessed at.

**The uncensored entries are a product surface, not only a technical one.** Whether the Play Store listing needs to say so is out of scope here.

**Open questions the research doc leaves.** What `totalMem` a real "12 GB" phone reports is unverified, so the 9B tier's device rule is an estimate until a phone check reads one. Hy-MT2's card and its own `generation_config.json` disagree on top-p (0.6 vs 0.8); the card wins. Hy-MT2's maker repetition penalty of 1.05 conflicts with [#153](https://github.com/artsaraiva/yomu/issues/153)'s finding that a repeat penalty harms intentional repetition in manga dialogue, so [#294](https://github.com/artsaraiva/yomu/issues/294) looks specifically at stammers and laughter.

**Out of scope.** The custom-GGUF slot, the faster CPU build, model families and largest-fitting-sibling selection, per-model Advanced settings, `swa_full = false`, deliverables above ~9B, and any automatic selection by device.
