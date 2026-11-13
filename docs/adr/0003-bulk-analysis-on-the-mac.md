# 0003: Analyse the library on the Mac, import to the phone

Date: 2026-11-13 · Status: accepted

**Context.** A full sound analysis on the phone could take several nights for 16k songs; new songs must still be analysed on demand.

**Decision.** A Kotlin/JVM command-line tool reuses `:core:analysis` to analyse the whole library on the Mac and writes a file that the app imports. Songs not in the import are analysed on the phone. Files match by path relative to the music root plus file size.

**Consequences.** Two decoders (Mac and Android) must agree within a tolerance, checked by a golden-file test. The export carries `profiler_version`; a mismatch is rejected.
