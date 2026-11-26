package uk.co.crackinevents.showroomtv;

import android.content.Context;
import android.webkit.WebResourceResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Saves each video on the stick the first time the page asks for it, one at a time in the background,
 * then plays it from the saved copy. Until a video has finished saving it streams from the website as normal.
 */
public class VideoStore {
    static final long KEEP_MS = 14L * 24 * 3600 * 1000;
    static final long MAX_BYTES = 1200L * 1024 * 1024;

    private final File dir;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Set<String> busy = new HashSet<String>();
    private final Set<String> served = new HashSet<String>();

    public VideoStore(Context c) {
        dir = new File(c.getFilesDir(), "videos");
        dir.mkdirs();
        pool.execute(new Runnable() { public void run() { tidy(); } });
    }

    public static boolean isVideo(String url) {
        String p = url;
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        p = p.toLowerCase();
        return p.endsWith(".mp4") || p.endsWith(".m4v") || p.endsWith(".mov") || p.endsWith(".webm");
    }

    private static String mime(String url) {
        String u = url.toLowerCase();
        return u.contains(".webm") ? "video/webm" : "video/mp4";
    }

    private File fileFor(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return new File(dir, sb + ".vid");
        } catch (Exception e) {
            return new File(dir, Integer.toHexString(url.hashCode()) + ".vid");
        }
    }

    /** Returns a response from the saved copy, or null to let the page stream it from the website. */
    public WebResourceResponse get(final String url, String range) {
        final File f = fileFor(url);
        if (f.isFile() && f.length() > 0) {
            try {
                WebResourceResponse r = serve(f, range, mime(url));
                if (served.add(url)) MainActivity.addLog("playing from the stick " + shortName(url));
                f.setLastModified(System.currentTimeMillis());
                return r;
            } catch (Exception e) {
                f.delete();
            }
        }
        if (served.add("s:" + url)) MainActivity.addLog("streaming " + shortName(url) + " while it saves");
        synchronized (busy) {
            if (busy.contains(url)) return null;
            busy.add(url);
        }
        pool.execute(new Runnable() { public void run() { download(url, f); } });
        return null;
    }

    private static String shortName(String url) {
        int q = url.indexOf('?');
        if (q >= 0) url = url.substring(0, q);
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private void download(String url, File dest) {
        File part = new File(dest.getPath() + ".part");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Accept-Encoding", "identity");
            if (c.getResponseCode() != 200) throw new IOException("http " + c.getResponseCode());
            long len = c.getContentLength();
            if (len > 0 && dir.getUsableSpace() < len + 150L * 1024 * 1024) throw new IOException("not enough space");
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(part);
            byte[] buf = new byte[64 * 1024];
            long got = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                got += n;
                if (got > 400L * 1024 * 1024) throw new IOException("too big");
            }
            out.close();
            in.close();
            if (len > 0 && got != len) throw new IOException("incomplete");
            if (!part.renameTo(dest)) throw new IOException("rename");
            MainActivity.addLog("saved " + shortName(url) + " " + (got / 1024) + " KB");
        } catch (Exception e) {
            part.delete();
            MainActivity.addLog("could not save " + shortName(url) + " (" + e.getMessage() + ")");
        } finally {
            if (c != null) c.disconnect();
            synchronized (busy) { busy.remove(url); }
        }
    }

    private WebResourceResponse serve(File f, String range, String mime) throws IOException {
        long len = f.length();
        long start = 0, end = len - 1;
        boolean partial = false;
        if (range != null && range.startsWith("bytes=")) {
            String[] p = range.substring(6).split("-", 2);
            try {
                if (!p[0].isEmpty()) start = Long.parseLong(p[0].trim());
                if (p.length > 1 && !p[1].trim().isEmpty()) end = Long.parseLong(p[1].trim());
                partial = true;
            } catch (NumberFormatException ignored) { }
        }
        if (start < 0 || start >= len) start = 0;
        end = Math.min(end, len - 1);
        if (end < start) end = len - 1;
        final RandomAccessFile raf = new RandomAccessFile(f, "r");
        raf.seek(start);
        final long[] left = {end - start + 1};
        InputStream in = new InputStream() {
            @Override public int read() throws IOException {
                byte[] b = new byte[1];
                return read(b, 0, 1) < 0 ? -1 : (b[0] & 0xff);
            }
            @Override public int read(byte[] b, int off, int n) throws IOException {
                if (left[0] <= 0) return -1;
                int r = raf.read(b, off, (int) Math.min(n, left[0]));
                if (r > 0) left[0] -= r;
                return r;
            }
            @Override public void close() throws IOException { raf.close(); }
        };
        Map<String, String> h = new HashMap<String, String>();
        h.put("Accept-Ranges", "bytes");
        h.put("Content-Length", String.valueOf(end - start + 1));
        h.put("Access-Control-Allow-Origin", "*");
        if (partial) h.put("Content-Range", "bytes " + start + "-" + end + "/" + len);
        WebResourceResponse r = new WebResourceResponse(mime, null, in);
        r.setStatusCodeAndReasonPhrase(partial ? 206 : 200, partial ? "Partial Content" : "OK");
        r.setResponseHeaders(h);
        return r;
    }

    /** Removes half-saved files, videos not used for two weeks, and the oldest ones if the store grows too big. */
    private void tidy() {
        File[] all = dir.listFiles();
        if (all == null) return;
        long now = System.currentTimeMillis();
        long total = 0;
        for (File f : all) {
            if (f.getName().endsWith(".part") || now - f.lastModified() > KEEP_MS) f.delete();
        }
        all = dir.listFiles();
        if (all == null) return;
        Arrays.sort(all, new Comparator<File>() {
            public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        for (File f : all) total += f.length();
        for (File f : all) {
            if (total <= MAX_BYTES) break;
            total -= f.length();
            f.delete();
        }
    }
}
