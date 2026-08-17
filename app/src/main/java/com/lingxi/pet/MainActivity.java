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
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 灵汐主界面：桌宠预览 + 悬浮窗开关 + 动画风格 + AI 配置 + 形象资源管理。
 */
public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_NOTIF = 1002;

    private FrameLayout previewContainer;
    private PetHost previewHost;
    private Switch swOverlay, swTts, swAuto, swDebug;
    private EditText etChat, etBase, etKey, etModel, etPrompt, etSprite, etModelUrl;
    private TextView tvChatReply, tvSpriteStatus, tvPetInfo, tvSizeLabel;
    private SeekBar seekSize;
    private Spinner spStyle, spModel;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LingxiDiagnostics.clear();
        setContentView(R.layout.activity_main);
        bindViews();
        loadConfigIntoUi();
        setupStyleSpinner();
        setupModelSpinner();
        setupListeners();
        buildPreview();
        showPetInfo();
    }

    private void bindViews() {
        previewContainer = findViewById(R.id.preview_container);
        swOverlay = findViewById(R.id.sw_overlay);
        swTts = findViewById(R.id.sw_tts);
        swAuto = findViewById(R.id.sw_autostart);
        swDebug = findViewById(R.id.sw_debug);
        etChat = findViewById(R.id.et_chat);
        tvChatReply = findViewById(R.id.tv_chat_reply);
        etBase = findViewById(R.id.et_api_base);
        etKey = findViewById(R.id.et_api_key);
        etModel = findViewById(R.id.et_model);
        etPrompt = findViewById(R.id.et_prompt);
        etSprite = findViewById(R.id.et_sprite_url);
        etModelUrl = findViewById(R.id.et_model_url);
        tvSpriteStatus = findViewById(R.id.tv_sprite_status);
        tvPetInfo = findViewById(R.id.tv_pet_info);
        tvSizeLabel = findViewById(R.id.tv_size_label);
        seekSize = findViewById(R.id.seek_pet_size);
        spStyle = findViewById(R.id.sp_style);
        spModel = findViewById(R.id.sp_model);
    }

    private void loadConfigIntoUi() {
        swOverlay.setChecked(PetConfig.overlayEnabled(this));
        swTts.setChecked(PetConfig.ttsEnabled(this));
        swAuto.setChecked(PetConfig.autostart(this));
        swDebug.setChecked(PetConfig.debugHitbox(this));
        etBase.setText(PetConfig.apiBase(this));
        etKey.setText(PetConfig.apiKey(this));
        etModel.setText(PetConfig.model(this));
        etPrompt.setText(PetConfig.systemPrompt(this));
        etSprite.setText(PetConfig.spriteUrl(this));
        etModelUrl.setText(PetConfig.live2dModelUrl(this));
        int size = PetConfig.petSizeDp(this);
        seekSize.setProgress(size - 100);
        tvSizeLabel.setText("形象大小：" + size + " dp");
        tvSpriteStatus.setText(hasLocalSprite() ? "像素风格资源：本地已就绪 ✅" : "像素风格资源：未下载（将使用内置资源）");
    }

    private void setupStyleSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Live2D 灵动（默认）", "DeepSeek娘 经典像素"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStyle.setAdapter(adapter);
        spStyle.setSelection(PetConfig.live2dMode(this) ? 0 : 1);
        final boolean[] ready = {false};
        spStyle.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!ready[0]) { ready[0] = true; return; }
                String style = (position == 0) ? PetConfig.STYLE_LIVE2D : PetConfig.STYLE_SPRITE;
                PetConfig.setPetStyle(MainActivity.this, style);
                buildPreview();
                restartPetService();
                showPetInfo();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupModelSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Haru（内置·蓝发少女）", "白兮 Baixi（内置·VTS）", "NOIR（内置·VTS）"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spModel.setAdapter(adapter);
        String cur = PetConfig.live2dModel(this);
        int sel = PetConfig.MODEL_NOIR.equals(cur) ? 2 : (PetConfig.MODEL_BAIXI.equals(cur) ? 1 : 0);
        spModel.setSelection(sel);
        final boolean[] ready = {false};
        spModel.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!ready[0]) { ready[0] = true; return; }
                String m = position == 2 ? PetConfig.MODEL_NOIR
                        : (position == 1 ? PetConfig.MODEL_BAIXI : PetConfig.MODEL_HARU);
                PetConfig.setLive2dModel(MainActivity.this, m);
                buildPreview();
                restartPetService();
                showPetInfo();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void buildPreview() {
        if (previewHost != null) {
            try { previewHost.recycle(); } catch (Exception ignored) {}
            previewHost = null;
        }
        previewContainer.removeAllViews();
        BaseOverlay host = PetConfig.live2dMode(this)
                ? new Live2dStage(this)
                : new PetStage(this);
        previewHost = host;
        previewContainer.addView(host,
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        host.setListener(new PetView.Listener() {
            @Override public void onTap() {
                host.tapReaction();
                host.showBubble("嗨～主人！我是灵汐 🐳", 3000);
            }
            @Override public void onPat() {
                host.patReaction();
                host.showBubble("嘻嘻，被摸头好舒服～", 3000);
            }
            @Override public void onLongPress() {
                Toast.makeText(MainActivity.this, "长按可以拖动我哦（悬浮窗里）", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupListeners() {
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

        findViewById(R.id.btn_chat_send).setOnClickListener(v -> sendChat());

        watch(etBase, s -> PetConfig.setApiBase(this, s));
        watch(etKey, s -> PetConfig.setApiKey(this, s));
        watch(etModel, s -> PetConfig.setModel(this, s));
        watch(etPrompt, s -> PetConfig.setSystemPrompt(this, s));
        watch(etSprite, s -> PetConfig.setSpriteUrl(this, s));
        watch(etModelUrl, s -> PetConfig.setLive2dModelUrl(this, s));

        // 模型 URL 输入完成后（失焦）应用
        etModelUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                buildPreview();
                restartPetService();
            }
        });

        swTts.setOnCheckedChangeListener((v, on) -> PetConfig.setTtsEnabled(this, on));
        swAuto.setOnCheckedChangeListener((v, on) -> PetConfig.setAutostart(this, on));

        swDebug.setOnCheckedChangeListener((v, on) -> {
            PetConfig.setDebugHitbox(this, on);
            buildPreview();
            restartPetService();
        });

        seekSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int size = progress + 100;
                tvSizeLabel.setText("形象大小：" + size + " dp");
                if (fromUser) PetConfig.setPetSizeDp(MainActivity.this, size);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                if (PetService.running) {
                    stopPetService();
                    if (Settings.canDrawOverlays(MainActivity.this)) startPetService();
                }
            }
        });

        findViewById(R.id.btn_sprite_download).setOnClickListener(v -> downloadSprite());
        findViewById(R.id.btn_copy_log).setOnClickListener(v -> showDiagnostics());
    }

    private void showDiagnostics() {
        final String report = LingxiDiagnostics.buildReport(this);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        final TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTextSize(11);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int pad = Math.round(getResources().getDisplayMetrics().density * 14);
        tv.setPadding(pad, pad, pad, pad);
        sv.addView(tv);
        new android.app.AlertDialog.Builder(this)
                .setTitle("📋 诊断日志（可截图或复制）")
                .setView(sv)
                .setPositiveButton("复制到剪贴板", (d, w) -> LingxiDiagnostics.copy(this))
                .setNegativeButton("关闭", null)
                .show();
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
        if (PetConfig.live2dMode(this)) {
            String mm = PetConfig.live2dModel(this);
            String modelName = PetConfig.MODEL_NOIR.equals(mm) ? "NOIR（VTS 模型）"
                    : (PetConfig.MODEL_BAIXI.equals(mm) ? "白兮 Baixi（VTS 模型）"
                    : "Haru（Live2D 官方示例·蓝发少女）");
            String custom = PetConfig.live2dModelUrl(this);
            String suffix = custom.isEmpty() ? "" : " · 自定义: " + custom;
            tvPetInfo.setText("当前风格：Live2D 灵动 · 模型：" + modelName + suffix);
            return;
        }
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
            tvPetInfo.setText("当前风格：DeepSeek娘 经典像素 —— " + name + " " + desc);
        } catch (Exception e) {
            tvPetInfo.setText("当前风格：DeepSeek娘 经典像素");
        }
    }

    private boolean hasLocalSprite() {
        return PetResources.spriteFile(this).exists();
    }

    private void downloadSprite() {
        if (PetConfig.live2dMode(this)) {
            Toast.makeText(this, "当前是 Live2D 风格，无需下载像素资源", Toast.LENGTH_SHORT).show();
            return;
        }
        final String url = etSprite.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "请先填写形象资源 URL", Toast.LENGTH_SHORT).show();
            return;
        }
        tvSpriteStatus.setText("正在下载形象资源…");
        PetResources.download(this, url, () -> {
            tvSpriteStatus.setText("形象资源下载成功 ✅");
            PetResources.invalidateCache();
            if (previewHost instanceof PetStage) {
                ((PetStage) previewHost).pet.animator().setSheet(PetResources.load(this));
            }
            previewHost.showBubble("新衣服到手啦～好看吗？", 3500);
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
        previewHost.aiThinking();
        previewHost.showBubble("让我想想…", 60000);
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
                previewHost.aiReply(s);
                previewHost.hideBubble();
                previewHost.showBubble(r, Math.min(12000, 3000 + r.length() * 80));
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

    private void restartPetService() {
        if (PetService.running) {
            stopPetService();
            if (Settings.canDrawOverlays(this)) {
                handlerPostDelayed(500, this::startPetService);
            }
        }
    }

    private void handlerPostDelayed(long ms, Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r, ms);
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

    @Override protected void onDestroy() {
        LingxiDiagnostics.markCleanExit();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean shouldBeOn = PetConfig.overlayEnabled(this) && Settings.canDrawOverlays(this);
        if (shouldBeOn && !PetService.running) startPetService();
        swOverlay.setChecked(shouldBeOn);
    }
}
