package com.lingxi.pet;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 桌宠舞台：角色 + 上方的对话气泡。
 * 应用内预览和悬浮窗都复用它。
 */
public class PetStage extends FrameLayout {

    public final PetView pet;
    public final TextView bubble;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideBubble;

    public PetStage(Context c) {
        this(c, null);
    }

    public PetStage(Context c, AttributeSet a) {
        super(c, a);
        setClipChildren(false);

        // 气泡
        bubble = new TextView(c);
        bubble.setTextColor(0xFF3A4157);
        bubble.setTextSize(13);
        bubble.setLineSpacing(2f, 1f);
        bubble.setPadding(dp(12), dp(7), dp(12), dp(7));
        bubble.setBackgroundResource(R.drawable.bubble_bg);
        bubble.setVisibility(GONE);
        bubble.setMaxWidth(dp(200));
        bubble.setOnClickListener(new OnClickListener() {
            @Override public void onClick(android.view.View v) {
                onBubbleClicked();
            }
        });
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        blp.bottomMargin = dp(PetConfig.petSizeDp(c)) + dp(8);
        addView(bubble, blp);

        // 角色
        int size = dp(PetConfig.petSizeDp(c));
        pet = new PetView(c);
        addView(pet, new FrameLayout.LayoutParams(size, size, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
    }

    /** 气泡被点击（悬浮窗中用于打开聊天） */
    protected void onBubbleClicked() {}

    public void showBubble(String text, long ms) {
        bubble.setText(text);
        bubble.setVisibility(VISIBLE);
        handler.removeCallbacks(hideBubble);
        hideBubble = new Runnable() {
            @Override public void run() { bubble.setVisibility(GONE); }
        };
        handler.postDelayed(hideBubble, ms);
    }

    public void hideBubble() {
        handler.removeCallbacks(hideBubble);
        bubble.setVisibility(GONE);
    }

    public void setBubbleText(String text) {
        bubble.setText(text);
        bubble.setVisibility(VISIBLE);
    }

    protected int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
