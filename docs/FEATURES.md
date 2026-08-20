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
