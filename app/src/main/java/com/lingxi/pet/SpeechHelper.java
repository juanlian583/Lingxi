package com.lingxi.pet;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/** 语音合成（TTS）帮助类，让灵汐能开口说话。 */
public final class SpeechHelper {

    private static TextToSpeech tts;
    private static boolean ready = false;
    private static boolean initStarted = false;

    private static synchronized void ensureInit(Context c) {
        if (tts == null && !initStarted) {
            initStarted = true;
            tts = new TextToSpeech(c.getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        int r = tts.setLanguage(Locale.CHINA);
                        ready = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
                    }
                }
            });
        }
    }

    public static void speak(Context c, String text) {
        if (!PetConfig.ttsEnabled(c)) return;
        if (text == null || text.isEmpty()) return;
        ensureInit(c);
        if (ready && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lingxi");
        }
    }

    public static void stop() {
        if (tts != null) tts.stop();
    }

    public static void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            ready = false;
            initStarted = false;
        }
    }

    private SpeechHelper() {}
}
