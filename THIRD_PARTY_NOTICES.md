# Third-Party Notices

This project includes and links against the following third-party software:

---

### 1. youtubedl-android
- **Artifact:** `io.github.junkfood02.youtubedl-android:library` and `common`
- **Version:** 0.18.1
- **Upstream Repository:** [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android)
- **Maintained Fork:** [junkfood02/youtubedl-android](https://github.com/junkfood02/youtubedl-android)
- **License:** GNU General Public License v3.0 (GPL-3.0)
- **License Link:** [https://www.gnu.org/licenses/gpl-3.0.html](https://www.gnu.org/licenses/gpl-3.0.html)

---

### 2. yt-dlp
- **Component:** Python package bundled within youtubedl-android
- **Version:** 2025.11.12 (bundled), with on-device self-update support
- **Source Repository:** [yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp)
- **License:** The Unlicense (Public Domain dedication)
- **License Link:** [https://unlicense.org/](https://unlicense.org/)

---

### 3. FFmpeg
- **Artifact:** `io.github.junkfood02.youtubedl-android:ffmpeg` (`libffmpeg.so`, `libffmpeg.zip.so`, `libffprobe.so`)
- **Version:** 7.1.1
- **Build Origin:** Termux Android build (`android-r28c-api-24-v1`)
- **Source Repository:** [FFmpeg](https://ffmpeg.org/) / [termux-packages](https://github.com/termux/termux-packages)
- **Build Flags:** `--enable-gpl --enable-version3 --enable-libx264 --enable-libx265 --enable-libopus --enable-libvpx --enable-libwebp --enable-shared ...`
- **License:** GNU General Public License v3.0 (GPL-3.0) (due to `--enable-gpl --enable-version3`, libx264, libx265)
- **License Link:** [https://www.gnu.org/licenses/gpl-3.0.html](https://www.gnu.org/licenses/gpl-3.0.html)
