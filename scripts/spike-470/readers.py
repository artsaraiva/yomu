"""Spike #470: three crop readers behind one interface, on onnxruntime CPU.

Each reader's read(image) returns (text, confidence, tokens). Confidence is the mean softmax
probability of the chosen tokens, the stop token included, which is how Yomu's OcrEngine scores
a read. The Baberu and Hayai loops follow the host loops their authors publish beside the ONNX
files (onnx_infer.py and inference_onnx.py), rewritten here for one crop at a time.
"""
import json
import math
import os
import unicodedata

import numpy as np
import onnxruntime as ort
from PIL import Image

REPO = os.environ.get('YOMU_REPO', '/Users/ctw04928/Documents/Projects/personal-projects/yomu-manga-translation-app')
WORK = os.environ['SPIKE_WORK']  # holds dl/baberu and dl/hayai
VISION = REPO + '/scripts/speed-fixtures/models/vision'
THREADS = int(os.environ.get('SPIKE_THREADS', '4'))


def session(path):
    so = ort.SessionOptions()
    so.intra_op_num_threads = THREADS
    so.inter_op_num_threads = 1
    return ort.InferenceSession(path, so, providers=['CPUExecutionProvider'])


def softmax_prob(logits, index):
    z = logits.astype(np.float64)
    z = z - z.max()
    e = np.exp(z)
    return float(e[index] / e.sum())


def android_bilinear(arr, out_w, out_h):
    """Bitmap.createScaledBitmap(filter=true): bilinear at pixel centres, clamped, no antialiasing."""
    h, w = arr.shape[:2]
    xs = np.clip((np.arange(out_w) + 0.5) * w / out_w - 0.5, 0, w - 1)
    ys = np.clip((np.arange(out_h) + 0.5) * h / out_h - 0.5, 0, h - 1)
    x0 = np.floor(xs).astype(int); x1 = np.minimum(x0 + 1, w - 1); fx = (xs - x0)[None, :, None]
    y0 = np.floor(ys).astype(int); y1 = np.minimum(y0 + 1, h - 1); fy = (ys - y0)[:, None, None]
    a = arr.astype(np.float32)
    top = a[y0][:, x0] * (1 - fx) + a[y0][:, x1] * fx
    bot = a[y1][:, x0] * (1 - fx) + a[y1][:, x1] * fx
    return np.round(top * (1 - fy) + bot * fy)


class Shipped:
    """Yomu's reader: jzhang533/manga-ocr-base-2025 as artsaraiva/manga-ocr-2025-onnx-past."""
    name = 'shipped'
    files = ['manga_ocr_encoder.onnx', 'manga_ocr_decoder.onnx', 'vocab.txt']

    def __init__(self):
        self.enc = session(VISION + '/manga_ocr_encoder.onnx')
        self.dec = session(VISION + '/manga_ocr_decoder.onnx')
        self.vocab = [line.strip() for line in open(VISION + '/vocab.txt', encoding='utf-8')]
        self.sep = self.vocab.index('[SEP]')
        self.past_names = [i.name for i in self.dec.get_inputs() if i.name.startswith('past_key_values')]
        self.past_shapes = {i.name: [d if isinstance(d, int) else None for d in i.shape]
                            for i in self.dec.get_inputs() if i.name.startswith('past_key_values')}
        self.out_names = [o.name for o in self.dec.get_outputs()]

    def read(self, image, max_tokens=300):
        rgb = np.asarray(image.convert('RGB'))
        x = (android_bilinear(rgb, 224, 224) / 255.0 - 0.5) / 0.5
        hidden = self.enc.run(None, {self.enc.get_inputs()[0].name: x.transpose(2, 0, 1)[None].astype(np.float32)})[0]
        past = {}
        for n in self.past_names:
            heads, dim = self.past_shapes[n][1] or 12, self.past_shapes[n][3] or 64
            past[n] = np.zeros((1, heads, 0, dim), np.float32)
        token, ids, probs, first = 2, [], [], True
        for _ in range(max_tokens):
            feed = {'input_ids': np.array([[token]], np.int64), 'encoder_hidden_states': hidden,
                    'use_cache_branch': np.array([not first]), **past}
            out = dict(zip(self.out_names, self.dec.run(None, feed)))
            logits = out['logits'][0, -1]
            token = int(np.argmax(logits))
            probs.append(softmax_prob(logits, token))
            for name, value in out.items():
                if not name.startswith('present'):
                    continue
                key = name.replace('present', 'past_key_values')
                # The cache branch returns empty cross-attention presents; the first step's stay in use.
                if first or '.encoder.' not in key:
                    past[key] = value
            first = False
            if token == self.sep:
                break
            ids.append(token)
        text = ''.join(t[2:] if t.startswith('##') else t for t in (self.vocab[i] for i in ids)
                       if not (t.startswith('[') and t.endswith(']') and len(t) > 2))
        return text.strip(), float(np.mean(probs)) if probs else 0.0, len(ids)


