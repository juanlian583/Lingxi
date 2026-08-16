package com.lingxi.pet;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

/**
 * Live2D 桌宠舞台：透明 WebView 渲染骨骼动画（默认内置蓝发少女 Haru），
 * 气泡由页面内 DOM 元素展示（保证在透明 WebView 之上可见）。
 */
public class Live2dStage extends BaseOverlay implements PetHost {

    private static final String TAG = "LingxiLive2d";

    protected WebView webView;
    private boolean pageLoaded = false;

    public Live2dStage(Context c) {
        this(c, null);
    }

    public Live2dStage(Context c, AttributeSet a) {
        super(c);
        setClipChildren(false);

        int size = dp(PetConfig.petSizeDp(c));
        int w = size;
        int h = dp((int) (PetConfig.petSizeDp(c) * 208f / 192f));

        webView = new WebView(c);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setClickable(false);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(TAG, cm.message() + " @" + cm.lineNumber());
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
            }
        });
        webView.addJavascriptInterface(new JsBridge(), "LingxiNative");

        addView(webView, new FrameLayout.LayoutParams(w, h, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        loadPage();
    }

    /** 自定义模型 URL（model3.json），为空则用内置 Haru */
    protected String customModelUrl() {
        String u = PetConfig.live2dModelUrl(getContext());
        return (u == null || u.trim().isEmpty()) ? null : u.trim();
    }

    protected void loadPage() {
        String base = "file:///android_asset/live2d/index.html";
        String m = customModelUrl();
        String url = (m == null) ? base : base + "?model=" + Uri.encode(m);
        webView.loadUrl(url);
    }

    private void js(String script) {
        final WebView wv = webView;
        if (wv == null) return;
        wv.post(new Runnable() {
            @Override public void run() {
                try {
                    wv.evaluateJavascript(script, null);
                } catch (Exception ignored) {}
            }
        });
    }

    private static String q(String s) {
        return JSONObject.quote(s == null ? "" : s);
    }

    private class JsBridge {
        @JavascriptInterface public void onReady() {
            Log.d(TAG, "JS ready");
        }
        @JavascriptInterface public void onBubbleClick() {
            onBubbleClicked();
        }
    }

    // ---------------- PetHost ----------------

    @Override public void showBubble(String text, long ms) {
        js("Lingxi.showBubble(" + q(text) + ", " + ms + ")");
    }

    @Override public void setBubbleText(String text) {
        js("Lingxi.showBubble(" + q(text) + ", 60000)");
    }

    @Override public void hideBubble() {
        js("Lingxi.hideBubble()");
    }

    @Override public void tapReaction() {
        js("Lingxi.randomMotion()");
    }

    @Override public void patReaction() {
        js("Lingxi.pat()");
        spawnHearts();
    }

    @Override public void aiThinking() {
        js("Lingxi.think()");
    }

    @Override public void aiReply(boolean success) {
        js("Lingxi.reply(" + success + ")");
    }

    @Override public void recycle() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.removeAllViews();
                webView.destroy();
            } catch (Exception ignored) {}
            webView = null;
        }
    }
}
