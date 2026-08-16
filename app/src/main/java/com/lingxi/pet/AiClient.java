package com.lingxi.pet;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话客户端：兼容 OpenAI 格式的接口（默认 DeepSeek API）。
 */
public final class AiClient {

    public static final class Msg {
        public final String role;
        public final String content;
        public Msg(String role, String content) { this.role = role; this.content = content; }
    }

    private static final List<Msg> history = new ArrayList<>();

    /** 调用 AI 接口，返回回复文本；未配置 Key 时返回离线应答。 */
    public static String chat(Context ctx, String userText) throws Exception {
        String base = PetConfig.apiBase(ctx).trim();
        String key = PetConfig.apiKey(ctx).trim();
        String model = PetConfig.model(ctx).trim();
        if (model.isEmpty()) model = PetConfig.DEFAULT_MODEL;

        if (key.isEmpty()) {
            return "（灵汐还没接上 AI 大脑呢～）去「AI 设置」里填上 API Key，我就能真正陪你聊天啦！";
        }

        synchronized (history) {
            history.add(new Msg("user", userText));
            while (history.size() > 20) history.remove(0);
        }

        String endpoint;
        if (base.endsWith("/chat/completions")) {
            endpoint = base;
        } else if (base.endsWith("/v1")) {
            endpoint = base + "/chat/completions";
        } else {
            endpoint = base.replaceAll("/+$", "") + "/chat/completions";
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", false);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", PetConfig.systemPrompt(ctx)));
        synchronized (history) {
            for (Msg m : history) {
                msgs.put(new JSONObject().put("role", m.role).put("content", m.content));
            }
        }
        body.put("messages", msgs);

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(is);
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new Exception("AI 接口返回 " + code + ": " + truncate(resp, 200));
        }

        JSONObject json = new JSONObject(resp);
        String reply = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim();
        synchronized (history) {
            history.add(new Msg("assistant", reply));
        }
        return reply;
    }

    public static void clearHistory() {
        synchronized (history) { history.clear(); }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private AiClient() {}
}
