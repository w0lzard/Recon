# Recon

Share a short video to Recon from any Android app. It tells you what the video is about and looks up the specific
things it mentions (apps, books, places, repos, products), with sources where it can. Android 8+, sideload only.
Uses Gemini with your own Google account. See `docs/plan.md` for the full plan.

> **Personal use.** Recon is a personal tool. To analyse a video it downloads a temporary copy, then deletes it when
> the analysis finishes. Instagram, TikTok, X and other platforms may forbid downloading in their terms of service.
> You are responsible for what you share with Recon. Only share content you're allowed to process.
>
> Videos and screenshots are sent to Gemini with your Google account, through its website rather than an official
> API. Google's terms apply.

## Which APK

Release APKs keep the exact file names the Android build produces: `recon-<version>-<abi>-release.apk`.

- `recon-X.Y.Z-arm64-v8a-release.apk`: almost every phone from 2017 on.
- `recon-X.Y.Z-armeabi-v7a-release.apk`: older or Android Go phones.

If installing says "App not installed" or "not compatible", try the other one. The in-app update check looks for exactly these names.

## Install

1. On your phone, open the [Releases page](https://github.com/w0lzard/recon/releases) in the browser and download the APK (see Which APK).
2. Open it from the download notification or the Files app.
3. When Android says the app isn't allowed to install unknown apps, tap **Settings** and turn on **Allow from this source**
   for that browser or Files app. On most phones the path is Settings → Apps → Special app access → Install unknown apps;
   the wording varies by brand (Samsung, Xiaomi, etc.).
4. Go back and tap **Install**.
5. If Google Play Protect warns that the app is unrecognised, tap **More details → Install anyway**.
6. Optionally turn **Allow from this source** off again afterwards.

## Verify the download (optional)

- **Checksum:** each release has `SHA256SUMS.txt`. Run `sha256sum -c SHA256SUMS.txt --ignore-missing` next to the APK.
- **Signature:** `apksigner verify --print-certs recon-X.Y.Z-arm64-v8a-release.apk` must print this certificate SHA-256:

  `78:3E:03:0A:9C:18:76:70:82:20:53:D8:F8:E2:CC:C6:3F:B1:C4:0F:C8:81:7A:CA:AF:9A:09:93:DA:6D:31:00`

  (`apksigner` prints it lowercase without colons: `783e030a9c187670822053d8f8e2ccc63fb1c40fc8817acaaf9a0993da6d3100`.)

  Every Recon release is signed with this one key. A different fingerprint means the file is not from this project.

## Updating

Recon checks GitHub once a day and shows a banner on the home screen when a new version exists ("Download" or
"What's new"; Settings → About → **Check for updates** checks right away). Download the APK and install it over the
existing app; your history and sign-in are kept. You can also track releases with Obtainium.

## Sign in to Gemini

Recon uses Gemini with your Google account, the same way gemini.google.com does. On first start it shows Google's
sign-in page; once you're signed in, Recon keeps the session cookies encrypted on the phone and only sends them to Google.

> **Account risk.** Recon talks to Gemini through its website, not an official API. Google doesn't support this and
> may limit or suspend accounts that use it. Use an account you can afford to lose access to.

If Google refuses to sign you in inside the app, tap **Can't sign in here? Paste cookies**: open gemini.google.com in a
desktop browser, copy the `Cookie` request header of any request to gemini.google.com from the developer tools, and
paste it. Gemini's own usage limits for your account apply; when one is reached, the job fails with a message and you
can retry later. Jobs run in temporary chats, so they don't show up in your Gemini history.

## Privacy

- **Leaves the phone:** the video (or sampled frames and audio) goes to Gemini with your Google account. Names of
  things found go to GitHub and Wikipedia to look them up. The app asks GitHub for its latest release once a day.
- **Stays on the phone:** your history and your Gemini sign-in (encrypted with the Android Keystore). There is no Recon
  server and no analytics.
- **Google's retention:** Google may keep what you send to Gemini for up to 72 hours, even in temporary chats.

## Uninstall

Uninstall Recon like any app. That removes your history and your Gemini sign-in from the phone. To end the session
on Google's side too, sign out of all sessions in your Google account's security settings.

## Building

- **JDK:** pinned to **21** through Gradle toolchains: the Gradle daemon (`gradle/gradle-daemon-jvm.properties`), Kotlin compilation (`jvmToolchain(21)`) and unit tests all use it. The foojay resolver plugin downloads it if your machine doesn't have it. Bytecode targets Java 17.
- **Android SDK:** `compileSdk`/`targetSdk` 37, `minSdk` 26. Put `sdk.dir` in `local.properties`.
- **Tests:** `./gradlew :core:test :app:testDebugUnitTest`; instrumented: `scripts/connected-tests.sh` (an API 26 emulator is the reference device). It deletes old results, runs `connectedDebugAndroidTest` and fails unless the XML report ran exactly the expected number of tests (default: the `@Test` count in `app/src/androidTest`) with none failing, because Gradle can report BUILD SUCCESSFUL after an install failure with zero tests run.
- **Screenshots:** `ScreenshotsTest` writes PNGs to the app's external files dir; run it with `am instrument -e class dev.wolzard.recon.ui.ScreenshotsTest -e suffix _default …` and `adb pull` them (see `docs/screenshots/`). Not through Gradle, which uninstalls the app and its files afterwards.
- **JDK internals opened for unit tests (Robolectric only).** Robolectric's Android 16 (SDK 36) runtime reaches into JDK internals for `FileDescriptor` shared memory, so `app` unit tests run with:
  - `--add-opens=java.base/java.io=ALL-UNNAMED`
  - `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`

  These flags apply only to the test JVM (`tasks.withType<Test>` in `app/build.gradle.kts`), never to the app. Robolectric tests also use a plain `Application` (`app/src/test/resources/robolectric.properties`), because `ReconApp` starts Koin, which can only start once per JVM.
- **Release signing:** `keystore.properties` (git-ignored), created once by `scripts/make-release-keystore.sh` (Task 14; run it yourself in a terminal, it prompts for the password). Without it, release APKs are debug-signed and named `recon-<version>-UNSIGNED-FOR-RELEASE-<abi>-release.apk`. Debug APKs are always `recon-<version>-<abi>-debug.apk`.
