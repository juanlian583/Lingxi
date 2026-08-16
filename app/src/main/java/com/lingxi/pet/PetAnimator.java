package com.lingxi.pet;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.Choreographer;

/**
 * 精灵图逐帧动画引擎（Choreographer 垂直同步驱动，流畅无抖动）。
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
    /** 每个状态的帧率(fps)，比原版略高更顺滑 */
    private static final int[] FPS = {9, 14, 14, 12, 14, 12, 10, 12, 10};

    private Bitmap sheet;
    private State state = State.IDLE;
    private boolean loop = true;
    private int frame = 0;
    private long lastTick = 0;
    private boolean running = false;

    private Choreographer choreographer;
    private final Choreographer.FrameCallback frameCallback;
    /** 每帧(vsync)回调：用于爱心等需要 60fps 平滑的动画 */
    private final Runnable onTick;
    /** 精灵帧切换回调：触发重绘 */
    private final Runnable onFrame;
    private final Rect srcRect = new Rect();

    public PetAnimator(Bitmap sheet, Runnable onTick, Runnable onFrame) {
        this.sheet = sheet;
        this.onTick = onTick;
        this.onFrame = onFrame;
        frameCallback = new Choreographer.FrameCallback() {
            @Override public void doFrame(long frameTimeNanos) {
                if (!running) return;
                tick(frameTimeNanos);
                if (onTick != null) onTick.run();
                choreographer.postFrameCallback(this);
            }
        };
    }

    public void setSheet(Bitmap b) { sheet = b; }

    public void start() {
        if (running) return;
        running = true;
        lastTick = System.nanoTime();
        choreographer = Choreographer.getInstance();
        choreographer.postFrameCallback(frameCallback);
    }

    public void stop() {
        running = false;
        if (choreographer != null) {
            choreographer.removeFrameCallback(frameCallback);
            choreographer = null;
        }
    }

    private void tick(long nowNanos) {
        long elapsedMs = (nowNanos - lastTick) / 1_000_000L;
        int idx = state.ordinal();
        long interval = 1000L / Math.max(1, FPS[idx]);
        if (elapsedMs >= interval) {
            lastTick = nowNanos - (elapsedMs % interval) * 1_000_000L;
            frame++;
            int n = COUNTS[idx];
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
    }

    /**
     * 切换状态。同一状态下不会重置帧 —— 避免拖拽时每帧都把动画打回第 0 帧导致抽搐。
     */
    public synchronized void setState(State s, boolean loopIt) {
        if (state == s && this.loop == loopIt) return;
        state = s;
        loop = loopIt;
        frame = 0;
        lastTick = System.nanoTime();
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
        srcRect.set(f * CELL_W, row * CELL_H, f * CELL_W + CELL_W, row * CELL_H + CELL_H);
        c.drawBitmap(sheet, srcRect, dst, null);
    }
}
