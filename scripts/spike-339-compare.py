"""Desktop A/B: today's no-past OCR export vs the new past-key-values export (#339).

Crops come from the bubble detector on each page (top-left letterbox to 1280, conf >= 0.25),
roughly as the app does. Both paths decode greedily from [CLS] like OcrDecoding.decodeGreedy.
"""
import sys
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image

FIX = Path(sys.argv[1])  # scripts/speed-fixtures
NEW = Path(sys.argv[2])  # export output dir
VISION = FIX / "models/vision"
START, MAX_LEN = 2, 128
vocab = (VISION / "vocab.txt").read_text().splitlines()
SEP = vocab.index("[SEP]")


def sess(p):
    o = ort.SessionOptions()
    o.intra_op_num_threads = 4
    return ort.InferenceSession(str(p), o, providers=["CPUExecutionProvider"])


det = sess(VISION / "bubble_detection.onnx")
old_enc, old_dec = sess(VISION / "manga_ocr_encoder.onnx"), sess(VISION / "manga_ocr_decoder.onnx")
new_enc, merged = sess(NEW / "encoder_model.onnx"), sess(NEW / "decoder_model_merged.onnx")
first, with_past = sess(NEW / "decoder_model.onnx"), sess(NEW / "decoder_with_past_model.onnx")


def crops(page):
    img = Image.open(page).convert("RGB")
    s = 1280 / max(img.size)
    w, h = round(img.width * s), round(img.height * s)
    canvas = np.full((1280, 1280, 3), 114, np.uint8)
    canvas[:h, :w] = np.asarray(img.resize((w, h), Image.BILINEAR))
    out = det.run(None, {det.get_inputs()[0].name: canvas.transpose(2, 0, 1)[None].astype(np.float32) / 255})[0][0]
    for x1, y1, x2, y2, conf, _ in out:
        if conf >= 0.25:
            yield img.crop(tuple(int(v / s) for v in (x1, y1, x2, y2)))


def pixels(crop):
    a = np.asarray(crop.resize((224, 224), Image.BILINEAR), np.float32) / 255
    return ((a - 0.5) / 0.5).transpose(2, 0, 1)[None]


def greedy(step):
    ids = []
    for _ in range(MAX_LEN):
        tok = int(step(ids)[0, -1].argmax())
        if tok == SEP:
            break
        ids.append(tok)
    return ids


def decode_old(px):
    hidden = old_enc.run(None, {"pixel_values": px})[0]
    return greedy(lambda ids: old_dec.run(None, {"input_ids": np.array([[START] + ids]), "encoder_hidden_states": hidden})[0])


def past_names(s):
    return [o.name.replace("present", "past_key_values") for o in s.get_outputs()[1:]]


def decode_merged(px):
    hidden = new_enc.run(None, {"pixel_values": px})[0]
    names = past_names(merged)
    empty = np.zeros((1, 12, 0, 64), np.float32)
    past = {n: empty for n in names}
    use_cache = np.array([False])

    def step(ids):
        nonlocal past, use_cache
        token = ids[-1] if ids else START
        out = merged.run(None, {"input_ids": np.array([[token]]), "encoder_hidden_states": hidden,
                                "use_cache_branch": use_cache, **past})
        # The cache branch returns empty cross-attention presents; keep the first step's.
        past = {n: (v if v.size else past[n]) for n, v in zip(names, out[1:])}
        use_cache = np.array([True])
        return out[0]
    return greedy(step)


def decode_split(px):
    hidden = new_enc.run(None, {"pixel_values": px})[0]
    past = None

    def step(ids):
        nonlocal past
        if past is None:
            out = first.run(None, {"input_ids": np.array([[START]]), "encoder_hidden_states": hidden})
            past = dict(zip(past_names(first), out[1:]))
        else:
            out = with_past.run(None, {"input_ids": np.array([[ids[-1]]]), "encoder_hidden_states": hidden, **past})
            past.update(zip(past_names(with_past), out[1:]))
        return out[0]
    return greedy(step)


paths = {"old": decode_old, "merged": decode_merged, "split": decode_split}
totals = {k: 0.0 for k in paths}
n = same = 0
for page in sorted((FIX / "pages").glob("*.jpg")):
    for crop in crops(page):
        px = pixels(crop)
        texts = {}
        for k, fn in paths.items():
            t = time.perf_counter()
            ids = fn(px)
            totals[k] += time.perf_counter() - t
            texts[k] = "".join(vocab[i].removeprefix("##") for i in ids if not vocab[i].startswith("["))
        n += 1
        same += len(set(texts.values())) == 1
        print(("OK  " if len(set(texts.values())) == 1 else "DIFF"), page.stem, texts if len(set(texts.values())) > 1 else texts["old"])
print(f"\n{same}/{n} crops identical")
for k, v in totals.items():
    print(f"{k:7s} {v * 1000:8.0f} ms total  {v * 1000 / n:6.1f} ms/crop")
