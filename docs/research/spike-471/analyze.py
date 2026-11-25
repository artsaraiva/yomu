"""Spike #471: counts from spike page reports. Usage: analyze.py <report-dir>..."""
import json, statistics, sys
from pathlib import Path

def gap(a, b):
    dx = max(0, max(a[0], b[0]) - min(a[2], b[2]))
    dy = max(0, max(a[1], b[1]) - min(a[3], b[3]))
    return max(dx, dy)

def inside(t, b, f=0.9):
    iw = max(0, min(t[2], b[2]) - max(t[0], b[0])); ih = max(0, min(t[3], b[3]) - max(t[1], b[1]))
    area = (t[2]-t[0])*(t[3]-t[1])
    return area > 0 and iw*ih/area >= f

tot = {}
def add(k, n=1): tot[k] = tot.get(k, 0) + n
gains = []
for d in map(Path, sys.argv[1:]):
    p = json.loads((d/"page.json").read_text())
    W = p["pageWidth"]; tol = 0.01 * W
    # Photos' own toolbar (Share/Edit/Trash) sits below y=2580 on the 1280x2856 AVD (#364); not manga text.
    bs = [b for b in p["bubbles"] if b.get("spike") and b["bounds"][1] < 2580]
    balloons = p.get("balloons") or []
    order = p.get("readingOrder") or []
    print(f"== {d.name}: {len(bs)} text boxes, {len(balloons)} balloons, order {order}")
    add("pages"); add("text", len(bs)); add("balloons", len(balloons))
    for b in bs:
        s = b["spike"]
        add("label:"+s["label"]); add("today:"+s["todayOutcome"])
        src = s["shapeSource"] or "none:"+s["noShapeReason"]
        add("shape:"+src); add(f"today:{s['todayOutcome']}->{src}")
        if s["rectFontSize"] is not None:
            add("typeset")
            if s["shapedFontSize"] is not None:
                g = s["shapedFontSize"] - s["rectFontSize"]
                gains.append((g, s["rectLines"], s["shapedLines"], src, s["todayOutcome"]))
                if s["shapedFontSize"] >= 36: add("shaped at 36 cap")
                if s["rectFontSize"] >= 36: add("rect at 36 cap")
    # grouping
    for bal in balloons:
        n = sum(1 for b in bs if inside(b["bounds"], bal))
        if n >= 2: add("balloon holds >=2"); print("  balloon holds", n, [b["id"] for b in bs if inside(b["bounds"], bal)])
        if n == 0: add("empty balloon")
    for b in bs:
        if b["spike"]["label"] != "text_free": continue
        near = [bal for bal in balloons if gap(b["bounds"], bal) <= tol]
        if not near: continue
        add("text_free touching")
        bal = min(near, key=lambda x: gap(b["bounds"], x))
        parents = [o for o in bs if o is not b and o["spike"]["balloonBox"] and all(abs(u-v) < 0.5 for u, v in zip(o["spike"]["balloonBox"], bal))]
        msg = f"  text_free #{b['id']} {b['ocrText']!r} gap={gap(b['bounds'], bal):.0f}px"
        if parents and b["id"] in order and parents[0]["id"] in order:
            pi, ai = order.index(parents[0]["id"]), order.index(b["id"])
            changes = ai != pi + 1
            add("aside order would change" if changes else "aside already follows")
            msg += f" parent #{parents[0]['id']} {parents[0]['ocrText']!r} order parent@{pi} aside@{ai} -> {'CHANGES' if changes else 'same'}"
        print(msg)
for k in sorted(tot): print(f"{k}: {tot[k]}")
if gains:
    gs = [g for g, *_ in gains]
    best = [max(0, g) for g in gs]
    print(f"best-of (shaped only when larger): median gain {statistics.median(best):+.1f}px, >=+2px {sum(g>=2 for g in best)}/{len(best)}, mean {statistics.mean(best):+.1f}")
    print("shaped at the 36px cap:", sum(1 for g,_,_,_,_ in gains if False))
    print(f"shaped vs rect on {len(gs)}: median gain {statistics.median(gs):+.0f}px, >=+2px {sum(g >= 2 for g in gs)}, worse<=-1 {sum(g <= -1 for g in gs)}, within 1px {sum(-1 < g < 1 for g in gs)}")
    for src in sorted({x[3] for x in gains}):
        sub = [g for g, _, _, s, _ in gains if s == src]
        print(f"  {src}: n={len(sub)} median {statistics.median(sub):+.0f} >=+2 {sum(g>=2 for g in sub)} worse<=-1 {sum(g<=-1 for g in sub)} worse<=-2 {sum(g<=-2 for g in sub)}")
    print("  by today outcome:", {o: (len([g for g,_,_,_,t in gains if t==o]), statistics.median([g for g,_,_,_,t in gains if t==o])) for o in sorted({x[4] for x in gains})})
    print("  worse:", [(round(g,1), s, t) for g,_,_,s,t in gains if g <= -1])
    print("  lines rect->shaped median", statistics.median([r for _, r, _, _, _ in gains]), "->", statistics.median([s for _, _, s, _, _ in gains]))