class Baberu:
    """genshiai-daichi/baberu-ocr v1.2, smallest set: vision int4 + decoder int8."""
    name = 'baberu'
    PAST = [f'past_k{i}' for i in range(6)] + [f'past_v{i}' for i in range(6)]
    MEAN = np.array([0.485, 0.456, 0.406], np.float32)
    STD = np.array([0.229, 0.224, 0.225], np.float32)

    def __init__(self, vision='vision_int4.onnx'):
        d = WORK + '/dl/baberu'
        self.vis = session(f'{d}/onnx/{vision}')
        self.pre = session(d + '/onnx/decoder_prefill_int8.onnx')
        self.stp = session(d + '/onnx/decoder_step_int8.onnx')
        charset = json.load(open(d + '/tokenizer/vocab.json', encoding='utf-8'))
        self.chars = {i + 4: ch for i, ch in enumerate(charset)}
        self.content = {i for i, ch in self.chars.items()
                        if len(ch) == 1 and ch not in 'ーｰ〜~' and unicodedata.category(ch)[0] in 'LN'}

    def read(self, image, max_tokens=256, penalty=1.2):
        img = image.convert('RGB').resize((224, 224), Image.BICUBIC)
        x = ((np.asarray(img, np.float32) / 255.0 - self.MEAN) / self.STD).transpose(2, 0, 1)[None]
        vis = self.vis.run(['vision_embeds'], {'pixel_values': x})[0]
        out = self.pre.run(None, {'vision_embeds': vis, 'input_ids': np.array([[1]], np.int64)})
        seen, toks, probs, pos = {1}, [], [], vis.shape[1] + 1
        while True:
            raw = out[0][0, -1].astype(np.float64)
            logits = raw.copy()
            for t in seen:
                logits[t] = logits[t] * penalty if logits[t] < 0 else logits[t] / penalty
            if toks:
                last = toks[-1]
                cap = 12 if last in self.content else 16
                run = 0
                for t in reversed(toks):
                    if t != last:
                        break
                    run += 1
                if run >= cap:
                    logits[last] = -np.inf
            nxt = int(np.argmax(logits))
            probs.append(softmax_prob(raw, nxt))
            if nxt == 2:
                break
            toks.append(nxt); seen.add(nxt)
            if len(toks) >= max_tokens:
                break
            feed = {'input_ids': np.array([[nxt]], np.int64), 'position_ids': np.array([[pos]], np.int64)}
            feed.update(zip(self.PAST, out[1:]))
            out = self.stp.run(None, feed)
            pos += 1
        return ''.join(self.chars.get(t, '') for t in toks), float(np.mean(probs)), len(toks)


