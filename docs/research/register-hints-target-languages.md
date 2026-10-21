# Register hints for non-English targets (research #363)

Research and desktop runs, 2026-10-21. The #350 note ([jev-bubble-class-hints.md](jev-bubble-class-hints.md)) found one positive sign among its hand-written hints: with a French target, register marks made rough lines use *tu* consistently. That run had two flaws. Every mark leaked into the reply, and it tried one wording on two pages. Now that #337 wires the reader's target language into the page prompt, this note asks the question properly. Does a per-bubble register label (polite, casual or rough) visibly improve Qwen2.5-1.5B's T–V choices (*tu*/*vous*, *tú*/*usted*, *du*/*Sie*, *você*/*o senhor*) once the reply cannot carry the label?

**What was run, and on what.**

- **Nothing ran on a phone or an emulator.** Every run is desktop CPU. The emulator OCR used below is #350's, already captured.
- **Hardware:** Apple M4 Pro, 48 GiB, macOS 26.7, CPU only, 4 threads.
- **llama.cpp:** Yomu's pin `7fe450e` (`/usr/bin/git ls-tree origin/main ml/llama.cpp`), fetched fresh and built in the session scratchpad with the Android SDK's CMake 3.22.1 and Ninja. It used the app's CMake options (`LLAMA_BUILD_COMMON=ON`, `GGML_METAL=OFF`, `LLAMA_BLAS=OFF`, tools, tests and examples off; `ml/src/main/cpp/CMakeLists.txt:5-18`), with two desktop differences: `GGML_NATIVE` was left on and `GGML_BACKEND_DL` off. The repository's `ml/llama.cpp` submodule was not touched.
- **Model:** the app's pinned Qwen2.5-1.5B-Instruct Q4_K_M, `bartowski/Qwen2.5-1.5B-Instruct-GGUF@9eadc66`, 986,048,768 B, sha256 `1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370`. That matches `ModelManager.kt:121-122` and `Constants.QWEN25_15B_SIZE`.
- **Harness:** not `llama-server`. It is a small C++ program that compiles the app's own `chat_prompt.cpp`, `grammar_sampler.cpp` and `prompt_budget.h`, copied unchanged from `origin/main` `f44f0cc`. A five-line shim stands in for `<android/log.h>`. Its `main` repeats `nativeLoadModel` and `nativeGenerate` from `llama_jni.cpp` line for line, minus JNI, streaming and the deadline (next section). The prompts are built by a Python port of `buildBatchPrompt` and `buildBatchGrammar`.
- **Inputs:** the three fixture pages (`scripts/speed-fixtures/pages/`, OpenMantra, CC BY-NC 4.0), in two forms:
  - #350's hand transcription (its Appendix A), with #350's panel breaks;
  - #350's real emulator OCR (its Appendix D), as the "gated" prompts, which drop the six garbage reads the way the OCR-confidence gate now does on `main` (#390).
- **Seeds:** 7, 8 and 9 for every arm. The phone draws a random seed per page (`seed = -1`, `core/src/main/java/com/yomu/core/TranslationSlot.kt:67`).
- **Scripts** live in the session scratchpad, not in the repo: `/private/tmp/claude-502/-Users-ctw04928-Documents-Projects-personal-projects-yomu-manga-translation-app/363-register-hints/` (`harness/` for the C++ replica, `exp/` for the arms, analysis and appendix generators, `exp/results/` for every raw reply as JSONL).

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

Each arm ran on both inputs (hand, real), three pages, four languages and three seeds: 72 replies per arm, 504 in all. [Appendix A](#appendix-a-prompts-grammars-and-reference-labels) has every prompt shape, and [Appendix C](#appendix-c-every-register-reply) every reply.

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

**Replacing the default system turn changes the output on its own.** The `sys-ctl` arm swaps "You are Qwen, created by Alibaba Cloud. You are a helpful assistant." for "You are a manga translator." and nothing else. In English ([Appendix D](#appendix-d-every-english-re-run-reply)):

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


## Appendix C: every register reply

Desktop llama.cpp `7fe450e`, CPU, 4 threads, the app's sampler chain, grammar sampler and chat-template code. Seeds 7, 8 and 9. Each block gives the call time and token counts per seed. A seed whose reply is byte-identical to an earlier seed's is marked as such rather than repeated. `none-np` is not repeated anywhere: all 72 of its replies are byte-identical to `none`'s.


### balloon-dense-dialogue, hand transcription, French

**none** · seed 7: 2698 ms, 270 prompt / 190 reply tokens · seed 8: 2732 ms, 270 prompt / 191 reply tokens · seed 9: 2769 ms, 270 prompt / 195 reply tokens

```text
--- seed 7 ---
[1] Seulement si on recueille autant de ballons d'air, ils ne vont pas monter.
[2] Il est certainement bête.
[3] Mmm
[4] Qu'est-ce qui te prend!
[5] Sans helium, les ballons d'air ne montent pas.
[6] C'est probablement une caméra à usage unique.
[7] C'est super bien exposé.
[8] Ah, c'était pas cher!
[9] Pourquoi vouloir photographier à l'astronaute alors?
[10] C'est trop laid!
[11] Oui, bien sûr, c'est possible, mais...
[12] On peut trouver n'importe où sur le net.
[13] Pourquoi vouloir faire ça?
[14] Ah
[15] Oui, bien sûr...
--- seed 8 ---
[1] Seulement si tu accumules des ballons d'air, ils ne vont pas monter.
[2] Il est certainement bête.
[3] Mmm
[4] Qu'est-ce que c'est que ça!?
[5] Sans helium, les ballons d'air ne vont pas monter.
[6] C'est probablement une caméra à usage unique.
[7] C'est super bien exposé.
[8] Ah, c'était pas cher!
[9] Pourrais-tu faire ça dans l'espace?
[10] C'est trop bizarre.
[11] Oui, c'est possible, mais...
[12] Il y a des photos sur Internet pour tout, n'importe où.
[13] Tu n'as pas besoin de te les prendre toi-même.
[14] Ah
[15] C'est vrai...
--- seed 9 ---
[1] Seulement si on collecte des ballons d'air, ils ne vont pas monter.
[2] Il est certainement stupide.
[3] Mmm
[4] Qu'est-ce que c'est que ça!?
[5] Sans helium, les ballons ne vont pas monter.
[6] C'est probablement une caméra à usage unique.
[7] C'est vraiment bien pour les photos.
[8] Ah, c'était pas cher!
[9] Pourquoi vouloir prendre des photos dans l'espace?
[10] C'est vraiment trop!
[11] Oui, mais en principe, c'est possible.
[12] On peut toujours poster des photos sur le net, n'est-ce pas?
[13] Pourquoi vouloir les prendre soi-même?
[14] Ah!
[15] C'est bien, hein...
```

**inline-shipped** · seed 7: 3730 ms, 357 prompt / 258 reply tokens · seed 8: 3642 ms, 357 prompt / 248 reply tokens · seed 9: 3856 ms, 357 prompt / 271 reply tokens

```text
--- seed 7 ---
[1] (rude) Il n'y a jamais eu d'air dans un ballon vide!
[2] (rude) Il est vraiment un imbécile!
[3] (rude) Mmm...
[4] (casual) Qu'est-ce qui te prend!
[5] (rude) Sans hélicolène, le ballon ne monte pas!
[6] (rude) C'est une caméra à usage unique!
[7] (rude) C'est super bien!
[8] (polite) C'était vraiment cher!
[9] (rude) Pourquoi vouloir photographier dans l'espace alors que vous avez un appareil photo?
[10] (rude) C'est quoi, la tête!
[11] (casual) Oui, bien sûr, mais c'est un peu compliqué!
[12] (casual) Il y a des photos de la Terre sur Internet, n'importe où!
[13] (casual) Pourquoi vouloir faire une photo alors que vous avez un appareil photo?
[14] (casual) Ah!
[15] (polite) C'est vrai...
--- seed 8 ---
[1] (rude) Il est impossible de faire monter autant de ballons dans l'air!
[2] (rude) Il est certain qu'il est bête!
[3] (rude)
[4] (casual) Qu'est-ce que c'est que ça!?
[5] (rude) Sans helium, le ballon ne montera pas!
[6] (rude) C'est probablement une caméra à usage unique!
[7] (rude)
[8] (polite) Je suis désolé, mais...
[9] (rude) Comment peux-tu vouloir photographier dans l'espace sans un film?
[10] (rude) Qu'est-ce que c'est que ça!?
[11] (casual) Oui, c'est possible, mais...
[12] (casual) Il y a des photos de la Terre sur Internet, n'est-ce pas?
[13] (casual) Tu n'as pas besoin de prendre des photos pour...
[14] (casual) Ah!
[15] (polite) C'est vrai...
--- seed 9 ---
[1] (rude) Il n'y a pas de vent soufflant qui puisse faire monter un ballon vide!
[2] (rude) Il est certainement un imbécile!
[3] (rude)
[4] (casual) Qu'est-ce que c'est que ça!?
[5] (rude) Sans hélicolène, le ballon ne peut pas monter!
[6] (rude) C'est probablement une caméra à usage unique!
[7] (rude)
[8] (polite) Je suis désolé, mais...
[9] (rude) Pourquoi vouloir photographier dans l'espace alors que vous avez des appareils photo à votre disposition?
[10] (rude) C'est quoi, la guerre?
[11] (casual) Oui, bien sûr, mais c'est possible?
[12] (casual) Il y a des photos de la Terre sur Internet, n'est-ce pas?
[13] (casual) Pourquoi vouloir prendre des photos alors que vous avez un appareil photo à votre disposition?
[14] (rude)
[15] (polite) Oui, bien sûr...
```

**inline-np** · seed 7: 3543 ms, 357 prompt / 241 reply tokens · seed 8: 3048 ms, 357 prompt / 192 reply tokens · seed 9: 3625 ms, 357 prompt / 247 reply tokens

```text
--- seed 7 ---
[1] "Toutefois, même si on collecte des ballons dans l'air, ils ne vont pas flotter!"
[2] "C'est sûr qu'il est bête!"
[3] "Mmm"
[4] "Qu'est-ce que c'est que ça?!"
[5] "Sans hélicolène, le ballon ne va pas flotter!"
[6] "En général, c'est une caméra à usage unique!"
[7] "Super bien!"
[8] "Je suis désolé, mais c'était vraiment cher!"
[9] "Est-ce que tu as l'intention de tourner des films dans l'espace?"
[10] "Tout à fait!"
[11] "En effet, en théorie, c'est possible, mais..."
[12] "Sur le net, il y a des dizaines de milliers d'images de la Terre!"
[13] "Et si tu n'en fais pas une photo, tu vas t'en faire un gros problème!"
[14] "Ah!"
[15] "C'est vrai, mais..."
--- seed 8 ---
[1] "Just as many balloons filled with air won't float, no matter how many you collect!"
[2] "He's definitely a fool!"
[3] "Mug!"
[4] "What's the big deal!?"
[5] "Helium is needed for the balloon to float!"
[6] "It's a disposable camera, basically!"
[7] "They're really good at taking pictures!"
[8] "I'm sorry, it was quite expensive!"
[9] "Are you crazy? You want to take pictures in space!?"
[10] "What a jerk!"
[11] "Yes, technically it's possible, but... "
[12] "There are plenty of pictures of Earth on the internet, right?"
[13] "You'd better take a picture yourself, you know!"
[14] "Ah!"
[15] "That's right..."
--- seed 9 ---
[1] "Toutefois, même si vous collectez des ballons dans l'air, ils ne vont pas flotter!"
[2] "C'est sûr qu'il est bête!"
[3] "Mu-guu"
[4] "Qu'est-ce que vous voulez dire!?"
[5] "Sans hélicolène, le ballon ne va pas flotter!"
[6] "En général, c'est une caméra à déchets."
[7] "Très bien, très bien!"
[8] "Je suis désolé, c'était cher!"
[9] "Est-ce que vous voulez que je filme en film dans l'espace?"
[10] "Tout à fait!"
[11] "C'est vrai, mais c'est possible, mais je ne suis pas sûr."
[12] "Tout le monde peut poster des photos de la Terre sur le net, n'est-ce pas?"
[13] "Et si vous ne le faites pas, c'est votre faute!"
[14] "Ah!"
[15] "C'est vrai, mais je ne suis pas sûr."
```

**sys-ctl** · seed 7: 2917 ms, 260 prompt / 211 reply tokens · seed 8: 2878 ms, 260 prompt / 208 reply tokens · seed 9: 2684 ms, 260 prompt / 186 reply tokens

```text
--- seed 7 ---
[1] Seulement, même si on collecte des ballons d'air, ils ne vont pas monter!
[2] Il est certainement bête!
[3] muguu
[4] Qu'est-ce qui te prend?!?
[5] Sans hélicolène, le ballon ne monte pas!
[6] En général, c'est une caméra à déchets!
[7] Très bien, très bien!
[8] Ah, c'était pas cher!
[9] Pourquoi vouloir faire des photos dans l'espace?!?
[10] Qu'est-ce que tu raconte?!?
[11] Oui, bien sûr, c'est possible, mais...
[12] On peut trouver n'importe où des photos de la Terre sur le net!
[13] Pourquoi vouloir prendre une photo si on peut trouver des photos sur le net?!?
[14] Ah!
[15] C'est vrai...
--- seed 8 ---
[1] Seulement, même si on collecte des ballons d'air, ils ne vont pas flotter!
[2] Il est certainement bête!
[3] mugga
[4] Qu'est-ce que c'est!?
[5] Sans helium, le ballon ne va pas flotter!
[6] C'est probablement une caméra à usage unique!
[7] C'est super bien!
[8] Ah, c'était pas cher!
[9] Pourquoi vouloir prendre des photos dans l'espace!?
[10] Trop de saleté!
[11] Oui, c'est possible, mais...
[12] Il y a des photos de la Terre sur Internet, partout!
[13] Pourquoi vouloir faire des photos avec une caméra alors qu'on peut faire ça avec une caméra mobile!
[14] Ah!
[15] Oui, c'est vrai...
--- seed 9 ---
[1] Seulement, même si on collecte tous les ballons d'air, ils ne vont pas flotter!
[2] Il est certainement bête!
[3] mogg
[4] Qu'est-ce que c'est que ça!?
[5] Sans helium, le ballon ne va pas flotter!
[6] C'est probablement une caméra à usage unique!
[7] C'est super bien!
[8] Ah, c'était pas cher!
[9] Pourquoi vouloir prendre des photos dans l'espace!?
[10] Tant pis!
[11] En effet, c'est possible, mais...
[12] Il y a des photos sur Internet pour tout!
[13] Pourquoi vouloir les prendre soi-même...
[14] Ah!
[15] C'est vrai...
```

**sys** · seed 7: 3117 ms, 344 prompt / 199 reply tokens · seed 8: 3045 ms, 344 prompt / 197 reply tokens · seed 9: 2952 ms, 344 prompt / 190 reply tokens

```text
--- seed 7 ---
[1] Seulement, même si on collecte tous les ballons d'air, ils ne vont pas flotter!
[2] Il est certainement un idiot!
[3] Mu-gg!
[4] Qu'est-ce qui te prend!?
[5] Sans hélicolène, le ballon ne va pas flotter!
[6] Il semble que c'est une caméra à usage unique!
[7] C'est super bien!
[8] Ah, c'était pas cher!
[9] Pourquoi vouloir photographier l'espace avec un film?
[10] C'est quoi, la tête!
[11] Oui, bien sûr, c'est possible, mais...
[12] Il y a des photos sur Internet qui tombent à l'eau!
[13] Pourquoi vouloir les prendre à la main...
[14] Ah!
[15] C'est vrai...
--- seed 8 ---
[1] Seulement les ballons de vent que vous accumulez ne seront jamais en suspension!
[2] Il est certainement un imbécile!
[3] Mu-gg!
[4] Qu'est-ce qui te prend!?
[5] Sans hélicolène, les ballons de vent ne seront jamais en suspension!
[6] En général, c'est une caméra à disque!
[7] C'est vraiment bien!
[8] Ah, c'était pas cher!
[9] Pourquoi vouloir photographier dans l'espace!?
[10] Trop de saleté!
[11] Oui, c'est possible selon les principes, mais...
[12] Il y a des photos sur Internet pour chaque pays sur la Terre!
[13] Pourquoi vouloir les prendre soi-même...
[14] Ah!
[15] C'est vrai...
--- seed 9 ---
[1] Seulement, même si on collecte des ballons d'air, ils ne vont pas monter!
[2] Il est certainement bête!
[3] Mmm
[4] Qu'est-ce que c'est!?
[5] Sans hélicolène, les ballons ne montent pas!
[6] En général, c'est une caméra à disque!
[7] Ça donne de très bons résultats!
[8] Ah, c'était trop cher!
[9] Pourquoi vouloir photographier dans l'espace!?
[10] C'est quoi!?
[11] Oui, mais en principe, c'est possible!
[12] Il y a des photos sur Internet pour tout!
[13] Pourquoi vouloir les prendre soi-même...?
[14] Ah!
[15] C'est vrai...
```

**sys-qwen** · seed 7: 3143 ms, 354 prompt / 207 reply tokens · seed 8: 3153 ms, 354 prompt / 206 reply tokens · seed 9: 3105 ms, 354 prompt / 199 reply tokens

```text
--- seed 7 ---
[1] ただ les ballons d'air n'iront jamais en l'air, quelle que soit la quantité qu'on les rassemble!
[2] Il est certainement bête!
[3] mmm
[4] Qu'est-ce qui te prend!?
[5] Sans hélicolène, les ballons d'air ne vont pas monter!
[6] En général, c'est une caméra à usage unique!
[7] C'est super bien!
[8] Ah, c'était pas cher!
[9] Pourquoi vouloir prendre des photos dans l'espace!?
[10] Tant pis!
[11] Oui, bien sûr, c'est possible, mais...
[12] Les photos de la Terre tombent partout sur le net, n'est-ce pas?
[13] Pourquoi vouloir les prendre soi-même...
[14] Ah!
[15] C'est vrai...
--- seed 8 ---
[1] ただ les ballons d'air n'iront pas jusqu'à l'air libre, quoi!!
[2] C'est sûr qu'il est bête!
[3] Mmm
[4] Qu'est-ce qui te prend!?
[5] Sans hélicolène, les ballons d'air ne vont pas voler!
[6] En gros, c'est une caméra à disque!
[7] Très bien, ça donne des photos!
[8] Ah, c'était pas cher!
[9] Pourquoi vouloir photographier dans l'espace, alors!?
[10] Trop d'ennui!
[11] Oui, bien sûr, c'est possible, mais...
[12] Les photos de la Terre, elles tombent partout sur Internet!
[13] Pourquoi vouloir les prendre moi-même, alors?
[14] Ah!
[15] C'est vrai...
--- seed 9 ---
[1] ただ les ballons d'air n'iront pas jusqu'à ce qu'on les ait tous rassemblés!
[2] C'est certainement un imbécile!
[3] Mu-gu!
[4] Qu'est-ce que ça veut dire!?
[5] Sans hélicolène, les ballons d'air ne vont pas monter!
[6] En général, c'est une caméra à disque!
[7] C'est super bien!
[8] Ah, c'était pas cher!
[9] Pourquoi vouloir prendre des photos dans l'espace!?
[10] Je suis vraiment déçu!
[11] Oui, c'est possible, mais...
[12] Les photos de la Terre sont partout sur Internet!
[13] Pourquoi vouloir les prendre soi-même...
[14] Ah!
[15] C'est vrai...
```


### balloon-dense-dialogue, hand transcription, Spanish

**none** · seed 7: 3227 ms, 270 prompt / 242 reply tokens · seed 8: 2764 ms, 270 prompt / 195 reply tokens · seed 9: 2666 ms, 270 prompt / 184 reply tokens

```text
--- seed 7 ---
[1] Solo, aunque se esfuerzan a boga, no se puede levantar ni un solo globo.
[2] Eso es obvio, ¡está loco!
[3] Mu-guu
[4] ¡Qué extraño! ¿Qué pasa?
[5] No se puede levantar ni un solo globo si no se mete heliúm.
[6] ¿Por qué? Es una cámara de uso único.
[7] ¡Es genial! ¡Es increíble!
[8] ¡Qué mala suerte! ¡Fue muy caro!
[9] ¡Por qué no se toma una foto en el espacio con un film?! ¡Qué locura!
[10] ¡Qué loco!
[11] ¡Por supuesto! Pero no es posible.
[12] ¡Qué extraño! ¡¿Por qué no se toma una foto en la Tierra?
[13] ¡¿Por qué no se toma una foto en la Tierra?!
[14] ¡Qué mala suerte!
[15] ¡Sí, eso es verdad!
--- seed 8 ---
[1] Solo, aunque se trate de tantos cohetes de aire, nunca se levantarán.
[2] Este es absolutamente loco.
[3] Mu-gg
[4] ¡Qué extraño! ¿Qué pasa?
[5] No se levantarán sin el hidrógeno.
[6] Es una cámara de película de uso único.
[7] ¡Es genial!
[8] ¡Qué asequible!...
[9] ¿Cómo se atreve a intentarlo en el espacio?
[10] ¡Qué absurdo!
[11] ¡Por supuesto, en teoría, es posible!
[12] ¡Pero en la web, se pueden encontrar millones de fotos de la Tierra!
[13] ¡Y si no lo hago yo mismo!
[14] ¡Ah!
[15] ¡Sí, eso es verdad!
--- seed 9 ---
[1] Solo, aunque se esfuerzan a boga, no pueden levantar un globo vacío.
[2] Este es absolutamente loco.
[3] Mu-guu
[4] ¿Qué demonios!?
[5] No se puede levantar un globo sin hidrógeno.
[6] Es probablemente una cámara de fotos de uso único.
[7] ¡Qué fotos!
[8] ¡Qué mala suerte! ¡Era tan barata!
[9] ¿Por qué no se pueden hacer fotos en el espacio con film?
[10] ¡Qué mierda!
[11] ¡Claro que sí! Pero...
[12] ¡Qué fotos!
[13] ¡Por qué no se las hace uno mismo!
[14] ¡Ah!
[15] ¡Eso es verdad!
```

**inline-shipped** · seed 7: 4951 ms, 357 prompt / 389 reply tokens · seed 8: 3641 ms, 357 prompt / 249 reply tokens · seed 9: 3575 ms, 357 prompt / 244 reply tokens

```text
--- seed 7 ---
[1] (rough) Solo que con tantos fogonazos de aire en el balón, ¿cómo va a flotar?
[2] (rough) ¡Este es un estúpido!
[3] (rough) Mu-gg
[4] (casual) ¿Qué demonios!?
[5] (rough) ¡Sin hidrógeno, el balón no flota!
[6] (rough) Eso es, es una cámara de fotos de uso único.
[7] (polite) ¡Es genial!
[8] (polite) ¡Oh, ¡era un poco caro!
[9] (rough) ¿Cómo que en el espacio? ¡No puedes hacer eso!
[10] (rough) ¡Qué mierda!
[11] (casual) ¡Claro, pero...!
[12] (casual) ¡En internet, puedes encontrarlo en cualquier parte!
[13] (casual) ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[14] (casual) ¡Ah!
[15] (polite) ¡Eso es cierto!
--- seed 8 ---
[1] (rough) Solo que, con tantos fogonazos, no va a flotar ni un solo globo.
[2] (rough) Este es un completo imbécil.
[3] (rough) Mu-guuu
[4] (casual) ¿Qué demonios?!
[5] (rough) No flota sin hidrógeno.
[6] (rough) Es una cámara de fotos de uso temporal.
[7] (polite) ¡Es genial!
[8] (polite) ¡Oh, estaba un poco caro!
[9] (rough) ¿Cómo que no puedo filmar con la cámara en el espacio?
[10] (rough) ¡Qué mierda!
[11] (casual) ¡Sí, en teoría sí, pero...
[12] (casual) ¡Pues claro! ¡Puedo estar en la Luna!
[13] (casual) ¡Pues claro! ¡No me apetecía!
[14] (polite) ¡Ah!
[15] (polite) ¡Sí, eso es cierto!
--- seed 9 ---
[1] (rough) Solo que con tantos fogones en el aire, no va a flotar ni un poquito!!
[2] (rough) Ese es un tonto
[3] muguu
[4] (casual) ¿Qué?!
[5] (rough) No flota sin el hidrógeno
[6] (rough) Eso es porque es un cámara de fotos de plástico
[7] ¡Qué bien! ¡Qué buena foto!
[8] (polite) ¡Oh, fue tan caro!
[9] (rough) ¿Cómo que con la película? ¡No se puede hacer eso en el espacio!
[10] (rough) ¡Qué mierda!
[11] (casual) ¡Por supuesto, pero...!
[12] (casual) ¡¿Y qué? ¡Tanto como en la web!
[13] (casual) ¡¿Y qué? ¡No me hagas eso!
[14] (casual) ¡Ah!
[15] (polite) ¡Eh, eso es cierto...!
```

**inline-np** · seed 7: 3122 ms, 357 prompt / 194 reply tokens · seed 8: 3497 ms, 357 prompt / 235 reply tokens · seed 9: 3536 ms, 357 prompt / 237 reply tokens

```text
--- seed 7 ---
[1] Pero, aunque se esconda en un balón de aire, no se saldrá del agua.
[2] ¡Ese es un estúpido!
[3] Mu-gg
[4] ¡Qué extraño! ¿Qué pasa?
[5] ¡No se puede flotar sin hidrógeno!
[6] ¡Es una cámara de fotos de uso único!
[7] ¡Es genial!
[8] ¡Oh, estaba un poco caro!
[9] ¡¿Cómo se atreve a disparar en el espacio con una cámara de fotos?
[10] ¡Qué mala suerte!
[11] ¡Eso es cierto, pero...
[12] ¡¿Qué? ¡¿Qué?!
[13] ¡¿Qué? ¡¿Qué?!
[14] ¡Oh!
[15] ¡Eso es cierto!
--- seed 8 ---
[1] "Pero, incluso si se recoge una infinidad de fogatas, no se puede levantar."
[2] "¡Este es un estúpido!"
[3] "Mugüüüüü!"
[4] "¡Qué extraño! ¿Qué pasa?"
[5] "¡No se puede levantar sin hidrógeno!"
[6] "¡Es probablemente una cámara de fotos de uso único!"
[7] "¡Es genial! ¡Espero que sea genial!"
[8] "¡Oh, ¡fue caro!"
[9] "¡¿Cómo se atreve a tirar una cámara con un film en el espacio?! "
[10] "¡Qué mierda!"
[11] "¡Eso es cierto, pero... "
[12] "¡¿Qué? ¿En internet hay tantas fotos de la Tierra?!"
[13] "¡¿Por qué no se toma una foto?!"
[14] "¡Oh! "
[15] "¡Eso es cierto!"
--- seed 9 ---
[1] "Pero, aunque se esfuerzan a bote pronto, el globo no flota."
[2] "¡Esto es un estúpido!"
[3] "Mu-gg"
[4] "¡Qué extraño! ¿Qué pasa?"
[5] "Sin hidrógeno, el globo no flota."
[6] "Eso es, es un cámara de fotos de plástico."
[7] "¡Muy bien! ¡Espero que estés satisfecho!"
[8] "¡Oh, ¡fue caro!"
[9] "¡¿Cómo puedes filmar en el espacio?! ¡No puedes hacer eso!"
[10] "¡Qué mierda!"
[11] "¡Sí, pero... ¡no es posible!"
[12] "¡¿Qué? ¡¿Qué? ¡¿Qué?!"
[13] "¡¿Por qué no te das el tiempo para hacerlo?!"
[14] "¡Oh!"
[15] "¡Sí, pero... ¡no es posible!"
```

**sys-ctl** · seed 7: 2579 ms, 260 prompt / 178 reply tokens · seed 8: 2793 ms, 260 prompt / 195 reply tokens · seed 9: 2850 ms, 260 prompt / 201 reply tokens

```text
--- seed 7 ---
[1] Solo, aunque se esfuerzan a borrar, no se puede levantar.
[2] Este es loco.
[3] Mu-guu
[4] ¿Qué demonios está diciendo?
[5] No se puede levantar sin hidrógeno.
[6] Es una cámara de fotos de uso único.
[7] ¡Es increíble!
[8] ¡Qué mala compra!...
[9] ¿Por qué no se puede filmar en el espacio?
[10] ¡Qué mierda!
[11] Sí, es posible, pero...
[12] ¿En qué mundo se ha metido?
[13] ¿Por qué no se ha tomado una foto?
[14] ¡Qué mierda!
[15] Sí, es cierto.
--- seed 8 ---
[1] Solo, aunque intentes juntar tantos globoles de aire, nunca van a flotar!
[2] Él definitivamente es un imbécil
[3] Mu-gg
[4] ¿Qué demonios!?
[5] No flotará sin el hidrógeno
[6] Es un reloj de mano
[7] ¡Qué bonito!
[8] ¡Qué mala compra!...
[9] ¿Por qué no se puede hacer una cámara en el espacio?
[10] ¡Qué mierda!
[11] Sí, pero... no estoy seguro de si es posible...
[12] ¿Qué demonios, en internet hay tantas fotos de la Tierra?
[13] ¿Por qué no se la toma uno mismo?
[14] ¡Qué mierda!
[15] ¡Eh!...
--- seed 9 ---
[1] Solo, aunque se esfuerzan a bocajarro, no van a poder levantar el globo.
[2] Éste definitivamente es un bobo.
[3] Mu-guu
[4] ¿Qué demonios!?
[5] No se puede levantar un globo sin hidrógeno.
[6] Parece ser que es una cámara de fotos de uso único.
[7] ¡Es increíblemente bien retratado!
[8] ¡Qué mala suerte!...
[9] ¿Por qué no se pueden tomar fotos en el espacio con film?
[10] ¡Qué mierda!
[11] Claro, pero...
[12] ¡Qué mierda! ¡Tanto como en la web!
[13] ¡Y además, no se puede hacer eso!
[14] ¡Qué mierda!
[15] ¡Sí, eso!
```

**sys** · seed 7: 3240 ms, 344 prompt / 210 reply tokens · seed 8: 2970 ms, 344 prompt / 188 reply tokens · seed 9: 3012 ms, 344 prompt / 183 reply tokens

```text
--- seed 7 ---
[1] Solo que, aunque intentes juntar tantos baloncitos de aire, nunca van a flotar.
[2] Éste es definitivamente un tonto.
[3] Mu-gg
[4] ¿Qué demonios está pasando aquí?
[5] No flotarán sin hidrógeno.
[6] ¿Por qué usan cámaras de fotos de plástico?
[7] ¡Es genial! ¡Es increíble!
[8] ¡Qué mala compra! ¡Fue muy caro!
[9] ¿Por qué no se filman en el espacio?
[10] ¡Qué mala idea!
[11] ¡Claro que es posible, pero...
[12] ¡En internet hay millones de fotos de la Tierra!
[13] ¡¿Por qué no se las toma ellos mismos?
[14] ¡Ah!
[15] ¡Sí, eso es verdad!
--- seed 8 ---
[1] Solo que, aunque intentes recoger tantos estibos, no van a flotar.
[2] Este es un idiota.
[3] Mu-gg
[4] ¡¿Qué demonios!?
[5] No flotará sin hidrógeno.
[6] ¿Por qué no es un cámara de película?
[7] ¡Es genial!
[8] ¡Qué mala compra!
[9] ¿Por qué no se filma en el espacio?
[10] ¡Qué mierda!
[11] ¡Por supuesto, te lo puedes permitir, pero no es lo mismo!
[12] ¡En la web hay miles de fotos del planeta!
[13] ¡Y además, te has pasado!
[14] ¡Mierda!
[15] ¡Sí, eso es cierto!
--- seed 9 ---
[1] Solo que, aunque coleccionas tantos globos de aire, no van a flotar.
[2] Él definitivamente es estúpido.
[3] Mu-gg!
[4] ¿Qué importa eso!?
[5] No flotarán sin hidrógeno.
[6] ¿Por qué se usa un cámara de película?
[7] ¡Es genial! ¡Es increíble!
[8] ¡Qué mala idea!
[9] ¿Por qué no se filman en el espacio?
[10] ¡Qué mierda!
[11] Sí, es posible, pero...
[12] ¿Por qué se usa una cámara de película?
[13] ¡¿Por qué?!
[14] ¡Qué mierda!
[15] ¡Eh!...
```

**sys-qwen** · seed 7: 2838 ms, 354 prompt / 167 reply tokens · seed 8: 7874 ms, 354 prompt / 664 reply tokens · seed 9: 3041 ms, 354 prompt / 190 reply tokens

```text
--- seed 7 ---
[1] Solo que, aunque coleccionas cuánto quieras, no flota.
[2] Este es definitivamente un bobo.
[3] Mu-gg
[4] ¡¿Qué rayos!?
[5] No flota sin hidrógeno.
[6] ¿Por qué se usa una cámara de película?
[7] ¡Qué buenos son!
[8] ¡Qué mala idea!
[9] ¿Cómo se atreve a filmar en el espacio?
[10] ¡Qué mierda!
[11] Sí, pero técnicamente es posible.
[12] ¡Qué fotos!
[13] ¡Por qué te metes en esto!
[14] ¡Ah!
[15] ¡Eh!
--- seed 8 ---
[1] Solo que, aunque coleccionas tantos globos de aire, nunca van a flotar.
[2] Esta es una completa estupidez.
[3] Mu-gg!
[4] ¡¿Qué demonios!?
[5] No flotarán sin hidrógeno.
[6] Aunque probablemente sea una cámara de uso único, es una cámara de fotos.
[7] ¡Qué fotos!
[8] ¡Qué mala suerte! ¡Estaba tan seguro de que era barato!
[9] ¡¿Cómo puedes hacer fotos en el espacio!?
[10] ¡Qué mierda!
[11] ¡Por supuesto, en teoría, es posible!
[12] ¡¿Qué? ¡¿Qué?!
[13] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[14] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[15] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 9 ---
[1] Solo que, aunque coleccionas tantos fogones, nunca van a flotar.
[2] Este es absolutamente loco.
[3] Mu-gg!
[4] ¡Por qué no está asombrado!
[5] No flotarán sin hidrógeno.
[6] ¿Por qué se usa una cámara de fotos de plástico?
[7] ¡Es genial!
[8] ¡Qué mala compra!
[9] ¿Por qué no se filma en la Luna?
[10] ¡Qué mala idea!
[11] Sí, en teoría, es posible, pero…
[12] ¡Qué mala idea!
[13] ¡No, no lo hago por mala voluntad!
[14] ¡Qué mala idea!
[15] ¡Sí, eso es verdad!
```


### balloon-dense-dialogue, hand transcription, German

**none** · seed 7: 2990 ms, 270 prompt / 217 reply tokens · seed 8: 2990 ms, 270 prompt / 215 reply tokens · seed 9: 2877 ms, 270 prompt / 205 reply tokens

```text
--- seed 7 ---
[1] Nur wenn du so viele Luftballons zusammenkommst, wird es auch nicht fliegen!
[2] Das ist definitiv ein Idiot!
[3] Muuuuuggh!
[4] Was?!
[5] Ohne Helium wird der Ballon nicht fliegen!
[6] Das ist eine Filmkamera, die verwendet wird, wenn man keine Filme hat!
[7] Das ist wirklich sehr gut!
[8] Ach, das war zu teuer!
[9] Kannst du das Filmpapier auf dem Mond machen und die Kamera schalten?
[10] Ich meine, das ist wirklich nicht möglich!
[11] Ja, ich weiß, das ist möglich, aber...
[12] Auf der ganzen Welt gibt es Fotos, die man auf der Internetseite herunterladen kann!
[13] Warum musstest du dich selbst fotografieren?
[14] Ach, das tut mir leid!
[15] Ja, das ist richtig...
--- seed 8 ---
[1] Nur wenn du so viele Luftballons zusammenkommst, wird es auch nicht fliegen!
[2] Das ist definitiv ein Idiot!
[3] Muuuuugghh
[4] Was?!
[5] Du musst Helium in den Ballons lassen, damit sie fliegen!
[6] Das ist eine Dummheit! Das ist eine Dummheit!
[7] So gut sind die Fotos!
[8] Ach, das war zu teuer gewesen...
[9] Kannst du das Filmpapier in der Galaxis fotografieren und die Kamera schalten?
[10] So was!
[11] Ja, das ist möglich, aber... 
[12] Auf der ganzen Welt gibt es Fotos von der Erde, die man im Internet finden kann!
[13] Du könntest sie auch selbst machen, aber... 
[14] Ach, das war so teuer!
[15] Ja, das ist so!
--- seed 9 ---
[1] Nur wenn du so viele Luftballons zusammenkrönen, wird es nicht fliegen!
[2] Das ist definitiv ein Idiot!
[3] Muuuuugghh!
[4] Was?!
[5] Ohne Helium wird der Ballon nicht fliegen!
[6] Das ist eine Einweg-Kamera!
[7] So gut ist das!
[8] Ach, das war zu teuer!
[9] Was, ist es denn so schwierig, eine Kamera mit Filmpaketen zu fotografieren?
[10] So was!
[11] Ja, das ist möglich, aber ...
[12] Auf der ganzen Welt fallen schon Fotos vom Planeten von den Netzwerken herunter!
[13] Was, hast du dich überhaupt nicht um die Aufnahme gekümmert?
[14] Ach, das ist ja auch so!
[15] Ja, das ist so!
```

**inline-shipped** · seed 7: 4021 ms, 357 prompt / 292 reply tokens · seed 8: 3722 ms, 357 prompt / 260 reply tokens · seed 9: 3604 ms, 357 prompt / 249 reply tokens

```text
--- seed 7 ---
[1] (rough) Nur wenn du eine Luftballonflugzeugkiste voller Luft hältst, wird sie nicht fliegen!
[2] (rough) Das ist definitiv ein Idiot!
[3] (rough) Mu-gg!
[4] (casual) Was ist das für ein Unsinn!?
[5] (rough) Ohne Helium kann die Luftballonflugzeugkiste nicht fliegen!
[6] (rough) Das ist eine Dummheit, die man mit einem Filmkamera machen kann!
[7] (polite) Ich habe es mir gedacht, das war ein großer Fehler!
[8] (casual) Ich habe es mir gedacht, das war ein großer Fehler!
[9] (rough) In der Galaxis kann man Filme mit einem Kamera schneiden!
[10] (rough) Ach du Scheiße!
[11] (casual) Ja, das ist wahrscheinlich möglich, aber...
[12] (casual) Auf der Welt gibt es Hunderte von Fotos, die man auf der Internetseite herunterladen kann!
[13] (casual) Man sollte sich nicht die Mühe machen, selbst zu fotografieren...
[14] (casual) Ach du Scheiße!
[15] (polite) Das ist richtig...
--- seed 8 ---
[1] (rough) Nur wenn du die Luftballonchen ausreichend viele zusammenkommst, werden sie nicht fliegen!
[2] (rough) Dieser ist definitiv dumm!
[3] mugg
[4] (casual) Was ist das für ein Unsinn!?
[5] (rough) Ohne Helium kann der Luftballonchen nicht fliegen!
[6] (rough) Das ist eine Taschenlampe!
[7] super
[8] (polite) Ich habe es mir nicht verkraftet, also...
[9] (rough) In der Galaxis würde ich mich nicht vor dem Klicken der Kamera für den Film aufhalten lassen!
[10] (rough) Ach du Scheiße!
[11] (casual) Das ist ja auch nicht unmöglich, aber... 
[12] (casual) Auf der Welt gibt es so viele Fotos, dass sie auf der Welt von der Welt selbst geliefert werden!
[13] (casual) Aber... 
[14] (casual) Ach du Schlimmes!
[15] (polite) Das ist ja auch nicht unmöglich, aber...
--- seed 9 ---
[1] (rough) Nur wenn du die Luftballonchen ausreichend viele zusammenkommst, werden sie nicht fliegen!
[2] (rough) Dieser ist definitiv dumm!
[3] mugg
[4] (casual) Was?!
[5] (rough) Ohne Helium ist der Luftballon nicht flüssig!
[6] (rough) Das ist eine Gebrauchtwaffe!
[7] sehr gut
[8] (polite) Es tut mir leid, aber...
[9] (rough) In der Galaxis würde ich Filmpakete mit Schalterschrauben verpacken!
[10] (rough) Ach du Scheiße!
[11] (casual) Ja, das ist ziemlich wahrscheinlich, aber...
[12] (casual) Auf der Welt gibt es Millionen von Fotos, die man auf der Internetseite herunterladen kann!
[13] (casual) Aber, du musst es trotzdem machen...
[14] (casual) Ach du Scheiße!
[15] (polite) Das ist richtig...
```

**inline-np** · seed 7: 3486 ms, 357 prompt / 234 reply tokens · seed 8: 3326 ms, 357 prompt / 219 reply tokens · seed 9: 4086 ms, 357 prompt / 298 reply tokens

```text
--- seed 7 ---
[1] Aber wenn man nur so viel Luft in den Balloons aufnehmen würde, würden sie doch nicht fliegen!
[2] Das ist definitiv ein Idiot!
[3] Muuuuugggg!
[4] Was ist das für ein Unsinn!?
[5] Ohne Helium würde die Balloons nicht fliegen!
[6] Das ist eine Taschenlampe, die man mit einem Kabel an einen Lichtkasten anbringen kann.
[7] Das ist eine fantastische Leistung!
[8] Ich hätte es mir gedacht, dass es so teuer war.
[9] In der Galaxis würde man das Filmpaket und den Schalter machen!
[10] Ach du Scheiße!
[11] Das ist natürlich möglich, aber nicht praktisch.
[12] Auf dem Planeten gibt es Fotos, die man überall auf der Welt finden kann.
[13] Man sollte sich nicht die Mühe machen, selbst zu fotografieren.
[14] Ach du Lieber!
[15] Das ist richtig.
--- seed 8 ---
[1] Ich kann keine Luftballonflotte zusammenstellen, die fliegen kann!
[2] Das ist definitiv ein Idiot!
[3] Mu-gg!
[4] Was ist das für ein Unsinn!?
[5] Ohne Helium kann die Luftballon nicht fliegen!
[6] Das ist eine Leckwaffe, das ist eine Leckwaffe!
[7] So gut wie möglich!
[8] Ich hoffe, es war nicht teuer!
[9] Warum sollte ich das tun? Ich habe Filmpakete in den Himmel schicken wollen!
[10] Ich denke, das ist zu viel!
[11] Ja, das ist möglich, aber nur theoretisch!
[12] Auf der ganzen Welt gibt es Fotos von der Erde, die man im Internet finden kann!
[13] Warum sollte ich das tun? Ich habe die Kamera nicht mitnehmen wollen!
[14] Ach!
[15] Das ist richtig!
--- seed 9 ---
[1] "Keine Sorge, auch wenn man die Luftballonchen in der Luft hält, bleiben sie nicht hoch."
[2] "Er ist definitiv ein Idiot."
[3] "Müggel!"
[4] "Was ist das für ein Unsinn!?"
[5] "Wenn man Helium in die Luftballonchen einpumpt, bleiben sie nicht hoch."
[6] "Das ist eine Leckkappe, das ist eine Leckkappe, das ist eine Leckkappe."
[7] "Gut, gut, das ist toll!"
[8] "Ich habe es mir nicht gedacht, es war wirklich teuer."
[9] "In der Galaxis kann man Filme aufnehmen und Schaltkreise betätigen!"
[10] "Was für ein Idiot! Ich dachte, das ist ein Witz!"
[11] "Ja, das ist möglich, aber ich denke, es ist nicht so einfach wie du denkst."
[12] "Die Bilder von der Erde fallen überall auf der Welt auf der Internetseite."
[13] "Ich würde das auch nicht tun, wenn ich nicht so dumm wäre."
[14] "Ach, ich dachte, das wäre so einfach."
[15] "Das ist richtig, aber ich denke, es ist nicht so einfach wie du denkst."
```

**sys-ctl** · seed 7: 2667 ms, 260 prompt / 189 reply tokens · seed 8: 3052 ms, 260 prompt / 229 reply tokens · seed 9: 2918 ms, 260 prompt / 215 reply tokens

```text
--- seed 7 ---
[1] Nur wenn ich die Luftballons ausreichend集gehe, werden sie nicht fliegen!
[2] Das ist definitiv ein Idiot!
[3] Mu-gg!
[4] Was?!
[5] Ohne Helium ist der Luftballon nicht flott!
[6] Das ist eine Kamera mit einer Leinwand!
[7] So gut ist das!
[8] Ach, das war zu teuer!
[9] Kannst du das auf der Erde machen mit Kassettenfilm und Schaltkreisen?
[10] Ach du Scheiße!
[11] Ja, das ist möglich, aber...
[12] Auf dem Internet gibt es genug Fotos von Planeten.
[13] Du musst es selbst machen!
[14] Ach du Scheiße!
[15] Ja, das ist so...
--- seed 8 ---
[1] Nur wenn du so viele Luftballons zusammenkommst, werden sie doch nicht fliegen!
[2] Das ist definitiv ein Idiot!
[3] Muuuuugghh
[4] Was?!
[5] Ohne Helium ist der Ballon nicht flott!
[6] Das ist eine Kamera, die verwendet wird, wenn man schnellere Fotos braucht!
[7] Das ist super gut!
[8] Ach, das war so teuer gewesen...
[9] Kannst du das in der Galaxis mitnehmen und mit dem Schalldämpfer schalten?
[10] Ach du Scheiße!
[11] Ja, das ist möglich, aber... 
[12] Auf der Welt gibt es so viele Fotos, dass man sie auf der Welt nur im Internet finden kann!
[13] Warum musst du das selbst machen, wenn man einfach Fotografie auf der Welt kaufen kann?
[14] Ach, das war so teuer gewesen...
[15] Ja, das ist so...
--- seed 9 ---
[1] Nur wenn ich die Luftballonchen aus der Luft ziehe, werden sie immer noch nicht fliegen!
[2] Das ist definitiv ein Idiot!
[3] Mu-gg!
[4] Was?!
[5] Ohne Helium kann der Ballon nicht fliegen!
[6] Das ist eine Dummheit! Das ist eine Dummheit!
[7] So gut ist das nicht!
[8] Ach, das war so teuer gewesen!
[9] Kannst du das in der Galaxis machen, um die Kamera zu fotografieren?
[10] So was!
[11] Ja, das ist möglich, aber ich denke, das ist nicht das Richtige!
[12] Auf der ganzen Welt gibt es so viel Fotos auf der Internet, dass man sie einfach nur herunterladen kann!
[13] Du musst es trotzdem machen, obwohl...
[14] Ach!
[15] Ja, das ist so...
```

**sys** · seed 7: 3339 ms, 344 prompt / 230 reply tokens · seed 8: 3135 ms, 344 prompt / 208 reply tokens · seed 9: 3264 ms, 344 prompt / 222 reply tokens

```text
--- seed 7 ---
[1] Nur wenn man die Luftballonchen ausreichend集t, werden sie nicht fliegen!
[2] Das ist definitiv ein Idiot!
[3] Mu-gg!
[4] Was ist das für ein Unsinn!?
[5] Ohne Helium wird der Luftballon nicht fliegen!
[6] Das ist eine Frage der Einstellung: Das ist eine Kamera für den täglichen Gebrauch.
[7] Das ist ein super-kluges Ding!
[8] Ach, das war zu teuer gewesen!
[9] Kannst du das in der Galaxis machen, um mit dem Film zu schießen?!
[10] Ach du Scheiße!
[11] Ja, das ist möglich, aber nur theoretisch!
[12] Auf der ganzen Welt gibt es Fotos, die man über die Internetseite herunterladen kann.
[13] Du musst dich nicht selbst fotografieren!
[14] Ach du Scheiße!
[15] Das ist ja so eindeutig!
--- seed 8 ---
[1] Nur wenn man die Luftballonchen ausreichend集meht, werden sie nicht fliegen!
[2] Er ist definitiv dumm!
[3] Mu-gg!
[4] Was ist denn da überhaupt!?
[5] Ohne Helium wird der Luftballon nicht fliegen!
[6] Das ist der Grund, warum ich eine Einweg-Kamera habe!
[7] Das ist super! Ich kann das so gut!
[8] Ach, das war zu teuer!
[9] Was für ein Idiot, das hier drauf zu schauen!
[10] So was!
[11] Ja, das ist möglich, aber nicht praktisch!
[12] Auf der Welt gibt es Hunderttausend Bilder, die man nur auf der Weltteiche hat!
[13] Ich würde das nicht machen, wenn ich es nicht tun musste!
[14] Ach!
[15] Das ist richtig...
--- seed 9 ---
[1] Nur wenn du die Luftballonchen ausreichend viele zusammengehängst, werden sie nicht fliegen!
[2] Das ist definitiv ein Idiot!
[3] Mu-guu
[4] Was?!
[5] Ohne Helium wird der Luftballon nicht fliegen!
[6] Das ist eine Kamera, die verwendet wird, um Fotos zu machen.
[7] Das ist ein super-Schmuck!
[8] Ach, das war zu teuer gewesen!
[9] Könntest du denn auch in der Galaxis eine Kamera mit Filmpaketen und Schaltkreisen einsetzen?
[10] Ach du Scheiße!
[11] Ja, das ist möglich, aber...
[12] Auf der Welt gibt es Millionen von Fotos, die man über die Internetseiten heraussucht.
[13] Warum musst du das selbst machen?
[14] Ach du Arsch!
[15] Das ist ja so eindeutig...
```

**sys-qwen** · seed 7: 3515 ms, 354 prompt / 240 reply tokens · seed 8: 3310 ms, 354 prompt / 222 reply tokens · seed 9: 3219 ms, 354 prompt / 212 reply tokens

```text
--- seed 7 ---
[1] ただ Luftballons, die nur von so vielen gesammelt werden, werden nicht fliegen!
[2] Dieser ist definitiv dumm!
[3] Mu-gg!
[4] Was ist das für ein Unsinn!?
[5] Ohne Helium kann der Luftballon nicht fliegen!
[6] Das ist eine Frage der Preiswerte! Das ist eine Frage der Preiswerte!
[7] Das ist eine Frage der Preiswerte! Das ist eine Frage der Preiswerte!
[8] Ah, das war teuer!
[9] Warum sollte ich das Filmpaket mit dem Schalter machen?
[10] Ich kann das nicht fassen!
[11] Ja, das ist möglich, aber nur theoretisch!
[12] Auf der Welt gibt es so viele Fotos, dass sie auf der Welt auf der Welt auf der Welt auf der Welt auf der Welt auf der Welt auf der Welt auf der Welt auf der W
[13] Ich würde das auch nicht tun!
[14] Ach!
[15] Das ist ja so eindeutig!
--- seed 8 ---
[1] ただ Luftballons, die nur von so vielen gefangen werden, werden nicht fliegen!
[2] Dieser ist definitiv dumm!
[3] Mu-gg!
[4] Was ist das für ein Unsinn!?
[5] Ohne Helium kann der Luftballon nicht fliegen!
[6] Das ist eine Frage der Einstellung! Das ist eine Frage der Einstellung!
[7] Das ist super! Das ist super!
[8] Ach, das war zu teuer!
[9] Kannst du in der Galaxie Filmpakete mit Schaltersondern fotografieren?!
[10] Ach du Scheiße!
[11] Ja, es ist möglich, aber nur theoretisch!
[12] Die Bilder von der Erde sind überall auf der Welt auf der Welt zu finden!
[13] Du würdest dich auch selbst fotografieren!
[14] Ach du Scheiße!
[15] Das ist ja so eindeutig!
--- seed 9 ---
[1] ただ Luftballons, die nur von so vielen gesammelt werden, können nicht fliegen!
[2] Dieser ist definitiv dumm!
[3] Mu-gg!
[4] Was ist das für ein Unsinn!?
[5] Ohne Helium kann der Luftballon nicht fliegen!
[6] Das ist eine Frage der Warenpreise! Das ist ein Filmkamera!
[7] So gut sind die Bilder!
[8] Ach, das war zu teuer gewesen!
[9] Kannst du in der Galaxis Filmpakete und Schaltkreise fotografieren?
[10] Dreck!
[11] Ja, das ist möglich, aber nur theoretisch!
[12] Auf der Welt gibt es so viele Fotos, dass sie auf der Internetseite jederzeit heruntergeladen werden!
[13] Man könnte auch selbst die Bilder machen!
[14] Ach!
[15] Das ist ja richtig...
```


### balloon-dense-dialogue, hand transcription, Brazilian Portuguese

**none** · seed 7: 2791 ms, 271 prompt / 200 reply tokens · seed 8: 2709 ms, 271 prompt / 190 reply tokens · seed 9: 2845 ms, 271 prompt / 204 reply tokens

```text
--- seed 7 ---
[1] Só que se você colar um balão de ar comprimido, ele não vai subir!
[2] Ele é absolutamente bêbado!
[3] Mu-guu
[4] O que?!
[5] Se não colocar hidrogênio, o balão não vai subir!
[6] Isso é porque são câmeras de filme a base de película!
[7] Muito bem! Muito bem!
[8] Ah, foi um preço baixo!
[9] Como se eu tivesse a coragem de tirar uma foto na estação espacial!
[10] Que coisa!
[11] Sim, claro, mas não é possível!
[12] A foto do planeta está lá embaixo, na internet!
[13] E se eu não tirar a foto?
[14] Ah!
[15] Sim, é verdade!
--- seed 8 ---
[1] Só que se você coletar tanto balões de ar, eles nunca irão subir!
[2] Ele é absolutamente bêbedo!
[3] Mu-gg
[4] O que?!
[5] Se você não colocar hidrogênio na balança, ela não vai subir!
[6] Isso é porque são câmeras de filme de uso único!
[7] Muito bem! Muito bem!
[8] Ah, era caro!
[9] Como você quer que eu faça isso no espaço?
[10] Que diabos!
[11] Sim, é possível, mas...
[12] As fotos do planeta estão por toda a internet, não é mesmo?
[13] E se eu não fizesse isso por mim?
[14] Ah!
[15] Isso mesmo...
--- seed 9 ---
[1] Só que se você pegar tantos balões de ar, eles nunca vão ficar a flutuar!
[2] Ele é absolutamente bêbado!
[3] MUGU!
[4] O que?!
[5] Se você não colocar hidrogênio na balança, ela nunca vai ficar flutuando!
[6] Isso é porque são câmeras de filme, não é?
[7] Parece que são ótimos para tirar fotos!
[8] Ah, era caro!
[9] Como se eu pudesse tirar fotos com filme em espaço!
[10] Que coisa!
[11] Sim, mas não é possível de forma prática.
[12] Eles estão espalhados pelo mundo inteiro na internet!
[13] E você, por que não faz isso?
[14] Ah!
[15] Sim, você está certo.
```

**inline-shipped** · seed 7: 3735 ms, 358 prompt / 264 reply tokens · seed 8: 3855 ms, 358 prompt / 276 reply tokens · seed 9: 4613 ms, 358 prompt / 355 reply tokens

```text
--- seed 7 ---
[1] (rough) Mas, o que vocês estão fazendo com esses balões de ar quente? Eu não vou deixar esses balões flutuar!
[2] (rough) Ele é um bobo!
[3] (rough) Muguu
[4] (casual) O que você está fazendo?!
[5] (rough) Sem hidrogênio, o balão não vai flutuar!
[6] (rough) Basicamente, é um fotógrafo de revista que usa isso.
[7] (polite) Muito bem, isso é um ótimo resultado!
[8] (polite) Ah, foi caro, mas…
[9] (rough) No espaço, vocês podem tirar fotos com filme?
[10] (rough) Que coisa!
[11] (casual) Sim, mas isso não é possível de forma geral.
[12] (casual) Você pode encontrar muitos fotos de planeta na internet.
[13] (casual) Você não precisa tirar fotos se não quiser!
[14] (rough) Ah!
[15] (polite) Isso é verdade, eu concordo.
--- seed 8 ---
[1] (rough) Mas, só coletando tantos foguetes de ar, eles nunca vão ficar no ar!
[2] (rough) Ele é absolutamente bêbado!
[3] (rough) Mu-guu
[4] (casual) O que, você está se perguntando?!?
[5] (rough) Sem hidrogênio, o foguete de ar não vai ficar no ar!
[6] (rough) Basicamente, ele é um fotógrafo de uso único.
[7] (polite) Eles são ótimos!
[8] (polite) Ah, foi um preço baixo!
[9] (rough) Como você pode fotografar em espaço com um filme enrolado na lente?
[10] (rough) Tá, tá, é claro, mas…
[11] (rough) Mas, na verdade, é possível!
[12] (casual) Você acha que eu vou me meter em uma viagem para tirar fotos do planeta?
[13] (casual) Você acha que vou me meter em uma viagem para tirar fotos do planeta?
[14] (rough) Ah
[15] (polite) Isso é verdade...
--- seed 9 ---
[1] (rough) Mas, o que vocês estão fazendo com esses balões vazios? Não vão ficar no ar, né?
[2] (rough) Ele é um bobo, certo?
[3] (rough) Mmm...
[4] (casual) O que você está fazendo? Não está entendendo nada?
[5] (rough) Se você não colocar hidrogênio na balança, o balão não vai ficar no ar.
[6] (rough) Isso é como se você fosse usar uma câmera de filme de uma máquina de fotografia de papel. Não é assim que funciona.
[7] (polite) Ei, eu vi que vocês estão usando um dos meus produtos. Obrigado.
[8] (polite) Ah, eu achei que era um preço normal.
[9] (rough) Ei, você tem certeza de que vai conseguir tirar uma foto? Não sei se você pode tirar uma foto na lua!
[10] (rough) Tá, tá, eu entendi.
[11] (casual) Sim, eu sei, mas não é tão simples assim.
[12] (casual) Você acha que eu vou me meter na internet para tirar uma foto do meu próprio planeta? Não, eu não vou fazer isso.
[13] (casual) Você acha que vou me meter na internet para tirar uma foto do meu próprio planeta? Não, eu não vou fazer isso.
[14] (casual) Ah, tá.
[15] (polite) Sim, eu entendi.
```

**inline-np** · seed 7: 3444 ms, 358 prompt / 229 reply tokens · seed 8: 3242 ms, 358 prompt / 211 reply tokens · seed 9: 3209 ms, 358 prompt / 207 reply tokens

```text
--- seed 7 ---
[1] Não importa quantos balões de ar comprimido você colete, eles sempre irão cair!
[2] Ele definitivamente é um bobo!
[3] Mu-gg!
[4] O que você está se referindo?!
[5] Sem hélice, o balão de ar comprimido não vai subir!
[6] Basicamente, isso é um fotógrafo de revista!
[7] Muito bem! Muito bem!
[8] Desculpe, mas foi muito caro!
[9] Como alguém que vive na宇宙, você tem a capacidade de fotografar e apertar o botão de disparo?
[10] Que idiota!
[11] Sim, é verdade, mas...
[12] Você acha que eu vou me meter em uma viagem para tirar fotos do planeta Terra?
[13] Você realmente acha que vou me arriscar tirar fotos do meu próprio celular?
[14] Ah!
[15] Isso é verdade, mas...
--- seed 8 ---
[1] Não importa quantas balas de ar comprimido você coletar, elas sempre ficarão no chão.
[2] Ele é um bobo.
[3] Muito
[4] O que você está se referindo?!
[5] Sem Helium, as balas de ar comprimido não ficarão no ar.
[6] Basicamente, é um fotógrafo de uso único.
[7] Muito bem, essas são ótimas.
[8] Tudo bem, foi um prazer.
[9] Como você pode fotografar com filme em um espaço vazio no espaço?
[10] Que idiota!
[11] Sim, mas isso não é possível.
[12] As fotos do planeta estão por todo o mundo na internet.
[13] Você poderia ter feito isso, mas você não o fez.
[14] Ah.
[15] Sim, como você disse.
--- seed 9 ---
[1] Não importa quantos foguetes você colete, eles sempre vão cair no mar.
[2] Ele é um idiota.
[3] Mu-gg
[4] O que você está dizendo?!
[5] Sem hidrogênio, o foguete não vai subir!
[6] Basicamente, é um fotógrafo de viagem.
[7] Muito bem, muito bem!
[8] Tudo bem, foi um prazer!
[9] Como alguém que vive na lua, você não tem a menor ideia do que é filmar com um filme?
[10] Tudo bem.
[11] Sim, mas isso não significa que seja possível.
[12] Aqui estão algumas fotos do planeta na internet.
[13] E você, por que você não faz isso?
[14] Ah, ah, ah!
[15] Sim, mas isso não significa que seja possível.
```

**sys-ctl** · seed 7: 3000 ms, 261 prompt / 227 reply tokens · seed 8: 2938 ms, 261 prompt / 217 reply tokens · seed 9: 2790 ms, 261 prompt / 203 reply tokens

```text
--- seed 7 ---
[1] Só que, se você coletar tantos foguetes de ar, eles nunca irão subir!
[2] Ele é um bobo!
[3] muuuu
[4] O que, então, você está fazendo?!
[5] Se você não colocar hidrogênio na balança, ela nunca irá subir!
[6] Basicamente, isso é um fotógrafo de uso único!
[7] Muito bem, isso é um ótimo fotógrafo!
[8] Ah, eu tinha que pagar caro...
[9] E, no espaço, você pode tirar fotos com filme e apertar o botão de disparo!
[10] Que pena!
[11] Sim, é possível, mas...
[12] As fotos do mundo todo estão espalhadas na internet, não é mesmo?
[13] E, mesmo que eu não me importe, eu não vou fazer isso!
[14] Ah...
[15] Isso é verdade...
--- seed 8 ---
[1] Só que, se você pegar tantos balões de ar comprimido, eles não vão ficar no ar!
[2] Ele é um bobo, sem dúvida!
[3] mugg
[4] O que?!
[5] Se não colocar hidrogênio na balança, ela não vai ficar no ar!
[6] Isso é porque são câmeras de filme a prachar!
[7] Muito bem, vocês são bons!
[8] Ah, foi um preço baixo!
[9] E se eu fizer uma foto na lua, vou poder tirar fotos com o filme?
[10] Que pena!
[11] Sim, é possível, mas...
[12] A gente vê muitas fotos de nosso planeta na internet, não é mesmo?
[13] E se eu tivesse que tirar uma foto, eu faria?
[14] Ah, que pena!
[15] É, é...
--- seed 9 ---
[1] Só que, se você pegar tantos balões de ar comprido, eles não vão ficar no ar!
[2] Essa pessoa é idiota!
[3] muguuu
[4] O que?!
[5] Se não colocar hidrogênio na balança, ela não vai ficar no ar!
[6] Basicamente, isso é um flash de foto!
[7] Muito bem! (Muito bem! )
[8] Ah, eu sabia que ia ser caro...
[9] Como você vai tirar fotos com filme na lua?
[10] Que coisa!
[11] Sim, mas não é possível de forma geral.
[12] Você pode encontrar milhares de fotos de nosso planeta na internet!
[13] E você não vai tirar fotos com isso...
[14] Ah...
[15] É, mas não é possível de forma geral.
```

**sys** · seed 7: 3290 ms, 345 prompt / 224 reply tokens · seed 8: 3255 ms, 345 prompt / 222 reply tokens · seed 9: 3056 ms, 345 prompt / 201 reply tokens

```text
--- seed 7 ---
[1] Só que, mesmo se você coletar milhares de fogos de artifício, eles não vão ficar no ar!
[2] Ele é um bobo, absolutamente bobo!
[3] Mu-guu
[4] O que?!
[5] Se não colocar hidrogênio na balança, a balança não vai ficar no ar!
[6] Acho que é porque são câmeras de uso único!
[7] Muito bem, são ótimos para tirar fotos!
[8] Ah, foi um preço baixo!
[9] Como vocês, na lua, tirar fotos com filme e apertar o disparador?
[10] Que coisa!
[11] Sim, é possível, mas...
[12] As fotos do mundo todo estão espalhadas na internet, não é mesmo?
[13] E se você não tirar a foto, não vai ter como fazer isso!
[14] Ah...
[15] É, é...
--- seed 8 ---
[1] Só que, mesmo coletando tantos balões de ar, eles nunca vão ficar no ar!
[2] Ele é absolutamente bêbado!
[3] Mu-gg!
[4] O que, então, você está fazendo?!
[5] Sem hidrogênio, o balão não vai ficar no ar!
[6] Basicamente, ele é um fotógrafo de uso único!
[7] Muito bem, essas são ótimas fotos!
[8] Ah, foi um preço muito baixo!
[9] Como vocês podem imaginar, eu não tenho interesse em filmar em espaço!
[10] Que coisa!
[11] Sim, eu sei, mas...
[12] As fotos do planeta estão espalhadas pelo mundo inteiro na internet!
[13] E, mesmo que eu não me importe, eu não vou me arriscar!
[14] Ah, que coisa!
[15] Isso é verdade...
--- seed 9 ---
[1] Só que, mesmo se você coletar tantos foguetes de ar, eles nunca irão subir!
[2] Essa pessoa é absolutamente boba!
[3] Muguu
[4] O que?!
[5] Sem hidrogênio, o foguete não vai subir!
[6] Basicamente, isso é um fotógrafo de uso único!
[7] Muito bem, esses são ótimos!
[8] Ah, era caro!
[9] Como você pode fotografar na lua, com um disparador de câmera?
[10] Tchauuu!
[11] Sim, mas isso não é possível de fato...
[12] As fotos do mundo todo estão espalhadas na internet!
[13] E você, por que não faz isso?
[14] Ah!
[15] Sim, é verdade...
```

**sys-qwen** · seed 7: 3190 ms, 355 prompt / 212 reply tokens · seed 8: 3110 ms, 355 prompt / 201 reply tokens · seed 9: 3084 ms, 355 prompt / 199 reply tokens

```text
--- seed 7 ---
[1] Só que, mesmo se você coletar milhares de balões de ar quente, eles não vão ficar no ar.
[2] Essa pessoa é absolutamente boba.
[3] Mu-gg!
[4] O que?!
[5] Sem hidrogênio, o balão não vai ficar no ar!
[6] Isso é porque, na verdade, são câmeras de filme.
[7] Eles são incrivelmente bons para fotos!
[8] Ah, era caro!
[9] Como você pode tirar fotos com filme em órbita na Terra?
[10] Que idiota!
[11] Sim, é possível, mas...
[12] Você pode encontrar milhares de fotos de nosso planeta na internet.
[13] E, mesmo que eu não me importe, eu não vou fazer isso.
[14] Ah, que pena!
[15] Sim, é verdade...
--- seed 8 ---
[1] Só que, se você coletar tantos balões de ar, eles nunca irão subir!
[2] Ele é absolutamente bêbado!
[3] Mu-guu
[4] O que isso quer dizer?!
[5] Não vai subir se não colocar hidrogênio!
[6] Isso é porque são câmeras de filme usáveis uma vez.
[7] São ótimos para tirar fotos!
[8] Ah, eu tinha pensado que seria caro...
[9] Como você vai tirar fotos com filme em espaço?
[10] Que idiota!
[11] Sim, mas não é possível de forma prática.
[12] Você pode encontrar muitas fotos do planeta na internet, não é?
[13] Você poderia ter tirado a foto, mas...
[14] Ah...
[15] Isso é verdade...
--- seed 9 ---
[1] Só que, mesmo se você coletar milhares de balões de ar quente, eles não vão ficar no ar.
[2] Ele é absolutamente bobo.
[3] Mu-gg
[4] O que?!
[5] Não vai ficar no ar sem o hidrogênio.
[6] Basicamente, ele é uma câmera de filme de uso único.
[7] Muito bem, isso é uma câmera de filme.
[8] Ah, eu pensei que fosse barato.
[9] Como você vai tirar fotos com filme em espaço?
[10] Que pena.
[11] Sim, é possível, mas...
[12] Você pode tirar fotos de qualquer lugar no mundo na internet.
[13] Você não vai tirar fotos apenas para mim.
[14] Ah.
[15] Isso, é verdade.
```


### bourei-p04, hand transcription, French

**none** · seed 7: 1744 ms, 182 prompt / 122 reply tokens · seed 8: 1749 ms, 182 prompt / 123 reply tokens · seed 9: 1931 ms, 182 prompt / 143 reply tokens

```text
--- seed 7 ---
[1] Je suis mort.
[2] "Les gens que l'on voit" sont des êtres.
[3] Bienvenue chez le cérémonialiste de morts, bonjour !
[4] ?
[5] L'apothéose de ma sœur ?
[6] Oui...
[7] Le président de la société de cérémonial des urnes, Kusumakura Kururin, 25 ans.
[8] Le cérémonialiste de morts, Sakurae Suzzume, 19 ans.
--- seed 8 ---
[1] Je suis mort.
[2] "Les gens que l'on voit" sont des êtres.
[3] Bienvenue chez le cérémonial des morts, Monsieur Kusumura.
[4] ?
[5] L'apothéose de ma sœur ?
[6] Oui...
[7] Le président de la société de cérémonies de l'ombre, Kusumura Kusumura, 25 ans.
[8] Le cérémonial de la tombe, Suzu, 19 ans.
--- seed 9 ---
[1] Je suis mort.
[2] "Les gens que l'on voit" sont des gens.
[3] Bienvenue chez le cérémonie de dépoussiérage de fantômes, Mr. Kusumoto, bonjour.
[4] ?
[5] L'habilleuse de la sœur décédée?
[6] Oui...
[7] Le président de la société de dépoussiérage de fantômes, Kusumoto Kusumoto, 25 ans.
[8] Le cérémonie de dépoussiérage de fantômes, Sakurae Suzume, 19 ans.
```

**inline-shipped** · seed 7: 2373 ms, 243 prompt / 168 reply tokens · seed 8: 2106 ms, 243 prompt / 138 reply tokens · seed 9: 2165 ms, 243 prompt / 146 reply tokens

```text
--- seed 7 ---
[1] (polite) Je suis en train de mourir
[2] (polite) "Les gens que l'on peut voir" sont des gens
[3] (rough) Bienvenue chez le cérémonie de dépoussiérage de fantômes, bonjour
[4] ?
[5] (polite) Avez-vous vu l'odeur de l'odeur de la mort de votre tante ?
[6] (polite) Oui...
[7] (polite) Le président de l'entreprise de dépoussiérage de fantômes, Ouchin, 25 ans
[8] (polite) Le cérémonier de la demande de dépoussiérage, Sazume, 19 ans
--- seed 8 ---
[1] (polite) Je suis mort
[2] (polite) "Les gens que l'on peut voir" sont des gens
[3] (rough) Bienvenue chez le cérémonial des morts, bonjour
[4] ?
[5] (polite) L'odeur de la mort de la sœur de monsieur
[6] (polite) Oui...
[7] (polite) Le président de l'Ushigome Cérémonial
[8] (polite) Le cérémonial de l'Ushigome, Sakurae Suzuki, 19 ans
--- seed 9 ---
[1] (polite) Je suis mort
[2] (polite) "Les gens que l'on voit" sont des gens
[3] (rough) Bienvenue chez le cimetière de fantômes, bonjour
[4] ?
[5] (polite) L'odeur de la mort de la sœur de l'âme ?
[6] (polite) Oui...
[7] (polite) Le président de la société de cimetière Uramitsu, Kusumaru 25 ans
[8] (polite) Le cérémonier demandeur d'assistance, Sakurae Suzume 19 ans
```

**inline-np** · seed 7: 2004 ms, 243 prompt / 125 reply tokens · seed 8: 2134 ms, 243 prompt / 138 reply tokens · seed 9: 2286 ms, 243 prompt / 154 reply tokens

```text
--- seed 7 ---
[1] Je suis mort.
[2] "Les gens que l'on peut voir" sont des types.
[3] Bienvenue chez les cérémonies funèbres, Mr. Kusumakura.
[4] ?
[5] Avez-vous vu la peau de ma tante décédée ?
[6] Oui...
[7] Le président de la société de cérémonies funèbres Uchigomu, 25 ans.
[8] Le cérémonier de requêtes, Sakurae Suzume, 19 ans.
--- seed 8 ---
[1] Je suis mort.
[2] "Les gens que l'on peut voir" sont des gens.
[3] Bienvenue chez le cérémonie de dépoussiérage de la mort, Mr. Kusumoto.
[4] ?
[5] La beauté de votre tante qui est décédée ?
[6] Oui...
[7] Le président de l'entreprise de dépoussiérage de la mort, Kusumoto Kusumoto, 25 ans.
[8] Le cérémonier de dépoussiérage de la mort, Sakurae Sume, 19 ans.
--- seed 9 ---
[1] Je suis mort.
[2] Il s'agit de personnes visibles.
[3] Bienvenue chez les funéraires d'ombres, bonjour
[4] ?
[5] Avez-vous vu la maquillage de la sœur décédée ?
[6] Oui...
[7] Le président de l'entreprise de funérailles Uraga, Uraga Kururururururururururururururururururururururururururururururururururururururururururururururururururur
[8] Le demandeur de service, Sazume, 19 ans
```

**sys-ctl** · seed 7: 1726 ms, 172 prompt / 127 reply tokens · seed 8: 1679 ms, 172 prompt / 121 reply tokens · seed 9: 1723 ms, 172 prompt / 126 reply tokens

```text
--- seed 7 ---
[1] Je suis mort.
[2] "Les gens que l'on voit" sont des types.
[3] Bienvenue chez les funérailles de fantômes, Mr. Kusumori, bonjour
[4] ?
[5] La maquilleuse de ma sœur décédée ?
[6] Oui...
[7] Le président de l'entreprise de funérailles Uramitsu, Kusumori Kurururin, 25 ans
[8] L'intermédiaire de funérailles, Sakurae Suzume, 19 ans
--- seed 8 ---
[1] Je suis mort.
[2] "Les gens que l'on voit" sont des types.
[3] Bienvenue chez les funéraires, Monsieur Gomé.
[4] ?
[5] L'œil de ma tante est-il encore là ?
[6] Oui...
[7] Le président de la société de funérailles Uchigomé, Uchigomé Kururū, 25 ans.
[8] Le propriétaire de la société de funérailles Sakura, Sakura Suzume, 19 ans.
--- seed 9 ---
[1] Je suis mort.
[2] "Les gens que l'on voit" sont des êtres.
[3] Bienvenue chez le cérémonialiste de l'enterrement, Mr. Kusumasa Kururun.
[4] ?
[5] L'odeuil de ma tante ?
[6] Oui...
[7] Le président de l'entreprise de cérémonies funéraires Uramitsu Kururun, 25 ans.
[8] Le cérémonialiste de requête Sakurae Suzzume, 19 ans.
```

**sys** · seed 7: 1986 ms, 223 prompt / 131 reply tokens · seed 8: 1863 ms, 223 prompt / 121 reply tokens · seed 9: 1914 ms, 223 prompt / 126 reply tokens

```text
--- seed 7 ---
[1] Je suis en train de mourir.
[2] "Les gens que l'on peut voir" sont des êtres.
[3] Bienvenue chez le cérémonial des morts, monsieur.
[4] ?
[5] L'apothéose de ma sœur ?
[6] Oui...
[7] Le directeur de l'entreprise de cérémonies funéraires Uchigome, Uchigome Kururin, 25 ans.
[8] Le cérémonial de la demande de service, Sakurae Suzume, 19 ans.
--- seed 8 ---
[1] Je suis en train de mourir.
[2] "Les gens que l'on peut voir" sont des êtres.
[3] Bienvenue chez le cérémonial des morts, bonjour.
[4] ?
[5] L'apothéose de ma sœur ?
[6] Oui...
[7] Le président de la société de cérémonial des morts, Kurosu Kurosu 25 ans.
[8] Le cérémonial de la demande de service, Sakurae Suzume 19 ans.
--- seed 9 ---
[1] Je suis en train de mourir.
[2] "Les êtres visibles" sont des êtres qui sont des êtres.
[3] Bienvenue à la maison des funérailles de l'âme, bonjour
[4] ?
[5] L'âge d'une sœur décédée?
[6] Oui...
[7] Le président de l'entreprise de funérailles Uragami, Uragami Kusukuri 25 ans
[8] Le demandeur de services funéraires Sakurae Suzzume 19 ans
```

**sys-qwen** · seed 7: 2212 ms, 233 prompt / 156 reply tokens · seed 8: 2051 ms, 233 prompt / 138 reply tokens · seed 9: 1934 ms, 233 prompt / 124 reply tokens

```text
--- seed 7 ---
[1] Je suis en train de mourir.
[2] "Les gens que l'on peut voir" sont des êtres.
[3] Bienvenue chez le cérémonie de dépoussiérage de l'âme, Mr. Kusumura, bonne chance.
[4] ?
[5] L'odeur de l'âme de ma tante est-elle là?
[6] Oui...
[7] Le président de la société de dépoussiérage de l'âme, Kusumura Kusumura, 25 ans.
[8] Le cérémonie de dépoussiérage de l'âme, Suzume Sakura, 19 ans.
--- seed 8 ---
[1] Je suis en train de mourir.
[2] "Les gens que l'on peut voir" sont des gens.
[3] Bienvenue chez le cérémonial de morts, Monsieur Kusumura.
[4] ?
[5] L'application de la maquillage à la morte de la sœur de l'ami ?
[6] Oui...
[7] Le président de la société de cérémonies de morts, Kusumura Kusumura, 25 ans.
[8] Le cérémonial de morts, Sazanami Suzume, 19 ans.
--- seed 9 ---
[1] Je suis en train de mourir.
[2] "Les gens que l'on peut voir" sont des êtres.
[3] Bienvenue chez les funérailles de fantômes, monsieur.
[4] ?
[5] L'apothéose de ma sœur?
[6] Oui...
[7] Le patron de la société de funérailles Ōkami, Ōkami Kusumaru, 25 ans.
[8] Le requérant de funérailles, Sakurae Suzume, 19 ans.
```


### bourei-p04, hand transcription, Spanish

**none** · seed 7: 1674 ms, 182 prompt / 116 reply tokens · seed 8: 1734 ms, 182 prompt / 124 reply tokens · seed 9: 1792 ms, 182 prompt / 130 reply tokens

```text
--- seed 7 ---
[1] Estoy muerto, ¿no?
[2] "Los 'vistos' son personas, ¿verdad?"
[3] ¡Bienvenido al funeral de fantasmas, ¡disfruta!
[4] ¿Qué?
[5] ¿Qué hizo la hermana muerta?
[6] Sí...
[7] El jefe del funeral de fantasmas, Kusumaru Kusumaru, 25 años
[8] El solicitante de servicios de funeral, Sakurae Suzume, 19 años
--- seed 8 ---
[1] Estoy muerto, ¿verdad?
[2] "¿El que se puede ver? ¿Ese tipo de personas?"
[3] ¡Bienvenido a la casa de los muertos, ¡que tengas un buen día!
[4] ¿Qué?
[5] ¿Qué hizo la hermana muerta?
[6] Sí...
[7] El jefe del funeral de Uchigusa, Uchigusa Kusuru, 25 años
[8] El solicitante de servicios funerarios, Sakurae Suzuki, 19 años
--- seed 9 ---
[1] Estoy muerto, ¿verdad?
[2] "Personas que se pueden ver" son esos tipos de personas.
[3] ¡Bienvenido a la casa de los muertos! ¡Buena suerte!
[4] ¿Qué?
[5] ¿Qué hizo la tía muerta?
[6] Sí...
[7] El jefe del funeral de la Ushiroku, Ushiroku Kusumasa, tiene 25 años.
[8] El solicitante de servicios funerarios es Ushiroku Kusumasa, 19 años.
```

**inline-shipped** · seed 7: 2150 ms, 243 prompt / 144 reply tokens · seed 8: 2255 ms, 243 prompt / 155 reply tokens · seed 9: 2122 ms, 243 prompt / 141 reply tokens

```text
--- seed 7 ---
[1] (polite) Estoy muerto, ¿no?
[2] (polite) "Personas que se pueden ver" son esos tipos.
[3] (rough) ¡Bienvenido a la casa de los muertos, señorita! ¡Qué lindo día!
[4] (rough) ¿Qué?
[5] (polite) ¿Qué hizo la tía muerta?
[6] (polite) Sí...
[7] (polite) El jefe del Muñeco Funeral, Kusuma Kuro, 25 años
[8] (polite) El solicitante del funeral, Sakura Sume, 19 años
--- seed 8 ---
[1] (polite) Estoy muerto, ¿no?
[2] (polite) "Es un humano visible", ¿verdad?
[3] (rough) ¡Bienvenido a la oficina de entierro de los muertos, ¡buen día!
[4] (rough) ¿Qué?
[5] (polite) ¿Qué hizo la tía muerta?
[6] (polite) Sí...
[7] (polite) El jefe de la oficina de entierro Uragami, Uragami Kusuru, 25 años
[8] (polite) El solicitante de servicios de entierro, Sakurae Suzume, 19 años
--- seed 9 ---
[1] (polite) Estoy muerto, ¿no?
[2] (polite) "Los seres visibles" son esos tipos.
[3] (rough) ¡Bienvenido al funeral de fantasmas, señor Ichijo! ¡Buena suerte!
[4] (rough) ¿Qué?
[5] (polite) ¿La maquiavelliana de la tía?
[6] (polite) Sí...
[7] (rough) Ichijo, 25 años, presidente del funeral de fantasmas.
[8] (polite) Sakurae, 19 años, solicitante del funeral de fantasmas.
```

**inline-np** · seed 7: 1992 ms, 243 prompt / 127 reply tokens · seed 8: 1872 ms, 243 prompt / 113 reply tokens · seed 9: 2096 ms, 243 prompt / 134 reply tokens

```text
--- seed 7 ---
[1] ¿Estás muerto, verdad?
[2] "¿Qué es un ser visible?" ¿Qué es un ser visible?
[3] ¡Bienvenido a la oficina de funeral de Utsuruma! ¡Qué lindo día!
[4] ¿Qué?
[5] ¿Qué hizo la tía muerta?
[6] Sí...
[7] El jefe de Utsuruma Funeral Service es Utsuruma Kusumaru, 25 años.
[8] El solicitante de funeral es Utsuruma Suzume, 19 años.
--- seed 8 ---
[1] ¿Estás muerto, verdad?
[2] "¿Qué quieres decir?", ¿no?
[3] ¡Bienvenido a la casa de los muertos! ¡Qué lindo día!
[4] ¿Qué?
[5] ¿Qué hizo la tía muerta?
[6] Sí...
[7] El dueño del funeral Ura Funeral Ura Kusumaru, 25 años
[8] El dueño del funeral Ura Funeral Ura Kusumaru, 19 años
--- seed 9 ---
[1] "¿Estás muerto, verdad?"
[2] "¿Qué quieres decir con 'ver'?"
[3] "¡Bienvenido a la oficina de entierro! ¡Buena suerte!"
[4] "¿Qué?"
[5] "¿Qué hizo tu tía muerta?"
[6] "¡Sí!"
[7] "El jefe de la sociedad de entierro Uchida, Uchida Kusumori, de 25 años.
[8] "El solicitante de la ceremonia de entierro, Sakura Mizu, de 19 años."
```

**sys-ctl** · seed 7: 1681 ms, 172 prompt / 121 reply tokens · seed 8: 1743 ms, 172 prompt / 129 reply tokens · seed 9: 1804 ms, 172 prompt / 132 reply tokens

```text
--- seed 7 ---
[1] Estoy muerto, ¿verdad?
[2] "¿Qué es un ser visible?" ¿dijiste?
[3] ¡Bienvenido al funeral de fantasmas, señor Gomi! ¡Que tengas un buen día!
[4] ¿Qué?
[5] ¿Qué hizo tu hermana muerta?
[6] Sí...
[7] El jefe del funeral de fantasmas, Gomi Kusumaru, 25 años
[8] El solicitante de servicios funerarios, Suzume Sakura, 19 años
--- seed 8 ---
[1] Estoy muerto, ¿verdad?
[2] "Personas que se pueden ver" son esos tipos.
[3] ¡Bienvenido a la oficina de entierro, señor Gomi! ¡Qué bonito!
[4] ¿Qué?
[5] ¿Qué hizo la tía muerta?
[6] ¡Sí!
[7] El jefe de la oficina de entierro, Kusumori Kusumori, 25 años
[8] El solicitante de servicios de entierro, Sakurae Suzzume, 19 años
--- seed 9 ---
[1] Estoy muerto, ¿verdad?
[2] "¿Los que se ven? Son esos tipos, ¿no?"
[3] ¡Bienvenido a la oficina de entierro de los muertos, señor Gomi! ¡Qué gusto tenerte!
[4] ¿Qué?
[5] ¿Qué hizo la tía muerta?
[6] ¡Sí!
[7] El jefe del funeral de la casa Uragami, Uragami Kusuru, de 25 años
[8] El solicitante de servicios de funeral, Sakurae Suzuki, de 19 años
```

**sys** · seed 7: 1957 ms, 223 prompt / 124 reply tokens · seed 8: 1978 ms, 223 prompt / 128 reply tokens · seed 9: 1908 ms, 223 prompt / 122 reply tokens

```text
--- seed 7 ---
[1] Estoy muerto, ¿no?
[2] "Personas visibles" son esos tipos.
[3] ¡Bienvenido a la oficina de cremación, señorita! ¡Que tengas un buen día!
[4] ¿Qué?
[5] ¿Qué hizo a tu hermana muerta?
[6] Sí...
[7] El señor Kurokuru, dueño de la empresa de cremación, tiene 25 años.
[8] La persona que solicitó el servicio de funeral es Sakurae Mizu, de 19 años.
--- seed 8 ---
[1] Estoy muerto, ¿verdad?
[2] "Personas que se pueden ver" son esos tipos, ¿no?
[3] ¡Bienvenido a la oficina de funeral de Uragami, ¡que tengas un buen día!
[4] ¿Qué?
[5] ¿Qué tal la maquillaje de la tía muerta?
[6] Sí...
[7] El jefe del funeral Uragami, Uragami Kusumori, 25 años
[8] El solicitante de funeral, Sakurae Suzuki, 19 años
--- seed 9 ---
[1] Estoy muerto, ¿no?
[2] "Personas visibles" son esos tipos.
[3] ¡Bienvenido a la oficina de entierro, señorita!
[4] ¿Qué?
[5] ¿Qué hizo tu hermana muerta?
[6] Sí...
[7] El señor Kurokuru, dueño de la Sociedad de Entierro Uchigata, tiene 25 años.
[8] El solicitante de servicios de entierro, Sakurae Suzume, tiene 19 años.
```

**sys-qwen** · seed 7: 1973 ms, 233 prompt / 126 reply tokens · seed 8: 1984 ms, 233 prompt / 126 reply tokens · seed 9: 1991 ms, 233 prompt / 129 reply tokens

```text
--- seed 7 ---
[1] Estoy muerto, ¿verdad?
[2] "Los seres visibles" son esos tipos.
[3] ¡Bienvenido a la casa de los muertos! ¡Que tengas un buen día!
[4] ¿Qué?
[5] ¿Qué hizo la tía muerta?
[6] Sí...
[7] El jefe de la empresa de cremaciones Uragami, Uragami Kusumaru, de 25 años.
[8] El solicitante de servicios de funeral, Sakurae Suzzume, de 19 años.
--- seed 8 ---
[1] Estoy muerto, ¿no?
[2] "Personas que se pueden ver" son esos tipos de personas.
[3] ¡Bienvenido a la casa de los muertos! ¡Que tengas un buen día!
[4] ¿Qué?
[5] ¿Qué hizo la tía muerta?
[6] Sí...
[7] El jefe de la Ushiro Funeral Service, Ushiro Kusumaru, tiene 25 años.
[8] El solicitante de servicios funerarios, Sakurae Suzume, tiene 19 años.
--- seed 9 ---
[1] Estoy muerto, ¿no?
[2] "Personas que se pueden ver" son esos tipos.
[3] ¡Bienvenido a la casa de los muertos! ¡Buena suerte!
[4] ¿Qué?
[5] ¿Qué hizo la tía muerta?
[6] Sí...
[7] El jefe de la Sociedad de Inhumaciones Uragami, Uragami Kusumaru, de 25 años.
[8] El solicitante de la ceremonia de entierro, Sakurae Suzume, de 19 años.
```


### bourei-p04, hand transcription, German

**none** · seed 7: 1856 ms, 182 prompt / 132 reply tokens · seed 8: 1768 ms, 182 prompt / 124 reply tokens · seed 9: 1770 ms, 182 prompt / 126 reply tokens

```text
--- seed 7 ---
[1] "Du bist tot, nicht wahr?"
[2] "Es gibt Menschen, die sehen können."
[3] "Willkommen bei der Leichenschauerkraft, herzlichen Glückwunsch!"
[4] "Was? "
[5] "Was für eine Ahne hat sich umgelegt? "
[6] "Ja... "
[7] "Der Leichenschauerkraft von Uragami, Uragami Kusumaru, 25 Jahre alt"
[8] "Der Leichenschauerkraft von Sakurae, Sakurae Suzume, 19 Jahre alt"
--- seed 8 ---
[1] "Er ist tot, nein."
[2] "Es gibt Menschen, die sehen können."
[3] "Willkommen bei der Leichnamsservice-Abteilung, Herrschaften!"
[4] "Was? "
[5] "Was für eine alte Dame hat die Make-up? "
[6] "Ja... "
[7] "Der Leichnamsservice-Präsident, Herr Kururuku, 25 Jahre alt."
[8] "Der Leichnamsservice-Anrufer, Herr Suzuki, 19 Jahre alt."
--- seed 9 ---
[1] "Es ist tot."
[2] "Es gibt Menschen, die Sie sehen können."
[3] "Willkommen bei der Leichenzuräucherhalle, Herr Gute Nacht!"
[4] "Hm?"
[5] "Was für eine alte Schwesternsage?"
[6] "Ja... "
[7] "Der Leichenzuräucherhalle-Vorsitzende, Herr Kururuku, 25 Jahre alt."
[8] "Der Leichenzuräucherhalle-Anrufer, Herr Suzume, 19 Jahre alt."
```

**inline-shipped** · seed 7: 2107 ms, 243 prompt / 138 reply tokens · seed 8: 2481 ms, 243 prompt / 180 reply tokens · seed 9: 2241 ms, 243 prompt / 153 reply tokens

```text
--- seed 7 ---
[1] (polite) Du bist tot.
[2] (polite) "Sichtbare" Menschen sind das.
[3] Willkommen bei der Leichnamsservice von Kurzweil, eine angenehme Überraschung.
[4] ?
[5] (polite) Hatte die Tante, die gestorben ist, ihre Make-up?
[6] (polite) Ja...
[7] Kurzweil, 25 Jahre alt, Leichnamsservice-Verleger.
[8] Die Leichnamsserviceanbieterin ist Sakura, 19 Jahre alt.
--- seed 8 ---
[1] (polite) "Gestorben bist du, mein Freund."
[2] (polite) "Es gibt Menschen, die man sehen kann."
[3] (polite) "Willkommen bei der Leichenschauerkraft, Herr Kusumaru, frohe Hochzeit!"
[4] (rough) "Was?!"
[5] (polite) "Was für eine Schönheit, die deine ältere Schwester hat?"
[6] (polite) "Ja... "
[7] (polite) "Der Leichenschauerkraft von Uchibara-Krematorium, Herr Kusumaru, 25 Jahre alt."
[8] (polite) "Der Leichenschauerkraft von Uchibara-Krematorium, Herr Kusumaru, 19 Jahre alt."
--- seed 9 ---
[1] (polite) Du bist tot.
[2] (polite) "Sichtbare" Menschen sind das.
[3] Willkommen bei der Leichnamsservice von Kusuma-Grunder, frohe Hochzeit!
[4] ?
[5] (polite) Hatte die Tante, die gestorben ist, ihr Make-up?
[6] (polite) Ja...
[7] Kusuma-Grunder, der Leichnamsservicepräsident, Kusuma Kusumokuro, 25 Jahre alt.
[8] Der Leichnamsserviceanvertrauende, Kusuma Kusumokuro, 19 Jahre alt.
```

**inline-np** · seed 7: 2022 ms, 243 prompt / 126 reply tokens · seed 8: 1897 ms, 243 prompt / 115 reply tokens · seed 9: 1913 ms, 243 prompt / 118 reply tokens

```text
--- seed 7 ---
[1] Ich bin tot.
[2] Es gibt Menschen, die Sie sehen können.
[3] Willkommen bei der Leichnam-Unterredigenschaft, Herr Gute Morgen.
[4] ?
[5] Hatte die Tante, die Sie gekannt haben, eine Make-up?
[6] Ja...
[7] Herr Kurokura, Leichnam-Unterredigenschaftsleiter, 25 Jahre alt.
[8] Die Leichnam-Unterredigenschaftsleiterin, Sakurae Suzuki, 19 Jahre alt.
--- seed 8 ---
[1] Ich bin tot.
[2] Es gibt Menschen, die Sie sehen können.
[3] Willkommen bei der Leichnamsservice von Kurzleben.
[4] ?
[5] Hatte die Tante, die Sie gekannt haben, eine Make-up?
[6] Ja...
[7] Herr Kurzleben, Leichnamsservice-Verleger, 25 Jahre alt.
[8] Die Leichnamsserviceanbieterin, Sakura-Wein, 19 Jahre alt.
--- seed 9 ---
[1] Ich bin tot.
[2] Es gibt Menschen, die man sehen kann.
[3] Willkommen bei der Leichnamsservice-Abteilung, Herr Gute Abend.
[4] Was ist das?
[5] Hatte die Tante, die dich gekannt hat, ihre Make-up?
[6] Ja...
[7] Herr Kusuma, der Leichnamsservice-Präsident, 25 Jahre alt.
[8] Der Anrufer ist Sakura-野, Suzuki, 19 Jahre alt.
```

**sys-ctl** · seed 7: 1512 ms, 172 prompt / 105 reply tokens · seed 8: 1437 ms, 172 prompt / 96 reply tokens · seed 9: 1667 ms, 172 prompt / 119 reply tokens

```text
--- seed 7 ---
[1] Du bist tot.
[2] "Sichtbare" Menschen sind das.
[3] Willkommen bei dem Leichnamsservicehaus, Herr Kusumura.
[4] ?
[5] Was für eine Ausdünstung für deine Mutter?
[6] Ja...
[7] Herr Kusumura, der Leichnamsservicehausleiter, 25 Jahre alt.
[8] Der Anrufer ist Herr Suzuki, 19 Jahre alt.
--- seed 8 ---
[1] Du bist tot.
[2] "Sichtbare" Menschen sind das.
[3] Willkommen bei der Leichenschauerkraftin Gute Morgen
[4] ?
[5] Was für eine Make-up hat die Tante gestern?
[6] Ja...
[7] Herr Kururū von der Ura-Krematorium 25 Jahre
[8] Der Anrufer ist Sakurae Suzuki 19 Jahre
--- seed 9 ---
[1] Du bist tot.
[2] "Sichtbare" Menschen sind das.
[3] Willkommen bei dem Leichnamsverwalterhaus, Herr Gute Nacht.
[4] ?
[5] Was für eine Schönheit, die Gesichtsmaske der Todejungfer?
[6] Ja...
[7] Herr Kusuma, der Leichnamssherdsleiter, 25 Jahre alt.
[8] Der Leichnamssherdsleiterin, Sakuraomi Suzuki, 19 Jahre alt.
```

**sys** · seed 7: 1796 ms, 223 prompt / 113 reply tokens · seed 8: 1975 ms, 223 prompt / 132 reply tokens · seed 9: 1745 ms, 223 prompt / 108 reply tokens

```text
--- seed 7 ---
[1] Ich bin tot, du Schwein!
[2] "Sehen Sie sich die Menschen an, die da sind!"
[3] Willkommen im Haus der Totenbeschwörer, Herr!
[4] Ach du verdammte Hure!
[5] Wie geht es Ihrer Tante, die tot ist?
[6] Ja...
[7] Herr Kusuma, der Herr der Urau, 25 Jahre alt.
[8] Der Anrufer namens Sakurae, 19 Jahre alt.
--- seed 8 ---
[1] Ich bin tot, du dummes Arschloch!
[2] "Sehen Sie sich die Menschen an, die sehen können, wie sie sind."
[3] Willkommen im Haus der Totenbeschwörer, Herrschaften!
[4] Ach, du Narr!
[5] Wie heißt die Totenmutter, die du so schön beschrieben hast?
[6] Ja...
[7] Herr Kurokuru, der Herr der Totenbeschwörung, 25 Jahre alt.
[8] Der Anrufer namens Sakurae Suzume, 19 Jahre alt.
--- seed 9 ---
[1] Ich bin tot, du Schwein!
[2] "Sichtbare" Menschen sind das!
[3] Willkommen bei der Leichenzuräucherungsgesellschaft, Herr!
[4] ?
[5] Wie geht es meiner Schwester?
[6] Ja...
[7] Herr Kurokuru, Leichenzuräucherungsgesellschaft, 25 Jahre
[8] Herr Suzuki, Leichenzuräucherungsgesellschaft, 19 Jahre
```

**sys-qwen** · seed 7: 1908 ms, 233 prompt / 123 reply tokens · seed 8: 1741 ms, 233 prompt / 105 reply tokens · seed 9: 1918 ms, 233 prompt / 124 reply tokens

```text
--- seed 7 ---
[1] Ich bin tot.
[2] "Sichtbare" Menschen sind das.
[3] Willkommen bei dem Toten-Kremierhändler Gute Abend
[4] ?
[5] Wie ist die Make-up von der Toten-Angesichts-Bräutigam?
[6] Ja...
[7] Herr Kurokuru, Geschäftsführer der Kurokuru-Kremiergesellschaft, 25 Jahre alt.
[8] Der Antragsteller, Herr Kurokuru, 19 Jahre alt.
--- seed 8 ---
[1] Ich bin tot.
[2] "Sichtbare" Menschen sind das.
[3] Willkommen bei dem Leichnamsvergleichshaus, Herr Kusumaru.
[4] ?
[5] Wie heißt die Totenmutter?
[6] Ja...
[7] Herr Kusumaru, Leichnamsserviceleiter, 25 Jahre alt.
[8] Herr Kusumaru, Antragsperson, 19 Jahre alt.
--- seed 9 ---
[1] Ich bin tot.
[2] "Sichtbare" Menschen sind das.
[3] Willkommen bei der Leichnamsaufführung, Herr Kusumura.
[4] ?
[5] Wie ist die Make-up von der Toten der Ältesten?
[6] Ja...
[7] Herr Kusumura, der Leichnamsaufführungsgeschäftsführer, 25 Jahre alt.
[8] Herr Kusumura, der Leichnamsaufführungsgeschäftsführer, 19 Jahre alt.
```


### bourei-p04, hand transcription, Brazilian Portuguese

**none** · seed 7: 1592 ms, 183 prompt / 108 reply tokens · seed 8: 1619 ms, 183 prompt / 110 reply tokens · seed 9: 1668 ms, 183 prompt / 115 reply tokens

```text
--- seed 7 ---
[1] Estou morto, hein?
[2] "Os que podem ser vistos" são esses, hein?
[3] Olá, fantasma funeral house, good to meet you!
[4] ?
[5] A makeup of the dead sister?
[6] Sim...
[7] O dono da Urasu Funeral House, Urasu Kururin, 25 anos
[8] O solicitante de funeral, Sakurae Suzume, 19 anos
--- seed 8 ---
[1] Estou morto, né
[2] "Os humanos que podem ser vistos" são eles
[3] Olá, fantasma funerário, bom dia
[4] ?
[5] A maquiagem da minha irmã falecida?
[6] Sim...
[7] O chefe da Ushiro Funeral Service, Ushiro Kusumaru, 25 anos
[8] O solicitante de funeral, Sakurae Suzuki, 19 anos
--- seed 9 ---
[1] Estou morto, hein?
[2] "Os que podem ser vistos" são esses, hein?
[3] Olá, fantasma funeral house, good to meet you
[4] ?
[5] O maquiagem da minha irmã falecida?
[6] Sim...
[7] O presidente da Ushigome Funeral House, Ushigome Kusumaru, 25 anos
[8] O solicitante de funeral, Sakurae Suzuki, 19 anos
```

**inline-shipped** · seed 7: 1945 ms, 244 prompt / 122 reply tokens · seed 8: 2034 ms, 244 prompt / 132 reply tokens · seed 9: 2129 ms, 244 prompt / 143 reply tokens

```text
--- seed 7 ---
[1] (polite) Estou morto, hein?
[2] (polite) "Vizível" pessoa, hein?
[3] (rough) Olá, fantasma funeral house, good to meet you
[4] ?
[5] (polite) O maestrinha da minha tia que morreu?
[6] (polite) Sim...
[7] (polite) Mr. Kusumasa, funeral house, good to meet you
[8] (polite) Miss Sakura, funeral house, good to meet you
--- seed 8 ---
[1] (polite) Está morto, hein?
[2] (polite) "Os que se podem ver" são esses, hein?
[3] (rough) Olá, morto, hein? Que bom!
[4] (polite) Oh?
[5] (polite) A maquiagem da tia morta?
[6] (polite) Sim...
[7] (polite) O Sr. Kusumasa, dono da firma de enterros, 25 anos
[8] (polite) A dona Sakura, 19 anos
--- seed 9 ---
[1] (polite) Estou morto, não?
[2] (polite) "Vista" é o tipo de pessoa?
[3] (rough) Olá, morto, queria fazer um funeral?
[4] (rough) ?
[5] (polite) A sobrinha do meu tio morreu, você poderia fazer o funeral?
[6] (polite) Sim...
[7] (polite) O chefe da Ura Kurokura Funeral Service, Ura Kurokura, 25 anos
[8] (polite) O solicitante do funeral, Sakurae Suzuki, 19 anos
```

**inline-np** · seed 7: 1872 ms, 244 prompt / 112 reply tokens · seed 8: 1782 ms, 244 prompt / 105 reply tokens · seed 9: 1889 ms, 244 prompt / 115 reply tokens

```text
--- seed 7 ---
[1] Eu estou morto, hein?
[2] Você é uma pessoa que pode ser vista, hein?
[3] Olá, velho, bom dia!
[4] Oi?
[5] A sua tia que morreu está usando maquiagem?
[6] Sim, hein?
[7] O chefe da Uragamatsu Funeral Home é Kururin, 25 anos.
[8] O dono da cervejaria é Suzume, 19 anos.
--- seed 8 ---
[1] Você está morto, hein?
[2] Você está morto, hein?
[3] Olá, enterrador de enterrados, bom dia
[4] ?
[5] Aos enterrados da minha tia?
[6] Sim...
[7] O Sr. Kururin, presidente da Enterrador de Enterrados Kururin, 25 anos
[8] O Sr. Suzume, solicitante de serviços funerários
--- seed 9 ---
[1] Você está morto, hein?
[2] Você está morto, hein?
[3] Olá, enterrador de entes queridos, ótimo dia
[4] ?
[5] Aquele ente querido que morreu, o maquiagem?
[6] ?
[7] O chefe da Urasu Funeral Service, Urasu Kusumaru, 25 anos
[8] O solicitante de serviços funerários, Sakurae Suzuki, 19 anos
```

**sys-ctl** · seed 7: 1603 ms, 173 prompt / 112 reply tokens · seed 8: 1673 ms, 173 prompt / 121 reply tokens · seed 9: 1923 ms, 173 prompt / 148 reply tokens

```text
--- seed 7 ---
[1] Estou morto, hein?
[2] "O que vocês podem ver" são esses tipos de pessoas.
[3] Olá, velho morto, bom dia
[4] ?
[5] A maquiagem da minha irmã falecida?
[6] Sim...
[7] O chefe da Ushiroma Funeral Service, Ushiroma Kururū, 25 anos
[8] O solicitante de funeral, Sakurae Suzuki, 19 anos
--- seed 8 ---
[1] Estou morto, né.
[2] "Os que podem ser vistos" são esses tipos.
[3] Olá, confrário de enterramento de espíritos, ótimo para você.
[4] ?
[5] A maquiagem da minha irmã falecida?
[6] Sim...
[7] O chefe da Ushigome Funeral Service, Ushigome Kururin, 25 anos.
[8] O solicitante de funeral, Sakurae Suzuki, 19 anos.
--- seed 9 ---
[1] Estou morto, hein?
[2] "O que vocês chamam de 'pessoas visíveis' são esses, hein?"
[3] Olá, guia dos enterramentos de mortos, ótimo dia
[4] ?
[5] O que vocês chamam de "pessoas visíveis" estão usando a maquiagem da minha irmã que morreu?
[6] Sim...
[7] O dono da Ura Kurokura Funeral Service, Ura Kurokura Kururin, 25 anos
[8] O solicitante de serviços funerários, Sakurae Suzuki, 19 anos
```

**sys** · seed 7: 1754 ms, 224 prompt / 110 reply tokens · seed 8: 1781 ms, 224 prompt / 113 reply tokens · seed 9: 1850 ms, 224 prompt / 119 reply tokens

```text
--- seed 7 ---
[1] Estou morto, hein?
[2] "O que vocês podem ver" são pessoas assim.
[3] Olá, fantasma funerária, bom dia!
[4] ?
[5] O maquiagem da minha irmã falecida?
[6] Sim...
[7] O chefe da Uragami Funeral Service, Uragami Kusumaru, 25 anos
[8] O solicitante de funerais, Sakurae Suzuki, 19 anos
--- seed 8 ---
[1] Estou morto, néo.
[2] "Os humanos que podem ser vistos" são vocês.
[3] Olá, morto de alma, bom dia.
[4] ?
[5] A maquiagem da sua tia morta?
[6] Sim...
[7] O chefe da Uragamatsu Funeral Service, Uragamatsu Kusumaru, 25 anos.
[8] O solicitante de funeral, Sakurae Suzume, 19 anos.
--- seed 9 ---
[1] Estou morto, hein?
[2] "Ou seja, esses são os tipos que você vê."
[3] Olá, morto de coragem, senhor. Que bom ter você aqui.
[4] ?
[5] O maquiagem da minha irmã falecida?
[6] Sim...
[7] O dono da Ushiro Funeral Service, Ushiro Kusumaru, 25 anos.
[8] O solicitante de funeral, Sakurae Suzuki, 19 anos.
```

**sys-qwen** · seed 7: 1758 ms, 234 prompt / 106 reply tokens · seed 8: 1812 ms, 234 prompt / 111 reply tokens · seed 9: 1748 ms, 234 prompt / 105 reply tokens

```text
--- seed 7 ---
[1] Estou morto, hein?
[2] "Os humanos que podem ser vistos" são esses, hein?
[3] Oi, funeral director, good to meet you
[4] ?
[5] The makeup of the deceased aunt?
[6] Yes...
[7] Funeral director Uchigusa Funeral Service, Uchigusa Kusumaru, 25 years old
[8] Funeral director Sakura Mune, 19 years old
--- seed 8 ---
[1] Estou morto, vocês.
[2] "As pessoas que podem ser vistos são esses."
[3] Olá, morto de coragem, bom dia!
[4] ?
[5] O maquiagem da minha irmã falecida?
[6] Sim...
[7] O Sr. Kurokuru, chefe da Kurokuru Funeral Service, 25 anos.
[8] O dono do funeral, Sakura Mizu, 19 anos.
--- seed 9 ---
[1] Estou morto, hein?
[2] "Os humanos visíveis" são esses, hein?
[3] Obrigado pela presença, fantasma funeral house, good to meet you
[4] ?
[5] A maquiagem da minha irmã falecida?
[6] Sim...
[7] O Sr. Kurokuru Funeral House, 25 anos
[8] O Sr. Sazanami, 19 anos
```


### tojime-dense-action, hand transcription, French

**none** · seed 7: 2541 ms, 260 prompt / 180 reply tokens · seed 8: 2680 ms, 260 prompt / 193 reply tokens · seed 9: 2755 ms, 260 prompt / 201 reply tokens

```text
--- seed 7 ---
[1] Le mafieux qui se léchette n'est pas un peu
[2] G'gu
[3] Ah
[4] Fermez les yeux, c'est un couteau!
[5] Avec le couteau au côté du torse, je peux voir ce que ça donne?
[6] Ah?
[7] Ah, ah...
[8] Arrête, c'est pas le moment!
[9] PLAN
[10] N'importe quoi?
[11] Tu sais peut-être...
[12] Fille?
[13] Ah, oui... mais...
[14] Hohoho
[15] Montrons-nous!
[16] Ah?
[17] Fermez les yeux, mais tu as une intelligence!
[18] Un peu...!
--- seed 8 ---
[1] Le mafieux qui me suche n'est pas un gars
[2] Grrrr
[3] Ah
[4] Ferme les yeux, tu es un couteau à sang
[5] Avec un fusil sur la cuisse, tu peux-je te montrer?
[6] Ah?
[7] Ah, c'est vrai...
[8] Arrête, c'est pas la peine!
[9] PLAN
[10] ?
[11] Tu sais, peut-être...
[12] Fille?
[13] Ah, oui... mais...
[14] Hohoho
[15] Montrons-nous les dents
[16] Ah?
[17] Ferme les yeux, mais tu as un bon esprit, tu peux le vendre
[18] Ah, un peu...!
--- seed 9 ---
[1] Le mafieux qui se léchette est probablement un mafieux.
[2] G'aaaaah!
[3] Aah!
[4] Fermez les yeux, c'est un gardien.
[5] Avec un fusil à tire-longs, je vais voir si je peux te faire une tête de cerveau.
[6] Ah?
[7] Ah, ah…
[8] Arrêtez, c'est pas le moment!
[9] PLAN
[10] ??
[11] Tu sais, peut-être ?
[12] Fille ?
[13] Ah, ah... c'est vrai, mais...
[14] Hohoho
[15] Montrons-nous la face.
[16] Ah ?
[17] Fermez les yeux, mais tu as un bon esprit, tu peux te le payer.
[18] Yaaah !
```

**inline-shipped** · seed 7: 3474 ms, 341 prompt / 245 reply tokens · seed 8: 3269 ms, 341 prompt / 225 reply tokens · seed 9: 3281 ms, 341 prompt / 226 reply tokens

```text
--- seed 7 ---
[1] (rude) Je ne veux pas être touché par la mafia.
[2] Grrr
[3] Ah
[4] (rude) Est-ce que tu te caches derrière un masque de cerveau ?
[5] (rude) Tu as l'air d'avoir un arme cachée dans ton ventre ?
[6] (rude) Ah ?
[7] Ah, ah...
[8] (casual) Arrête, c'est pas grave !
[9] PLAN
[10] ?
[11] (rude) Tu penses quoi ?
[12] (rude) Tu es une fille ?
[13] (polite) Oui, mais... c'est vrai ?
[14] Hohoho
[15] (rude) Montrons-nous nos poches !
[16] (rude) Ah ?
[17] (rude) Si tu te caches derrière un masque, tu as une intelligence supérieure, alors tu peux acheter quoi que ce soit !
[18] (casual) Ah, ah...
--- seed 8 ---
[1] (rude) Je ne veux pas être touché par la mafia
[2] Grrrrrr
[3] Ah
[4] (rude) Est-ce que tu veux que je ferme les yeux pour t'attaquer?
[5] (rude) Tu as des chances si tu es armé
[6] (rude) Ah?
[7] Ah, ah...
[8] (casual) Arrête, c'est pas le moment!
[9] PLAN
[10] ?
[11] (rude) Tu sais quoi?
[12] (rude) Tu es une fille?
[13] (polite) Oui, mais... c'est vrai...
[14] Hohoho
[15] (rude) Montrons-nous la face
[16] (rude) Ah?
[17] (casual) Montrons-nous la face, alors?
[18] (casual) Ah, ah, ah!
--- seed 9 ---
[1] (rude) Je ne veux pas être touché par la mafia.
[2] Grrrr
[3] Ah
[4] (rude) Est-ce que tu te caches avec tes yeux?
[5] (rude) Tu as des chances de te faire tuer avec ton arme sur l'épaule?
[6] (rude) Ah?
[7] Ah, ah...
[8] (casual) Arrêtez! C'est pas le moment!
[9] PLAN
[10] ?
[11] (rude) Tu es sûr que tu es un garçon?
[12] (rude) Tu es une fille?
[13] (polite) Ah, oui... C'est vrai...
[14] Hohoho
[15] (rude) Montrons-nous nos dents!
[16] Ah!
[17] (rude) Si tu te caches, tu as une chance!
[18] (casual) Un peu...!
```

**inline-np** · seed 7: 3172 ms, 341 prompt / 212 reply tokens · seed 8: 6069 ms, 341 prompt / 494 reply tokens · seed 9: 2980 ms, 341 prompt / 194 reply tokens

```text
--- seed 7 ---
[1] Je ne sais pas ce que tu racontes.
[2] Grrr.
[3] Ah.
[4] Est-ce que tu as l'air de faire attention?
[5] Tu as été touché par une balle dans le cerveau avec un fusil à tire à distance?
[6] Ah?
[7] Ah, ah...
[8] Tu as raison, on ne doit pas faire confiance à ceux qui feront attention à leur sécurité.
[9] Plan
[10] Qu'est-ce que tu veux dire?
[11] Tu penses que...
[12] Tu es une fille?
[13] Oui, mais...
[14] Hohoho.
[15] Montrons-nous nos dents.
[16] Ah?
[17] Si tu fermes les yeux, tu as un esprit bien élevé, alors je peux te vendre quelque chose.
[18] Hohoho!
--- seed 8 ---
[1] Je ne sais pas ce que tu racontes.
[2] Grrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr
[3] Ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah ah
[4] Est-ce que tu ferme les yeux pour être un couteau suisse?
[5] Je me demande si tu as une arme dans le dos.
[6] Ah?
[7] Ah, ah...
[8] Tu as raison, je ferme les yeux pour être un couteau suisse.
[9] Plan
[10] Ah?
[11] Tu sais quoi, tu es une fille?
[12] Tu es une fille?
[13] Oui, c'est vrai, mais...
[14] Hohoho
[15] Allez, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, mon
[16] Ah?
[17] Allez, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, mon
[18] Allez, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, monsieur, mon

--- seed 9 ---
[1] Oh, I'm not going to touch the mafia.
[2] Goo
[3] Ah
[4] Oh, is it just because I close my eyes that I'm an idiot?
[5] Oh, if you're going to shoot me with a gun on my waist, you can see my brain.
[6] Oh?
[7] Ah, that's it...
[8] Hey, calm down! This is not the place for you.
[9] Plan
[10] Nn?
[11] You too, maybe?
[12] You know, girl?
[13] Oh, uh, yes, but...
[14] Hoh
[15] Show me your face.
[16] Oh?
[17] Close your eyes, and if you have some brains, I'll buy you.
[18] Hey, just a little...!
```

**sys-ctl** · seed 7: 2685 ms, 250 prompt / 199 reply tokens · seed 8: 3503 ms, 250 prompt / 282 reply tokens · seed 9: 2624 ms, 250 prompt / 192 reply tokens

```text
--- seed 7 ---
[1] Le mafieux qui se léchette n'est pas le plus souvent
[2] Grr
[3] Ah
[4] Fermez les yeux, c'est un espadon!
[5] Avec un fusil à tire-longs, je peux te montrer ce qui se passe dans la tête?
[6] Ah?
[7] Ah, ah...
[8] Arrête, c'est pas le moment!
[9] Plan
[10] Nn?
[11] Tu sais, peut-être
[12] Fille?
[13] Ah ouais... mais c'est vrai...
[14] Hohoho
[15] Montrons-nous la face
[16] Ah?
[17] Fermez les yeux, mais si tu as un esprit, tu pourras toujours le vendre...
[18] Ah, un peu...!
--- seed 8 ---
[1] Le mafieux, je le suce, je le prends, je le prends, je le prends.
[2] Grrrr
[3] Ah
[4] Ferme les yeux, tu es un couteau!
[5] Avec le canon de ton holster, je vais te faire avaler la tête!
[6] Ah?
[7] Ah, ah…
[8] Arrête, arrête, là, là!
[9] Plan
[10] Nn?
[11] Tu sais, tu es peut-être une fille?
[12] Ouais, ouais, mais je suis une fille!
[13] Ah, ah, ah…
[14] Hohoho
[15] Montrons-nous nos dents!
[16] Ah?
[17] Ferme les yeux, mais tu as un esprit bien élevé!
[18] Ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, 
--- seed 9 ---
[1] Le mafieux qui se léchette est probablement un peu trop délicat.
[2] Grr
[3] Ah
[4] Fermez les yeux, c'est un gardien de la loi.
[5] Avec un fusil sur la hanche, tu vas voir si je peux te faire un coup de tête?
[6] Ah?
[7] Ah, ah…
[8] Arrête, c'est pas le moment!
[9] Plan
[10] Nn?
[11] Tu sais peut-être?
[12] Fille?
[13] Ah, ah…
[14] Hohoho
[15] Montrons-nous la face!
[16] Ah?
[17] Fermez les yeux, mais tu as un esprit de gardien!
[18] Yaa, yaa!
```

**sys** · seed 7: 3277 ms, 332 prompt / 230 reply tokens · seed 8: 2802 ms, 332 prompt / 181 reply tokens · seed 9: 2859 ms, 332 prompt / 187 reply tokens

```text
--- seed 7 ---
[1] Le mafieux qui se délecte de ce genre de truc est probablement un type un peu trop vulgaire.
[2] Grrrr
[3] Ah
[4] Ferme les yeux, tu es un type d'entraînement!
[5] Avec un fusil à tire-longs, tu as l'air d'avoir un cerveau qui a été tiré par la tranchée?
[6] Ah?
[7] Ah, c'est ça...
[8] Stop, c'est pas dans ce genre de situation!
[9] Plan
[10] ?
[11] Tu sais, peut-être...
[12] Fille?
[13] Ah, oui... c'est vrai, mais...
[14] Hohoho
[15] Montrons-nous la face
[16] Ah?
[17] Ferme les yeux, mais même si tu as un esprit, tu peux toujours trouver un acheteur...
[18] Ah, un peu...!
--- seed 8 ---
[1] Le mafieux se lécher la langue, c'est probable
[2] Grr
[3] Ah
[4] Fermez les yeux, tu es un couteau?
[5] Avec un fusil à tire-longs, tu as quoi dans le crâne?
[6] Ah?
[7] Ah, c'est moi...
[8] Arrête, là!
[9] Plan
[10] ?
[11] Tu sais, peut-être
[12] Fille?
[13] Eh, oui... mais...
[14] Hoh
[15] Montrer ton visage
[16] Ah?
[17] Fermez les yeux, mais si tu as une intelligence, tu pourras toujours être acheté...
[18] Un peu...!
--- seed 9 ---
[1] Le contact avec les mafieux est courant.
[2] Grr
[3] Ah
[4] Ferme les yeux, tu es un cordon bleu.
[5] Avec un couteau à côté de la tête, tu peux-je te montrer le cordon bleu?
[6] ?
[7] ?
[8] Arrête, ici, c'est trop tôt!
[9] Plan
[10] ?
[11] Tu sais, peut-être...
[12] C'est une fille?
[13] Ah oui... c'est vrai, mais...
[14] Hohoho
[15] Montrer ton visage.
[16] ?
[17] Ferme les yeux, mais même un cordon bleu peut être acheté...
[18] Un peu...!
```

**sys-qwen** · seed 7: 2861 ms, 342 prompt / 184 reply tokens · seed 8: 3000 ms, 342 prompt / 199 reply tokens · seed 9: 3614 ms, 342 prompt / 257 reply tokens

```text
--- seed 7 ---
[1] Le mafia se lèche la langue, je suppose.
[2] Grrr.
[3] Ah.
[4] Ferme les yeux, tu es un couteau à sang!
[5] Avec un fusil à tire-longs, tu as eu la tête de ton cerveau?
[6] Ah?
[7] Ah, c'est ça...
[8] Arrête, c'est pas le moment!
[9] Plan
[10] ?
[11] Tu crois que...
[12] Fille?
[13] Eh bien, oui... mais...
[14] Hohoho
[15] Montrons-nous le visage!
[16] Ah?
[17] Ferme les yeux, mais tu as un esprit bien!
[18] Un peu...!
--- seed 8 ---
[1] Les mafieux ne sont pas des gens à se laisser manipuler facilement.
[2] Grrrr.
[3] Ah.
[4] Fermez les yeux, c'est un cordonnier.
[5] Avec un fusil à tire-longs, je peux te faire une décapitation.
[6] Ah?
[7] Ah, c'est toi…
[8] Stop, on ne peut pas se laisser manipuler comme ça!
[9] Plan
[10] ?
[11] Tu as peut-être raison…
[12] C'est une fille?
[13] Ah, oui… c'est vrai.
[14] Hohoho
[15] Montrer tes tripes.
[16] Ah?
[17] Fermez les yeux, mais même si tu as une intelligence, tu seras acheté.
[18] Un peu…!
--- seed 9 ---
[1] Le mafia, je ne peux pas le supporter.
[2] Grrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr
[3] Ah!
[4] Fermez les yeux, tu es un couteau à sang!
[5] Avec un fusil sur l'abdomen, tu veux me montrer quoi?
[6] Ah?
[7] Ah, ah...
[8] Arrête, ça suffit!
[9] Plan
[10] ?
[11] Tu sais, tu peux être...
[12] Fille?
[13] Ah, oui... c'est vrai...
[14] Hohoho
[15] Montrer ton visage!
[16] Ah?
[17] Fermez les yeux, mais tu as un esprit d'acier!
[18] Un peu...!
```


### tojime-dense-action, hand transcription, Spanish

**none** · seed 7: 2727 ms, 260 prompt / 194 reply tokens · seed 8: 2741 ms, 260 prompt / 198 reply tokens · seed 9: 8468 ms, 260 prompt / 768 reply tokens

```text
--- seed 7 ---
[1] La mafiosa se la come a pecho.
[2] Gah
[3] Aaah
[4] Cierro los ojos y soy el bastón.
[5] Con un tiro en la cadera, ¿qué pasa?
[6] Ah?
[7] Ah, ah…
[8] ¡Detente! ¡Esto no es lugar para esto!
[9] PLAN
[10] ¿Qué?
[11] ¿También tú, tal vez?
[12] ¿Eh? ¿Eh?
[13] ¡Eh! ¡Eh!
[14] Huh
[15] ¡Tápate la cara!
[16] ¡Eh!
[17] ¡Cierro los ojos! ¡Y si tienes buen juicio, vendrás!
[18] ¡Un momento!
--- seed 8 ---
[1] La mafiosa se la come a pecho.
[2] Gah
[3] Ah
[4] Cierro los ojos y soy el guardián?
[5] Con un tiro de la cadera, ¿qué pasa si me lo quito?
[6] Ah?
[7] Ah, eso…
[8] ¡Vamos, por favor! ¡En esta situación!
[9] PLAN
[10] ¿Qué?
[11] ¿También tú, tal vez?
[12] ¿Eh? ¿Eh?
[13] ¿Eh? ¡Sí, pero...!
[14] Huh
[15] ¡Mira!
[16] ¿Eh?
[17] ¡Cierro los ojos! ¡Y si tienes buen juicio, vendrás a comprar!
[18] ¡Un momento!
--- seed 9 ---
[1] La mafiosa se la come a pecho.
[2] Gua
[3] Ah
[4] Cierro los ojos para ser un asesino?
[5] ¿Con un revólver en la cintura, veré el cerebro desgarrado?
[6] ¿?
[7] ¿A, eso?
[8] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[9] Plánu
[10] ¿?
[11] ¿También tú, tal vez?
[12] ¿Mujer?
[13] ¡¿? ¡Sí, pero...!
[14] Hoho
[15] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[16] ¿?
[17] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[18] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
```

**inline-shipped** · seed 7: 3216 ms, 341 prompt / 220 reply tokens · seed 8: 3207 ms, 341 prompt / 220 reply tokens · seed 9: 4832 ms, 341 prompt / 362 reply tokens

```text
--- seed 7 ---
[1] (rough) La mafiosa me la come a la mayoría
[2] Goo
[3] Aa
[4] (rough) Cierra los ojos, es un guardián
[5] (rough) Con un arma en el cuello, ¿me la quita?
[6] (rough) Aa?
[7] A, ah…
[8] (casual) ¡Un momento! ¡Esto no es para aquí!
[9] PLAN
[10] Nn?
[11] (rough) ¿También?
[12] (rough) ¿Hombre?
[13] (polite) ¡Eh, sí… pero… ¡sí! ¡Sí!
[14] Ho
[15] (rough) Ve la cara
[16] ¡Eh!
[17] (rough) Con los ojos cerrados, ¿tienes buen juicio?
[18] (casual) ¡Un momento!
--- seed 8 ---
[1] (rough) La mafia me come a la base
[2] GG
[3] Ah
[4] (rough) Cierro los ojos soy el asesino?
[5] (rough) ¿Con un arma de fuego en el cuello, ¿me lo querrás ver?
[6] (rough) ¿?
[7] Ah, ah...
[8] (casual) ¡Un momento, por favor! No estoy aquí.
[9] PLAN
[10] ¿?
[11] (rough) ¿También?
[12] (rough) ¿Hombre?
[13] (polite) ¡Oh, sí... pero... ¡sí!...
[14] Huh
[15] (rough) ¡Muestra tu cara!
[16] ¿?
[17] (rough) Cierro los ojos y tu inteligencia me lo comprará...
[18] (casual) ¡Un momento, ¡un momento!
--- seed 9 ---
[1] (rough) La mafiosa me la come a la mayoría
[2] GG
[3] Ah
[4] (rough) Cierro los ojos soy el asesino?
[5] (rough) ¿Con un arma de fuego en el cuello?
[6] (rough) ¿?
[7] Ah, ah...
[8] (casual) ¡Basta ya! ¡Esto no es para aquí!
[9] PLAN
[10] ¿?
[11] (rough) ¿También tú?
[12] (rough) ¿Eh?
[13] (polite) ¡Oh! ¡Sí, pero...
[14] Hoho
[15] (rough) ¡Muestra la cara!
[16] ¡¿Qué?
[17] (rough) Cierro los ojos y el ingenio es lo que se compra...
[18] (casual) ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

```

**inline-np** · seed 7: 8712 ms, 341 prompt / 768 reply tokens · seed 8: 8887 ms, 341 prompt / 768 reply tokens · seed 9: 6013 ms, 341 prompt / 500 reply tokens

```text
--- seed 7 ---
[1] ¡El mafioso me toca la polla! ¡Tengo que verlo!
[2] ¡Grrr!
[3] ¡Aaah!
[4] ¡¿Cerrando los ojos es un asesino?
[5] ¡Con un revólver en la cintura, ¿qué haría si me disparaba la cabeza?
[6] ¡Aa?
[7] ¡A, eso...
[8] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[9] ¡Plano!
[10] ¡?
[11] ¡¿¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[12] ¡¿¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[13] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 8 ---
[1] ¡Soy una perra que se come a los demás! ¡Todas las veces que me tocan, me come!
[2] ¡Grrr!
[3] ¡Aaah!
[4] ¡Cierro los ojos y soy un asesino?
[5] ¡Con un arma en la cintura, ¿qué haría si me disparaban el cerebro?
[6] ¡Aa?
[7] ¡A, eso...
[8] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[9] ¡Plano!
[10] ¿?
[11] ¡¿Qué?!
[12] ¡¿Qué?!
[13] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[14] Hoho
[15] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[16] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
--- seed 9 ---
[1] Yo, no me toca tocar a la mafia.
[2] Grrr...
[3] Ah...
[4] ¿Qué? ¿Qué estás haciendo?
[5] ¿Con qué arma te disparaste? ¿Quieres ver mi cerebro?
[6] ¿Qué?
[7] ¡Ah, ah...
[8] ¡No, no, no! ¡No aquí!
[9] Plano
[10] ¿Qué?
[11] ¿Tú también?
[12] ¿Tú eres una mujer?
[13] ¡Pero sí! ¡Sí, pero...
[14] Huh...
[15] ¡Mira mi cara!
[16] ¡¿Qué?! ¡¿Qué te crees que haces!
[17] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[18] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

```

**sys-ctl** · seed 7: 2713 ms, 250 prompt / 199 reply tokens · seed 8: 2667 ms, 250 prompt / 193 reply tokens · seed 9: 2699 ms, 250 prompt / 199 reply tokens

```text
--- seed 7 ---
[1] La mafiosa se la come a poca gente
[2] Gah
[3] Ah
[4] Cierro los ojos soy el asesino?
[5] ¿Con un arma de fuego en el cuello, ¿qué pasa?
[6] Ah?
[7] Ah, eso…
[8] ¡No, por favor! ¡En este lugar!
[9] PLAN
[10] ¿Qué?
[11] ¿También tú?
[12] ¿Eh? ¿Eh? ¿Eh?
[13] ¡Eh! ¡Eh! ¡Eh!
[14] Huhu
[15] ¡Muestra tu cara!
[16] ¡Eh!
[17] ¡Cierro los ojos, pero si tienes buen juicio, te compraré!
[18] ¡Un poco más!

--- seed 8 ---
[1] La mafiosa se la come a pescara, la mayoría.
[2] Goo
[3] Aaah
[4] Cierro los ojos, soy un guardián?
[5] Con un arma en el cuello, ¿quieres ver mi cerebro?
[6] Aa?
[7] A, eso...
[8] ¡Un momento! No estoy en este lugar.
[9] Plánu
[10] Nn?
[11] ¿También?
[12] ¿Es una mujer?
[13] ¡Oh, sí... pero...!
[14] Hoho
[15] ¡Saca la cara!
[16] ¡Aaah!
[17] Cierro los ojos, pero si tienes buen juicio, te compraré.
[18] ¡Un momento!
--- seed 9 ---
[1] La mafiosa se la come a pescara, la mayoría.
[2] Guau
[3] Aaah
[4] Cierro los ojos y soy el asesino?
[5] Con un arma en la cintura, ¿qué te parece que te haga daño?
[6] Aa?
[7] A, ah...
[8] ¡Cállate! ¡En esta situación!
[9] Plánu
[10] ¿N?
[11] ¿También?
[12] ¿Es una mujer?
[13] ¡¿Qué? ¡Sí, pero...!
[14] Hoho
[15] ¡Muestra la cara!
[16] ¡Aaah!
[17] Cierro los ojos y, aunque sea, es un buen negocio...
[18] ¡Un poco más!
```

**sys** · seed 7: 2961 ms, 332 prompt / 199 reply tokens · seed 8: 3027 ms, 332 prompt / 204 reply tokens · seed 9: 8735 ms, 332 prompt / 768 reply tokens

```text
--- seed 7 ---
[1] Mafioso se la toman la lengua, ¿hay que decirle?
[2] Goo
[3] Aaah
[4] Cierro los ojos, soy un asesino?
[5] ¿Con un arma de fuego en el cuello, ¿qué harías?
[6] Aa?
[7] A, ah...
[8] ¡Un momento! ¿En este lugar?
[9] Plano
[10] ¿N?
[11] ¿Tú también, tal vez?
[12] ¿Eh?
[13] ¡Eh, sí... pero...!
[14] Huh
[15] ¡Muestra tu cara!
[16] Aaah
[17] Cierro los ojos, pero si tienes buen juicio, vendrán a comprarte...
[18] ¡Un momento...!
--- seed 8 ---
[1] La mafiosa se la come a poca gente
[2] Grr
[3] Ah
[4] Cierro los ojos y soy el asesino?
[5] ¿Con un revólver en el cuello, ¿qué te parece que te saquen el cerebro?
[6] ?
[7] ?
[8] ¡Pero, pero… ¡qué mala suerte!
[9] Plano
[10] ¿?
[11] ¿También tú, tal vez?
[12] ¿Eh?
[13] ¡Eh, sí… pero… sí!
[14] Huh
[15] ¡Pero si me das la cara!
[16] ¡Eh!
[17] ¡Pero si te cierras los ojos, ¿no puedes ser un buen negocio?
[18] ¡Pero un poco…!
--- seed 9 ---
[1] Mafiosismo, me lo tomo a pecho abierto.
[2] Gah!
[3] Ah.
[4] Cierra los ojos, es un asesino.
[5] Con un tiro de pistola, ¿qué te parece que te saquen el cerebro?
[6] ¿?
[7] ¿A, ah...
[8] ¡Mierda, ¡mierda, ¡mierda!
[9] Plan.
[10] ¿?
[11] ¿También tú, ¿no?
[12] ¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿¿
[13] ¡¿¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[14] Hah
[15] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[16] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
```

**sys-qwen** · seed 7: 3055 ms, 342 prompt / 197 reply tokens · seed 8: 3059 ms, 342 prompt / 204 reply tokens · seed 9: 8213 ms, 342 prompt / 718 reply tokens

```text
--- seed 7 ---
[1] Mafiya舐めんのも大概にしやがれ
[2] GG
[3] Aa
[4] Cerrar los ojos, es el asesino?
[5] ¿Con un revólver en el cuello, verás?
[6] ?
[7] Aa, ah…
[8] ¡Por favor, ¡no! ¡En este lugar!
[9] PLAN
[10] ¿?
[11] ¿También, ¿también?
[12] ¿Hombre?
[13] ¡Eh, sí… pero… ¡sí! ¡Sí, sí, sí!
[14] Hoho
[15] ¡Dame la cara!
[16] ¡Eh!
[17] Cerrar los ojos, pero el cerebro es bueno…
[18] ¡Un poco más…!
--- seed 8 ---
[1] El mafioso se la trae a la boca.  
[2] Grrr  
[3] Ah  
[4] Cierro los ojos y soy el asesino?  
[5] ¿Con un revólver en el cuello, ¿qué harías?  
[6] Ah?  
[7] Ah, ah...  
[8] ¡No! ¡En este lugar!  
[9] Plano  
[10] ¿Qué?  
[11] ¿También tú, tal vez?  
[12] ¿Eh?  
[13] ¡Ah! ¡Sí, pero...!  
[14] Huhu  
[15] ¡Ponte la cara al frente!  
[16] ¿Qué?  
[17] Cierro los ojos, pero si tienes buen juicio, te compraré...  
[18] ¡Un segundo!
--- seed 9 ---
[1] La mafiosa se la come a poca cosa
[2] Gah
[3] Ah
[4] Cierro los ojos y soy el asesino?
[5] ¿Con un revólver en el cuello, ¿qué harías?
[6] ?
[7] ?
[8] ¡Un momento! ¡En este lugar!
[9] Plano
[10] ¿Qué?
[11] ¿También tú, tal vez?
[12] ¿Una mujer?
[13] ¡¿Qué?! ¡Sí, pero...!
[14] Hoho
[15] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[16] ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! ¡¿Qué?! 
[17] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[18] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

```


### tojime-dense-action, hand transcription, German

**none** · seed 7: 2883 ms, 260 prompt / 206 reply tokens · seed 8: 2745 ms, 260 prompt / 197 reply tokens · seed 9: 2614 ms, 260 prompt / 185 reply tokens

```text
--- seed 7 ---
[1] Die Mafia-Zunge ist nicht zu fassen.
[2] G'schmerz
[3] Ach
[4] Schließen die Augen, sie ist der Schutzschild!
[5] Mit dem Revolver im Hals, was ist das für ein Schädel?
[6] Ach?
[7] Ach, das ist so…
[8] Halt, halt, so weit ist es noch nicht!
[9] PLAN
[10] Nimm's auf den Mund!
[11] Du auch, oder?
[12] Weißt du, was ich meine?
[13] Ach, ja... das ist so...
[14] Hör auf, so weit ist es noch nicht!
[15] Zeig deinen Kopf!
[16] Ach?
[17] Schließen die Augen, auch das ist ein Zeichen!
[18] Ach, ein bisschen...!
--- seed 8 ---
[1] Die Mafia-Schlammling ist nicht zu verachten.
[2] G'schmerz.
[3] Ach
[4] Schließen die Augen, sie ist der Waffe.
[5] Mit dem Revolver im Rücken, was ist mit dem Hirn?
[6] Ach?
[7] Ach, das ist ja…
[8] Lassen Sie das, so weit ist es noch nicht!
[9] PLAN
[10] Nee?
[11] Hast du auch?
[12] Weißt du, was?
[13] Ach, ja… das ist so, aber…
[14] Hohhh
[15] Zeig deinen Gesichtsausdruck an
[16] Ach?
[17] Schließen die Augen, auch das ist ein Talent…
[18] Ach, ein bisschen…!
--- seed 9 ---
[1] Die Mafia-Zunge ist nicht zu fassen.
[2] G's
[3] Aaah
[4] Schließe die Augen, sie ist der Schutzschild.
[5] Mit dem Revolver im Schoß, was ist mit dem Hirn?
[6] Aa?
[7] Aa, das ist ja...
[8] Lass das, so weit bin ich schon.
[9] PLAN
[10] Nnnn?
[11] Oder Oder du bist es?
[12] Eine Frau?
[13] Aa, das ist ja...
[14] Hoho
[15] Zeig mir mal deine Nase!
[16] Aa?
[17] Schließe die Augen, auch das ist ein Talent!
[18] Yassss!
```

**inline-shipped** · seed 7: 3654 ms, 341 prompt / 266 reply tokens · seed 8: 3601 ms, 341 prompt / 260 reply tokens · seed 9: 3545 ms, 341 prompt / 253 reply tokens

```text
--- seed 7 ---
[1] (rough) Ich werde mich nicht an den Mafiagewissheiten halten.
[2] Gute Nacht.
[3] Ach, das ist ja toll.
[4] (rough) Ich denke, man sollte sich auf die Augen achten.
[5] (rough) Ich würde mich gerne mit dem Revolver an der Brust verletzen lassen, um zu sehen, ob ich noch so gut wie vorher bin.
[6] Ach?
[7] Ach, das ist ja toll...
[8] (casual) Lass uns doch mal aufhören! So weit reicht es ja nicht.
[9] Planer
[10] Was?
[11] (rough) Hast du auch?
[12] (rough) So wie du siehst, ist sie eine Frau?
[13] (polite) Ach, ja... Sieht so aus, als ob du recht hast.
[14] Hm?
[15] (rough) Lass uns die Augen sehen.
[16] Ach?
[17] (rough) Wenn man die Augen schließt, kann man ja auch noch klug sein.
[18] (casual) Ach, das ist ja toll...
--- seed 8 ---
[1] (rough) Ich mag's nicht, wenn man mich von den Mafiabeklauern herumredet.
[2] Gute Nacht.
[3] Ach, Ach.
[4] (rough) Ich schätze, du bist ein Mann, der sich auf seine Gedanken konzentriert.
[5] (rough) Ich würde gern sehen, wie du mit dem Revolver auf deinen Schädel schlägst.
[6] Ach?
[7] Ach, das ist ja ein komisches Wort.
[8] (casual) Lass uns das mal auskundschaften!
[9] Plan.
[10] Was?
[11] (rough) Hast du auch Angst vor mir?
[12] (rough) Hast du auch eine Frau?
[13] (polite) Ach, ja... Sieht so aus, als ob du recht hast.
[14] Hm.
[15] (rough) Ich will dich sehen, ohne dich zu sehen.
[16] Ach?
[17] (rough) Du könntest auch ein cleverer Mensch sein.
[18] (casual) Ach, das ist ja ein komisches Wort.
--- seed 9 ---
[1] (rugged) Mafiya-schlammberei kann ich mir nicht leisten
[2] Guck
[3] Ach
[4] (rugged) Ich hoffe, du verstehst, dass ich dich nicht anfassen kann
[5] (rugged) Ich will dich nur sehen, wie du stirbst
[6] Ach?
[7] Ach, das ist so ein Idiot
[8] (casual) Ich muss aufhören! So was ist nicht in deinem Leben passiert
[9] PLAN
[10] Was?
[11] (rugged) Hast du auch?
[12] (rugged) So ein Idiotin bist du?
[13] (polite) Ach, nein, das ist nicht wahr...
[14] Huhu
[15] (rugged) Ich will dich sehen, wie du stirbst
[16] Ach?
[17] (rugged) Ich hoffe, du verstehst, dass ich dich nicht anfassen kann
[18] (casual) Ach, das ist so ein Idiot!
```

**inline-np** · seed 7: 3365 ms, 341 prompt / 231 reply tokens · seed 8: 3379 ms, 341 prompt / 232 reply tokens · seed 9: 2782 ms, 341 prompt / 172 reply tokens

```text
--- seed 7 ---
[1] Ich werde mich nicht mit den Mafiabisseln anlegen.
[2] Gute Nacht.
[3] Ach
[4] Ich denke, man sollte sich die Augen schließen, wenn man sich in einer Gefahr befindet.
[5] Ich habe den Arm mit dem Gewehr verletzt, was ich mir vorstellen muss, wenn ich mich an einem Mann verirre.
[6] Ach?
[7] Ach, das ist ja komisch...
[8] Ich sage dir, lass das! So weit habe ich noch nicht gedacht!
[9] Plan
[10] Was?
[11] Hast du auch?
[12] Hast du auch?
[13] Ich muss sagen, ja, das ist richtig.
[14] Hm
[15] Ich will dich sehen, wenn du dich verirrst.
[16] Ach?
[17] Ach, wenn man sich nur die Augen schließt, kann man auch einiges kaufen.
[18] Ach, das ist ja komisch...
--- seed 8 ---
[1] Ich werde mich nicht mit Mafiakuss beschäftigen.
[2] Gute Nacht.
[3] Ach
[4] Ich dachte, du bist ein Verräter?
[5] Ich habe dich mit dem Gewehr erschossen und den Kopf deines Gehirns abgezogen.
[6] Ach?
[7] Ach, das ist ja komisch...
[8] Ich sage dir, lass uns aufhören! So weit hier ist es nicht!
[9] Plan
[10] Was?
[11] Hast du auch?
[12] Hast du auch?
[13] Ja, ich habe es gehört, aber ich glaube, es ist nur ein Scherz.
[14] Hör auf, ich will nicht!
[15] Lass mich die Augen schließen sehen!
[16] Ach, du bist ja verrückt!
[17] Ich denke, wenn du dich stillhältst, wird man dich auch kaufen!
[18] Ach, du bist ja ein Narr!
--- seed 9 ---
[1] Wasch mir die Mafiabisseln!
[2] Gute Nacht!
[3] Ach
[4] Wasch die Augen, damit du den Mann verstehst?
[5] Wasch die Arme, damit du den Kopf sehen kannst!
[6] Ach?
[7] Ach, das ist ja komisch!
[8] Wasch dich mal!
[9] Plan!
[10] Wasch dich mal!
[11] Wasch dich mal!
[12] Wasch dich mal!
[13] Wasch dich mal!
[14] Wasch dich mal!
[15] Wasch dich mal!
[16] Ach?
[17] Ach, das ist ja komisch!
[18] Ach, das ist ja komisch!
```

**sys-ctl** · seed 7: 2563 ms, 250 prompt / 185 reply tokens · seed 8: 2684 ms, 250 prompt / 197 reply tokens · seed 9: 2634 ms, 250 prompt / 189 reply tokens

```text
--- seed 7 ---
[1] Die Mafia-Zunge ist fürchterlich.
[2] Gugg
[3] Ach
[4] Schließen die Augen ist der Waffe.
[5] Mit dem Revolver auf dem Bauch sehen, wie ich dich erschieße?
[6] Ach?
[7] Ach, das ist so…
[8] Halt, halt! So weit ist es nicht!
[9] PLAN
[10] Nnn?
[11] Oder du bist auch eine Frau?
[12] Oder?
[13] Ach, das ist so…
[14] Hohoho
[15] Sieh zu, dass ich dich sehe.
[16] Ach?
[17] Schließen die Augen auch, dann kann ich dich kaufen.
[18] Ach, nur ein bisschen…!
--- seed 8 ---
[1] Die Mafia-Schmeichelei ist für die meisten da.
[2] Gutt
[3] Ach
[4] Schließen die Augen ist der Waffe.
[5] Mit dem Revolver in der Hüfte würde ich dich umbringen.
[6] Ach?
[7] Ach, das ist…
[8] Halt, halt! So weit ist es noch nicht!
[9] PLAN
[10] Nimm's gut!
[11] Du meinst, du bist auch eine Frau?
[12] Ja?
[13] Ach, ja... Aber das ist ja auch schon so.
[14] Huhu
[15] Lass mich das Gesicht sehen!
[16] Ach?
[17] Ach, das ist... Aber das ist ja auch schon so.
[18] Ach, das ist... Aber das ist ja auch schon so.

--- seed 9 ---
[1] Die Mafia-Zunge ist in der Regel nicht.
[2] Gute Nacht.
[3] Ach.
[4] Schließe die Augen, sie sind der Verstand.
[5] Mit dem Revolver im Rücken, was ich mir vorstellen kann?
[6] Ach?
[7] Ach, das ist…
[8] Lass das! So weit ist es noch nicht!
[9] PLAN
[10] Was?
[11] Hast du auch?
[12] Weißt du, ich könnte das auch sein?
[13] Ach, ja... Das ist aber so.
[14] Huhu
[15] Zeig deinen Kopf!
[16] Ach?
[17] Schließe die Augen auch, dann kann man dich kaufen.
[18] Hast du schon mal einen kleinen Schnitt?
```

**sys** · seed 7: 2970 ms, 332 prompt / 198 reply tokens · seed 8: 3126 ms, 332 prompt / 206 reply tokens · seed 9: 3014 ms, 332 prompt / 203 reply tokens

```text
--- seed 7 ---
[1] Die Mafia-Zunge ist nicht zu schätzen.
[2] Gute Nacht.
[3] Ach.
[4] Schließen Sie die Augen, Sie sind der Wächter.
[5] Mit dem Revolver im Rücken, was ist mit dem Kopf?
[6] Ach?
[7] Ach, das ist…
[8] Halt, halt, hier ist nicht der Ort!
[9] PLAN
[10] Was?
[11] Du auch, vielleicht?
[12] Ein Mädchen?
[13] Ach, ja… das ist aber wahr!
[14] Hör auf!
[15] Zeig mir mal dein Gesicht!
[16] Ach?
[17] Schließen Sie die Augen, auch dann ist es nicht ausgeschlossen, dass man es kaufen kann.
[18] Hör auf, nur ein bisschen...!
--- seed 8 ---
[1] Mafia-schmeißen kann ich nicht zulassen
[2] Guck
[3] Aaah
[4] Schließen die Augen, das ist der Wächter!
[5] Mit dem Revolver in der Hüfte, was ist das für ein Scherz?
[6] Ach?
[7] Ach, das ist so…
[8] Lass das! So weit bist du schon!
[9] PLAN
[10] Nimm's
[11] Du auch? Ich dachte, du wärst ein Mädchen?
[12] Frau?
[13] Ach ja... ja, das ist so...
[14] Huhu
[15] Zeig deinen Gesichtsausdruck an
[16] Ach?
[17] Schließen die Augen, aber auch dann ist es noch kein Problem!
[18] Ach, ein bisschen...!
--- seed 9 ---
[1] Die Mafia-tasten werden von den meisten genommen.
[2] Gute Nacht.
[3] Ach
[4] Schließe die Augen, wenn du dich auf den Schläger konzentrierst?
[5] Was, wenn ich mit dem Revolver auf deinen Kopf schieße?
[6] Ach?
[7] Ach, das ist so…
[8] Lass das! So weit draußen!
[9] PLAN
[10] Was?
[11] Du auch, vielleicht?
[12] Eine Frau?
[13] Ach, ja… das ist aber wahr?
[14] Hm
[15] Sieh zu, dass du dich zeigt.
[16] Ach?
[17] Schließe die Augen, aber es gibt auch Leute, die ihre Eigenschaften trotzdem haben.
[18] Ach, ein bisschen…!
```

**sys-qwen** · seed 7: 2969 ms, 342 prompt / 194 reply tokens · seed 8: 3028 ms, 342 prompt / 191 reply tokens · seed 9: 3353 ms, 342 prompt / 224 reply tokens

```text
--- seed 7 ---
[1] Die Mafia saugt sich an der Sache nicht.
[2] Guck!
[3] Ach
[4] Schließen Sie die Augen, Sie sind der Wächter!
[5] Mit dem Revolver im Hals, was ist mit dem Hirn?
[6] Ach?
[7] Ach, das ist…
[8] Halt mal! So weit ist es noch nicht!
[9] PLAN
[10] Nee?
[11] Du auch, hast du?
[12] Frau?
[13] Ach, ja… das ist aber wahr!
[14] Huhu
[15] Sieh zu, dass du dich zeigst!
[16] Ach?
[17] Schließen Sie die Augen, auch dann ist man noch nicht ausgeschlossen!
[18] Ach, ein bisschen...!
--- seed 8 ---
[1] Mafia-tastiert auch in der Regel
[2] Guck
[3] Aaah
[4] Schließen die Augen, das ist der Wächter
[5] Mit dem Revolver im Rücken, was ist mit dem Kopf?
[6] Aa?
[7] Aa, das ist...
[8] Kurz halt! So weit ist es noch!
[9] PLAN
[10] Nnn?
[11] Du auch, vielleicht?
[12] Was für eine Frau?
[13] Eeeh ja... das ist aber so, das ist aber so...
[14] Hohoho
[15] Zeig deinen Bauch!
[16] Aaah
[17] Schließen die Augen, auch dann ist der Charakter gut...
[18] Yessss!
--- seed 9 ---
[1] Die Mafia lickt sich die Zunge. Das ist normal.
[2] Gack
[3] Aaah
[4] Schließe die Augen, wenn du dich auf den Armwurf vorbereiten möchtest?
[5] Was, wenn ich mit dem Revolver auf deinen Kopf schieße?
[6] Aa?
[7] Aa, das ist …
[8] Lass es gut sein! So weit bist du schon!
[9] PLAN
[10] Nnn?
[11] Du meinst, du könntest auch ein Mädchen sein?
[12] Was, ein Mädchen?
[13] Aaah, ja... das ist aber so was von normal!
[14] Hohoho
[15] Zeig deinen Kopf!
[16] Aaah?
[17] Schließe die Augen, aber auch dann ist dein Charakter gut genug, um gekauft zu werden!
[18] Yessss!
```


### tojime-dense-action, hand transcription, Brazilian Portuguese

**none** · seed 7: 2650 ms, 261 prompt / 179 reply tokens · seed 8: 2582 ms, 261 prompt / 182 reply tokens · seed 9: 2683 ms, 261 prompt / 193 reply tokens

```text
--- seed 7 ---
[1] O começo da mafia é geralmente um estupro
[2] GG
[3] Ah
[4] Fechando os olhos é o meu guarda-chuva?
[5] Usando um tiro na perna do abdômen, como vai?
[6] Ah?
[7] Ah, aquilo…
[8] Tá, vamos parar por enquanto! Nesse lugar!
[9] PLAN
[10] N?
[11] Você também pode ser?
[12] Mulher?
[13] Ah, sim... mas...
[14] Huh
[15] Mostra a cara
[16] Ah?
[17] Fechando os olhos, também pode ser um bom negócio...
[18] Tá, um pouco...!
--- seed 8 ---
[1] O que você está fazendo com o nariz domafia?
[2] Gua
[3] Ah
[4] Fechando os olhos é o seu guardião?
[5] Usando o tiro na cintura para ver o cérebro?
[6] Ah?
[7] Ah, aquele…
[8] Pode parar um pouco! Isso não é lugar para isso!
[9] Plano
[10] N?
[11] Você também pode ser?
[12] Mulher?
[13] Ah, sim…mas…
[14] Huh
[15] Mostre o rosto para eu ver.
[16] Ah?
[17] Fechando os olhos, também é uma habilidade.
[18] Um pouco…!
--- seed 9 ---
[1] O que você está fazendo com a mafia?
[2] Grita
[3] Ah
[4] Fechando os olhos é um ateu?
[5] Usando um revólver na cintura, vai ver se eu não te encontro?
[6] Ah?
[7] Ah, ah…
[8] Pare, por favor! Não está na hora!
[9] Plano
[10] Nada?
[11] Você também pode ser?
[12] Mulher?
[13] Oh, sim... mas...
[14] Huhu
[15] Mostra o rosto para eu ver se não é uma mulher.
[16] Ah?
[17] Fechando os olhos, mesmo que eu não seja um idiota, ainda posso comprar você.
[18] Ah, um pouco...!
```

**inline-shipped** · seed 7: 3183 ms, 342 prompt / 217 reply tokens · seed 8: 3197 ms, 342 prompt / 218 reply tokens · seed 9: 3444 ms, 342 prompt / 243 reply tokens

```text
--- seed 7 ---
[1] (rough) A mafia toshin ni mo ageru
[2] GG
[3] Ah
[4] (rough) Fechar os olhos é um guarda-chuva?
[5] (rough) Com um tiro na cintura, verá se o cérebro está vivo?
[6] (rough) Ah?
[7] Ah, ah…
[8] (casual) Tchau! Não é assim que se fala!
[9] PLAN
[10] ?
[11] (rough) Você também, de jeito nenhum?
[12] (rough) Mulher?
[13] (polite) Ah, ah... Sim, mas...
[14] Hohoho
[15] (rough) Mostre o rosto
[16] Ah?
[17] (rough) Fechar os olhos também pode ser uma habilidade?
[18] (casual) Ah, ah...!
--- seed 8 ---
[1] (rough) A mafia tá na boca deles, geralmente.
[2] GG
[3] Ah
[4] (rough) Fechando os olhos é um ateu?
[5] (rough) Sei lá, com um tiro na cabeça, o cérebro fica lá pra fora?
[6] (rough) Ah?
[7] Ah, ah…
[8] (casual) Vamos parar! Não está na hora!
[9] PLÁN
[10] N?
[11] (rough) Você também, né?
[12] (rough) Mulher?
[13] (polite) Ah, ah... Sim, mas...
[14] Hoh
[15] (rough) Mostra o rosto.
[16] Ah?
[17] (rough) Fechando os olhos, o seu juízo é bom?
[18] (casual) Vamos, um pouco...!
--- seed 9 ---
[1] (rough) A mafia toshimono ga agatte kudasai
[2] Goo
[3] Achi
[4] (rough) Chokushi ga uta-moto da?
[5] (rough) Yoko no kuzure de ika kara kotsu mo kudasai?
[6] (rough) A?
[7] A, ano…
[8] (casual) Totten yomede yo! Tsuba kara!
[9] Plaan
[10] N?
[11] (rough) Ote mo shitsurei
[12] (rough) Onna ka?
[13] (polite) E! Hyaku! Sore wa kudasai!
[14] Ho!
[15] (rough) Hana shite miru
[16] E?
[17] (rough) Chokushi mo kikai ga koto ga yoroku da?
[18] (casual) Ya! Chokushi mo kikai ga koto ga yoroku da?
```

**inline-np** · seed 7: 2620 ms, 342 prompt / 157 reply tokens · seed 8: 3066 ms, 342 prompt / 201 reply tokens · seed 9: 8449 ms, 342 prompt / 743 reply tokens

```text
--- seed 7 ---
[1] Não me tocam na mafia
[2] Tudo bem
[3] Ah
[4] Não, você é um guarda-chu?
[5] Com um tiro na cabeça, você vai se sentir bem?
[6] Ah?
[7] Ah, ah...
[8] Não, você não pode ser um garoto?
[9] Plano
[10] O que?
[11] Você também?
[12] Você é uma mulher?
[13] Ah, ah...
[14] Oh
[15] Mostra-me a cara
[16] Ah?
[17] Olha, olha, olha...
[18] Ah, ah...
--- seed 8 ---
[1] Não me toque na máscara, geralmente não me tocam na máscara.
[2] Tchau
[3] Ah
[4] Não, você é um guarda-chu?
[5] Sei lá, com um tiro na cabeça, você poderia ver o que aconteceu com o seu cérebro?
[6] Ah?
[7] Ah, ah...
[8] Não, você não está se escondendo, você está se escondendo.
[9] Plano
[10] Tchau
[11] Você também, você também?
[12] Você é uma mulher?
[13] Ah, ah...
[14] Huhu
[15] Tente mostrar seu rosto
[16] Ah?
[17] Se você fechar os olhos, você pode ser comprado mesmo?
[18] Ah, ah...
--- seed 9 ---
[1] Não me tocam na máfia, já sei.
[2] Tchau.
[3] Ah.
[4] Não, você é um guarda-chu?
[5] Sei lá, um tiro na cabeça, olha só.
[6] Ah?
[7] Ah, ah...
[8] Não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, não, 
[9] Plano.
[10] Tchau.
[11] Você também, você também, você também, você também, você também, você também, você também, você também, você também, você também, você também, você também, você
[12] Você também, você também, você também, você também, você também, você também, você também, você também, você também, você também, você também, você também, você
[13] Ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, 
[14] Oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, oh, 
[15] Olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha
[16] Ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, 
[17] Olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha
[18] Olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha, olha
```

**sys-ctl** · seed 7: 2507 ms, 251 prompt / 177 reply tokens · seed 8: 2707 ms, 251 prompt / 200 reply tokens · seed 9: 2512 ms, 251 prompt / 176 reply tokens

```text
--- seed 7 ---
[1] O que omafia beijar não é geralmente
[2] Gigg
[3] Ah
[4] Fechando os olhos é um guarda-chuva?
[5] Com o tiro na cintura, verá o cérebro
[6] Ah?
[7] Ah, ah…
[8] Tá, vamos parar por enquanto! Nesse lugar!
[9] Plaian
[10] N?
[11] Você também pode ser
[12] Mulher?
[13] Ah, ah... Sim, mas...
[14] Huh
[15] Mostra a cara
[16] Ah?
[17] Fechando os olhos, também pode ser bom
[18] Um pouco...!
--- seed 8 ---
[1] A mafia to kusumei ni mo kangaeru ga arigatou
[2] Goo
[3] Aa
[4] Fechar os olhos é um guarda-chuva?
[5] Arma na cintura, vai ver se eu estouro o cerebro?
[6] Aa?
[7] Aa, aquele…
[8] Tchau, tá na hora!
[9] Plaian
[10] Nn?
[11] O seu também pode ser
[12] Mulher?
[13] Aaa, sim, mas…
[14] Hohoho
[15] Mostra o rosto
[16] Aaa?
[17] Fechar os olhos, mas o seu juízo é bom, também é comprado…
[18] Tchau, um pouco…

--- seed 9 ---
[1] O que omafia beijar não é geralmente
[2] G
[3] Ah
[4] Fechando os olhos é um guarda-chuva?
[5] Com o rifle na cintura, verá o cérebro cortado?
[6] Ah?
[7] Ah, aquele…
[8] Pode parar! Isso não é lugar para isso!
[9] PLAN
[10] N?
[11] Você também pode ser
[12] Mulher?
[13] Ah, sim… Mas vocês são…
[14] Hoh
[15] Mostre a face
[16] Ah?
[17] Fechando os olhos, também é bom
[18] Um pouco…!
```

**sys** · seed 7: 2682 ms, 333 prompt / 167 reply tokens · seed 8: 2738 ms, 333 prompt / 172 reply tokens · seed 9: 2922 ms, 333 prompt / 194 reply tokens

```text
--- seed 7 ---
[1] O que você está fazendo com os criminosos?
[2] Gua
[3] Ah
[4] Olha, você é um guardião?
[5] Com o tiro na cabeça, você vai ver se está vivo?
[6] Ah?
[7] Ah, ah...
[8] Pare! Não está na hora!
[9] Plano
[10] ?
[11] Você também pode ser...
[12] Uma mulher?
[13] Ah, sim... Mas...
[14] Huh
[15] Mostre seu rosto
[16] Ah?
[17] Olha, mesmo fechado os olhos, você tem capacidade?
[18] Ah, um pouco...!
--- seed 8 ---
[1] O que você está fazendo com os criminosos?
[2] Ggg
[3] Ah
[4] Olha, você é um espantalho?
[5] Com o tiro na cabeça, você pode ver o meu coração?
[6] Ah?
[7] Ah, ah...
[8] Pare, por favor! Não está na hora!
[9] Plano
[10] ?
[11] Você também, talvez?
[12] É uma mulher?
[13] Ah, sim... mas...
[14] Huh
[15] Mostra a cara!
[16] Ah?
[17] Olha, mesmo fechando os olhos, você tem um bom senso!
[18] Um pouco...!
--- seed 9 ---
[1] O toque de mafiosos é geralmente feito com um punhal.
[2] GG
[3] Ah
[4] Fechando os olhos é o que se chama de "espinha".
[5] Usando um rifle na cintura, verá o cérebro cortado?
[6] Ah?
[7] Ah, ah…
[8] Tchau, tchau! Não é assim que se fala!
[9] Plano
[10] ?
[11] Você também, talvez?
[12] Mulher?
[13] Ah, ah... Mas é verdade.
[14] Huh?
[15] Mostra a cara!
[16] Ah?
[17] Fechando os olhos, a inteligência é o que importa.
[18] Um pouco...!
```

**sys-qwen** · seed 7: 3583 ms, 343 prompt / 257 reply tokens · seed 8: 3051 ms, 343 prompt / 204 reply tokens · seed 9: 3570 ms, 343 prompt / 254 reply tokens

```text
--- seed 7 ---
[1] Mafiosos tokei no mo kawasaka ni shimeru ga arigatou
[2] Goo
[3] Aa
[4] Zoku o kigoto ga yasumi da?
[5] Hoshi no kuzen de iru kouki mo tsukai no shiru kouki mo tsukai no shiru kouki mo tsukai no shiru kouki mo tsukai no shiru kouki mo tsukai no shiru kouki mo tsuk
[6] Aa?
[7] A, ane…
[8] Soshite yo! Tsuba kara!
[9] Plan
[10] N?
[11] Ore mo shika shite
[12] Onna ka?
[13] E? Ii… sou desu ka?
[14] Ee
[15] Hantei shite miru
[16] Ee
[17] Zoku o kigoto ga kari no kyou ga yasumi da
[18] Yattsu chotto!
--- seed 8 ---
[1] A mafia to mitsukeru mo kangaeru ga arigatou
[2] Goo
[3] A
[4] Zoku to yasui dake?
[5] Yoko no kuzure de ika karu kusogu miru ka?
[6] A?
[7] A, ano…
[8] Chotto yotte yo! Soshite koko de!
[9] Plaian
[10] N?
[11] Ote mo shite shite
[12] Onna ka?
[13] E! Iie… sou de mo…
[14] Eo
[15] Genmae shite miru
[16] Aa
[17] Zoku to mo kikai ga yori ya yomu shi…
[18] Ya to shitsurei shi!
--- seed 9 ---
[1] O que você está fazendo com a mafia? (Formal)
[2] Gostei! (Casual)
[3] Ah! (Casual)
[4] Olha, você é um guarda-chuva? (Casual)
[5] Você usou um revólver na cintura e quer ver o que acontece com o cérebro? (Formal)
[6] Ah? (Casual)
[7] Ah, ah... (Casual)
[8] Pare! Não está na hora! (Formal)
[9] Plano! (Casual)
[10] O que? (Casual)
[11] Você também, talvez? (Casual)
[12] Você é uma mulher? (Casual)
[13] Ah, sim... mas... (Formal)
[14] Oh! (Casual)
[15] Mostre seu rosto! (Formal)
[16] Ah? (Casual)
[17] Olha, mesmo fechando os olhos, você tem um bom senso! (Formal)
[18] Um pouco...! (Casual)
```


### balloon-dense-dialogue, real emulator OCR, gated, French

**none** · seed 7: 2212 ms, 220 prompt / 160 reply tokens · seed 8: 2368 ms, 220 prompt / 177 reply tokens · seed 9: 2219 ms, 220 prompt / 160 reply tokens

```text
--- seed 7 ---
[10] Il est certainement bête
[9] Mu-gu
[4] En général, c'est une caméra à usage unique
[1] Les photos de la Terre sont toujours disponibles sur le net
[11] Tchou
[2] C'est possible, mais...
[13] Qu'est-ce qui te prend?! Si tu ne mettras pas de hélicolène dans ton ballon, il ne pourra pas voler
[5] Pourrait-on faire ça dans l'espace?!
[8] C'était pas cher
[7] Ah
[3] Il y a des fois où on se rend compte qu'on peut faire quelque chose de la tête
[6] C'est vrai, mais...
--- seed 8 ---
[10] Il est certainement bête
[9] Mmm
[4] En fait, c'est un appareil à usage unique
[1] Il n'y a pas de photo de la Terre sur Internet, à part celles que l'on peut trouver sur le net
[11] Tssss
[2] C'est vrai, en principe, c'est possible, mais...
[13] Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne va pas voler!
[5] Pourquoi vouloir faire des photos de film dans l'espace?!
[8] C'était pas cher, donc...
[7] Ah
[3] Il a fallu que je le prenne moi-même pour...
[6] C'est vrai...
--- seed 9 ---
[10] Il est certainement bête
[9] Mmm
[4] En fait, c'est une caméra à usage unique
[1] Les photos de la Terre sont facilement disponibles sur le net
[11] Tch
[2] C'est possible, mais...
[13] Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne va pas voler
[5] Est-ce que tu as l'intention de prendre des photos de la Lune avec du film?
[8] C'était un peu cher, donc...
[7] Ah
[3] Il n'a pas dû vouloir se faire photographier
[6] C'est vrai...
```

**inline-shipped** · seed 7: 3058 ms, 300 prompt / 216 reply tokens · seed 8: 3040 ms, 300 prompt / 214 reply tokens · seed 9: 3125 ms, 300 prompt / 224 reply tokens

```text
--- seed 7 ---
[10] (rude) C'est certainement une baka!
[9] (rough) Muguu
[4] (rude) En fait, c'est une caméra à usage unique!
[1] (casual) Les photos de la Terre, elles tombent partout sur Internet, non?
[11] (rude) T'as vraiment raison!
[2] (casual) C'est vrai, en principe, c'est possible...
[13] (rude) Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne monte pas!
[5] (rude) Est-ce que tu as l'intention de faire des photos de l'espace avec du film?
[8] (polite) C'est vraiment cher, donc...
[7] (rude) Ah...
[3] (casual) Pourquoi tu vas te prendre la tête pour ça...
[6] (polite) C'est vrai...
--- seed 8 ---
[10] (rude) C'est certainement un imbécile!
[9] mmm
[4] (rude) En fait, c'est une caméra à usage unique!
[1] (casual) Les photos de la Terre sont partout sur le net, n'est-ce pas?
[11] (rude) T'as vraiment raison!
[2] (casual) Oui, en principe, c'est possible, mais...
[13] (rude) Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne va pas voler!
[5] (rude) Tu crois vraiment que tu peux faire des photos de l'espace avec du film?
[8] (polite) C'était un peu cher, hein...
[7] ah
[3] (casual) Tu as vraiment besoin de prendre des photos toi-même?
[6] (polite) Oui, enfin...

--- seed 9 ---
[10] (rude) C'est certainement un imbécile
[9] (rough) Mmm
[4] (rude) En fait, c'est une caméra à usage unique!
[1] (casual) Les photos de la Terre se trouvent partout sur Internet, n'est-ce pas?
[11] (rude) Tssss
[2] (casual) Oui, en principe, c'est possible, mais...
[13] (rude) Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne va pas flotter!
[5] (rude) Est-ce que tu as l'intention de faire des photos de l'espace avec du film?
[8] (polite) C'était un peu cher, hein...
[7] (rude) Ah
[3] (casual) Tu as vraiment voulu faire ça, hein?
[6] (polite) Oui, c'est vrai...
```

**inline-np** · seed 7: 2547 ms, 300 prompt / 159 reply tokens · seed 8: 2631 ms, 300 prompt / 171 reply tokens · seed 9: 2336 ms, 300 prompt / 140 reply tokens

```text
--- seed 7 ---
[10] C'est certainement une baka!
[9] Mmm
[4] C'est probablement une caméra à usage unique!
[1] Les photos de la Terre sont toujours disponibles sur le net, n'est-ce pas?
[11] Tssss
[2] C'est possible, mais...
[13] Qu'est-ce qui te fait penser que ça ne marche pas?
[5] Est-ce que tu as l'intention de prendre des photos de l'espace avec du film?
[8] Je suis désolé, mais...
[7] Ah
[3] Tu es vraiment idiot de vouloir prendre des photos avec une caméra?
[6] C'est vrai, n'est-ce pas?

--- seed 8 ---
[10] Qu'il est certainement bête!
[9] Mmm
[4] En fait, il s'agit d'un appareil à usage unique!
[1] Tu peux toujours trouver des photos de la Terre sur Internet, n'est-ce pas?
[11] Qu'est-ce que c'est!
[2] C'est possible, mais il faudrait un ballon rempli d'air, n'est-ce pas?
[13] Qu'est-ce qui te fait penser que ça ne marche pas?
[5] Pourquoi vouloir photographier l'espace avec du film?
[8] Je suis désolé, mais...
[7] Ah
[3] Tu as du talent, n'est-ce pas?
[6] C'est vrai, n'est-ce pas?

--- seed 9 ---
[10] C'est certainement un imbécile
[9] Mmm
[4] En fait, c'est une caméra à usage unique
[1] C'est du net, ça tombe bien
[11] Tss
[2] C'est possible, mais...
[13] Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne monte pas
[5] C'est possible, mais...
[8] Enfin, c'était pas cher
[7] Ah
[3] Tu as du mal à faire ça par toi-même...
[6] C'est vrai...
```

**sys-ctl** · seed 7: 2337 ms, 210 prompt / 175 reply tokens · seed 8: 2082 ms, 210 prompt / 149 reply tokens · seed 9: 2287 ms, 210 prompt / 167 reply tokens

```text
--- seed 7 ---
[10] C'est certainement un imbécile
[9] Muguu
[4] Pourquoi utilise-t-on des caméras à jetable?
[1] Il y a tellement de photos sur Internet que l'on peut les trouver n'importe où sur la Terre
[11] Tant pis
[2] C'est possible, mais... c'est un peu... ah non...
[13] Qu'est-ce qui te prend?! Si tu ne mettras pas de hélicorne, ton ballon ne pourra pas voler!
[5] Tu as raison, mais... c'est un peu... ah non...
[8] C'était pas cher, donc...
[7] Ah
[3] Pourquoi tu vas te prendre la tête à faire ça...?
[6] C'est vrai...
--- seed 8 ---
[10] C'est certainement une baffe!
[9] Mugga!
[4] En fait, c'est une caméra à usage unique!
[1] Les photos de la Terre, elles tombent partout sur Internet!
[11] Tch!
[2] En effet, en principe, c'est possible...
[13] Qu'est-ce qui te prend?! Sans hélicolène, le ballon ne bougera pas!
[5] On pourrait faire ça dans l'espace, non?
[8] C'était pas cher, donc...
[7] Ah!
[3] Pourquoi avoir pris le temps de prendre une photo?
[6] C'est vrai...

--- seed 9 ---
[10] Il est certainement bête
[9] Mmm
[4] En fait, c'est une caméra à usage unique!
[1] Les photos de la Terre se retrouvent facilement sur Internet
[11] Tch
[2] C'est vrai, en principe, c'est possible...
[13] Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne se lève pas!
[5] Avez-vous jamais pensé à prendre des photos de l'espace avec des pellicules?
[8] C'était un peu cher...
[7] Ah
[3] Il y a des fois où il faut se débarrasser de la photo!
[6] C'est vrai...
```

**sys** · seed 7: 2509 ms, 286 prompt / 164 reply tokens · seed 8: 2542 ms, 286 prompt / 168 reply tokens · seed 9: 2589 ms, 286 prompt / 171 reply tokens

```text
--- seed 7 ---
[10] C'est certainement un imbécile
[9] Mu-guu
[4] En fait, c'est une caméra à usage unique!
[1] Les photos de la Terre sont toujours sur Internet, n'est-ce pas
[11] Tch
[2] En effet, en principe, c'est possible, mais...
[13] Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène dans ton ballon, il ne va pas flotter!
[5] Pourquoi vouloir photographier l'espace avec du film?!
[8] C'était tellement cher...
[7] Ah
[3] Pourquoi avoir pris le temps de faire ça...
[6] C'est vrai...
--- seed 8 ---
[10] C'est certainement une baka!
[9] Muguu
[4] En général, c'est une caméra à usage unique!
[1] Les photos de la Terre sont partout sur le net, n'est-ce pas?
[11] T'as raison!
[2] C'est vrai, en principe, c'est possible, mais...
[13] Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne va pas voler!
[5] Est-ce que tu as envie de faire des photos de l'espace avec du film?
[8] C'est pour ça que...
[7] Ah
[3] Pourquoi tu me fais ça, toi?
[6] C'est vrai...
--- seed 9 ---
[10] C'est certainement un imbécile.
[9] Muuuuuh!
[4] En fait, c'est une caméra à usage unique!
[1] Les photos de la Terre sont partout sur Internet, n'est-ce pas?
[11] Tch!
[2] C'est vrai, en théorie, c'est possible, mais...
[13] Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne va pas voler!
[5] Est-ce que tu as jamais pensé à utiliser des pellicules dans l'espace?
[8] Je m'en suis souvenu pour...
[7] Ah...
[3] Pourquoi tu as voulu faire ça toi-même...
[6] C'est vrai...
```

**sys-qwen** · seed 7: 2549 ms, 296 prompt / 168 reply tokens · seed 8: 2397 ms, 296 prompt / 151 reply tokens · seed 9: 2659 ms, 296 prompt / 181 reply tokens

```text
--- seed 7 ---
[10] C'est certainement un imbécile
[9] Muuuu
[4] En fait, c'est une caméra à usage unique
[1] Il y a tellement de photos sur Internet que...
[11] Tssss
[2] Oui, en principe, c'est possible, mais...
[13] Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne va pas flotter!
[5] Y a-t-il vraiment l'envie de prendre des photos de l'espace avec du film?
[8] C'était tellement cher...
[7] Ah
[3] Pourquoi avoir pris le temps de prendre une photo à toi-même...
[6] C'est vrai...
--- seed 8 ---
[10] C'est certainement une baka
[9] Mmm
[4] En fait, c'est une caméra à usage unique
[1] Il y a tellement de photos sur Internet que...
[11] Tch
[2] C'est possible, mais...
[13] Qu'est-ce qui te prend?! Si tu n'as pas d'hydrogène, ton ballon ne monte pas!
[5] Est-ce que tu as l'intention de prendre des photos dans l'espace?!
[8] C'était tellement cher...
[7] Ah
[3] Pourquoi tu as pris le temps de faire ça...?
[6] C'est vrai...

--- seed 9 ---
[10] C'est certainement un imbécile!
[9] Muuuuuh!
[4] En fait, c'est une caméra à usage unique!
[1] Les photos de la Terre sont facilement disponibles sur le net, n'est-ce pas?
[11] Tch!
[2] C'est vrai, en théorie, c'est possible, mais...
[13] Qu'est-ce qui te prend?! Sans hélicolé, le ballon ne flotte pas!
[5] Y a-t-il vraiment l'intention de prendre des photos de la lune avec du film?!
[8] C'était pas cher, donc...
[7] Ah!
[3] Pourquoi avoir pris le temps de prendre une photo avec une caméra à usage unique, alors?
[6] C'est vrai, n'est-ce pas?
```


### balloon-dense-dialogue, real emulator OCR, gated, Spanish

**none** · seed 7: 3790 ms, 220 prompt / 326 reply tokens · seed 8: 2306 ms, 220 prompt / 163 reply tokens · seed 9: 7654 ms, 220 prompt / 702 reply tokens

```text
--- seed 7 ---
[10] Ése es absolutamente un estúpido
[9] Muuuu
[4] Aunque eso es, eso es, eso es...
[1] ¡No hay nada que reprobar en las fotos de la Tierra!
[11] ¡Qué mierda!
[2] ¡Por cierto, te aseguro que es posible!
[13] ¡¿Qué demonios! No hay hélice, no hay hélice, no hay hélice!
[5] ¿Por qué no se puede hacer esto en el espacio?
[8] ¡Pues claro que sí! ¡Es tan caro!
[7] ¡Aaaah!
[3] ¡No es que no quiera hacerlo, es que no tengo tiempo!
[6] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 8 ---
[10] Él definitivamente es un bobo
[9] Muuuu
[4] ¿Por qué no es un cámara de uso único?
[1] ¡No hay nada que reprobar en las fotos de la Tierra en Internet!
[11] ¡Qué mierda!
[2] ¡Por supuesto, teóricamente, es posible!
[13] ¡¿Qué?!
[5] ¿Cómo puede ser que en el espacio, con film y disparador, se pueda hacer una foto?
[8] ¡Pues fue tan barato!
[7] ¡Aaah!
[3] ¡Siempre que se haga una foto, no importa si se usa una cámara profesional!
[6] ¡Eso es!

--- seed 9 ---
[10] Ése es absolutamente estúpido
[9] Muuuuuggh
[4] Aunque eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, eso es, y
[1] ¡En la Tierra, las fotos se pueden descargar en la web a voluntad!
[11] ¡Qué mierda!
[2] ¡Por cierto, teoricamente, es posible!
[13] ¡Qué mierda! ¡No hay hélice, no hay hélice, no hay hélice, no hay hélice, no hay hélice, no hay hélice, no hay hélice, no hay hélice, no hay hélice, no hay hélc
[5] ¡¿Qué?!
[8] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[7] ¡Aaaah!
[3] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[6] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

```

**inline-shipped** · seed 7: 5767 ms, 300 prompt / 491 reply tokens · seed 8: 5863 ms, 300 prompt / 496 reply tokens · seed 9: 2875 ms, 300 prompt / 201 reply tokens

```text
--- seed 7 ---
[10] (rough) ¡Ese es absolutamente un estúpido!
[9] むぐっ
[4] (rough) Eso es lo que dicen con su cámara de uso a corto plazo!
[1] (casual) ¡Tienes que ver las fotos de la Tierra en la web!
[11] (rough) ¡Qué mierda!
[2] (casual) ¡Por supuesto, teoricamente es posible!
[13] (rough) ¡¿Qué mierda! ¡No puedes hacer una bala de hielo sin helado!
[5] (rough) ¡¿Cómo puedes filmar en el espacio?!
[8] (polite) ¡Oh, eso fue bastante caro!
[7] ああ
[3] (casual) ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[6] (polite) ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 8 ---
[10] (rough) ¡Ese es absolutamente un estúpido!
[9] むぐっ
[4] (rough) Eso es lo que dicen con una cámara de fotos de uso temporal!
[1] (casual) ¡Tienes que ver las fotos de la Tierra en la web!
[11] (rough) ¡Qué mierda!
[2] (casual) ¡Por supuesto, teóricamente, es posible!
[13] (rough) ¡¿Qué mierda! ¡No puedes hacer fotos con un globo de gas helado sin oxígeno!
[5] (rough) ¡¿Cómo puedes hacer fotos en el espacio con film?
[8] (polite) ¡Oh, estaba un poco caro!
[7] ああ
[3] (casual) ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[6] (polite) ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 9 ---
[10] (rough) ¡Ese es absolutamente un estúpido!
[9] むぐっ
[4] (rough) Es probable que sea una cámara de uso único.
[1] (casual) ¡Tiene que haber fotos de la Tierra en internet!
[11] (rough) ¡Qué mierda!
[2] (casual) ¡Claro, te digo, te digo!
[13] (rough) ¡¿Qué demonios! ¿Por qué no metes hidrógeno en el balón de nieve?
[5] (rough) ¿Cómo puede un ser humano filmar en el espacio?
[8] (polite) ¡Oh, eso fue bastante caro!
[7] ああ
[3] (casual) ¡¿Por qué no te has metido en la cámara?
[6] (polite) ¡Eso es cierto!
```

**inline-np** · seed 7: 2795 ms, 300 prompt / 189 reply tokens · seed 8: 2768 ms, 300 prompt / 186 reply tokens · seed 9: 2575 ms, 300 prompt / 165 reply tokens

```text
--- seed 7 ---
[10] ¡Ese es definitivamente un estúpido!
[9] Muuuu
[4] ¡Eso es lo que dicen los comerciales!
[1] ¡Qué foto de la Tierra! ¿No puedes encontrarla en internet?
[11] ¡Qué mierda!
[2] ¡Por supuesto, te lo puedo hacer, pero...!
[13] ¡¿Qué?! ¡No puedes hacer una bala de hielo sin helado!
[5] ¡¿Cómo puedes filmar en el espacio con una película?!
[8] ¡Oh, me arrepiento de haber pagado tanto por esto!
[7] ¡Ahhhh!
[3] ¡¿Por qué te has molestado en tomar una foto cuando ya tienes una cámara profesional?
[6] ¡Por supuesto, eso es lo que dicen los comerciales!

--- seed 8 ---
[10] ¡Ese es absolutamente un estúpido!
[9] Muuuu
[4] ¡En realidad, es una cámara de uso único!
[1] ¡Qué fotos de la Tierra se pueden encontrar en la web!
[11] ¡Qué mierda!
[2] ¡Por supuesto, teóricamente, es posible!
[13] ¡¿Qué demonios! ¿Por qué no metes hidrógeno en el balón de nieve antes de que flote!
[5] ¡¿Cómo puede un ser humano filmar en el espacio con film y disparar?
[8] ¡Oh, estaba un poco caro, pero...
[7] ¡Oh!
[3] ¡¿Por qué no te metes en la cámara y la fotografías?
[6] ¡Por supuesto, eso es cierto!

--- seed 9 ---
[10] ¡Ese es absolutamente un estúpido!
[9] Muuuu
[4] ¡En realidad, es una cámara de uso único!
[1] ¡Tienes que ver las fotos del planeta en internet!
[11] ¡Qué mierda!
[2] ¡Por supuesto, en teoría, es posible!
[13] ¡¿Qué?! ¡No puedes hacer fotos con un globo de gas helado!
[5] ¡¿Cómo puedes filmarte en el espacio con una película?
[8] ¡Oh, eso fue bastante caro!
[7] ¡Aaaah!
[3] ¡¿Por qué te has molestado en tomar una foto?
[6] ¡Por supuesto, eso es así!

```

**sys-ctl** · seed 7: 6619 ms, 210 prompt / 610 reply tokens · seed 8: 6593 ms, 210 prompt / 602 reply tokens · seed 9: 8273 ms, 210 prompt / 768 reply tokens

```text
--- seed 7 ---
[10] Éste es loco de verdad
[9] Muuuuuggg
[4] ¿Por qué no es un reproductor de fotos?!
[1] ¡Tienes que ver esto en la web!
[11] ¡Qué mierda!
[2] ¡Claro, te lo puedo hacer!
[13] ¡¿Qué?! ¡No hay hélice, el globo no flota!
[5] ¡¿Cómo se puede filmar en el espacio con film?!
[8] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[7] ¡Aaaah!
[3] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[6] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 8 ---
[10] Éste está loco
[9] Muuuu
[4] ¿Por qué usa cámaras de fotos de uso único?
[1] ¡Esto está en la web!
[11] ¡Qué mierda!
[2] ¡Claro! En teoría, sí es posible...
[13] ¡¿Qué?! ¡No se puede flotar sin hidrógeno en el balón!
[5] ¿Por qué no se puede filmar en el espacio?!
[8] ¡Pues por eso! ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[7] ¡Aaaaah!
[3] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[6] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 9 ---
[10] Éste es loco
[9] Mu-guu
[4] ¿Por qué no usas una cámara de fotos de goma?
[1] ¡Tienes que ver esto en internet!
[11] ¡Qué mierda!
[2] ¡Claro, te lo digo por experiencia!
[13] ¡¿Qué mierda?! ¡No puedes hacer fotos con un heliuremo sin llenarlo de aire!
[5] ¿Por qué no quieres usar una cámara de fotos de goma?
[8] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[7] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[3] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[6] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
```

**sys** · seed 7: 8495 ms, 286 prompt / 768 reply tokens · seed 8: 3970 ms, 286 prompt / 316 reply tokens · seed 9: 2884 ms, 286 prompt / 205 reply tokens

```text
--- seed 7 ---
[10] Ese es absolutamente un estúpido
[9] Muuuu
[4] ¿Por qué usa una cámara de fotos de uso único?
[1] ¡En internet hay millones de fotos de la Tierra!
[11] ¡Qué mierda!
[2] ¡Por supuesto, teoricamente es posible!
[13] ¡¿Qué demonios! No flota sin hidrógeno en el tanque!
[5] ¿Cómo puede un ser humano filmar en el espacio?
[8] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[7] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[3] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[6] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
--- seed 8 ---
[10] Ese es definitivamente un estúpido
[9] Muuuu
[4] ¿Por qué usa un cámara de fotos de uso temporal?
[1] ¡Tienes que ver cómo se meten las fotos en la red!
[11] ¡Qué mierda!
[2] ¡Por cierto, teoricamente, es posible!
[13] ¡¿Qué demonios! No flota sin hidrógeno!
[5] ¡¿Por qué se mete en la película y en el disparador en el espacio?
[8] ¡Qué mala compra!
[7] ¡Aaah!
[3] ¡Siempre que se metan en la cámara!
[6] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 9 ---
[10] ¡Ese es absolutamente un estúpido!
[9] Muuuuugh!
[4] ¿Por qué usas una cámara de fotos de uso temporal?
[1] ¡Tienes que ver cómo se meten en la red las fotos de la Tierra!
[11] ¡Puto!
[2] ¡Por supuesto, teóricamente, es posible!
[13] ¡¿Qué te crees que te estás diciendo! Si no metes hidrógeno en el balón, no va a flotar!
[5] ¡¿Cómo se atreve a filmar en el espacio?! ¡No tiene ni idea de cómo se usa!
[8] ¡Qué mala compra! ¡Parece que estaba a un precio muy bajo!
[7] ¡Aaaah!
[3] ¡¿Por qué te metes en esto sin querer!
[6] ¡Eso es cierto...!
```

**sys-qwen** · seed 7: 4017 ms, 296 prompt / 319 reply tokens · seed 8: 4188 ms, 296 prompt / 335 reply tokens · seed 9: 2613 ms, 296 prompt / 169 reply tokens

```text
--- seed 7 ---
[10] ¡Ese es un estúpido!
[9] Muuuuugh!
[4] ¿Por qué usa una cámara de fotos de uso temporal?
[1] ¡Tanta información en la red!
[11] ¡Qué mierda!
[2] ¡Por supuesto, en teoría, es posible!
[13] ¡¿Qué demonios! No hay hélice, el globo no flota!
[5] ¡¿Cómo se puede filmar en el espacio con película?
[8] ¡Oh, estaba a un precio tan bajo!
[7] ¡Aaah!
[3] ¡¿Por qué no se le ha pedido a esa persona que se haga la foto?
[6] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 8 ---
[10] ¡Ese es absolutamente un estúpido!
[9] Muuuuugh...
[4] ¿Por qué usa una cámara de fotos instantáneas?
[1] ¡En internet, se pueden encontrar fotos de la Tierra con facilidad!
[11] ¡Qué mierda!
[2] ¡Por supuesto, en teoría, es posible!
[13] ¡¿Qué demonios?! ¡No se puede flotar una bala de gas helado sin el oxígeno!
[5] ¡¿Cómo puede alguien que vive en el espacio filmar con película?
[8] ¡Qué mala compra!
[7] ¡Aaah!
[3] ¡Siempre que se le pida, se le obliga a tomar una foto!
[6] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 9 ---
[10] ¡Ese es un estúpido!
[9] Muuuu
[4] ¿Por qué no es un reproductor de fotos de uso temporal?
[1] ¡No hay nada que reproducir en la Tierra!
[11] ¡Qué mierda!
[2] ¡Por supuesto, teóricamente, es posible!
[13] ¡¿Qué demonios! No se puede flotar sin hidrógeno en el balón!
[5] ¡¿Por qué no se puede filmar en el espacio con película?
[8] ¡Qué mala suerte!
[7] ¡Aaah!
[3] ¡¿Por qué no se hace una foto sin querer?
[6] ¡Eso es cierto!
```


### balloon-dense-dialogue, real emulator OCR, gated, German

**none** · seed 7: 2296 ms, 220 prompt / 169 reply tokens · seed 8: 2406 ms, 220 prompt / 171 reply tokens · seed 9: 2409 ms, 220 prompt / 173 reply tokens

```text
--- seed 7 ---
[10] Dieser ist definitiv dumm!
[9] Muuuuugggg!
[4] Normalerweise ist das eine Zuflugkamera!
[1] Auf der ganzen Welt fallen Fotos auf der Internet so oft wie du möchtest!
[11] Dummer!
[2] Ja, das ist möglich, aber...
[13] Was? Wenn ich Helium in den Windballon stecke, kann ich ihn nicht mehr hochkriechen!
[5] In der Galaxis kann man Filmpakete mit den Schaltkreisen schalten!
[8] Also war es zu dumm, das zu tun!
[7] Ach
[3] So habe ich es auch nicht gemacht...
[6] Das ist ja so eindeutig...
--- seed 8 ---
[10] Dieser ist definitiv dumm!
[9] Mu-gg!
[4] Normalerweise ist das eine Einweg-Kamera!
[1] Auf dem Planeten gibt es Fotos wie auf der Welt Internet, wie viele es auch immer gibt!
[11] Dummerweise
[2] Das ist möglich, wenn man es so macht...
[13] Was? Wenn man nicht den Helium in den Luftballon hineinlegt, wird der Ballon nicht fliegen!
[5] Könnte man im Universum Filmpakete und Schaltkreise machen?
[8] Es war zu teuer, also...
[7] Ach
[3] Man sollte es auch selbst machen, wenn man es so will...
[6] Das ist so, wie es ist...

--- seed 9 ---
[10] Dieser ist definitiv dumm!
[9] Muuuuugh
[4] Normalerweise ist das eine Einweg-Kamera!
[1] Auf dem Planeten gibt es Fotos wie auf der Welt in der Internet-Allianz, wie viele sie auch fallen!
[11] Ach du Scheiße!
[2] Ja, das ist möglich, aber...
[13] Was?! Wenn ich Helium in den Windballon stecke, kann ich ihn nicht mehr hochkämpfen!
[5] In der Galaxie kann man Filmpakete und Schaltkreise machen!
[8] Ach so, das war billig gewesen!
[7] Ach
[3] Ich hätte es auch selbst machen können!
[6] Das ist ja so, das ist ja so...

```

**inline-shipped** · seed 7: 3199 ms, 300 prompt / 227 reply tokens · seed 8: 3268 ms, 300 prompt / 244 reply tokens · seed 9: 3330 ms, 300 prompt / 249 reply tokens

```text
--- seed 7 ---
[10] (rassend) Das ist definitiv ein Idiot!
[9] (rassend) Muu-gg!
[4] (rassend) Das ist normal, er ist ein Dauerkamera!
[1] (freundlich) Auf dem Planeten Erde gibt es Fotos auf der ganzen Welt auf der Internet!
[11] (rassend) Ach du Scheiße!
[2] (freundlich) Ja, das ist möglich, aber...
[13] (rassend) Was denn? Wenn du den Helium in den Windballon hineinlässt, wird er nicht fliegen!
[5] (rassend) In der Galaxis kann man Filmen machen und Schalldruck machen!
[8] (polite) Es tut mir leid, aber...
[7] (rassend) Ach du Scheiße!
[3] (freundlich) Warum musst du dich selbst fotografieren?
[6] (polite) Ja, das ist richtig...
--- seed 8 ---
[10] (rough) Ich denke, dass er ein Idiot ist.
[9] (rough) Muuuuuh!
[4] (rough) Er ist bestimmt ein Dummchen!
[1] (casual) Auf dem Planeten Erde gibt es Fotos, die man überall auf der Welt finden kann.
[11] (rough) Ich denke, das ist ein Mist!
[2] (casual) Ich denke, das ist möglich, aber nicht wahrscheinlich.
[13] (rough) Was soll ich damit sagen? Wenn ich einen Heliumgasballon in den Wind schicke, wird er nicht fliegen!
[5] (rough) In der Galaxie ist es möglich, Filmpakete zu haben und die Schiebetür zu drücken!
[8] (polite) Ich habe es mir gedacht, es war ein bisschen teuer.
[7] (rough) Ach du lieber Gott!
[3] (casual) Ich habe mich nicht gedacht, dass ich selbst etwas machen würde...
[6] (polite) Ja, das ist richtig...
--- seed 9 ---
[10] (rassend) Dieser ist definitiv dumm!
[9] (rassend) Mu-gg!
[4] (rassend) Normalerweise ist das ein Abfallkamera!
[1] (freundlich) Auf dem Planeten gibt es so viele Fotos, dass sie auf der Weltall auf der ganzen Welt auf der ganzen Welt auf der ganzen Welt auf der ganzen Welt!
[11] (rassend) Ach du Scheiße!
[2] (freundlich) Ja, das ist möglich, aber...
[13] (rassend) Was ist das für ein Unsinn! Wenn ich einen Helium in den Windballen einstecken würde, würde er nicht fliegen!
[5] (rassend) In der Galaxis kann man Filmpakete und Schaltkreise machen!
[8] (polite) Ach, das war ein bisschen teuer, aber...
[7] (freundlich) Ach
[3] (freundlich) Warum musst du dich selbst fotografieren?
[6] (polite) Ja, das ist richtig...
```

**inline-np** · seed 7: 3068 ms, 300 prompt / 221 reply tokens · seed 8: 2940 ms, 300 prompt / 206 reply tokens · seed 9: 4149 ms, 300 prompt / 335 reply tokens

```text
--- seed 7 ---
[10] "Er ist definitiv dumm!"
[9] "Müüüüüü!"
[4] "Das ist wahrscheinlich, dass er ein Dummkopf ist!"
[1] "Es gibt Fotos von der Erde auf der Internet, wie oft man sie auch auf der ganzen Welt verstecken kann."
[11] "Verdammte Scheiße!"
[2] "Natürlich ist das möglich, aber... "
[13] "Was ist das für ein Unsinn?! Wenn man einen Heliumgasbalg in den Windballen einsetzt, wird der Balg nicht mehr fliegen können!"
[5] "Haben Sie im Universum eine Kamera mit Filmpaketen und Schaltkreisen?!"
[8] "Ich habe es mir nicht leisten können, also... "
[7] "Aaahh... "
[3] "Warum sollte ich das tun, wenn ich es nicht tun muss... "
[6] "Ja, das ist richtig... "
--- seed 8 ---
[10] "Er ist definitiv dumm!"
[9] "Müüüüüü!"
[4] "Ich denke, er ist ein Dummchen!"
[1] "Die Fotos von der Erde können manchmal auf der Internetseite heruntergeladen werden, wenn man will."
[11] "Dummer!"
[2] "Natürlich ist das möglich, aber... "
[13] "Was? Das Helium muss in der Luft sein, damit der Ball auf dem Wasser fliegt!"
[5] "Würde ich in der Galaxie mit Filmen und Schalterschlägen umspringen, könnte das sein?"
[8] "Ich habe es mir etwas über den Tisch gelegt, weil... "
[7] "Aaahh!"
[3] "Warum musstest du dich selbst fotografieren, wenn du nicht so dumm bist?"
[6] "Ja, das ist richtig..."
--- seed 9 ---
[10] "Er ist definitiv dumm!"
[9] "Müüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüüü
[4] "Ich denke, er ist wahrscheinlich ein Dummchen!"
[1] "Es gibt Fotos von der Erde auf der Internet, wie viele man auch immer auf die Server schicken kann."
[11] "Was?!"
[2] "Es ist möglich, aber nicht wahrscheinlich..."
[13] "Was?!"
[5] "Würde man in der Galaxie Filme machen und Schalldruck machen, um die Kamera zu fotografieren?"
[8] "Ich habe es für so ein kleines Geld gekauft..."
[7] "Ahh... "
[3] "Warum sollte ich das tun, wenn ich es nicht will?"
[6] "Ja, das ist richtig..."
```

**sys-ctl** · seed 7: 2183 ms, 210 prompt / 161 reply tokens · seed 8: 2250 ms, 210 prompt / 168 reply tokens · seed 9: 2319 ms, 210 prompt / 175 reply tokens

```text
--- seed 7 ---
[10] Dieser ist definitiv dumm!
[9] Muuuuuggg!
[4] Das ist, was man mit einem Single-Use-Kamera macht!
[1] Auf dem Planeten ist alles auf der Welt Internet verfügbar!
[11] Äh...
[2] Ja, das ist möglich, aber...
[13] Was? Wenn ich Helium in den Windballon stecke, kann ich ihn nicht mehr hochkurbeln!
[5] In der Galaxie kann man Filme aufnehmen und Schalldächer werfen!
[8] Ich habe das für günstig gehalten...
[7] Ach du...
[3] Ich habe es aus dem Interesse getan!
[6] Das ist ja so...

--- seed 8 ---
[10] Dieser ist definitiv dumm!
[9] Mu-guu
[4] Normalerweise ist das eine Ausweiskamera!
[1] Auf dem Planeten gibt es Fotos, die man überall auf der Weltseele herunterladen kann!
[11] Dummerweise
[2] Ja, das ist möglich, aber...
[13] Was? Wenn ich Helium in den Windballon einstecke, kann ich ihn nicht mehr fliegen!
[5] In der Galaxis kann man Filmpakete mit Schaltkreisen schalten!
[8] Ich habe es nur für den Preis gekauft...
[7] Ach
[3] Ich habe das aus eigener Kraft nicht geschafft...
[6] Das ist ja so...
--- seed 9 ---
[10] Dieser ist definitiv dumm!
[9] Muuuuuggg!
[4] Das ist, was man mit einem Schiebefoto-Kamera macht!
[1] Auf den Planeten der Bilder ist alles auf der Welt Internet zu finden!
[11] Dummerie!
[2] Ja, das ist möglich, aber...
[13] Was? Wenn man nicht einen Helium-Kopf reinlegt, kann man den Ball nicht auf den Wind fliegen!
[5] In der Galaxis kann man Filmpakete und Schaltkreise machen!
[8] Also, das war ein bisschen teuer...
[7] Aaah...
[3] Ich hätte es mir ja gar nicht aus den Augen gelassen...
[6] Das ist, wie du sagst...
```

**sys** · seed 7: 2480 ms, 286 prompt / 166 reply tokens · seed 8: 2521 ms, 286 prompt / 169 reply tokens · seed 9: 2452 ms, 286 prompt / 160 reply tokens

```text
--- seed 7 ---
[10] Dieser ist definitiv dumm!
[9] Muu-gg!
[4] Das ist, was ich mit meinem Dampfblicker gemacht habe!
[1] Auf der ganzen Welt ist es so, dass manche Bilder auf der Internetseite einfach fallen.
[11] Dreck!
[2] Ja, das ist möglich, aber...
[13] Was? Das Luftballon würde nicht fliegen, wenn ich ihn nicht reinstecke!
[5] In der Galaxis kann man Filmpakete und Schaltkreise machen!
[8] Ich habe es für günstig gehalten...
[7] Ach
[3] Ich hätte es nicht tun sollen, dass ich selbst gemacht habe...
[6] Ja, das ist richtig...
--- seed 8 ---
[10] Dieser ist definitiv dumm!
[9] Muu-gg!
[4] Normalerweise ist das eine Dauerkamera!
[1] Auf dem Planeten gibt es Fotos, die man überall auf der Welt finden kann.
[11] Äh...
[2] Ja, theoretisch könnte das funktionieren, aber...
[13] Was? Wenn ich Helium in den Windballon stecke, kann ich ihn nicht mehr hochziehen!
[5] In der Galaxie kann man Filmpakete mit Schaltkreisen schalten!
[8] Ich dachte, ich hätte es für günstig gehalten...
[7] Ach
[3] Ich habe es aus eigener Faust gemacht...
[6] Ja, das ist richtig...
--- seed 9 ---
[10] Dieser ist definitiv dumm!
[9] Muu-gg!
[4] Das ist der Grund, warum ich ein Flugzeugkamera habe!
[1] Auf dem ganzen Planeten fallen immerhin Fotos auf der Internetseite.
[11] Äh?
[2] Das ist ja wohl möglich, aber...
[13] Was? Das Luftballon kann nicht fliegen, wenn es keine Helium enthält!
[5] In der Galaxis kann man Filmpakete und Schaltkreise machen?
[8] Es war nur ein kleiner Fehler, dass...
[7] Ach
[3] Warum musstest du dich selbst fotografieren?
[6] Das ist ja wohl so...
```

**sys-qwen** · seed 7: 2530 ms, 296 prompt / 166 reply tokens · seed 8: 2767 ms, 296 prompt / 193 reply tokens · seed 9: 2449 ms, 296 prompt / 159 reply tokens

```text
--- seed 7 ---
[10] Dieser ist definitiv dumm!
[9] Muu-guu!
[4] Normalerweise ist das eine Kondensatorkamera!
[1] Auf der Welt gibt es Fotos, die man über die Internetseite jederzeit herunterladen kann.
[11] Äh, äh...
[2] Ja, das ist möglich, aber...
[13] Was? Wenn man Helium in den Luftballon einpumpt, kann man ihn nicht mehr fliegen!
[5] In der Galaxis kann man Filmpakete mit Schaltkreisen schalten!
[8] Ach, das war zu teuer!
[7] Ach
[3] Man kann das nicht ohne eigene Aufnahme!
[6] Das ist ja so...
--- seed 8 ---
[10] Dieser ist definitiv dumm!
[9] Muu-guu!
[4] Das ist, wie ich sehe, ein Drittkopf-Kamera!
[1] Auf dem Planeten gibt es Fotos, die überall auf der Welt auf der Internet freigegeben sind!
[11] Äh!
[2] Das ist, wenn man es so meint, möglich!
[13] Was? Ist das nicht überraschend! Wenn man Helium in den Windfederen einsetzt, kann man die Sonde nicht mehr fliegen!
[5] In der Galaxis ist es möglich, Filmpakete zu haben und die Kamera zu drücken!
[8] Ach, das war zu teuer!
[7] Ach!
[3] Aber ich habe es selbst gemacht!
[6] Das ist, wenn man es so meint, ja!
--- seed 9 ---
[10] Dieser ist definitiv dumm!
[9] Muu-guuu!
[4] Das ist, was man mit einem Drittkamera auf der Welt macht!
[1] Die Bilder von der Erde fallen überall auf der Welt auf der Internetseite!
[11] Äh...
[2] Ja, das ist möglich, aber...
[13] Was? Das Helium muss in der Luft sein, damit der Ball fliegt!
[5] Auf dem Mond kann man Filmpakete und Schaltkreise machen!
[8] Also, das war ein bisschen teuer...
[7] Ach du!
[3] Ich habe das selbst gemacht, aber...
[6] Ja, das ist so...
```


### balloon-dense-dialogue, real emulator OCR, gated, Brazilian Portuguese

**none** · seed 7: 2161 ms, 221 prompt / 152 reply tokens · seed 8: 2243 ms, 221 prompt / 160 reply tokens · seed 9: 2208 ms, 221 prompt / 160 reply tokens

```text
--- seed 7 ---
[10] Ele definitivamente é bobo
[9] Mu-gu
[4] Basicamente, ele é um flash de fotos
[1] As fotos do mundo estão espalhadas na internet
[11] Tá chato
[2] Claro que é possível, mas...
[13] O que? Se não colocar hidrogênio, o balão não vai ficar flutuando
[5] E se eu fizesse isso na lua? E se eu fizesse isso na lua?
[8] Ah, era barato
[7] Ah
[3] Afinal, ele mesmo não queria fazer isso
[6] É, é

--- seed 8 ---
[10] Ele absolutamente é bobo
[9] Mu-gu
[4] Basicamente, ele é uma câmera de uso único
[1] As fotos do mundo estão espalhadas na internet, não é?
[11] Tá
[2] Claro, teoricamente, é possível, mas...
[13] O que? Se não colocar hidrogênio, o balão não vai flutuar!
[5] E se você quiser tirar fotos de filmes em espaço?!
[8] Ah, era barato...
[7] Ah
[3] Se você tiver que tirar fotos, mesmo que seja para você, não vai fazer
[6] Sim, é verdade...

--- seed 9 ---
[10] Ele definitivamente é bobo
[9] Mu-gu
[4] Acho que é porque é uma câmera de uso único
[1] Não vejo por que isso pode ser escrito na internet
[11] Que pena
[2] Claro, mas é possível, mas...
[13] O que? Se não colocar hidrogênio, o balão não vai ficar flutuando
[5] E se eu fizesse isso no espaço? E se eu tirasse uma foto com filme?
[8] Foi caro, mas...
[7] Ah
[3] E se eu tivesse que fazer isso sozinho...
[6] Isso é verdade...
```

**inline-shipped** · seed 7: 2823 ms, 301 prompt / 187 reply tokens · seed 8: 2789 ms, 301 prompt / 183 reply tokens · seed 9: 2826 ms, 301 prompt / 186 reply tokens

```text
--- seed 7 ---
[10] (rough) Ele definitivamente é bobo
[9] Ói
[4] (rough) Basicamente, ele é um flash de luz
[1] (casual) As fotos do mundo todo estão caindo na internet, não é?
[11] (rough) Que diabos
[2] (casual) Claro, teoricamente, é possível, mas...
[13] (rough) O que você está dizendo? Não há hélio, não há balão que flote!
[5] (rough) E se ele quiser tirar fotos no espaço?!
[8] (polite) Foi um erro, mas...
[7] Ah
[3] (casual) Se você não quer que eu tome a foto, não vou fazer
[6] (polite) Sim, é verdade...
--- seed 8 ---
[10] (rough) Ele absolutamente é bobo
[9] Ói
[4] (rough) Acho que ele é um filme usado
[1] (casual) As fotos do mundo estão espalhadas por todo o site
[11] (rough) Que diabos
[2] (casual) Claro, mas...
[13] (rough) O que você está brincando? Se não colocar hidrogênio na balança, o balão não vai flutuar
[5] (rough) Você queria tirar fotos no espaço?!
[8] (polite) Foi um erro, mas...
[7] Ah
[3] (casual) Se você tivesse feito isso, teria...
[6] (polite) Sim, é verdade...
--- seed 9 ---
[10] (rough) Ele definitivamente é bobo
[9] Ói
[4] (rough) Basicamente, ele é um flash de luz
[1] (casual) As fotos do mundo todo estão caindo na internet, não é?
[11] (rough) Que diabos
[2] (casual) Claro, teoricamente é possível, mas...
[13] (rough) O que? Se não colocar hidrogênio, o balão não vai flutuar!
[5] (rough) Você queria tirar fotos na lua?!
[8] (polite) Desculpe, mas...
[7] Ah
[3] (casual) Se você não tivesse que fazer isso, por que você fez?
[6] (polite) Sim, é verdade...
```

**inline-np** · seed 7: 2865 ms, 301 prompt / 190 reply tokens · seed 8: 2711 ms, 301 prompt / 173 reply tokens · seed 9: 2797 ms, 301 prompt / 186 reply tokens

```text
--- seed 7 ---
[10] "Não está absolutamente errado ser bobo, idiota!"
[9] "Muito!"
[4] "Na verdade, ele usou uma câmera de substituição!"
[1] "As fotos do planeta estão espalhadas por todo o mundo na internet, não é?"
[11] "Pffft!"
[2] "Claro, na teoria, isso é possível, mas..."
[13] "O que você está dizendo? Se não colocar hidrogênio na balança, o balão não vai flutuar!"
[5] "Será que ele realmente queria tirar fotos em espaço?!"
[8] "Desculpe, mas... foi um erro!"
[7] "Ah, não!"
[3] "Por que você mesmo não tirou uma foto?"
[6] "Ah, é verdade... "

--- seed 8 ---
[10] Querido, ele definitivamente é um bobo.
[9] Muuuu
[4] Quase, ele é uma câmera de uso único!
[1] Eu não acredito que isso seja possível na internet.
[11] Querido, que diabos!
[2] Eu sei, mas na verdade, é possível.
[13] Quem é que está se gabando? Se não colocar hidrogênio, o balão não vai flutuar!
[5] Você acredita que o filme pode ser tirado em espaço?
[8] Desculpe, mas eu estava com um prejuízo.
[7] Ah
[3] Quem teve a ideia de fazer isso?
[6] Sim, isso é verdade...
--- seed 9 ---
[10] "Não está absolutamente errado ser bobo, idiota."
[9] "Muito"
[4] "Normalmente, ele usava câmeras de substituição."
[1] "As fotos do mundo estão espalhadas por todo o mundo na internet, não é?"
[11] "Pfffft"
[2] "Claro, teoricamente, é possível, mas..."
[13] "O que você está reclamando?! Se não colocar hidrogênio na balão, ele não vai flutuar!"
[5] "Em espaço, você pode tirar fotos com filme, não é?"
[8] "Desculpe, mas..."
[7] "Ah"
[3] "Se você não tiver que fazer isso, por que você está fazendo isso?"
[6] "Sim, é verdade..."
```

**sys-ctl** · seed 7: 2181 ms, 211 prompt / 156 reply tokens · seed 8: 2149 ms, 211 prompt / 153 reply tokens · seed 9: 2193 ms, 211 prompt / 158 reply tokens

```text
--- seed 7 ---
[10] Ele definitivamente é bobo
[9] Muuuu
[4] Basicamente, ele é um réveillon de papelão!
[1] As fotos do planeta estão caindo na internet, não é?
[11] Tá chato
[2] Sim, teoricamente, é possível, mas...
[13] O que? Se não colocar hidrogênio, o balão não vai flutuar!
[5] E se vocês quiserem filmar na lua, é possível?
[8] Ah, foi caro...
[7] Ah
[3] Vá, você mesmo não queria fazer isso...
[6] Isso, eu entendo...
--- seed 8 ---
[10] Ele absolutamente é bobo
[9] Mu-guu
[4] A razão por que eu uso isso é porque é um réveillon
[1] As fotos do planeta estão por todo o lado na internet
[11] Tá chato
[2] Claro que é possível, mas...
[13] O que é que você está brincando!? Se não colocar hidrogênio na bala, a balança não vai ficar no ar!
[5] Você queria filmar o filme na lua?!
[8] Foi caro, mas...
[7] Ah
[3] Eu mesmo fiz isso...
[6] Isso mesmo...

--- seed 9 ---
[10] Ele definitivamente é bobo
[9] Muuuu
[4] A razão por que eu uso isso é porque é um réveillon
[1] As fotos do planeta estão por todo o lado na internet
[11] Tchau
[2] Claro, mas na verdade isso é possível...
[13] O que? Se eu não colocar hidrogênio na bala, ela não vai flutuar
[5] E se eu tivesse que tirar uma foto do espaço com filme?!
[8] Tinha que ser barato
[7] Ah
[3] Eu me lembrei de fazer isso mesmo
[6] Isso, eu sei

```

**sys** · seed 7: 2614 ms, 287 prompt / 174 reply tokens · seed 8: 2542 ms, 287 prompt / 166 reply tokens · seed 9: 2506 ms, 287 prompt / 164 reply tokens

```text
--- seed 7 ---
[10] Ele definitivamente é bobo
[9] Mu-gu
[4] Basicamente, ele é um fotógrafo de uso único!
[1] As fotos do mundo estão caindo na internet, não é?
[11] Tá zonzo
[2] Claro, teoricamente, é possível, mas...
[13] Quêz, o que é que? Se não colocar hidrogênio, o balão não vai flutuar!
[5] Aí, por que você quer tirar fotos de filme no espaço?!
[8] Ah, foi caro, então...
[7] Ah
[3] E se eu tivesse que tirar uma foto, eu não faria isso!
[6] Isso, eu entendo...
--- seed 8 ---
[10] Ele definitivamente é bobo
[9] Mu-gu
[4] Basicamente, ele é um flash de fotos
[1] As fotos do mundo estão caindo na internet, não é?
[11] Que diabos
[2] Claro, teoricamente, é possível, mas...
[13] O que, você está se perguntando?! Se não colocar hidrogênio na balança, o balão não vai ficar no ar!
[5] E se você quiser tirar fotos de filmes no espaço?!
[8] Ah, foi um preço baixo...
[7] Ah
[3] Se você não tiver que fazer isso por conta própria...
[6] Isso, eu entendo...
--- seed 9 ---
[10] Ele definitivamente é bobo
[9] Mu-gu
[4] Basicamente, ele é um fotógrafo de uso único!
[1] As fotos do mundo estão caindo na internet como se fosse um problema
[11] Tchau
[2] Claro, teoricamente, isso é possível...
[13] Quê? O que você está fazendo? Se não colocar hidrogênio, o balão não vai flutuar!
[5] Você tem certeza de que quer filmar em espaço?!
[8] Ah, foi um preço baixo
[7] Ah
[3] Eles não precisam de ninguém para fazer isso...
[6] Sim, é verdade...
```

**sys-qwen** · seed 7: 2522 ms, 297 prompt / 162 reply tokens · seed 8: 2434 ms, 297 prompt / 153 reply tokens · seed 9: 2694 ms, 297 prompt / 179 reply tokens

```text
--- seed 7 ---
[10] Essa pessoa é absolutamente bobo
[9] Muito
[4] Basicamente, ela é uma câmera de uso único
[1] Não há como não dizer que isso é uma foto do planeta, não?
[11] Que diabos
[2] Claro, mas na verdade, isso é possível
[13] Que diabos! Não há como não dizer que o balão não pode flutuar sem hidrogênio
[5] Que diabo, você queria filmar na lua?
[8] Ah, eu estava com medo de pagar muito
[7] Ah
[3] Eu mesmo, eu mesmo, eu mesmo...
[6] Sim, é verdade

--- seed 8 ---
[10] Essa pessoa é absolutamente bobo
[9] Muito
[4] Basicamente, ela é uma câmera de uso único
[1] Não há como não dizer que essas fotos do mundo estão espalhadas na internet
[11] Que pena
[2] Sim, tecnicamente, isso é possível, mas...
[13] O que você está se referindo?!
[5] E você, com o filme, vai tirar fotos no espaço?!
[8] Eu pensei que fosse mais barato...
[7] Ah
[3] E você, por que você não faz isso por conta própria...
[6] Sim, é verdade...
--- seed 9 ---
[10] Essa pessoa é absolutamente bobo
[9] Muito
[4] Basicamente, ela é uma câmera de uso único
[1] Não há como não dizer que está na internet que você pode encontrar qualquer foto do mundo
[11] Pff
[2] Claro, na verdade, isso é possível, mas...
[13] O que, então, você está se queixando? Não há como não ter um balão flutuante se não colocar hidrogênio
[5] E você, na Terra, está tentando tirar fotos com filme?!
[8] Ah, foi caro, então...
[7] Ah
[3] E, mesmo que eu não fosse eu, eu também faria isso...
[6] Isso, na verdade...
```


### bourei-p04, real emulator OCR, gated, French

**none** · seed 7: 853 ms, 107 prompt / 53 reply tokens · seed 8: 888 ms, 107 prompt / 57 reply tokens · seed 9: 824 ms, 107 prompt / 50 reply tokens

```text
--- seed 7 ---
[3] Je suis morte.
[1] "Les gens que l'on voit" sont des types.
[2] L'apothéose de ma sœur est-elle visible?
[7] Eh?
[4] Oui...

--- seed 8 ---
[3] Je suis morte, hein ?
[1] "Les gens que l'on peut voir" sont des types
[2] Pourquoi tu veux savoir ce que fait ta sœur décédée ?
[7] Eh ?
[4] Oui...

--- seed 9 ---
[3] Je suis morte.
[1] "Les gens que l'on voit" sont des types.
[2] L'habillage de ma sœur décédée?
[7] Eh?
[4] Oui...

```

**inline-shipped** · seed 7: 1189 ms, 168 prompt / 69 reply tokens · seed 8: 1194 ms, 168 prompt / 71 reply tokens · seed 9: 1168 ms, 168 prompt / 67 reply tokens

```text
--- seed 7 ---
[3] (polite) "Il est mort."
[1] (polite) "Il y a des gens qui sont visibles."
[2] (polite) "La maquillage de la tante qui est décédée?"
[7] え?
[4] (polite) "Oui..."

--- seed 8 ---
[3] (polite) Je suis en train de mourir
[1] (polite) "Les gens que l'on peut voir" sont des gens
[2] (polite) Les maquillages de la sœur décédée ?
[7] え?
[4] (polite) Oui...

--- seed 9 ---
[3] (polite) "Il est mort"
[1] (polite) "Il y a des gens que vous pouvez voir"
[2] (polite) "La maquillage de la sœur de mon amie"
[7] ？
[4] (polite) "Oui..."
```

**inline-np** · seed 7: 1085 ms, 168 prompt / 57 reply tokens · seed 8: 1096 ms, 168 prompt / 57 reply tokens · seed 9: 1068 ms, 168 prompt / 55 reply tokens

```text
--- seed 7 ---
[3] Je suis mort.
[1] "Les gens que l'on peut voir, c'est ça, hein ?"
[2] "La maquillage de ta tante qui est décédée ?"
[7] Eh ?
[4] Oui...
--- seed 8: identical to seed 7 ---
--- seed 9 ---
[3] Je suis mort.
[1] "Les gens que l'on peut voir, c'est eux, hein?"
[2] "La maquillage de la tante qui est décédée?"
[7] Eh?
[4] Oui...
```

**sys-ctl** · seed 7: 787 ms, 97 prompt / 50 reply tokens · seed 8: 819 ms, 97 prompt / 55 reply tokens · seed 9: 842 ms, 97 prompt / 57 reply tokens

```text
--- seed 7 ---
[3] Il est mort.
[1] "Les gens que l'on peut voir, c'est eux."
[2] "La maquillage de ta sœur décédée?"
[7] Eh?
[4] Oui...

--- seed 8 ---
[3] Il est mort.
[1] "Les gens que l'on peut voir, c'est eux, hein ?"
[2] "La maquillage de ta sœur décédée ?"
[7] Eh ?
[4] Oui...
--- seed 9 ---
[3] Il est mort.
[1] "Les gens que l'on peut voir" sont des types.
[2] Pourquoi tu demandes à l'âge d'une tante qui est décédée ?
[7] Eh ?
[4] Oui...

```

**sys** · seed 7: 1012 ms, 148 prompt / 58 reply tokens · seed 8: 1019 ms, 148 prompt / 57 reply tokens · seed 9: 959 ms, 148 prompt / 51 reply tokens

```text
--- seed 7 ---
[3] Je suis morte.
[1] "Les gens que l'on peut voir sont des gens comme ça."
[2] Pourquoi tu as fait ta peau pour ta tante qui est décédée?
[7] Euh...
[4] Oui...
--- seed 8 ---
[3] Je suis morte, hein
[1] "Les gens que l'on peut voir, c'est ça, hein"
[2] "La maquillage de ta sœur décédée?"
[7] Eh?
[4] Oui...
--- seed 9 ---
[3] Je suis morte.
[1] "Les gens que l'on voit sont des êtres."
[2] "La maquillage de la sœur décédée?"
[7] Eh?
[4] Oui...
```

**sys-qwen** · seed 7: 1032 ms, 158 prompt / 56 reply tokens · seed 8: 1024 ms, 158 prompt / 55 reply tokens · seed 9: 1040 ms, 158 prompt / 56 reply tokens

```text
--- seed 7 ---
[3] Je suis morte, hein?
[1] "Les gens que je peux voir, c'est des types."
[2] "La maquillage de la tante qui est décédée?"
[7] Eh?
[4] Oui...
--- seed 8 ---
[3] Je suis en train de mourir.
[1] "Les gens que l'on peut voir, c'est ça."
[2] "La maquillage de la sœur qui est décédée?"
[7] ?
[4] Oui...
--- seed 9: identical to seed 7 ---
```


### bourei-p04, real emulator OCR, gated, Spanish

**none** · seed 7: 1814 ms, 107 prompt / 160 reply tokens · seed 8: 795 ms, 107 prompt / 48 reply tokens · seed 9: 901 ms, 107 prompt / 60 reply tokens

```text
--- seed 7 ---
[3] Estás muerto, ¿verdad?
[1] "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa
[2] "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa? ", "¿Qué pasa
[7] ¿Qué?
[4] Sí...
--- seed 8 ---
[3] Estás muerto, verdad?
[1] "¿Es un humano que se puede ver? "
[2] ¿Qué hizo tu hermana muerta?
[7] ¿Qué?
[4] Sí...
--- seed 9 ---
[3] Estoy muerto, ¿verdad?
[1] "¿Puedo ver a los muertos?"
[2] ¿Dónde está la maquillaje de la tía que murió?
[7] ¿Qué?
[4] Sí...
```

**inline-shipped** · seed 7: 1252 ms, 168 prompt / 76 reply tokens · seed 8: 1161 ms, 168 prompt / 66 reply tokens · seed 9: 1280 ms, 168 prompt / 80 reply tokens

```text
--- seed 7 ---
[3] (polite) Estoy muerto, ¿no?
[1] (polite) "Personas que se pueden ver" son esos tipos.
[2] (polite) ¿Qué te refieres a la maquiagem de la tía que murió?
[7] ¿Qué?
[4] (polite) Sí...

--- seed 8 ---
[3] (polite) "Estoy muerto, verdad?"
[1] (polite) "¿Es un humano visible?"
[2] (polite) "¿Qué hizo tu tía muerta?"
[7] (rough) ¿Qué?
[4] (polite) "Sí..."
--- seed 9 ---
[3] (polite) Estoy muerto, verdad?
[1] (polite) "Personas que se pueden ver" son esos tipos de personas.
[2] (polite) ¿Qué quieres hacer con la maquillaje de la tía que murió?
[7] (rough) ¿Qué?
[4] (polite) Sí...

```

**inline-np** · seed 7: 1095 ms, 168 prompt / 57 reply tokens · seed 8: 1082 ms, 168 prompt / 56 reply tokens · seed 9: 1096 ms, 168 prompt / 58 reply tokens

```text
--- seed 7 ---
[3] ¿Estás muerto, verdad?
[1] "¿Ves a alguien?" ¿Qué tipo de persona es?
[2] ¿La maquiagem de la tía que murió?
[7] ¿Qué?
[4] Sí...
--- seed 8 ---
[3] ¿Estás muerto, verdad?
[1] "¿Ves a alguien?" ¿Quién es?
[2] ¿Qué quieres hacer con la maquillaje de mi hermana?
[7] ¿Qué?
[4] Sí...
--- seed 9 ---
[3] ¿Estás muerto, verdad?
[1] "¿Qué pasa con los humanos que se pueden ver?"
[2] ¿Qué hiciste con la maquiagem de mi hermana muerta?
[7] ¿Qué?
[4] Sí...

```

**sys-ctl** · seed 7: 769 ms, 97 prompt / 49 reply tokens · seed 8: 755 ms, 97 prompt / 47 reply tokens · seed 9: 838 ms, 97 prompt / 57 reply tokens

```text
--- seed 7 ---
[3] Está muerto, ¿verdad?
[1] "Los que se ven" son esos tipos.
[2] ¿Qué hizo tu hermana muerta?
[7] ¿Eh?
[4] Sí...

--- seed 8 ---
[3] Está muerto, ¿no?
[1] "Los que se ven" son esos tipos.
[2] ¿Qué hizo la hermana muerta?
[7] ¿Qué?
[4] Sí...

--- seed 9 ---
[3] Está muerto, ¿verdad?
[1] "¿Ves a los mortales?"  
[2] ¿Qué hiciste con la maquillaje de tu hermana muerta?
[7] ¡Eh!
[4] Sí...
```

**sys** · seed 7: 1003 ms, 148 prompt / 57 reply tokens · seed 8: 1037 ms, 148 prompt / 61 reply tokens · seed 9: 998 ms, 148 prompt / 57 reply tokens

```text
--- seed 7 ---
[3] Estoy muerto, ¿verdad?
[1] "¿Ves a los mortales?"
[2] ¿Qué quieres decir con que la abuela mía haya hecho maquillaje?
[7] ¿Qué?
[4] Sí...
--- seed 8 ---
[3] Estoy muerto, ¿verdad?
[1] "Pero los humanos que se pueden ver son los que son"
[2] ¿Qué te refieres con la maquillaje de la tía?
[7] ¿Qué?
[4] Sí...
--- seed 9 ---
[3] Estoy muerto, ¿verdad?
[1] "¿Ves a alguien?"  
[2] ¿Qué quieres decir con "la maquillaje de la tía muerta"?  
[7] ¿Qué?
[4] Sí...
```

**sys-qwen** · seed 7: 1075 ms, 158 prompt / 61 reply tokens · seed 8: 1058 ms, 158 prompt / 60 reply tokens · seed 9: 1073 ms, 158 prompt / 60 reply tokens

```text
--- seed 7 ---
[3] Estoy muerto, ¿no?
[1] "¿Vesible" personas son esos, ¿verdad?
[2] ¿Qué te parece la maquillaje de la tía que murió?
[7] ¿Qué?
[4] Sí...
--- seed 8 ---
[3] Estoy muerto, ¿verdad?
[1] "Personas que se pueden ver, ¿saben?"
[2] ¿Qué te parece la maquillaje de la tía que murió?
[7] ¿Qué?
[4] Sí...
--- seed 9 ---
[3] Estoy muerto, ¿verdad?
[1] "Personas que se pueden ver, ¿no?"
[2] ¿Dónde está la maquillaje de la tía que murió?
[7] ¿Qué?
[4] Sí...
```


### bourei-p04, real emulator OCR, gated, German

**none** · seed 7: 792 ms, 107 prompt / 48 reply tokens · seed 8: 734 ms, 107 prompt / 41 reply tokens · seed 9: 885 ms, 107 prompt / 58 reply tokens

```text
--- seed 7 ---
[3] Du bist tot.
[1] "Sichtbare" Menschen sind sie.
[2] Was hast du mit deinem ehemaligen Schwesterchen gemacht?
[7] Einen?
[4] Ja...
--- seed 8 ---
[3] Du bist tot.
[1] "Sichtbare" Menschen sind das.
[2] Wie hast du deine Schwester gekannt?
[7] Was?
[4] Ja...
--- seed 9 ---
[3] Du bist tot.
[1] "Denkbarer ist der Mensch, der sie sieht."
[2] Wie hast du die Make-up von deiner Tante, die gestorben ist, gemacht?
[7] Einen?
[4] Ja...
```

**inline-shipped** · seed 7: 1305 ms, 168 prompt / 84 reply tokens · seed 8: 1180 ms, 168 prompt / 69 reply tokens · seed 9: 1188 ms, 168 prompt / 69 reply tokens

```text
--- seed 7 ---
[3] (polite) "Er ist tot, " sagte er.
[1] (polite) "Es gibt Menschen, die sehen können, " erklärte er.
[2] (polite) "Wie heißt die Tochter der Toten? " fragte er.
[7] (rough) "Was? "
[4] (polite) "Ja... " sagte er.
--- seed 8 ---
[3] (polite) "Er ist tot, mein Freund."
[1] (polite) "Es gibt Menschen, die sehen können."
[2] (polite) "Wie soll ich die Totenmutter machen?"
[7] (rough) "Äh?"
[4] (polite) "Ja..."
--- seed 9 ---
[3] (polite) "Er ist tot, ja"
[1] (polite) "Es gibt Menschen, die sehen können"
[2] (polite) "Wie soll ich die Tochter meines Tanten aussehen lassen?"
[7] (rough) ?
[4] (polite) "Ja..."
```

**inline-np** · seed 7: 916 ms, 168 prompt / 39 reply tokens · seed 8: 1026 ms, 168 prompt / 51 reply tokens · seed 9: 967 ms, 168 prompt / 45 reply tokens

```text
--- seed 7 ---
[3] Ich bin tot.
[1] Sie sind Menschen, die man sehen kann.
[2] Hatte deine Mutter makeup?
[7] Was?
[4] Ja...
--- seed 8 ---
[3] "Gestorben ist, ja"
[1] "Es gibt Menschen, die Sie sind"
[2] "Wie soll ich die Totenmutter machen?"
[7] "Was? "
[4] "Ja..."
--- seed 9 ---
[3] "Es ist tot."
[1] "Es gibt Menschen, die Sie sehen können."
[2] "Wie geht es Ihrer Schwester?"
[7] "Was?"
[4] "Ja... "
```

**sys-ctl** · seed 7: 763 ms, 97 prompt / 49 reply tokens · seed 8: 741 ms, 97 prompt / 45 reply tokens · seed 9: 763 ms, 97 prompt / 49 reply tokens

```text
--- seed 7 ---
[3] Du bist tot.
[1] "Sichtbare" Menschen sind das.
[2] Was hast du mit deinem Schattenvermummel?
[7] Ach du lieber Gott!
[4] Ja...
--- seed 8 ---
[3] Du bist tot.
[1] "Sichtbare" Menschen sind das.
[2] Wie soll ich die Make-up von deiner Tante sehen?
[7] Was?
[4] Ja...
--- seed 9 ---
[3] Du bist tot.
[1] "Sehen Sie die Menschen?"
[2] Was für eine Ahnung hast du von der Ohmässung meiner Tante?
[7] Was?!
[4] Ja...
```

**sys** · seed 7: 927 ms, 148 prompt / 49 reply tokens · seed 8: 838 ms, 148 prompt / 40 reply tokens · seed 9: 841 ms, 148 prompt / 40 reply tokens

```text
--- seed 7 ---
[3] Du bist tot.
[1] "Sichtbare" Menschen sind sie.
[2] Was hast du mit deiner Tante, die gestorben ist, gemacht?
[7] Was?
[4] Ja...
--- seed 8 ---
[3] Du bist tot.
[1] "Sichtbare" Menschen sind sie.
[2] Wie heißt die Totenmutter?
[7] Ach?
[4] Ja...
--- seed 9 ---
[3] Du bist tot.
[1] "Sichtbare" Menschen sind sie.
[2] Wie heißt die Totenmutter?
[7] Was?
[4] Ja...
```

**sys-qwen** · seed 7: 885 ms, 158 prompt / 40 reply tokens · seed 8: 882 ms, 158 prompt / 40 reply tokens · seed 9: 940 ms, 158 prompt / 46 reply tokens

```text
--- seed 7 ---
[3] Du bist tot.
[1] "Sichtbare" Menschen sind das.
[2] Wie heißt die Toten-Tochter?
[7] Was?
[4] Ja...
--- seed 8 ---
[3] Du bist tot.
[1] "Sichtbare" Menschen sind das.
[2] Wie heißt die Totenmutter?
[7] Was?
[4] Ja...
--- seed 9 ---
[3] Du bist tot.
[1] "Sichtbare" Menschen sind sie.
[2] Hatte deine ältere Schwester ihre Make-up abgemacht?
[7] Ach?
[4] Ja...
```


### bourei-p04, real emulator OCR, gated, Brazilian Portuguese

**none** · seed 7: 874 ms, 108 prompt / 58 reply tokens · seed 8: 811 ms, 108 prompt / 50 reply tokens · seed 9: 863 ms, 108 prompt / 57 reply tokens

```text
--- seed 7 ---
[3] Estou morto, hein?
[1] "As pessoas que você pode ver são os tipos que você pode ver, hein?"
[2] "A maquiagem da minha irmã falecida?"
[7] ?
[4] Sim...
--- seed 8 ---
[3] Estou morto, né?
[1] "As pessoas que você pode ver são os tipos assim."
[2] A maquiagem da minha irmã falecida?
[7] ?
[4] Sim...

--- seed 9 ---
[3] Estou morto, hein?
[1] "As pessoas que você pode ver são os tipos que você pode ver, né?"
[2] A maquiagem da minha irmã falecida?
[7] E?
[4] Sim...
```

**inline-shipped** · seed 7: 1215 ms, 169 prompt / 72 reply tokens · seed 8: 1238 ms, 169 prompt / 76 reply tokens · seed 9: 1232 ms, 169 prompt / 75 reply tokens

```text
--- seed 7 ---
[3] (polite) Estou morto, hein?
[1] (polite) "Os que se podem ver são pessoas, hein?"
[2] (polite) "A maquiagem da minha tia falecida?"
[7] (rough) ?
[4] (polite) "Sim..."

--- seed 8 ---
[3] (polite) Estou morto, hein?
[1] (polite) "Os 'vistos' são pessoas, hein?"
[2] (polite) "A maquiagem da minha tia falecida?"
[7] (rough) ?
[4] (polite) "Sim, hein..."

--- seed 9 ---
[3] (polite) Estou morto, hein?
[1] (polite) "Pessoas que podem ser vistos" são essas pessoas?
[2] (polite) A maquiagem da minha tia falecida?
[7] (rough) E?
[4] (polite) Sim...

```

**inline-np** · seed 7: 1059 ms, 169 prompt / 54 reply tokens · seed 8: 1328 ms, 169 prompt / 84 reply tokens · seed 9: 1092 ms, 169 prompt / 59 reply tokens

```text
--- seed 7 ---
[3] "Está morto, hein?"
[1] "Você é um humano visível, certo?"
[2] "O maquiagem da sua tia falecida?"
[7] "Ah?"
[4] "Sim..."
--- seed 8 ---
[3] "Está morto, hein?"
[1] "O que você está dizendo? Você está se referindo a alguém que pode ser visto?"
[2] "O que você quer dizer com isso? Você está perguntando sobre a maquiagem da sua irmã que morreu?"
[7] "Ah, hein?"
[4] "Sim, eu... "

--- seed 9 ---
[3] "Está morto, hein?"
[1] "É humano que você está falando, certo?"
[2] "Aqui está a maquiagem da sua tia que morreu?"
[7] "Ah?"
[4] "Sim, eu...
```

**sys-ctl** · seed 7: 772 ms, 98 prompt / 49 reply tokens · seed 8: 800 ms, 98 prompt / 52 reply tokens · seed 9: 774 ms, 98 prompt / 49 reply tokens

```text
--- seed 7 ---
[3] Está morto, né.
[1] "Os vistos" são esses, né?
[2] A maquiagem da minha irmã falecida?
[7] ?
[4] Sim...
--- seed 8 ---
[3] Está morto, hein?
[1] "As pessoas que você pode ver são os tipos assim"
[2] A maquiagem da minha irmã falecida?
[7] Eee?
[4] Sim...

--- seed 9 ---
[3] Está morto, né.
[1] "As pessoas que você pode ver são os tipos assim."
[2] A maquiagem da minha irmã falecida?
[7] ?
[4] Sim...

```

**sys** · seed 7: 950 ms, 149 prompt / 51 reply tokens · seed 8: 946 ms, 149 prompt / 49 reply tokens · seed 9: 952 ms, 149 prompt / 51 reply tokens

```text
--- seed 7 ---
[3] Estou morto, hein?
[1] "Vê-los, " pessoas, hein?
[2] A maquiagem da sua tia que morreu?
[7] Ah?
[4] Sim...
--- seed 8 ---
[3] Estou morto, né.
[1] "Vê-se" é o tipo de pessoa.
[2] A maquiagem da minha irmã falecida?
[7] ?
[4] Sim...
--- seed 9 ---
[3] Estou morto, hein?
[1] "O que você vê é humano, né?"
[2] "A maquiagem da minha irmã falecida?"
[7] ?
[4] Sim...
```

**sys-qwen** · seed 7: 1046 ms, 159 prompt / 57 reply tokens · seed 8: 1003 ms, 159 prompt / 52 reply tokens · seed 9: 999 ms, 159 prompt / 52 reply tokens

```text
--- seed 7 ---
[3] Estou morto, hein?
[1] "Os humanos que podem ser vistos são vocês, hein?"
[2] "O maquiagem da minha irmã falecida?"
[7] ?
[4] Sim...
--- seed 8 ---
[3] Estou morto, vocês sabem.
[1] "Vocês podem ver" é uma pessoa.
[2] A maquiagem da minha irmã falecida?
[7] ?
[4] Sim...
--- seed 9 ---
[3] Estou morto, hein?
[1] "Os humanos que você pode ver são os tipos"
[2] "O maquiagem da minha irmã falecida?"
[7] ?
[4] Sim...
```


### tojime-dense-action, real emulator OCR, gated, French

**none** · seed 7: 2323 ms, 219 prompt / 170 reply tokens · seed 8: 2099 ms, 219 prompt / 146 reply tokens · seed 9: 2181 ms, 219 prompt / 156 reply tokens

```text
--- seed 7 ---
[5] Le mafioso qui se masturbe est probablement un imbécile
[1] Qu'as-tu fait de ta tête, qui a été tuer par un fusil à tire-laser ?
[7] Tu as peut-être...
[4] Arrête, on est dans un endroit où on ne devrait pas être !
[11] Ah, ah...
[8] Est-ce que c'est une fille ?
[3] Eh bien... c'est vrai...
[10] Ah...
[9] Montrer tes fesses
[6] As-tu fermé les yeux ?
[15] ?
[2] Ferme les yeux, même si tu es un imbécile, on peut toujours acheter un imbécile...
[12] Un peu... !
--- seed 8 ---
[5] Le mafioso à la bite est presque là
[1] Qu'est-ce que tu fais avec ta balle au coude pour voir si je suis dans le pétrin?
[7] Tu sais quoi?
[4] Arrête, c'est pas le moment!
[11] Ah, ah...
[8] C'est une fille?
[3] Eh bien... c'est vrai...
[10] Ah
[9] Montrer tes fesses
[6] Tu es un bon coup de couteau?
[15] Quoi?
[2] Ferme les yeux, mais tu as de la tête!
[12] Un peu...!
--- seed 9 ---
[5] Le mafioso qui se masturbe n'est pas un gamin
[1] Avec un tireur à portée de main, tu peux voir le cerveau qui a été frappé
[7] Pourquoi tu es là, toi aussi ?
[4] Arrête, c'est pas le moment
[11] Ah, ah...
[8] C'est une fille ?
[3] Hé, oui... mais...
[10] Ah
[9] Montrer tes fesses
[6] As-tu fermé les yeux pour être un bon client ?
[15] ?
[2] Avec un regard, même un idiot peut être acheté
[12] Un peu... !
```

**inline-shipped** · seed 7: 2730 ms, 297 prompt / 185 reply tokens · seed 8: 2767 ms, 297 prompt / 188 reply tokens · seed 9: 2771 ms, 297 prompt / 190 reply tokens

```text
--- seed 7 ---
[5] (rude) Je ne veux pas être touché par le mafia
[1] (rude) Tu as été touché par un fusil sur la cuisse? Tu veux voir?
[7] (rude) Tu es peut-être...
[4] (casual) Arrête ça! Tu ne peux pas être ici!
[11] Ah, ah...
[8] (rude) Est-ce que c'est une fille?
[3] (polite) Eh bien... c'est vrai...
[10] Ah
[9] (rude) Montrons-nous la face
[6] (rude) Tu es un gardien de la foi?
[15] ?
[2] (rude) Tu peux acheter même si tu as une intelligence faible...
[12] (casual) Un peu...!
--- seed 8 ---
[5] (rude) Je ne veux pas être touché par le mafia
[1] (rude) Tu as été touché par un fusil à tire-longs, tu veux voir son cerveau?
[7] (rude) Tu es aussi?
[4] (casual) Arrête, tu es dans un mauvais endroit!
[11] Ah, ah...
[8] (rude) C'est une fille?
[3] (polite) Ah, oui... mais...
[10] Ah
[9] (rude) Montrons-nous les dents
[6] (rude) Tu es aussi malin que les yeux fermés?
[15] ?
[2] (rude) Tu es aussi intelligent même si tu te couilles?
[12] (casual) Un peu...!
--- seed 9 ---
[5] (rude) Je ne veux pas être touché par le mafia
[1] (rude) Tu as été touché par un fusil à tire-longs, tu veux voir la tête de ton cerveau?
[7] (rude) Tu es aussi?
[4] (casual) Arrête ça! On ne peut pas être ici!
[11] Ah, ah...
[8] (rude) C'est une fille?
[3] (polite) Eh bien... c'est vrai, mais...
[10] Ah
[9] (rude) Montrons-nous les dents
[6] (rude) Tu es un sorcier caché?
[15] ?
[2] (rude) Tu as un esprit, alors tu peux acheter
[12] (casual) Un peu...!
```

**inline-np** · seed 7: 2350 ms, 297 prompt / 145 reply tokens · seed 8: 2466 ms, 297 prompt / 156 reply tokens · seed 9: 2418 ms, 297 prompt / 149 reply tokens

```text
--- seed 7 ---
[5] Je ne veux pas être touché par la mafia.
[1] Tu as été touché par un fusil sur la cuisse ?
[7] Tu as peut-être...
[4] Arrête, c'est pas le moment !
[11] Ah, ah...
[8] Est-ce que c'est une fille ?
[3] Eh bien... c'est vrai...
[10] Ah...
[9] Montrer tes tripes !
[6] As-tu l'air de bien voir ?
[15] ?
[2] Si tu fermes les yeux, tu as une bonne intelligence, tu peux acheter quelque chose...
[12] Un peu... !
--- seed 8 ---
[5] Je ne veux pas être touché par la mafia.
[1] Tu as été touché par une arme à feu ? Tu veux voir son cerveau ?
[7] As-tu aussi peur ?
[4] Arrête ! C'est pas le moment !
[11] Ah, ah...
[8] Est-ce que c'est une fille ?
[3] Eh bien... c'est vrai...
[10] Ah...
[9] Montrons-nous la face !
[6] As-tu l'air de ne pas vouloir te cacher ?
[15] ?
[2] Si tu as une intelligence, tu peux acheter un peu de monde...
[12] Un peu... !
--- seed 9 ---
[5] Je ne veux pas être touché par la mafia.
[1] Tu as été touché par un fusil sur la cuisse ?
[7] As-tu aussi ?
[4] Tu peux arrêter ! Dans ce genre de situation, c'est inapproprié !
[11] Ah, ah...
[8] Est-ce que c'est une fille ?
[3] Eh bien... Oui, mais...
[10] Ah...
[9] Montrer tes tripes !
[6] As-tu l'air prudent ?
[15] Quoi ?
[2] Si tu fermes les yeux, tu es un bon client !
[12] Un peu... !
```

**sys-ctl** · seed 7: 2299 ms, 209 prompt / 171 reply tokens · seed 8: 2216 ms, 209 prompt / 164 reply tokens · seed 9: 2121 ms, 209 prompt / 152 reply tokens

```text
--- seed 7 ---
[5] Le mafieux qui se masturbe est généralement un imbécile
[1] Tu as été tiré au nez par un fusil à tire-longs, tu veux voir la tête qui en résulte ?
[7] Tu as peut-être ?
[4] Arrête, c'est pas la peine ! On est pas là pour ça !
[11] Ah, ah...
[8] C'est une fille ?
[3] Ah ouais... c'est vrai...
[10] Ah
[9] Montrer tes fesses
[6] Tu as fermé les yeux pour être un bon client ?
[15] ?
[2] Fermetes les yeux, même un client peu intelligent peut être acheté...
[12] Un peu... !
--- seed 8 ---
[5] Le mafieux qui me mangeait la bouche était-il un peu... ?
[1] Qu'as-tu fait de mon cerveau ? Tu veux voir ?
[7] Tu es peut-être...
[4] Arrête, c'est pas la peine ! On ne se bat pas ici !
[11] Ah, ah...
[8] C'est une fille ?
[3] Eh bien... c'est vrai...
[10] Ah...
[9] Montrons-nous nos dents...
[6] As-tu fermé les yeux pour être un bon mercenaire ?
[15] ?
[2] Ferme les yeux, même un peu, et tu seras achetable...
[12] Un peu... !
--- seed 9 ---
[5] Le mafieux qui se léchonne la bouche est la plupart du temps
[1] Qu'as-tu fait de ta tête ? Tu veux voir ?
[7] Tu es peut-être
[4] Arrête, c'est pas la peine
[11] Ah, ah...
[8] C'est une fille ?
[3] Ah ouais... mais c'est vrai...
[10] Ah
[9] Montrer ton visage
[6] Est-ce que tu as les yeux fermés ?
[15] ?
[2] Avec les yeux fermés, tu peux toujours être un bon acheteur...
[12] Un peu... !
```

**sys** · seed 7: 2517 ms, 283 prompt / 164 reply tokens · seed 8: 2578 ms, 283 prompt / 170 reply tokens · seed 9: 8825 ms, 283 prompt / 768 reply tokens

```text
--- seed 7 ---
[5] Le mafieux, je ne peux pas le toucher
[1] Qu'est-ce que tu vas faire avec mon cerveau éventré par les coups de pistolet sur la hanche ?
[7] Tu es peut-être...
[4] Arrête ça ! On ne peut pas se faire du mal dans ce genre de situation !
[11] Ah, ah...
[8] C'est une fille ?
[3] Ah ouais... c'est vrai ?
[10] Ah
[9] Montrer tes dents
[6] Tu es un cordon bleu ?
[15] ?
[2] Montrer tes dents, même avec les yeux fermés, ça ne change rien
[12] Un peu... !
--- seed 8 ---
[5] Le mafieux est-il ce qui est le plus souvent mangé?
[1] Qu'as-tu fait de ta tête, tu as été frappé par un fusil sur l'épaule?
[7] Tu es peut-être...
[4] Arrête, tu es dans un mauvais endroit!
[11] Ah, ah...
[8] Est-ce que c'est une fille?
[3] Eh bien, oui... mais...
[10] Ah...
[9] Montrer tes dents, je te le demande!
[6] As-tu l'air de t'en prendre pour un malin?
[15] Quoi?
[2] Si tu as un esprit, tu peux toujours être acheté...
[12] Un peu...!
--- seed 9 ---
[5] Le mafieux, je le suçonne, je le croque, je le déchire, je le déchire, je le déchire, je le déchire, je le déchire, je le déchire, je le déchire, je le déchire,
[1] Qu'est-ce que je peux faire de ta tête, tu as été touchée par la balle dans le dos, tu as été touchée par la balle dans le dos, tu as été touchée par la balle, 
[7] Tu es peut-être un mafieux, peut-être non, je ne sais pas, je ne sais pas, je ne sais pas, je ne sais pas, je ne sais pas, je ne sais pas, je ne sais pas, je ne
[4] Arrête, c'est pas ça, c'est pas ça, c'est pas ça, c'est pas ça, c'est pas ça, c'est pas ça, c'est pas ça, c'est pas ça, c'est pas ça, c'est pas ça, 
[11] Ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, 
[8] Est-ce que c'est une fille, est-ce que c'est une fille, est-ce que c'est une fille, est-ce que c'est une fille, est-ce que c'est une fille, est-ce que c'est une
[3] Eh bien, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, oui, 
[10] Ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, ah, 
[9] Montrer tes fesses, montrer tes fesses, montrer tes fesses, montrer tes fesses, montrer tes fesses, montrer tes fesses, montrer tes fesses, montrer tes fesses, 
[6] As-tu fermé les yeux, as-tu fermé les yeux, as-tu fermé les yeux, as-tu fermé les yeux, as-tu fermé les yeux, as-tu fermé les yeux, as-tu fermé les yeux, as-tu
[15] Qu'est-ce que tu veux, qu'est-ce que tu veux, qu'est-ce que tu veux, qu'est-ce que tu veux, qu'est-ce que tu veux, qu'est-ce que tu veux, qu'est-ce que tu
[2] Si tu as une tête, si tu as une tête, si tu as une tête, si tu as une tête, si tu as une tête, si tu as une tête, si tu as une tête, si tu as une tête, si tu
[12] Oh, oh, oh, oh, oh, oh, oh, oh, oh,
```

**sys-qwen** · seed 7: 2528 ms, 293 prompt / 163 reply tokens · seed 8: 2378 ms, 293 prompt / 149 reply tokens · seed 9: 2387 ms, 293 prompt / 149 reply tokens

```text
--- seed 7 ---
[5] Le mafieux se lécher la langue est-il assez rare
[1] À quoi ça sert d'être éventrée dans le dos avec une arme à feu ?
[7] Tu crois que tu peux...
[4] Arrête, c'est pas dans ton style !
[11] Ah, ah...
[8] C'est une fille ?
[3] Ah ouais... c'est vrai ?
[10] Ah !
[9] Montrer ton visage
[6] Est-ce que tu as l'air de bien te défendre ?
[15] ?
[2] Montrer tes capacités, même si tu as les yeux fermés, ça vaut le coup ! 
[12] Un peu... !
--- seed 8 ---
[5] Le mafieux se délecte de son propre souffle
[1] Qu'as-tu fait de ta tête, blessée par un fusil à tire-longs ?
[7] Tu as peut-être...
[4] Arrête, c'est trop tôt !
[11] Ah, ah...
[8] Est-ce que c'est une fille ?
[3] Ah, oui... mais...
[10] Ah...
[9] Montrer tes tripes
[6] As-tu l'air de bien penser ?
[15] ?
[2] Si tu as un esprit, tu peux toujours acheter quelque chose...
[12] Un peu...!
--- seed 9 ---
[5] Le trafic de drogue est-il un peu une industrie ?
[1] Qu'as-tu fait de ta tête ? Tu vas voir ?
[7] Tu es peut-être...
[4] Arrête ça ! On ne peut pas se promener comme ça !
[11] Ah, ah...
[8] Est-ce que c'est une fille ?
[3] Ah ouais... c'est vrai ?
[10] Ah...
[9] Montrer tes tripes...
[6] As-tu l'air d'être un tueur ?
[15] ?
[2] Si tu ferme les yeux, tu es un bon client...
[12] Un peu... !
```


### tojime-dense-action, real emulator OCR, gated, Spanish

**none** · seed 7: 2283 ms, 219 prompt / 165 reply tokens · seed 8: 2286 ms, 219 prompt / 168 reply tokens · seed 9: 3989 ms, 219 prompt / 328 reply tokens

```text
--- seed 7 ---
[5] La maffia me la toma a la boca
[1] ¿Quieres ver mi cerebro desgarrado con la bala de la pistola en la cadera?
[7] ¿También tú?
[4] ¡No, ¡cuidado! ¡En este lugar!
[11] ¡Ahí!
[8] ¿Es una mujer?
[3] ¡¿Qué?... ¡Sí, pero...!
[10] ¡Ah!
[9] ¡Dame la cara!
[6] ¿Eres tan astuto que te puedes quedar sin ojos?
[15] ¿Qué?
[2] ¡Eres tan inteligente que puedes comprar a un tonto!
[12] ¡Un poco...!
--- seed 8 ---
[5] La maffia se la mima a la mayoría
[1] ¿Quieres ver mi cerebro desgarrado con la bala de la pistola en la cadera?
[7] ¿También?
[4] ¡No, déjame! No en este lugar!
[11] ¡Ahí!
[8] ¿Es una mujer?
[3] ¡¿Qué?... ¡Sí, pero...!
[10] ¡Ah!
[9] ¡Muestra tu cara!
[6] ¿Es un círculo de ojos?
[15] ¿Qué?
[2] ¡Círculo de ojos! ¡Tienes buen juicio, por lo que te compraré!
[12] ¡Un poco...!
--- seed 9 ---
[5] La maffia me la mima, ¿hay algo más?
[1] ¿Con un tiro en el hombro y una bala en la cabeza, ¿quieres verlo?
[7] ¿También tú?
[4] ¡No, ¡deja ya! ¡En esta situación!
[11] ¡Ah, ah...
[8] ¿Es una mujer?
[3] ¡¿Qué?... ¡Sí, pero...!
[10] ¡Aaah!
[9] ¡Ponte la cara al frente!
[6] ¿Tienes ojos para ser un asesino?
[15] ¿Qué?
[2] ¡Cierres los ojos, pero... ¡tienes buen juicio!
[12] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

```

**inline-shipped** · seed 7: 5720 ms, 297 prompt / 486 reply tokens · seed 8: 2929 ms, 297 prompt / 205 reply tokens · seed 9: 4418 ms, 297 prompt / 358 reply tokens

```text
--- seed 7 ---
[5] (raro) Me lo quitaron de la boca de la mafia
[1] (raro) ¿Quieres ver mi cerebro con un arma de mi cuello?
[7] (raro) ¿También tú?
[4] (cálido) ¡No, no, no! ¡No, no, no!
[11] ¡Ah, ah...
[8] (raro) ¿Es una mujer?
[3] (polite) ¡Oh, sí... pero... ¡sí!
[10] ¡Ah!
[9] (raro) ¡Ve, ve!
[6] (raro) ¿Tienes ojos para ocultar tu mente?
[15] ¿Qué?
[2] (raro) ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[12] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 8 ---
[5] (rudo) Me lo comió la mafia
[1] (rudo) ¿Quieres ver mi cerebro herido con un arma de fuego?
[7] (rudo) ¿También tú?
[4] (casual) ¡No te hagas el valiente! ¡No puedes estar aquí!
[11] ¡Ah, ah...
[8] (rudo) ¿Es una mujer?
[3] (polite) ¡Oh, sí... pero... ¡sí!
[10] ¡Aaah!
[9] (rudo) ¡Ve a mostrarme tu cara!
[6] (rudo) ¿Eres tan estúpido que te cierres los ojos?
[15] ¿Qué?
[2] (rudo) ¡Eres tan inteligente que incluso los que no tienen cerebro pueden comprarte!
[12] (casual) ¡Un poco más...!
--- seed 9 ---
[5] (raro) Me lo comió la mafia
[1] (raro) ¿Con qué arma te dispararon? ¿Quieres ver mi cerebro?
[7] (raro) ¿También tú?
[4] (cálido) ¡No, no, no! ¡Esto no es momento!
[11] ¡Ah, ah...
[8] (raro) ¿Es una mujer?
[3] (polite) ¡¿Qué?! ¡Sí, pero...
[10] ¡Ah!
[9] (raro) ¡Pide que te vea!
[6] (raro) ¿Eres tan estúpido que te acostumbras a cerrar los ojos?
[15] ¿Qué?
[2] (raro) ¡Eres tan estúpido que puedes ser comprado incluso si te escondes!
[12] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

```

**inline-np** · seed 7: 2512 ms, 297 prompt / 158 reply tokens · seed 8: 6986 ms, 297 prompt / 605 reply tokens · seed 9: 2494 ms, 297 prompt / 156 reply tokens

```text
--- seed 7 ---
[5] Yo, I'm not going to eat the Mafia's cock.
[1] Are you sure you want to see my brain after being shot with a gun in the waist?
[7] Are you really?
[4] Don't you think we should stop? This is not the place!
[11] Ah, ah...
[8] Are you a girl?
[3] Hey, yeah... but I'm still here.
[10] Ah...
[9] Show me your face.
[6] Are you so blind that you can't even see the meaning of my words?
[15] What?
[2] If you close your eyes, you can still be a valuable asset.
[12] Let's try this again...!
--- seed 8 ---
[5] Yo me lo tomo con mala leche
[1] ¿Qué te pasa con eso de la bala en el cuello?
[7] ¿Tú también?
[4] ¡No, vamos! ¿En qué te crees?
[11] Ahí...
[8] ¿Es una mujer?
[3] ¡¿Qué?! ¡Sí, pero...
[10] ¡Ah!
[9] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[6] ¿Tus ojos son el guardián?
[15] ¿Qué?
[2] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[12] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 9 ---
[5] Yo, I'm not gonna touch that Mafia stuff.
[1] Are you sure you want to see my brain in that way?
[7] Are you sure you're not just a little bit of a pervert?
[4] No, no, calm down! This is not the place!
[11] Ah, ah...
[8] Is it a girl?
[3] Hey, yeah... I guess you're right.
[10] Ah...
[9] Show me your face.
[6] Are you sure you're not just a little bit of a pervert?
[15] What?
[2] If you close your eyes, you're still a good guy.
[12] Just a little...!
```

**sys-ctl** · seed 7: 5086 ms, 209 prompt / 459 reply tokens · seed 8: 3729 ms, 209 prompt / 320 reply tokens · seed 9: 5450 ms, 209 prompt / 485 reply tokens

```text
--- seed 7 ---
[5] La mafiosa se la come a poca cosa
[1] ¿Quieres ver el cerebro de tu cuello? ¿Qué?
[7] ¿También tú?
[4] ¡No, ¡no! ¡En esta situación!
[11] ¡Ah, ah...
[8] ¿Es una mujer?
[3] ¡¿Qué?!... ¡Sí, pero...
[10] ¡Aaah!
[9] ¡Ponte la cara al frente!
[6] ¿Tienes ojos para ocultar tu mente?
[15] ¿Qué?
[2] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[12] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 8 ---
[5] La mafiosa me la come a poca cosa
[1] ¿Quieres ver mi cerebro? ¡¿Qué?!
[7] ¿También tú?
[4] ¡No, déjalo! ¡En este lugar!
[11] ¡Ah, ah...
[8] ¿Es una mujer?
[3] ¡¿Qué?... ¡Sí, pero...!
[10] ¡Aaah!
[9] ¡Muestra tu cara!
[6] ¿Eres tan tonto que te acostumbras a cerrar los ojos?
[15] ¿Qué?
[2] ¡Eres tan tonto que tienes que ser tan inteligente como para comprarme!
[12] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 9 ---
[5] La mafiosa se la come a pescara, la mayoría de las veces.
[1] ¿Quieres ver mi cerebro desgarrado con un tiro de escopeta? ¡¿Qué?
[7] ¿También tú?
[4] ¡No, ¡cuidado! ¡En esta situación!
[11] ¡Ah, ah...
[8] ¿Es una chica?
[3] ¡¿Qué? ¡Sí, pero...!
[10] ¡Aaah!
[9] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[6] ¿Tus ojos cerrados son como un guardián?
[15] ¿Qué?
[2] ¿Tus ojos cerrados no son una muestra de tu inteligencia?
[12] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

```

**sys** · seed 7: 2400 ms, 283 prompt / 153 reply tokens · seed 8: 8547 ms, 283 prompt / 768 reply tokens · seed 9: 2444 ms, 283 prompt / 158 reply tokens

```text
--- seed 7 ---
[5] La mafia se la come a pecho
[1] ¿Tienes miedo de que te mueran los cerebros con el tiro de la pistola?
[7] ¿También tú?
[4] ¡No, no, no! ¡No aquí!
[11] ¡Ah, ah...
[8] ¿Es una mujer?
[3] ¡¿Qué?!... ¡Sí, pero...
[10] ¡Ah!
[9] ¡Ponte la cara!
[6] ¿Eres un ciego?
[15] ¿Qué?
[2] ¿Eres un ciego? ¡Tienes un buen cerebro!
[12] ¡Un poco más!
--- seed 8 ---
[5] La mafiosa se la come a pescara
[1] ¿Quieres ver mi cerebro? ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[7] ¿También tú?
[4] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[11] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[8] ¿Es una niña?
[3] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[10] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
--- seed 9 ---
[5] La mafiosa se la come a pescara
[1] ¿Quieres ver mi cerebro desgarrado con la pistola en el cuello?
[7] ¿También tú?
[4] ¡No, no, ¡no! ¡En este lugar!
[11] ¡Ah, ah...
[8] ¿Es una mujer?
[3] ¡¿Qué?! ¡Sí, sí! ¡Pero...
[10] ¡Ah!
[9] ¡Dame tu cara!
[6] ¿Es un cordero?
[15] ¿Qué?
[2] ¡Cerrad los ojos, pero si tienes buen juicio...
[12] ¡Un poco más!
```

**sys-qwen** · seed 7: 5407 ms, 293 prompt / 457 reply tokens · seed 8: 2474 ms, 293 prompt / 155 reply tokens · seed 9: 2482 ms, 293 prompt / 161 reply tokens

```text
--- seed 7 ---
[5] La mafiosa se la come a poca cosa
[1] ¿Qué tal, ¿te has dado una vuelta con la pistola en la cadera?
[7] ¿Tú también?
[4] ¡No, no, ¡no! ¡No en este lugar!
[11] ¡Ah, ah...
[8] ¿Es una mujer?
[3] ¡¿Qué?... ¡Sí, pero...!
[10] ¡Ah!
[9] ¡Dame la cara!
[6] ¿Es un cuchillo?
[15] ¿Qué?
[2] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡
[12] ¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡¡

--- seed 8 ---
[5] La mafiosa se la mima tanto, ¿verdad?
[1] ¿Con qué arma te dispararon en el cuello?
[7] ¿También tú?
[4] ¡No, no, ¡no! ¿En este lugar?
[11] ¡Ah, ah...
[8] ¿Es una mujer?
[3] ¡¿Qué?... ¡Sí, pero...!
[10] ¡Ah!
[9] ¡Mostradme la cara!
[6] ¿Eres tan astuto que no te das cuenta?
[15] ¿Qué?
[2] ¡Eres tan astuto que no te das cuenta!
[12] ¡Un poco más...!
--- seed 9 ---
[5] La mafiosa se la come a poca gente
[1] ¿Tienes miedo de que te saquen el cerebro con un tiro de cadera?
[7] ¿También?
[4] ¡No, no, ¡no! ¡En este lugar!
[11] ¡Ah, ah...
[8] ¿Es una mujer?
[3] ¡Qué?... ¡Sí, pero...!
[10] ¡Ah!
[9] ¡Muestra tu cara!
[6] ¿Eres tan estúpido que te cierres los ojos?
[15] ¿Qué?
[2] ¡Eres tan estúpido que...!
[12] ¡Un poco...!
```


### tojime-dense-action, real emulator OCR, gated, German

**none** · seed 7: 2202 ms, 219 prompt / 157 reply tokens · seed 8: 2148 ms, 219 prompt / 152 reply tokens · seed 9: 1997 ms, 219 prompt / 136 reply tokens

```text
--- seed 7 ---
[5] Mafia-tastiert auch das Großteil
[1] Was? Ich will dich nur ansehen, wenn du mich anfassen willst?
[7] Was meinst du damit?
[4] Ich sage dir, lass das! So weit ist es noch nicht!
[11] Ach, das ist ja...
[8] Frau?
[3] Ach, ja... Das ist aber wahr...
[10] Ach
[9] Lass mich das ansehen
[6] Schließ die Augen, sie sind die Waffe?
[15] Was?
[2] Schließ die Augen, auch das ist ein Talent, das man kaufen kann...
[12] Ach, ein bisschen...!
--- seed 8 ---
[5] Mafia-tastiert auch viele
[1] Was ich dir an die Nase binden muss?
[7] Was meinst du, wenn ich dich an die Nase binden?
[4] Lass das! So weit bist du schon!
[11] Ach, das ist ja...
[8] Frau?
[3] Ach ja... Siehst du das also?
[10] Ach
[9] Zeig mir mal deine Nase!
[6] Was ist, wenn ich dich mit meinen Augen betrachte?
[15] Nee?
[2] Mit geschlossenen Augen ist man doch auch nicht dumm!
[12] Ach, ein bisschen...!
--- seed 9 ---
[5] Mafia-Zunge-Zunge-Genuss-also
[1] Rüssel-Rüssel-Feuille-Feuille-schau mal?
[7] So auch?
[4] Halt mal! So weit!
[11] Ach, das...
[8] Frau?
[3] Ach ja... das ist aber so...
[10] Ach
[9] Gesicht zeigen!
[6] Schließ die Augen, bist du's?
[15] Nee?
[2] Schließ die Augen, auch wenn du's bist...
[12] Ach, ein bisschen...!
```

**inline-shipped** · seed 7: 3526 ms, 297 prompt / 267 reply tokens · seed 8: 2634 ms, 297 prompt / 173 reply tokens · seed 9: 3474 ms, 297 prompt / 256 reply tokens

```text
--- seed 7 ---
[5] (rassendlich) Ich werde mich nicht mit den Mafiosi auseinandersetzen.
[1] (rassendlich) Was soll ich mit dem Schuss in deinem Leibarzt? Ich?
[7] (rassendlich) So wie du bist, bist du auch in der Lage, mich zu erschießen.
[4] (freundlich) Lass uns das bitte auf ein anderes Mal machen! So weit sind wir ja schon.
[11] (freundlich) Ach, das ist ja schon.
[8] (rassendlich) Was für eine Frau bist du?
[3] (polite) Ach, ja... Das ist richtig.
[10] (rassendlich) Ach, das ist ja schon.
[9] (rassendlich) Gib mir deinen Kopf!
[6] (rassendlich) Ist das dein Augenlid, das hilft dir dabei, den Feind zu sehen?
[15] (rassendlich) Was?
[2] (rassendlich) Wenn du dich so anstellst, kannst du auch noch ein paar andere kaufen.
[12] (freundlich) Ach, das ist ja schon.
--- seed 8 ---
[5] (rugged) I'm not interested in Mafia stuff
[1] (rugged) I want to see your brain after being shot with a gun
[7] (rugged) Are you too?
[4] (casual) Stop it! This isn't the place!
[11] Ah, ah...
[8] (rugged) Is it a girl?
[3] (polite) Oh, yes... but...
[10] Ah
[9] (rugged) Show me your face
[6] (rugged) Are you so blind that you don't even notice your eyes?
[15] Hmm?
[2] (rugged) Even if you close your eyes, you can't hide your talent
[12] (casual) Just a little...!
--- seed 9 ---
[5] (rassendlich) Ich werde mich mit den Mafiabisseln nicht anfassen.
[1] (rassendlich) Was, wenn ich mit dem Revolver am Hals geschossen wurde und jetzt nur noch mein Hirn habe?
[7] (rassendlich) Was meinst du, wenn ich auch so aussehen würde?
[4] (freundlich) Komm, lass uns das nicht tun! So weit draußen!
[11] (freundlich) Ja, ja...
[8] (rassendlich) Was für eine Frau?
[3] (polite) Hör mal, ich sage dir, das ist wahr...
[10] (freundlich) Ach, ach...
[9] (rassendlich) Zeig mal, was du hast!
[6] (rassendlich) Was, wenn ich mit geschlossenen Augen ein Kriechmesser hätte?
[15] (rassendlich) Was?
[2] (rassendlich) Wenn ich geschlossen habe, könnte ich auch ein guter Kaufmann sein...
[12] (freundlich) Ach, ach...
```

**inline-np** · seed 7: 2515 ms, 297 prompt / 158 reply tokens · seed 8: 2706 ms, 297 prompt / 181 reply tokens · seed 9: 2522 ms, 297 prompt / 164 reply tokens

```text
--- seed 7 ---
[5] Ich werde mich nicht mit den Mafiabisseln anlegen.
[1] Hast du etwa wirklich einen Revolver in der Hüfte?
[7] Hast du vielleicht auch?
[4] Hast du bitte nicht so laut? So weit ist es noch nicht!
[11] Ach, das ist ja...
[8] Hast du etwa wirklich eine Frau?
[3] Ja, das ist richtig, aber...
[10] Ach, das ist ja...
[9] Gib mir bitte die Augen zu!
[6] Hast du etwa wirklich einen Schutzschild im Kopf?
[15] Was?
[2] Hast du etwa wirklich einen Schutzschild im Kopf?
[12] Hast du etwa wirklich eine Frau?
--- seed 8 ---
[5] Ich werde mich nicht mit Mafiakuss beschäftigen.
[1] Hast du etwa wirklich einen Pistolenschuss in den Kopf bekommen?
[7] Hast du vielleicht auch so etwas getan?
[4] Komm, lass uns das auf den Mund kehren! So weit kommst du ja nicht!
[11] Ach, das ist ja so ein seltsames Verhalten!
[8] Hast du etwa wirklich so etwas getan?
[3] Ja, das ist richtig, aber...
[10] Ach, das ist ja so ein seltsames Verhalten!
[9] Lass uns das mal sehen!
[6] Hast du etwa wirklich so etwas getan?
[15] Was?
[2] Lass uns das mal sehen!
[12] Ach, das ist ja so ein seltsames Verhalten!
--- seed 9 ---
[5] Ich werde mich nicht mit Mafiakuss beschäftigen.
[1] Hast du etwa wirklich so etwas getan?
[7] Hast du etwa wirklich so etwas getan?
[4] Hast du etwa wirklich so etwas getan?
[11] Ja, das ist ja auch nicht so schlecht.
[8] Hast du etwa wirklich so etwas getan?
[3] Ja, das ist ja auch nicht so schlecht.
[10] Ach, das ist ja auch nicht so schlecht.
[9] Gib mir deinen Gesichtsausdruck an.
[6] Hast du etwa wirklich so etwas getan?
[15] Was?
[2] Du könntest auch gut sein.
[12] Hast du etwa wirklich so etwas getan?
```

**sys-ctl** · seed 7: 2242 ms, 209 prompt / 165 reply tokens · seed 8: 2062 ms, 209 prompt / 148 reply tokens · seed 9: 2782 ms, 209 prompt / 224 reply tokens

```text
--- seed 7 ---
[5] Die Mafia-Zunge ist mir egal
[1] Was, wenn ich dich mit dem Revolver erschreckt habe?
[7] Was meinst du, wenn ich dich mit dem Revolver erschreckt habe?
[4] Lass das! So weit habe ich es noch nicht ausgezogen!
[11] Ach, das war ja so eindeutig...
[8] Was für eine Frau?
[3] Ach ja... Sie ist also eine Frau?
[10] Ach
[9] Lass mich die Augen sehen!
[6] Ist das nicht ein Anzeige?
[15] Nee?
[2] Lass mich die Augen sehen!
[12] Ach, das war ja so eindeutig...
--- seed 8 ---
[5] Die Mafia-Zunge ist nicht zu verachten
[1] Was willst du damit? Ich will dich sehen!
[7] Was meinst du mit "Ich könnte es sein"
[4] Lass das! So weit ist es nicht!
[11] Ach, das ist ja...
[8] Frauen?
[3] Ach ja... Sieht so aus
[10] Ach
[9] Zeig mir deine Hände
[6] Was ist mit deinen Augen? Sie sind ein Wächter
[15] Was?
[2] Wenn du dich nicht ändert, werde ich dich kaufen
[12] Ach, ein bisschen...!
--- seed 9 ---
[5] Die Mafia-Zunge ist mir egal
[1] Was, wenn ich dich mit dem Revolver in den Leib trete?
[7] Was meinst du mit "Ich bin auch so"?
[4] Ich will das nicht! So weit bist du schon!
[11] Ach, das ist ja...
[8] Was ist das für ein Mädchen?
[3] Ach, ja... Aber ich muss es tun...
[10] Ach, nein...
[9] Wenn du das Zeigefinger zeigst, soll ich das auch?
[6] Ist das nicht ein Witz?
[15] Ach, nein...
[2] Wenn du das Zeigefinger zeigst, soll ich das auch?
[12] Ach, nein... Ach, nein... Ach, nein... Ach, nein... Ach, nein... Ach, nein... Ach, nein... Ach, nein... Ach, nein... Ach, nein... Ach, nein... Ach, nein... Ach,
```

**sys** · seed 7: 2270 ms, 283 prompt / 142 reply tokens · seed 8: 2475 ms, 283 prompt / 161 reply tokens · seed 9: 2410 ms, 283 prompt / 155 reply tokens

```text
--- seed 7 ---
[5] Die Mafia-Zunge ist nicht zu verachten
[1] Was ist mit dem Schuss an deinem Hals?
[7] Hast du auch?
[4] Lass uns doch! So weit bist du schon!
[11] Ach, das ist ja...
[8] Was für eine Frau?
[3] Ach ja... So ist es also...
[10] Ach, das ist ja...
[9] Zeig mal deine Hände
[6] Die Augen schließen sind ein Wächter
[15] Was?
[2] Die Augen schließen sind auch ein Talent
[12] Lass uns doch!
--- seed 8 ---
[5] Die Mafia-Schmeichelei ist normalerweise nicht üblich
[1] Was, wenn ich dich mit dem Revolver erschieße?
[7] Was meinst du, du Dummkopf?
[4] Lass das! So weit bist du schon!
[11] Ach, das ist so...
[8] Was für eine Frau?
[3] Ach ja... ja, das ist so...
[10] Ach, das ist so...
[9] Zeig mir mal deine Hände
[6] Ist dein Auge ein Schutzschild?
[15] Was?
[2] Wenn du dich nur still haltest, kannst du auch gut gekauft werden
[12] Lass es einfach!
--- seed 9 ---
[5] Die Mafia-Zunge ist nicht zu verachten
[1] Was ist mit dem Kugelhalsband, den du mir gebracht hast?
[7] Was meinst du, hast du...
[4] Ich sage dir, lass das! So weit bist du schon!
[11] Ach, das ist...
[8] Was für eine Frau?
[3] Ach ja... das ist richtig...
[10] Ach, das ist...
[9] Wenn du dich nur zeigt...
[6] Das Schließen der Augen ist ein Verräter?
[15] Was?
[2] Das Schließen der Augen kann auch einen klüger machen...
[12] Ich muss...!
```

**sys-qwen** · seed 7: 2589 ms, 293 prompt / 173 reply tokens · seed 8: 2684 ms, 293 prompt / 183 reply tokens · seed 9: 2546 ms, 293 prompt / 167 reply tokens

```text
--- seed 7 ---
[5] Die Mafia-Zunge wird nicht so leicht angefasst.
[1] Was, wenn du mich mit dem Revolver erschießt?
[7] Was meinst du, wenn ich dich mit dem Revolver erschieße?
[4] Lass uns doch bitte! So weit reicht es!
[11] Ach, das ist ja...
[8] So eine Frau?
[3] Ach, ja... Aber das ist ja so!
[10] Ach, das ist ja...
[9] Lass mich das Gesicht sehen!
[6] Ist das nicht ein Zeichen für Vorsicht?
[15] Nee?
[2] Lass mich die Augen schließen! So gut ist es auch!
[12] Lass uns das bitte noch ein bisschen...!
--- seed 8 ---
[5] Die Mafia saugt auch nicht so leicht.
[1] Was willst du damit, dass ich dich mit der Waffe in den Leib trete?
[7] Was denkst du, dass ich tun würde?
[4] Lass uns doch mal ein bisschen abwarten! So weit ist es noch nicht!
[11] Ach, das ist ja so ein...
[8] Ist das Mädchen?
[3] Ach ja... ja, das ist richtig.
[10] Ach, das ist ja so ein...
[9] Lass mich das Gesicht sehen!
[6] Ist das so wichtig, dass ich mich verstecken muss?
[15] Nee?
[2] Wenn ich dich nur ansehe, kann ich dich auch kaufen!
[12] Lass uns doch ein bisschen abwarten!
--- seed 9 ---
[5] Die Mafia-Blutung ist nicht zu unterschätzen.
[1] Was, wenn ich dich mit dem Revolver erschlage?
[7] Was meinst du, wenn ich dich anfasse?
[4] Lass uns doch bitte! So weit sind wir noch nicht!
[11] Ach, das ist ja...
[8] Was für eine Frau?
[3] Ach, ja... So ist es.
[10] Ach, das ist ja...
[9] Zeig mir mal deine Hände!
[6] Ist das nicht ein Zeichen für Vorsicht?
[15] Nee?
[2] Wenn du dich nur still haltst, könnte man dich kaufen.
[12] Hast du das nur für einen Scherz gedacht?
```


### tojime-dense-action, real emulator OCR, gated, Brazilian Portuguese

**none** · seed 7: 2081 ms, 220 prompt / 141 reply tokens · seed 8: 2125 ms, 220 prompt / 144 reply tokens · seed 9: 3565 ms, 220 prompt / 297 reply tokens

```text
--- seed 7 ---
[5] A mafia gostam de meus cabelos
[1] Que tal eu te mostrar meu cérebro com um tiro na cintura?
[7] Você também?
[4] Vamos parar! Não está na hora!
[11] Ah, ah...
[8] É mulher?
[3] Oh, sim... mas...
[10] Ah...
[9] Mostra o meu rosto
[6] Você é um vigilante fechando os olhos?
[15] N?
[2] Fechando os olhos, a inteligência não é um problema
[12] Um pouco...!
--- seed 8 ---
[5] A mafia goste de me beijar?
[1] O que você vai fazer com o meu cérebro machucado com um tiro na cintura?
[7] Você também?
[4] Não, eu não vou permitir isso!
[11] Ah, ah...
[8] Você é mulher?
[3] Oh, sim... mas...
[10] Ah...
[9] Mostra o meu rosto!
[6] O que você está fazendo comigo?
[15] Nuh?
[2] Fechando os olhos, você é uma pessoa de mérito?
[12] Um pouco...!
--- seed 9 ---
[5] A mafia gostam de meus braços?
[1] A bala na cabeça me acordou?
[7] Você também?
[4] Não, vamos parar! Isso não é lugar para isso!
[11] Ah, ah...
[8] É mulher?
[3] Oh, sim... mas...
[10] Ah...
[9] Mostra a cara!
[6] O que você fez com os olhos é um bandido?
[15] Nããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããããã
[2] Fechou os olhos, mas era um bom jogador?
[12] Um pouquinho...!
```

**inline-shipped** · seed 7: 2732 ms, 298 prompt / 176 reply tokens · seed 8: 2595 ms, 298 prompt / 170 reply tokens · seed 9: 2605 ms, 298 prompt / 171 reply tokens

```text
--- seed 7 ---
[5] (rough) O que você beijou na máfia?
[1] (rough) Você foi ferido com um tiro na cabeça?
[7] (rough) Você também, talvez?
[4] (casual) Pare com isso! Não está na hora!
[11] Ah, ah...
[8] (rough) Você é mulher?
[3] (polite) Oh, sim... mas...
[10] Ah
[9] (rough) Mostra o rosto para eu ver?
[6] (rough) Você está usando um olho como um bastião?
[15] N?
[2] (rough) Se você fechar os olhos, você é uma pessoa de talento, então você pode comprar...
[12] (casual) Um pouco...!
--- seed 8 ---
[5] (rough) O que a mafia faz com você?
[1] (rough) Que tipo de macho é você que está tentando machucar?
[7] (rough) Você também?
[4] (casual) Pare com isso! Não está na hora!
[11] Ah, ah...
[8] (rough) Você é mulher?
[3] (polite) Oh, sim... mas...
[10] Ah...
[9] (rough) Mostra a cara!
[6] (rough) O seu olho fechado é um guarda-chuva?
[15] Nuh?
[2] (rough) Se você fechar os olhos, é bom que seja um bom negócio.
[12] (casual) Ah, vamos...!
--- seed 9 ---
[5] (rough) O que você quer com a máfia?
[1] (rough) Quem te machucou com o rifle na cintura?
[7] (rough) Você também, talvez?
[4] (casual) Pare de falar assim! Não está na hora!
[11] Ah, ah...
[8] (rough) É mulher?
[3] (polite) Oh, sim... mas...
[10] Ah
[9] (rough) Mostra a cara!
[6] (rough) O que você está tentando proteger com os olhos fechados?
[15] Hmm?
[2] (rough) Se você tiver um bom senso, pode comprar.
[12] (casual) Um pouco...!
```

**inline-np** · seed 7: 2698 ms, 298 prompt / 181 reply tokens · seed 8: 2350 ms, 298 prompt / 143 reply tokens · seed 9: 2418 ms, 298 prompt / 150 reply tokens

```text
--- seed 7 ---
[5] Não beijarei a boca do seu cérebro se você for um traficante.
[1] Você realmente foi atingido com um tiro na cintura e agora está com medo?
[7] Você também, talvez?
[4] Pare de me provocar! Não estamos em um lugar para isso.
[11] Ah, ah...
[8] Você é uma mulher?
[3] Olá, sim, mas...
[10] Ah...
[9] Mostra seu rosto para eu ver se está tudo bem.
[6] Você realmente pensa que eu não vou ouvir quando você fechar os olhos?
[15] Que?
[2] Se você não quiser, você pode ser comprado mesmo assim.
[12] Pare de ser tão rude! Você está me provocando.
--- seed 8 ---
[5] Não vou beijar nada de mafia.
[1] Vou ver se você foi baleado com um rifle na cintura.
[7] Você também, talvez?
[4] Não, por favor! Não está na hora!
[11] Ah, ah...
[8] É mulher?
[3] Oh, sim... mas...
[10] Ah...
[9] Vamos mostrar o rosto.
[6] Você está escondendo os olhos como um guarda-chuva?
[15] Hmm?
[2] Se você não esconde os olhos, pode comprar.
[12] Vamos, um pouco...!
--- seed 9 ---
[5] Não beijarei a boca do seu cérebro se você me forçar.
[1] Você realmente queria ver minha cabeça?!
[7] Você também, talvez?
[4] Pare de me incomodar! Não está na hora!
[11] Ah, ah...
[8] Você é mulher?
[3] Olá, sim, mas...
[10] Ah...
[9] Mostra-me o seu rosto!
[6] Você é um espelho de pensamentos?
[15] Ah?
[2] Se você não quiser, pode comprar o seu pensamento!
[12] Pare de me incomodar! Não está na hora!
```

**sys-ctl** · seed 7: 2072 ms, 210 prompt / 147 reply tokens · seed 8: 2143 ms, 210 prompt / 156 reply tokens · seed 9: 2231 ms, 210 prompt / 165 reply tokens

```text
--- seed 7 ---
[5] O que omafia faz com a cabeça deles
[1] Que tal eu ver se você ainda tem a cabeça no seu saco?
[7] Você também, talvez?
[4] Vamos parar! Nesse lugar, não!
[11] Ah, ah...
[8] É mulher?
[3] Ah, sim... mas...
[10] Ah
[9] Mostra o rosto
[6] O que você faz com os olhos fechados?
[15] Nuh?
[2] Seus olhos fechados, você tem um bom juízo, então pode comprar
[12] Um pouco...!
--- seed 8 ---
[5] O que os mafiosos gostam de beber são geralmente as mãos molhadas.
[1] Quer beber a cabeça do seu braço? Não?
[7] Você também, talvez?
[4] Pare! Não é assim que se faz.
[11] Ah, ah...
[8] É mulher?
[3] Ah, sim... mas...
[10] Ah...
[9] Deixa eu ver seu rosto.
[6] O que você fez com os olhos? Eles são os nossos guardiões.
[15] Ah?
[2] Seus olhos fechados, você é um bom negócio.
[12] Um pouco...!
--- seed 9 ---
[5] O que omafia beijar pode ser geralmente
[1] Que tal eu ver se você ainda tem a cabeça depois de ser disparado com um tiro na cintura?
[7] Você também pode ser
[4] Não, você precisa parar! Não pode ser em uma situação dessas!
[11] Ah, ah...
[8] Você é mulher?
[3] Ei, sim... mas...
[10] Ah
[9] Mostra o rosto
[6] O que você fez com o olho fechado?
[15] N?
[2] O que você fez com o olho fechado? Você tem bom senso mesmo?
[12] Ah, um pouco...!
```

**sys** · seed 7: 2419 ms, 284 prompt / 158 reply tokens · seed 8: 2419 ms, 284 prompt / 157 reply tokens · seed 9: 2234 ms, 284 prompt / 137 reply tokens

```text
--- seed 7 ---
[5] Apanhando a mafia, a maioria é boa
[1] Que tal eu ver se você ainda tem a cabeça depois de ser disparado com o punhal na cintura?
[7] Você também, talvez?
[4] Não, você precisa parar! Não está na hora!
[11] Ah, ah...
[8] É mulher?
[3] Oh, sim... mas...
[10] Ah...
[9] Mostra seu rosto
[6] Você é um espelho fechado?
[15] N?
[2] Se você não quiser, não tem problema, mas é difícil vender um homem que não se mostra
[12] Ah, um pouco...!
--- seed 8 ---
[5] A mafia se beijar também é geralmente
[1] Que tal eu ver seu cérebro machucado com um tiro na cintura? Não?
[7] Você também, talvez?
[4] Não, por favor. Não pode ser aqui!
[11] Ah, ah...
[8] Você é mulher?
[3] Oh, sim... mas...
[10] Ah...
[9] Mostra o rosto para eu ver
[6] O que você fez com os olhos? É um guarda-chuva?
[15] ?
[2] Seus olhos fechados, então você tem bom senso...
[12] Ah, vamos...!
--- seed 9 ---
[5] Acho que ele gostou de mim
[1] Quem sabe se você não foi ferido com um tiro na cabeça
[7] Você também pode ser
[4] Pare com isso! Não é assim que se faz
[11] Ah, ah...
[8] É mulher?
[3] Oh, sim... mas...
[10] Ah...
[9] Mostra pra gente
[6] Você é um espelho fechado?
[15] Ah?
[2] Se você não é um espelho fechado, pode ser comprado
[12] Ah, um pouco...!
```

**sys-qwen** · seed 7: 2598 ms, 294 prompt / 173 reply tokens · seed 8: 2410 ms, 294 prompt / 153 reply tokens · seed 9: 2333 ms, 294 prompt / 144 reply tokens

```text
--- seed 7 ---
[5] A mafia to shime nō o kangaeru ga yarou
[1] Iku kuchi no ketsu to iku kuchi no kotsu to kawatte miru ka? A?
[7] Ore mo shite sore
[4] Suki yotte yo! Soshite koko de!
[11] A, ane...
[8] Onna ka?
[3] E? Ii... yasou de...
[10] Aa
[9] Ketsu to miru
[6] Ketsu ga iki mado da?
[15] N?
[2] Ketsu de mo kizoku ga yasai ya yasai shi
[12] Yatsu koso!
--- seed 8 ---
[5] Acho que você beijou a boca dele com o nariz da mafia.
[1] Quer dizer, você usou o rifle na cintura para fazer isso?
[7] Você também?
[4] Pare! Por que você está fazendo isso aqui?
[11] Ah, ah...
[8] É uma mulher?
[3] Ah, sim... mas...
[10] Ah...
[9] Deixe-me ver seu rosto.
[6] Você está usando o olho como um guarda-chuva?
[15] N?
[2] Se você fechar os olhos, ainda assim, é uma pessoa de mérito.
[12] Um pouco...!
--- seed 9 ---
[5] Apanhando a mafia, geralmente não se faz isso.
[1] Que tal eu ver o seu cérebro machucado com um tiro na cintura?
[7] Você também, talvez?
[4] Não, por favor, vamos parar aqui.
[11] Ah, ah...
[8] É uma mulher?
[3] Oh, sim... mas...
[10] Ah...
[9] Mostra seu rosto.
[6] Você é um cego?
[15] Nada...
[2] Se você não for um bom candidato, não vale a pena.
[12] Ainda mais...!
```



## Appendix D: every English re-run reply

Hand transcription, English target, seeds 7, 8 and 9. `none` is the shipped prompt with Qwen's default system turn; `sys-ctl` replaces that turn with "You are a manga translator." and nothing else.


### balloon-dense-dialogue, hand transcription, English

**none** · seed 7: 2544 ms, 270 prompt / 176 reply tokens · seed 8: 2626 ms, 270 prompt / 187 reply tokens · seed 9: 2521 ms, 270 prompt / 176 reply tokens

```text
--- seed 7 ---
[1] Only by collecting as many balloons as possible will they float, no matter how much air you put in them!!
[2] He's definitely a fool!!
[3] mugu
[4] What nonsense!?
[5] Helium is needed to make the balloon float!!
[6] It's a disposable camera, basically!!
[7] It's really good at taking photos!!
[8] Ah, it was too cheap!!
[9] Can you even take a picture with film in space?!!
[10] What a shame!!
[11] Yes, technically it's possible, but...
[12] Photos of the Earth can be found on the internet for free!!
[13] Even if you take it yourself, it's not worth it...
[14] Ah
[15] That's right...
--- seed 8 ---
[1] Only by collecting as many balloons as possible will they float, no matter how much air is put in them!!
[2] This guy is definitely a fool!!
[3] mugu
[4] What the hell!!?
[5] Helium is needed to make the balloon float!!
[6] It's a disposable camera, basically!!
[7] It's amazing!! (This is a translation of "すごい!!" which is an exclamation of admiration or surprise)
[8] Ah, it was cheap!!
[9] Can you even take a picture in space with film??!!
[10] What the hell!!?
[11] Yes, theoretically it is possible, but...
[12] Photos of the Earth are everywhere on the internet, right?
[13] Even if you take it yourself...
[14] Ah
[15] That's right...
--- seed 9 ---
[1] Only by collecting as many balloons as possible will they float, no matter how much air is put in them!!
[2] This guy is definitely a fool!!
[3] mugu
[4] What the hell!?
[5] Helium is needed to make the balloon float!!
[6] It's a disposable camera, basically!!
[7] It's really good at taking photos!!
[8] Oh, it was too cheap!!
[9] Can you even take pictures in space with film???
[10] What a waste of time!!
[11] Yes, theoretically it's possible, but...
[12] There are plenty of pictures of the Earth on the internet, right?
[13] You wouldn't even take a picture yourself...
[14] Ah
[15] That's right...
```

**sys-ctl** · seed 7: 2462 ms, 260 prompt / 171 reply tokens · seed 8: 2647 ms, 260 prompt / 191 reply tokens · seed 9: 2489 ms, 260 prompt / 173 reply tokens

```text
--- seed 7 ---
[1] Only by collecting as many balloons as possible will they float, no matter how much air is pumped into them!!
[2] This guy is obviously a fool
[3] mu-gg
[4] What the hell!?
[5] Helium is needed for the balloon to float!!
[6] It's a disposable camera, basically
[7] It's really good at taking pictures
[8] Ah, it was too cheap...
[9] Can you take pictures with film in space?!
[10] What a waste of time
[11] Yes, theoretically it's possible, but...
[12] There are plenty of pictures of Earth on the internet
[13] Even if you take it yourself...
[14] Ah
[15] That's right...
--- seed 8 ---
[1] Only by collecting as many balloons as possible will they float, no matter how much air you put in them!!
[2] This guy is definitely a fool!
[3] mu-gg
[4] What the hell!?
[5] Helium is needed for the balloon to float!!
[6] It's probably a disposable camera, right?
[7] It's so good at taking photos
[8] Ah, it was too cheap...
[9] Are you trying to take photos in space, or are you just taking pictures and throwing them away?
[10] What the hell!!
[11] Yes, theoretically it's possible, but...
[12] There are plenty of pictures of Earth on the internet, right?
[13] You wouldn't even take a picture just to take a picture, would you?
[14] Ah
[15] That's right...
--- seed 9 ---
[1] Only by collecting as many balloons as possible will they float, no matter how much air you put in them!
[2] This guy is obviously stupid
[3] mu-gg
[4] What the heck!?
[5] Helium is needed for the balloon to float!
[6] It's a disposable camera, basically
[7] It's really good at taking photos
[8] Ah, it was too cheap...
[9] Are you trying to take photos in space?!
[10] What the heck!
[11] Yes, technically it's possible, but...
[12] There are plenty of photos of the Earth on the internet, right?
[13] You wouldn't even bother taking a photo...
[14] Ah
[15] That's right...
```

**class-sys** · seed 7: 2948 ms, 359 prompt / 186 reply tokens · seed 8: 2899 ms, 359 prompt / 179 reply tokens · seed 9: 2850 ms, 359 prompt / 172 reply tokens

```text
--- seed 7 ---
[1] Just because you collect balloons, no matter how many you gather, they'll never float!
[2] He's definitely a fool.
[3] Mu-gg!
[4] What the hell!?
[5] Helium is needed for the balloon to float!
[6] It's a disposable camera, right?
[7] Wow, it's so good at taking photos!
[8] It was too cheap, so...
[9] Are you going to take pictures in space, or are you just going to take them and throw them away?
[10] What a waste of time.
[11] Yes, technically, it's possible, but...
[12] There are plenty of pictures of Earth on the internet, right?
[13] You'd better take pictures yourself, or else...
[14] Ah...
[15] That's right...
--- seed 8 ---
[1] Only by collecting as many balloons as possible will they float, no matter how much air you put in them!
[2] He's definitely a fool!
[3] mu-gg!
[4] What the hell!?
[5] You can't float a balloon without helium!
[6] It's a disposable camera, basically!
[7] Wow, it's so good at taking photos!
[8] Ah, it was too cheap...
[9] Can you take photos in space?!
[10] What a waste of time!
[11] Yes, theoretically it's possible, but...
[12] There are plenty of photos of Earth on the internet, right?
[13] You wouldn't even bother taking a picture if you didn't have to!
[14] Ah...
[15] That's right...
--- seed 9 ---
[1] Just because I collect balloons, no matter how many I gather, they'll never float!
[2] He's definitely a fool!
[3] Mu-guu
[4] What's the big deal!?
[5] Without helium, the balloon won't float!
[6] It's a disposable camera, right?
[7] So good at taking photos!
[8] Ah, it was too cheap...
[9] Can you even take a picture in space?
[10] What a waste of time!
[11] Yes, theoretically, it's possible, but...
[12] There are plenty of photos of Earth on the internet, right?
[13] Why would you take a picture yourself when you could just...
[14] Ah...
[15] That's right...
```

**class-ns-sys** · seed 7: 2661 ms, 300 prompt / 176 reply tokens · seed 8: 2615 ms, 300 prompt / 167 reply tokens · seed 9: 2690 ms, 300 prompt / 180 reply tokens

```text
--- seed 7 ---
[1] Just because I collect balloons, no matter how many I gather, they will never float!
[2] He's definitely a fool.
[3] Mu-guu
[4] What the hell!?
[5] Without helium, the balloon won't float!
[6] It's a disposable camera, right?
[7] So good at taking photos!
[8] Ah, it was too cheap...
[9] Can you take photos in space with film rolls?
[10] What a waste of time.
[11] Yes, technically it's possible, but...
[12] All the photos of Earth are already falling on the internet.
[13] Why would you take a photo yourself when you can just look at the ones on the internet?
[14] Ah.
[15] That's right...
--- seed 8 ---
[1] Only by collecting as many as possible, the balloons will never float!
[2] This guy is definitely a fool!
[3] mu-gg
[4] What the hell!?
[5] Helium is needed to make the balloon float!
[6] It's a disposable camera, right?
[7] Wow, it's so good at taking pictures!
[8] Ah, it was too cheap...
[9] Are you trying to take pictures in space?!
[10] What the hell!
[11] Yes, theoretically it's possible, but...
[12] There are plenty of photos of Earth on the internet, right?
[13] You'd better take pictures yourself, right?
[14] Ah
[15] That's right...
--- seed 9 ---
[1] Just because I collect balloons, no matter how many I gather, they will never float!
[2] This guy is definitely a fool!
[3] Mu-guu
[4] What's the big deal!?
[5] Without helium, the balloon won't float!
[6] It's a disposable camera, right?
[7] So good at taking photos!
[8] Ah, it was too cheap...
[9] Can you take photos in space with film rolls?!
[10] What a waste of time!
[11] Yes, theoretically, it's possible, but...
[12] All the photos of the Earth are falling onto the internet, right?
[13] Why would you take a picture yourself when you could just look at the internet?
[14] Ah
[15] That's right...
```

**class-inline-np** · seed 7: 2925 ms, 356 prompt / 182 reply tokens · seed 8: 2940 ms, 356 prompt / 182 reply tokens · seed 9: 3009 ms, 356 prompt / 189 reply tokens

```text
--- seed 7 ---
[1] "Just collecting balloons, no matter how many you gather, they won't float!"
[2] "He's definitely a fool!"
[3] "Mug!"
[4] "What the heck!?"
[5] "Helium is needed for the balloon to float!"
[6] "It's a disposable camera, right?"
[7] "Wow, it's so good!"
[8] "Oh, it was too cheap..."
[9] "Are you trying to take pictures in space?!"
[10] "What a shame!"
[11] "Yes, technically it's possible, but..."
[12] "There are plenty of photos of Earth on the internet, right?"
[13] "You wouldn't even bother taking a picture yourself..."
[14] "Ah, well..."
[15] "That's right..."
--- seed 8 ---
[1] "Just collecting balloons, no matter how many you gather, they won't float!"
[2] "He's definitely a fool!"
[3] "Mug!"
[4] "What's the point of that!?"
[5] "Helium is needed for the balloon to float!"
[6] "It's a disposable camera, right?"
[7] "Wow, it's so good!"
[8] "Oh, it was too cheap!"
[9] "Are you trying to take photos in space?!"
[10] "What a shame!"
[11] "Yes, technically it's possible, but..."
[12] "There are plenty of photos of Earth on the internet, right?"
[13] "Even if you don't take it yourself..."
[14] "Ah!"
[15] "That's right..."
--- seed 9 ---
[1] "Just collecting balloons, no matter how many I gather, they won't float!"
[2] "He's definitely a fool!"
[3] "Mu-gg!"
[4] "What's the point!?"
[5] "Helium is needed for the balloon to float!"
[6] "It's a disposable camera, right?"
[7] "Wow, so good!"
[8] "I'm so sorry, it was a bit expensive..."
[9] "Are you trying to take a photo in space?!"
[10] "What a shame!"
[11] "Yes, technically it's possible, but..."
[12] "There are plenty of photos of Earth on the internet, right?"
[13] "You wouldn't even bother taking a photo yourself..."
[14] "Ah, that's right..."
[15] "That's right..."
```


### bourei-p04, hand transcription, English

**none** · seed 7: 1644 ms, 182 prompt / 111 reply tokens · seed 8: 1616 ms, 182 prompt / 107 reply tokens · seed 9: 1839 ms, 182 prompt / 132 reply tokens

```text
--- seed 7 ---
[1] "I'm dead, aren't I?"
[2] "You mean 'visible' people, right?"
[3] "Welcome to the funeral home, good day!"
[4] "Eh?"
[5] "What about the makeup of the dead sister?"
[6] "Yes..."
[7] "Funeral director Uchigusa Funeral Service, Uchigusa Kusumokuro 25 years old"
[8] "The funeral requestor, Sakura Yume 19 years old"
--- seed 8 ---
[1] "I'm dead, aren't I?"
[2] "People you can see are like that, aren't they?"
[3] "Welcome to the funeral home, good luck!"
[4] "Eh?"
[5] "What about the makeup of the dead sister?"
[6] "Yes..."
[7] "Oroku Funeral Home President Oroku Kusumaru 25 years old"
[8] "The funeral requestor Sakurae Suzume 19 years old"
--- seed 9 ---
[1] "I'm dead, aren't I?"
[2] "You mean 'visible' people, right?"
[3] "Welcome to the funeral home of the dead, good day!"
[4] "Eh?"
[5] "Did you mean to ask about my sister's makeup after she died?"
[6] "Yes..."
[7] "The Funeral Director of Uchigusa Funeral Home, Uchigusa Kusumokuro 25 years old"
[8] "The Funeral Director of Uchigusa Funeral Home, Uchigusa Kusumokuro 19 years old"
```

**sys-ctl** · seed 7: 1581 ms, 172 prompt / 105 reply tokens · seed 8: 1476 ms, 172 prompt / 96 reply tokens · seed 9: 1593 ms, 172 prompt / 109 reply tokens

```text
--- seed 7 ---
[1] It's dead, you know.
[2] "Visible" people are like that.
[3] Welcome to the funeral home of the deceased, good day!
[4] ?
[5] The makeup of the deceased's sister?
[6] Yes...
[7] From the Uchigatsuno Funeral Home, Uchigatsuno Kusumori, 25 years old.
[8] The funeral home's requestor, Sakurae Suzuki, 19 years old.
--- seed 8 ---
[1] It's dead, you know.
[2] "Visible" people are like that.
[3] Welcome to the funeral home of the ghost, good day.
[4] ?
[5] The makeup of the dead sister?
[6] Yes...
[7] Uragami Funeral Home, Uragami Kusukuri, 25 years old.
[8] The funeral request person, Sakurae Suzuki, 19 years old.
--- seed 9 ---
[1] It's dead, you know.
[2] "Visible" people are like that.
[3] Welcome to the funeral home of the deceased, good day.
[4] What?
[5] Did you see the makeup on my sister's death?
[6] Yes...
[7] The head of the Uchigusa Funeral Home, Uchigusa Kusumori, 25 years old.
[8] The funeral home's requestor, Sakura Mizume, 19 years old.
```

**class-sys** · seed 7: 1722 ms, 243 prompt / 95 reply tokens · seed 8: 1801 ms, 243 prompt / 99 reply tokens · seed 9: 1759 ms, 243 prompt / 96 reply tokens

```text
--- seed 7 ---
[1] I'm dead.
[2] "Visible" people are like that.
[3] Welcome to the funeral home, Mr. Uchigusa, good luck.
[4] ?
[5] The makeup of the deceased aunt?
[6] Yes...
[7] Uchigusa Funeral Home President Uchigusa Kusumoru 25 years old
[8] Funeral requestee Sakurae Suzume 19 years old
--- seed 8 ---
[1] I'm dead, you know.
[2] "Visible" people like you.
[3] Welcome to the funeral home, Mr. Kusumura, good luck.
[4] ?
[5] The makeup of the deceased sister?
[6] Yes...
[7] Kusumura Funeral Home, Mr. Kusumura, 25 years old.
[8] The funeral requestor, Sakura Mizu, 19 years old.
--- seed 9 ---
[1] I'm dead.
[2] "Visible" people are like that.
[3] Welcome to the funeral home, Mr. Uchigusa, good luck.
[4] ?
[5] Did you mean my aunt's makeup?
[6] Yes...
[7] Uchigusa Funeral Home President Uchigusa Kusumoru 25 years old
[8] Funeral requestor Sakurae Suzume 19 years old
```

**class-ns-sys** · seed 7: 1613 ms, 220 prompt / 94 reply tokens · seed 8: 1659 ms, 220 prompt / 97 reply tokens · seed 9: 1656 ms, 220 prompt / 99 reply tokens

```text
--- seed 7 ---
[1] I'm dead.
[2] "Visible" people like that.
[3] Welcome to the funeral director's office, Mr. Kusumura, a very nice person.
[4] ?
[5] The makeup of the dead sister?
[6] Yes...
[7] Funeral director Kusumura, 25 years old.
[8] The person who requested the funeral service is Suzuki, 19 years old.
--- seed 8 ---
[1] I'm dead, aren't I?
[2] "Visible" people like you.
[3] Welcome to the funeral home, Mr. Kusumori, good day!
[4] ?
[5] Did you mean my aunt's makeup?
[6] Yes...
[7] Funeral home owner Kusumori Kusumori, 25 years old
[8] Funeral arrangement requestee Sakurae Suzuki, 19 years old
--- seed 9 ---
[1] I'm dead.
[2] "Visible" people like you.
[3] Welcome to the funeral director's office, Mr. Kusumori, good day.
[4] ?
[5] The makeup of my sister who died?
[6] Yes...
[7] Mr. Kusumori, the funeral director, 25 years old.
[8] The funeral director who requested the funeral, Sakura Mizu, 19 years old.
```

**class-inline-np** · seed 7: 1970 ms, 254 prompt / 115 reply tokens · seed 8: 1934 ms, 254 prompt / 113 reply tokens · seed 9: 1947 ms, 254 prompt / 115 reply tokens

```text
--- seed 7 ---
[1] "I'm dead, aren't I?"
[2] "People you can see are the kind of people."
[3] "Welcome to the funeral home of the ghost, good day!"
[4] "Eh?"
[5] "Did you mean my aunt who died?"
[6] "Yes..."
[7] "The funeral director of Uragam Funeral Home, Uragam Kusumakura, 25 years old."
[8] "The funeral requestor, Sakura Mizu, 19 years old."
--- seed 8 ---
[1] "I'm dead, aren't I?"
[2] "People you can see are the kind of people."
[3] "Welcome to the funeral home, good luck!"
[4] "Eh?"
[5] "What about the makeup of the dead sister?"
[6] "Yes..."
[7] "The funeral director of Uragam Funeral Home, Uragam Kusumakura, 25 years old."
[8] "The funeral request person, Sakura Mizu, 19 years old."
--- seed 9 ---
[1] "I'm dead, aren't I?"
[2] "You mean 'visible' people, right?"
[3] "Welcome to the funeral home, good day!"
[4] "Eh?"
[5] "Did you mean to ask about my sister's makeup?"
[6] "Yes..."
[7] "Funeral director Uchigusa Funeral Home, Uchigusa Kururin, 25 years old"
[8] "The funeral director requesting the service is Sakurae Suzuki, 19 years old."
```


### tojime-dense-action, hand transcription, English

**none** · seed 7: 2720 ms, 260 prompt / 169 reply tokens · seed 8: 2437 ms, 260 prompt / 155 reply tokens · seed 9: 2576 ms, 260 prompt / 172 reply tokens

```text
--- seed 7 ---
[1] Mafia licking my face is about to happen
[2] GG
[3] Ah
[4] Closing my eyes is my guard dog
[5] I'll see if I can get my brain out of the gun wound on your waist
[6] Ah?
[7] Ah, that's it...
[8] Stop it! This is not the place!
[9] PLAN
[10] Nn?
[11] You might be a girl too, right?
[12] Girl?
[13] Huh... Yes, but...
[14] Hoho
[15] Show me your face
[16] Ah?
[17] Closing my eyes, I can still be a good buyer...
[18] Just a little...!
--- seed 8 ---
[1] Mafia sucking mouth is almost all
[2] GG
[3] Ah
[4] Closing eyes are the dagger?
[5] Shot in the waist, see if you can open your brain?
[6] ?
[7] Ah, that's it...
[8] Stop, this is not a place for this!
[9] PLAN
[10] ?
[11] You too, maybe
[12] Girl?
[13] Oh, yes... but...
[14] Hoho
[15] Show me your face
[16] ?
[17] Closing eyes, even if you're not smart, buyers will still buy...
[18] Just a little...!
--- seed 9 ---
[1] Mafia sucking my cock is about as likely as it is to happen.
[2] GG
[3] Ah
[4] Closing my eyes is my guard dog.
[5] I'd like to see you shoot your gun into my brain.
[6] Ah?
[7] Ah, that's it...
[8] Stop it! You're not in this situation.
[9] PLAN
[10] Nn?
[11] You might be a girl too, right?
[12] Girl?
[13] Huh, yeah... but I'm not.
[14] Hoho
[15] Show me your face.
[16] Ah?
[17] Closing my eyes, I'm still a good customer.
[18] Just a little...!
```

**sys-ctl** · seed 7: 2294 ms, 250 prompt / 153 reply tokens · seed 8: 2635 ms, 250 prompt / 182 reply tokens · seed 9: 2343 ms, 250 prompt / 158 reply tokens

```text
--- seed 7 ---
[1] Mafia sucking is mostly done.
[2] Goo
[3] Ah
[4] Closing your eyes is your guard?
[5] Shot in the gut, what's the point of being shot in the head?
[6] ?
[7] Ah, that's it...
[8] Stop, you're too young!
[9] PLAN
[10] ?
[11] You're probably a girl, right?
[12] Girl?
[13] Uh, yeah, but...
[14] Hoho
[15] Show me your face
[16] ?
[17] Closing your eyes, you're still good...
[18] Just a little...!
--- seed 8 ---
[1] Mafia sucking mouth is basically all of it
[2] GG
[3] Ah
[4] Closing your eyes is your guard dog?
[5] I'll show you my brain with a bullet to the gut if you shoot it with a gun on my waist
[6] ?
[7] Ah, that's it...
[8] Stop it! You're not even in the right place!
[9] PLAN
[10] ?
[11] You might be a girl, right?
[12] No, I'm a boy
[13] Oh, yeah... but I'm not lying
[14] Huh
[15] Show me your face
[16] Oh
[17] Closing your eyes, you can still be a good purchase...
[18] Just a little bit...!
--- seed 9 ---
[1] Mafia licking my face is probably true
[2] GG
[3] Ah
[4] Closing my eyes is my guard?
[5] I'll show you my brain if you shoot me with my belt
[6] ?
[7] Ah, that's it...
[8] Stop it! This is too low!
[9] PLAN
[10] ?
[11] You're probably a girl, aren't you?
[12] Girl?
[13] Uh... Yes, but...
[14] Huh
[15] Show me your face
[16] Uh?
[17] Closing my eyes, I'm still a good guy...
[18] Just a little...!
```

**class-sys** · seed 7: 2758 ms, 361 prompt / 162 reply tokens · seed 8: 2720 ms, 361 prompt / 158 reply tokens · seed 9: 2727 ms, 361 prompt / 159 reply tokens

```text
--- seed 7 ---
[1] Mafia sucking my cock is about as common as it gets.
[2] GG
[3] Ah
[4] Eyes closed, you're the lookout?
[5] Shot in the gut, what's the point of having a brain?
[6] ?
[7] Ah, that's it...
[8] Stop it! This is too far!
[9] PLAN
[10] Nn?
[11] You too, maybe?
[12] Girl?
[13] Uh huh... you're right.
[14] Hoho
[15] Show me your face.
[16] Ah?
[17] Eyes closed, but you're smart enough to buy it...
[18] Just a little...!
--- seed 8 ---
[1] Mafia sucking my cock is probably the worst
[2] GG
[3] Ah
[4] Close your eyes, you're a spy?
[5] Shot in the gut, what do you want to see?
[6] ?
[7] Ah, that's it...
[8] Stop it! This is a bad place!
[9] PLAN
[10] ?
[11] You too, maybe
[12] Girl?
[13] Uh, yes... but...
[14] Huh
[15] Show me your face
[16] Ah
[17] Close your eyes, but you're still good enough to buy...
[18] Just a little more...!
--- seed 9 ---
[1] Mafia sucking mouth is probably not the case
[2] GG
[3] Ah
[4] Close your eyes, you're an assassin?
[5] Shot in the waist, let's see if the brain can survive
[6] ?
[7] Ah, that's it...
[8] Stop it! This place is too dangerous!
[9] PLAN
[10] ?
[11] You too, maybe
[12] Girl?
[13] Uh... yes, but...
[14] Huh
[15] Show me your face
[16] Ah
[17] Close your eyes, but you can still be a good purchase...
[18] Just a little...!
```

**class-ns-sys** · seed 7: 2490 ms, 293 prompt / 159 reply tokens · seed 8: 2510 ms, 293 prompt / 162 reply tokens · seed 9: 2460 ms, 293 prompt / 158 reply tokens

```text
--- seed 7 ---
[1] Mafia sucking is mostly done by the big ones.
[2] Goo
[3] Ah
[4] Closing your eyes is a spy.
[5] With a gun to the brain, let's see if you can survive the bullet.
[6] ?
[7] ?
[8] Stop, you're not in this place.
[9] PLAN
[10] ?
[11] You might be a girl, right?
[12] ?
[13] Uh-huh... I guess that's true.
[14] Hoho
[15] Show me your face.
[16] ?
[17] Closing your eyes, you can still be valuable.
[18] Just a little...!
--- seed 8 ---
[1] Mafia sucking my cock is pretty common
[2] GG
[3] Ah
[4] Closing your eyes is a vigilante
[5] I'll show you my brain with a gun to the neck
[6] ?
[7] Ah, that's it...
[8] Stop it! This is not a place for this!
[9] PLAN
[10] ?
[11] You might be a girl, right?
[12] Is that so?
[13] Ah, yes... but...
[14] Huh
[15] Show me your face
[16] Ah
[17] Even if you close your eyes, you have an IQ
[18] Just a little...!
--- seed 9 ---
[1] Mafia sucking my cock is about as common as it gets.
[2] GG
[3] Ah
[4] Closing your eyes is an assassin.
[5] I'm going to shoot your brain out with your hip.
[6] ?
[7] ?
[8] Stop, you're too young!
[9] PLAN
[10] ?
[11] You're probably a girl, aren't you?
[12] ?
[13] Yeah, but I'm not a girl.
[14] Huh
[15] Show me your face.
[16] ?
[17] Close your eyes, and you might be able to buy me.
[18] Just a little bit...!
```

**class-inline-np** · seed 7: 3005 ms, 356 prompt / 190 reply tokens · seed 8: 3213 ms, 356 prompt / 205 reply tokens · seed 9: 3211 ms, 356 prompt / 212 reply tokens

```text
--- seed 7 ---
[1] "I'm not going to let you lick my mafia."
[2] "Goo"
[3] "Ah"
[4] "Close your eyes, you're the lookout?"
[5] "With a gun to my brain, let's see if you can still shoot?"
[6] "Ah?"
[7] "Ah, that's it..."
[8] "Stop it! This isn't the place!"
[9] "Planner"
[10] "Nn?"
[11] "You too, maybe?"
[12] "Girl?"
[13] "Huh, yeah... but..."
[14] "Hoho"
[15] "Show me your face"
[16] "Ah?"
[17] "Close your eyes, but you're still good enough to buy..."
[18] "Yay, just a little bit..."
--- seed 8 ---
[1] "I'm going to lick the mafia's anus, but I'm not sure about that."
[2] "Goo"
[3] "Ah"
[4] "Close your eyes, you're the lookout?"
[5] "With a gun on my waist, let's see if I can get my brain out of it?"
[6] "Ah?"
[7] "Ah, that's it..."
[8] "Stop it! This isn't the place!"
[9] "Planner"
[10] "Nn?"
[11] "You too, maybe?"
[12] "Girl?"
[13] "Oh, yeah... but it's true..."
[14] "Hoo"
[15] "Show me your face"
[16] "Ah?"
[17] "Close your eyes, and if you have an ability, I'll buy you..."
[18] "Yaa, a little...!"
--- seed 9 ---
[1] "I'm not going to let a mafia lick my face, you hear me?"
[2] "Goo"
[3] "Ah"
[4] "Close your eyes, you're the lookout?"
[5] "With a gun to my brain, let's see if I can get my cock in your ass?"
[6] "Ah?"
[7] "Ah, that's it…"
[8] "Stop it! This isn't the place for this!"
[9] "Planner"
[10] "Nn?"
[11] "You too, maybe?"
[12] "Girl?"
[13] "Oh, yeah… but you're right, I guess…"
[14] "Huh"
[15] "Show me your face"
[16] "Ah"
[17] "Close your eyes, and you're a good buyer, even if you're blind"
[18] "Y-You're kidding, right?"
```

**gender-sys** · seed 7: 2780 ms, 343 prompt / 161 reply tokens · seed 8: 2599 ms, 343 prompt / 151 reply tokens · seed 9: 2779 ms, 343 prompt / 161 reply tokens

```text
--- seed 7 ---
[1] Mafia sucking is mostly done by the majority.
[2] GG
[3] Ah
[4] Closing your eyes is your guard dog.
[5] With a gun to the neck, let's see if you can survive the brain.
[6] Ah?
[7] Ah, that's it...
[8] Stop it! This is not the place!
[9] PLAN
[10] ?
[11] You too, maybe?
[12] Girl?
[13] Uh... Yes, but...
[14] Huh
[15] Show me your face
[16] Ah
[17] Even if you close your eyes, you're still valuable...
[18] Just a little...!
--- seed 8 ---
[1] Mafia sucking my cock is basically normal
[2] GG
[3] Ah
[4] Eyes closed, you're the dagger?
[5] Shot in the gut, what's the big deal?
[6] ?
[7] Ah, that…
[8] Stop it! This is too far!
[9] PLAN
[10] ?
[11] You too, maybe
[12] Girl?
[13] Uh huh... yeah, but...
[14] Huh
[15] Show me your face
[16] ?
[17] Eyes closed, but you have brains, I'll buy you
[18] Just a little...!
--- seed 9 ---
[1] Mafia sucking mouth is usually done
[2] GG
[3] Ah
[4] Eyes closed are the wise man's weapon?
[5] Shot in the waist, let's see if the brain is still alive?
[6] ?
[7] Ah, that's it...
[8] Stop it! This is not the place!
[9] PLAN
[10] ?
[11] You might be...
[12] Girl?
[13] Huh? Yes... but...
[14] Huh
[15] Show me your face
[16] ?
[17] Even if you close your eyes, if you have the talent, buyers will come...
[18] Just a little...!
```

**gender-inline-np** · seed 7: 3391 ms, 347 prompt / 186 reply tokens · seed 8: 3351 ms, 347 prompt / 167 reply tokens · seed 9: 3209 ms, 347 prompt / 172 reply tokens

```text
--- seed 7 ---
[1] It's normal for mafia to lick each other's ass.
[2] Goo
[3] Ah
[4] Are you the one who's careful?
[5] With the pistol on the waist, let's see if the brain is still intact.
[6] Ah?
[7] Ah, that's it...
[8] Stop it! This is too low!
[9] PLAN
[10] Nn?
[11] You too, maybe?
[12] You're a girl, huh... But it's true...
[13] Ah, yes... That's right...
[14] Hoo
[15] Show me your face
[16] Ah?
[17] With your eyes closed, even if you're not smart, you can still be bought...
[18] Ah, just a little...!
--- seed 8 ---
[1] It's normal to lick the mafia's face.
[2] GG
[3] Ah
[4] Are you the one who's being careful?
[5] With the gun on the waist, let's see if you can eat the brain.
[6] Ah?
[7] Ah, that's it...
[8] Stop it! This is too low!
[9] PLAN
[10] Nn?
[11] You too?
[12] Are you a girl?
[13] Oh, yes... but...
[14] Hoh
[15] Show me your face.
[16] Ah?
[17] With your eyes closed, you can still be a good quality.
[18] Just a little bit...!
--- seed 9 ---
[1] I'm going to lick the mafia's face.
[2] Goo
[3] Ah
[4] Are you a watchful mind?
[5] Shot in the waist, let's see what's in the brain?
[6] Ah?
[7] Ah, that's it...
[8] Female: Stop it! This is not the place!
[9] PLAN
[10] Nn?
[11] You too, maybe?
[12] You're a girl?
[13] Female: Oh, yes... but...
[14] Hoo
[15] Show me your face
[16] Ah?
[17] With closed eyes, one's ability is good, they can buy...
[18] Female: Just a little...!
```

**cont-sys** · seed 7: 2823 ms, 288 prompt / 169 reply tokens · seed 8: 2496 ms, 288 prompt / 160 reply tokens · seed 9: 2645 ms, 288 prompt / 177 reply tokens

```text
--- seed 7 ---
[1] Mafia sucking is mostly done by the big ones.
[2] GG
[3] Ah
[4] Closing your eyes is the brains of the operation.
[5] I'll see if you're still alive after being shot in the head with a gun.
[6] ?
[7] Ah, that's it...
[8] Stop it! This is not the place!
[9] PLAN
[10] ?
[11] Is it possible that you're also a girl?
[12] Girl?
[13] Huh, yeah... but...
[14] Hoho
[15] Show me your face.
[16] ?
[17] Closing your eyes, you can still be a good purchase...
[18] Just a little...!
--- seed 8 ---
[1] Mafia tasting mouth is about to be done.
[2] Goo
[3] Ah
[4] Closing eyes is the wise man?
[5] Shot in the waist, let's see if the brain is still there?
[6] ?
[7] Ah, that's it...
[8] Stop, stop, here!
[9] PLAN
[10] Nn?
[11] Is it possible that you too?
[12] Girl?
[13] Oh, yes... but...
[14] Hoho
[15] Show me your face
[16] ?
[17] Closing eyes, even if you're not smart, there are buyers...
[18] Just a little...!
--- seed 9 ---
[1] Mafia licking my face is probably the worst.
[2] Goo
[3] Ah
[4] Closing my eyes is a cautionary rod?
[5] With a gun to the neck, let's see what happens if you shoot my brain?
[6] ?
[7] Ah, that's it...
[8] Stop, stop! This is not the place!
[9] PLAN
[10] Nn?
[11] Is it possible that you too?
[12] Girl?
[13] Ehehe... Yes, but...
[14] Hoh
[15] Show me your face
[16] Ehehe
[17] Closing my eyes, even if I'm not smart enough, I can still buy someone...
[18] Yassss!
```

**cont-inline-np** · seed 7: 2496 ms, 309 prompt / 150 reply tokens · seed 8: 2634 ms, 309 prompt / 164 reply tokens · seed 9: 2509 ms, 309 prompt / 153 reply tokens

```text
--- seed 7 ---
[1] Mafia licking my ass is probably true
[2] Goo
[3] Ah
[4] Closing eyes is the wise man?
[5] Shot in the waist, what's the brain?
[6] ?
[7] Ah, that...
[8] Stop it! This is not the place!
[9] PLAN
[10] N?
[11] You too, maybe
[12] Are you a girl?
[13] Oh, yes... but...
[14] Hoho
[15] Show me your face
[16] Oh
[17] Closing eyes, the quality is good...
[18] Just a little...!
--- seed 8 ---
[1] Mafia sucking is not a big deal
[2] GG
[3] Ah
[4] Closing eyes is a vigilante
[5] Shot in the waist, brain in the chest, let's see if you can take it?
[6] ?
[7] Ah, that's it...
[8] Stop it! This is not the place!
[9] PLAN
[10] ?
[11] You might be a girl too
[12] Continue
[13] Oh, yes... but...
[14] Hoho
[15] Show me your face
[16] ?
[17] Closing eyes, even if you're not good, they'll buy you...
[18] Just a little...!
--- seed 9 ---
[1] Mafia sucking mouth is mostly done
[2] GG
[3] Ah
[4] Closing eyes is the guard dog?
[5] Shot in the waist, what's the brain? Let's see it?
[6] ?
[7] Ah, that...
[8] Stop, here!
[9] PLAN
[10] ?
[11] You too, maybe?
[12] Girl?
[13] Huh, yeah... but...
[14] Hoho
[15] Show me your face
[16] Huh?
[17] Closing eyes, even if you're not smart, you can still buy...
[18] Just a little...!
```



## Appendix E: register labels from Qwen and from an ending rule

Qwen2.5-1.5B labelled each page in one grammar-constrained pass (`root` pins `[id] polite|casual|rough|none` per line), with no system message and the app's sampler, seeds 7, 8 and 9. Its prompt: "For each Japanese manga line, say how formally the speaker talks: polite, casual or rough. Answer none for a line that is only a sound or a cry. Keep the [id] tag before each answer." followed by the `[id] text` lines. The ending rule is `ending_rule` in `exp/analyze.py`. "—" means no label.


**balloon-dense-dialogue, hand transcription**

| id | text | reference | ending rule | Qwen, seeds 7 / 8 / 9 |
|---|---|---|---|---|
| 1 | ただ空気いれた風船をいくら集めても浮くわけねーだろ!! | rough | rough | casual / casual / casual |
| 2 | こいつ絶対バカだし | rough | casual | rough / rough / rough |
| 3 | むぐっ | — | — | rough / rough / rough |
| 4 | なにあ然としてんの!? | casual | casual | polite / polite / casual |
| 5 | ヘリウム入れないと風船は浮かねーよ!! | rough | rough | casual / polite / casual |
| 6 | 大体なんで使い捨てカメラなんだよ! | rough | casual | casual / polite / casual |
| 7 | すご～くよく写るンズ | — | — | polite / polite / casual |
| 8 | あっ安かったので… | polite | — | polite / polite / casual |
| 9 | 宇宙でフィルム巻いてシャッターきる気か!? | rough | — | polite / polite / casual |
| 10 | ったく | rough | — | polite / polite / casual |
| 11 | 確かに原理的には可能かもしれないけどな… | casual | casual | polite / polite / casual |
| 12 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual | rough | casual / polite / casual |
| 13 | わざわざ自分で撮らなくたって… | casual | — | polite / polite / casual |
| 14 | ああ | — | — | polite / polite / casual |
| 15 | それはですね… | polite | polite | polite / polite / casual |

**bourei-p04, hand transcription**

| id | text | reference | ending rule | Qwen, seeds 7 / 8 / 9 |
|---|---|---|---|---|
| 1 | 死んでるんですよねぇ | polite | polite | casual / casual / casual |
| 2 | 「視える」人間ってヤツでして | polite | polite | polite / polite / polite |
| 3 | ようこそ亡霊葬儀屋さん 吉良いと | — | — | casual / casual / casual |
| 4 | え? | — | — | casual / casual / casual |
| 5 | 亡くなったお姉さんのお化粧を? | polite | — | polite / polite / polite |
| 6 | はい… | polite | — | casual / casual / casual |
| 7 | 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳 | — | — | polite / polite / polite |
| 8 | 葬儀依頼人 桜野 すずめ 19歳 | — | — | polite / polite / polite |

**tojime-dense-action, hand transcription**

| id | text | reference | ending rule | Qwen, seeds 7 / 8 / 9 |
|---|---|---|---|---|
| 1 | マフィア舐めんのも大概にしやがれ | rough | rough | rough / rough / rough |
| 2 | グッ | — | — | casual / casual / casual |
| 3 | あっ | — | — | casual / casual / casual |
| 4 | 閉じ眼が用心棒だ? | rough | casual | polite / polite / polite |
| 5 | 腰の銃でイかれた脳みそぶちまけてみるか? | rough | — | polite / casual / polite |
| 6 | あ? | rough | — | casual / casual / casual |
| 7 | あ、あの… | — | — | polite / polite / polite |
| 8 | ちょっとやめてよ!こんなとこで! | casual | casual | polite / polite / polite |
| 9 | プラーン | — | — | polite / polite / polite |
| 10 | ん? | — | — | casual / casual / casual |
| 11 | お前もしかして | rough | rough | polite / polite / polite |
| 12 | 女か? | rough | — | polite / polite / polite |
| 13 | えっはい…そうですけど… | polite | polite | polite / polite / polite |
| 14 | ほぉ | — | — | polite / polite / polite |
| 15 | 面見せてみろ | rough | rough | polite / polite / polite |
| 16 | えっ | — | — | casual / polite / casual |
| 17 | 閉じ眼でも器量が良けりゃ買い手は… | rough | — | polite / polite / polite |
| 18 | やっちょっと…! | casual | — | polite / polite / polite |

**balloon-dense-dialogue, real emulator OCR, gated**

| id | text | reference | ending rule | Qwen, seeds 7 / 8 / 9 |
|---|---|---|---|---|
| 10 | こいつ絶対バカだし | rough | casual | polite / polite / polite |
| 9 | むぐっ | — | — | rough / casual / rough |
| 4 | 大体なんで使い捨てカメラなんだよ! | rough | casual | casual / casual / casual |
| 1 | 地球の写真なんてネット上にいくらでも落ちてるだろ | casual | rough | polite / polite / polite |
| 11 | ったく | rough | — | casual / casual / casual |
| 2 | 確かに原理的には可能かもしれないけどな... | casual | casual | polite / polite / polite |
| 13 | なにあ然としてんの!?ヘリウム入れないと風船は浮かねーよ!! | rough | rough | casual / rough / casual |
| 5 | 宇宙でフィルム巻いてシャッターきる気か!? | rough | — | polite / polite / polite |
| 8 | あっ安かったので... | polite | — | polite / polite / polite |
| 7 | ああ | — | — | casual / casual / casual |
| 3 | わざわざ自分で撮らなくたって... | casual | — | polite / polite / polite |
| 6 | それはですね... | polite | polite | polite / polite / polite |

**bourei-p04, real emulator OCR, gated**

| id | text | reference | ending rule | Qwen, seeds 7 / 8 / 9 |
|---|---|---|---|---|
| 3 | 死んでるんですよねぇ | polite | polite | casual / casual / casual |
| 1 | 「視える」人間ってヤツでして | polite | polite | polite / polite / polite |
| 2 | 亡くなったお姉さんのお化粧を? | polite | — | casual / casual / casual |
| 7 | え? | — | — | rough / rough / rough |
| 4 | はい... | polite | — | polite / polite / polite |

**tojime-dense-action, real emulator OCR, gated**

| id | text | reference | ending rule | Qwen, seeds 7 / 8 / 9 |
|---|---|---|---|---|
| 5 | マフィア舐めんのも大概にしやがれ | rough | rough | rough / rough / rough |
| 1 | 腰の銃でイかれた脳みそぶちまけてみるか?あ? | rough | — | casual / casual / casual |
| 7 | お前もしかして | rough | rough | polite / polite / polite |
| 4 | ちょっとやめてよ!こんなとこで! | casual | casual | polite / polite / polite |
| 11 | あ、あの... | — | — | polite / polite / polite |
| 8 | 女か? | rough | — | polite / polite / polite |
| 3 | えっはい...そうですけど... | polite | polite | polite / polite / polite |
| 10 | あっ | — | — | casual / casual / casual |
| 9 | 面見せてみろ | rough | rough | polite / polite / polite |
| 6 | 閉じ眼が用心棒だ? | rough | casual | polite / polite / polite |
| 15 | ん? | — | — | polite / polite / polite |
| 2 | 閉じ眼でも器量が良けりゃ買い手は... | rough | — | polite / polite / polite |
| 12 | やっちょっと...! | casual | — | casual / casual / casual |
