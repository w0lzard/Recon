package spike.ytdlp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Process;
import android.system.Os;
import android.util.Log;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

// Throwaway S3 spike (D12). All results go to logcat tag "S3".
// Default: the S3 job list. `--es mode job --es url U [--es rate 100K]`: one job in cacheDir/jobs/<id>/
// (download, frame pass, cleanup in finally). `--es mode cancel`: kill the running job's yt-dlp process.
public class MainActivity extends Activity {
    static final String FMT = "b[height<=854][width<=854]/b[height<=1280][width<=1280]/w";
    static final String[][] JOBS = {
            {"ig", "https://www.instagram.com/reel/DeJ_pr9CNuO/", FMT},
            {"yt", "https://www.youtube.com/watch?v=Psy_4mEWNDI", FMT},
            // pre-spike proposal for YouTube: two separate files, no merge
            {"ytv", "https://www.youtube.com/watch?v=Psy_4mEWNDI", "bv*[height<=854][width<=854]"},
            {"yta", "https://www.youtube.com/watch?v=Psy_4mEWNDI", "ba[ext=m4a]"},
    };

    static volatile String runningJob;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        onNewIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent i) {
        String mode = i.getStringExtra("mode");
        if ("cancel".equals(mode)) {
            String id = runningJob;
            log("cancel job=" + id + " destroyed=" + (id != null && YoutubeDL.getInstance().destroyProcessById(id)));
        } else if ("job".equals(mode)) {
            String url = i.getStringExtra("url"), rate = i.getStringExtra("rate");
            new Thread(() -> fullJob(url, rate)).start();
        } else if (runningJob == null) {
            new Thread(this::run).start();
        }
    }

    // One job the way RunJob will do it: private job dir, download, frame pass, dir deleted on every exit path.
    void fullJob(String url, String rate) {
        String id = "j" + System.currentTimeMillis();
        File dir = new File(getCacheDir(), "jobs/" + id);
        runningJob = id;
        PssSampler s = new PssSampler();
        s.start();
        long t = System.nanoTime();
        String outcome = "DONE";
        try {
            YoutubeDL y = YoutubeDL.getInstance();
            y.init(getApplicationContext());
            dir.mkdirs();
            YoutubeDLRequest r = new YoutubeDLRequest(url);
            // API 26 has no AV1 decoder: prefer avc1, never av01.
            r.addOption("-f", FMT + "/bv*[vcodec^=avc1][height<=854][width<=854]/bv*[vcodec!^=av01][height<=854][width<=854]");
            r.addOption("--no-playlist");
            r.addOption("--socket-timeout", "15");
            if (rate != null) r.addOption("-r", rate);
            r.addOption("-o", dir + "/%(id)s.%(ext)s");
            r.addOption("--print", "after_move:%(format_id)s|%(width)sx%(height)s|%(vcodec)s|%(acodec)s|%(filepath)s");
            r.addOption("--no-simulate");
            YoutubeDLResponse resp = y.execute(r, id, null);
            String out = resp.getOut().trim();
            File f = new File(out.substring(out.lastIndexOf('|') + 1));
            log("job=" + id + " download_ms=" + (System.nanoTime() - t) / 1_000_000 + " picked=" + out.substring(0, out.lastIndexOf('|'))
                    + " bytes=" + f.length() + " tracks=" + tracks(f));
            frames(f, dir);
        } catch (YoutubeDL.CanceledException e) {
            outcome = "CANCELLED";
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            outcome = "FAILED err=" + msg.substring(Math.max(0, msg.length() - 300)).replace('\n', ' ');
        } finally {
            runningJob = null;
            s.stop_();
            delete(dir);
            log("job=" + id + " " + outcome + " total_ms=" + (System.nanoTime() - t) / 1_000_000 + " pss_peak_kb=" + s.peakKb
                    + " procs_peak=" + s.peakProcs + " cleanup dir_exists=" + dir.exists());
        }
    }

    // Path B frame pass: ≤ 20 evenly spaced frames, long edge ≤ 480, one Bitmap alive at a time.
    static void frames(File video, File dir) throws Exception {
        long t = System.nanoTime();
        MediaMetadataRetriever m = new MediaMetadataRetriever();
        int n = 0;
        try {
            m.setDataSource(video.getPath());
            long durUs = Long.parseLong(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
            int count = (int) Math.max(1, Math.min(20, durUs / 1_000_000));
            for (int i = 0; i < count; i++) {
                Bitmap src = m.getFrameAtTime(durUs * (2L * i + 1) / (2L * count), MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (src == null) continue;
                float k = 480f / Math.max(src.getWidth(), src.getHeight());
                Bitmap b = k < 1 ? Bitmap.createScaledBitmap(src, Math.round(src.getWidth() * k), Math.round(src.getHeight() * k), true) : src;
                if (b != src) src.recycle();
                try (FileOutputStream o = new FileOutputStream(new File(dir, "f" + i + ".jpg"))) { b.compress(Bitmap.CompressFormat.JPEG, 80, o); }
                b.recycle();
                n++;
            }
        } finally {
            m.release();
        }
        log("frames n=" + n + " frames_ms=" + (System.nanoTime() - t) / 1_000_000);
    }

    static void delete(File f) {
        File[] c = f.listFiles();
        if (c != null) for (File x : c) delete(x);
        f.delete();
    }

    static void log(String s) { Log.i("S3", s); }

    void run() {
        YoutubeDL y = YoutubeDL.getInstance();
        try {
            long t = System.nanoTime();
            y.init(getApplicationContext());
            log("init_ms=" + (System.nanoTime() - t) / 1_000_000 + " version=" + y.version(this));
            t = System.nanoTime();
            try {
                YoutubeDL.UpdateStatus st = y.updateYoutubeDL(getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
                log("update_ms=" + (System.nanoTime() - t) / 1_000_000 + " status=" + st + " version=" + y.version(this));
            } catch (Exception e) {
                log("update_FAIL " + e);
            }
        } catch (Exception e) {
            log("init_FAIL " + e);
            return;
        }
        for (String[] j : JOBS) job(y, j[0], j[1], j[2]);
        log("DONE");
    }

    void job(YoutubeDL y, String label, String url, String fmt) {
        PssSampler s = new PssSampler();
        s.start();
        long t = System.nanoTime();
        try {
            YoutubeDLRequest r = new YoutubeDLRequest(url);
            r.addOption("-f", fmt);
            r.addOption("--no-playlist");
            r.addOption("-o", getCacheDir() + "/" + label + "_%(id)s.%(ext)s");
            r.addOption("--print", "after_move:%(format_id)s|%(width)sx%(height)s|%(vcodec)s|%(acodec)s|%(filepath)s");
            r.addOption("--no-simulate");
            YoutubeDLResponse resp = y.execute(r);
            long ms = (System.nanoTime() - t) / 1_000_000;
            s.stop_();
            String out = resp.getOut().trim();
            String path = out.substring(out.lastIndexOf('|') + 1);
            File f = new File(path);
            log("job=" + label + " OK ms=" + ms + " pss_peak_kb=" + s.peakKb + " procs_peak=" + s.peakProcs
                    + " picked=" + out.substring(0, out.lastIndexOf('|')) + " bytes=" + f.length() + " tracks=" + tracks(f));
            File dst = new File(getExternalFilesDir(null), f.getName());
            Files.copy(f.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            f.delete();
        } catch (Exception e) {
            s.stop_();
            String msg = String.valueOf(e.getMessage());
            log("job=" + label + " FAIL ms=" + (System.nanoTime() - t) / 1_000_000 + " pss_peak_kb=" + s.peakKb + " err="
                    + msg.substring(Math.max(0, msg.length() - 400)).replace('\n', ' '));
        }
    }

    static String tracks(File f) {
        MediaExtractor x = new MediaExtractor();
        StringBuilder sb = new StringBuilder();
        try {
            x.setDataSource(f.getPath());
            for (int i = 0; i < x.getTrackCount(); i++) {
                MediaFormat m = x.getTrackFormat(i);
                sb.append(m.getString(MediaFormat.KEY_MIME));
                if (m.containsKey(MediaFormat.KEY_WIDTH)) sb.append('@').append(m.getInteger(MediaFormat.KEY_WIDTH)).append('x').append(m.getInteger(MediaFormat.KEY_HEIGHT));
                sb.append(',');
            }
        } catch (Exception e) {
            sb.append("ERR ").append(e);
        } finally {
            x.release();
        }
        return sb.toString();
    }

    // Sums Pss of every process owned by this app's UID (app + yt-dlp Python child), once a second.
    static class PssSampler extends Thread {
        volatile boolean on = true;
        volatile long peakKb;
        volatile int peakProcs;

        void stop_() { on = false; }

        @Override public void run() {
            int uid = Process.myUid();
            while (on) {
                long sum = 0;
                int n = 0;
                File[] ps = new File("/proc").listFiles();
                if (ps != null) for (File p : ps) {
                    if (!p.getName().matches("\\d+")) continue;
                    try {
                        if (Os.stat(p.getPath()).st_uid != uid) continue;
                        long kb = pss(new File(p, "smaps_rollup"));
                        if (kb > 0) { sum += kb; n++; }
                    } catch (Exception ignored) { }
                }
                if (sum > peakKb) peakKb = sum;
                if (n > peakProcs) peakProcs = n;
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            }
        }

        // smaps_rollup needs kernel 4.14+; older kernels (API 26 AVD) fall back to summing smaps.
        static long pss(File f) throws Exception {
            if (!f.exists()) {
                long sum = 0;
                try (BufferedReader r = new BufferedReader(new FileReader(new File(f.getParentFile(), "smaps")))) {
                    for (String l; (l = r.readLine()) != null; )
                        if (l.startsWith("Pss:")) sum += Long.parseLong(l.replaceAll("\\D", ""));
                }
                return sum;
            }
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                for (String l; (l = r.readLine()) != null; )
                    if (l.startsWith("Pss:")) return Long.parseLong(l.replaceAll("\\D", ""));
            }
            return 0;
        }
    }
}
