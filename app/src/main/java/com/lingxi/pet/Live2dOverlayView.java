package com.lingxi.pet;

import android.content.Context;
import android.view.WindowManager;

/**
 * Live2D 桌宠悬浮窗：支持拖拽移动（Live2D 骨骼动画自身足够顺滑，拖拽时无需额外动画）。
 */
public class Live2dOverlayView extends Live2dStage {

    public Live2dOverlayView(Context c, WindowManager wm, WindowManager.LayoutParams params) {
        super(c);
        attachDrag(wm, params);
    }
}
