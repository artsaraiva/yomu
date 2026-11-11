# Spike #437: ogkalu comic-text-and-bubble-detector v4-s

Desktop replica and scoring scripts. `uv venv .venv && uv pip install onnxruntime pillow numpy`, put
`detector-v4-s_int8.onnx` (HF `ogkalu/comic-text-and-bubble-detector@16e8a62`) in `../model/` relative
to the scripts, then run `replica.py` (dumps `raw.json` for the 214 OpenMantra pages), `score2.py`
(hit, merge and extra counts per threshold) and `analyze.py` (contact sheets, satellites, aside order).

The app change is in `ml/.../OnnxRuntime.kt` (RT-DETR branch) and `DetectorSpeedTest.kt`.
