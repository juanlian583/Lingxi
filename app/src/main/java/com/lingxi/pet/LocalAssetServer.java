package com.lingxi.pet;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 极简本地静态资源服务器：
 * 把 assets/live2d 下的资源通过 http://127.0.0.1:port 提供，
 * 绕开现代 WebView 禁止 file:// 页面 XHR 加载 file:// 资源的限制。
 */
public final class LocalAssetServer {

    private static final String TAG = "LingxiHttp";
    private static final String ROOT = "live2d";

    private static LocalAssetServer instance;

    private final Context appContext;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running;
    private int port = -1;

    private LocalAssetServer(Context c) {
        this.appContext = c.getApplicationContext();
    }

    public static synchronized LocalAssetServer get(Context c) {
        if (instance == null) instance = new LocalAssetServer(c);
        return instance;
    }

    /** 确保服务器已启动，返回端口；失败返回 -1 */
    public synchronized int ensureStarted() {
        if (serverSocket != null) return port;
        try {
            serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            running = true;
            pool = Executors.newCachedThreadPool();
            Thread t = new Thread(this::acceptLoop, "lingxi-asset-server");
            t.setDaemon(true);
            t.start();
            Log.d(TAG, "本地资源服务器已启动: 127.0.0.1:" + port);
            return port;
        } catch (IOException e) {
            Log.e(TAG, "本地资源服务器启动失败", e);
            return -1;
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket s = serverSocket.accept();
                pool.execute(() -> handle(s));
            } catch (IOException e) {
                break;
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket sock = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1));
             OutputStream out = sock.getOutputStream()) {
            String line = in.readLine();
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];
            // 读完剩余请求头
            while (true) {
                String h = in.readLine();
                if (h == null || h.isEmpty()) break;
            }
            if (!"GET".equals(method)) {
                respond(out, 405, "text/plain", "method not allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String p = path.split("\\?")[0];
            if (p.equals("/") || p.isEmpty()) p = "/index.html";
            if (p.contains("..")) {
                respond(out, 403, "text/plain", "forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] data = readAsset(ROOT + p);
            if (data == null) {
                respond(out, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(out, 200, mime(p), data);
        } catch (Exception ignored) {
        }
    }

    private byte[] readAsset(String path) {
        try (InputStream is = appContext.getAssets().open(path)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private void respond(OutputStream out, int code, String mime, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + code + " OK\r\n"
                + "Content-Type: " + mime + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static String mime(String path) {
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".wav")) return "audio/wav";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".moc3")) return "application/octet-stream";
        if (p.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    public synchronized void shutdown() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (pool != null) pool.shutdownNow();
        serverSocket = null;
        pool = null;
        instance = null;
    }
}
