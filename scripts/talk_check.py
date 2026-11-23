#!/usr/bin/env python3
"""Checks the talking-outro heuristic of phase 7 slice 4 (ADR 0104) against a profiles export.

The rule is the one in core/model TalkDetector, number for number: the tail (outro start to music end, the music end
capped at the song's duration) is talk when it lasts at least 8000 ms and its mean stored energy (RMS per second) is
at most 0.5 of the mean of the body (intro end to outro start). A second's bin belongs to a range when the middle of
that second lies in it. Nothing is written to the export; it is only read.

  python3 scripts/talk_check.py MusicPhoneProfiles.jsonl --match "Plutonio - Deja Vu"
  python3 scripts/talk_check.py MusicPhoneProfiles.jsonl --csv /tmp/talk.csv

Standard library only. Python 3.8 or later.
"""
import argparse
import csv
import json
import statistics
import sys
import unicodedata

# Layout of the 33 fixed floats (ADR 0065): 8 features, first beat, tempo, confidence, 3 candidates (2 floats each),
# 12 chroma bins, then music start, intro end, outro start, music end in seconds.
MUSIC_START, INTRO_END, OUTRO_START, MUSIC_END, FIXED_SIZE = 29, 30, 31, 32, 33
TALK_MIN_MS = 8000
TALK_ENERGY_RATIO = 0.5


def fold(text):
    """Lower case without accents, so 'Deja Vu' finds 'Déjà Vu'."""
    decomposed = unicodedata.normalize("NFD", text)
    return "".join(c for c in decomposed if not unicodedata.combining(c)).casefold()


def mean_between(curve, from_ms, to_ms):
    """Mean of the bins whose middle lies in [from_ms, to_ms). None when none, or when a value is not a level."""
    values = []
    for i, v in enumerate(curve):
        if from_ms <= i * 1000 + 500 < to_ms:
            if not isinstance(v, (int, float)) or v != v or v in (float("inf"), float("-inf")) or v < 0:
                return None
            values.append(v)
    return sum(values) / len(values) if values else None


