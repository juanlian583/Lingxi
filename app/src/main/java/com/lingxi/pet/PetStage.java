package com.lingxi.pet;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * 经典像素风桌宠（DeepSeek娘 精灵图）：
 * 精灵动画 + 原生气泡。
 */
public class PetStage extends BaseOverlay implements PetHost {

    public final PetView pet;
    private final TextView bubble;
    private final Random random = new Random();

    private Runnable hideBubble;

    public PetStage(Context c) {
        this(c, null);
    }

    public PetStage(Context c, AttributeSet a) {
        super(c);
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
            @Override public void onClick(View v) { onBubbleClicked(); }
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

    // ---------------- PetHost ----------------

    @Override public void showBubble(String text, long ms) {
        bubble.setText(text);
        bubble.setVisibility(VISIBLE);
        getOverlayHandler().removeCallbacks(hideBubble);
        hideBubble = new Runnable() {
            @Override public void run() { bubble.setVisibility(GONE); }
        };
        getOverlayHandler().postDelayed(hideBubble, ms);
    }

    @Override public void setBubbleText(String text) {
        bubble.setText(text);
        bubble.setVisibility(VISIBLE);
    }

    @Override public void hideBubble() {
        getOverlayHandler().removeCallbacks(hideBubble);
        bubble.setVisibility(GONE);
    }

    @Override public void tapReaction() {
        int r = random.nextInt(3);
        if (r == 0) pet.animator().playOnce(PetAnimator.State.WAVE);
        else if (r == 1) pet.animator().playOnce(PetAnimator.State.JUMP);
        else pet.animator().playOnce(PetAnimator.State.REVIEW);
    }

    @Override public void patReaction() {
        pet.animator().playOnce(PetAnimator.State.JUMP);
        spawnHearts();
    }

    @Override public void aiThinking() {
        pet.animator().setState(PetAnimator.State.WAIT, true);
    }

    @Override public void aiReply(boolean success) {
        pet.animator().playOnce(success ? PetAnimator.State.REVIEW : PetAnimator.State.FAIL);
    }

    @Override public void recycle() {
        pet.animator().stop();
    }
}
