package com.lingxi.pet;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 悬浮聊天窗口：在桌宠旁与 AI 对话。
 */
public class ChatDialog extends Dialog {

    public interface Callback {
        void onSend(String text);
    }

    private TextView history;
    private EditText input;
    private ScrollView scroll;
    private final Callback callback;

    public ChatDialog(Context c, Callback cb) {
        super(c);
        this.callback = cb;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_chat);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().setDimAmount(0.25f);

        history = findViewById(R.id.chat_history);
        input = findViewById(R.id.chat_input);
        scroll = findViewById(R.id.chat_scroll);

        findViewById(R.id.chat_send).setOnClickListener(v -> send());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });

        append("灵汐", "主人好呀～我是灵汐，想聊什么都可以哦！🐳");
    }

    private void send() {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) return;
        input.setText("");
        append("我", t);
        if (callback != null) callback.onSend(t);
    }

    public void append(String who, String text) {
        if (history == null) return;
        String prefix = history.getText().length() > 0 ? "\n" : "";
        history.append(prefix + who + "：" + text);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    public void appendAssistant(String text) {
        append("灵汐", text);
    }

    @Override public void show() {
        Window w = getWindow();
        if (w != null) {
            w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.y = 48;
            w.setAttributes(lp);
        }
        super.show();
    }
}
