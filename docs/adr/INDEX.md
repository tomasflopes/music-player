# ADR index

One line per decision. Open only the ADRs relevant to the task. Accepted ADRs are never edited, only superseded.

| # | Decision | Status |
|---|---|---|
| [0001](0001-no-cloud-ci.md) | No cloud CI; `./gradlew check` runs locally, enforced by a pre-push hook | accepted |
| [0002](0002-min-sdk-android-17.md) | minSdk, targetSdk and compileSdk are all Android 17 (API 37) | accepted |
| [0003](0003-bulk-analysis-on-the-mac.md) | Analyse the library on the Mac, import to the phone | accepted |
| [0004](0004-read-id3-tags-from-files.md) | Read ID3 tags from each file, not from MediaStore | accepted |
| 0005 | Folder picker to MediaStore: reserved for the result of the phase 1 spike (plan Task 2) | pending |
| [0006](0006-hand-written-id3-reader.md) | Hand-written ID3 reader in `:core:tags`, no tag library | accepted |
