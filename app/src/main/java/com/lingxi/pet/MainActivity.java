package com.lingxi.pet;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 灵汐主界面：桌宠预览 + 悬浮窗开关 + AI 配置 + 形象资源管理。
 */
public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_NOTIF = 1002;

    private PetStage preview;
    private Switch swOverlay, swTts, swAuto;
    private EditText etChat, etBase, etKey, etModel, etPrompt, etSprite;
    private TextView tvChatReply, tvSpriteStatus, tvPetInfo, tvSizeLabel;
    private SeekBar seekSize;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        loadConfigIntoUi();
        setupListeners();
        showPetInfo();
    }

    private void bindViews() {
        preview = findViewById(R.id.preview_stage);
        swOverlay = findViewById(R.id.sw_overlay);
        swTts = findViewById(R.id.sw_tts);
        swAuto = findViewById(R.id.sw_autostart);
        etChat = findViewById(R.id.et_chat);
        tvChatReply = findViewById(R.id.tv_chat_reply);
        etBase = findViewById(R.id.et_api_base);
        etKey = findViewById(R.id.et_api_key);
        etModel = findViewById(R.id.et_model);
        etPrompt = findViewById(R.id.et_prompt);
        etSprite = findViewById(R.id.et_sprite_url);
        tvSpriteStatus = findViewById(R.id.tv_sprite_status);
        tvPetInfo = findViewById(R.id.tv_pet_info);
        tvSizeLabel = findViewById(R.id.tv_size_label);
        seekSize = findViewById(R.id.seek_pet_size);
    }

    private void loadConfigIntoUi() {
        swOverlay.setChecked(PetConfig.overlayEnabled(this));
        swTts.setChecked(PetConfig.ttsEnabled(this));
        swAuto.setChecked(PetConfig.autostart(this));
        etBase.setText(PetConfig.apiBase(this));
        etKey.setText(PetConfig.apiKey(this));
        etModel.setText(PetConfig.model(this));
        etPrompt.setText(PetConfig.systemPrompt(this));
        etSprite.setText(PetConfig.spriteUrl(this));
        int size = PetConfig.petSizeDp(this);
        seekSize.setProgress(size - 100);
        tvSizeLabel.setText("形象大小：" + size + " dp");
        tvSpriteStatus.setText(hasLocalSprite() ? "形象资源：本地已就绪 ✅" : "形象资源：未下载（将使用内置资源）");
    }

    private void setupListeners() {
        // 预览互动
        preview.pet.setListener(new PetView.Listener() {
            @Override public void onTap() {
                preview.pet.animator().playOnce(PetAnimator.State.WAVE);
                preview.showBubble("嗨～主人！我是灵汐 🐳", 3000);
            }
            @Override public void onPat() {
                preview.pet.animator().playOnce(PetAnimator.State.JUMP);
                preview.pet.spawnHearts();
                preview.showBubble("嘻嘻，被摸头好舒服～", 3000);
            }
            @Override public void onLongPress() {
                Toast.makeText(MainActivity.this, "长按可以拖动我哦（悬浮窗里）", Toast.LENGTH_SHORT).show();
            }
        });

        // 悬浮窗开关
        swOverlay.setOnCheckedChangeListener((v, on) -> {
            PetConfig.setOverlayEnabled(this, on);
            if (on) {
                if (!Settings.canDrawOverlays(this)) {
                    requestOverlayPermission();
                    swOverlay.setChecked(false);
                    return;
                }
                requestNotifPermissionIfNeeded();
                startPetService();
            } else {
                stopPetService();
            }
        });

        // 应用内快速聊天
        findViewById(R.id.btn_chat_send).setOnClickListener(v -> sendChat());

        // 设置项自动保存
        watch(etBase, s -> PetConfig.setApiBase(this, s));
        watch(etKey, s -> PetConfig.setApiKey(this, s));
        watch(etModel, s -> PetConfig.setModel(this, s));
        watch(etPrompt, s -> PetConfig.setSystemPrompt(this, s));
        watch(etSprite, s -> PetConfig.setSpriteUrl(this, s));

        swTts.setOnCheckedChangeListener((v, on) -> PetConfig.setTtsEnabled(this, on));
        swAuto.setOnCheckedChangeListener((v, on) -> PetConfig.setAutostart(this, on));

        // 大小调节
        seekSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int size = progress + 100;
                tvSizeLabel.setText("形象大小：" + size + " dp");
                if (fromUser) PetConfig.setPetSizeDp(MainActivity.this, size);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                // 悬浮窗运行时应用新大小
                if (PetService.running) {
                    stopPetService();
                    if (Settings.canDrawOverlays(MainActivity.this)) startPetService();
                }
            }
        });

        // 下载形象资源
        findViewById(R.id.btn_sprite_download).setOnClickListener(v -> downloadSprite());
    }

    private void watch(EditText et, final java.util.function.Consumer<String> saver) {
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (saver != null) saver.accept(s == null ? "" : s.toString());
            }
        });
    }

    private void showPetInfo() {
        try (InputStream is = getAssets().open("pet.json")) {
            byte[] buf = new byte[is.available()];
            int off = 0;
            while (off < buf.length) {
                int n = is.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            JSONObject o = new JSONObject(new String(buf, 0, off, StandardCharsets.UTF_8));
            String name = o.optString("displayName", "DeepSeek娘");
            String desc = o.optString("description", "");
            tvPetInfo.setText("当前形象：" + name + " —— " + desc);
        } catch (Exception e) {
            tvPetInfo.setText("当前形象：DeepSeek娘（蓝发鲸鱼女仆）");
        }
    }

    private boolean hasLocalSprite() {
        return PetResources.spriteFile(this).exists();
    }

    private void downloadSprite() {
        final String url = etSprite.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "请先填写形象资源 URL", Toast.LENGTH_SHORT).show();
            return;
        }
        tvSpriteStatus.setText("正在下载形象资源…");
        PetResources.download(this, url, () -> {
            tvSpriteStatus.setText("形象资源下载成功 ✅");
            PetResources.invalidateCache();
            preview.pet.animator().setSheet(PetResources.load(this));
            preview.showBubble("新衣服到手啦～好看吗？", 3500);
            Toast.makeText(this, "形象资源已更新", Toast.LENGTH_SHORT).show();
        }, () -> {
            tvSpriteStatus.setText("下载失败，请检查 URL 与网络");
            Toast.makeText(this, "形象资源下载失败", Toast.LENGTH_SHORT).show();
        });
    }

    private void sendChat() {
        final String text = etChat.getText().toString().trim();
        if (text.isEmpty()) return;
        etChat.setText("");
        tvChatReply.setText("灵汐：让我想想…");
        preview.pet.animator().setState(PetAnimator.State.WAIT, true);
        preview.showBubble("让我想想…", 60000);
        new Thread(() -> {
            String reply;
            boolean ok;
            try {
                reply = AiClient.chat(this, text);
                ok = true;
            } catch (Exception e) {
                reply = "呜…出错了：" + e.getMessage();
                ok = false;
            }
            final String r = reply;
            final boolean s = ok;
            runOnUiThread(() -> {
                preview.pet.animator().playOnce(s ? PetAnimator.State.REVIEW : PetAnimator.State.FAIL);
                preview.hideBubble();
                preview.showBubble(r, Math.min(12000, 3000 + r.length() * 80));
                tvChatReply.setText("灵汐：" + r);
                SpeechHelper.speak(this, r);
            });
        }).start();
    }

    // ---------------- 服务与权限 ----------------

    private void startPetService() {
        Intent i = new Intent(this, PetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void stopPetService() {
        Intent i = new Intent(this, PetService.class).setAction("STOP");
        startService(i);
    }

    private void requestOverlayPermission() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(i, REQ_OVERLAY);
    }

    private void requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                PetConfig.setOverlayEnabled(this, true);
                requestNotifPermissionIfNeeded();
                startPetService();
                swOverlay.setChecked(true);
                Toast.makeText(this, "灵汐已飞上你的桌面！", Toast.LENGTH_SHORT).show();
            } else {
                PetConfig.setOverlayEnabled(this, false);
                swOverlay.setChecked(false);
                Toast.makeText(this, "需要悬浮窗权限才能显示桌宠", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        boolean shouldBeOn = PetConfig.overlayEnabled(this) && Settings.canDrawOverlays(this);
        if (shouldBeOn && !PetService.running) startPetService();
        swOverlay.setChecked(shouldBeOn);
    }
}
