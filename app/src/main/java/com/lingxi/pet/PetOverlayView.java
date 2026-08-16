package com.lingxi.pet;

import android.content.Context;
import android.view.WindowManager;

/**
 * 悬浮窗版桌宠：在 PetStage 基础上支持拖拽移动窗口。
 */
public class PetOverlayView extends PetStage {

    private final WindowManager wm;
    private WindowManager.LayoutParams params;
    private int baseX, baseY;
    private final Context ctx;

    public PetOverlayView(Context c, WindowManager wm, WindowManager.LayoutParams params) {
        super(c);
        this.ctx = c;
        this.wm = wm;
        this.params = params;
        this.baseX = params.x;
        this.baseY = params.y;

        pet.setDragListener(new PetView.DragListener() {
            @Override public void onDragStart() {
                baseX = params.x;
                baseY = params.y;
            }

            @Override public void onDrag(float dx, float dy) {
                params.x = baseX + (int) dx;
                params.y = baseY + (int) dy;
                wm.updateViewLayout(PetOverlayView.this, params);
                if (dx < 0) pet.animator().setState(PetAnimator.State.RUN_LEFT, true);
                else pet.animator().setState(PetAnimator.State.RUN_RIGHT, true);
            }

            @Override public void onDragEnd() {
                pet.animator().setState(PetAnimator.State.IDLE, true);
                baseX = params.x;
                baseY = params.y;
            }
        });
    }

    /** 点击气泡时触发（由服务设置为打开聊天） */
    public Runnable bubbleClickListener;

    @Override protected void onBubbleClicked() {
        if (bubbleClickListener != null) bubbleClickListener.run();
    }
}
