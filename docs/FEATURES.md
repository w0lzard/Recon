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

## Feature flows

### Flow: First launch

1. User opens Recon.
2. The **Before you start** notice appears.
3. User reads it and taps **I understand**.
4. The sign-in intro (Step 2 of 2) appears. Continue with [Signing in with Google](#flow-signing-in-with-google) or [Signing in with pasted cookies](#flow-signing-in-with-pasted-cookies).

If the user shared something before accepting the notice, Recon holds that share and processes it right after **I understand**.

### Flow: Signing in with Google

1. On the sign-in intro, user taps **Sign in with Google**.
2. Google's sign-in page opens full screen under a slim bar with a back arrow and **Paste cookies**.
3. User enters their email, taps **Next**, enters their password and completes any Google checks.
4. When the page reaches gemini.google.com, Recon shows *Checking sign-in…* and makes one request to Gemini with the new session.
5. If the check passes, the session is saved and Recon opens Home.
6. If it fails, a message explains why (cookies rejected, no connection, or a Google consent or verification page). The user can retry or use pasted cookies.

### Flow: Signing in with pasted cookies

1. On a computer, user opens gemini.google.com in a browser and signs in.
2. In the browser's developer tools, user copies the **Cookie** header of any request to gemini.google.com.
3. On the phone, user taps **Paste cookies instead** on the intro, or **Paste cookies** on Google's page.
4. User pastes the header into the field and taps **Use these cookies**.
5. Recon checks the format first. If the text is empty, malformed or lacks the session cookie, a message says what is wrong.
6. Recon then checks the session with Google, as in the WebView flow. The field is emptied, and the clipboard is cleared if it still holds the cookies.

### Flow: Sharing a video link

1. In Instagram, TikTok, X, YouTube or another app, user taps **Share** on a video and picks **Recon**.
2. The share sheet opens over that app with **Add context**: the site, the link path and, once fetched, the thumbnail and length.
3. User either types a question, taps a suggestion chip, or leaves the field empty.
4. User taps **Start**, or **Skip** to start without context.
5. The sheet shows the job's progress: *Queued*, *Downloading*, *Analysing*, *Looking up*.
6. When the job finishes, the result appears in the sheet. User drags the sheet up to read all of it.
7. User closes the sheet by dragging it down or tapping the dimmed area above it.

If Recon is not set up yet (notice not accepted, or not signed in), the share opens the full app instead and is handled there.

### Flow: Sharing a video file

1. In a gallery, file manager or messaging app, user shares a video file to **Recon**.
2. Recon copies the file and the sheet opens with **Add context** (labelled *Shared video*).
3. User adds a question or taps **Skip**, then **Start**.
4. The job runs as for a link. Closing the sheet before **Start** deletes Recon's copy of the file.

### Flow: Pasting a link inside the app

1. User copies a video link in any app.
2. User opens Recon and taps **Paste a video link** on Home.
3. Recon validates the link and opens **Add context**.
4. User adds a question or skips, and the job opens on its own screen.

### Flow: Getting the result after closing the sheet

1. While a job is running in the sheet, Recon asks once to *display over other apps*.
2. User taps **Allow** and turns the permission on in Android settings, or taps **Not now**.
3. User closes the sheet and goes back to scrolling.
4. When the job finishes:
   - **Succeeded, permission on, Recon not open**: the sheet pops up over the current app with the result.
   - **Succeeded without the permission, or Recon open on another screen**: a *Recon finished* notification appears.
   - **Failed**: a *Recon couldn't finish* notification appears.
5. Tapping a notification opens the sheet on that job.
