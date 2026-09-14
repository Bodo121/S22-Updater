package com.bodo121.s22updater;

import android.app.Application;
import com.facebook.soloader.SoLoader;

public final class ReactApplicationHost extends Application {
    @Override public void onCreate() {
        super.onCreate();
        SoLoader.init(this, false);
    }
}
