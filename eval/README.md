# Yomu evaluation datasets

Curated, real-world manga cases used to measure two things that must not regress:

1. **Bubble detection recall** — every readable text box must be detected. A single
   missed bubble breaks the reading experience, so the target is effectively 100%
   recall on the accepted case set.
2. **Translation quality** — Japanese OCR text must become coherent English.
   Track artifacts (e.g. `SEP`), incoherent output, and untranslated/source-language
   output.

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

The harness computes IoU@0.5 recall and false positives.

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

Lines must align with `source.txt`. The harness reports untranslated rate,
artifact rate, exact-match rate, and a readability word-count ratio.

## Interpreting results

- **Bubble detection**: average recall@0.5 should be near 1.0; missed boxes are
  regressions. False positives are also tracked but are secondary to recall.
  Two recall figures are printed: the per-case average, and the box-weighted figure over all
  boxes. They diverge sharply on this case set, because a 1-box case counts as much as a 17-box
  one in the per-case average. Quote the box-weighted number when comparing detectors.
  Note that ground truth is OpenMantra *text-region* annotation, so IoU@0.5 against a balloon
  detector partly measures box-convention agreement rather than whether text was found; see #36.
- **Translation quality**: lower untranslated and artifact rates are better.
  Readability ratio near 1.0 means the engine is producing a similar amount of
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
