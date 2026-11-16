import json, sys, collections, numpy as np
rows=[];paths=[]
with open(sys.argv[1]) as f:
    f.readline()
    for line in f:
        try: d=json.loads(line)
        except Exception: continue
        if len(d["fixed"])!=33: continue
        rows.append(d["fixed"]); paths.append(d["path"])
X=np.array(rows,dtype=np.float64); F=X[:,:8]
top=[p.split("/")[0] for p in paths]
print("top folders:",collections.Counter(top).most_common(8))
base=[p.rsplit("/",1)[-1].lower().replace(".mp3","") for p in paths]
dup=collections.Counter(base); print("filenames appearing more than once:",sum(1 for v in dup.values() if v>1),"songs involved:",sum(v for v in dup.values() if v>1))
def nz(col): 
    lo,hi=np.percentile(col,[5,95]); return np.clip((col-lo)/max(hi-lo,1e-12),0,1)
loud,lvar,cent,bass,onset,bs,zcr=[F[:,i] for i in (1,2,3,4,5,6,7)]
rel=lvar/np.maximum(loud,1e-9)
print("corr(rel dynamics, mean loudness) =",round(float(np.corrcoef(nz(rel),nz(loud))[0,1]),2)," corr(lvar, loud) =",round(float(np.corrcoef(nz(lvar),nz(loud))[0,1]),2))
A=np.stack([nz(cent),nz(bass),nz(onset),np.sqrt(nz(bs)),nz(rel)],1)*np.array([1.0,1.0,1.0,0.8,0.8])
B=np.stack([nz(loud),nz(lvar),nz(cent),nz(bass),nz(onset),nz(bs),nz(zcr)],1)
found=json.load(open("/tmp/dscan/found.json")); found["dillaz copo balao"]=2011
def nn(M,i,k=10):
    d=np.sqrt(((M-M[i])**2).sum(1)); d[i]=np.inf
    o=np.argsort(d)[:k+3]; o=[j for j in o if d[j]>1e-9 and base[j]!=base[i]][:k]
    return o,d
med=np.median(np.sqrt(((A[:2000,None,:]-A[None,:2000,:])**2).sum(-1)))
print("median pair distance (A):",round(float(med),2))
hh=0;tot=0;ov=[]
for name,i in found.items():
    o,d=nn(A,i); ob,_=nn(B,i)
    ov.append(len(set(o)&set(ob))/10)
    print(f"\n== {name}  [{paths[i]}]")
    for r,j in enumerate(o,1):
        tot+=1; hh+= top[j]==top[i]
        print(f"  {r:2d} d={d[j]:.2f} {paths[j].replace('.mp3','')}")
print("\nsame top-level folder share across all seeds' top-10:",round(hh/tot,2)," overlap A vs B top-10 (mean):",round(float(np.mean(ov)),2))
print("folder base rate of seed folders:", {k:round(v/len(top),2) for k,v in collections.Counter(top).items() if k in {top[i] for i in found.values()}})
