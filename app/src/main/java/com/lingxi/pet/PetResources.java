package com.lingxi.pet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 形象资源管理：优先使用本地下载的精灵图，否则回退到内置资源；
 * 支持从网上（默认 DeepSeek娘 仓库的 raw 地址）下载更新形象。
 */
public final class PetResources {

    public static final String SPRITE_PATH = "pet/spritesheet.webp";
    private static volatile Bitmap cached;

    public static File spriteFile(Context c) {
        return new File(c.getFilesDir(), SPRITE_PATH);
    }

    /** 同步加载精灵图（内存缓存 + 本地文件 + 内置 assets 三级回退）。 */
    public static Bitmap load(Context c) {
        Bitmap b = cached;
        if (b != null && !b.isRecycled()) return b;
        File f = spriteFile(c);
        if (f.exists()) {
            b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) { cached = b; return b; }
        }
        try (InputStream is = c.getAssets().open("spritesheet.webp")) {
            b = BitmapFactory.decodeStream(is);
            if (b != null) cached = b;
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    public static void invalidateCache() { cached = null; }

    /** 后台下载精灵图，完成后在主线程回调。 */
    public static void download(final Context c, final String url,
                                final Runnable onDone, final Runnable onFail) {
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try {
                    File dir = new File(c.getFilesDir(), "pet");
                    if (!dir.exists()) dir.mkdirs();
                    File tmp = new File(dir, "spritesheet.tmp");

                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "Lingxi-Pet/1.0");
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        try (InputStream in = conn.getInputStream();
                             FileOutputStream out = new FileOutputStream(tmp)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                        }
                        Bitmap check = BitmapFactory.decodeFile(tmp.getAbsolutePath());
                        if (check != null && check.getWidth() >= PetAnimator.CELL_W
                                && check.getHeight() >= PetAnimator.CELL_H) {
                            check.recycle();
                            File dst = spriteFile(c);
                            if (dst.exists()) dst.delete();
                            tmp.renameTo(dst);
                            cached = null;
                            ok = true;
                        } else {
                            tmp.delete();
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    ok = false;
                }
                final boolean success = ok;
                h.post(new Runnable() {
                    @Override public void run() {
                        if (success) { if (onDone != null) onDone.run(); }
                        else if (onFail != null) onFail.run();
                    }
                });
            }
        }).start();
    }

    private PetResources() {}
}
