package com.bodo121.s22updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/** Self-update check against this app's own GitHub releases. */
final class AppUpdate {
    static final String REPO = "Bodo121/S22-Updater";
    static final String LATEST_API =
            "https://api.github.com/repos/" + REPO + "/releases/latest";
    static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases/latest";

    static final class Info {
        int versionCode;
        String versionName = "";
        String tag = "";
        String apkName = "";
        String apkUrl = "";
        String apkSha256 = "";
        String htmlUrl = RELEASES_PAGE;
    }

    static int installedCode(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) return (int) info.getLongVersionCode();
            return info.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static String installedName(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** Reads releases/latest, then the app-update.json asset for exact metadata. */
    static Info check() throws Exception {
        JSONObject release = new JSONObject(Network.text(LATEST_API));
        Info info = new Info();
        info.tag = release.optString("tag_name", "").trim();
        info.htmlUrl = release.optString("html_url", RELEASES_PAGE);
        String manifestUrl = null;
        String apkUrl = null;
        String sumsUrl = null;
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                if (name.equals("app-update.json")) manifestUrl = url;
                else if (name.endsWith(".apk") && apkUrl == null) apkUrl = url;
                else if (name.equals("SHA256SUMS")) sumsUrl = url;
            }
        }
        if (manifestUrl == null || manifestUrl.isEmpty())
            throw new java.io.IOException("Release " + info.tag + " has no app-update.json");
        JSONObject manifest = new JSONObject(Network.text(manifestUrl));
        info.versionCode = manifest.getInt("versionCode");
        info.versionName = manifest.optString("versionName", info.tag);
        info.apkName = manifest.optString("apk", "");
        info.apkUrl = manifest.optString("apkUrl", apkUrl == null ? "" : apkUrl);
        if (sumsUrl != null && !sumsUrl.isEmpty()) {
            String sums = Network.text(sumsUrl);
            for (String line : sums.split("\n")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2 && parts[1].equals(info.apkName)) {
                    info.apkSha256 = parts[0].trim();
                    break;
                }
            }
        }
        if (info.apkUrl.isEmpty()) throw new java.io.IOException("Update manifest has no APK URL");
        return info;
    }

    static File download(Context context, Info info, Network.Progress progress) throws Exception {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("No cache dir");
        File part = new File(dir, "update.apk.part");
        File target = new File(dir, "update.apk");
        Network.download(info.apkUrl, part, progress);
        try {
            if (target.exists() && !target.delete())
                throw new java.io.IOException("Cannot replace staged update");
            if (!part.renameTo(target)) throw new java.io.IOException("Cannot stage update");
            if (!info.apkSha256.isEmpty()) {
                String actual = PayloadStore.hash(target);
                if (!actual.equalsIgnoreCase(info.apkSha256)) {
                    target.delete();
                    throw new java.io.IOException("Update SHA-256 mismatch");
                }
            }
            if (!target.setReadable(true, false))
                throw new java.io.IOException("Cannot expose update file");
            return target;
        } finally {
            part.delete();
        }
    }

    static void install(Activity activity, File apk) {
        Uri uri = ApkProvider.uriFor(activity, apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
    }

    static void openReleases(Activity activity) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)));
        } catch (Exception ignored) {
        }
    }

    static void promptInstall(final Activity activity, final Info info, final File apk) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                new AlertDialog.Builder(activity)
                        .setTitle("Install app update?")
                        .setMessage("S22 Updater " + info.versionName + " is downloaded and verified.\n\n"
                                + "Android will ask you to confirm the install.")
                        .setPositiveButton("Install",
                                new android.content.DialogInterface.OnClickListener() {
                                    @Override public void onClick(android.content.DialogInterface d, int w) {
                                        try {
                                            install(activity, apk);
                                        } catch (Exception e) {
                                            openReleases(activity);
                                        }
                                    }
                                })
                        .setNegativeButton("Later", null)
                        .show();
            }
        });
    }
}
