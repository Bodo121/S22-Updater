package com.bodo121.s22updater;

import com.facebook.react.bridge.*;

final class S22Module extends ReactContextBaseJavaModule {
    S22Module(ReactApplicationContext context) { super(context); }
    @Override public String getName() { return "S22Native"; }
    private ReactScreenActivity host() {
        if (!(getCurrentActivity() instanceof ReactScreenActivity))
            throw new IllegalStateException("Native controller is not attached");
        return (ReactScreenActivity) getCurrentActivity();
    }
    @ReactMethod public void getState(Promise promise) {
        UiThreadUtil.runOnUiThread(() -> {
            try { promise.resolve(host().snapshot()); } catch (Exception e) { promise.reject("STATE", e); }
        });
    }
    @ReactMethod public void action(String name, String value, Promise promise) {
        UiThreadUtil.runOnUiThread(() -> {
            try { host().executeAction(name, value); promise.resolve(null); }
            catch (Exception e) { promise.reject("ACTION", e); }
        });
    }
    @ReactMethod public void openNativeUi(Promise promise) {
        UiThreadUtil.runOnUiThread(() -> {
            try {
                ReactScreenActivity activity = host();
                org.json.JSONObject state = new org.json.JSONObject(activity.snapshot());
                if (state.getBoolean("busy")) throw new IllegalStateException("Wait for the current operation");
                activity.startActivity(new android.content.Intent(activity, MainActivity.class));
                activity.finish(); promise.resolve(null);
            } catch (Exception e) { promise.reject("FALLBACK", e); }
        });
    }
    @ReactMethod public void respondDialog(double id, String choice, Promise promise) {
        UiThreadUtil.runOnUiThread(() -> {
            try { host().respondDialog(id, choice); promise.resolve(null); }
            catch (Exception e) { promise.reject("DIALOG", e); }
        });
    }
    @ReactMethod public void addListener(String name) { }
    @ReactMethod public void removeListeners(double count) { }
}
