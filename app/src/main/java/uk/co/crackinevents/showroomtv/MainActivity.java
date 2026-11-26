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
import android.webkit.RenderProcessGoneDetail;
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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
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
    void showPlayer() {
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
        root.setContent(web);
        web.loadUrl(url());
        web.requestFocus();
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

    void refreshButtons(Button rot, Button awake, Button boot) {
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
        col.addView(boot, mp(0, 0, 0, 30));
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

        Button start = option("Start");
        start.setGravity(Gravity.CENTER);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String u = addr.getText().toString().trim();
                if (u.isEmpty()) u = DEFAULT_URL;
                if (!u.matches("(?i)^https?://.*")) u = "https://" + u;
                prefs.edit().putString("url", u).putBoolean("done", true).apply();
                showPlayer();
            }
        });
        col.addView(start, mp(0, 0, 0, 24));
        col.addView(label("Press Menu on the remote at any time to come back here", 16, 0xFF9A9A9A));

        root.setContent(sv);
        rot.requestFocus();
    }

    LinearLayout.LayoutParams mp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 110);
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
