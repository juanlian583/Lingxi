package com.lingxi.pet;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * 悬浮桌宠前台服务：把灵汐显示在所有应用（桌面）之上。
 * 支持 Live2D（默认）与经典像素精灵图两种风格。
 */
public class PetService extends Service {

    public static volatile boolean running = false;

    private static final int NOTIF_ID = 1;
    private static final String CHANNEL_ID = "lingxi_pet";

    private WindowManager wm;
    private BaseOverlay overlay;
    private WindowManager.LayoutParams params;
    private ChatDialog chatDialog;
    private volatile boolean aiBusy = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String[] LINES = {
            "主人好呀～今天也要加油哦！🐳",
            "尾巴摇一摇，代表灵汐很开心～",
            "要不要休息一下，陪我聊聊天？",
            "我在认真工作，不会偷懒的！",
            "嘿嘿，被主人发现啦～",
            "深海里有好多秘密，改天讲给你听～"
    };

    private final Runnable idleLines = new Runnable() {
        @Override public void run() {
            if (!running || overlay == null || aiBusy) {
                handler.postDelayed(this, 90000);
                return;
            }
            String line = LINES[(int) (Math.random() * LINES.length)];
            overlay.setBubbleText(line);
            overlay.tapReaction();
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (overlay != null) overlay.hideBubble();
                }
            }, 4200);
            SpeechHelper.speak(PetService.this, line);
            handler.postDelayed(this, 90000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        ensureOverlay();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureOverlay();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // ---------------- 悬浮窗 ----------------

    private void ensureOverlay() {
        if (overlay != null) return;
        if (!Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = dp(24);
        int petPx = dp(PetConfig.petSizeDp(this));
        params.x = dm.widthPixels - petPx - margin;
        params.y = dm.heightPixels - petPx - margin;

        BaseOverlay ov = PetConfig.live2dMode(this)
                ? new Live2dOverlayView(this, wm, params)
                : new PetOverlayView(this, wm, params);
        overlay = ov;
        overlay.setListener(new PetView.Listener() {
            @Override public void onTap() { onTapInteract(); }
            @Override public void onPat() { onPat(); }
            @Override public void onLongPress() { showMenu(); }
        });
        overlay.setBubbleClickListener(new Runnable() {
            @Override public void run() { openChat(); }
        });
        wm.addView(overlay, params);
        handler.removeCallbacks(idleLines);
        handler.postDelayed(idleLines, 30000);
    }

    private void onTapInteract() {
        if (aiBusy) return;
        String line;
        int r = (int) (Math.random() * 3);
        if (r == 0) {
            line = "嗨～主人！";
        } else if (r == 1) {
            line = "耶！好开心！";
        } else {
            line = "嗯嗯，我在听着呢～";
        }
        overlay.tapReaction();
        overlay.showBubble(line, 3500);
        SpeechHelper.speak(this, line);
    }

    private void onPat() {
        if (aiBusy) return;
        overlay.patReaction();
        String line = "嘻嘻，被主人摸头好舒服～";
        overlay.showBubble(line, 4000);
        SpeechHelper.speak(this, line);
    }

    private void showMenu() {
        String[] items = {"和灵汐聊天", "复制诊断日志", "打开设置", "隐藏桌宠"};
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("灵汐菜单")
                .setItems(items, (d, w) -> {
                    if (w == 0) openChat();
                    else if (w == 1) LingxiDiagnostics.copy(this);
                    else if (w == 2) {
                        Intent i = new Intent(this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } else {
                        stopSelf();
                    }
                })
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dlg.show();
    }

    // ---------------- 聊天 + AI ----------------

    private void openChat() {
        if (chatDialog != null && chatDialog.isShowing()) return;
        chatDialog = new ChatDialog(this, new ChatDialog.Callback() {
            @Override public void onSend(String text) { askAi(text); }
        });
        chatDialog.show();
    }

    private void askAi(final String text) {
        if (aiBusy) return;
        aiBusy = true;
        overlay.setBubbleText("让我想想…");
        overlay.aiThinking();
        new Thread(new Runnable() {
            @Override public void run() {
                String reply;
                boolean ok;
                try {
                    reply = AiClient.chat(PetService.this, text);
                    ok = true;
                } catch (Exception e) {
                    reply = "呜…出错了：" + e.getMessage();
                    ok = false;
                }
                final String r = reply;
                final boolean success = ok;
                handler.post(new Runnable() {
                    @Override public void run() {
                        aiBusy = false;
                        if (overlay == null) return;
                        overlay.aiReply(success);
                        overlay.setBubbleText(r);
                        handler.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (overlay != null) overlay.hideBubble();
                            }
                        }, Math.min(12000, 3000 + r.length() * 80));
                        SpeechHelper.speak(PetService.this, r);
                        if (chatDialog != null && chatDialog.isShowing()) {
                            chatDialog.appendAssistant(r);
                        }
                    }
                });
            }
        }).start();
    }

    // ---------------- 通知 ----------------

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "灵汐桌宠", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("桌面桌宠运行状态");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("灵汐桌宠运行中")
                .setContentText("单击互动 · 双击摸头 · 长按菜单")
                .setContentIntent(pi)
                .setOngoing(true);
        Intent stop = new Intent(this, PetService.class).setAction("STOP");
        PendingIntent sp = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        b.addAction(new Notification.Action.Builder(R.drawable.ic_notif, "隐藏", sp).build());
        return b.build();
    }

    // ---------------- 生命周期 ----------------

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (overlay != null && wm != null) {
            try { wm.removeView(overlay); } catch (Exception ignored) {}
            try { overlay.recycle(); } catch (Exception ignored) {}
            overlay = null;
        }
        if (chatDialog != null) {
            try { chatDialog.dismiss(); } catch (Exception ignored) {}
            chatDialog = null;
        }
        SpeechHelper.shutdown();
        stopForeground(true);
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
