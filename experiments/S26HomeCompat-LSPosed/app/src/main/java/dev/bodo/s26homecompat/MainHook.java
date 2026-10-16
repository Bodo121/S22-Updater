package dev.bodo.s26homecompat;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Experimental mitigation for the S26 One UI Home crash involving
 * android.window.TaskSnapshotListener. This does not add the missing API.
 */
public final class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "S26HomeCompat";
    private static final String TARGET_PACKAGE = "com.sec.android.app.launcher";
    private static final String TARGET_CLASS = "com.honeyspace.transition.datasource.TopTaskSource$2";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + ": loaded in " + lpparam.packageName + " process=" + lpparam.processName);
        try {
            XposedHelpers.findAndHookMethod(
                TARGET_CLASS, lpparam.classLoader, "invokeSuspend", Object.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        try {
                            Object unit = XposedHelpers.getStaticObjectField(
                                XposedHelpers.findClass("kotlin.Unit", lpparam.classLoader), "INSTANCE");
                            XposedBridge.log(TAG + ": bypassed TopTaskSource coroutine; task-stack transition tracking may be disabled.");
                            return unit;
                        } catch (Throwable error) {
                            XposedBridge.log(TAG + ": kotlin.Unit lookup failed: " + error);
                            return null;
                        }
                    }
                });
            XposedBridge.log(TAG + ": hook installed");
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": FAILED to install hook; class/API resolution may precede interception: " + error);
        }
    }
}
