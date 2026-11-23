#!/usr/bin/env python3
"""Phase 7 tempo-confidence calibration (ADR 0098). Read-only on the profiles jsonl.

  scripts/tempo_calibration.py analyse [profiles.jsonl]   step A: distributions + calibration/sample.csv
  scripts/tempo_calibration.py clicks  [--music-root DIR]   step C: click-check audio files
  scripts/tempo_calibration.py fit     [profiles.jsonl]   step D: gate table + pair simulation

Fixed-float order (ADR 0065): 0 tempo_bpm, 6 beat_strength, 8 first_beat_s, 9 beat_tempo_bpm,
10 tempo_confidence, 11/13/15 candidate BPMs, 12/14/16 candidate scores (candidate 1 is always 1.0).
Stored confidence = pulse x (share - 1/3) x 1.5 where share = 1 / (1 + s2 + s3); the pulse factor
is not stored, so `implied pulse` is derived here, never stored.
"""
import csv, json, os, random, sys
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_JSONL = os.path.join(ROOT, "MusicPhoneProfiles.jsonl")
CAL_DIR = os.path.join(ROOT, "calibration")
SAMPLE = os.path.join(CAL_DIR, "sample.csv")
BUCKETS = [(0.0, 0.1), (0.1, 0.2), (0.2, 0.35), (0.35, 0.5), (0.5, 1.01)]
SEED = 98


def load(path):
    paths, X = [], []
    with open(path) as f:
        f.readline()
        for line in f:
            try:
                d = json.loads(line)
            except ValueError:
                continue
            if len(d["fixed"]) != 33:
                continue
            paths.append(d["path"])
            X.append(d["fixed"])
    return paths, np.array(X, dtype=np.float64)


def derived(X):
    s2, s3 = X[:, 14], X[:, 16]
    base = (1.0 / (1.0 + s2 + s3) - 1 / 3) * 1.5
    ratio = np.where(s2 > 0, 1.0 / np.maximum(s2, 1e-9), np.inf)  # cand1 / cand2 score
    pulse = np.where(base > 1e-9, X[:, 10] / np.maximum(base, 1e-9), 0.0)
    return base, ratio, pulse


def rel(a, b):
    r = a / np.maximum(b, 1e-9)
    out = np.full(len(a), "other", dtype=object)
    out[np.abs(r - 1) <= 0.01] = "same"
    out[np.abs(r - 0.5) <= 0.01] = "half"
    out[np.abs(r - 2) <= 0.01] = "double"
    return out


def pct(x, qs=(1, 5, 10, 25, 50, 75, 90, 95, 99)):
    return {f"p{q}": round(float(np.percentile(x, q)), 3) for q in qs}


def counts(arr):
    u, c = np.unique(arr, return_counts=True)
    return {str(k): int(v) for k, v in zip(u, c)}


def analyse(path):
    paths, X = load(path)
    n = len(paths)
    conf = X[:, 10]
    base, ratio, pulse = derived(X)
    fin_mask = np.isfinite(ratio)
    print(f"songs: {n}")
    print("confidence:", pct(conf), "max", round(float(conf.max()), 3))
    print("buckets:", {f"{lo}-{hi}": int(((conf >= lo) & (conf < hi)).sum()) for lo, hi in BUCKETS})
    print("songs with conf >= t:", {t: int((conf >= t).sum()) for t in (0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.5)})
    print("cand1/cand2 score ratio:", pct(ratio[fin_mask]), "(cand2 score 0:", int((~fin_mask).sum()), ")")
    print("share-only confidence (no pulse):", pct(base))
    print("implied pulse factor:", pct(pulse))
    print("beat_strength:", pct(X[:, 6]))
    print("\ntempo_bpm vs beat_tempo_bpm:", counts(rel(X[:, 0], X[:, 9])))
    print("beat_tempo vs candidate_1:", counts(rel(X[:, 9], X[:, 11])))
    print("candidate_2 vs candidate_1:", counts(rel(X[:, 13], X[:, 11])))
    print("candidate_3 vs candidate_1:", counts(rel(X[:, 15], X[:, 11])))
    top2 = rel(X[:, 13], X[:, 11])
    print("cand2 is half/double of cand1, per confidence bucket:")
    for lo, hi in BUCKETS:
        m = (conf >= lo) & (conf < hi)
        mm = m & fin_mask
        hd = np.isin(top2[m], ["half", "double"]).mean() if m.any() else 0
        print(f"  {lo}-{hi}: n={int(m.sum())}, half/double {hd:.2f}, median ratio {np.median(ratio[mm]) if mm.any() else 0:.2f}")
    write_sample(paths, X)


