package com.lingxi.pet;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

/**
 * 精灵图逐帧动画引擎。
 * 精灵图规格（Codex Pet v2，DeepSeek娘）：1536x2288，8列x11行，每格 192x208。
 * 行布局：0=待机 1=向右跑 2=向左跑 3=挥手 4=跳跃 5=失败 6=等待 7=跑步 8=审查
 */
public class PetAnimator {

    public static final int SHEET_W = 1536, SHEET_H = 2288;
    public static final int CELL_W = 192, CELL_H = 208;
    public static final int COLS = 8;

    public enum State { IDLE, RUN_LEFT, RUN_RIGHT, WAVE, JUMP, FAIL, WAIT, RUN, REVIEW }

    /** 每个状态所在行 */
    private static final int[] ROWS = {0, 2, 1, 3, 4, 5, 6, 7, 8};
    /** 每个状态的帧数 */
    private static final int[] COUNTS = {7, 8, 8, 4, 5, 8, 6, 6, 6};
    /** 每个状态的帧率(fps) */
    private static final int[] FPS = {7, 12, 12, 10, 12, 10, 8, 10, 8};

    private Bitmap sheet;
    private State state = State.IDLE;
    private boolean loop = true;
    private int frame = 0;
    private long lastTick = 0;
    private boolean running = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable onFrame;
    private Runnable ticker;

    public PetAnimator(Bitmap sheet, Runnable onFrame) {
        this.sheet = sheet;
        this.onFrame = onFrame;
    }

    public void setSheet(Bitmap b) { sheet = b; }

    public void start() {
        if (running) return;
        running = true;
        lastTick = System.currentTimeMillis();
        ticker = new Runnable() {
            @Override public void run() {
                if (!running) return;
                long now = System.currentTimeMillis();
                long elapsed = now - lastTick;
                int idx = state.ordinal();
                long interval = 1000L / Math.max(1, FPS[idx]);
                if (elapsed >= interval) {
                    lastTick = now - (elapsed % interval);
                    int n = COUNTS[idx];
                    frame++;
                    if (frame >= n) {
                        if (loop) {
                            frame = 0;
                        } else {
                            frame = 0;
                            setState(State.IDLE, true);
                        }
                    }
                    if (onFrame != null) onFrame.run();
                }
                handler.postDelayed(this, Math.max(16, interval - elapsed));
            }
        };
        handler.post(ticker);
    }

    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    public synchronized void setState(State s, boolean loopIt) {
        state = s;
        loop = loopIt;
        frame = 0;
        lastTick = System.currentTimeMillis();
        if (onFrame != null) onFrame.run();
    }

    /** 播放一次后回到待机 */
    public void playOnce(State s) { setState(s, false); }

    public synchronized State getState() { return state; }

    public synchronized void draw(Canvas c, Rect dst) {
        if (sheet == null || dst == null) return;
        int idx = state.ordinal();
        int row = ROWS[idx];
        int f = Math.min(frame, COUNTS[idx] - 1);
        int sx = f * CELL_W;
        int sy = row * CELL_H;
        Rect src = new Rect(sx, sy, sx + CELL_W, sy + CELL_H);
        c.drawBitmap(sheet, src, dst, null);
    }
}
