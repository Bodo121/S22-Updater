package com.bodo121.s22updater;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.facebook.react.*;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.react.common.LifecycleState;
import com.facebook.hermes.reactexecutor.HermesExecutorFactory;
import com.horcrux.svg.SvgPackage;

/** Retains the existing native controller and fallback UI during the migration. */
public final class ReactScreenActivity extends MainActivity implements DefaultHardwareBackBtnHandler {
    private ReactRootView root;
    private ReactInstanceManager manager;
    private final Handler events = new Handler(Looper.getMainLooper());
    private long revision;
    private org.json.JSONObject dialog;
    private Runnable dialogPrimary, dialogSecondary;
    private long dialogId;
    private volatile boolean destroyed;
    private final Runnable emit = () -> {
        if (manager == null || destroyed) return;
        try {
            ReactContext context = manager.getCurrentReactContext();
            if (context != null && context.hasActiveReactInstance())
                context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("S22State", snapshot());
        } catch (Throwable t) {
            android.util.Log.w("S22Bridge", "state snapshot publish failed", t);
        }
    };

    String snapshot() {
        try {
            return new org.json.JSONObject(presentationSnapshot()).put("revision", ++revision)
                    .put("dialog", dialog == null ? org.json.JSONObject.NULL : dialog).toString();
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    void executeAction(String name, String value) {
        if (destroyed) throw new IllegalStateException("Activity is destroyed");
        android.util.Log.i("S22Bridge", "action=" + name);
        presentationAction(name, value);
        changed();
    }
    private void changed() { events.removeCallbacks(emit); events.post(emit); }

    @Override protected void showSheet(String title, String message, String primary, Runnable primaryAction,
                                        String secondary, Runnable secondaryAction) {
        try {
            dialog = new org.json.JSONObject().put("id", ++dialogId).put("title", title)
                    .put("message", message).put("primary", primary).put("secondary", secondary);
            dialogPrimary = primaryAction; dialogSecondary = secondaryAction;
            changed();
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }

    void respondDialog(double id, String choice) {
        if (dialog == null || id != dialogId) return;
        Runnable callback;
        switch (choice) {
            case "primary": callback = dialogPrimary; break;
            case "secondary": callback = dialogSecondary; break;
            case "dismiss": callback = null; break;
            default: throw new IllegalArgumentException("Invalid dialog response");
        }
        // Clear before invoking: repeated JS messages cannot replay a privileged callback.
        dialog = null; dialogPrimary = null; dialogSecondary = null;
        if (callback != null) callback.run();
        changed();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // super restores same-boot state before the first React frame.
        Bundle props = new Bundle(); props.putString("initialState", snapshot());
        root = new ReactRootView(this);
        manager = ReactInstanceManager.builder().setApplication(getApplication())
                .setCurrentActivity(this).setBundleAssetName("index.android.bundle")
                .setJSMainModulePath("index").setUseDeveloperSupport(false)
                .setJavaScriptExecutorFactory(new HermesExecutorFactory())
                .addPackage(new MainReactPackage()).addPackage(new SvgPackage()).addPackage(new S22Package())
                .setInitialLifecycleState(LifecycleState.BEFORE_CREATE).build();
        manager.addReactInstanceEventListener(context -> events.post(emit));
        observePresentation(this::changed);
        root.startReactApplication(manager, "S22Updater", props);
        setContentView(root);
    }
    @Override protected void onResume() { super.onResume(); if (manager != null) manager.onHostResume(this, this); changed(); }
    @Override protected void onPause() { if (manager != null) manager.onHostPause(this); super.onPause(); }
    @Override public void onBackPressed() { if (manager != null) manager.onBackPressed(); else super.onBackPressed(); }
    @Override public void invokeDefaultOnBackPressed() { super.onBackPressed(); }
    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (manager != null) manager.onActivityResult(this, request, result, data);
    }
    @Override protected void onDestroy() {
        destroyed = true;
        events.removeCallbacksAndMessages(null);
        try {
            if (root != null) root.unmountReactApplication();
        } catch (Throwable t) {
            android.util.Log.w("S22Bridge", "react unmount failed", t);
        } finally {
            root = null;
        }
        try {
            if (manager != null) { manager.onHostDestroy(this); manager.destroy(); }
        } catch (Throwable t) {
            android.util.Log.w("S22Bridge", "react host destroy failed", t);
        } finally {
            manager = null;
        }
        super.onDestroy();
    }
}
