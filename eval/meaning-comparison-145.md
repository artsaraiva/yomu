# Qwen2.5-1.5B against CAT-Translate-1.4b on meaning (#145)

**Verdict: where CAT-Translate-1.4b translates at all, it is the more accurate translator — fewer
name errors, zero polarity inversions, half the detail loss. It also declines to translate 7 of the
same 42 bubbles, and the shipped grammar does not fix that.** The two findings point opposite ways
and neither is decided here; #145 was asked to produce the meaning comparison, not to move the
default.

This is the C1 route from the ticket: the `eval/semantic-review-120.md` taxonomy, applied by hand to
both engines on the same pages. **No new inference was run.**

## Provenance and comparability

Both arms are reference-phone (Samsung SM-S911B) runs of the #84 bake-off, on the bare id-keyed
page-level batch path, both `supportsIdKeyedBatch() = true`, scored against the same source and the
same OpenMantra reference:

| Arm | Run | Date |
| --- | --- | --- |
| `qwen25_1.5b` | `eval/benchmark-results/20260818-104743/` | 2026-08-18 (#72 phone confirmation) |
| `cat_translate_1.4b` | `eval/benchmark-results/20260817-143921/` | 2026-08-17 (#84 bake-off) |

The Qwen arm is the one `semantic-review-120.md` reviewed — every line that review quotes appears
verbatim in `20260818-104743` — so the Qwen column below is that review re-counted, not a second
opinion on different text. (The review cites a path, `cases/<case>/actual/qwen25_1.5b.json`, that no
longer exists; the surviving `qwen_batch.json` in that directory is a *different* arm and does not
match its quotes. Score against the run artifact, not that file.)

Same device, same call shape, same corpus, one day apart.

**Neither arm is in version control.** The ticket says the outputs are "already committed"; they are
not. `eval/benchmark-results/` and `eval/translation-quality/cases/*/actual/` are both gitignored
(`.gitignore:11,39`) — engine outputs are run artifacts on the machine that ran them, and a fresh
clone has none of this. That is why the full bubble-by-bubble evidence is transcribed into the
appendix at the bottom of this file: the scoring below stays checkable after the run directories are
gone. Re-running the unmodified ADR-0004 scorer over the two runs reproduces the published gate row:

| | `qwen25_1.5b` | `cat_translate_1.4b` |
| --- | --- | --- |
| Japanese-residue rate (gate 0) | 0.102 | 0.252 |
| Non-translation rate (gate 0) | 0.000 | 0.000 |
| Coverage (gate 100%) | 100% (147 ids) | 100% (147 ids) |
| Source echoes (never gated) | 15 (0.102) | **34 (0.231)** |
| Readability ratio (diagnostic) | 0.969 | 0.894 |

"Source echo" is the scorer's definition and nothing looser (`run_eval_lib.py:250`): the source line
carries CJK **and** the output equals it after stripping. Punctuation-only bubbles (`?`, `...`),
whose correct translation is the same string, are excluded — counting them would put a floor under
every engine. A hand count that skips the CJK test returns 36 for CAT and 18 for Qwen; the numbers
here are the scorer's.

Echo and residue are not the same measure and do not have to agree: **Japanese residue** (CONTEXT.md)
fires on Japanese characters anywhere in an output, a source echo only on an output that equals its
source exactly. CAT's 0.252 residue against 0.231 echo is the gap — a handful of bubbles where it
translated part of the line and left the rest in Japanese.

## Scope

The four pages `semantic-review-120.md` covers — `rasetugari-p19`, `bourei-p04`,
`balloon-dense-dialogue`, `balloon-mixed-dialogue` — **42 bubbles**, scored bubble-by-bubble for both
engines against the English reference, in the review's **four** classes (proper names; subject,
agent and possessor; polarity; detail loss and invention). One column is added:

- **Japanese residue** — the bubble came back as its Japanese source, verbatim. CONTEXT.md's term,
  used deliberately: it is not a meaning error in the taxonomy's sense but the absence of a
  translation, and folding it into "detail loss" would flatter the engine that produces it.

Every mark is in the appendix, bubble by bubble, so any of them can be disputed individually. One
deviates from `semantic-review-120.md`: that review files `balloon-mixed-dialogue` [4]
`実に夢がない` under **polarity**; here it is **detail loss** for Qwen, because the error is a
reproachful question flattened into a verdict rather than a dropped negation. Move it back and Qwen
reads 3 polarity / 11 detail; nothing in the verdict turns on it.

Scoring is one reviewer against OpenMantra's loose English annotation. The same caveat
`semantic-review-120.md` carries applies in full: this says which classes of error each engine makes
and how often on these pages. It is not a bilingual human rating, and 42 bubbles is not a corpus.

## Per-class counts

| Class | Qwen2.5-1.5B | CAT-Translate-1.4b |
| --- | --- | --- |
| Proper names | **8** | 4 |
| Subject / agent / possessor | 7 | 6 |
| Polarity | **2** | **0** |
| Detail loss and invention | **12** | 6 |
| Japanese residue (added column) | **0** | **7** |
| **Total error marks** | **29** | **23** |
| Bubbles with ≥ 1 error | 26 / 42 | 23 / 42 |
| Clean bubbles | 16 / 42 (38%) | 19 / 42 (45%) |
| Clean among bubbles it *did* translate | 16 / 42 (38%) | **19 / 35 (54%)** |

Per page:

| Page | Bubbles | Qwen (names/subj/pol/detail/residue) | CAT (names/subj/pol/detail/residue) |
| --- | --- | --- | --- |
| `rasetugari-p19` | 6 | 3 / 2 / 0 / 0 / 0 = 5 | 2 / 0 / 0 / 1 / 1 = 4 |
| `bourei-p04` | 8 | 3 / 3 / 0 / 4 / 0 = 10 | 2 / 2 / 0 / 1 / 0 = 5 |
| `balloon-dense-dialogue` | 16 | 0 / 1 / 2 / 4 / 0 = 7 | 0 / 3 / 0 / 3 / 0 = 6 |
| `balloon-mixed-dialogue` | 12 | 2 / 1 / 0 / 4 / 0 = 7 | 0 / 1 / 0 / 1 / 6 = 8 |

## Where the difference is

**Polarity — CAT 0, Qwen 2, and both of Qwen's are load-bearing.**
`balloon-dense-dialogue` [0] `ただ空気いれた風船をいくら集めても浮くわけねーだろ!!` is the joke the
page is built on. Qwen: *"Only by collecting as many balloons as possible will they float!"* —
the negation is gone and the line asserts the opposite of what bubble [5] then contradicts. CAT:
*"Just putting a balloon filled with air won't make it float, you know!!"* — negation intact; it
loses `いくら集めても` (however many you collect), scored as detail loss. Same page, [13]
`わざわざ自分で撮らなくたって...`: Qwen *"Even if you take it yourself..."* drops the negation; CAT
*"I didn't want to shoot it myself.."* keeps it and flips the subject instead.

**Proper names — CAT 4, Qwen 8.** CAT gets surnames and name order right where Qwen does not:
`烏丸枢` → CAT *"Karasuma Suguru"* (surname right, given name is the on-reading) against Qwen
*"Urakei Kurokuru"* (both wrong); `在藤宏也` → CAT *"In Fuji Hiroya"* against Qwen *"In Tōhōya"*.
Qwen also loses `吉良いと` outright, rendering it *"good day"*. CAT is not clean here either — it
loses the whole `葬儀依頼人 桜野すずめ １９歳` caption on `bourei-p04` [7], returning *"What?"*, and
it leaves `華鏡丸!!` and `相川仁` in Japanese rather than reading them, counted as residue.

**Detail loss — CAT 6, Qwen 12.** The sharpest instance is the one
`semantic-review-120.md` already isolated. `balloon-dense-dialogue` [9]
`宇宙でフィルム巻いてシャッターきる気か!?` mocks two manual actions. CAT: *"Are you trying to roll the
film and pull the shutter in space?"* — both present. Qwen: *"…with a roll of film and a shutter
release?"* — collapsed into one noun phrase. Qwen also invents: [9] on `balloon-mixed-dialogue`
pulls the *following* bubble's clause forward and inverts it.

**Subject / agent — near parity, 7 against 6.** Qwen reverses the agent on `rasetugari-p19` [3]
(*"You are going to send me to hell"*, CAT: *"I will send you to hell"*) and flips the possessor on
`bourei-p04` [4] (the client's late sister becomes the speaker's). CAT's are milder and more
uniform: it defaults to a third-person subject where Japanese omits one (*"They are dead, aren't
they?"*, *"Why are they using disposable cameras anyway?"*), which is wrong but not reversed.

**Japanese residue — CAT 7, Qwen 0, and this is the finding that cuts the other way.** Six of CAT's
seven are on one page: `balloon-mixed-dialogue` comes back with bubbles 0, 1, 2, 6, 7 and 8 as
verbatim Japanese and 3, 4, 5, 9, 10 in English. The seventh is `華鏡丸!!` on `rasetugari-p19`.

## The residue premise does not survive

The ticket's sharp edge is that residue "is precisely the axis a grammar makes structurally
unreachable (#135)", so the bake-off may have rejected CAT on a moot axis. On the committed data it
does not hold, in either engine's favour.

**Qwen's residue was mostly the structural class, and the grammar did remove it.** Of Qwen's 15
scored residue bubbles, 13 are the single whole-page fallback on `tencho-p21` — the malformed-id
failure ADR-0013 names. Under the shipped grammar arm that page is clean and the residue rate is
**0.014** (ADR-0013's #137 table; the arm's own outputs are the gitignored
`cases/*/actual/qwen_batch_grammar.json`). The grammar did exactly what #135 promised.

**CAT's residue is a different class and the grammar cannot reach it.** Its 34 source echoes are
spread across 10 of 17 pages and sit *mid-page*, interleaved with translated bubbles on the same
call — 6 of 12 on `balloon-mixed-dialogue`, 8 of 13 on `tencho-p21`, a trailing tail of 5 on
`bourei-dense-emotional`, all 7 on `tencho-p33`. Nothing there is a malformed id tag; the ids parsed
and the model returned the source as the translation.

ADR-0013 already states the consequence, for the 0.8b sibling and for the same reason:

> forcing the id shape on a model that echoes its source produces well-formed garbage, and residue
> is the only gate that would catch it

and, in the same ADR: *"The grammar constrains **shape, not content**."* That first line is written
about the **0.8b** sibling; it is extended to the 1.4b here by analogy, on the strength of the same
observed behaviour — 34 bubble-level source echoes — and not by anything ADR-0013 says about the
1.4b directly. A GBNF that fixes the JSON
envelope does not ban Japanese characters inside a `line`, and one that did would force a copying
model to emit Latin characters — transliteration or invention, not a translation, and invisible to
the residue gate that currently catches it. So the bake-off did **not** reject CAT-1.4b on an axis
about to become moot: it rejected it on the one axis the shipped architecture leaves standing.

## Repetition fidelity — not measured, and not measurable at C1 cost

The #139 follow-up asks for the #152 repetition probe on both arms, costing it at "~7 seconds per
arm against the corpus run's minutes". That costing is right about the *decode* and wrong about the
*arm*: this ticket runs no arms. C1's whole premise is that both engines' outputs are already on
disk, so there is no loaded model to spend seven seconds on. Standing a CAT-1.4b probe arm up means
fetching the 931 MB GGUF, installing it, and running
`EngineBenchmarkTest#measureRepeatPenalty` on a device — new inference, which the ticket's own "Do
not" list rules out of C1.

So the probe is deferred, not dismissed, and the disagreement is with the seven-second figure's
assumption rather than with the ask. Qwen's floor of **8 harm hits of 17 scored bubbles** at
`penalty_repeat = 1.0` stands alone; it is not a delta against CAT, and must not be read as one.

One zero-inference data point exists, and it is one bubble. `balloon-p28` [0]
`あああっあいかわしゃん...` (reference *"AAA Aikawa- shan..."*) is the corpus's only character-run
source outside the probe. Qwen: *"Ahhh! I can't believe it!"*; CAT: *"Aah, that's so cute..."*. Both
compress the run and both drop the name. That is a coin flip, not a measurement.

**Cost to close it properly:** one focused `run-benchmark.sh` pass with `challengers=cat_translate_1.4b`
against the probe corpus — minutes of device time, no new code. Worth opening only if CAT is still a
live candidate after the architecture call.

## Recommendation on COMET

**Yes — open it, but not to settle this.**

C1 answered the question it was given and did not come out ambiguous: 29 error marks against 23, with
CAT ahead on four of the five columns and clean on 54% of what it translates against Qwen's 38%. C2 (the
GEMBA-MQM judge pass) would spend real money re-deciding a question that a hand count already
decided in one direction — skip it.

What C1 cannot do is track this over time. It is one reviewer, four pages, 42 bubbles, and it has to
be redone by hand for every prompt change, every sampler knob, every model swap. Every ticket that
has touched translation quality since #84 — #119's prompt modes, #137's grammar arms, #153's
`repeat_penalty` — has had to argue about meaning from residue and chrF2 because no continuous
adequacy metric exists. #141 costed doc-level COMET at roughly one developer-day with no blockers.
That is the right reason to build it: a metric that runs on every arm without a reviewer, not a
tiebreak for a comparison that is not tied.

## What this does not decide

- **Not a default flip.** ADR-0010 set the current default and demoted CAT to the storage floor;
  nothing here amends it. The two engines are not running the same call shape: CAT-1.4b's 34
  mid-page source echoes are a content failure the grammar does not repair, and the shipped path is
  grammar-constrained batch (ADR-0013). Any default move needs CAT re-measured under the shipped
  architecture first, and ADR-0013 explicitly declines to force the id shape on a source-echoing
  model.
- **Not a rehabilitation of residue as a quality metric.** ADR-0004 stands: residue is a rare-event
  failure detector. What changed here is the reading of *CAT's* residue specifically — it is not a
  formatting artifact waiting for a grammar, it is the model declining to translate.
- **Not a ranking of the two on repetition.** See above.

## Reproduce

Both run directories are gitignored, so this needs the machine that holds them:

```sh
# Re-score both arms with the unmodified ADR-0004 scorer (zero inference):
#   copy eval/ to a scratch dir, clear cases/*/actual/*.json, then for each case copy
#   benchmark-results/20260818-104743/.../qwen25_1.5b.json and
#   benchmark-results/20260817-143921/.../cat_translate_1.4b.json into actual/
eval/.venv/bin/python eval/run-eval.py --no-bubble
```

`mean_chrf` comes back null without sacrebleu (`eval/README.md`); no number here depends on it.

The per-bubble scoring needs nothing but the appendix below, which carries the source, the reference
and both engines' output for all 42 bubbles.

## Feeds

- **#84** — its CAT-1.4b rejection is upheld, on a firmer reason than the one it gave. "Outclassed
  by the general 1.5B model" is not what the meaning data shows; "returns the source verbatim on
  23% of the corpus (34 of 147 bubbles), and the shipped grammar does not fix that" is.
- **#141** — the adequacy-evidence gap it opened is now partly filled by hand, and its COMET
  costing is the recommended next step for the same reason it gave.
- **#153 / #152** — the repetition probe stays a Qwen-only absolute number; a CAT arm is one focused
  device pass whenever it is wanted.
- **ADR-0010** — untouched. It set Qwen2.5-1.5B as the default and CAT as the low-storage floor;
  this is evidence for a future revisit, not a revisit.
- **ADR-0013** — its "grammar constrains shape, not content" line and its refusal to re-measure a
  source-echoing model under a grammar are load-bearing here, applied to the 1.4b for the first time.

## Appendix — every scored bubble

Both run directories are gitignored, so the evidence is transcribed here. Marks:
**N** proper name · **S** subject/agent/possessor · **P** polarity · **D** detail loss or invention ·
**R** Japanese residue. A bubble can carry more than one. `✓` is no mark, not a claim of perfection.

### `rasetugari-p19`

| # | Source | Reference | Qwen2.5-1.5B | CAT-Translate-1.4b |
| --- | --- | --- | --- | --- |
| 0 | 羅刹狩り討伐士 | demon hunter | Demon Hunter ✓ | Knife wielding hunter exterminator **[D]** |
| 1 | 在藤宏也 | hiroya arifuji | In Tōhōya **[N]** | In Fuji Hiroya **[N]** |
| 2 | 十年待った家族の仇だ | i have waited 10 years to wreak vengeance on you | The family's revenge for ten years **[S]** | I waited for ten years to avenge my family. ✓ |
| 3 | お前は俺が地獄へ送ってやる | I'll send you to hell | You are going to send me to hell **[S]** | I will send you to hell. ✓ |
| 4 | 華鏡丸!! | kakyomaru!! | Kōkai Maru!! **[N]** | 華鏡丸!! **[R]** |
| 5 | 在藤...? | arifuji..? | Tōhōya... **[N]** | Fuji..? **[N]** |

N/S/P/D/R — Qwen 3/2/0/0/0 = **5**; CAT 2/0/0/1/1 = **4**

### `bourei-p04`

| # | Source | Reference | Qwen2.5-1.5B | CAT-Translate-1.4b |
| --- | --- | --- | --- | --- |
| 0 | 死んでるんですよねぇ | you're already dead ... | It's dead. **[S]** | They are dead, aren't they? **[S]** |
| 1 | ようこそ 亡霊葬儀屋さん 吉良いと | Welcome,  ghost undertaker @Kilightit | Welcome to the funeral service company, good day. **[N D]** | Welcome to the ghost funeral service, Yoshii-to. **[N]** |
| 2 | 「視える」人間ってヤツでして | ... am one of those who can "see" | "Visible" people are like that. **[S]** | They are the kind of person who can see. **[S]** |
| 3 | え? | Hmm | ? **[D]** | What? ✓ |
| 4 | 亡くなったお姉さんのお化粧を? | you want to put on your late sister's make up? | What about my sister's makeup? **[S D]** | Did you do the makeup for the sister who passed away? ✓ |
| 5 | 烏丸葬儀社社長 烏丸枢（からすまくるる）　２５歳 | karasuma undertaking ceo kururu karasuma (25) | From the Funeral Service Company, Urakei Kurokuru (Urakei Kurokuru), 25 years old **[N D]** | The president of Karasuma Funeral Company, Karasuma Suguru, age 25. **[N]** |
| 6 | はい... | yes... | Yes... ✓ | Yes... ✓ |
| 7 | 葬儀依頼人 桜野　すずめ　１９歳 | Client: Suzume Sakurano (19) | The funeral request person, Sakurae Suzuki, 19 years old. **[N]** | What? **[D]** |

N/S/P/D/R — Qwen 3/3/0/4/0 = **10**; CAT 2/2/0/1/0 = **5**

### `balloon-dense-dialogue`

| # | Source | Reference | Qwen2.5-1.5B | CAT-Translate-1.4b |
| --- | --- | --- | --- | --- |
| 0 | ただ空気いれた風船をいくら集めても浮くわけねーだろ!! | even millions of balloons won't float if they're inflated with air!! | Only by collecting as many balloons as possible will they float! **[P]** | Just putting a balloon filled with air won't make it float, you know!! **[D]** |
| 1 | むぐっ | mm hmm | Muuuggh ✓ | Mmm. ✓ |
| 2 | こいつ絶対バカだし | she's definitely stupid | This guy is a complete idiot **[S]** | This guy is definitely an idiot. **[S]** |
| 3 | .......? | ...... ? | ....... ✓ | ..? ✓ |
| 4 | なにあ然としてんの!? | how can you look so surprised? | What the hell!? **[D]** | What the heck are you acting like? **[D]** |
| 5 | ヘリウム入れないと風船は浮かねーよ!! | If you don't add helium gas, the balloons won't float! | Without helium, the balloons won't float! ✓ | You need a helium tank to make a balloon float!! **[D]** |
| 6 | 大体なんで使い捨てカメラなんだよ! | besides, why are you using a disposable camera? | It's a disposable camera, right? **[D]** | Why are they using disposable cameras anyway? **[S]** |
| 7 | あっ | ah! | Ah ✓ | Oh ✓ |
| 8 | 安かったので... | since it's cheap ... | It was cheap... ✓ | It was cheap, so.. ✓ |
| 9 | 宇宙でフィルム巻いてシャッターきる気か!? | who's gonna wind up the film and press the shutter in space?! | Are you trying to film in space with a roll of film and a shutter release? **[D]** | Are you trying to roll the film and pull the shutter in space? ✓ |
| 10 | ったく | come on... | Feh ✓ | darn it ✓ |
| 11 | 確かに原理的には可能かもしれないけどな... | maybe it's possible in theory, but ... | It's possible, technically, but... ✓ | Certainly it may be possible in theory, but.. ✓ |
| 12 | 地球の写真なんてネット上にいくらでも落ちてるだろ | there are plenty of pictures of earth on the Internet. | There are tons of photos of Earth on the internet, right? ✓ | There are plenty of photos of the Earth on the internet. ✓ |
| 13 | わざわざ自分で撮らなくたって... | there's no need to take on e yourself. | Even if you take it yourself... **[P]** | I didn't want to shoot it myself.. **[S]** |
| 14 | ああ | Oh | Ah ✓ | Yeah. ✓ |
| 15 | それはですね... | well, that ... | That's right... **[D]** | That's because.. ✓ |

N/S/P/D/R — Qwen 0/1/2/4/0 = **7**; CAT 0/3/0/3/0 = **6**

### `balloon-mixed-dialogue`

| # | Source | Reference | Qwen2.5-1.5B | CAT-Translate-1.4b |
| --- | --- | --- | --- | --- |
| 0 | 十年後ーー夏 | 10 years later  summer | Ten years later - Summer ✓ | 十年後ーー夏 **[R]** |
| 1 | 相川仁 | Jin Aikawa, | Aokita Shin **[N]** | 相川仁 **[R]** |
| 2 | お前には一流の大学を目指す学力がある | you have the academic ability to enter those top universities. | You have the ability to go to a first-rate university ✓ | お前には一流の大学を目指す学力がある **[R]** |
| 3 | なのになぜ三流大の推薦なんかを狙う? | why get recommendation letters for a third tiered college? | Why would I even try to get a recommendation from a third-rate big school? **[S]** | Why is that? **[D]** |
| 4 | 実に夢がない | don't you have aspirations? | You actually have no dreams **[D]** | Really has no dreams. **[S]** |
| 5 | 夢...?くだらない | aspirations? nonsense. | Dream...? Not important **[D]** | Dream..? What a joke. ✓ |
| 6 | 楽で安全な道があるならそちらを選ぶべきでしょう | It's better to choose an easier and safer path | There's a safer and more enjoyable path, so choose that **[D]** | 楽で安全な道があるならそちらを選ぶべきでしょう **[R]** |
| 7 | 俺間違ってます? | am i wrong? | I'm wrong? ✓ | 俺間違ってます? **[R]** |
| 8 | いいぞぉ相川... | great, Aikawa. | Good job, Aokita... **[N]** | いいぞぉ相川... **[R]** |
| 9 | お前のようなかわいくない生徒には | saucy students like you | You're not a cute student like that, so you can rely on a lot of things **[D]** | Students like you don't look cute, do they? ✓ |
| 10 | 色々とモノを頼みやすい | are more easy to ask a favor of. | You're easy to rely on ✓ | It's easy to ask them for various things. ✓ |
| 11 | ? | ? | ? ✓ | ? ✓ |

N/S/P/D/R — Qwen 2/1/0/4/0 = **7**; CAT 0/1/0/1/6 = **8**

### Totals

42 bubbles.

| | N | S | P | D | R | Total | Bubbles with ≥1 mark | Clean |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Qwen2.5-1.5B | 8 | 7 | 2 | 12 | 0 | 29 | 26 / 42 | 16 / 42 |
| CAT-Translate-1.4b | 4 | 6 | 0 | 6 | 7 | 23 | 23 / 42 | 19 / 42 |
