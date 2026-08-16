package com.lingxi.pet;

/**
 * 桌宠宿主接口：屏蔽「Live2D」与「像素精灵图」两种渲染实现的差异，
 * 服务层只依赖本接口。
 */
public interface PetHost {

    void setListener(PetView.Listener l);

    /** 点击气泡回调（用于打开聊天） */
    void setBubbleClickListener(Runnable r);

    void showBubble(String text, long ms);

    void setBubbleText(String text);

    void hideBubble();

    /** 双击摸头爱心 */
    void spawnHearts();

    /** 单击互动动画 */
    void tapReaction();

    /** 摸头动画 */
    void patReaction();

    /** AI 思考中动画 */
    void aiThinking();

    /** AI 回复完成（成功/失败） */
    void aiReply(boolean success);

    /** 释放资源（动画/WebView） */
    void recycle();
}
