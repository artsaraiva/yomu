import json,os,sys
from score import *
def match(d,g): return contained(d,g[:4])>=0.5 or contained(g[:4],d)>=0.5
def score2(variant,thr,classes,small_h=60):
    tot=hit=0; merges=0; extras=0; kept_n=0; st=sh=0; missed=[]
    for key,gts in GT.items():
        page=raw.get(key)
        if page is None: continue
        ks=kept(page[variant],thr,classes); kept_n+=len(ks)
        for k in ks:
            m=[g for g in gts if match(k,g)]
            if len(m)>=2: merges+=1
            if len(m)==0: extras+=1
        for g in gts:
            tot+=1; h=any(match(k,g) for k in ks); hit+=h
            if (g[3]-g[1])<small_h: st+=1; sh+=h
            if not h: missed.append((key,g))
    return dict(gt=tot,hit=hit,rate=round(hit/tot,3),merges=merges,extras=extras,kept=kept_n,small=f'{sh}/{st}'),missed
if __name__=='__main__':
    for thr in [0.10,0.20,0.25,0.30,0.40,0.50]:
        for v,c in [('yolo',{0}),('ogk_stretch',{1,2}),('ogk_letterbox',{1,2})]:
            print(v.ljust(14),thr,score2(v,thr,c)[0])
    # missed by both at 0.25, and by yolo only
    my=set((k,g) for k,g in score2('yolo',0.25,{0})[1]); mo=set((k,g) for k,g in score2('ogk_stretch',0.25,{1,2})[1])
    print('missed yolo',len(my),'ogk',len(mo),'both',len(my&mo),'yolo-only',len(my-mo),'ogk-only',len(mo-my))
    print('ogk-only misses:'); [print(' ',k,g) for k,g in sorted(mo-my)]
    print('both misses (first 15):'); [print(' ',k,g) for k,g in sorted(my&mo)[:15]]
