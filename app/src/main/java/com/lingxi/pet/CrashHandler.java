package com.lingxi.pet;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 全局崩溃捕获：把崩溃堆栈记录到诊断日志与 crash.log 文件，
 * 便于通过「复制诊断日志」定位问题。
 */
public final class CrashHandler {

    private static final Thread.UncaughtExceptionHandler DEFAULT =
            Thread.getDefaultUncaughtExceptionHandler();

    public static void install(final Context c) {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, Throwable throwable) {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                String stack = "💥 崩溃线程: " + thread.getName() + "\n" + sw;
                LingxiDiagnostics.append(stack);
                try {
                    File f = new File(c.getFilesDir(), "crash.log");
                    FileOutputStream fos = new FileOutputStream(f, true);
                    try {
                        fos.write(("\n===== " + new java.util.Date() + " =====\n" + stack + "\n")
                                .getBytes("UTF-8"));
                    } finally {
                        fos.close();
                    }
                } catch (Exception ignored) {
                }
                if (DEFAULT != null) {
                    DEFAULT.uncaughtException(thread, throwable);
                }
            }
        });
    }

    private CrashHandler() {}
}
