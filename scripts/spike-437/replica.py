"""Desktop replica: Yomu's yolo26n path and the ogkalu RT-DETR v4-s, dumped raw per page."""
import json, sys, time, glob, os
import numpy as np
from PIL import Image
import onnxruntime as ort

S = os.path.dirname(os.path.abspath(__file__))
REPO = '/Users/ctw04928/Documents/Projects/personal-projects/yomu-manga-translation-app'
YOLO = REPO + '/scripts/speed-fixtures/models/vision/bubble_detection.onnx'
OGK = os.path.join(S, '..', 'model', 'detector-v4-s_int8.onnx')
PAD = 0x72

so = ort.SessionOptions(); so.intra_op_num_threads = 4
yolo = ort.InferenceSession(YOLO, so, providers=['CPUExecutionProvider'])
ogk = ort.InferenceSession(OGK, so, providers=['CPUExecutionProvider'])

def chw(img):
    return (np.asarray(img, dtype=np.float32) / 255.0).transpose(2, 0, 1)[None]

def run_yolo(img):
    w, h = img.size
    scale = 1280 / max(w, h)
    sw, sh = round(w * scale), round(h * scale)
    iw, ih = (sw + 31) // 32 * 32, (sh + 31) // 32 * 32
    canvas = Image.new('RGB', (iw, ih), (PAD, PAD, PAD))
    canvas.paste(img.resize((sw, sh), Image.BILINEAR), (0, 0))
    out = yolo.run(None, {'images': chw(canvas)})[0][0]  # 300x6
    dets = []
    for x1, y1, x2, y2, conf, cls in out:
        if conf >= 0.05 and int(cls) == 0:
            dets.append([float(x1 / scale), float(y1 / scale), float(x2 / scale), float(y2 / scale), float(conf), 0])
    return dets

def run_ogk(img, mode, size_order):
    w, h = img.size
    if mode == 'stretch':
        inp = img.resize((640, 640), Image.BILINEAR)
        sx, sy = 1, 1; tw, th = w, h
    else:  # letterbox: pad to square (bottom/right), then 640
        side = max(w, h)
        canvas = Image.new('RGB', (side, side), (PAD, PAD, PAD)); canvas.paste(img, (0, 0))
        inp = canvas.resize((640, 640), Image.BILINEAR)
        tw = th = side
    sizes = np.array([[th, tw] if size_order == 'hw' else [tw, th]], dtype=np.int64)
    labels, boxes, scores = ogk.run(None, {'images': chw(inp), 'orig_target_sizes': sizes})
    dets = []
    for l, b, s in zip(labels[0], boxes[0], scores[0]):
        if s >= 0.05:
            dets.append([float(b[0]), float(b[1]), float(b[2]), float(b[3]), float(s), int(l)])
    return dets

if __name__ == '__main__':
    pages = sorted(glob.glob(REPO + '/vendor/open-mantra-dataset/images/*/ja/*.jpg'))
    if len(sys.argv) > 1: pages = pages[:int(sys.argv[1])]
    out = {}
    t = {'yolo': 0, 'stretch': 0, 'letterbox': 0}
    for p in pages:
        img = Image.open(p).convert('RGB')
        key = p.split('/images/')[1]
        t0 = time.perf_counter(); y = run_yolo(img); t['yolo'] += time.perf_counter() - t0
        t0 = time.perf_counter(); a = run_ogk(img, 'stretch', 'wh'); t['stretch'] += time.perf_counter() - t0
        t0 = time.perf_counter(); b = run_ogk(img, 'letterbox', 'wh'); t['letterbox'] += time.perf_counter() - t0
        out[key] = {'size': img.size, 'yolo': y, 'ogk_stretch': a, 'ogk_letterbox': b}
    json.dump(out, open(os.path.join(S, 'raw.json'), 'w'))
    print(len(pages), 'pages', {k: round(v / len(pages), 3) for k, v in t.items()}, 's/page')
