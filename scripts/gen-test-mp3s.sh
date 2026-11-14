#!/bin/sh
# Regenerates testdata/analysis/: licence-free MP3s (synthesised signals only) and reference.tsv (ADR 0053).
# The reference is measured on the lossless source of each file, not on a decoder, so both decoders are judged
# against the same truth. Needs ffmpeg with libmp3lame and python3. Output is committed; rerun only to change it.
set -eu
cd "$(dirname "$0")/.."
OUT="${1:-testdata/analysis}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"
rm -f "$OUT"/*.mp3 "$OUT/reference.tsv"

SECONDS_LONG=10
: > "$TMP/index.tsv"

# make <name> <rate> <channels> <lavfi source> <encoder args...>
make() {
  name="$1"; rate="$2"; channels="$3"; src="$4"; shift 4
  ffmpeg -v error -y -f lavfi -i "$src" -c:a pcm_s16le "$TMP/$name.wav"
  ffmpeg -v error -y -f lavfi -i "$src" -c:a libmp3lame "$@" -write_id3v2 0 -map_metadata -1 "$OUT/$name.mp3"
  printf '%s\t%s\t%s\n' "$name" "$rate" "$channels" >> "$TMP/index.tsv"
}

click() { printf "aevalsrc='0.9*lt(mod(t,%s),0.005)*sin(2*PI*1000*t)':s=%s:d=%s:c=mono" "$(awk "BEGIN{printf \"%.6f\", 60/$1}")" "$2" "$SECONDS_LONG"; }
tone() { printf "aevalsrc='0.5*sin(2*PI*440*t)':s=%s:d=%s:c=mono" "$1" "$SECONDS_LONG"; }
sweep() { printf "aevalsrc='0.5*sin(2*PI*(100*t+395*t*t))':s=%s:d=%s:c=mono" "$1" "$SECONDS_LONG"; }
noise() { printf "anoisesrc=d=%s:c=white:r=%s:a=0.25:seed=7" "$SECONDS_LONG" "$1"; }
silence() { printf "aevalsrc=0:s=%s:d=%s:c=mono" "$1" "$SECONDS_LONG"; }
left() { printf "aevalsrc='0.5*sin(2*PI*440*t)|0':s=%s:d=%s:c=stereo" "$1" "$SECONDS_LONG"; }

CBR="-b:a 96k"
VBR="-q:a 4"
make click90_cbr_44100  44100 1 "$(click 90 44100)"  $CBR
make click120_cbr_44100 44100 1 "$(click 120 44100)" $CBR
make click150_cbr_44100 44100 1 "$(click 150 44100)" $CBR
make click120_vbr_44100 44100 1 "$(click 120 44100)" $VBR
make click120_cbr_48000 48000 1 "$(click 120 48000)" $CBR
make click120_vbr_48000 48000 1 "$(click 120 48000)" $VBR
make sine440_cbr_44100  44100 1 "$(tone 44100)"      $CBR
make sine440_vbr_48000  48000 1 "$(tone 48000)"      $VBR
make sweep_cbr_44100    44100 1 "$(sweep 44100)"     $CBR
make noise_cbr_44100    44100 1 "$(noise 44100)"     $CBR
make noise_vbr_48000    48000 1 "$(noise 48000)"     $VBR
make silence_cbr_44100  44100 1 "$(silence 44100)"   $CBR
make left440_cbr_44100  44100 2 "$(left 44100)"      -b:a 128k

python3 -I - "$TMP" "$OUT/reference.tsv" <<'PY'
import struct, sys, wave

tmp, out = sys.argv[1], sys.argv[2]
WINDOWS = [(1037, 2000), (4213, 2000), (7391, 2000)]
ONSET_LEVEL = 0.2

rows = ["# file, sampleRate, channels, durationMs, onsetMs (-1 = none), windowStartMs, windowLengthMs, monoRms"]
for line in open(f"{tmp}/index.tsv"):
    name, rate, channels = line.strip().split("\t")
    with wave.open(f"{tmp}/{name}.wav") as w:
        n, ch = w.getnframes(), w.getnchannels()
        data = struct.unpack("<%dh" % (n * ch), w.readframes(n))
    mono = [sum(data[i * ch:(i + 1) * ch]) / ch / 32768.0 for i in range(n)]
    sr = int(rate)
    onset = next((round(i * 1000 / sr) for i, v in enumerate(mono) if abs(v) > ONSET_LEVEL), -1)
    if not name.startswith("click"):
        onset = -1
    for start, length in WINDOWS:
        seg = mono[start * sr // 1000:(start + length) * sr // 1000]
        rms = (sum(v * v for v in seg) / len(seg)) ** 0.5
        rows.append("\t".join(map(str, [name, sr, ch, n * 1000 // sr, onset, start, length, f"{rms:.6f}"])))
open(out, "w").write("\n".join(rows) + "\n")
PY
echo "Wrote $(ls "$OUT"/*.mp3 | wc -l | tr -d ' ') MP3s and $OUT/reference.tsv"
