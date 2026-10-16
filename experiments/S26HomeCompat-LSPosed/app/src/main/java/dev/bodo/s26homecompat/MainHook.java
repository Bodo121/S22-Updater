package dev.bodo.s26homecompat;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Experimental bypasses for two S26 launcher coroutines incompatible with older One UI APIs. */
public final class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "S26HomeCompat";
    private static final String TARGET_PACKAGE = "com.sec.android.app.launcher";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + ": loaded in " + lpparam.packageName
                + " process=" + lpparam.processName);

        // This coroutine references SystemUI's TaskStackChangeListeners, which
        // fails to initialize because android.window.TaskSnapshotListener is absent.
        hookCoroutine(lpparam, "com.honeyspace.transition.datasource.TopTaskSource$2",
                "top-task/SystemUI transition tracking");

        // This coroutine references a nested listener class missing from this launcher APK.
        hookCoroutine(lpparam,
                "com.honeyspace.core.repository.PackageOperationTracker$automatedOperation$1",
                "automated package-operation tracking");
    }

    private static void hookCoroutine(XC_LoadPackage.LoadPackageParam lpparam,
                                      String className, String feature) {
        try {
            XposedHelpers.findAndHookMethod(
                    className, lpparam.classLoader, "invokeSuspend", Object.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            try {
                                Object unit = XposedHelpers.getStaticObjectField(
                                        XposedHelpers.findClass("kotlin.Unit", lpparam.classLoader),
                                        "INSTANCE");
                                XposedBridge.log(TAG + ": bypassed " + feature);
                                return unit;
                            } catch (Throwable error) {
                                XposedBridge.log(TAG + ": Unit lookup failed for "
                                        + feature + ": " + error);
                                return null;
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hook installed for " + className);
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": FAILED to hook " + className
                    + " (" + feature + "): " + error);
        }
    }
}
