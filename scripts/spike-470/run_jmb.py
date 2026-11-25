"""Spike #470: read JMangaBench_Read's crops with one reader and write a predictions file for its eval/score.py.

Usage: run_jmb.py <shipped|baberu|hayai> <images dir> <gt.jsonl> <out.jsonl>
The dataset is CC BY-NC-SA 4.0: evaluation only, nothing from it goes in the repo.
"""
import json
import sys

from PIL import Image

import readers

name, images, gt, out = sys.argv[1:5]
reader = {'shipped': readers.Shipped, 'baberu': readers.Baberu, 'hayai': readers.Hayai}[name]()
with open(out, 'w') as f:
    for n, line in enumerate(open(gt, encoding='utf-8')):
        g = json.loads(line)
        text, conf, _ = reader.read(Image.open(f'{images}/{g["file"]}'))
        f.write(json.dumps({'key': g['key'], 'pred': text, 'conf': round(conf, 4)}, ensure_ascii=False) + '\n')
        if n % 500 == 0:
            print(name, n, flush=True)
