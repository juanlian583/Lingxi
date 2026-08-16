package com.lingxi.pet;

import android.content.Context;
import android.view.WindowManager;

/**
 * 精灵图桌宠悬浮窗：拖拽时播放左右跑动画。
 */
public class PetOverlayView extends PetStage {

    public PetOverlayView(Context c, WindowManager wm, WindowManager.LayoutParams params) {
        super(c);
        attachDrag(wm, params);
    }

    @Override protected void onDragStart() {
        super.onDragStart();
        pet.animator().setState(PetAnimator.State.RUN, true);
    }

    @Override protected void onDrag(float dx, float dy) {
        super.onDrag(dx, dy);
        if (dx < 0) pet.animator().setState(PetAnimator.State.RUN_LEFT, true);
        else if (dx > 0) pet.animator().setState(PetAnimator.State.RUN_RIGHT, true);
    }

    @Override protected void onDragEnd() {
        super.onDragEnd();
        pet.animator().setState(PetAnimator.State.IDLE, true);
    }
}
