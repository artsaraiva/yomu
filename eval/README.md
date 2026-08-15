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
├── run-eval.py              # Phase 1 eval harness CLI
├── run_eval_lib.py          # Scoring and case-loading logic
├── generate-cases.py        # Build cases from vendor/OpenMantra
├── bubble-detection/
│   ├── SCHEMA.md
│   └── cases/<case-id>/     # page.jpg + expected.json
└── translation-quality/
    ├── SCHEMA.md
    └── cases/<case-id>/     # page.jpg + source.txt + reference.txt
```

## Populating the dataset

The OpenMantra dataset is vendored, not committed:

```bash
git clone https://github.com/mantra-inc/open-mantra-dataset.git \
  vendor/open-mantra-dataset
python3 eval/generate-cases.py
```

`vendor/` and the copied `page.jpg` files are gitignored. Cases are regenerated
from the vendored annotations so the repository only carries the harness and
small derived metadata.

## Running the harness

```bash
./eval/run-eval.py --stub
```

`--stub` runs the scoring logic with synthetic perfect outputs. For real engine
comparison, collect on-device outputs and place them in the format below, then
run without `--stub`.

## One-command benchmark runner

Use the script below to run the full on-device benchmark flow (instrumentation,
artifact pull, and scoring):

```bash
./eval/run-benchmark.sh
```

### Prerequisites

- Android device/emulator connected and visible to `adb`
- `adb` in `PATH` (Android platform-tools)
- `python3` in `PATH`
- Executable Gradle wrapper at `./gradlew`

If any prerequisite is missing, the script fails early with an actionable error.

### What the script does

`run-benchmark.sh` executes numbered progress steps with elapsed time:

1. prerequisites
2. build/install
3. device test
4. pull artifacts
5. score results
6. summary

It streams instrumentation/eval output to the terminal and writes a persistent
log for the run.

### Output layout

Each run creates a unique timestamped directory (never overwrites prior runs):

```text
eval/benchmark-results/<timestamp>/
├── benchmark.log          # full combined run log
├── raw-artifacts/         # pulled device files from yomu-benchmark/
├── run-eval-output.log    # eval script stdout
└── scored-results.json    # copied score JSON from run-eval.py
```

Artifacts are pulled from the app-specific path on device:

`/sdcard/Android/data/com.yomu.app/files/yomu-benchmark/`

### Flags

- `--skip-build`: skip build/install, still runs connected instrumentation tests
- `--skip-eval`: skip scoring step

Examples:

```bash
./eval/run-benchmark.sh --skip-build
./eval/run-benchmark.sh --skip-eval
```

### Engine availability note

If an on-device model is unavailable (for example OPUS-MT model files are not
present), that engine may be skipped or reported with an error in results while
other engines continue to be scored.

### Bubble detection output format

`run-benchmark.sh` produces this automatically: `BubbleDetectionBenchmarkTest` runs the real
`BubbleDetector` on device over every case page and logs the boxes, which the script writes to
`actual.json` per case. Both the case pages and the detector weights ride into the test APK as
gitignored assets that the script stages before the build, so the run does not depend on what the
device happens to have downloaded.

Per case, the file written is `eval/bubble-detection/cases/<case-id>/actual.json`:

```json
{
  "boxes": [
    {"x": 120, "y": 340, "w": 260, "h": 180},
    {"x": 720, "y": 1980, "w": 150, "h": 120}
  ]
}
```

The harness scores these by **containment**, per
[ADR-0003](../docs/adr/0003-detection-hit-criterion.md): each detection is padded by 4% of page
width per side (mirroring the crop the pipeline hands OCR), a ground-truth box is a hit when one
padded detection covers ≥95% of its area under one-to-one matching, and a detection covering two
or more ground-truth centres is a hit for none of them. IoU is no longer used.

### Translation output format

Per case and per engine, write
`eval/translation-quality/cases/<case-id>/actual/<engine>.json`:

```json
{
  "engine": "mlkit",
  "translations": [
    "english line 1",
    "english line 2"
  ]
}
```

Lines must align with `source.txt` by bubble id. The harness gates non-translation
rate (0), Japanese-residue rate (0, reference-adjudicated) and bubble coverage (100%),
and reports a readability word-count ratio as a diagnostic. See
`translation-quality/SCHEMA.md`.

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
