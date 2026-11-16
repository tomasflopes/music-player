exec(open("var.py").read().split("for v in [")[0])
for k,i in idx.items():
    a=top(i,"A"); n=top(i,"A1"); old=set(a)
    print(f"\n{k}: "+"; ".join(("*" if j not in old else "")+("[US] " if paths[j].startswith("Mús") else "")+paths[j].rsplit('/',1)[-1].replace('.mp3','')[:48] for j in n))
