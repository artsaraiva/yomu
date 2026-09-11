# `penalty_repeat` measured against the pre-registered gate (#153)

**Result: null, and worse than null on the harm side. `penaltyRepeat` stays at 1.0 (off).**

#139 pre-registered two bars before any number existed. `penalty_repeat = 1.1` ships only if
**both** hold:

1. **Benefit** — residue or non-translation moves in the good direction on the gate corpus, and
   readability does not regress by more than 0.05. A latency-only improvement does not qualify.
2. **No harm** — zero harm hits on the repetition probe.

It cleared neither. Residue and non-translation are byte-identical between the arms, and probe harm
rises monotonically with the penalty: **8 → 12 → 14** hits out of 17 scored bubbles at 1.0 / 1.1 /
1.2.

## Runs

`EngineBenchmarkTest#measureRepeatPenalty`, one model load per arm, Qwen2.5-1.5B Q4_K_M on the
shipped per-line `TRANSLATION_ONLY` path, seed pinned to 0 so the arms differ by the penalty alone.

- **Probe (#152):** 22 bubbles, arms 1.0 / 1.1 / 1.2.
- **Gate corpus:** the 17 ADR-0004 pages, arms 1.0 and 1.1.

Per-page and per-bubble timings for every arm are in `repeat-penalty-153-timing.csv`, so the
latency claims below are checkable without a re-run.

## Deviations from the pre-registered protocol

Three, all forced by the device, all recorded here rather than folded into the design:

1. **#153 says "reference phone". This ran on an arm64 emulator (`sdk_gphone16k_arm64`).** No phone
   was available. Everything that depends on the hardware — median ms/page, peak PSS — is this
   emulator's, and nothing here should be read as a phone measurement.
2. **#139 names #137's published `qwen_perline` row as the control; this run uses its own 1.0 arm
   instead.** That row was measured on the phone, and differencing 1.1-on-emulator against
   1.0-on-phone would put the device inside the delta. The paired comparison the gate asks for is
   preserved; the thing it is paired against changed. The phone row stays in the table beside it as
   a reference point, and the two are never subtracted.
3. **#153 asks for one gate arm at 1.1; two were run.** That is the direct cost of deviation 2 —
   the control had to be measured, not cited. It doubled the expensive run.

The emulator's absolute numbers happen to land beside the phone's — 3360 ms/page median against
3228, peak PSS 2073 MiB against 2074. That is worth knowing and is not load-bearing: no conclusion
below rests on it, and deviation 2 stands regardless of how close the two devices turned out to be.

## Gate corpus — paired delta, 1.1 against 1.0 on the same device

The Δ column is 1.1 − 1.0, both measured here. The last column is #137's phone row, printed for
reference and never subtracted from the others (deviation 2).

| | 1.0 (control) | 1.1 (candidate) | Δ | #137 `qwen_perline` (phone) |
| --- | --- | --- | --- | --- |
| Non-translation rate | 0.000 | 0.000 | 0.000 | 0.000 |
| Japanese-residue rate | 0.007 | 0.007 | 0.000 | 0.000 |
| Bubble coverage | 100% (147 ids) | 100% (147 ids) | — | 100% |
| Source echoes | 0 | 0 | 0 | — |
| Readability ratio | 1.034 | 1.027 | −0.007 | 1.016 |
| Mean bubble chrF2 | 27.89 | 28.15 | +0.26 | 27.40 |
| Median ms/page | 3360 | 3406 | +46 | 3228 (phone) |
| Peak PSS | 2073 MiB | 2075 MiB | +2 | 2074 MiB (phone) |

**Benefit clause: not met.** Neither gated failure rate moves at all. The single residue hit is the
same bubble in both arms — `烏丸葬儀社社長 烏丸枢（からすまくるる）　２５歳` on `bourei-p04`, where the
model romanises the name but leaves the 丸 — which is a tokenisation/name-handling failure with no
repetition in it for a repetition penalty to touch. chrF2 moves +0.26 and readability −0.007; both
are inside the noise this 147-bubble corpus can resolve, and neither is a gated metric.

**Coverage cross-check.** Coverage cannot fail here — `TranslationEngine` substitutes
`bubble.sourceText` when the slot returns nothing — so it is never reported alone (#137's warning).
`score_translation` now counts **source echoes**: an id whose output is its own Japanese source.
Both arms score 0, so the 100% is real on both. (The three punctuation-only bubbles — `...`, `?` —
are correctly passed through and are excluded by the CJK condition; without it the cross-check
would have a permanent floor of 3 and never read 0.)

## Repetition probe — harm rises monotonically

| Arm | Harm hits / 17 scored | Probe wall time |
| --- | --- | --- |
| 1.0 | 8 | 11.5 s |
| 1.1 | 12 | 5.7 s |
| 1.2 | 14 | 5.6 s |

Four bubbles flip from clean to harmed between 1.0 and 1.1, and two more between 1.1 and 1.2:

| id | source | reference run | 1.0 | 1.1 | 1.2 |
| --- | --- | --- | --- | --- | --- |
| 0 | `うおおおおおおおおお!?` | 4 | 5 | 4 | **2** |
| 2 | `はははははははははははっ` | 9 | 9 | 9 | **8** |
| 5 | `ふふっ` | 3 | 3 | **2** | **2** |
| 7 | `ふふっ` | 3 | 3 | **2** | **2** |
| 14 | `キヒヒッ` | 3 | 504 | **1** | **1** |
| 15 | `おおおおおおおおーー！！` | 20 | 255 | **1** | **1** |

This is the harm #139 built the probe to catch, and it is present at the candidate value.

**1.1 fails clause 2 as written: 12 hits against a pre-registered bar of zero.** That is the
verdict, and nothing below softens it.

**Separately — and this is a defect in the criterion, not a reading of the result — the bar of zero
was never reachable.** The control arm, no penalty at all, already scores 8 hits. Those are not
penalty damage: they are the model declining to reproduce a long scream at all (`あああああ` → one
`a`), which is a fact about Qwen2.5-1.5B rather than about the sampler. An absolute bar written
before any number existed turns out to measure the model. The right repair is a **paired** clause 2
— "no more harm hits than the control" — but that repair is proposed here, not applied: rewriting a
pre-registered bar after seeing the measurement is exactly what pre-registration exists to prevent,
and it would be worth nothing on a run that could be argued to have benefited from it. It happens to
change no outcome here (8 → 12 fails a paired bar too), which is the only reason it is safe to state
at all. Logged against #152's criterion for the next measurement that uses it, alongside the loop
asymmetry below.

**Two probe rows at 1.0 are runaway loops, not preserved repetition** — the probe's second criterion
defect. ids 14 and 15 score runs of
504 and 255 against references of 3 and 20 — the model looped to the token cap (`Oh no no no no…`).
The harm scorer only asks whether the output run is *shorter* than the reference's, so a loop reads
as "ok" and the penalty that kills the loop reads as harm on the same bubble. That is a real limit
of the #152 criterion on this arm; it does not change the verdict, because 1.1 harms four bubbles
that were neither loops nor clean-at-1.0 edge cases (ids 5, 7, and the two above). Worth recording
against #152 if the probe is ever reused: an upper bound belongs beside the lower one.

**The only thing the penalty improves is latency, which the gate explicitly excludes.** Probe wall
time halves (11.5 s → 5.7 s) because the runaway loops stop reaching the token cap. On the gate
corpus that improvement is worth nothing — per-line outputs there run a median of 19 characters and
never approach `MAX_TOKENS = 256` (#139), so there is no loop to prevent, and median ms/page does
not move.

## Decision

`penaltyRepeat` stays at **1.0**. `penaltyLastN = 64`, `penaltyFreq = 0`, `penaltyPresent = 0` stay
as they are: they are inert while the repeat penalty is off, and they are the window the next
candidate would use. The numbers above are recorded in the `GenerationParams` comment block so the
next reader of that default finds the measurement rather than an undocumented constant.

The value is not re-opened without a new job for it. If the architecture ticket flips the shipped
path to the page-level batch — where #137 measured residue bubbles and 5/17 token-cap loops — the
penalty acquires a real job on that path and this measurement must be re-run there, against a batch
control, before any value ships.
