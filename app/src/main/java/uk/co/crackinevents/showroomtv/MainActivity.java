package uk.co.crackinevents.showroomtv;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    static final String PREFS = "cstv";
    static final String DEFAULT_URL = "https://www.crackinevents.co.uk/tv";
    static final int GOLD = 0xFFE3C9A0;
    static final int INK = 0xFF0B0B0B;

    static final java.util.LinkedList<String> LOG = new java.util.LinkedList<String>();
    static String webVersion = "";
    VideoStore store;
    SharedPreferences prefs;
    TurnFrame root;
    WebView web;
    boolean inSetup = false;
    final Handler handler = new Handler(Looper.getMainLooper());

    /** Holds one child, centred, and turns it so a physically turned TV shows it upright. */
    static class TurnFrame extends FrameLayout {
        int turn = 0;
        View content;

        TurnFrame(Context c) {
            super(c);
            setBackgroundColor(Color.BLACK);
        }

        void setContent(View v) {
            removeAllViews();
            content = v;
            addView(v, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            relayout();
        }

        void setTurn(int t) {
            turn = t;
            relayout();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            post(new Runnable() { public void run() { relayout(); } });
        }

        void relayout() {
            int w = getWidth(), h = getHeight();
            if (content == null || w == 0 || h == 0) return;
            LayoutParams lp = (LayoutParams) content.getLayoutParams();
            if (turn == 0) { lp.width = w; lp.height = h; } else { lp.width = h; lp.height = w; }
            lp.gravity = Gravity.CENTER;
            content.setLayoutParams(lp);
            content.setRotation(turn);
        }
    }


    /** Six slots for the pairing code, filled in with the remote: up and down change a character, left and right move. */
    class CodeView extends LinearLayout {
        static final String SET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final int[] v = new int[]{-1, -1, -1, -1, -1, -1};
        final TextView[] cell = new TextView[6];
        int idx = 0;

        CodeView(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setFocusable(true);
            setFocusableInTouchMode(true);
            for (int i = 0; i < 6; i++) {
                TextView t = new TextView(c);
                t.setGravity(Gravity.CENTER);
                t.setTextSize(34);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setTextColor(Color.WHITE);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 120, 1f);
                lp.setMargins(i == 0 ? 0 : 10, 0, 0, 0);
                addView(t, lp);
                cell[i] = t;
            }
            draw();
        }

        void draw() {
            for (int i = 0; i < 6; i++) {
                boolean on = hasFocus() && i == idx;
                cell[i].setText(v[i] < 0 ? "" : String.valueOf(SET.charAt(v[i])));
                cell[i].setBackground(box(on ? 0xFF2A2A2A : INK, on ? Color.WHITE : 0xFF3A3A3A, 14));
            }
        }

        @Override
        protected void onFocusChanged(boolean gained, int dir, android.graphics.Rect r) {
            super.onFocusChanged(gained, dir, r);
            draw();
        }

        String code() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) { if (v[i] < 0) return ""; sb.append(SET.charAt(v[i])); }
            return sb.toString();
        }

        @Override
        public boolean onKeyDown(int k, KeyEvent e) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP: v[idx] = v[idx] < 0 ? 10 : (v[idx] + 1) % 36; draw(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: v[idx] = v[idx] <= 0 ? 35 : v[idx] - 1; draw(); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (idx == 0) { View n = focusSearch(View.FOCUS_UP); if (n != null) n.requestFocus(); }
                    else { idx--; draw(); }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (idx == 5) { View n = focusSearch(View.FOCUS_DOWN); if (n != null) n.requestFocus(); }
                    else { idx++; draw(); }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (v[idx] < 0) v[idx] = 10;
                    if (idx == 5) { View n = focusSearch(View.FOCUS_DOWN); if (n != null) n.requestFocus(); }
                    else { idx++; if (v[idx] < 0) v[idx] = 10; draw(); }
                    return true;
                case KeyEvent.KEYCODE_DEL:
                    v[idx] = -1;
                    if (idx > 0) idx--;
                    draw();
                    return true;
                default:
                    int ch = Character.toUpperCase(e.getUnicodeChar());
                    int pos = ch > 0 ? SET.indexOf(ch) : -1;
                    if (pos >= 0) { v[idx] = pos; if (idx < 5) idx++; draw(); return true; }
            }
            return super.onKeyDown(k, e);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        store = new VideoStore(this);
        root = new TurnFrame(this);
        setContentView(root);
        root.setTurn(prefs.getInt("rot", 90));
        applyWindow();
        if (prefs.getBoolean("done", false)) showPlayer(); else showSetup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyWindow();
        if (web != null) web.onResume();
    }

    void applyWindow() {
        if (prefs.getBoolean("awake", true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) applyWindow();
    }

    String url() {
        return prefs.getString("url", DEFAULT_URL);
    }

    // ---------- Player ----------

    @SuppressLint("SetJavaScriptEnabled")
    void showPlayer() { showPlayer(url()); }

    void showPlayer(String target) {
        inSetup = false;
        destroyWeb();
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.setBackgroundColor(Color.BLACK);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if ("GET".equals(r.getMethod()) && VideoStore.isVideo(u)) {
                    return store.get(u, r.getRequestHeaders().get("Range"));
                }
                return null;
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                addLog("page loaded " + u);
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest r, android.webkit.WebResourceResponse e) {
                addLog("http " + e.getStatusCode() + " " + r.getUrl());
            }

            @Override
            public void onReceivedError(WebView v, int code, String desc, String failingUrl) {
                if (failingUrl != null && failingUrl.equals(url())) waitAndRetry();
            }

            @TargetApi(23)
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) waitAndRetry();
            }

            @TargetApi(26)
            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                handler.post(new Runnable() { public void run() { if (!inSetup) showPlayer(); } });
                return true;
            }
        });
        s.setUserAgentString(s.getUserAgentString() + " CrackinTVApp/1.0.7" + (prefs.getBoolean("fx", false) ? "" : " nofx"));
        webVersion = s.getUserAgentString();
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                addLog(m.message() + (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR ? "  [error line " + m.lineNumber() + "]" : ""));
                return true;
            }
        });
        root.setContent(web);
        web.loadUrl(target);
        web.requestFocus();
    }

    static void addLog(String m) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.UK).format(new java.util.Date());
        synchronized (LOG) {
            LOG.add(t + "  " + m);
            while (LOG.size() > 40) LOG.removeFirst();
        }
    }

    void waitAndRetry() {
        if (web == null) return;
        web.loadDataWithBaseURL(null,
                "<html><body style='margin:0;background:#0b0b0b;color:#E3C9A0;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center;letter-spacing:4px;text-transform:uppercase;font-size:28px'>Waiting for the connection</body></html>",
                "text/html", "utf-8", null);
        handler.postDelayed(new Runnable() {
            public void run() {
                if (!inSetup && web != null) web.loadUrl(url());
            }
        }, 15000);
    }

    void destroyWeb() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            try {
                root.removeAllViews();
                web.stopLoading();
                web.destroy();
            } catch (Exception ignored) { }
            web = null;
        }
    }

    // ---------- Setup screen ----------

    static String rotName(int r) {
        return r == 90 ? "Anticlockwise" : r == 270 ? "Clockwise" : "Upright";
    }

    static GradientDrawable box(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(2, stroke);
        d.setCornerRadius(radius);
        return d;
    }

    Button option(String text) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setTextSize(22);
        bt.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        bt.setPadding(36, 0, 36, 0);
        bt.setTextColor(Color.WHITE);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, box(0xFF2A2A2A, Color.WHITE, 14));
        sl.addState(new int[]{}, box(INK, 0xFF3A3A3A, 14));
        bt.setBackground(sl);
        return bt;
    }

    TextView label(String t, int size, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    Button fxBtn;

    void refreshButtons(Button rot, Button awake, Button boot) {
        if (fxBtn != null) fxBtn.setText("Fade between videos:  " + (prefs.getBoolean("fx", false) ? "On" : "Off (faster)"));
        rot.setText("Screen rotation:  " + rotName(prefs.getInt("rot", 90)));
        awake.setText("Keep the screen awake:  " + (prefs.getBoolean("awake", true) ? "On" : "Off"));
        boot.setText("Start when the Firestick turns on:  " + (prefs.getBoolean("boot", true) ? "On" : "Off"));
    }

    void showSetup() {
        inSetup = true;
        destroyWeb();

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(INK);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(110, 70, 110, 70);
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView brand = label("CRACKIN EVENTS", 16, GOLD);
        brand.setLetterSpacing(0.3f);
        col.addView(brand);
        TextView title = label("Showroom TV setup", 34, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 8, 0, 40);
        col.addView(title);

        col.addView(label("Address", 18, 0xFF9A9A9A));
        final EditText addr = new EditText(this);
        addr.setText(url());
        addr.setSingleLine(true);
        addr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addr.setTextColor(Color.WHITE);
        addr.setTextSize(22);
        addr.setPadding(36, 24, 36, 24);
        StateListDrawable asl = new StateListDrawable();
        asl.addState(new int[]{android.R.attr.state_focused}, box(INK, Color.WHITE, 14));
        asl.addState(new int[]{}, box(INK, 0xFF3A3A3A, 14));
        addr.setBackground(asl);
        col.addView(addr, mp(0, 6, 0, 22));

        col.addView(label("Turn the picture to match how the TV has been turned", 18, 0xFF9A9A9A));
        final Button rot = option("");
        final Button awake = option("");
        final Button boot = option("");
        col.addView(rot, mp(0, 6, 0, 18));
        col.addView(awake, mp(0, 0, 0, 18));
        fxBtn = option("");
        col.addView(boot, mp(0, 0, 0, 18));
        col.addView(fxBtn, mp(0, 0, 0, 30));
        fxBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefs.edit().putBoolean("fx", !prefs.getBoolean("fx", false)).apply();
                refreshButtons(rot, awake, boot);
            }
        });
        refreshButtons(rot, awake, boot);

        rot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int r = prefs.getInt("rot", 90);
                int n = r == 0 ? 270 : r == 270 ? 90 : 0;
                prefs.edit().putInt("rot", n).apply();
                root.setTurn(n);
                refreshButtons(rot, awake, boot);
            }
        });
        awake.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefs.edit().putBoolean("awake", !prefs.getBoolean("awake", true)).apply();
                applyWindow();
                refreshButtons(rot, awake, boot);
            }
        });
        boot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefs.edit().putBoolean("boot", !prefs.getBoolean("boot", true)).apply();
                refreshButtons(rot, awake, boot);
            }
        });

        col.addView(label("Pairing code (first time only, leave empty if this TV is already connected)", 18, 0xFF9A9A9A));
        final CodeView code = new CodeView(this);
        col.addView(code, mp(0, 6, 0, 30, 120));

        Button start = option("Start");
        start.setGravity(Gravity.CENTER);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String u = addr.getText().toString().trim();
                if (u.isEmpty()) u = DEFAULT_URL;
                if (!u.matches("(?i)^https?://.*")) u = "https://" + u;
                prefs.edit().putString("url", u).putBoolean("done", true).apply();
                String c = code.code();
                showPlayer(c.isEmpty() ? u : u + (u.contains("?") ? "&" : "?") + "pair=" + c + "&rot=none");
            }
        });
        col.addView(start, mp(0, 0, 0, 24));
        col.addView(label("Press Menu on the remote at any time to come back here", 16, 0xFF9A9A9A));
        String ver = "";
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("Chrome/([0-9.]+)").matcher(webVersion);
        if (mm.find()) ver = mm.group(1);
        StringBuilder lg = new StringBuilder("App 1.0.7   Web engine " + (ver.isEmpty() ? "unknown" : ver) + "\n");
        synchronized (LOG) {
            int from = Math.max(0, LOG.size() - 16);
            for (int i = from; i < LOG.size(); i++) lg.append(LOG.get(i)).append("\n");
        }
        TextView dbg = label(lg.toString(), 12, 0xFF6F6F6F);
        dbg.setPadding(0, 30, 0, 0);
        col.addView(dbg);

        root.setContent(sv);
        rot.requestFocus();
    }

    LinearLayout.LayoutParams mp(int l, int t, int r, int b) { return mp(l, t, r, b, 110); }

    LinearLayout.LayoutParams mp(int l, int t, int r, int b, int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    // ---------- Remote control ----------

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_MENU) {
            if (!inSetup) showSetup();
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (inSetup && prefs.getBoolean("done", false)) showPlayer();
            else if (!inSetup) e.startTracking();
            return true;
        }
        return super.onKeyDown(k, e);
    }

    @Override
    public boolean onKeyLongPress(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK && !inSetup) {
            showSetup();
            return true;
        }
        return super.onKeyLongPress(k, e);
    }

    @Override
    protected void onDestroy() {
        destroyWeb();
        super.onDestroy();
    }
}
