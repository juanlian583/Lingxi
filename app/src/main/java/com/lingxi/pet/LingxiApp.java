package com.lingxi.pet;

import android.app.Application;

public class LingxiApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        LingxiDiagnostics.init(this);
        CrashHandler.install(this);
    }
}
