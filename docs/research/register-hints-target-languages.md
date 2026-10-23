# Register hints for non-English targets (research #363)

Research and desktop runs, 2026-10-21. The #350 note ([jev-bubble-class-hints.md](jev-bubble-class-hints.md)) found one positive sign among its hand-written hints: with a French target, register marks made rough lines use *tu* consistently. That run had two flaws. Every mark leaked into the reply, and it tried one wording on two pages. Now that #337 wires the reader's target language into the page prompt, this note asks the question properly. Does a per-bubble register label (polite, casual or rough) visibly improve Qwen2.5-1.5B's T–V choices (*tu*/*vous*, *tú*/*usted*, *du*/*Sie*, *você*/*o senhor*) once the reply cannot carry the label?

**What was run, and on what.**

- **Nothing ran on a phone or an emulator.** Every run is desktop CPU. The emulator OCR used below is #350's, already captured.
- **Hardware:** Apple M4 Pro, 48 GiB, macOS 26.7, CPU only, 4 threads.
- **llama.cpp:** Yomu's pin `7fe450e`, fetched fresh and built locally with the Android SDK's CMake 3.22.1 and Ninja. It used the app's CMake options (`LLAMA_BUILD_COMMON=ON`, `GGML_METAL=OFF`, `LLAMA_BLAS=OFF`, tools, tests and examples off; `ml/src/main/cpp/CMakeLists.txt:5-18`), with two desktop differences: `GGML_NATIVE` was left on and `GGML_BACKEND_DL` off. The repository's `ml/llama.cpp` submodule was not touched.
- **Model:** the app's pinned Qwen2.5-1.5B-Instruct Q4_K_M, `bartowski/Qwen2.5-1.5B-Instruct-GGUF@9eadc66`, 986,048,768 B, sha256 `1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370`. That matches `ModelManager.kt:121-122` and `Constants.QWEN25_15B_SIZE`.
- **Harness:** not `llama-server`. It is a small C++ program that compiles the app's own `chat_prompt.cpp`, `grammar_sampler.cpp` and `prompt_budget.h`, copied unchanged from `origin/main` `f44f0cc`. A five-line shim stands in for `<android/log.h>`. Its `main` repeats `nativeLoadModel` and `nativeGenerate` from `llama_jni.cpp` line for line, minus JNI, streaming and the deadline (next section). The prompts are built by a Python port of `buildBatchPrompt` and `buildBatchGrammar`.
- **Inputs:** the three fixture pages (`scripts/speed-fixtures/pages/`, OpenMantra, CC BY-NC 4.0), in two forms:
  - #350's hand transcription (its Appendix A), with #350's panel breaks;
  - #350's real emulator OCR (its Appendix D), as the "gated" prompts, which drop the six garbage reads the way the OCR-confidence gate now does on `main` (#390).
