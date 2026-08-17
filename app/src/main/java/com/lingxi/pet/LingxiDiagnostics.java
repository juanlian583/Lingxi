package com.lingxi.pet;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * 全局诊断日志收集器：内存环形缓冲 + 持久化文件（崩溃后仍可回溯）。
 * 支持一键组装报告并复制到剪贴板。
 */
public final class LingxiDiagnostics {

    private static final int MAX_LINES = 400;
    private static final long FILE_MAX_BYTES = 512 * 1024;
    private static final String SESSION_MARK = "===== 新会话开始 ";
    private static final String CLEAN_MARK = "==正常结束==";

    private static Context appContext;
    private static final StringBuilder log = new StringBuilder();

    public static void init(Context c) {
        appContext = c.getApplicationContext();
        append(SESSION_MARK + new java.util.Date() + " =====");
    }

    public static void append(String msg) {
        if (msg == null) return;
        synchronized (LingxiDiagnostics.class) {
            log.append(msg).append('\n');
            int lines = 0;
            for (int i = 0; i < log.length(); i++) {
                if (log.charAt(i) == '\n') lines++;
            }
            while (lines > MAX_LINES) {
                int idx = log.indexOf("\n");
                if (idx < 0) break;
                log.delete(0, idx + 1);
                lines--;
            }
            writeFile(msg + "\n");
        }
    }

    /** 正常退出标记（MainActivity/PetService 正常销毁时写入） */
    public static void markCleanExit() {
        append(CLEAN_MARK + " " + new java.util.Date());
    }

    public static synchronized String getLog() { return log.toString(); }

    public static synchronized void clear() {
        // 只清内存缓冲，保留持久化文件（崩溃前的日志要留给下次诊断）
        log.setLength(0);
    }

    private static void writeFile(String line) {
        if (appContext == null) return;
        try {
            File f = new File(appContext.getFilesDir(), "lingxi_diag.log");
            FileOutputStream fos = new FileOutputStream(f, true);
            try {
                fos.write(line.getBytes(StandardCharsets.UTF_8));
            } finally {
                fos.close();
            }
            if (f.length() > FILE_MAX_BYTES) {
                rotateFile(f);
            }
        } catch (Exception ignored) {
        }
    }

    private static void rotateFile(File f) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(f, "rw");
        try {
            long len = raf.length();
            long keep = 200 * 1024;
            long skip = len - keep;
            if (skip > 0) {
                raf.seek(skip);
                byte[] buf = new byte[(int) (len - skip)];
                raf.readFully(buf);
                raf.setLength(0);
                raf.seek(0);
                raf.write(buf);
            }
        } finally {
            raf.close();
        }
    }

    /** 上次会话日志（崩溃后重开应用也能看到崩溃前发生了什么） */
    public static String getPreviousSessionTail() {
        synchronized (LingxiDiagnostics.class) {
            try {
                if (appContext == null) return "";
                File f = new File(appContext.getFilesDir(), "lingxi_diag.log");
                if (!f.exists()) return "";
                // 按会话标记切分：prev = 上一个完整会话，cur = 当前运行中的会话
                StringBuilder prev = new StringBuilder();
                StringBuilder cur = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        new FileInputStream(f), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith(SESSION_MARK)) {
                            prev = cur;
                            cur = new StringBuilder();
                        }
                        cur.append(line).append('\n');
                    }
                }
                String s = prev.toString().trim();
                if (s.isEmpty()) return "";
                boolean clean = s.contains(CLEAN_MARK);
                String tail = tail(s, 100);
                return (clean ? "" : "⚠️ 上次会话异常终止（可能崩溃/被杀）\n") + tail;
            } catch (Exception e) {
                return "";
            }
        }
    }

    private static String tail(String s, int maxLines) {
        String[] lines = s.split("\n");
        if (lines.length <= maxLines) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = lines.length - maxLines; i < lines.length; i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString().trim();
    }

    /** 组装完整诊断报告（版本/设备/配置 + 运行日志 + 上次会话日志） */
    public static String buildReport(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("===== 灵汐诊断报告 =====\n");
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            sb.append("版本: v").append(pi.versionName).append(" (code ").append(pi.versionCode).append(")\n");
        } catch (Exception ignored) {}
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE)
                .append(" / API ").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("风格: ").append(PetConfig.petStyle(c)).append('\n');
        sb.append("大小: ").append(PetConfig.petSizeDp(c)).append(" dp\n");
        String mm = PetConfig.live2dModel(c);
        String modelName = PetConfig.MODEL_NOIR.equals(mm) ? "NOIR"
                : (PetConfig.MODEL_BAIXI.equals(mm) ? "白兮 Baixi" : "Haru");
        String modelUrl = PetConfig.live2dModelUrl(c);
        sb.append("Live2D模型: 内置 ").append(modelName)
                .append(modelUrl.isEmpty() ? "" : " + 自定义 " + modelUrl).append('\n');
        sb.append("像素资源: ").append(PetConfig.spriteUrl(c)).append('\n');
        sb.append("AI: ").append(PetConfig.apiBase(c)).append(" / ").append(PetConfig.model(c))
                .append(" / Key: ").append(PetConfig.apiKey(c).isEmpty() ? "未配置" : "已配置(隐藏)").append('\n');
        sb.append("悬浮窗服务: ").append(PetService.running ? "运行中" : "未运行").append('\n');
        sb.append("碰撞箱调试: ").append(PetConfig.debugHitbox(c) ? "开" : "关").append('\n');
        sb.append("----- 本次会话日志 -----\n");
        sb.append(getLog().isEmpty() ? "(暂无日志)" : getLog());
        String prev = getPreviousSessionTail();
        if (!prev.isEmpty()) {
            sb.append("\n----- 上次会话日志 -----\n").append(prev).append('\n');
        }
        return sb.toString();
    }

    /** 复制完整报告到剪贴板并提示 */
    public static void copy(Context c) {
        String report = buildReport(c);
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("灵汐诊断日志", report));
        }
        Toast.makeText(c, "诊断日志已复制 ✅ 直接粘贴发给我即可", Toast.LENGTH_LONG).show();
    }

    private LingxiDiagnostics() {}
}
