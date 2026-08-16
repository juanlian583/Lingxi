package com.lingxi.pet;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 灵汐配置中心：所有用户设置都存在 SharedPreferences 中。
 */
public final class PetConfig {

    private static SharedPreferences sp;

    public static final String DEFAULT_SPRITE_URL =
            "https://raw.githubusercontent.com/xpy12367/codex-pet-DeepSeek-girl/main/spritesheet.webp";

    public static final String DEFAULT_API_BASE = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-chat";

    public static final String DEFAULT_PERSONA =
            "你是灵汐，一只可爱的蓝发鲸鱼女仆AI桌宠（DeepSeek娘二创形象）。" +
            "性格温柔可爱、元气满满、工作认真，偶尔会有点小慌张，失败时会摇动鲸鱼尾巴。" +
            "用简短、活泼、口语化的中文回复，可以带颜文字，但每次不要超过两句话。";

    public static synchronized SharedPreferences sp(Context c) {
        if (sp == null) {
            sp = c.getApplicationContext().getSharedPreferences("lingxi", Context.MODE_PRIVATE);
        }
        return sp;
    }

    // ---- 悬浮窗 ----
    public static boolean overlayEnabled(Context c) { return sp(c).getBoolean("overlay_enabled", false); }
    public static void setOverlayEnabled(Context c, boolean v) { sp(c).edit().putBoolean("overlay_enabled", v).apply(); }

    public static int petSizeDp(Context c) { return sp(c).getInt("pet_size_dp", 170); }
    public static void setPetSizeDp(Context c, int v) { sp(c).edit().putInt("pet_size_dp", v).apply(); }

    public static boolean ttsEnabled(Context c) { return sp(c).getBoolean("tts_enabled", true); }
    public static void setTtsEnabled(Context c, boolean v) { sp(c).edit().putBoolean("tts_enabled", v).apply(); }

    public static boolean autostart(Context c) { return sp(c).getBoolean("autostart", true); }
    public static void setAutostart(Context c, boolean v) { sp(c).edit().putBoolean("autostart", v).apply(); }

    // ---- AI ----
    public static String apiBase(Context c) { return sp(c).getString("api_base", DEFAULT_API_BASE); }
    public static void setApiBase(Context c, String v) { sp(c).edit().putString("api_base", v).apply(); }

    public static String apiKey(Context c) { return sp(c).getString("api_key", ""); }
    public static void setApiKey(Context c, String v) { sp(c).edit().putString("api_key", v).apply(); }

    public static String model(Context c) { return sp(c).getString("model", DEFAULT_MODEL); }
    public static void setModel(Context c, String v) { sp(c).edit().putString("model", v).apply(); }

    public static String systemPrompt(Context c) { return sp(c).getString("system_prompt", DEFAULT_PERSONA); }
    public static void setSystemPrompt(Context c, String v) { sp(c).edit().putString("system_prompt", v).apply(); }

    // ---- 形象资源 ----
    public static String spriteUrl(Context c) { return sp(c).getString("sprite_url", DEFAULT_SPRITE_URL); }
    public static void setSpriteUrl(Context c, String v) { sp(c).edit().putString("sprite_url", v).apply(); }

    // ---- 渲染风格：live2d（默认） / sprite（经典像素 DeepSeek娘） ----
    public static final String STYLE_LIVE2D = "live2d";
    public static final String STYLE_SPRITE = "sprite";
    public static String petStyle(Context c) { return sp(c).getString("pet_style", STYLE_LIVE2D); }
    public static void setPetStyle(Context c, String v) { sp(c).edit().putString("pet_style", v).apply(); }
    public static boolean live2dMode(Context c) { return STYLE_LIVE2D.equals(petStyle(c)); }

    /** 自定义 Live2D 模型 model3.json URL（为空用内置模型） */
    public static String live2dModelUrl(Context c) { return sp(c).getString("live2d_model_url", ""); }
    public static void setLive2dModelUrl(Context c, String v) { sp(c).edit().putString("live2d_model_url", v).apply(); }

    /** 内置 Live2D 模型选择：haru（默认）/ baixi（白兮 VTS） */
    public static final String MODEL_HARU = "haru";
    public static final String MODEL_BAIXI = "baixi";
    public static String live2dModel(Context c) { return sp(c).getString("live2d_model", MODEL_HARU); }
    public static void setLive2dModel(Context c, String v) { sp(c).edit().putString("live2d_model", v).apply(); }

    private PetConfig() {}
}