- **Seeds:** 7, 8 and 9 for every arm. The phone draws a random seed per page (`seed = -1`, `core/src/main/java/com/yomu/core/TranslationSlot.kt:67`).
- **Scripts** were throwaway experiment code (a C++ replica of the app's prompt path, the arms, and the analysis), not part of the repo.

## Questions, and where the issue's checks went

| Issue check | Where it is answered |
|---|---|
| 1. Stop the leaks | [§2](#2-stopping-the-leaks) |
| 2. With and without register marks, per target | [§3](#3-register-marks-per-target-language) |
| 3. Hints in a system turn, and the other hints in English | [§4](#4-hints-in-a-system-turn) |
| 4. A source for the labels | [§5](#5-where-the-labels-would-come-from) |

## Short answer

- **No hint shape both stays out of the reply and visibly improves T–V.**
- **Inline marks (#350's shape) are the only shape that moved T–V,** and only in French and German, on a handful of lines.
  - With the shipped grammar they leak in every run: 72 of 72 runs, about 175 of 213 lines per language. Qwen copies the mark, translates it ("(rude)"), and invents marks for lines that had none.
  - With `(` removed from the grammar's `line` rule, no mark can be written. But the model was about to write one, and forcing it elsewhere derails the reply: lines that loop to the 160-character cap (6 in French and 9 in Portuguese, against 0 and 1 unmarked), four whole replies in English, *vous* on rough lines in one French run. Excluding `(` also breaks ADR-0013's rule that `line` may exclude structure characters only (`docs/adr/0013-grammar-constrained-page-level-batch.md:115`).
- **Hints in a system turn do not leak, and do not help.**
  - With a role sentence plus the labels, no label reached the reply in 72 runs. With Qwen's default identity kept plus the labels, one run of 72 tagged every line "(Formal)" or "(Casual)".
  - T–V was no better than in the control arm, whose system turn carries the role sentence alone. French rough lines still came out "Fermez les yeux" in one seed of three, and German gained "Schließen Sie die Augen" in one. The one polite question on these pages that needs an address form never took *vous* in French.
- **Per target language:**
  - **French:** the shipped prompt mixes *vous* imperatives into rough lines (tojime [4], [17]) in two seeds of three. Inline marks remove that, and give *vous* to the polite question in four of twelve runs. The system turn does neither visibly.
  - **German:** mostly *du* in every arm. Inline marks gave *Sie* on the polite question in three runs of six. The system turn added *Sie* to two rough lines.
  - **Spanish:** no *usted* in any of the 504 register replies. The model's Spanish is the weakest of the four: lines loop to the cap even in the unmarked arm, and Portuguese words leak in ("maquiagem").
  - **Brazilian Portuguese:** *você* everywhere and never *o senhor* / *a senhora*, in every arm. *Você* is the neutral form in Brazil, so these pages give the hint nothing to fix.
- **Replacing Qwen's default system turn changes the output by itself.** With "You are a manga translator." in place of "You are Qwen, created by Alibaba Cloud…", in English bourei lost the quotation marks around every line, and balloon went from "!!" on 7–9 lines per seed to 0–3. Any system-turn hint inherits that change, so it needs this control arm to be read at all.
- **The English re-run in the system-turn shape confirms #350:** bubble class, speaker gender and continuation hints made no visible difference. In the inline shape with `(` excluded, the marks leaked without parentheses instead ("Female: Stop it!", "[12] Continue").
- **No label source is good enough, and the hint would not use one anyway.** Qwen labelling its own page got 2–5 register labels right per page, of 4–12. A sentence-ending rule got 12 of 27 exactly, but 25 of 27 on "polite or not", with no false "polite". #350's decision models did worse.
- **Verdict: not worth it.** The reasons are in the [conclusion](#conclusion).

## 1. How a page reaches Qwen today (verified on `main`, `f44f0cc`)

Each item below is what the harness reproduces. The arms change only the system turn, the user turn's header and marks, and the grammar's excluded characters.

- **Target language.** `TargetLanguage` has five entries; the four non-English ones are Brazilian Portuguese, Spanish, French and German, written into the prompt by `promptName` (`core/src/main/java/com/yomu/core/TargetLanguage.kt:7-12`). Qwen's catalog entry is `multilingual = true` (`app/src/main/java/com/yomu/app/translation/LlmModelCatalog.kt:113`), so it is not refused (`ml/src/main/java/com/yomu/ml/LlamaTranslationBridge.kt:120`).
- **User turn.** `buildBatchPrompt` writes one header line, "Translate each Japanese line to {language}. Keep the [id] tag before each line. Panels are separated by ---.", then `[id] text` per bubble, with `---` between panels, each line newline-terminated (`LlamaTranslationBridge.kt:231-240`). Source text is capped at 300 characters and empty bubbles are dropped (`pipeline/src/main/java/com/yomu/pipeline/translation/TranslationEngine.kt:102`, `:149-159`).
- **Grammar.** `buildBatchGrammar` emits every id as a literal, in prompt order, with an optional final newline (#368). `line ::= [^\r\n\[]{1,160}`: it excludes `[` because `lineExcludesIdBracket` defaults to true (`LlamaTranslationBridge.kt:254-258`; `TranslationSlot.kt:77`). It admits `(`, which is how #350's marks leaked.
- **System turn.** Qwen's entry sets no `systemMessage`, so it carries the default `""` (`LlmModelCatalog.kt:70`, `:107-118`, mapped into the profile at `:410`; passed to the native call at `LlamaTranslationBridge.kt:295`). `format_chat_prompt` adds a system message only when that string is non-empty (`ml/src/main/cpp/chat_prompt.cpp:19-25`). It renders the GGUF's own template with `add_generation_prompt = true`, `enable_thinking = false` and `force_pure_content = true` (`:33-41`).
- **Qwen's default system turn.** The GGUF's embedded `tokenizer.chat_template` says, in its no-tools branch: if `messages[0]['role'] == 'system'`, render it; else render `'<|im_start|>system\nYou are Qwen, created by Alibaba Cloud. You are a helpful assistant.<|im_end|>\n'`. I read the template out of the pinned file, and the harness's rendered prompt shows that turn when no system message is sent. A system message therefore *replaces* the default turn; it is never added beside it.
- **Sampling.** `rebuild_sampler` chains penalties (last-n 64, repeat 1.0, freq 0, present 0), top-k 40, top-p 0.9, temperature 0.2 and `dist(seed)` (`ml/src/main/cpp/llama_jni.cpp:96-119`; values from `GenerationParams`, `TranslationSlot.kt:36-67`). The grammar sits outside the chain: sample first, test the token, and re-sample with the grammar applied first only on rejection (`ml/src/main/cpp/grammar_sampler.cpp:50-81`).
- **Context and decode.** `n_ctx` 2048 (`TranslationSlot.kt:164`), `n_batch` 2048, 4 threads (`llama_jni.cpp:192-200`; `TranslationSlot.kt:163`). The template output is tokenized with `parse_special = true` (`llama_jni.cpp:301`), and the decode loop stops at end-of-generation or at `MAX_BATCH_OUTPUT = 768` tokens (`llama_jni.cpp:353-391`; `LlamaTranslationBridge.kt:64`).
- **What the harness leaves out:** the 120 s deadline, streaming to the line splitter, and the abort flag. None of them changes the tokens chosen.

## 2. Stopping the leaks

Two shapes can keep labels out of the reply: forbid the mark character in the grammar, or move the labels out of the per-line tags. The arms:

| Arm | System turn | User turn | Grammar `line` excludes |
|---|---|---|---|
| `none` | none sent (Qwen's default) | shipped | `\r \n [` (shipped) |
| `none-np` | none sent | shipped | `\r \n [ ( （` |
| `inline-shipped` | none sent | #350's mark sentence + `(polite)`/`(casual)`/`(rough)` after tags | shipped |
| `inline-np` | none sent | as `inline-shipped` | `\r \n [ ( （` |
| `sys-ctl` | "You are a manga translator." | shipped | shipped |
| `sys` | that sentence + the labels + one sentence on using them | shipped | shipped |
| `sys-qwen` | Qwen's default sentence + the labels + the same sentence | shipped | shipped |

Each arm ran on both inputs (hand, real), three pages, four languages and three seeds: 72 replies per arm, 504 in all. [Appendix A](#appendix-a-prompts-grammars-and-reference-labels) has every prompt shape, and [Appendix C](https://github.com/artsaraiva/yomu/blob/173b94769129fb591122e1a93e49f6fbd4e583e1/docs/research/register-hints-target-languages.md#appendix-c-every-register-reply) every reply.

- **Inline marks with the shipped grammar leak in every run.** That is 72 of 72 runs, and between 172 and 177 of each language's 213 lines. The forms:
  - copied: "(polite) Oui...";
  - translated: "(rude)" for "(rough)" in French;
  - invented for lines with no mark: "(rough) Muguu" on balloon [3], "(polite)" on the camera text [7].

  The #368 grammar fix (optional last newline) did not change this. These leaks sit at the start of a line, not in run-on.
- **Excluding `(` stops the copied marks by construction, and costs nothing when there are no marks.** `none-np` was byte-identical to `none` in all 72 pairs: unmarked, Qwen never chose `(` on these pages in a non-English target.
- **With marks in the prompt, excluding `(` derails the reply.** The model's next token after a tag is the mark's `(`. Masking it pushes the model into tokens it gave little weight:
  - **Loops:** lines that run to the 160-character cap went from 0 (`none`) to 6 in French. One tojime run ended with "Allez, monsieur, monsieur, monsieur, …" on three lines. Portuguese went from 1 to 9 and Spanish stayed at 12.
  - **Wrong language:** four replies came out wholly or mostly in English (French hand balloon seed 8 and hand tojime seed 9, Spanish real tojime seeds 7 and 9). No unmarked or system-turn run did this.
  - **Content leaks:** possibly one, "Pare de ser tão rude!" for やっちょっと…!, which carried a casual mark.
  - **Off-register address:** one French balloon run addressed everybody as *vous*, rough lines included.
  - **And it breaks ADR-0013:** "`line` may exclude structure characters, never content characters" (`docs/adr/0013-grammar-constrained-page-level-batch.md:115`). `(` is content in "(sigh)" or a bracketed aside. Banning it would remove legitimate text in order to hide an instruction the prompt itself put there.
- **Labels outside the per-line tags mostly stay out.**
  - **`sys`:** 0 leaks in 72 runs.
  - **`sys-qwen`:** one run of 72 (Portuguese hand tojime, seed 9) appended "(Formal)" or "(Casual)" to all 18 lines, Qwen's own labels rather than the ones given.
  - **`sys-ctl`:** one stray "(Muito bem! )", which is not a label.

  The shipped grammar admits all of these, so the system turn is leak-*resistant*, not leak-*proof*.
- **Cost:** the system-turn labels add 40–90 prompt tokens per page. Median call time was 2,263 ms for `none`, 2,513 ms for `sys`, 2,598 ms for `inline-np` and 2,985 ms for `inline-shipped`.

## 3. Register marks, per target language

**Reference labels.** For bourei and tojime I reused #350's register marks. #350 marked no balloon lines, so I labelled balloon myself: the boy's lines rough or casual, the girl's two lines polite. [Appendix A](#appendix-a-prompts-grammars-and-reference-labels) lists every label. Labels for the real OCR reads follow #350's mapping from reads to hand bubbles; a merged read takes the rougher label.

**How I read them.** I read the replies by eye, per ADR-0015. To find the lines worth reading, a small tagger marks each labelled line T, V, both or neither. Its word lists are in `exp/analyze.py`; they include the French and Spanish imperatives seen in the replies. [Appendix B](#appendix-b-the-t-and-v-forms-per-labelled-line) has its per-line output for every arm. It is a reading aid, not a score. Each case below was checked against the reply.

**What the pages can show.** Most labelled lines carry no address form in any language, whatever the arm: "Oui… mais…", "Ah?", "Fille?". Only one polite line asks the listener something directly, the funeral director's question to the client (bourei [5] in the hand transcription, read [2] in the real OCR). The rough lines with address are mostly on tojime.

| Target | Arm | Rough/casual lines with an address form: with a V form | Polite lines with an address form: with a V form |
|---|---|---|---|
| French | none | 5 of 39 | 0 of 1 |
| French | inline-shipped | 5 of 50 | 2 of 2 |
| French | inline-np | 4 of 44 | 3 of 5 |
| French | sys-ctl | 5 of 39 | 0 of 3 |
| French | sys | 3 of 45 | 0 of 2 |
| French | sys-qwen | 4 of 41 | 0 of 0 |
| Spanish | every arm | 0 of 12–18 | 0 of 1–3 |
| German | none | 1 of 27 | 1 of 5 |
| German | inline-shipped | 0 of 34 | 0 of 5 |
| German | inline-np | 1 of 40 | 7 of 11 |
| German | sys-ctl | 0 of 30 | 1 of 6 |
| German | sys | 2 of 31 | 3 of 8 |
| German | sys-qwen | 2 of 25 | 0 of 1 |
| Brazilian Portuguese | every arm | 0 of 24–50 | 0 of 1–10 |

Hand and real inputs are pooled, over three seeds; `none-np` equals `none`. "Polite lines with V" counts lines, and several of German's are not on the question that needs it, so the paragraphs below are the reading.

- **French.**
  - **Rough lines:** with the shipped prompt, tojime [4] and [17] read 閉じ眼 (the heroine's nickname, "Closed-Eyes") as an order, "Fermez les yeux" (*vous*), in seeds 7 and 9. The same lines then use *tu* in the next clause ("Fermez les yeux, mais tu as une intelligence !"). Inline marks removed the *vous*: none of their six runs used it on these lines (one of them answered in English). The system turn gave "Fermez" in one seed of three, `sys-ctl` in two and `sys-qwen` in two.
  - **The polite question:** the shipped prompt never addresses the client ("L'apothéose de ma sœur ?"), except one real-OCR run that used *tu* ("Pourquoi tu veux savoir ce que fait ta sœur décédée ?"). Inline marks gave "Avez-vous vu… ?" / "votre tante" in all three hand runs with `(` excluded and in one with the shipped grammar. On real OCR, `inline-np` used *ta* twice. `sys` used *tu* or *ta* twice on real OCR and never *vous*.
  - **Balloon:** inline marks made several replies address the boy's rough lines as *vous* ("Pourquoi vouloir photographier dans l'espace alors que vous avez un appareil photo ?"). That is the opposite of the mark.
- **German.**
  - **Rough lines:** *du* in every arm. The system turn put "Schließen Sie die Augen, Sie sind der Wächter" on tojime [4] and [17] in one seed (`sys` and `sys-qwen`, seed 7), where `none` and `sys-ctl` had none.
  - **The polite question:** on the hand text, `inline-np` gave "Hatte die Tante, die Sie gekannt haben, …" twice and *dich* once; `sys` gave "Wie geht es Ihrer Tante…?" once. On real OCR, `none` used *du* in all three seeds; `sys` used *du* once and no address twice; `inline-np` used *Ihrer* once and *deine* once.
- **Spanish.** No *usted* anywhere, in 504 replies. The polite question took *tu* ("¿Qué hizo tu hermana muerta?") in `sys`, `sys-ctl` and `none` alike. Spanish is also the least stable target. The unmarked arm ran 12 lines to the cap, among them "¿Qué pasa?" repeated across bourei [2]. It mixes in Portuguese words ("la maquiagem").
- **Brazilian Portuguese.** *Você* / *sua* in every arm, never *o senhor* / *a senhora*. As #350 found, marks do not change this, and in Brazilian usage *você* is the ordinary polite-neutral form, so it is not an error here. English leaks into lines in every arm ("A makeup of the dead sister?", "The makeup of the deceased aunt?"). `sys-qwen` produced romanised Japanese three times for tojime [13] ("E? Ii… sou desu ka?").

**Reading of §3.** The register mark visibly changes French, and German a little, only when it sits on the line it describes. That is exactly the shape that cannot be kept out of the reply without damage (§2). Spanish and Portuguese show no T–V movement in any arm. Across all four languages, the larger quality problem on these pages is comprehension, not register: "Fermez les yeux" for a nickname, "L'apothéose de ma sœur" for the makeup question. Those stay wrong in every arm.

## 4. Hints in a system turn

**Register (non-English).** The comparison is `sys` against `sys-ctl`, which differ only by the labels and the sentence on using them, and `sys-qwen` against `none`, which differ the same way while keeping Qwen's identity sentence.

- **Neither pair shows a visible T–V gain** (§3). `sys` moved French tojime from "Fermez" in two seeds (`sys-ctl`) to one, and German from none to one "Schließen Sie". `sys-qwen` matched `none` in French and added the same German *Sie* line. Those moves are the size of a seed change.
- **Inline marks moved T–V more than system-turn labels did.** A label naming a line by id in a separate turn seems to be weaker for a 1.5B model than one sitting beside the text. That matches the leak pattern: the model treats an inline mark as part of the line's shape, which is why it copies it.
- **Leaks:** see §2. The system turn is the only shape that is both leak-resistant and within ADR-0013's grammar rule.

**Replacing the default system turn changes the output on its own.** The `sys-ctl` arm swaps "You are Qwen, created by Alibaba Cloud. You are a helpful assistant." for "You are a manga translator." and nothing else. In English ([Appendix D](https://github.com/artsaraiva/yomu/blob/173b94769129fb591122e1a93e49f6fbd4e583e1/docs/research/register-hints-target-languages.md#appendix-d-every-english-re-run-reply)):

- bourei: `none` wraps every line in quotation marks in all three seeds; `sys-ctl` in none;
- balloon: `none` puts "!!" on 7–9 of 15 lines per seed; `sys-ctl` on 0–3;
- tojime: `none` renders あ? and えっ as "Ah?", `sys-ctl` mostly as a bare "?".

Neither style is a register change. But any system-turn hint arm differs from `none` by at least this much, so it must be read against `sys-ctl`, not against `none`.

**Bubble class, speaker gender and continuation, English, system-turn shape.** These ran on the hand transcription with three seeds (19 arm-page combinations, 57 replies; Appendix D):

| Arm | What changed against `none` and `sys-ctl` |
|---|---|
| `class-sys` (every bubble labelled) | Nothing visible beyond the `sys-ctl` style change. Captions read as captions with or without labels ("Funeral requestor Sakurae Suzume 19 years old"); グッ stays "GG"/"Goo"; プラーン stays "PLAN". |
| `class-ns-sys` (only narration and sfx labelled) | The same. In two tojime seeds, [7] and [12] became a bare "?" (`sys-ctl` did this too on [6] and [16]). |
| `class-inline-np` (#350's marks, `(` excluded) | Every line of every run came back inside quotation marks, and プラーン became "Planner". No parenthesised mark is possible, and none appeared in another form. |
| `gender-sys` | No change to tojime [17]'s subject ("Even if you close your eyes, you're still valuable…"), the error gender was meant to fix. [12] stays "Girl?". |
| `gender-inline-np` | The mark leaked without parentheses: "Female: Stop it! This is not the place!" on [8], [13] and [18] in seed 9. |
| `cont-sys` ([12] finishes [11]) | Seed 7 joined the sense ("Is it possible that you're also a girl?" / "Girl?"). But `none` already wrote "You might be a girl too, right?" in two seeds, so this is not a gain. |
| `cont-inline-np` | Seed 8 wrote "[12] Continue": the mark, translated into content. |

So the system-turn wording does not rescue the hints #350 found useless: none made a visible difference. The inline shape, made leak-proof by grammar, leaks through words instead of brackets.

## 5. Where the labels would come from

Only worth asking if §3–§4 had shown a gain. Recorded here for completeness ([Appendix E](#appendix-e-register-labels-from-qwen-and-from-an-ending-rule)).

- **Qwen itself (rung zero).** One grammar-constrained pass per page answering `polite|casual|rough|none` for each line, with the shipped sampler, three seeds:
  - **Accuracy:** exact labels on 2–5 lines per page, of 4–12 labelled, on the hand text, and 2–3 of 4–10 on real OCR.
  - **Errors:** it called tojime's rough lines polite: お前もしかして, 面見せてみろ, 閉じ眼でも器量が良けりゃ….
  - **Never "none":** it answered "none" for no line, sounds and cries included.
  - **Cost:** 0.6–1.8 s per page on this desktop, a second prefill of the page.
- **A sentence-ending rule.** I tested です/ます (and でして, ません, ください) → polite; ぞ/ぜ/やがれ/ねー/お前/imperative ろ → rough; だ/だろ/よ/けど → casual; otherwise no label (`exp/analyze.py`, `ending_rule`).
  - **Exact labels:** 12 of 27 labelled hand lines right, and 10 of 24 on real OCR.
  - **Polite or not**, the only distinction T–V needs: 25 of 27 hand lines and 21 of 24 real ones right. It never called a rough or casual line polite. It misses polite lines with no polite ending (あっ安かったので…, はい…, 亡くなったお姉さんのお化粧を?), and the question to the client is one of them.
  - **It is silent on cries and sounds,** which is right.
- **ADR-0007's objection applies to the rule.** It rejected deriving a field heuristically because "it manufactures a field flowing into the translation prompt" (`docs/adr/0007-bubble-text-detection-approach.md`). An ending rule reads the same Japanese Qwen already reads. It adds no information, only emphasis, and where it is silent or wrong it would put a confident wrong label beside the line.
- **A local decision model (#350 §4).** #350 already asked register of d1-3B and d1-omni-600M on the same pages. d1-3B got 6 of 11 rough tojime lines and 0 of 8 polite bourei lines. d1-omni said "polite" for everything. Neither is better than the ending rule, and both need a second resident model.

## Conclusion

**Not worth it.**

- **Visible T–V gains appeared only with inline marks,** and only in French and German, on a few lines of three pages.
- **That shape cannot ship.** With the shipped grammar, the marks leak in every run. With `(` banned, the reply derails into loops and wrong-language lines, and the grammar breaks ADR-0013's structure-only rule.
- **The shape that stays out of the reply does not help.** A system turn kept the labels out in 72 of 72 runs with a role sentence, and 71 of 72 with Qwen's identity sentence, but showed no T–V gain over its own control.
- **Spanish and Brazilian Portuguese showed no formal address in any arm,** so there is nothing for a label to switch on.
- **The cheapest label source (the ending rule) is decent but adds nothing Qwen cannot already read.** The other sources (Qwen itself, #350's decision models) are wrong too often.
- **The errors a reader would notice are comprehension errors,** and no register label touches them. "Fermez les yeux" for a nickname and "L'apothéose de ma sœur" for a makeup question are two examples.

**What would reopen it:** a different translation model that already uses formal address when the Japanese is polite, so the hint has something to steer, tested first in the system-turn shape against a `sys-ctl` control. With Qwen2.5-1.5B, no follow-up issue.

**Side findings, not filed:**

1. **Qwen2.5-1.5B's Spanish is the weakest target on these pages.** The unmarked arm ran 12 lines to the 160-character cap in 18 runs, against 0 in French and German, and Portuguese words leak in. #337's own phone check is where to confirm it.
2. **Replacing Qwen's default system turn changes surface style** (quotation marks, "!!", bare "?"). If a system message is ever given to Qwen's catalog entry for another reason, it is a visible change and needs a look on the fixture pages.

## Open questions / not verified

- **Nothing ran on a phone or an emulator.** The real-OCR input is #350's from 2026-10-17. Since then `main` decodes OCR from start token 2 and gates at 0.85 (`pipeline/src/main/java/com/yomu/pipeline/ocr/OcrDecoding.kt:10`, `OcrEngine.kt:23`, `TranslationPipeline.kt:189-197`). A page report taken on the emulator on 2026-10-20 reads tojime's ほぉ and えっ, which Appendix D lacks. Neither is a register-labelled line, so the register-bearing input is unchanged, but the prompt the app sends today is not byte-identical to the one used here.
- **Panel breaks for the real OCR come from #350's port of the old `ContextAssembler`.** It now reads panels from gutters (#375).
- **One polite line on three pages needs an address form.** That is far too few to measure a T–V effect on polite speech in any language. More pages with polite address (shop, school or office scenes) would be the next input, not more seeds.
- **The labels are mine and #350's,** for three pages, and some are judgment calls. Balloon [8] あっ安かったので… is polite only by the speaker's other line. こいつ絶対バカだし is rough by its insult, not its ending.
- **The T–V tagger is a word list.** It misses imperatives it does not list and German sentence-initial *Sie*. I read every flagged line, and the Appendix C replies, by eye, but a missed form is possible.
- **One wording per shape.** Other wordings of the system turn (naming the T–V forms, putting one label per line, or using the target language) were not tried.
- **Desktop builds differ from the phone** in CPU kernels (`GGML_NATIVE`, no repacked ARM variants). Tokens at temperature 0.2 can differ by kernel, so the phone may pick different words for the same seed.

## Sources

- GitHub (artsaraiva/yomu): #363, #350, #337 and PR #395, #368, #214, #390, #375.
- Repo at `f44f0cc`:
  - `ml/src/main/java/com/yomu/ml/LlamaTranslationBridge.kt`;
  - `ml/src/main/cpp/chat_prompt.cpp`, `grammar_sampler.cpp`, `llama_jni.cpp`, `prompt_budget.h`, `CMakeLists.txt`;
  - `core/src/main/java/com/yomu/core/TargetLanguage.kt`, `TranslationSlot.kt`, `Constants.kt`;
  - `app/src/main/java/com/yomu/app/translation/LlmModelCatalog.kt`, `app/src/main/java/com/yomu/app/service/ModelManager.kt`;
  - `pipeline/src/main/java/com/yomu/pipeline/translation/TranslationEngine.kt`, `TranslationPipeline.kt`, `ocr/OcrEngine.kt`, `ocr/OcrDecoding.kt`, `context/ContextAssembler.kt`;
  - ADR-0007, 0010, 0013 (with the #203, #214 and #368 amendments) and 0015; `CONTEXT.md`; `AGENTS.md`;
  - `docs/research/jev-bubble-class-hints.md` (Appendices A, C, D and §4).
- llama.cpp at `7fe450e` (ggml-org/llama.cpp): `include/llama.h`, `common/chat.h`, and the sampler and grammar code the harness links.
- Hugging Face: `bartowski/Qwen2.5-1.5B-Instruct-GGUF` @`9eadc66`, `Qwen2.5-1.5B-Instruct-Q4_K_M.gguf`, with its embedded `tokenizer.chat_template` read from the file.


## Appendix A: prompts, grammars and reference labels

**Reference register labels.** Bourei and tojime hand labels are #350's register marks, unchanged. Balloon labels are mine (#350 marked none). Real-OCR labels map each read to its hand bubble(s) by #350's Appendix D, and a merged read takes the rougher label. A line with no label carried no mark and no system-turn entry. Bubble-class and gender labels for the English re-run are #350's Appendix A labels and its gender-arm marks.


**balloon-dense-dialogue, hand transcription**

| id | text | register |
|---|---|---|
| 1 | ただ空気いれた風船をいくら集めても浮くわけねーだろ!! | rough |
| 2 | こいつ絶対バカだし | rough |
| 3 | むぐっ | — |
| 4 | なにあ然としてんの!? | casual |
| 5 | ヘリウム入れないと風船は浮かねーよ!! | rough |
| 6 | 大体なんで使い捨てカメラなんだよ! | rough |
| 7 | すご～くよく写るンズ | — |
| 8 | あっ安かったので… | polite |
| 9 | 宇宙でフィルム巻いてシャッターきる気か!? | rough |
| 10 | ったく | rough |
| 11 | 確かに原理的には可能かもしれないけどな… | casual |
| 12 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual |
| 13 | わざわざ自分で撮らなくたって… | casual |
| 14 | ああ | — |
| 15 | それはですね… | polite |

**bourei-p04, hand transcription**

| id | text | register |
|---|---|---|
| 1 | 死んでるんですよねぇ | polite |
| 2 | 「視える」人間ってヤツでして | polite |
| 3 | ようこそ亡霊葬儀屋さん 吉良いと | — |
| 4 | え? | — |
| 5 | 亡くなったお姉さんのお化粧を? | polite |
| 6 | はい… | polite |
| 7 | 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳 | — |
| 8 | 葬儀依頼人 桜野 すずめ 19歳 | — |

**tojime-dense-action, hand transcription**

| id | text | register |
|---|---|---|
| 1 | マフィア舐めんのも大概にしやがれ | rough |
| 2 | グッ | — |
| 3 | あっ | — |
| 4 | 閉じ眼が用心棒だ? | rough |
| 5 | 腰の銃でイかれた脳みそぶちまけてみるか? | rough |
| 6 | あ? | rough |
| 7 | あ、あの… | — |
| 8 | ちょっとやめてよ!こんなとこで! | casual |
| 9 | プラーン | — |
| 10 | ん? | — |
| 11 | お前もしかして | rough |
| 12 | 女か? | rough |
| 13 | えっはい…そうですけど… | polite |
| 14 | ほぉ | — |
| 15 | 面見せてみろ | rough |
| 16 | えっ | — |
| 17 | 閉じ眼でも器量が良けりゃ買い手は… | rough |
| 18 | やっちょっと…! | casual |

**balloon-dense-dialogue, real emulator OCR, gated (OCR ids; hand ids in brackets)**

| id | text | register |
|---|---|---|
| 10 ([2]) | こいつ絶対バカだし | rough |
| 9 ([3]) | むぐっ | — |
| 4 ([6]) | 大体なんで使い捨てカメラなんだよ! | rough |
| 1 ([12]) | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual |
| 11 ([10]) | ったく | rough |
| 2 ([11]) | 確かに原理的には可能かもしれないけどな... | casual |
| 13 ([4], [5]) | なにあ然としてんの!?ヘリウム入れないと風船は浮かねーよ!! | rough |
| 5 ([9]) | 宇宙でフィルム巻いてシャッターきる気か!? | rough |
| 8 ([8]) | あっ安かったので... | polite |
| 7 ([14]) | ああ | — |
| 3 ([13]) | わざわざ自分で撮らなくたって... | casual |
| 6 ([15]) | それはですね... | polite |

**bourei-p04, real emulator OCR, gated (OCR ids; hand ids in brackets)**

| id | text | register |
|---|---|---|
| 3 ([1]) | 死んでるんですよねぇ | polite |
| 1 ([2]) | 「視える」人間ってヤツでして | polite |
| 2 ([5]) | 亡くなったお姉さんのお化粧を? | polite |
| 7 ([4]) | え? | — |
| 4 ([6]) | はい... | polite |

**tojime-dense-action, real emulator OCR, gated (OCR ids; hand ids in brackets)**

| id | text | register |
|---|---|---|
| 5 ([1]) | マフィア舐めんのも大概にしやがれ | rough |
| 1 ([5], [6]) | 腰の銃でイかれた脳みそぶちまけてみるか?あ? | rough |
| 7 ([11]) | お前もしかして | rough |
| 4 ([8]) | ちょっとやめてよ!こんなとこで! | casual |
| 11 ([7]) | あ、あの... | — |
| 8 ([12]) | 女か? | rough |
| 3 ([13]) | えっはい...そうですけど... | polite |
| 10 ([3]) | あっ | — |
| 9 ([15]) | 面見せてみろ | rough |
| 6 ([4]) | 閉じ眼が用心棒だ? | rough |
| 15 ([10]) | ん? | — |
| 2 ([17]) | 閉じ眼でも器量が良けりゃ買い手は... | rough |
| 12 ([18]) | やっちょっと...! | casual |

**Prompt shapes.** One example of each shape, as the harness sent it. The other pages and languages differ only in the lines, the language name and the labels.


**hand, bourei-p04, French, none**

```text
[system: none sent; the template adds Qwen's default]
[user]
Translate each Japanese line to French. Keep the [id] tag before each line. Panels are separated by ---.
[1] 死んでるんですよねぇ
[2] 「視える」人間ってヤツでして
[3] ようこそ亡霊葬儀屋さん 吉良いと
---
[4] え?
[5] 亡くなったお姉さんのお化粧を?
[6] はい…
[7] 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳
[8] 葬儀依頼人 桜野 すずめ 19歳
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n"?
line ::= [^\r\n\[]{1,160}
```


**hand, bourei-p04, French, inline-np**

```text
[system: none sent; the template adds Qwen's default]
[user]
Translate each Japanese line to French. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (polite), (casual) or (rough) mark after its tag: how formally the speaker talks. It is not part of the text, so do not copy it into your reply.
[1] (polite) 死んでるんですよねぇ
[2] (polite) 「視える」人間ってヤツでして
[3] ようこそ亡霊葬儀屋さん 吉良いと
---
[4] え?
[5] (polite) 亡くなったお姉さんのお化粧を?
[6] (polite) はい…
[7] 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳
[8] 葬儀依頼人 桜野 すずめ 19歳
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n"?
line ::= [^\r\n\[(（]{1,160}
```


**hand, bourei-p04, French, sys**

```text
[system]
You are a manga translator.
How formally each speaker talks — polite: [1], [2], [5], [6]. Lines not listed carry no label.
Use this to choose between formal and informal forms of address in the translation. Do not write these labels in your reply.
[user]
Translate each Japanese line to French. Keep the [id] tag before each line. Panels are separated by ---.
[1] 死んでるんですよねぇ
[2] 「視える」人間ってヤツでして
[3] ようこそ亡霊葬儀屋さん 吉良いと
---
[4] え?
[5] 亡くなったお姉さんのお化粧を?
[6] はい…
[7] 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳
[8] 葬儀依頼人 桜野 すずめ 19歳
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n"?
line ::= [^\r\n\[]{1,160}
```


**hand, bourei-p04, French, sys-qwen**

```text
[system]
You are Qwen, created by Alibaba Cloud. You are a helpful assistant.
How formally each speaker talks — polite: [1], [2], [5], [6]. Lines not listed carry no label.
Use this to choose between formal and informal forms of address in the translation. Do not write these labels in your reply.
[user]
Translate each Japanese line to French. Keep the [id] tag before each line. Panels are separated by ---.
[1] 死んでるんですよねぇ
[2] 「視える」人間ってヤツでして
[3] ようこそ亡霊葬儀屋さん 吉良いと
---
[4] え?
[5] 亡くなったお姉さんのお化粧を?
[6] はい…
[7] 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳
[8] 葬儀依頼人 桜野 すずめ 19歳
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n"?
line ::= [^\r\n\[]{1,160}
```


**hand, tojime-dense-action, German, sys**

```text
[system]
You are a manga translator.
How formally each speaker talks — polite: [13]; casual: [8], [18]; rough: [1], [4], [5], [6], [11], [12], [15], [17]. Lines not listed carry no label.
Use this to choose between formal and informal forms of address in the translation. Do not write these labels in your reply.
[user]
Translate each Japanese line to German. Keep the [id] tag before each line. Panels are separated by ---.
[1] マフィア舐めんのも大概にしやがれ
[2] グッ
[3] あっ
[4] 閉じ眼が用心棒だ?
---
[5] 腰の銃でイかれた脳みそぶちまけてみるか?
[6] あ?
[7] あ、あの…
[8] ちょっとやめてよ!こんなとこで!
[9] プラーン
---
[10] ん?
---
[11] お前もしかして
[12] 女か?
[13] えっはい…そうですけど…
---
[14] ほぉ
---
[15] 面見せてみろ
[16] えっ
[17] 閉じ眼でも器量が良けりゃ買い手は…
[18] やっちょっと…!
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n" "[9] " line "\n" "[10] " line "\n" "[11] " line "\n" "[12] " line "\n" "[13] " line "\n" "[14] " line "\n" "[15] " line "\n" "[16] " line "\n" "[17] " line "\n" "[18] " line "\n"?
line ::= [^\r\n\[]{1,160}
```


**real, tojime-dense-action, Spanish, inline-np**

```text
[system: none sent; the template adds Qwen's default]
[user]
Translate each Japanese line to Spanish. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (polite), (casual) or (rough) mark after its tag: how formally the speaker talks. It is not part of the text, so do not copy it into your reply.
[5] (rough) マフィア舐めんのも大概にしやがれ
[1] (rough) 腰の銃でイかれた脳みそぶちまけてみるか?あ?
[7] (rough) お前もしかして
[4] (casual) ちょっとやめてよ!こんなとこで!
[11] あ、あの...
[8] (rough) 女か?
[3] (polite) えっはい...そうですけど...
---
[10] あっ
[9] (rough) 面見せてみろ
---
[6] (rough) 閉じ眼が用心棒だ?
[15] ん?
[2] (rough) 閉じ眼でも器量が良けりゃ買い手は...
[12] (casual) やっちょっと...!
```

Grammar:

```text
root ::= "[5] " line "\n" "[1] " line "\n" "[7] " line "\n" "[4] " line "\n" "[11] " line "\n" "[8] " line "\n" "[3] " line "\n" "[10] " line "\n" "[9] " line "\n" "[6] " line "\n" "[15] " line "\n" "[2] " line "\n" "[12] " line "\n"?
line ::= [^\r\n\[(（]{1,160}
```


**hand, balloon-dense-dialogue, Brazilian Portuguese, sys**

```text
[system]
You are a manga translator.
How formally each speaker talks — polite: [8], [15]; casual: [4], [11], [12], [13]; rough: [1], [2], [5], [6], [9], [10]. Lines not listed carry no label.
Use this to choose between formal and informal forms of address in the translation. Do not write these labels in your reply.
[user]
Translate each Japanese line to Brazilian Portuguese. Keep the [id] tag before each line. Panels are separated by ---.
[1] ただ空気いれた風船をいくら集めても浮くわけねーだろ!!
[2] こいつ絶対バカだし
[3] むぐっ
---
[4] なにあ然としてんの!?
[5] ヘリウム入れないと風船は浮かねーよ!!
---
[6] 大体なんで使い捨てカメラなんだよ!
[7] すご～くよく写るンズ
[8] あっ安かったので…
[9] 宇宙でフィルム巻いてシャッターきる気か!?
---
[10] ったく
[11] 確かに原理的には可能かもしれないけどな…
---
[12] 地球の写真なんてネット上にいくらでも落ちてるだろ
[13] わざわざ自分で撮らなくたって…
---
[14] ああ
[15] それはですね…
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n" "[9] " line "\n" "[10] " line "\n" "[11] " line "\n" "[12] " line "\n" "[13] " line "\n" "[14] " line "\n" "[15] " line "\n"?
line ::= [^\r\n\[]{1,160}
```


**hand, balloon-dense-dialogue, English, class-sys**

```text
[system]
You are a manga translator.
What each line is — speech: [1], [2], [3], [4], [5], [6], [8], [9], [10], [11], [12], [13], [14], [15]; sfx: [7].
Use this to translate each line in the right voice: speech as a character talking, narration as a caption, sfx as a sound effect. Do not write these labels in your reply.
[user]
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[1] ただ空気いれた風船をいくら集めても浮くわけねーだろ!!
[2] こいつ絶対バカだし
[3] むぐっ
---
[4] なにあ然としてんの!?
[5] ヘリウム入れないと風船は浮かねーよ!!
---
[6] 大体なんで使い捨てカメラなんだよ!
[7] すご～くよく写るンズ
[8] あっ安かったので…
[9] 宇宙でフィルム巻いてシャッターきる気か!?
---
[10] ったく
[11] 確かに原理的には可能かもしれないけどな…
---
[12] 地球の写真なんてネット上にいくらでも落ちてるだろ
[13] わざわざ自分で撮らなくたって…
---
[14] ああ
[15] それはですね…
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n" "[9] " line "\n" "[10] " line "\n" "[11] " line "\n" "[12] " line "\n" "[13] " line "\n" "[14] " line "\n" "[15] " line "\n"?
line ::= [^\r\n\[]{1,160}
```


**hand, balloon-dense-dialogue, English, class-ns-sys**

```text
[system]
You are a manga translator.
Lines that are not speech — sfx: [7]. Every other line is a character talking.
Use this to translate each line in the right voice. Do not write these labels in your reply.
[user]
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[1] ただ空気いれた風船をいくら集めても浮くわけねーだろ!!
[2] こいつ絶対バカだし
[3] むぐっ
---
[4] なにあ然としてんの!?
[5] ヘリウム入れないと風船は浮かねーよ!!
---
[6] 大体なんで使い捨てカメラなんだよ!
[7] すご～くよく写るンズ
[8] あっ安かったので…
[9] 宇宙でフィルム巻いてシャッターきる気か!?
---
[10] ったく
[11] 確かに原理的には可能かもしれないけどな…
---
[12] 地球の写真なんてネット上にいくらでも落ちてるだろ
[13] わざわざ自分で撮らなくたって…
---
[14] ああ
[15] それはですね…
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n" "[9] " line "\n" "[10] " line "\n" "[11] " line "\n" "[12] " line "\n" "[13] " line "\n" "[14] " line "\n" "[15] " line "\n"?
line ::= [^\r\n\[]{1,160}
```


**hand, tojime-dense-action, English, gender-sys**

```text
[system]
You are a manga translator.
The speaker's gender — male: [1], [4], [5], [6], [10], [11], [12], [14], [15], [17]; female: [3], [7], [8], [13], [16], [18]. Lines not listed carry no label.
Use this to choose pronouns and gendered words. Do not write these labels in your reply.
[user]
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[1] マフィア舐めんのも大概にしやがれ
[2] グッ
[3] あっ
[4] 閉じ眼が用心棒だ?
---
[5] 腰の銃でイかれた脳みそぶちまけてみるか?
[6] あ?
[7] あ、あの…
[8] ちょっとやめてよ!こんなとこで!
[9] プラーン
---
[10] ん?
---
[11] お前もしかして
[12] 女か?
[13] えっはい…そうですけど…
---
[14] ほぉ
---
[15] 面見せてみろ
[16] えっ
[17] 閉じ眼でも器量が良けりゃ買い手は…
[18] やっちょっと…!
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n" "[9] " line "\n" "[10] " line "\n" "[11] " line "\n" "[12] " line "\n" "[13] " line "\n" "[14] " line "\n" "[15] " line "\n" "[16] " line "\n" "[17] " line "\n" "[18] " line "\n"?
line ::= [^\r\n\[]{1,160}
```


**hand, tojime-dense-action, English, cont-sys**

```text
[system]
You are a manga translator.
Line [12] finishes the sentence begun in line [11].
Translate the two lines so they read as one sentence split across them. Do not write this note in your reply.
[user]
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[1] マフィア舐めんのも大概にしやがれ
[2] グッ
[3] あっ
[4] 閉じ眼が用心棒だ?
---
[5] 腰の銃でイかれた脳みそぶちまけてみるか?
[6] あ?
[7] あ、あの…
[8] ちょっとやめてよ!こんなとこで!
[9] プラーン
---
[10] ん?
---
[11] お前もしかして
[12] 女か?
[13] えっはい…そうですけど…
---
[14] ほぉ
---
[15] 面見せてみろ
[16] えっ
[17] 閉じ眼でも器量が良けりゃ買い手は…
[18] やっちょっと…!
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n" "[9] " line "\n" "[10] " line "\n" "[11] " line "\n" "[12] " line "\n" "[13] " line "\n" "[14] " line "\n" "[15] " line "\n" "[16] " line "\n" "[17] " line "\n" "[18] " line "\n"?
line ::= [^\r\n\[]{1,160}
```


**hand, tojime-dense-action, English, cont-inline-np**

```text
[system: none sent; the template adds Qwen's default]
[user]
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (continues [n]) mark after its tag: it finishes the sentence begun in line n. It is not part of the text, so do not copy it into your reply.
[1] マフィア舐めんのも大概にしやがれ
[2] グッ
[3] あっ
[4] 閉じ眼が用心棒だ?
---
[5] 腰の銃でイかれた脳みそぶちまけてみるか?
[6] あ?
[7] あ、あの…
[8] ちょっとやめてよ!こんなとこで!
[9] プラーン
---
[10] ん?
---
[11] お前もしかして
[12] (continues [11]) 女か?
[13] えっはい…そうですけど…
---
[14] ほぉ
---
[15] 面見せてみろ
[16] えっ
[17] 閉じ眼でも器量が良けりゃ買い手は…
[18] やっちょっと…!
```

Grammar:

```text
root ::= "[1] " line "\n" "[2] " line "\n" "[3] " line "\n" "[4] " line "\n" "[5] " line "\n" "[6] " line "\n" "[7] " line "\n" "[8] " line "\n" "[9] " line "\n" "[10] " line "\n" "[11] " line "\n" "[12] " line "\n" "[13] " line "\n" "[14] " line "\n" "[15] " line "\n" "[16] " line "\n" "[17] " line "\n" "[18] " line "\n"?
line ::= [^\r\n\[(（]{1,160}
```


## Appendix B: the T and V forms per labelled line

The tagger's reading of each labelled line, one character per seed (7, 8, 9): **T** informal address (French *tu/te/ton/ta*, Spanish *tú/te/tu*, German *du/dich/dir/dein*, Portuguese *você/tu/teu*, plus the imperatives listed in `exp/analyze.py`); **V** formal address (*vous/votre*, *usted*, *Sie/Ihnen/Ihr*, *o senhor/a senhora*); **B** both; **·** neither; **?** the line is missing from the reply. A reading aid only; §3 is checked against the replies themselves.


**balloon-dense-dialogue, hand transcription, French**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | ただ空気いれた風船をいくら集めても浮くわけねーだろ!! | rough | ·T· | ·T· | ··· | ··V | ··· | ·V· | ··· |
| 2 | こいつ絶対バカだし | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 4 | なにあ然としてんの!? | casual | T·· | T·· | T·· | ··V | T·· | TT· | TT· |
| 5 | ヘリウム入れないと風船は浮かねーよ!! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 6 | 大体なんで使い捨てカメラなんだよ! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 8 | あっ安かったので… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 9 | 宇宙でフィルム巻いてシャッターきる気か!? | rough | ·T· | ·T· | VTV | T·V | ··· | ··· | ··· |
| 10 | ったく | rough | ··· | ··· | ··· | ··· | T·· | ··· | ··· |
| 11 | 確かに原理的には可能かもしれないけどな… | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 12 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 13 | わざわざ自分で撮らなくたって… | casual | ·T· | ·T· | VTV | T·V | ··· | ··· | ··· |
| 15 | それはですね… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**balloon-dense-dialogue, hand transcription, Spanish**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | ただ空気いれた風船をいくら集めても浮くわけねーだろ!! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 2 | こいつ絶対バカだし | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 4 | なにあ然としてんの!? | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 5 | ヘリウム入れないと風船は浮かねーよ!! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 6 | 大体なんで使い捨てカメラなんだよ! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 8 | あっ安かったので… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 9 | 宇宙でフィルム巻いてシャッターきる気か!? | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 10 | ったく | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 11 | 確かに原理的には可能かもしれないけどな… | casual | ··· | ··· | ··· | ··· | ··· | ·T· | ··· |
| 12 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 13 | わざわざ自分で撮らなくたって… | casual | ··· | ··· | ··· | ··T | ··· | ·T· | T·· |
| 15 | それはですね… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**balloon-dense-dialogue, hand transcription, German**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | ただ空気いれた風船をいくら集めても浮くわけねーだろ!! | rough | TTT | TTT | TTT | ··· | ·T· | ··T | ··· |
| 2 | こいつ絶対バカだし | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 4 | なにあ然としてんの!? | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 5 | ヘリウム入れないと風船は浮かねーよ!! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 6 | 大体なんで使い捨てカメラなんだよ! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 8 | あっ安かったので… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 9 | 宇宙でフィルム巻いてシャッターきる気か!? | rough | TT· | TT· | ··· | ··· | TTT | T·T | ·TT |
| 10 | ったく | rough | ··· | ··· | TTT | T·· | TT· | T·T | ·T· |
| 11 | 確かに原理的には可能かもしれないけどな… | casual | ··· | ··· | ··· | ··T | ··· | ··· | ··· |
| 12 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 13 | わざわざ自分で撮らなくたって… | casual | T·T | T·T | ··T | ··· | ·T· | T·T | ·T· |
| 15 | それはですね… | polite | ··· | ··· | ··· | ··T | ··· | ··· | ··· |

**balloon-dense-dialogue, hand transcription, Brazilian Portuguese**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | ただ空気いれた風船をいくら集めても浮くわけねーだろ!! | rough | TTT | TTT | T·T | TTT | TTT | T·T | TTT |
| 2 | こいつ絶対バカだし | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 4 | なにあ然としてんの!? | casual | ··· | ··· | TTT | TTT | T·· | ·T· | ··· |
| 5 | ヘリウム入れないと風船は浮かねーよ!! | rough | ·TT | ·TT | ··T | ··· | T·· | ··· | ··· |
| 6 | 大体なんで使い捨てカメラなんだよ! | rough | ··· | ··· | ··T | ··· | ··· | ··· | ··· |
| 8 | あっ安かったので… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 9 | 宇宙でフィルム巻いてシャッターきる気か!? | rough | ·T· | ·T· | TTT | TTT | T·T | TTT | TTT |
| 10 | ったく | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 11 | 確かに原理的には可能かもしれないけどな… | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 12 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual | ··· | ··· | TTT | T·· | ··T | ··· | TTT |
| 13 | わざわざ自分で撮らなくたって… | casual | ··T | ··T | TTT | TTT | ··T | T·T | ·TT |
| 15 | それはですね… | polite | ··T | ··T | ··· | ·T· | ··· | ··· | ··· |

**bourei-p04, hand transcription, French**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 死んでるんですよねぇ | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 2 | 「視える」人間ってヤツでして | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 5 | 亡くなったお姉さんのお化粧を? | polite | ··· | ··· | V·· | VVV | ··· | ··· | ··· |
| 6 | はい… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**bourei-p04, hand transcription, Spanish**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 死んでるんですよねぇ | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 2 | 「視える」人間ってヤツでして | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 5 | 亡くなったお姉さんのお化粧を? | polite | ··· | ··· | ··· | ··T | T·· | T·T | ··· |
| 6 | はい… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**bourei-p04, hand transcription, German**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 死んでるんですよねぇ | polite | ··· | ··· | ·T· | ··· | ··· | TTT | ··· |
| 2 | 「視える」人間ってヤツでして | polite | ··V | ··V | ··· | VV· | ··· | VV· | ··· |
| 5 | 亡くなったお姉さんのお化粧を? | polite | ··· | ··· | ·T· | VVT | T·· | VT· | ··· |
| 6 | はい… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**bourei-p04, hand transcription, Brazilian Portuguese**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 死んでるんですよねぇ | polite | ··· | ··· | ··· | ·TT | ··· | ··· | ·T· |
| 2 | 「視える」人間ってヤツでして | polite | ··· | ··· | ··· | TTT | T·T | TTT | ··· |
| 5 | 亡くなったお姉さんのお化粧を? | polite | ··· | ··· | ··T | ··· | ··T | ··· | ··· |
| 6 | はい… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**tojime-dense-action, hand transcription, French**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | マフィア舐めんのも大概にしやがれ | rough | ··· | ··· | ··· | TT· | ··· | ··· | ··· |
| 4 | 閉じ眼が用心棒だ? | rough | VTV | VTV | TTT | TT· | VTV | TBT | TVB |
| 5 | 腰の銃でイかれた脳みそぶちまけてみるか? | rough | ·TT | ·TT | TTT | TT· | TTT | TTT | TTT |
| 6 | あ? | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 8 | ちょっとやめてよ!こんなとこで! | casual | TTV | TTV | TTV | TT· | TTT | ·TT | T·T |
| 11 | お前もしかして | rough | TTT | TTT | TTT | TT· | TTT | TTT | TTT |
| 12 | 女か? | rough | ··· | ··· | TTT | TT· | ··· | ··· | ··· |
| 13 | えっはい…そうですけど… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 15 | 面見せてみろ | rough | ··· | ··· | ··· | ··· | ··· | ·TT | ·TT |
| 17 | 閉じ眼でも器量が良けりゃ買い手は… | rough | BTB | BTB | T·T | T·· | BTB | TBT | TBB |
| 18 | やっちょっと…! | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**tojime-dense-action, hand transcription, Spanish**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | マフィア舐めんのも大概にしやがれ | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 4 | 閉じ眼が用心棒だ? | rough | ··· | ··· | T·· | ··· | ··· | ··T | ··· |
| 5 | 腰の銃でイかれた脳みそぶちまけてみるか? | rough | ··· | ··· | ··· | ··T | ··T | ·TT | ··· |
| 6 | あ? | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 8 | ちょっとやめてよ!こんなとこで! | casual | T·· | T·· | ··· | ··· | ··· | ··· | ··· |
| 11 | お前もしかして | rough | TTT | TTT | ··T | ··T | T·· | TTT | ·TT |
| 12 | 女か? | rough | ··· | ··· | ··· | ··T | ··· | ··· | ··· |
| 13 | えっはい…そうですけど… | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 15 | 面見せてみろ | rough | ·T· | ·T· | ·TT | ?·T | T·T | T·· | ··· |
| 17 | 閉じ眼でも器量が良けりゃ買い手は… | rough | ··· | ··· | ·T· | ??· | TT· | ·T? | ·T· |
| 18 | やっちょっと…! | casual | ··· | ··· | ··· | ??· | ··· | ··? | ··· |

**tojime-dense-action, hand transcription, German**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | マフィア舐めんのも大概にしやがれ | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 4 | 閉じ眼が用心棒だ? | rough | ··· | ··· | ·TT | ·TT | ··· | V·T | V·T |
| 5 | 腰の銃でイかれた脳みそぶちまけてみるか? | rough | ··· | ··· | ·TT | ·TT | TT· | ··T | ··T |
| 6 | あ? | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 8 | ちょっとやめてよ!こんなとこで! | casual | ·V· | ·V· | ··T | TTT | ··· | ·T· | ··T |
| 11 | お前もしかして | rough | ·TT | ·TT | TTT | TTT | TTT | ·T· | T·T |
| 12 | 女か? | rough | TT· | TT· | TTT | TTT | ··T | ··· | ··· |
| 13 | えっはい…そうですけど… | polite | ··· | ··· | TT· | ··T | ··· | ··· | ··· |
| 15 | 面見せてみろ | rough | TTT | TTT | ·TT | T·T | T·T | TTT | TTT |
| 17 | 閉じ眼でも器量が良けりゃ買い手は… | rough | ··· | ··· | ··T | ·T· | T·T | V·· | V·T |
| 18 | やっちょっと…! | casual | ··· | ··· | ··· | ·T· | ··T | ··· | ··· |

**tojime-dense-action, hand transcription, Brazilian Portuguese**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 1 | マフィア舐めんのも大概にしやがれ | rough | ·TT | ·TT | ··· | ··· | ··· | TT· | ··T |
| 4 | 閉じ眼が用心棒だ? | rough | ··· | ··· | ··· | TTT | ··· | TT· | ··T |
| 5 | 腰の銃でイかれた脳みそぶちまけてみるか? | rough | ··· | ··· | ··· | TT· | ··· | TT· | ··T |
| 6 | あ? | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 8 | ちょっとやめてよ!こんなとこで! | casual | ··· | ··· | ··· | TT· | ··· | ··· | ··· |
| 11 | お前もしかして | rough | TTT | TTT | TT· | TTT | T·T | TTT | ··T |
| 12 | 女か? | rough | ··· | ··· | ··· | TTT | ··· | ··· | ··T |
| 13 | えっはい…そうですけど… | polite | ··· | ··· | ··· | ··· | ··T | ··· | ··· |
| 15 | 面見せてみろ | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 17 | 閉じ眼でも器量が良けりゃ買い手は… | rough | ··T | ··T | ··· | ·T· | ··· | TT· | ··T |
| 18 | やっちょっと…! | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**balloon-dense-dialogue, real emulator OCR, gated, French**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 10 | こいつ絶対バカだし | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 4 | 大体なんで使い捨てカメラなんだよ! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 1 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual | ··· | ··· | ··· | ·T· | ··· | ··· | ··· |
| 11 | ったく | rough | ··· | ··· | TT· | ··· | ··· | ·T· | ··· |
| 2 | 確かに原理的には可能かもしれないけどな... | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 13 | なにあ然としてんの!?ヘリウム入れないと風船は浮かねーよ!! | rough | TTT | TTT | TTT | TTT | TTT | TTT | TTT |
| 5 | 宇宙でフィルム巻いてシャッターきる気か!? | rough | ··T | ··T | TTT | T·· | T·V | ·TT | ·T· |
| 8 | あっ安かったので... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 3 | わざわざ自分で撮らなくたって... | casual | ··· | ··· | TTT | TTT | T·· | ·TT | TT· |
| 6 | それはですね... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**balloon-dense-dialogue, real emulator OCR, gated, Spanish**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 10 | こいつ絶対バカだし | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 4 | 大体なんで使い捨てカメラなんだよ! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 1 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 11 | ったく | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 2 | 確かに原理的には可能かもしれないけどな... | casual | T·· | T·· | ··T | T·· | T·T | ··· | ··· |
| 13 | なにあ然としてんの!?ヘリウム入れないと風船は浮かねーよ!! | rough | ··· | ··· | ··· | ··· | ··· | ··T | ··· |
| 5 | 宇宙でフィルム巻いてシャッターきる気か!? | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 8 | あっ安かったので... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 3 | わざわざ自分で撮らなくたって... | casual | ··· | ··· | ··T | TTT | ··· | ··T | ··· |
| 6 | それはですね... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**balloon-dense-dialogue, real emulator OCR, gated, German**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 10 | こいつ絶対バカだし | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 4 | 大体なんで使い捨てカメラなんだよ! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 1 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual | T·· | T·· | ··· | ··· | ··· | ··· | ··· |
| 11 | ったく | rough | ··T | ··T | T·T | ··· | ··· | ··· | ··· |
| 2 | 確かに原理的には可能かもしれないけどな... | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 13 | なにあ然としてんの!?ヘリウム入れないと風船は浮かねーよ!! | rough | ··· | ··· | T·· | ··· | ··· | ··· | ··· |
| 5 | 宇宙でフィルム巻いてシャッターきる気か!? | rough | ··· | ··· | ··· | V·· | ··· | ··· | ··· |
| 8 | あっ安かったので... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 3 | わざわざ自分で撮らなくたって... | casual | ··· | ··· | T·T | ·T· | ··· | ··T | ··· |
| 6 | それはですね... | polite | ··· | ··· | ··· | ··· | ··T | ··· | ··· |

**balloon-dense-dialogue, real emulator OCR, gated, Brazilian Portuguese**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 10 | こいつ絶対バカだし | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 4 | 大体なんで使い捨てカメラなんだよ! | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 1 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··T |
| 11 | ったく | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 2 | 確かに原理的には可能かもしれないけどな... | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 13 | なにあ然としてんの!?ヘリウム入れないと風船は浮かねーよ!! | rough | ··· | ··· | TT· | T·T | ·T· | ·TT | ·TT |
| 5 | 宇宙でフィルム巻いてシャッターきる気か!? | rough | ·T· | ·T· | ·TT | ·TT | TT· | TTT | TTT |
| 8 | あっ安かったので... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 3 | わざわざ自分で撮らなくたって... | casual | ·T· | ·T· | TTT | T·T | T·· | ·T· | ·T· |
| 6 | それはですね... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**bourei-p04, real emulator OCR, gated, French**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 3 | 死んでるんですよねぇ | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 1 | 「視える」人間ってヤツでして | polite | ··· | ··· | ··V | ··· | ··· | ··· | ··· |
| 2 | 亡くなったお姉さんのお化粧を? | polite | ·T· | ·T· | ··· | TT· | TTT | TT· | ··· |
| 4 | はい... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**bourei-p04, real emulator OCR, gated, Spanish**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 3 | 死んでるんですよねぇ | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 1 | 「視える」人間ってヤツでして | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 2 | 亡くなったお姉さんのお化粧を? | polite | ·T· | ·T· | TT· | ··· | T·T | ·T· | TT· |
| 4 | はい... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**bourei-p04, real emulator OCR, gated, German**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 3 | 死んでるんですよねぇ | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 1 | 「視える」人間ってヤツでして | polite | ··· | ··· | ··· | ·VV | ··V | ··· | ··· |
| 2 | 亡くなったお姉さんのお化粧を? | polite | TTT | TTT | ··· | T·V | TTT | T·· | ··T |
| 4 | はい... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**bourei-p04, real emulator OCR, gated, Brazilian Portuguese**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 3 | 死んでるんですよねぇ | polite | ··· | ··· | ··· | ··· | ··· | ··· | ·T· |
| 1 | 「視える」人間ってヤツでして | polite | TTT | TTT | ··· | TTT | ·TT | ··T | TTT |
| 2 | 亡くなったお姉さんのお化粧を? | polite | ··· | ··· | ··· | ·T· | ··· | ··· | ··· |
| 4 | はい... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**tojime-dense-action, real emulator OCR, gated, French**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 5 | マフィア舐めんのも大概にしやがれ | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 1 | 腰の銃でイかれた脳みそぶちまけてみるか?あ? | rough | TTT | TTT | TTT | TTT | TTT | TTT | ·TT |
| 7 | お前もしかして | rough | TTT | TTT | TTT | TTT | TTT | TTT | TTT |
| 4 | ちょっとやめてよ!こんなとこで! | casual | TTT | TTT | TTT | TTT | TTT | TTT | TTT |
| 8 | 女か? | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 3 | えっはい...そうですけど... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 9 | 面見せてみろ | rough | TTT | TTT | ··· | T·T | T·T | TTT | TTT |
| 6 | 閉じ眼が用心棒だ? | rough | TTT | TTT | TTT | TTT | TTT | TTT | TTT |
| 2 | 閉じ眼でも器量が良けりゃ買い手は... | rough | TT· | TT· | TTT | TTT | ·TT | TTT | TTT |
| 12 | やっちょっと...! | casual | ··· | ··· | ··· | ··· | ··· | ··· | ··· |

**tojime-dense-action, real emulator OCR, gated, Spanish**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 5 | マフィア舐めんのも大概にしやがれ | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 1 | 腰の銃でイかれた脳みそぶちまけてみるか?あ? | rough | ··· | ··· | ··T | ·T· | T·· | T·· | TTT |
| 7 | お前もしかして | rough | T·T | T·T | TTT | ·T· | TTT | TTT | TT· |
| 4 | ちょっとやめてよ!こんなとこで! | casual | ·T· | ·T· | ·T· | ·T· | ··· | ··· | ··· |
| 8 | 女か? | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 3 | えっはい...そうですけど... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 9 | 面見せてみろ | rough | ·T· | ·T· | ·TT | ··· | ·T· | ·?T | ··T |
| 6 | 閉じ眼が用心棒だ? | rough | T·· | T·· | TTT | ·T· | TTT | ·?· | ·TT |
| 2 | 閉じ眼でも器量が良けりゃ買い手は... | rough | ·T· | ·T· | ··T | ··· | ··T | ·?· | ·T· |
| 12 | やっちょっと...! | casual | ··· | ··· | ··· | ··· | ··· | ·?· | ··· |

**tojime-dense-action, real emulator OCR, gated, German**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 5 | マフィア舐めんのも大概にしやがれ | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 1 | 腰の銃でイかれた脳みそぶちまけてみるか?あ? | rough | TT· | TT· | T·· | TTT | TTT | TTT | TTT |
| 7 | お前もしかして | rough | TT· | TT· | T·T | TTT | TTT | TTT | TTT |
| 4 | ちょっとやめてよ!こんなとこで! | casual | TT· | TT· | ··· | TTT | ··T | TTT | ··· |
| 8 | 女か? | rough | ··· | ··· | T·· | TTT | ··· | ··· | ··· |
| 3 | えっはい...そうですけど... | polite | ·T· | ·T· | ··T | ··· | ··· | ··· | ··· |
| 9 | 面見せてみろ | rough | ·T· | ·T· | T·T | ··T | ·TT | TTT | ··T |
| 6 | 閉じ眼が用心棒だ? | rough | ·TT | ·TT | T·· | TTT | ·T· | ·T· | ··· |
| 2 | 閉じ眼でも器量が良けりゃ買い手は... | rough | ··T | ··T | T·· | T·· | ·TT | ·T· | ·TT |
| 12 | やっちょっと...! | casual | ··· | ··· | ··· | T·T | ··· | ··· | ··T |

**tojime-dense-action, real emulator OCR, gated, Brazilian Portuguese**

| id | text | label | none | none-np | inline-shipped | inline-np | sys-ctl | sys | sys-qwen |
|---|---|---|---|---|---|---|---|---|---|
| 5 | マフィア舐めんのも大概にしやがれ | rough | ··· | ··· | TTT | T·T | ··· | ··· | ·T· |
| 1 | 腰の銃でイかれた脳みそぶちまけてみるか?あ? | rough | ·T· | ·T· | TT· | TTT | T·T | T·T | ·T· |
| 7 | お前もしかして | rough | TTT | TTT | TTT | TTT | TTT | TTT | ·TT |
| 4 | ちょっとやめてよ!こんなとこで! | casual | ··· | ··· | ··· | ··· | ··T | T·· | ·T· |
| 8 | 女か? | rough | ·T· | ·T· | TT· | T·T | ··T | ·T· | ··· |
| 3 | えっはい...そうですけど... | polite | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 9 | 面見せてみろ | rough | ··· | ··· | ··· | ··· | ··· | ··· | ··· |
| 6 | 閉じ眼が用心棒だ? | rough | TTT | TTT | T·T | TTT | TTT | TTT | ·TT |
| 2 | 閉じ眼でも器量が良けりゃ買い手は... | rough | ·T· | ·T· | TTT | TTT | TTT | TTT | ·TT |
| 12 | やっちょっと...! | casual | ··· | ··· | ··· | T·· | ··· | ··· | ··· |


## Appendix C and D: every reply

The 7,500 lines of raw replies (every register reply, and every English re-run reply) were moved out of this note to keep it readable. They are preserved in the [original version of this note](https://github.com/artsaraiva/yomu/blob/173b94769129fb591122e1a93e49f6fbd4e583e1/docs/research/register-hints-target-languages.md#appendix-c-every-register-reply).
