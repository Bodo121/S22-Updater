# S26 One UI Home Compat — experimental LSPosed source project

**Status: experimental source project, not a tested or installable APK.** This project has not been compiled or run on a device.

It targets `com.sec.android.app.launcher` and attempts to replace `com.honeyspace.transition.datasource.TopTaskSource$2.invokeSuspend(Object)` with `kotlin.Unit` to avoid the crash path that reached `TaskStackChangeListeners` and the missing `android.window.TaskSnapshotListener` on One UI 8.

This does **not** add the missing framework API or guarantee the launcher will start. ART may resolve the missing class before LSPosed can install the hook. It also does not fix the separate `PackageOperationTracker$automatedOperation$1` error.

## Build

The dedicated `.github/workflows/build-s26homecompat.yml` workflow can be dispatched manually and uploads a debug APK as a GitHub Actions artifact. Compilation is not proof of runtime compatibility.

## Cautious testing

Keep the working S22 launcher available before testing. Enable the module in LSPosed scoped only to `com.sec.android.app.launcher`. If it crashes or the hook fails, disable it with LSPosed Safe Mode/recovery. Do not replace framework or SystemUI files based on this experiment.