def naflex_size(height, width, max_patches, patch=16, min_patches=64, max_scale=4.0):
    def dims(s):
        th = max(patch, int(math.ceil(height * s / patch)) * patch)
        tw = max(patch, int(math.ceil(width * s / patch)) * patch)
        return th, tw

    def search(lo, hi, limit):
        while hi - lo >= 1e-5:
            mid = 0.5 * (lo + hi)
            th, tw = dims(mid)
            if (th // patch) * (tw // patch) <= limit:
                lo = mid
            else:
                hi = mid
        return lo

    scale = search(1e-5, max_scale, max_patches)
    th, tw = dims(scale)
    if (th // patch) * (tw // patch) < min_patches:
        th, tw = dims(search(scale, 100.0, min(min_patches, max_patches)))
    return th, tw, th // patch, tw // patch


def best_max_patches(height, width):
    """The published 'auto' tier choice among 128, 192, 256 and 384 patches."""
    info = {}
    for mp in (128, 192, 256, 384):
        th, tw, nh, nw = naflex_size(height, width, mp)
        info[mp] = (nh * nw, min(th / max(1, height), tw / max(1, width)))
    n128, s128 = info[128]; n192, s192 = info[192]; n256, s256 = info[256]; n384 = info[384][0]
    if n128 == n384:
        return 128
    h, w = max(1, height), max(1, width)
    aspect = max(h, w) / min(h, w)
    square = 0.77 <= w / h <= 1.30
    horizontal = w / h > 1.30
    if not square:
        if not horizontal and s128 >= 1.65 and aspect < 3.8:
            return 128
        if horizontal and s128 >= 2.35 and aspect < 3.2:
            return 128
    if n384 > n256 and (s256 < 1.28 or (square and s256 < 2.20) or (horizontal and s256 < 1.45)
                        or (aspect >= 3.8 and s256 < 1.75) or (aspect >= 5.0 and s256 < 2.20)):
        return 384
    if n192 == n128:
        return 128
    if n256 == n192:
        return 192
    if not square:
        if not horizontal and s192 >= 1.55 and aspect < 3.6:
            return 192
        if horizontal and s192 >= 1.85 and aspect < 3.0:
            return 192
    return 256


class Hayai:
    """JustANormalTinkerer/hayai-ocr-v2.7-supernova-onnx, smallest set: prefill int4 + decode int8."""
    name = 'hayai'

    def __init__(self, prefill='hayai_prefill_int4.onnx', decode='hayai_decode_int8.onnx', max_patches='auto'):
        from tokenizers import Tokenizer
        d = WORK + '/dl/hayai'
        self.meta = json.load(open(d + '/hayai_onnx_config.json'))
        self.tok = Tokenizer.from_file(d + '/tokenizer.json')
        pos = np.load(d + '/pos_embedding.npy', allow_pickle=False)
        side = int(round(pos.shape[0] ** 0.5))
        self.side, self.grid = side, pos.reshape(side, side, -1).astype(np.float32)
        self.prefill = session(f'{d}/{prefill}')
        self.decode = session(f'{d}/{decode}')
        self.max_patches = max_patches
        self.pos_cache = {}

    def pos_embed(self, nh, nw):
        if (nh, nw) not in self.pos_cache:
            def weights(out, src):
                scale = src / out
                inv = 1.0 / scale if scale > 1.0 else 1.0
                centres = scale * (np.arange(out, dtype=np.float32) + 0.5)
                j = np.arange(src, dtype=np.float32) + 0.5
                w = np.maximum(0.0, 1.0 - np.abs(j[None] - centres[:, None]) * inv)
                return w / np.maximum(w.sum(axis=1, keepdims=True), 1e-12)
            self.pos_cache[(nh, nw)] = np.einsum('ih,jw,hwd->ijd', weights(nh, self.side), weights(nw, self.side),
                                                 self.grid).reshape(nh * nw, -1)
        return self.pos_cache[(nh, nw)]

    def read(self, image, max_tokens=128):
        ps, mult = 16, self.meta['pad_to_multiple_of']
        bos, eos, pad = self.meta['bos_token_id'], self.meta['eos_token_id'], self.meta['pad_token_id']
        rgb = image.convert('RGB')
        w, h = rgb.size
        mp = best_max_patches(h, w) if self.max_patches == 'auto' else int(self.max_patches)
        th, tw, nh, nw = naflex_size(h, w, mp)
        if (tw, th) != (w, h):
            rgb = rgb.resize((tw, th), Image.BILINEAR)
        patches = np.asarray(rgb, np.uint8).reshape(nh, ps, nw, ps, 3).transpose(0, 2, 1, 3, 4).reshape(nh * nw, -1)
        n = nh * nw
        keep = max(mult, (n + mult - 1) // mult * mult)
        pv = np.zeros((1, keep, ps * ps * 3), np.float32)
        pe = np.zeros((1, keep, self.grid.shape[-1]), np.float32)
        pv[0, :n] = patches.astype(np.float32) * (2.0 / 255.0) - 1.0
        pe[0, :n] = self.pos_embed(nh, nw)
        l_vis = ((nh + 1) // 2) * ((nw + 1) // 2)
        logits, k, v, valid = self.prefill.run(None, {
            'pixel_values': pv, 'pos_embeds': pe, 'spatial_shapes': np.array([[nh, nw]], np.int64),
            'bos_ids': np.array([[bos]], np.int64), 'l_vis_arange': np.arange(l_vis, dtype=np.int64)})
        mask = np.concatenate([valid, np.ones((1, 1), np.int64)], axis=1)
        toks, probs = [], []
        for step in range(1, max_tokens + 1):
            nxt = int(np.argmax(logits[0]))
            probs.append(softmax_prob(logits[0], nxt))
            if nxt in (eos, pad):
                break
            toks.append(nxt)
            if step == max_tokens:
                break
            logits, k, v = self.decode.run(None, {
                'input_ids': np.array([[nxt]], np.int64), 'past_attention_mask': mask,
                'position_id': np.array([step], np.int64), 'k_cache_in': k, 'v_cache_in': v})
            mask = np.concatenate([mask, np.ones((1, 1), np.int64)], axis=1)
        return self.tok.decode(toks, skip_special_tokens=True), float(np.mean(probs)), len(toks)


def all_readers():
    return [Shipped(), Baberu(), Hayai()]
