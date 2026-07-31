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

