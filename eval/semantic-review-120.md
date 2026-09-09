# Semantic review — Qwen2.5-1.5B page outputs

The output gate scores **formatting failure**: Japanese residue, non-translation, bubble coverage
(ADR-0004). Nothing in it looks at whether a translation says what the source said. This review
scores that second axis by hand and keeps it separate, so a gate PASS is never read as evidence
that the translation is right.

## Provenance

Reviewed material is the committed `eval/translation-quality/cases/<case>/actual/qwen25_1.5b.json`
from the #84 bakeoff. No new inference was run for this review. Numbers below come from
`run-eval.py --no-bubble` over those same files: 147 entries, 17/17 pages completed,
non-translation 0.000, Japanese residue 0.102 (15 entries — 13 of them the whole-page source
fallback on `tencho-p21`, plus one each on `bourei-sparse-single` and `tojime-p35`), coverage
100%, mean bubble chrF2 26.395.

The formatting gate therefore fails this engine on residue alone. Every error listed below is in a
bubble the gate passes.

## Error classes

Bubble ids are zero-based, matching `source.txt` line order.

### Proper names

The model transliterates rather than reads names, and sometimes drops them.

- `rasetugari-p19` [1] 在藤宏也 → "In Tōhōya" (reference: hiroya arifuji); [4] 華鏡丸 → "Kōkai Maru"
  (kakyomaru); [5] 在藤 → "Tōhōya".
- `bourei-p04` [5] 烏丸枢 → "Urakei Kurokuru" (kururu karasuma); [7] 桜野すずめ → "Sakurae Suzuki"
  (Suzume Sakurano); [1] 吉良いと → "good day" — the name is gone.
- `balloon-mixed-dialogue` [1] and [8] 相川仁 → "Aokita Shin" (Jin Aikawa).

A name is stable across a page and across a session, so a wrong reading is wrong in every bubble
that repeats it.

### Subject, agent, and possessor

- `rasetugari-p19` [3] お前は俺が地獄へ送ってやる → "You are going to send me to hell". The agent is
  reversed; the speaker is the one doing the sending.
- `bourei-p04` [4] 亡くなったお姉さんのお化粧を? → "What about my sister's makeup?" — the client's
  late sister becomes the speaker's.
- `balloon-dense-dialogue` [2] こいつ絶対バカだし → "This guy is a complete idiot" (reference: she's
  definitely stupid).
- `balloon-mixed-dialogue` [3] なのになぜ三流大の推薦なんかを狙う? → "Why would I even try to get a
  recommendation…" — a teacher's question about the student becomes the student's about himself.

### Polarity

- `balloon-dense-dialogue` [0] ただ空気いれた風船をいくら集めても浮くわけねーだろ!! → "Only by collecting
  as many balloons as possible will they float!" The negation is dropped and the line asserts the
  opposite of the joke it sets up, which bubble [5] then contradicts.
- `balloon-dense-dialogue` [13] わざわざ自分で撮らなくたって... → "Even if you take it yourself...".
- `balloon-mixed-dialogue` [4] 実に夢がない → "You actually have no dreams" reads as a flat verdict
  where the reference is a reproachful question.

### Detail loss and invention

- `balloon-dense-dialogue` [9] 宇宙でフィルム巻いてシャッターきる気か!? → "Are you trying to film in
  space with a roll of film and a shutter release?" — winding the film and pressing the shutter, the
  two manual actions the line is mocking, are collapsed into "film".
- `balloon-mixed-dialogue` [9] お前のようなかわいくない生徒には → "You're not a cute student like that,
  so you can rely on a lot of things" — the following bubble's clause is pulled forward and inverted.

## What this does not establish

One reviewer against OpenMantra's English annotation, which is itself a loose translation, not a
bilingual human rating and not a score. It says the classes of meaning error present in current
output; it does not measure how often they occur or rank engines. Adequacy ranking stays deferred
to #30's contrastive set and a possible future COMET-or-judge metric (ADR-0004). Formatting fixes —
#120's punctuation passthrough and clarification guard among them — move none of these lines.
