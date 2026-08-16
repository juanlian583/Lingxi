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
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Live2D 桌宠舞台：透明 WebView 渲染骨骼动画（默认内置蓝发少女 Haru），
 * 气泡由页面内 DOM 元素展示。
 * 本版本带屏上诊断面板：原生侧与 JS 侧的加载状态都会显示出来。
 */
public class Live2dStage extends BaseOverlay implements PetHost {

    private static final String TAG = "LingxiLive2d";

    protected WebView webView;
    private boolean pageLoaded = false;
    private TextView debugView;
    private int debugCount = 0;

    public Live2dStage(Context c) {
        this(c, null);
    }

    public Live2dStage(Context c, AttributeSet a) {
        super(c);
        setClipChildren(false);

        int size = dp(PetConfig.petSizeDp(c));
        int w = size;
        int h = dp((int) (PetConfig.petSizeDp(c) * 208f / 192f));

        // 屏上诊断面板（叠加在 WebView 上方）
        debugView = new TextView(c);
        debugView.setTextColor(0xFF7CFC98);
        debugView.setTextSize(9);
        debugView.setTypeface(android.graphics.Typeface.MONOSPACE);
        debugView.setBackgroundColor(0xCC000000);
        debugView.setPadding(dp(4), dp(3), dp(4), dp(3));
        debugView.setLineSpacing(1f, 1f);
        debugView.setVisibility(VISIBLE);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dlp.topMargin = dp(2);
        addView(debugView, dlp);
        debug("原生诊断面板就绪, 期望视口 " + w + "x" + h);

        webView = new WebView(c);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setClickable(false);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        WebView.setWebContentsDebuggingEnabled(true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // 关键：布局视口 = WebView 自身宽度，避免 JS 拿到全屏宽度导致模型被裁掉
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage cm) {
                debug("JS: " + cm.message());
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                debug("页面开始加载: " + url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                debug("页面加载完成 ✅ " + url);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                String msg = "错误 " + error.getErrorCode() + " " + error.getDescription() + " <- " + request.getUrl();
                if (request.isForMainFrame()) debug("❌ 主页面: " + msg);
                else debug("子资源: " + msg);
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                debug("HTTP " + errorResponse.getStatusCode() + " <- " + request.getUrl());
            }
        });
        webView.addJavascriptInterface(new JsBridge(), "LingxiNative");

        addView(webView, new FrameLayout.LayoutParams(w, h, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        loadPage();
    }

    private void debug(String msg) {
        Log.d(TAG, msg);
        if (debugView == null) return;
        debugCount++;
        String line = debugCount + ". " + msg;
        String cur = debugView.getText().toString();
        String[] lines = cur.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, lines.length - 12);
        for (int i = start; i < lines.length; i++) {
            if (!lines[i].isEmpty()) sb.append(lines[i]).append('\n');
        }
        sb.append(line);
        debugView.setText(sb.toString());
    }

    /** 自定义模型 URL（model3.json），为空则用内置 Haru */
    protected String customModelUrl() {
        String u = PetConfig.live2dModelUrl(getContext());
        return (u == null || u.trim().isEmpty()) ? null : u.trim();
    }

    protected void loadPage() {
        // 用本地 HTTP 服务器提供资源：现代 WebView 禁止 file:// 页面 XHR 加载 file:// 资源
        int port = LocalAssetServer.get(getContext()).ensureStarted();
        if (port <= 0) {
            debug("❌ 本地资源服务器启动失败");
            return;
        }
        debug("本地资源服务器: 127.0.0.1:" + port);
        String base = "http://127.0.0.1:" + port + "/index.html";
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
            debug("JS 桥接: 渲染就绪");
        }
        @JavascriptInterface public void onBubbleClick() {
            onBubbleClicked();
        }
        @JavascriptInterface public void onError(String message) {
            debug("❌ JS: " + message);
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
