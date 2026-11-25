"""Spike #470: read every OpenMantra text box and every fixture-page detector box with the three readers.

Writes $SPIKE_WORK/out/mantra.jsonl and fixtures.jsonl, one crop per line, and saves each crop
under $SPIKE_WORK/out/crops/ so the unsure ones can be looked at.
"""
import glob
import json
import os
import sys
import time

import numpy as np
from PIL import Image

import readers

OUT = readers.WORK + '/out'
DETECTOR = readers.VISION + '/rtdetr_v4s_int8.onnx'
CLASSES = {1: 'text_bubble', 2: 'text_free'}


def iou(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0])); iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    return inter / ((a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter + 1e-9)


def contained(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0])); iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    return ix * iy / (min((a[2] - a[0]) * (a[3] - a[1]), (b[2] - b[0]) * (b[3] - b[1])) + 1e-9)


class Detector:
    """The app's RT-DETR path: stretch to 640, text classes only, 0.25 threshold, 0.5 floor for text_free."""

    def __init__(self):
        self.sess = readers.session(DETECTOR)

    def detect(self, img):
        w, h = img.size
        x = (readers.android_bilinear(np.asarray(img), 640, 640) / 255.0).transpose(2, 0, 1)[None].astype(np.float32)
        labels, boxes, scores = self.sess.run(None, {'images': x, 'orig_target_sizes': np.array([[w, h]], np.int64)})
        dets = [(float(s), CLASSES[int(l)], [float(v) for v in b]) for l, b, s in zip(labels[0], boxes[0], scores[0])
                if int(l) in CLASSES and s >= (0.5 if int(l) == 2 else 0.25)]
        kept = []
        for d in sorted(dets, reverse=True):
            if all(iou(d[2], k[2]) <= 0.80 and contained(d[2], k[2]) < 0.90 for k in kept):
                kept.append(d)
        return kept


def app_crop(img, box):
    """CropBoundsCalculator: left, top, width and height are each truncated, then clamped."""
    w, h = img.size
    x = min(max(int(box[0]), 0), w - 1); y = min(max(int(box[1]), 0), h - 1)
    cw = max(min(int(box[2] - box[0]), w - x), 1); ch = max(min(int(box[3] - box[1]), h - y), 1)
    return img.crop((x, y, x + cw, y + ch))


def read_all(rs, crop):
    row = {}
    for r in rs:
        t0 = time.perf_counter()
        text, conf, tokens = r.read(crop)
        row[r.name] = {'text': text, 'conf': round(conf, 4), 'tokens': tokens, 'ms': round((time.perf_counter() - t0) * 1000, 1)}
    return row


def main():
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else None
    os.makedirs(OUT + '/crops', exist_ok=True)
    det, rs = Detector(), readers.all_readers()
    for r in rs:  # warm up, so the first timed crop is not a cold start
        r.read(Image.new('RGB', (100, 200), 'white'))

    with open(OUT + '/fixtures.jsonl', 'w') as f:
        for path in sorted(glob.glob(readers.REPO + '/scripts/speed-fixtures/pages/*.jpg')):
            img = Image.open(path).convert('RGB')
            for i, (score, cls, box) in enumerate(det.detect(img)):
                crop = app_crop(img, box)
                key = f'{os.path.basename(path)[:-4]}_{i:02d}'
                crop.save(f'{OUT}/crops/{key}.png')
                f.write(json.dumps({'key': key, 'cls': cls, 'det': round(score, 3), 'size': crop.size,
                                    **read_all(rs, crop)}, ensure_ascii=False) + '\n')
            print(path, flush=True)

    ann = json.load(open(readers.REPO + '/vendor/open-mantra-dataset/annotation.json'))
    pages = [(b['book_title'], p) for b in ann for p in b['pages']][:limit]
    with open(OUT + '/mantra.jsonl', 'w') as f:
        for n, (book, page) in enumerate(pages):
            img = Image.open(readers.REPO + '/vendor/open-mantra-dataset/' + page['image_paths']['ja']).convert('RGB')
            dets = det.detect(img)
            for i, t in enumerate(page['text']):
                box = [t['x'], t['y'], t['x'] + t['w'], t['y'] + t['h']]
                best = max(dets, key=lambda d: iou(d[2], box), default=None)
                cls = best[1] if best and iou(best[2], box) >= 0.3 else 'undetected'
                crop = app_crop(img, box)
                key = f'{book}_{page["page_index"]:03d}_{i:02d}'
                crop.save(f'{OUT}/crops/{key}.png')
                f.write(json.dumps({'key': key, 'cls': cls, 'gt': t['text_ja'], 'size': crop.size,
                                    **read_all(rs, crop)}, ensure_ascii=False) + '\n')
            if n % 20 == 0:
                print(n, len(pages), flush=True)


if __name__ == '__main__':
    main()
