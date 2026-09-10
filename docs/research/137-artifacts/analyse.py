#!/usr/bin/env python3
"""Summarise a spike-#137 logcat: per-arm latency, PSS, coverage, grammar overhead, token caps."""
import re, sys, statistics as st
from collections import defaultdict

log = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()

RESULT = re.compile(r"Result engine=(\S+) case=(\S+) bubbles=(\d+) durationMs=(\d+) pssKb=(\d+) covered=(\d+) sourceEchoes=(\d+)")
GEN = re.compile(r"Generation complete tokens=(\d+) resultLength=(\d+) durationMs=(\d+) sampleMs=([\d.]+) grammarMs=([\d.]+) grammarRejections=(\d+) grammar=(\d)")
ARM = re.compile(r"ARM_START arm=(\S+)")
OVER = re.compile(r"Prompt exceeds decode budget: (\d+) tokens")

arms, cur = defaultdict(lambda: {"rows": [], "gen": [], "over": []}), None
for line in log:
    m = ARM.search(line)
    if m:
        cur = m.group(1); arms[cur]
        continue
    m = OVER.search(line)
    if m and cur:
        arms[cur]["over"].append(int(m.group(1)))
        continue
    m = GEN.search(line)
    if m and cur:
        arms[cur]["gen"].append(dict(zip(
            ("tokens", "chars", "ms", "sample_ms", "grammar_ms", "rejections", "grammar"),
            (int(m[1]), int(m[2]), int(m[3]), float(m[4]), float(m[5]), int(m[6]), int(m[7])))))
        continue
    m = RESULT.search(line)
    if m:
        arms[m[1]]["rows"].append(dict(
            case=m[2], bubbles=int(m[3]), ms=int(m[4]), pss=int(m[5]),
            covered=int(m[6]), echoes=int(m[7])))

for arm, d in arms.items():
    rows, gen = d["rows"], d["gen"]
    if not rows:
        print(f"{arm}: no rows"); continue
    lat = [r["ms"] for r in rows]
    bub = sum(r["bubbles"] for r in rows)
    cov = sum(r["covered"] for r in rows)
    print(f"\n=== {arm} ===")
    print(f"  pages={len(rows)} bubbles={bub} covered={cov} ({cov/bub:.1%}) sourceEchoes={sum(r['echoes'] for r in rows)}")
    print(f"  latency/page ms: median={st.median(lat):.0f} mean={st.mean(lat):.0f} max={max(lat)} total={sum(lat)/1000:.1f}s")
    print(f"  peak PSS: {max(r['pss'] for r in rows)/1024:.0f} MiB (max over pages)")
    if gen:
        caps = [g for g in gen if g["tokens"] >= 768]
        print(f"  native calls={len(gen)} hitTokenCap={len(caps)}")
        print(f"  sampling ms/call: median={st.median([g['sample_ms'] for g in gen]):.2f} total={sum(g['sample_ms'] for g in gen):.0f}")
        if any(g["grammar"] for g in gen):
            gm = [g["grammar_ms"] for g in gen if g["grammar"]]
            toks = sum(g["tokens"] for g in gen if g["grammar"])
            print(f"  grammar ms/call: median={st.median(gm):.2f} total={sum(gm):.0f} "
                  f"=> {sum(gm)/max(toks,1):.4f} ms/token over {toks} tokens")
            print(f"  grammar rejections: total={sum(g['rejections'] for g in gen)} "
                  f"({sum(g['rejections'] for g in gen)/max(toks,1):.2%} of tokens)")
            print(f"  grammar share of sampling+grammar time: "
                  f"{sum(gm)/max(sum(gm)+sum(g['sample_ms'] for g in gen),1e-9):.1%}")
    if d["over"]:
        print(f"  PROMPT OVER BUDGET on {len(d['over'])} calls: {d['over']}")
