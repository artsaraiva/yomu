# Yomu evaluation datasets

Curated, real-world manga cases used to measure two things that must not regress:

1. **Bubble detection containment** — every readable text box must reach OCR whole. A missed or
   truncated bubble breaks the reading experience. No absolute pass bar is set; the numbers rank
   detectors rather than pass or fail them (ADR-0003), and they rank them only coarsely — see
   [What this case set can and cannot decide](#what-this-case-set-can-and-cannot-decide).
2. **Translation quality** — annotation Japanese must become English through one
   page-level call, matched to the reference by bubble id (ADR-0004). The gates catch
   failure modes — non-translation, Japanese residue, missing bubbles — not quality.

These datasets are the source of truth for judging detector and translation-engine
changes. Do not tune thresholds or swap engines without checking against them.

## Layout

```
eval/
├── README.md
├── SCHEMA.md                # Run record contract: manifest, records.jsonl, COMPLETE, validation
├── eval-contract.json       # Machine-readable metric registry, hashed into every run manifest
├── run-eval.py              # Phase 1 eval harness CLI
├── run_eval_lib.py          # Scoring logic
├── run_records.py           # Manifest writer + run-record validator
├── generate-cases.py        # Build gate cases from vendor/OpenMantra
├── generate-repetition-probe.py  # Build the repetition probe from vendor/OpenMantra
├── benchmark-results/<run-id>/   # One run: manifest.json + records.jsonl + COMPLETE (gitignored)
├── bubble-detection/
│   ├── SCHEMA.md
│   └── cases/<case-id>/     # page.jpg + expected.json
├── translation-quality/
│   ├── SCHEMA.md
│   └── cases/<case-id>/     # page.jpg + source.txt + reference.txt
└── repetition-probe/        # Targeted probe, reported beside the gate, never gated (#152)
    ├── SCHEMA.md
    └── bubbles.json         # Generated, gitignored
```

**Engine output is never written into the case directories.** Every run's outputs stay in the
timestamped run directory they were produced in and are scored there. That is what makes #58 — a
skipped engine scored against a prior run's leftovers — structurally impossible rather than merely
unlikely. See [SCHEMA.md](SCHEMA.md).

## Populating the dataset

The OpenMantra dataset is vendored, not committed:

```bash
git clone https://github.com/mantra-inc/open-mantra-dataset.git \
  vendor/open-mantra-dataset
python3 eval/generate-cases.py
python3 eval/generate-repetition-probe.py
```

`vendor/` and the copied `page.jpg` files are gitignored. Cases are regenerated
from the vendored annotations so the repository only carries the harness and
small derived metadata.

## Running the harness

`run_eval_lib.py` scores chrF2 through sacrebleu, so the harness needs the dependencies in
`eval/requirements.txt`. Create the virtualenv once (it is gitignored):

```bash
python3 -m venv eval/.venv
eval/.venv/bin/pip install -r eval/requirements.txt
```

Then run the harness and the scorer tests:

```bash
eval/.venv/bin/python eval/run-eval.py --stub
eval/.venv/bin/python -m pytest eval -q
```

Without sacrebleu the harness still runs and every gate metric is still scored, but `mean_chrf` is
null and the chrF test fails — that is a missing dependency, not a regression.

`--stub` runs the scoring logic with synthetic perfect outputs and needs no device. For real engine
numbers, score a run directory produced by `run-benchmark.sh`:

```bash
eval/.venv/bin/python eval/run-eval.py --run-dir eval/benchmark-results/<run-id>
```

It exits nonzero when any requested arm is invalid, after reporting every valid arm.

## One-command benchmark runner

Use the script below to run the full on-device benchmark flow (instrumentation,
artifact pull, and scoring):

```bash
./eval/run-benchmark.sh
```

### Prerequisites

- Android device/emulator connected and visible to `adb`
- `adb` in `PATH` (Android platform-tools)
- Executable Gradle wrapper at `./gradlew`
- A Python interpreter with `eval/requirements.txt` installed

If any prerequisite is missing, the script fails early with an actionable error.

### Which interpreter scores the run

`run-benchmark.sh` scores with `$PYTHON_BIN` if set, otherwise `eval/.venv/bin/python`, otherwise
`python3`. It then checks that interpreter can import `sacrebleu` and **stops** if it cannot.

That check is not pedantry. Without sacrebleu, `run_eval_lib.py` falls through its `except
ImportError` to `CHRF = None` and every run records `mean_chrf: null` — which reads identically to
"no bubbles were scored". chrF is a registered metric in `eval-contract.json`, so a silent null is
the contract measuring nothing (#156). Every `mean_chrf` recorded before this check was null for
that reason, not low; the other gate metrics never depended on sacrebleu and are unaffected.

### What the script does

`run-benchmark.sh` executes numbered progress steps with elapsed time:

1. prerequisites
2. build
3. install
4. plan the run (write `manifest.json`)
5. device test
6. extract run records (`adb exec-out run-as … tar`, then write `COMPLETE`)
7. score results
8. summary

It streams instrumentation/eval output to the terminal and writes a persistent
log for the run.

### Output layout

Each run creates a unique timestamped directory (never overwrites prior runs):

```text
eval/benchmark-results/<run-id>/
├── manifest.json          # what this run was planned to produce, written before the device ran
├── records.jsonl          # what the device actually did, one record per arm/case/stage
├── COMPLETE               # record count + records SHA-256 + terminal arm statuses
├── benchmark.log          # full combined run log
├── logcat.log             # diagnostic only; the scorer never reads it
├── run-eval-output.log    # eval script stdout
└── scored-results.json    # copied score JSON from run-eval.py
```

Records are extracted from the app's own files directory with
`adb exec-out run-as com.yomu.app tar c -C files yomu-benchmark/<run-id>`. The full contract,
including every validation rule and the failure it exists to catch, is in
[SCHEMA.md](SCHEMA.md).

### Flags

- `--skip-build`: skip build/install, still runs connected instrumentation tests
- `--skip-eval`: skip scoring step

Examples:

```bash
./eval/run-benchmark.sh --skip-build
./eval/run-benchmark.sh --skip-eval
```

### Engine availability note

If an on-device model is unavailable (for example OPUS-MT model files are not present), that engine
writes no records. Because the host declared it in the manifest, the scorer reports that arm as
**invalid** — no aggregate, and a nonzero exit — while every other arm is still scored. A skipped
engine that quietly scores as if it ran is #58.

### Bubble detection output

`run-benchmark.sh` produces this automatically: `BubbleDetectionBenchmarkTest` runs the real
`BubbleDetector` on device over every case page and writes a `detection` record per arm/case into
the run directory (boxes with `conf`, NMS counts, monotonic duration, and the detector it observed).
Both the case pages and the detector weights ride into the test APK as gitignored assets that the
script stages before the build, so the run does not depend on what the device happens to have
downloaded.

The harness scores these by **containment**, per
[ADR-0003](../docs/adr/0003-detection-hit-criterion.md): each detection is padded by 4% of page
width per side (mirroring the crop the pipeline hands OCR), a ground-truth box is a hit when one
padded detection covers ≥95% of its area under one-to-one matching, and a detection covering two
or more ground-truth centres is a hit for none of them. IoU is no longer used.

### Translation output

Per arm and case, the device writes a `context_assembly` record and a `translation` record. The
translation record carries the ids the model was asked for and its **raw** `{bubble_id, text}`
results, before `TranslationEngine` substitutes source text for an id it never answered.

The harness gates non-translation rate (0), Japanese-residue rate (0, reference-adjudicated), bubble
coverage (100%) and output shape (no missing, extra or duplicate returned id), and reports a
readability word-count ratio and chrF2 as ungated diagnostics. See
`translation-quality/SCHEMA.md` and [SCHEMA.md](SCHEMA.md).

## What this case set can and cannot decide

The set is 17 cases / 148 boxes: 15 story pages (145 boxes, the gate) and 2 cover pages (3 boxes,
reported separately). Pages are drawn from all 5 OpenMantra books, 3 story pages each.

**Separation rule: two detectors are separated only if their story-pool containment differs by at
least 8 percentage points. Below 8pp they are tied, and the choice is made on licence, model size,
and latency — never on score.**

That is not conservatism, it is the arithmetic. Detectors are compared paired — same boxes, two
scores — so only boxes they disagree on carry information:

- The incumbent misses 40 of 145 story boxes because its boxes sit inset from the glyphs. A
  detector that frames to bubble bounds recovers nearly all of them: ~40 discordant boxes, all one
  direction, sign test p far below 10⁻⁶. This set settles that comparison overwhelmingly.
- Two candidates both around 0.90 differ on ~6 boxes, split ~4/2. Sign test p = 0.69. Noise.
  Resolving a 3pp gap needs roughly 500 boxes.

OpenMantra contains 1592 boxes across 214 pages in total. Annotating **every page of the entire
dataset** still leaves a ±2.1pp confidence interval, so a near-tie is unresolvable on this corpus
at any size — not "we need more data", but "this corpus cannot answer that question". Do not grow
the set hoping to break a near-tie; break it on non-score criteria instead.

Page selection is **seeded random** (`random.Random(0)`, 3 story pages per book, excluding covers
and empty pages), recorded as a frozen literal in `generate-cases.py`. Never extend the set by
picking pages where the current detector fails: a set selected on one detector's misses measures
"does the candidate fix *these*", which is indistinguishable from "does the candidate happen to
suit these particular pages". The seeded rule exists so growth cannot be accused of that bias.

**The `label` field (`speech` / `narration` / `sfx`) is not annotation.** OpenMantra has no class
field; `generate-cases.py:label_for()` guesses from substrings. The harness deliberately reports no
per-class breakdown. Do not read meaning into those labels or reintroduce per-class scoring.

**Every arm of a comparison is measured on the same device.** A pre-registered gate names the
device its control was measured on, and a paired delta against a row measured elsewhere puts the
device inside the delta. When the candidate cannot run where the control was measured, re-measure
the control on the device you have and difference the two arms against each other, rather than
subtracting across devices. The repetition-penalty sweep below is the worked instance.

## Interpreting results

- **Bubble detection**: **containment recall** is the gate — the fraction of ground-truth boxes
  whose text is fully inside one padded detection. **Localisation recall** (ground-truth centre
  inside a detection, matched one-to-one) is reported alongside and never gated: the gap between
  the two is the difference between "the detector cannot see the text" and "the detector frames it
  badly", which point at opposite fixes. **Merging detections** — one detection swallowing two or
  more bubbles — are counted separately, because ADR-0002 addresses bubbles by detector id, so a
  merge silently drops a speaker. False positives are tracked but secondary.
  Every headline figure is **box-weighted** — total matched over total expected. Per-case recalls
  are printed as a diagnostic and are **never averaged**: under a per-case mean a 1-box case counts
  as much as a 17-box one, and the two numbers diverge sharply on this set.
  **Cover pages are scored but excluded from the gate**, reported on their own `Cover text` line.
  Their boxes are title typography and author credits, which no balloon detector is trained for, so
  they tax every candidate by the same constant and discriminate between none of them. Whether Yomu
  should translate cover text at all is a pipeline question, not a detector one.
  No absolute pass bar is set (see ADR-0003). The incumbent YOLO26n baseline, measured on the full
  15-story-page set, is **story containment 0.724 (105/145)** / localisation 0.876 / 19 merging
  detections / 26 false positives, with cover text 0.000 (0/3).
  Earlier figures were 0.718 over 78 boxes (8 pages, cover boxes still in the denominator) and
  0.747 over the 75 story boxes of those same 8 pages. The full-set number landing 2.3pp from the
  8-page one is worth noting: the original hand-picked pages were not badly unrepresentative, and
  the gap is far inside the confidence interval either set can support.
- **Translation quality**: an engine passes only at non-translation rate 0,
  Japanese-residue rate 0 and 100% bubble coverage; the set gates failures, it does
  not rank quality (#52). The LLM's page-level call is the gate, ML Kit / OPUS-MT a
  floor. Readability ratio near 1.0 means the engine is producing a similar amount of
  English text as the reference; much higher or lower suggests hallucination or
  dropped content. Exact-match is a sanity check, not a quality target.
  Every bar here scores the **form** of the output. Wrong names, flipped subjects and
  dropped negations pass all of them, so semantic accuracy is reviewed by hand and
  recorded separately in `eval/semantic-review-120.md` — a PASS says the output is
  shaped like a translation, not that it says what the source said. The same taxonomy
  applied to two engines at once is `eval/meaning-comparison-145.md` (Qwen2.5-1.5B against
  CAT-Translate-1.4b).

- **Repetition probe**: **harm** — the output's longest repeated-unit run is shorter than the
  reference's, on a bubble whose reference run is 3 or more. It answers one question the gate
  cannot: did a repetition penalty eat a laugh, a scream or a verbal tic the human translator kept?
  Read it as a **pointer, never a bar** — the set is targeted at that failure by construction, so a
  pass bar on it would reward avoiding these particular bubbles rather than fixing the penalty. The
  per-bubble runs are printed beside the count so every hit can be read against its source.
  See `repetition-probe/SCHEMA.md`.

Results are written to `eval/results/<timestamp>.json`.

## On-device engine invocation

ML Kit, OPUS-MT, and the LLM engine run on Android. Produce eval outputs via the
instrumentation test command for the target engine, then feed the resulting JSON
files into this harness. The harness itself does not run Android code.

## License

OpenMantra is licensed under CC BY-NC 4.0 (see `vendor/open-mantra-dataset/LICENSE.md`).
The derived case metadata (boxes and aligned text) inherits that license and is
for internal evaluation only; do not redistribute.

Citation: Hinami et al., "Towards Fully Automated Manga Translation", AAAI 2021.

Prefer regenerating cases from `vendor/` rather than committing large image files.

## Repetition-penalty sweep (#153)

`EngineBenchmarkTest#measureRepeatPenalty` measures `penalty_repeat` against the gate #139
pre-registered. It runs the #152 probe at 1.0 / 1.1 / 1.2 and the 17-page gate corpus at 1.0 and
1.1, off one model load, with the seed pinned so the arms differ by the penalty alone.

The 1.0 arm is run rather than taken from #137's published `qwen_perline` row: that row was
measured on the reference phone, and a paired delta against another device's numbers measures the
device. Run both arms on whatever device you have, and difference them against each other.

The sweep's arms (`repeat_1.0` / `repeat_1.1` / `repeat_1.2`) write into the same run directory as
any other run, so it goes through `run-benchmark.sh` rather than a hand-rolled copy loop. Records are
written to the app's own files directory, **not** logcat: a looping arm emits multi-kilobyte lines
and logcat's chatty filter silently drops them — the first run of this sweep lost 12 of 22 probe
bubbles that way, and a dropped bubble scores as an empty output, which reads as harm.

```bash
./eval/run-benchmark.sh --skip-eval   # once, to stage cases + probe assets and push the model

RUN_ID="$(date +%Y%m%d-%H%M%S)"
./gradlew :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.runId=$RUN_ID" \
  -Pandroid.testInstrumentationRunnerArguments.class=com.yomu.app.EngineBenchmarkTest#measureRepeatPenalty

mkdir -p "eval/benchmark-results/$RUN_ID"
adb exec-out "run-as com.yomu.app tar c -C files yomu-benchmark/$RUN_ID" \
  | tar x -C "eval/benchmark-results/$RUN_ID" --strip-components=2
```

Write a manifest for those arms (`eval/run_records.py manifest --arm …`, one `--arm` per penalty
value with `gen.penalty_repeat` and `gen.seed=0` pinned), then
`eval/run_records.py complete --run-dir …` and
`eval/.venv/bin/python eval/run-eval.py --run-dir "eval/benchmark-results/$RUN_ID" --no-bubble`.

Results: [the repeat-penalty measurement](repeat-penalty-153.md).

## Qwen prompt comparison

Install `eval/requirements.txt` into a Python virtual environment. With one arm64 Android
emulator/device connected and Qwen2.5-1.5B already downloaded in Yomu, run:

```sh
PYTHON_BIN=/path/to/venv/bin/python ./eval/run-prompt-benchmark.sh
```

Use `--skip-build` only when the app and test APKs already match the current source.
The runner installs with `adb install -r -t`, preserving app data, and compares the old
model-card prompt, translation-only instructions, and bounded current-capture dialogue.
It restores the selected engine, model, and context setting after normal completion.
An interrupted process may leave benchmark settings selected; check Settings afterward.

Each run has an isolated directory under `eval/results/prompt-<timestamp>/`, containing
model SHA-256, source revision/diff, corpus checksums, raw model logcat, per-mode outputs,
timing CSV, and aligned source/reference/output scores. It rejects incomplete runs;
a completed run may still fail the translation output gate.

`mean_chrf` is the mean per-bubble chrF2 score from sacrebleu 2.5.1 (0–100), not semantic
accuracy. It is null in the legacy scorer when sacrebleu is absent. The focused runner
requires it. Scores measure processed output; raw refusals may instead appear as source
fallbacks. Human review remains necessary for names, subjects, omissions, and invented meaning.

See [the September prompt comparison](prompt-comparison-119.md) for the reviewed results.
