import json, sys, numpy as np
rows=[];paths=[];curves=[]
with open(sys.argv[1]) as f:
    f.readline()
    for line in f:
        try: d=json.loads(line)
        except Exception: continue
        if len(d["fixed"])!=33: continue
        rows.append(d["fixed"]); paths.append(d["path"]); curves.append(np.array(d["energy"],dtype=np.float64))
X=np.array(rows,dtype=np.float64); F=X[:,:8]
base=[p.rsplit("/",1)[-1].lower().replace(".mp3","") for p in paths]
def nz(c):
    lo,hi=np.percentile(c,[5,95]); return np.clip((c-lo)/max(hi-lo,1e-12),0,1)
loud,lvar,cent,bass,onset,bs,zcr=[F[:,i] for i in (1,2,3,4,5,6,7)]
rel=lvar/np.maximum(loud,1e-9)
def cf(c):
    c=c[1:-1] if len(c)>8 else c; r=c/(np.median(c)+1e-9)
    return [np.percentile(r,90)-np.percentile(r,10),(r>1.3).mean(),(r<0.5).mean()]
E=np.array([cf(c) for c in curves]); E=np.stack([nz(E[:,j]) for j in range(3)],1)
lt=np.log2(F[:,0])
def tdist(i): 
    x=np.abs(lt-lt[i])%1.0; return 2*np.minimum(x,1-x)
cols=np.stack([nz(cent),nz(bass),nz(onset),np.sqrt(nz(bs)),nz(rel)],1)
W0=np.array([1,1,1,.8,.8])
def dist(i,v):
    w=W0.copy(); extra=0
    if v in("A1","A4"): w[2]=0.5
    d2=(((cols-cols[i])*w)**2).sum(1)
    if v in("A2","A4"): d2+=(((E-E[i])*0.5)**2).sum(1)
    if v=="A3": d2+=(0.5*tdist(i))**2
    return np.sqrt(d2)
def top(i,v,k=10):
    d=dist(i,v); d[i]=np.inf
    o=[j for j in np.argsort(d)[:k+5] if base[j]!=base[i]][:k]; return o
seeds={"plutonio":"Plutonio - Tirar Bilhete","x22":"X-Tense - 22-22","eql":"X-Tense - EQlibrio","6lack":"6Lack - Disconnect","dillaz":"HH/Dillaz/Dillaz - Copo De Balão","smorra":"Smorra - Ruff Baby","sam":"Sam The Kid - Homem Do Meio","valete":"Valete - Fim Da Ditadura","xeg":"Xeg - Caminho Inverso","lhast":"Lhast - Guava","ed":"Ed - Tua Falta"}
idx={k:[i for i,p in enumerate(paths) if v in p][0] for k,v in seeds.items()}
for v in ["A1","A2","A3","A4"]:
    ov={k:len(set(top(i,v))&set(top(i,"A")))/10 for k,i in idx.items() if k!="valete"}
    print(f"\n### {v}  mean overlap with A on the 10 other seeds: {np.mean(list(ov.values())):.2f}  (min {min(ov.values()):.1f})")
    print("  valete top-10:"," | ".join(("[US] " if paths[j].startswith("Mús") else "")+paths[j].rsplit('/',1)[-1].replace('.mp3','')[:42] for j in top(idx['valete'],v)))