def measure(song):
    """The numbers of one song, or None when the detector would say 'missing'."""
    fixed, curve, duration = song.get("fixed"), song.get("energy"), song.get("durationMs")
    if not fixed or len(fixed) < FIXED_SIZE or not curve or not isinstance(duration, (int, float)):
        return None
    secs = [fixed[i] for i in (MUSIC_START, INTRO_END, OUTRO_START, MUSIC_END)]
    if any(s != s or s in (float("inf"), float("-inf")) or s < 0 for s in secs):
        return None
    music_start, intro_end, outro_start, music_end = (round(s * 1000) for s in secs)
    if not music_start <= intro_end <= outro_start <= music_end:
        return None
    end = min(music_end, int(duration))
    if end <= outro_start or len(curve) * 1000 < end - 1000:
        return None
    body = mean_between(curve, intro_end, outro_start)
    tail = mean_between(curve, outro_start, end)
    if body is None or tail is None:
        return None
    tail_curve = [curve[i] for i in range(len(curve)) if outro_start <= i * 1000 + 500 < end]
    tail_ms = end - outro_start
    ratio = tail / body if body > 0 else float("inf")
    talk = tail_ms >= TALK_MIN_MS and body > 0 and tail <= TALK_ENERGY_RATIO * body
    third = max(1, len(tail_curve) // 3)
    first = sum(tail_curve[:third]) / third
    last = sum(tail_curve[-third:]) / third
    return {
        "path": song.get("path", "?"), "outro_start_ms": outro_start, "end_ms": end, "tail_ms": tail_ms,
        "body_mean": body, "tail_mean": tail, "ratio": ratio, "talk": talk, "tail_curve": tail_curve,
        # Last third of the tail over its first third: near 1 is a flat tail (talk), near 0 a fade-out.
        "decay": (last / first) if first > 0 else float("inf"),
    }


def shape(m):
    if m["decay"] <= 0.5:
        return "decaying"
    return "flat" if m["decay"] >= 0.7 else "in-between"


def mmss(ms):
    return "%d:%02d" % (ms // 60000, (ms // 1000) % 60)


def read_songs(path):
    with open(path, encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except ValueError:
                print("line %d is not JSON, skipped" % number, file=sys.stderr)
                continue
            if "fixed" in row:
                yield row


def show_match(path, needle):
    wanted = fold(needle)
    found = 0
    for song in read_songs(path):
        if wanted in fold(song.get("path", "")):
            found += 1
            m = measure(song)
            print("\n%s" % song.get("path"))
            if m is None:
                print("  no verdict: the profile has no usable boundaries or energy curve")
                continue
            print("  duration %s, outro start %s, music end %s, tail %.1f s" % (
                mmss(song["durationMs"]), mmss(m["outro_start_ms"]), mmss(m["end_ms"]), m["tail_ms"] / 1000))
            print("  body mean %.4f, tail mean %.4f, ratio %.3f (limit %.2f)" % (
                m["body_mean"], m["tail_mean"], m["ratio"], TALK_ENERGY_RATIO))
            print("  VERDICT: %s   tail shape: %s (last third / first third = %.2f)" % (
                "TALK" if m["talk"] else "not talk", shape(m), m["decay"]))
            print("  tail energy per second: " + " ".join("%.3f" % v for v in m["tail_curve"]))
    if not found:
        print("no song path contains %r" % needle)


def bar(count, total):
    return "#" * round(40 * count / total) if total else ""


def library(path, csv_path):
    rows, missing = [], 0
    for song in read_songs(path):
        m = measure(song)
        if m is None:
            missing += 1
        else:
            rows.append(m)
    total = len(rows)
    if not total:
        print("no song with a usable profile")
        return
    talk = [m for m in rows if m["talk"]]
    long_tail = [m for m in rows if m["tail_ms"] >= TALK_MIN_MS]
    print("songs read: %d usable, %d without a verdict" % (total, missing))
    print("tail of 8 s or more: %d (%.1f %%)" % (len(long_tail), 100 * len(long_tail) / total))
    print("FLAGGED AS TALK: %d (%.1f %% of usable songs)" % (len(talk), 100 * len(talk) / total))
    print("\nratio of tail to body, songs with a tail of 8 s or more:")
    edges = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]
    low = 0.0
    for edge in edges + [float("inf")]:
        n = sum(1 for m in long_tail if low <= m["ratio"] < edge)
        label = "%.1f-%.1f" % (low, edge) if edge != float("inf") else ">= 1.0   "
        print("  %s %5d %s%s" % (label, n, bar(n, len(long_tail)), "   <- talk if tail >= 8 s" if edge <= 0.5 else ""))
        low = edge
    if talk:
        tails = sorted(m["tail_ms"] for m in talk)
        print("\nflagged songs, tail length: median %.1f s, p10 %.1f s, p90 %.1f s, longest %.1f s" % (
            statistics.median(tails) / 1000, tails[len(tails) // 10] / 1000,
            tails[(len(tails) * 9) // 10] / 1000, tails[-1] / 1000))
        counts = {"flat": 0, "in-between": 0, "decaying": 0}
        for m in talk:
            counts[shape(m)] += 1
        print("flagged songs, tail shape (last third / first third): flat >= 0.7: %d, in-between: %d, "
              "decaying <= 0.5: %d (%.0f %%)" % (counts["flat"], counts["in-between"], counts["decaying"],
                                                  100 * counts["decaying"] / len(talk)))
    near = [m for m in rows if (m["tail_ms"] >= TALK_MIN_MS and TALK_ENERGY_RATIO < m["ratio"] <= 0.65) or
            (6000 <= m["tail_ms"] < TALK_MIN_MS and m["ratio"] <= TALK_ENERGY_RATIO)]
    print("near misses (ratio 0.5 to 0.65 with 8 s or more, or 6 to 8 s with ratio <= 0.5): %d" % len(near))

    def pick(group, n):
        group = sorted(group, key=lambda m: m["path"])
        step = max(1, len(group) // n)
        return group[::step][:n]

    samples = (("FLAT, flagged (likely talk)", pick([m for m in talk if shape(m) == "flat"], 10)),
               ("DECAYING, flagged (likely a fade-out, would be a false positive)",
                pick([m for m in talk if shape(m) == "decaying"], 10)),
               ("NEAR MISSES (not flagged)", pick(near, 10)))
    print("\nlisten to these: play the last 30 s of each and write down talk / music")
    for title, group in samples:
        print("\n%s" % title)
        for m in group:
            print("  %s  outro at %s, tail %.0f s, ratio %.2f, %s" % (
                m["path"], mmss(m["outro_start_ms"]), m["tail_ms"] / 1000, m["ratio"], shape(m)))
    if csv_path:
        with open(csv_path, "w", newline="", encoding="utf-8") as out:
            writer = csv.writer(out)
            writer.writerow(["path", "talk", "tail_s", "ratio", "shape", "decay", "outro_start", "body_mean", "tail_mean"])
            for m in sorted(rows, key=lambda r: r["path"]):
                writer.writerow([m["path"], int(m["talk"]), round(m["tail_ms"] / 1000, 1), round(m["ratio"], 3),
                                 shape(m), round(m["decay"], 3), mmss(m["outro_start_ms"]),
                                 round(m["body_mean"], 5), round(m["tail_mean"], 5)])
        print("\nall %d songs written to %s" % (len(rows), csv_path))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("export", help="the profiles export (JSON Lines, ADR 0056)")
    parser.add_argument("--match", help="show the verdict of songs whose path contains this text")
    parser.add_argument("--csv", help="library mode: write one row per song to this file")
    args = parser.parse_args()
    if args.match:
        show_match(args.export, args.match)
    else:
        library(args.export, args.csv)


if __name__ == "__main__":
    main()
