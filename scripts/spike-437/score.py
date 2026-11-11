"""Score raw detections against OpenMantra text boxes. GT box 'covered' by a detection when
intersection/GT area >= 0.5. hit: covered by >=1 kept box; merge: a kept box covers >=2 GT; extra: kept box covers 0 GT."""
import json, sys, os
S=os.path.dirname(os.path.abspath(__file__))
REPO='/Users/ctw04928/Documents/Projects/personal-projects/yomu-manga-translation-app'
raw=json.load(open(S+'/raw.json'))
ann=json.load(open(REPO+'/vendor/open-mantra-dataset/annotation.json'))
GT={}
for book in ann:
    for pg in book['pages']:
        key=pg['image_paths']['ja'].split('images/')[1]
        GT[key]=[(t['x'],t['y'],t['x']+t['w'],t['y']+t['h'],t['text_ja']) for t in pg['text']]

def inter(a,b):
    w=max(0,min(a[2],b[2])-max(a[0],b[0])); h=max(0,min(a[3],b[3])-max(a[1],b[1])); return w*h
def area(a): return max(0,a[2]-a[0])*max(0,a[3]-a[1])
def iou(a,b):
    i=inter(a,b); u=area(a)+area(b)-i; return i/u if u>0 else 0
def contained(inner,outer):
    ai=area(inner); return inter(inner,outer)/ai if ai>0 else 0

def nms(dets, iou_t=0.80, cont_t=0.90):
    """Port of nonMaxSuppressDetections incl. the #370 swallow step."""
    s=sorted(dets,key=lambda d:-d[4]); kept=[]
    def redundant(c,others):
        return any(iou(c,o)>iou_t or contained(c,o)>=cont_t or contained(o,c)>=cont_t for o in others)
    for c in s:
        if not redundant(c,kept): kept.append(c)
    for c in [d for d in s if d not in kept]:
        sw=next((k for k in kept if contained(k,c)>=cont_t),None)
        if sw is None: continue
        if not redundant(c,[k for k in kept if k is not sw]): kept[kept.index(sw)]=c
    return kept

def kept(dets, thr, classes):
    return nms([d for d in dets if d[4]>=thr and d[5] in classes])

def score(variant, thr, classes, small_h=None):
    tot=hit=merged_gt=0; merges=0; extras=0; kept_n=0; small_tot=small_hit=0
    for key,gts in GT.items():
        page=raw.get(key); 
        if page is None: continue
        ks=kept(page[variant],thr,classes); kept_n+=len(ks)
        cover=[[g for g in gts if contained(g[:4],k)>=0.5] for k in ks]
        for k,c in zip(ks,cover):
            if len(c)>=2: merges+=1; merged_gt+=len(c)
            if len(c)==0: extras+=1
        for g in gts:
            tot+=1
            h=any(contained(g[:4],k)>=0.5 for k in ks)
            hit+=h
            if small_h and (g[3]-g[1])<small_h:
                small_tot+=1; small_hit+=h
    return dict(gt=tot,hit=hit,hit_rate=round(hit/tot,3),merges=merges,merged_gt=merged_gt,extras=extras,kept=kept_n,
                small=f'{small_hit}/{small_tot}' if small_h else None)

if __name__=='__main__':
    rows=[]
    for thr in [0.10,0.15,0.20,0.25,0.30,0.40,0.50]:
        rows.append(('yolo',thr,score('yolo',thr,{0},small_h=60)))
        rows.append(('ogk_stretch',thr,score('ogk_stretch',thr,{1,2},small_h=60)))
        rows.append(('ogk_letterbox',thr,score('ogk_letterbox',thr,{1,2},small_h=60)))
    for r in rows: print(r[0].ljust(14), r[1], r[2])
