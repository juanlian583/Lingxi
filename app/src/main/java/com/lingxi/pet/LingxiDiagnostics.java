package com.lingxi.pet;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.widget.Toast;

/**
 * 全局诊断日志收集器：所有原生/JS 日志汇聚到这里，
 * 支持一键组装报告并复制到剪贴板。
 */
public final class LingxiDiagnostics {

    private static final int MAX_LINES = 400;
    private static final StringBuilder log = new StringBuilder();

    public static synchronized void append(String msg) {
        if (msg == null) return;
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
    }

    public static synchronized String getLog() { return log.toString(); }

    public static synchronized void clear() { log.setLength(0); }

    /** 组装完整诊断报告（版本/设备/配置 + 运行日志） */
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
        String modelUrl = PetConfig.live2dModelUrl(c);
        sb.append("Live2D模型: ").append(modelUrl.isEmpty() ? "(内置 Haru)" : modelUrl).append('\n');
        sb.append("像素资源: ").append(PetConfig.spriteUrl(c)).append('\n');
        sb.append("AI: ").append(PetConfig.apiBase(c)).append(" / ").append(PetConfig.model(c))
                .append(" / Key: ").append(PetConfig.apiKey(c).isEmpty() ? "未配置" : "已配置(隐藏)").append('\n');
        sb.append("悬浮窗服务: ").append(PetService.running ? "运行中" : "未运行").append('\n');
        sb.append("----- 运行日志 -----\n");
        sb.append(getLog().isEmpty() ? "(暂无日志)" : getLog());
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