def write_sample(paths, X):
    rng = random.Random(SEED)
    conf = X[:, 10]
    hh = np.array([p.startswith("HH/") for p in paths])
    bpm = X[:, 9]
    os.makedirs(CAL_DIR, exist_ok=True)
    rows = []
    for lo, hi in BUCKETS:
        idx = np.where((conf >= lo) & (conf < hi))[0]
        pick = []
        for want_hh, n_want in ((True, 6), (False, 2)):
            pool = sorted((i for i in idx if hh[i] == want_hh), key=lambda i: bpm[i])
            for k in range(n_want):
                t = pool[k * len(pool) // n_want:(k + 1) * len(pool) // n_want]
                if t:
                    pick.append(int(rng.choice(t)))
        rest = [int(i) for i in idx if int(i) not in pick]
        rng.shuffle(rest)
        while len(pick) < 8 and rest:
            pick.append(rest.pop())
        rows += pick[:8]
    with open(SAMPLE, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["path", "stored_bpm", "confidence", "first_beat_s", "true_bpm", "tempo_ok", "beat_ok"])
        for i in rows:
            w.writerow([paths[i], X[i, 9], round(X[i, 10], 4), round(X[i, 8], 3), "", "", ""])
    print(f"\nwrote {SAMPLE}: {len(rows)} rows, {sum(bool(hh[i]) for i in rows)} hip-hop")


SR = 44100
CLICK_SECONDS = 30.0


def click_burst(freq, ms=25, gain=0.5):
    n = int(SR * ms / 1000)
    t = np.arange(n) / SR
    return (gain * np.sin(2 * np.pi * freq * t) * np.exp(-t * 180)).astype(np.float32)


def decode_excerpt(src, start, seconds):
    import subprocess
    cmd = ["ffmpeg", "-v", "error", "-ss", f"{start:.3f}", "-t", f"{seconds}", "-i", src,
           "-f", "f32le", "-ac", "2", "-ar", str(SR), "-"]
    raw = subprocess.run(cmd, check=True, stdout=subprocess.PIPE).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).copy()


def clicks(args):
    """Step C: 30 s of each sample song with a click on every beat of the stored grid."""
    import subprocess
    opt = {"--music-root": os.path.expanduser("~/Music/Music"), "--where": "late",
           "--csv": SAMPLE, "--jsonl": DEFAULT_JSONL, "--out": os.path.join(CAL_DIR, "clicks")}
    for a, b in zip(args, args[1:]):
        if a in opt:
            opt[a] = b
    paths, X = load(opt["--jsonl"])
    index = {p: i for i, p in enumerate(paths)}
    os.makedirs(opt["--out"], exist_ok=True)
    hi, lo = click_burst(1800, gain=0.6), click_burst(1000, gain=0.4)
    with open(opt["--csv"]) as f:
        rows = list(csv.DictReader(f))
    for n, r in enumerate(rows, 1):
        i = index[r["path"]]
        bpm, first = X[i, 9], X[i, 8]
        music_start, outro_start, music_end = X[i, 29], X[i, 31], X[i, 32]
        if opt["--where"] == "early":
            start = max(0.0, first)
        else:  # late: where an outgoing song switches, the hardest place for the grid to hold
            start = max(music_start, min(outro_start, music_end) - CLICK_SECONDS)
        src = os.path.join(opt["--music-root"], r["path"])
        audio = decode_excerpt(src, start, CLICK_SECONDS)
        period = 60.0 / bpm
        k0 = int(np.ceil((start - first) / period))
        k = k0
        while True:
            t = first + k * period - start
            if t >= len(audio) / SR:
                break
            if t >= 0:
                b = hi if k % 4 == 0 else lo
                s0 = int(round(t * SR))
                e0 = min(len(audio), s0 + len(b))
                audio[s0:e0] += b[:e0 - s0, None]
            k += 1
        name = f"{n:02d} - {os.path.basename(r['path'])[:-4]}.flac"
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ac", "2", "-ar", str(SR), "-i", "-",
                        os.path.join(opt["--out"], name)], input=np.clip(audio, -1, 1).tobytes(), check=True)
        print(f"{name}: {opt['--where']} from {start:.1f}s, {bpm} BPM, first beat {first}s, {k - k0} clicks")


