package com.lingxi.pet;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮桌宠基类：统一处理 单击/双击/长按/拖拽 手势与爱心特效。
 * 子类负责具体的角色渲染（Live2D 或 精灵图）与气泡展示。
 */
public abstract class BaseOverlay extends FrameLayout implements PetHost {

    protected WindowManager wm;
    protected WindowManager.LayoutParams params;
    protected int baseX, baseY;

    protected PetView.Listener listener;
    public Runnable bubbleClickListener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GestureDetector gesture;
    private final int touchSlop;
    private float rawDownX, rawDownY;
    private boolean dragging = false;
    private boolean longPressArmed = false;
    private Runnable longPressRunnable;
    private final List<View> hearts = new ArrayList<>();

    public BaseOverlay(Context c) {
        super(c);
        setClipChildren(false);
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
    }

    /** 悬浮窗模式下挂接拖拽（预览模式不调用） */
    public void attachDrag(WindowManager wm, WindowManager.LayoutParams params) {
        this.wm = wm;
        this.params = params;
        this.baseX = params.x;
        this.baseY = params.y;
    }

    public void setListener(PetView.Listener l) { listener = l; }

    public void setBubbleClickListener(Runnable r) { bubbleClickListener = r; }

    protected void onBubbleClicked() {
        if (bubbleClickListener != null) bubbleClickListener.run();
    }

    // ---------------- 手势 ----------------

    @Override public boolean onInterceptTouchEvent(MotionEvent e) { return true; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // 用屏幕绝对坐标（raw）拖拽，避免窗口移动导致坐标漂移震荡
                rawDownX = e.getRawX();
                rawDownY = e.getRawY();
                dragging = false;
                longPressArmed = true;
                longPressRunnable = new Runnable() {
                    @Override public void run() {
                        if (longPressArmed && !dragging && listener != null) {
                            listener.onLongPress();
                        }
                    }
                };
                handler.postDelayed(longPressRunnable, 600);
                gesture.onTouchEvent(e);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - rawDownX;
                float dy = e.getRawY() - rawDownY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    longPressArmed = false;
                    handler.removeCallbacks(longPressRunnable);
                    dragging = true;
                    onDragStart();
                }
                if (dragging) {
                    onDrag(dx, dy);
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
                    onDragEnd();
                    return true;
                }
                gesture.onTouchEvent(e);
                return true;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                longPressArmed = false;
                if (dragging) {
                    dragging = false;
                    onDragEnd();
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    // ---------------- 拖拽 ----------------

    protected void onDragStart() {
        if (params == null) return;
        baseX = params.x;
        baseY = params.y;
        onDragStateChanged(true);
    }

    protected void onDrag(float dx, float dy) {
        if (params == null || wm == null) return;
        params.x = baseX + (int) dx;
        params.y = baseY + (int) dy;
        wm.updateViewLayout(this, params);
    }

    protected void onDragEnd() {
        if (params == null) return;
        baseX = params.x;
        baseY = params.y;
        onDragStateChanged(false);
    }

    /** 拖拽状态变化（子类可暂停/恢复渲染以提升流畅度） */
    protected void onDragStateChanged(boolean dragging) {}

    // ---------------- 爱心特效（共用） ----------------

    public void spawnHearts() {
        for (int i = 0; i < 5; i++) {
            final TextView h = new TextView(getContext());
            h.setText("❤");
            h.setTextColor(0xFFFF6B9D);
            h.setTextSize(18 + (float) (Math.random() * 8));
            addView(h, new FrameLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
            final float startY = -dp(100 + (int) (Math.random() * 40));
            h.setTranslationY(startY);
            h.setTranslationX((float) (Math.random() * 120 - 60));
            h.setAlpha(0f);
            h.animate().alpha(1f).setDuration(180).withEndAction(new Runnable() {
                @Override public void run() {
                    h.animate()
                            .translationY(startY - dp(150 + (int) (Math.random() * 60)))
                            .translationX(h.getTranslationX() + (float) (Math.random() * 40 - 20))
                            .alpha(0f)
                            .setDuration(1300)
                            .withEndAction(new Runnable() {
                                @Override public void run() {
                                    try { removeView(h); } catch (Exception ignored) {}
                                }
                            })
                            .start();
                }
            }).start();
        }
    }

    protected int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    protected Handler getOverlayHandler() { return handler; }

    // ---------------- PetHost 抽象方法 ----------------

    @Override public abstract void showBubble(String text, long ms);
    @Override public abstract void setBubbleText(String text);
    @Override public abstract void hideBubble();
    @Override public abstract void tapReaction();
    @Override public abstract void patReaction();
    @Override public abstract void aiThinking();
    @Override public abstract void aiReply(boolean success);
    @Override public abstract void recycle();
}
