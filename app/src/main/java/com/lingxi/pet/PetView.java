package com.lingxi.pet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 桌宠角色视图：负责绘制精灵动画、爱心特效，以及处理
 * 单击互动 / 双击摸头 / 长按菜单 / 拖拽移动 的手势。
 */
public class PetView extends View {

    public interface Listener {
        void onTap();
        void onPat();
        void onLongPress();
    }

    /** 拖拽回调（由悬浮窗实现） */
    public interface DragListener {
        void onDragStart();
        void onDrag(float dx, float dy);
        void onDragEnd();
    }

    protected PetAnimator animator;
    protected Bitmap sheet;
    protected Listener listener;
    protected DragListener dragListener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint heartPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Heart> hearts = new ArrayList<>();
    private final Random random = new Random();
    private final GestureDetector gesture;
    private final int touchSlop;
    private final android.graphics.Rect petRect = new android.graphics.Rect();

    private float downX, downY;
    private boolean dragging = false;
    private boolean longPressArmed = false;
    private boolean longPressed = false;
    private Runnable longPressRunnable;

    private static class Heart {
        float x, y, vx, vy, size;
        long born;
        Heart(float x, float y, float vx, float vy, float size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.size = size;
            this.born = System.currentTimeMillis();
        }
    }

    public PetView(Context c) { this(c, null); }

    public PetView(Context c, AttributeSet a) {
        super(c, a);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        gesture = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onTap();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (listener != null) listener.onPat();
                return true;
            }
        });
        sheet = PetResources.load(c);
        animator = new PetAnimator(sheet,
                new Runnable() {
                    @Override public void run() {
                        // 每帧(vsync)：更新爱心特效，有爱心时持续重绘
                        tickHearts();
                        if (!hearts.isEmpty()) postInvalidateOnAnimation();
                    }
                },
                new Runnable() {
                    @Override public void run() {
                        postInvalidateOnAnimation();
                    }
                });
        animator.start();
    }

    public PetAnimator animator() { return animator; }

    public void setListener(Listener l) { listener = l; }

    public void setDragListener(DragListener l) { dragListener = l; }

    public void spawnHearts() {
        long now = System.currentTimeMillis();
        int w = getWidth();
        float cx = w / 2f;
        for (int i = 0; i < 5; i++) {
            hearts.add(new Heart(
                    cx + random.nextInt(80) - 40,
                    getHeight() * 0.35f + random.nextInt(40),
                    random.nextInt(24) - 12,
                    -(60 + random.nextInt(40)),
                    26 + random.nextInt(22)));
        }
    }

    private void tickHearts() {
        long now = System.currentTimeMillis();
        Iterator<Heart> it = hearts.iterator();
        while (it.hasNext()) {
            Heart ht = it.next();
            float dt = 0.016f;
            ht.x += ht.vx * dt;
            ht.y += ht.vy * dt;
            if (now - ht.born > 1400) it.remove();
        }
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // 按 192:208 比例缩放绘制角色
        float scale = Math.min(w / (float) PetAnimator.CELL_W, h / (float) PetAnimator.CELL_H);
        int pw = (int) (PetAnimator.CELL_W * scale);
        int ph = (int) (PetAnimator.CELL_H * scale);
        int left = (w - pw) / 2;
        int top = h - ph;
        petRect.set(left, top, left + pw, top + ph);
        animator.draw(c, petRect);

        // 爱心
        long now = System.currentTimeMillis();
        for (Heart ht : hearts) {
            float life = (now - ht.born) / 1400f;
            if (life >= 1f) continue;
            heartPaint.setColor(0xFFFF6B9D);
            heartPaint.setAlpha((int) (255 * (1 - life)));
            heartPaint.setTextSize(ht.size);
            c.drawText("❤", ht.x, ht.y, heartPaint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                longPressed = false;
                longPressArmed = true;
                longPressRunnable = new Runnable() {
                    @Override public void run() {
                        if (longPressArmed && !dragging) {
                            longPressed = true;
                            if (listener != null) listener.onLongPress();
                        }
                    }
                };
                handler.postDelayed(longPressRunnable, 600);
                gesture.onTouchEvent(e);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    longPressArmed = false;
                    handler.removeCallbacks(longPressRunnable);
                    dragging = true;
                    if (dragListener != null) dragListener.onDragStart();
                }
                if (dragging) {
                    if (dragListener != null) dragListener.onDrag(dx, dy);
                    return true;
                }
                gesture.onTouchEvent(e);
                return true;
            }
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                longPressArmed = false;
                if (dragging) {
                    dragging = false;
                    if (dragListener != null) dragListener.onDragEnd();
                    return true;
                }
                gesture.onTouchEvent(e);
                return true;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                longPressArmed = false;
                if (dragging) {
                    dragging = false;
                    if (dragListener != null) dragListener.onDragEnd();
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    public boolean isLongPressed() { return longPressed; }
}