def wilson_lo(k, n, z=1.96):
    if n == 0:
        return float("nan")
    ph = k / n
    d = 1 + z * z / n
    c = ph + z * z / (2 * n)
    m = z * np.sqrt(ph * (1 - ph) / n + z * z / (4 * n * n))
    return (c - m) / d


def signals(X):
    """Every per-song signal a gate can use, all derived from the stored floats."""
    conf = X[:, 10]
    base, ratio, pulse = derived(X)
    s2, s3 = X[:, 14], X[:, 16]
    octv = lambda a, b: (np.abs(a / np.maximum(b, 1e-9) - 0.5) <= 0.01) | (np.abs(a / np.maximum(b, 1e-9) - 2) <= 0.01)
    s2o = np.where(octv(X[:, 13], X[:, 11]), 0.0, s2)   # an octave-related peak supports the tempo family
    s3o = np.where(octv(X[:, 15], X[:, 11]), 0.0, s3)
    base_oct = (1.0 / (1.0 + s2o + s3o) - 1 / 3) * 1.5
    rival = np.maximum(s2o, s3o)                          # strongest non-octave rival, relative to the best
    return {
        "conf": conf,
        "conf_oct": np.minimum(1.0, pulse * base_oct),
        "pulse": pulse,
        "ratio": ratio,                                   # best / second candidate
        "ratio_oct": np.where(rival > 0, 1.0 / np.maximum(rival, 1e-9), 99.0),
        "beat_strength": X[:, 6],
    }


