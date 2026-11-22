#!/bin/sh
# Regenerates testdata/transitions/: 40 s click tracks at 90 and 92 BPM for the transition deviceCheck (ADR 0092).
# Licence-free (synthesised); kept apart from testdata/analysis so the profiler golden files stay as they are.
# Needs ffmpeg with libmp3lame. Output is committed; rerun only to change it.
set -eu
cd "$(dirname "$0")/.."
OUT="${1:-testdata/transitions}"
mkdir -p "$OUT"
rm -f "$OUT"/*.mp3

click() {
  period="$(awk "BEGIN{printf \"%.6f\", 60/$1}")"
  printf "aevalsrc='0.9*lt(mod(t,%s),0.005)*sin(2*PI*1000*t)':s=44100:d=40:c=mono" "$period"
}

for bpm in 90 92; do
  ffmpeg -v error -y -f lavfi -i "$(click $bpm)" -c:a libmp3lame -b:a 96k -write_id3v2 0 -map_metadata -1 \
    "$OUT/click${bpm}_40s_cbr_44100.mp3"
done
