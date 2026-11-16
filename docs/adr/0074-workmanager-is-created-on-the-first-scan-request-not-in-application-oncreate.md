# 0074: WorkManager is created on the first scan request, not in Application.onCreate

Date: 2026-11-16 · Status: proposed · Extends 0014

**Context.** `MusicPlayerApp.onCreate` injects `MediaStoreObserver`, whose Hilt chain reaches `WorkManagerScanController`, and the provider called `WorkManager.getInstance` while building it. That created WorkManager (its own Room database, schedulers, configuration and the Hilt worker factory) on the main thread before the first frame. Nothing in the first frame needs it: the first scan request comes from the observer's flows, which run on `Dispatchers.Default`.

**Decision.** `WorkManagerScanController` takes a `Lazy<WorkManager>` and the provider passes `lazy { WorkManager.getInstance(context) }`. WorkManager is created by the first `requestScan()`, which already runs off the main thread. The observer, the debounce and the unique work `library-scan` with policy KEEP are unchanged. The Room database (`MusicDatabase`) and DataStore were already lazy and are untouched.

**Consequences.** Application start does less on the main thread; the cost moves to a background thread a few milliseconds later. A scan still starts at app start. Not measured yet: compare `adb shell am start -W` (TotalTime, WaitTime) before and after on the phone. A baseline profile and a Macrobenchmark module were considered and left out of this batch because they add a module that cannot be verified without a device run.
