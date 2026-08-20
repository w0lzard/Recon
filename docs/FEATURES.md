# Recon: Features and User Flows

A guide to what Recon does and how each part is used, for new users and for developers joining the project.

## Table of contents

1. [Introduction](#introduction)
2. [Feature listing](#feature-listing)
   - [Sharing and capture](#sharing-and-capture)
   - [Analysis and research](#analysis-and-research)
   - [Results](#results)
   - [Account, privacy and settings](#account-privacy-and-settings)
3. [Feature flows](#feature-flows)
   - [First launch](#flow-first-launch)
   - [Signing in with Google](#flow-signing-in-with-google)
   - [Signing in with pasted cookies](#flow-signing-in-with-pasted-cookies)
   - [Sharing a video link](#flow-sharing-a-video-link)
   - [Sharing a video file](#flow-sharing-a-video-file)
   - [Pasting a link inside the app](#flow-pasting-a-link-inside-the-app)
   - [Getting the result after closing the sheet](#flow-getting-the-result-after-closing-the-sheet)
   - [Reading a result](#flow-reading-a-result)
   - [Picking the right GitHub repository](#flow-picking-the-right-github-repository)
   - [Recovering from a failed job](#flow-recovering-from-a-failed-job)
   - [Signing in again when the session expires](#flow-signing-in-again-when-the-session-expires)
   - [Signing out](#flow-signing-out)
   - [Adding a GitHub token](#flow-adding-a-github-token)
   - [Checking for updates](#flow-checking-for-updates)
4. [For developers](#for-developers)

---

## Introduction

Recon is an Android app that tells you what a short video is about and helps you look up the specific things it mentions: the app, the book, the GitHub repository, the song, the place, or the claim someone makes in a reel.

You share a video, or a link to one, from Instagram, TikTok, X, YouTube or any app with a share sheet. Recon downloads a temporary copy and sends it to Gemini, using **your own Google account through Gemini web** (gemini.google.com), in a temporary chat. Each thing Gemini names is then checked against GitHub and Wikipedia, so you get real links rather than guesses. Every result says how it was found and how sure Recon is.

Recon is a personal tool distributed as a sideloaded APK (no Play Store) with no analytics.

> **Account risk.** Gemini web is not an official API. Google doesn't support apps using it and may limit, suspend or ban the account. That risk is yours. Sign in with a secondary Google account, not the one you rely on.

---

## Feature listing

### Sharing and capture

- **Share sheet**: Sharing a link or a video file to Recon opens a bottom sheet over the app you are in, instead of opening Recon itself. The Add context step fits the sheet; the job and result rest at 40% of the screen and drag up to full height.
- **Add context (optional)**: Before a job starts you can type a question or note (up to 300 characters) or tap a suggestion chip: *What movie is this?*, *What song is this?*, *Who or what is this?*, *Explain this*. **Skip** starts at once with no context.
- **Link preview**: The Add context card shows the video's thumbnail, path and length (for example `0:38 video`), fetched in the background. It never delays Start; if it fails, a link icon stays in its place.
- **Shared video files**: A video file shared to Recon is copied immediately (Android's access to it is temporary) and gets the same Add context step. Closing the sheet before Start deletes the copy.
- **Paste a video link**: The Home screen has a button that reads a link from the clipboard and opens Add context.
- **Background processing**: Jobs run one at a time in a foreground worker. Closing the sheet or the app does not stop a job.
- **Result pop-up and notifications**: When a job succeeds while you are in another app, the sheet pops back up over it (needs the *Display over other apps* permission). Failures, and successes without that permission, arrive as a notification; tapping it opens the sheet.

### Analysis and research

- **Video analysis with Gemini**: The video (or frames and audio from it) goes to Gemini in a temporary chat, so it does not appear in your Gemini history. Gemini returns a summary, the things worth looking up, any text shown on screen, and an answer to your question if you asked one.
- **Typed findings**: Each finding has a type: repository, app, product, book, person, place, recipe, media, claim or other. Media findings carry a sub-type (movie, series, song, game, anime) with its own icon, shown as for example `MEDIA · MOVIE`.
- **Evidence labels**: Each finding says whether it was *Spoken*, *On-screen* or *Inferred*. Inferred findings also get a *Guess* badge.
- **GitHub lookup**: Repositories are matched against the GitHub API. When the match is uncertain, Recon lists candidates for you to choose from.
- **Wikipedia lookup**: People, places, books, products, apps, media and other findings are checked against Wikipedia. Media is only confirmed when the page matches the video's year.
- **Claim checking**: Claims get a verdict. Health, legal and financial claims need a named source to be anything but *Unclear*, and they show a *Not professional advice* line.
- **Sharper video**: When on-screen text is hard to read, *Try sharper video* runs again with more frames at higher resolution.

### Results

- **Your question and the answer card**: If you asked something, the result opens with your question and an answer card: a label such as `ANSWER · MEDIA · SONG`, the title, the reply, a status chip (*Verified*, *Unverified* or *Unclear*), a confidence percentage and numbered sources. An unverified answer without sources says *No source*.
- **Summary**: A short description of the video.
- **Entities found**: One card per finding with its type, verdict, evidence, where the information came from, key facts, a next step (*Install*, *Get it*, *Read*, *Watch or listen*, *Visit*, *Ingredients & steps*) and a link to GitHub or Wikipedia when there is one.
- **Verdicts**: *Confirmed*, *Supported*, *Best match*, *Unclear*, *Disputed*, *Not found* or *Not checked*. Status is never shown by colour alone; every chip has an icon and a label.
- **Where it came from**: *From GitHub*, *From Wikipedia*, *From web sources*, *Unverified, from AI knowledge* or *Only what the video said*.
- **Sources**: One numbered list for the whole result (`SOURCES · N`), including the answer's sources.
- **On-screen text**: The text Gemini read from the video, behind *Show on-screen text*.
- **History**: Home lists recent jobs with their thumbnail, title, site, time and status (*Queued*, *Downloading*, *Analysing*, *Looking up*, *Waiting for quota*, *Done*, *Failed*).

### Account, privacy and settings

- **First-run notice**: Explains downloading, what is sent to Google, the link lookup and the account-ban risk. It must be accepted before any share is processed and is shown again whenever its text changes.
- **Two-step sign-in**: An intro with a one-line explanation, a risk notice, **Sign in with Google** and **Paste cookies instead**. Google's sign-in page then fills the screen and follows your phone's dark mode, and the keyboard never hides Google's *Next* button.
- **Verified sign-in**: Cookies are saved only after one authenticated check with Google succeeds. Rejected cookies, network errors and Google consent or "verify it's you" pages each get their own message.
- **Locked-down sign-in page**: The sign-in page can only visit Google sign-in and Gemini pages; other links open in your browser. File and content access are off.
- **Encrypted storage**: The Google session and the optional GitHub token are encrypted with a key in the Android Keystore and excluded from backups and device transfers.
- **Sign out**: Removes the saved session and wipes the sign-in page's cookies, storage and cache.
- **Settings**: Gemini account (sign in again, sign out), optional GitHub token, privacy notes, update check, and About (notice, version).
- **Update check**: Recon checks GitHub Releases at most once a day and shows a banner when a newer version exists.

---
