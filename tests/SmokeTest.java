package com.bodo121.s22updater.tests;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

public final class SmokeTest extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private TextView find(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = find(group.getChildAt(i), label);
                if (result != null) return result;
            }
        }
        return null;
    }
    private void check(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        for (String label : new String[]{"Check for updates", "Check root", "Authorize Shizuku", "Open KernelSU Manager", "Refresh changelog", "Save feed URL", "Check for app updates", "Diagnose Shizuku handshake"}) {
            TextView button = find(decor, label);
            if (button == null || !button.hasOnClickListeners()) throw new AssertionError("Unwired: " + label);
        }
        for (String label : new String[]{"Log", "Settings", "Home"}) {
            TextView button = find(decor, label);
            if (button == null || !button.performClick()) throw new AssertionError("Navigation: " + label);
        }
        if (find(decor, "Root: not checked") == null) throw new AssertionError("Root status missing");
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent intent = new Intent().setClassName("com.bodo121.s22updater", "com.bodo121.s22updater.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity = startActivitySync(intent);
            waitForIdleSync();
            SystemClock.sleep(2000);
            runOnMainSync(() -> check(activity));
            ActivityMonitor monitor = addMonitor("com.bodo121.s22updater.MainActivity", null, false);
            runOnMainSync(activity::recreate);
            Activity recreated = waitForMonitorWithTimeout(monitor, 10000);
            removeMonitor(monitor);
            if (recreated == null) throw new AssertionError("Recreation timed out");
            waitForIdleSync();
            runOnMainSync(() -> { check(recreated); recreated.finish(); });
            result.putString("stream", "PASS: launch, status views, listeners, tabs, activity recreation\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
