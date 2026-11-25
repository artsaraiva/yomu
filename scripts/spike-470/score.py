"""Spike #470: tables from run_pages.py's mantra.jsonl. Prints markdown."""
import json
import re
import sys
import unicodedata

import readers

GATE = 0.85
NAMES = ['shipped', 'baberu', 'hayai']


def norm(s):
    """Lenient: width, whitespace, and the form and length of dots, long marks, dashes and waves."""
    s = unicodedata.normalize('NFKC', s)
    s = re.sub(r'\s+', '', s)
    s = re.sub(r'[.・…‥。]{2,}|…|‥', '…', s)
    s = re.sub(r'[ー―—–─━-]+', 'ー', s)
    s = re.sub(r'[〜~～]+', '〜', s)
    return s


def edit(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def stats(pairs):
    """pairs: (prediction, truth), both normalised. Returns n, exact %, CER %, 1-NED %."""
    n = len(pairs)
    if not n:
        return 0, 0.0, 0.0, 0.0
    eds = [edit(p, g) for p, g in pairs]
    em = sum(p == g for p, g in pairs)
    cer = sum(eds) / max(1, sum(len(g) for _, g in pairs))
    ned = sum(1 - e / max(len(p), len(g), 1) for e, (p, g) in zip(eds, pairs)) / n
    return n, 100 * em / n, 100 * cer, 100 * ned


def main():
    rows = [json.loads(l) for l in open(readers.WORK + '/out/mantra.jsonl')]
    for r in rows:
        r['g'] = norm(r['gt'])
        for n in NAMES:
            r[n]['n'] = norm(r[n]['text'])
    classes = ['text_bubble', 'text_free', 'undetected', 'all']
    pick = lambda c: [r for r in rows if c == 'all' or r['cls'] == c]

    print('### Accuracy on OpenMantra boxes (every box read whole, no gate)\n')
    print('| Class | Boxes | Reader | Exact % | CER % | 1−NED % |\n|---|---|---|---|---|---|')
    for c in classes:
        for n in NAMES:
            k, em, cer, ned = stats([(r[n]['n'], r['g']) for r in pick(c)])
            print(f'| {c} | {k} | {n} | {em:.1f} | {cer:.2f} | {ned:.1f} |')

    print('\n### What each reader\'s own confidence says (gate 0.85)\n')
    print('| Class | Reader | Under gate | of which exact | Over gate | of which wrong |\n|---|---|---|---|---|---|')
    for c in classes:
        for n in NAMES:
            under = [r for r in pick(c) if r[n]['conf'] < GATE]
            over = [r for r in pick(c) if r[n]['conf'] >= GATE]
            print(f'| {c} | {n} | {len(under)} | {sum(r[n]["n"] == r["g"] for r in under)} | {len(over)} | '
                  f'{sum(r[n]["n"] != r["g"] for r in over)} |')

    print('\n### Shapes: what reaches the prompt\n')
    print('A box counts as delivered when its text passes the 0.85 gate of the reader that produced it; '
          'delivered-exact is a delivered box whose text is right.\n')
    print('| Class | Shape | Delivered | Delivered exact | Delivered wrong | Skipped | Time vs today |\n|---|---|---|---|---|---|---|')
    for c in classes:
        sel = pick(c)
        base = sum(r['shipped']['ms'] for r in sel)

        def line(label, chosen, cost):
            deliv = [(r, x) for r, x in chosen if x is not None]
            ok = sum(x['n'] == r['g'] for r, x in deliv)
            print(f'| {c} | {label} | {len(deliv)} | {ok} | {len(deliv) - ok} | {len(sel) - len(deliv)} | {cost / base:.2f}× |')

        line('today', [(r, r['shipped'] if r['shipped']['conf'] >= GATE else None) for r in sel], base)
        for n in NAMES[1:]:
            line(f'replace with {n}', [(r, r[n] if r[n]['conf'] >= GATE else None) for r in sel],
                 sum(r[n]['ms'] for r in sel))
            line(f'shipped, then {n} under the gate',
                 [(r, r['shipped'] if r['shipped']['conf'] >= GATE else (r[n] if r[n]['conf'] >= GATE else None)) for r in sel],
                 base + sum(r[n]['ms'] for r in sel if r['shipped']['conf'] < GATE))

    print('\n### CPU time per crop (4 threads, this desktop)\n')
    print('| Reader | Mean ms | Median ms | p90 ms | Ratio to shipped (mean) | Mean tokens |\n|---|---|---|---|---|---|')
    base = sum(r['shipped']['ms'] for r in rows) / len(rows)
    for n in NAMES:
        ms = sorted(r[n]['ms'] for r in rows)
        mean = sum(ms) / len(ms)
        print(f'| {n} | {mean:.0f} | {ms[len(ms) // 2]:.0f} | {ms[int(len(ms) * 0.9)]:.0f} | {mean / base:.1f}× | '
              f'{sum(r[n]["tokens"] for r in rows) / len(rows):.1f} |')

    if '--list' in sys.argv:
        print('\n### Boxes the shipped reader scores under 0.85\n')
        print('| Box | Class | Correct | Shipped | Baberu | Hayai |\n|---|---|---|---|---|---|')
        cell = lambda r, n: f'{"✓" if r[n]["n"] == r["g"] else "✗"} {r[n]["text"] or "(empty)"} ({r[n]["conf"]:.2f})'.replace('|', '¦')
        for r in sorted((r for r in rows if r['shipped']['conf'] < GATE), key=lambda r: (r['cls'], r['key'])):
            print(f'| {r["key"]} | {r["cls"]} | {r["gt"].replace("|", "¦")} | {cell(r, "shipped")} | {cell(r, "baberu")} | {cell(r, "hayai")} |')


if __name__ == '__main__':
    main()
