package com.lingxi.pet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

/**
 * 精灵图桌宠角色视图：负责逐帧绘制。手势由 BaseOverlay 统一处理。
 */
public class PetView extends View {

    public interface Listener {
        void onTap();
        void onPat();
        void onLongPress();
    }

    protected PetAnimator animator;
    protected Bitmap sheet;
    private final Rect petRect = new Rect();
    private final android.graphics.Paint debugPaint = new android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG);

    public PetView(Context c) {
        super(c);
        sheet = PetResources.load(c);
        animator = new PetAnimator(sheet, null, new Runnable() {
            @Override public void run() { postInvalidateOnAnimation(); }
        });
        animator.start();
    }

    public PetAnimator animator() { return animator; }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        float scale = Math.min(w / (float) PetAnimator.CELL_W, h / (float) PetAnimator.CELL_H);
        int pw = (int) (PetAnimator.CELL_W * scale);
        int ph = (int) (PetAnimator.CELL_H * scale);
        int left = (w - pw) / 2;
        int top = h - ph;
        petRect.set(left, top, left + pw, top + ph);
        animator.draw(c, petRect);
        if (PetConfig.debugHitbox(getContext())) {
            float d = getResources().getDisplayMetrics().density;
            debugPaint.setStyle(android.graphics.Paint.Style.STROKE);
            debugPaint.setStrokeWidth(2 * d);
            debugPaint.setColor(0xFF00FF66);
            c.drawRect(petRect, debugPaint);
            debugPaint.setColor(0xFFFF4444);
            c.drawRect(0, 0, getWidth(), getHeight(), debugPaint);
        }
    }
}
