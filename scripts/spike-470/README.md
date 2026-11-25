# Spike #470: Hayai v2.7 and Baberu v1.2 against the shipped OCR

Desktop scripts only. Results and the recommendation are in the issue.

Setup, with `$W` as a work folder outside the repo:

- `uv venv $W/.venv && uv pip install -p $W/.venv onnxruntime numpy pillow tokenizers regex`
- `$W/dl/baberu/`: `onnx/vision_int4.onnx`, `onnx/decoder_prefill_int8.onnx`, `onnx/decoder_step_int8.onnx` and
  `tokenizer/vocab.json` from `genshiai-daichi/baberu-ocr@b986843`.
- `$W/dl/hayai/`: `hayai_prefill_int4.onnx`, `hayai_decode_int8.onnx`, `hayai_onnx_config.json`, `tokenizer.json`
  and `pos_embedding.npy` from `JustANormalTinkerer/hayai-ocr-v2.7-supernova-onnx@bc1e39f`.
- The shipped reader and the detector come from `scripts/speed-fixtures/models/vision/`, the pages from
  `scripts/speed-fixtures/pages/` and `vendor/open-mantra-dataset/` in the main checkout (`YOMU_REPO`).

Run with `SPIKE_WORK=$W`:

- `readers.py`: the three readers behind one `read(image) -> (text, confidence, tokens)`.
- `run_pages.py`: reads every OpenMantra text box and every fixture-page detector box; writes
  `$W/out/mantra.jsonl`, `fixtures.jsonl` and the crops.
- `score.py [--list]`: accuracy by detector class, the 0.85 gate, the two shapes and the timings, as markdown.
- `run_jmb.py <reader> <images> <gt.jsonl> <out.jsonl>`: predictions for JMangaBench_Read, scored with that
  repo's own `eval/score.py`. Its data is CC BY-NC-SA 4.0: keep it under `$W`, never in the repo.
