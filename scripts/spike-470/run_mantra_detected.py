"""Spike #470: the app's view of OpenMantra. Reads the detector's own boxes, not the annotators'.

Each detector box takes the label text of the annotation it overlaps best (IoU >= 0.5), or none:
a box with no annotation is a sound effect, a sign or art. Writes $SPIKE_WORK/out/mantra_det.jsonl.
"""
import json

from PIL import Image

import readers
from run_pages import OUT, Detector, app_crop, iou, read_all

det, rs = Detector(), readers.all_readers()
ann = json.load(open(readers.REPO + '/vendor/open-mantra-dataset/annotation.json'))
with open(OUT + '/mantra_det.jsonl', 'w') as f:
    for book in ann:
        for page in book['pages']:
            img = Image.open(readers.REPO + '/vendor/open-mantra-dataset/' + page['image_paths']['ja']).convert('RGB')
            truths = [([t['x'], t['y'], t['x'] + t['w'], t['y'] + t['h']], t['text_ja']) for t in page['text']]
            for i, (score, cls, box) in enumerate(det.detect(img)):
                best = max(truths, key=lambda t: iou(t[0], box), default=None)
                gt = best[1] if best and iou(best[0], box) >= 0.5 else None
                crop = app_crop(img, box)
                key = f'det_{book["book_title"]}_{page["page_index"]:03d}_{i:02d}'
                crop.save(f'{OUT}/crops/{key}.png')
                f.write(json.dumps({'key': key, 'cls': cls, 'det': round(score, 3), 'gt': gt, 'size': crop.size,
                                    **read_all(rs, crop)}, ensure_ascii=False) + '\n')
        print(book['book_title'], flush=True)
