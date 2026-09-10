package com.bodo121.s22updater;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

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

    static final class SignatureReport {
        boolean compatible;
        String problem = "";
        String packageName = "";
        String installedCert = "";
        String updateCert = "";
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

    static boolean canRequestPackageInstalls(Context context) {
        return Build.VERSION.SDK_INT < 26 || context.getPackageManager().canRequestPackageInstalls();
    }

    static void openInstallPermission(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        }
    }

    static SignatureReport checkInstallCompatibility(Context context, File apk) throws Exception {
        SignatureReport report = new SignatureReport();
        PackageManager pm = context.getPackageManager();
        int flags = signatureFlags();
        PackageInfo current = pm.getPackageInfo(context.getPackageName(), flags);
        PackageInfo update = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (update == null) throw new java.io.IOException("Downloaded APK could not be parsed");
        report.packageName = update.packageName == null ? "" : update.packageName;
        if (!context.getPackageName().equals(report.packageName)) {
            report.problem = "Downloaded APK package is " + report.packageName
                    + ", expected " + context.getPackageName();
            return report;
        }
        Set<String> installed = certDigests(current);
        Set<String> incoming = certDigests(update);
        report.installedCert = first(installed);
        report.updateCert = first(incoming);
        if (installed.isEmpty() || incoming.isEmpty()) {
            report.problem = "Could not read APK signing certificates";
            return report;
        }
        for (String digest : installed) {
            if (incoming.contains(digest)) {
                report.compatible = true;
                return report;
            }
        }
        report.problem = "Installed app and update APK are signed by different certificates";
        return report;
    }

    private static int signatureFlags() {
        if (Build.VERSION.SDK_INT >= 28) return PackageManager.GET_SIGNING_CERTIFICATES;
        return PackageManager.GET_SIGNATURES;
    }

    @SuppressWarnings("deprecation")
    private static Set<String> certDigests(PackageInfo info) throws Exception {
        HashSet<String> digests = new HashSet<>();
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null) return digests;
        for (Signature signature : signatures) digests.add(sha256(signature.toByteArray()));
        return digests;
    }

    private static String first(Set<String> values) {
        for (String value : values) return value;
        return "";
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format("%02x", b & 0xff));
        return out.toString();
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

}
