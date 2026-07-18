# Recon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Per the owner's instruction this document is **plan only**. Tasks give files, interfaces, test cases (input → expected) and done-criteria; the code is written in the implementation phase, test-first.

**Goal:** Android app for a general audience: share a video link, and Recon works out what the video is about, identifies the things it mentions (repos, apps, products, books, people, places, recipes, claims), researches each one, and shows a plain-language explanation with sources.

**Architecture:** One `:app` module (Compose UI, Android adapters, Room, WorkManager) on top of a pure-Kotlin `:core` module (models, interfaces, the job pipeline). Model calls go through `VideoAnalyzer` and `LanguageModel` ports. Research goes through a chain of pluggable `Researcher`s: GitHub for repos, then Google Search grounding as the default for everything else and for anything GitHub could not resolve.

**Tech Stack:** Kotlin 2.x, Jetpack Compose + Material 3, Navigation Compose (type-safe routes), Koin, Room, OkHttp + kotlinx.serialization, Coroutines/Flow, WorkManager, youtubedl-android, Android Keystore, Gemini API (user's own key).

**Spec:** this file. There is no separate spec. `plan.md` is the single source of truth: when a decision changes, edit the Decisions log and the affected sections in the same commit.

---

## Change log

- **2026-10-08, model selection + housekeeping (owner):**
  - Model order is this key's last successful model first (sticky, keyed by a fingerprint of the key), then newest-first. A 503 puts a model on a 10-minute cooldown. A 429 never starts a cooldown; it only goes through quota handling (`ModelSelector`, D11).
  - JDK 21 is pinned through Gradle toolchains (daemon, compile, tests). The JDK internals opened for Robolectric are documented in the README.
  - APK assets use the actual AGP file names `recon-<version>-<abi>-release.apk`; T15 has a test per ABI.
  - The `spike/s1-s3` branch was audited: no key, no media. It does hold `manifest.tsv`, which contains the expected answers (ground truth); so does main.
- **2026-10-08, plan accepted + additions (owner):**
  1. **Models are discovered, not hard-coded.** At key setup (and when Settings → Test runs) the app calls `GET v1beta/models` and ranks the result (D11): newest `flash` first, then `flash-lite`. The two IDs in the code are last-resort defaults only.
  2. **429 daily quota → switch once** to the next ranked model with remaining local quota (a different quota bucket, not a retry). The same model is never retried on 429. The card shows which model answered.
  3. **Key-setup screen:** a note that enabling billing on the user's own key raises limits, linking to Google's pricing page and quoting no prices. Remaining **jobs** for today are shown, not only calls.
  4. `scratchpad/` added to `.gitignore`.
- **2026-10-08, post-spike decisions (owner):**
  1. **Search-grounding fallback applied.** The default researcher is now `KnowledgeResearcher`, which gives ungrounded answers labelled "Unverified, from AI knowledge" with verdicts capped at `BEST_MATCH`/`UNCLEAR`. `WikipediaResearcher` moves from H4 into the MVP (T10). Grounded `WebSearchResearcher` stays as an optional researcher, enabled only when a capability test with the user's own key succeeds (T5/T6).
  2. **Grounding diagnostic recorded** (Spike results → S1 diagnostic).
  3. **Call budget redesigned.** 1–2 AI calls per job (D9). Remaining-quota display, a `WAITING_QUOTA` state, a second model on 503, and no retry loops. Limits are read from errors, never hard-coded.
  4. **"No entity found" is an explicit valid output.** The prompt asks for an empty list when the model is unsure, and r10 is a test fixture.
  5. **GitHub verifier does fuzzy search from spoken names.** It shows the top 2–3 candidates for the user to pick, and r2 is a test fixture.
  6. **Defaults confirmed:**
     - Path A for non-YouTube links and shared files, Path B as fallback, YouTube URLs sent directly.
     - Model `gemini-3.5-flash`.
     - ≤ 480p h264 downloads, with 720p only as a user-triggered small-text fallback.
- **2026-10-11, backend swap (owner):** the official Gemini API with BYOK is replaced by the Gemini web backend (D22), reversing D4 and D14. Every user signs in with their own Google account and carries the account risk; the app warns about it on the sign-in screen, in Settings and in the personal-use notice (version 2). Removed: key setup, model discovery/ranking (D11), per-model quota tracking and Jobs-left (D9 counting), grounding capability test (D20, web researcher off until web citations are parsed).
  - Verified 2026-10-12: JVM tests 203/203 (8 new `GeminiWebClientTest` against MockWebServer fixtures, 3 `SignInViewModelTest`). `scripts/connected-tests.sh` on `recon_gate_api26`: 18 run, 17 passed; `YtDlpFetcherLargeTest` failed twice with YouTube's "Sign in to confirm you're not a bot" (HTTP 429) for this IP. That is external, but it now matters more: YouTube links are downloaded instead of being passed to the API. **Not yet verified: a live job through gemini.google.com** (needs the owner's sign-in), the WebView sign-in itself (Google may refuse embedded sign-in, so the paste path exists), and R8 on the release build.
- **2026-10-08, emulator gate (owner):** Four changes from the S3 emulator run. (1) The T7 YouTube video fallback is h264 only (`[vcodec^=avc1]`, `av01` excluded), because API 26 cannot decode AV1. (2) Zero extracted frames is a FAILED state with one retry on another format, never DONE (T7, T8, RunJob). (3) The 93 MB AVD peak is a **lower bound**: it came from a debug, R8-off spike app with no Gemini code. The gate is re-measured on the release build with the full pipeline (T17). (4) The real-phone low-end gate stays **open** until a phone with ≤ 3 GB RAM has run a job. It is no longer "if available".
- **2026-10-07, low-end (owner):** low-end support is a hard requirement. Added the Target device profile and low-end gate section. `minSdk` 27 → 26. yt-dlp format ≤ 480p. Temp files deleted on every exit path, so a failed job no longer keeps its download. S2 checks 480p readability, S3 checks armeabi-v7a cost. Spike device: Nothing Phone (2), labelled high-end.
- **2026-10-07, distribution (owner):** Recon is never published on Play. Play flavor, Play-policy notes, Play Integrity and the proxy are dropped; BYOK is the only access model. Added tasks: release keystore + backup (T14), GitHub Releases update check (T15), first-run personal-use notice (T16), README install guide (T17). Added risk R8 (developer verification, India). yt-dlp self-update at app start stays.
- **2026-10-07, scope change (owner):** general audience instead of GitHub-only. Typed entities + `Researcher` chain (D10, D13). Gemini web backend removed from v1 and parked (D4). Access model: BYOK with guided key setup (D14, Access model section). Distribution: GitHub Releases / sideload first (D15, Distribution section). Spike S1 repurposed to Search grounding.
- **2026-10-06:** first plan.

---

## Global Constraints

- Package / applicationId: `dev.wolzard.recon`
- Android only. `minSdk 26` (Android 8.0; API 26 lacks `getScaledFrameAtTime`, see Task 8). `targetSdk`/`compileSdk` = latest stable at Task 1.
- Free to run for the owner: no own backend, ever. Each user brings their own free Gemini API key (BYOK, the only access model). GitHub REST is unauthenticated, with an optional user token.
- Single model backend: `GeminiApiBackend`. The `VideoAnalyzer`/`LanguageModel` ports stay so a backend can be added later without UI changes.
- Distribution: signed APKs on GitHub Releases only (sideload). Recon is never published on Google Play.
- Personal use: first run shows a notice about platform terms that must be acknowledged before use (T16).
- Release build: R8 on, `isShrinkResources = true`, ABI splits `arm64-v8a` + `armeabi-v7a`, no universal APK, no bundled ML models.
- Low-end devices are a hard requirement: see Target device profile and low-end gate. In short: 3 GB RAM / API 26+ / both ARM ABIs. Videos ≤ 480p, YouTube never downloaded, jobs run one at a time, uploads streamed, frame bitmaps recycled one by one, and temp files deleted on every exit path.
- Secrets (Gemini API key, GitHub token) are encrypted at rest with an Android Keystore key. Never log them.
- Every failure the user sees has a specific message and a next-step action (see Failure table).
- Model-written text is display-only. Tappable links come only from API data: GitHub `html_url`, Wikipedia `content_urls` (`https://<lang>.wikipedia.org/`), or Google Search grounding source URIs (only when grounding is enabled). Links must be `https`.
- Answers from model knowledge (no sources) are always labelled **"Unverified, from AI knowledge"** and can never be `CONFIRMED` or `SUPPORTED`.
- Quota limits are never hard-coded. They are read from Gemini errors (`QuotaFailure`, `RetryInfo`) and stored per model (D9).
- When grounded results are shown (optional researcher, D13), the Google Search Suggestions widget (`searchEntryPoint.renderedContent`) is shown with them, as the Grounding with Google Search terms require.
- Output language for summaries and explanations = device locale. Text quoted from the video (`onScreenText`) stays verbatim.
- Downloaded media is used only for the analysis. It is deleted when the job finishes (DONE) and never shared or exported.
- Development uses a **throwaway Google account** for the AI Studio key.
- Library versions: latest stable at Task 1, pinned in `gradle/libs.versions.toml`.

## Review Focus

Inputs and conditions no single task's happy-path tests cover but a real user will hit. Each has a pinned test in the owning task.

1. **Messy shared text**: text with no URL, several URLs, trailing punctuation `)`/`.`/`…`, tracking params, or `m.`/`www.` hosts. Expected: the first valid http(s) URL, trimmed. No URL gives `UNSUPPORTED_LINK`. Pinned in Task 3.
2. **Video that mentions nothing researchable** (a dance clip, a meme; manifest r10). Expected: an empty `entities` list is a valid, complete analysis, and the job is DONE with summary and "Nothing specific to look up in this video". It must not end FAILED, and no research call is made. S2 showed Path A inventing an `inferred` "Kirby" for r10, so the prompt now tells the model to return an empty list when unsure. Pinned in Tasks 2, 6 and 11.
3. **Ambiguous or misspelled entity** (a spoken repo name garbled by speech recognition, manifest r2; a repo without owner, "that book by the guy from Atomic Habits", a place name shared by several cities). Expected: shown as "Best match" with sources, never as "Confirmed". A garbled repo name gets the top 2–3 GitHub candidates for the user to pick (T9). A repo GitHub can't find falls through to the next researcher. Pinned in Tasks 9 and 10.
4. **Claims.** A video asserts something false or unproven ("this drink cures diabetes"). Expected: never presented as fact. Without grounding (the default) the verdict is `Unclear`, labelled "Unverified, from AI knowledge", with what the evidence is believed to say. With grounding enabled it is `Disputed`/`Unclear` with sources. Pinned in Tasks 10 and 13.
5. **Process death, app swiped mid-job, long videos, non-English videos.** WorkManager re-runs the job. Room never shows a job stuck in a running state. A 20-minute video samples at most 20 frames evenly. A Hindi reel produces the summary in the device language with `onScreenText` verbatim. Pinned in Tasks 8, 11 and 12.

---

## Decisions log

| # | Decision | Why | Revisit if |
|---|----------|-----|------------|
| D1 | **Koin** over Hilt | No annotation processing, so faster builds and simple wiring. A ~20-binding graph does not need compile-time validation; a `verify()` unit test catches missing bindings. `koin-androidx-workmanager` covers worker injection. | Graph grows past ~50 bindings |
| D2 | **OkHttp** over Ktor | File-backed `RequestBody` streams uploads from disk. Smallest dependency. No multiplatform need. | KMP/iOS becomes a target |
| D3 | **Keystore AES-GCM + SharedPreferences ciphertext** instead of `EncryptedSharedPreferences` | `androidx.security:security-crypto` is deprecated. The replacement is ~40 lines behind `SecretStore`. | Owner insists on ESP. Only `SecretStore` changes. |
