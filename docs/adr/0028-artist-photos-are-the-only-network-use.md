# 0028: Artist photos are the only network use, gated by a setting

Date: 2026-11-14 · Status: accepted

**Context.** Design 3 says the app is fully offline: no `INTERNET` permission, no network libraries (guard in build-logic, `NoInternetPermissionTest`). Artist photos need Deezer's public search API and the images it names.

**Decision.** One bounded exception. The manifest gains `INTERNET` and `ACCESS_NETWORK_STATE` (to tell Wi-Fi from mobile data); nothing else. Only the artist-photo source calls the network: a JSON search in `:data` (`HttpURLConnection`) and the image download in `:app` (Coil with `coil-network-okhttp`, `kotlinx-serialization-json` in `:core:artistimage`). The setting "Artist photos from the internet" (Off / Wi-Fi only / Always, default Wi-Fi only) is checked before every request, the stored lookup, a chosen online photo and the candidate list; Off makes no request at all. The only data sent is the artist name in the search query. `NoNetworkGuard` allows OkHttp and `coil-network` in `:app` only; Retrofit, Ktor, Volley, Cronet, Play services and Firebase stay forbidden everywhere.

**Consequences.** The "no network" guarantee becomes "no network except artist photos"; `NoInternetPermissionTest` now pins the exact permission set. Deezer sees the artist names of rows that are scrolled into view. Supersedes nothing; amends design 3.
