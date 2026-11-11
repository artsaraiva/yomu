import json,os
from PIL import Image, ImageDraw
from score import *
from score2 import match
IMG=REPO+'/vendor/open-mantra-dataset/images/'
THR=0.25
def gt_text(key,box):
    for g in GT.get(key,[]):
        if match(box,g): return g[4]
    return None
def sheet(items,path,cell=180,cols=8):
    rows=(len(items)+cols-1)//cols
    out=Image.new('RGB',(cols*cell,rows*cell),'white'); d=ImageDraw.Draw(out)
    for i,(key,box,label) in enumerate(items):
        im=Image.open(IMG+key).convert('RGB'); W,H=im.size
        x1,y1,x2,y2=[int(v) for v in box[:4]]; pad=int(0.15*max(x2-x1,y2-y1))+8
        crop=im.crop((max(0,x1-pad),max(0,y1-pad),min(W,x2+pad),min(H,y2+pad)))
        dr=ImageDraw.Draw(crop); dr.rectangle((x1-max(0,x1-pad)-0, y1-max(0,y1-pad), x2-max(0,x1-pad), y2-max(0,y1-pad)),outline='red',width=2)
        crop.thumbnail((cell-4,cell-18)); x=(i%cols)*cell; y=(i//cols)*cell
        out.paste(crop,(x+2,y+2)); d.text((x+2,y+cell-14),label[:30],fill='blue')
    out.save(path); print('wrote',path,len(items))

# 1. extras of ogkalu at THR (stretch), by class
extras=[]
for key,gts in GT.items():
    page=raw[key]; ks=kept(page['ogk_stretch'],THR,{1,2})
    for k in ks:
        if not any(match(k,g) for g in gts): extras.append((key,k,f"{os.path.basename(key)[:3]} c{k[5]} {k[4]:.2f}"))
print('ogk extras',len(extras),'text_bubble',sum(1 for e in extras if e[1][5]==1),'text_free',sum(1 for e in extras if e[1][5]==2))
sheet(extras,'/'.join([os.path.dirname(os.path.abspath(__file__)),'extras_ogk.png']))
yextras=[]
for key,gts in GT.items():
    for k in kept(raw[key]['yolo'],THR,{0}):
        if not any(match(k,g) for g in gts): yextras.append((key,k,f"{os.path.basename(key)[:3]} {k[4]:.2f}"))
print('yolo extras',len(yextras)); sheet(yextras,os.path.dirname(os.path.abspath(__file__))+'/extras_yolo.png')

# 2. GT boxes yolo scores under 0.10 (raw max conf over matching raw dets), what ogkalu gives them
low=[]
for key,gts in GT.items():
    page=raw[key]
    for g in gts:
        ym=max([d[4] for d in page['yolo'] if match(d,g)],default=0)
        if ym<0.10:
            om=[(d[4],d[5]) for d in page['ogk_stretch'] if match(d,g) and d[5] in (1,2)]
            best=max(om,default=(0,-1))
            low.append((key,g,f"y{ym:.2f} o{best[0]:.2f}c{best[1]} {g[4][:8]}"))
            print('yolo<0.10:',key,g[4][:20],'yolo',round(ym,2),'ogk',best)
sheet(low,os.path.dirname(os.path.abspath(__file__))+'/yolo_low.png')

# 3. satellites: small ogk bubble near a >=2.5x bubble
def near(a,b,px=15):
    return not (a[2]<b[0]-px or b[2]<a[0]-px or a[3]<b[1]-px or b[3]<a[1]-px)
stat={'yolo':{'own':0,'merged':0,'missed':0},'ogk':{'own':0,'merged':0,'missed':0}}
sat_rows=[]
for key,page in raw.items():
    bubbles=kept(page['ogk_stretch'],THR,{0})
    yk=kept(page['yolo'],THR,{0}); ok=kept(page['ogk_stretch'],THR,{1,2})
    for sb in bubbles:
        big=[b for b in bubbles if b is not sb and area(b)>=2.5*area(sb) and near(sb,b)]
        if not big: continue
        for name,ks in (('yolo',yk),('ogk',ok)):
            m=[k for k in ks if contained(k,sb)>=0.5 or contained(sb,k)>=0.5]
            if not m: st='missed'
            elif any(any(contained(b,k)>=0.5 or contained(k,b)>=0.5 for b in big) for k in m): st='merged'
            else: st='own'
            stat[name][st]+=1
        sat_rows.append((key,sb))
print('satellites',len(sat_rows),stat)

# 4. aside order demo
def order(boxes):
    """inPanelOrder without gutters: rows of vertically overlapping boxes top to bottom, each right to left."""
    s=sorted(boxes,key=lambda b:b[1]); rows=[]; cur=[s[0]]; reach=s[0][3]
    for b in s[1:]:
        if b[1]>=reach: rows.append(cur); cur=[]
        cur.append(b); reach=max(reach,b[3])
    rows.append(cur)
    return [b for r in rows for b in sorted(r,key=lambda b:-(b[0]+b[2])/2)]
def touches(a,b,px=10): return near(a,b,px)
for key in ['tencho_isoro/ja/010.jpg','tencho_isoro/ja/012.jpg','tencho_isoro/ja/029.jpg','tencho_isoro/ja/030.jpg','boureisougi/ja/011.jpg','boureisougi/ja/021.jpg']:
    page=raw[key]; bubbles=kept(page['ogk_stretch'],THR,{0}); texts=kept(page['ogk_stretch'],THR,{1,2})
    def lab(t): return f"{'F' if t[5]==2 else 'B'}:{(gt_text(key,t) or '?')[:14]}"
    base=order(texts)
    # rule: a text_free touching a bubble goes right after that bubble's text line
    moved=list(base)
    for t in [t for t in texts if t[5]==2]:
        parents=[b for b in bubbles if touches(t,b)]
        if not parents: continue
        parent=max(parents,key=lambda b:inter(t,b) if inter(t,b)>0 else -abs((t[0]+t[2])/2-(b[0]+b[2])/2))
        ptext=[x for x in texts if x[5]==1 and contained(x,parent)>=0.5]
        if not ptext: continue
        moved.remove(t); moved.insert(moved.index(ptext[0])+1,t)
    print(key)
    print('  base :',' | '.join(lab(t) for t in base))
    print('  moved:',' | '.join(lab(t) for t in moved))
    print('  yolo :',' | '.join((gt_text(key,t) or '?')[:14] for t in order(kept(page['yolo'],THR,{0}))))
