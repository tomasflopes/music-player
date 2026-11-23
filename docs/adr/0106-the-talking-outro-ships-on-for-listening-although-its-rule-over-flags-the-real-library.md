# 0106: The talking outro ships on for listening although its rule over-flags the real library

Date: 2026-11-23 · Status: proposed · Extends 0104

**Context.** The owner ran `scripts/talk_check.py` on the real export (13,2xx usable songs). The rule of ADR 0104 (tail of 8 s or more, mean RMS at most 0.5 of the body's) flagged 5,657 songs, 42.7 %, against an expected few percent. Of the flagged tails 66 % (3,753) decay (last third at most half of the first third), which is a fade-out, not speech; 1,174 are flat. The ratio of the flagged songs is spread over 0.0 to 0.5, so the two numbers are not the problem. The one song the owner named, Plutonio - Deja Vu, is not flagged: its stored tail is 5.2 s, and the talking starts at 2:30 over the beat, where the energy never drops. Neither case can be fixed by a threshold on the stored curve.

**Decision.** `TransitionConfig.talkEnabled` stays true: the owner wants to listen to it on the phone before judging, so the default does not change. The rule is unchanged. After listening to the 10 flat songs of the script's sample, if most are real spoken outros: a flat-tail guard (last third of the tail at least 0.7 of the first) in a new ADR that supersedes the rule of 0104, re-run through the script. Otherwise the default goes to false.

**Consequences.** While it is on, about 43 % of songs with a tail of 8 s or more are treated as talk, most of them fade-outs, so expect a duck and a long overlap where a plain crossfade or beat match was planned. Talk over a beat is out of reach of this data; the options are a per-song override or a speech detector in a later analysis phase (a new `profiler_version` and a Mac re-run, which phase 7 excludes).