def fit(jsonl):
    paths, X = load(jsonl)
    n = len(paths)
    index = {p: i for i, p in enumerate(paths)}
    with open(SAMPLE, encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    si = np.array([index[r["path"]] for r in rows])
    beat = np.array([r["beat_ok"] == "y" for r in rows])
    fam = np.array([r["tempo_ok"] in ("y", "half", "double") for r in rows])
    sig = signals(X)
    conf = sig["conf"]
    bucket = np.digitize(conf, [b[1] for b in BUCKETS[:-1]])
    out = []
    P = out.append
    P(f"sample: {len(rows)} songs; beat_ok y {int(beat.sum())}; tempo family ok {int(fam.sum())}; "
      f"tempo_ok y {sum(r['tempo_ok']=='y' for r in rows)}, half {sum(r['tempo_ok']=='half' for r in rows)}, "
      f"double {sum(r['tempo_ok']=='double' for r in rows)}, n {sum(r['tempo_ok']=='n' for r in rows)}")
    P("\nper confidence bucket (population n, sample beat_ok y / sample, tempo family ok):")
    for b, (lo, hi) in enumerate(BUCKETS):
        m = bucket[si] == b
        P(f"  {lo}-{hi}: pop {int((bucket == b).sum())}, beat_ok {int(beat[m].sum())}/{int(m.sum())}, family {int(fam[m].sum())}/{int(m.sum())}")
    P("\nstored tempo resolution: share of songs whose beat tempo is a whole number: "
      f"{np.mean(np.abs(X[:, 9] - np.round(X[:, 9])) < 0.05):.2f}")

    gates = {}
    def add(name, mask):
        gates[name] = mask
    add("today: conf >= 0.5", conf >= 0.5)
    for t in (0.15, 0.2, 0.25, 0.3, 0.35, 0.4):
        add(f"conf >= {t}", conf >= t)
    for t in (0.2, 0.3, 0.4, 0.5):
        add(f"conf_oct >= {t}", sig["conf_oct"] >= t)
    for t in (1.5, 2.0, 2.5, 3.0):
        add(f"ratio >= {t}", sig["ratio"] >= t)
    for t in (1.5, 2.0, 3.0):
        add(f"ratio_oct >= {t}", sig["ratio_oct"] >= t)
    for t in (0.2, 0.3):
        add(f"beat_strength >= {t}", sig["beat_strength"] >= t)
    add("conf >= 0.2 & beat_strength >= 0.2", (conf >= 0.2) & (sig["beat_strength"] >= 0.2))
    add("conf >= 0.25 & ratio_oct >= 2", (conf >= 0.25) & (sig["ratio_oct"] >= 2))
    add("conf_oct >= 0.3 & beat_strength >= 0.2", (sig["conf_oct"] >= 0.3) & (sig["beat_strength"] >= 0.2))

    # 100,000 random pairs = adjacent pairs of a uniform shuffle
    rng = np.random.default_rng(SEED)
    a = rng.integers(0, n, 100000)
    b = rng.integers(0, n - 1, 100000)
    b = b + (b >= a)
    bpm = X[:, 9]
    r = bpm[a][:, None] / (bpm[b][:, None] * np.array([1.0, 0.5, 2.0])[None, :])
    tempo_ok_pair = (np.abs(r - 1).min(axis=1) <= 0.08 + 1e-9)

    P("\ngate | eligible songs (of %d) | sample n | beat_ok precision (Wilson low) | family precision | pop-weighted beat_ok | est. flam share of matches (1-p^2) | pairs with a beat match" % n)
    for name, mask in gates.items():
        e = mask[si]
        k, m = int(beat[e].sum()), int(e.sum())
        kf = int(fam[e].sum())
        pair = float(np.mean(mask[a] & mask[b] & tempo_ok_pair))
        prec = f"{k}/{m} = {k / m:.0%} ({wilson_lo(k, m):.0%})" if m else "n/a"
        precf = f"{kf}/{m} = {kf / m:.0%}" if m else "n/a"
        # population-weighted precision: the sample is balanced over confidence buckets, the library is not
        num = den = 0.0
        for bk in range(len(BUCKETS)):
            pop_b = int((mask & (bucket == bk)).sum())
            mb = e & (bucket[si] == bk)
            allb = bucket[si] == bk
            pb = beat[mb].mean() if mb.any() else beat[allb].mean()
            num += pop_b * pb
            den += pop_b
        pw = num / den if den else float("nan")
        P(f"{name:42s} | {int(mask.sum()):6d} | {m:3d} | {prec:22s} | {precf:12s} | {pw:5.0%} | {1 - pw * pw:5.0%} | {pair:.2%}")
    P(f"\nceiling: pairs whose stored tempos are within the 8 % stretch at x1, x0.5 or x2, whatever the gate: {float(np.mean(tempo_ok_pair)):.2%}")
    P("(pairs: uniform random pairs, both songs eligible and TempoMatcher within 8 % at x1, x0.5 or x2 of the stored beat tempo; "
      "the planner's switch-point and overlap fit are not simulated, so these are upper bounds)")
    text = "\n".join(out)
    print(text)
    with open(os.path.join(CAL_DIR, "fit_report.txt"), "w", encoding="utf-8") as f:
        f.write(text + "\n")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "analyse"
    if cmd == "analyse":
        analyse(sys.argv[2] if len(sys.argv) > 2 else DEFAULT_JSONL)
    elif cmd == "fit":
        fit(sys.argv[2] if len(sys.argv) > 2 else DEFAULT_JSONL)
    elif cmd == "clicks":
        clicks(sys.argv[2:])
    else:
        sys.exit(f"command not implemented yet: {cmd}")
