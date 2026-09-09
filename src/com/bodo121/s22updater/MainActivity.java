package com.bodo121.s22updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.JsonToken;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * S22 Updater v1 — payload version manager for IONSTACK-S22.
 *
 * - Fetches the targets feed (default: our own support/targets-v3.json on
 *   GitHub; any Root-My-Galaxy schema-v3 feed URL can be pasted instead,
 *   e.g. the BuSung-dev/Root-My-Galaxy feed).
 * - Shows the installed payload version as the sha256 of our private copy
 *   (filesDir/cve-2026-43499-app.so). The app never touches
 *   /data/local/tmp directly — that path is shell-only on stock Android.
 * - Downloads the selected payload artifact, compares sha256 with the
 *   installed copy, and if different prompts to install: the new file is
 *   staged and the app asks to restart to install it (on next launch the
 *   staged file becomes current).
 * - Shows recent changelogs (commit messages) from the source repos.
 *
 * v1 does NOT run the exploit itself — deploy/run stays in support/deploy.sh
 * and the XDA tutorial. No root required for this app.
 */
public class MainActivity extends Activity {

    private static final String DEFAULT_FEED_URL =
            "https://raw.githubusercontent.com/Bodo121/IONSTACK-S22/main/support/targets-v3.json";
    private static final String CHANGELOG_REPOS[] = {
            "Bodo121/IONSTACK-S22",
            "Bodo121/KSU-S22",
    };
    private static final int MAX_DOWNLOAD_BYTES = 32 * 1024 * 1024;

    private static final String PREFS = "s22updater";
    private static final String KEY_FEED_URL = "feed_url";
    private static final String KEY_SELECTED = "selected_payload";

    private static final String PAYLOAD_FILE = "cve-2026-43499-app.so";
    private static final String PENDING_FILE = "cve-2026-43499-app.so.pending";
    private static final String DOWNLOAD_TMP = "cve-2026-43499-app.so.download";

    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView statusView;
    private TextView installedView;
    private TextView remoteView;
    private TextView changelogView;
    private EditText feedEdit;
    private LinearLayout payloadBox;
    private Button checkButton;
    private Button installButton;

    private final List<Payload> payloads = new ArrayList<Payload>();
    private int selected = -1;
    private String pendingSha = null;

    private static final class Payload {
        String id = "";
        String name = "";
        String url = "";
        long size = 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Adopt a staged update from a previous run: restart installs it.
        File pending = new File(getFilesDir(), PENDING_FILE);
        final boolean justUpdated;
        if (pending.exists()) {
            justUpdated = pending.renameTo(new File(getFilesDir(), PAYLOAD_FILE));
        } else {
            justUpdated = false;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(title("S22 Payload Updater"));
        statusView = line(root, "Feed: not loaded");
        root.addView(label("Feed URL (schema-v3 targets JSON):"));
        feedEdit = new EditText(this);
        feedEdit.setText(prefs().getString(KEY_FEED_URL, DEFAULT_FEED_URL));
        feedEdit.setSingleLine(true);
        feedEdit.setTextSize(12);
        root.addView(feedEdit);

        root.addView(label("Payload:"));
        payloadBox = new LinearLayout(this);
        payloadBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(payloadBox);

        installedView = line(root, "Installed: checking...");
        remoteView = line(root, "Remote: unknown");

        checkButton = new Button(this);
        checkButton.setText("Check for updates");
        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onCheck(); }
        });
        root.addView(checkButton);

        installButton = new Button(this);
        installButton.setText("Download & install update");
        installButton.setEnabled(false);
        installButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onInstall(); }
        });
        root.addView(installButton);

        root.addView(label("Changelog (latest commits):"));
        changelogView = new TextView(this);
        changelogView.setText("Press \"Check for updates\" to load.");
        changelogView.setTextSize(12);
        changelogView.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(changelogView);

        root.addView(label("Note: this app manages payload files only. " +
                "Running the exploit stays a deploy.sh / adb job. No root needed here."));

        setContentView(scroll);
        refreshInstalled(justUpdated);
    }

    // ---------- UI helpers ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    private TextView line(LinearLayout root, String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setPadding(0, dp(2), 0, dp(2));
        root.addView(t);
        return t;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void setStatus(final String s) {
        ui.post(new Runnable() {
            @Override public void run() { statusView.setText("Feed: " + s); }
        });
    }

    // ---------- installed version (sha of our private copy) ----------

    private static String sha256File(File f) {
        if (!f.exists()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            InputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            } finally {
                in.close();
            }
            return hex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            sb.append(Character.forDigit((b[i] >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b[i] & 0xf, 16));
        }
        return sb.toString();
    }

    private static String shortSha(String sha) {
        return sha == null ? "none" : sha.substring(0, 12);
    }

    private void refreshInstalled(final boolean justUpdated) {
        new Thread(new Runnable() {
            @Override public void run() {
                final String sha = sha256File(new File(getFilesDir(), PAYLOAD_FILE));
                ui.post(new Runnable() {
                    @Override public void run() {
                        installedView.setText("Installed: " + shortSha(sha) +
                                (justUpdated ? "  (update installed after restart)" : ""));
                    }
                });
            }
        }).start();
    }

    // ---------- feed + check ----------

    private void onCheck() {
        final String url = feedEdit.getText().toString().trim();
        if (url.isEmpty()) {
            setStatus("empty URL");
            return;
        }
        prefs().edit().putString(KEY_FEED_URL, url).apply();
        setStatus("loading...");
        checkButton.setEnabled(false);
        installButton.setEnabled(false);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String json = httpGet(url, 256 * 1024);
                    final List<Payload> list = parseFeed(json);
                    final String changelog = loadChangelogs();
                    ui.post(new Runnable() {
                        @Override public void run() {
                            onFeedLoaded(list);
                            changelogView.setText(changelog);
                        }
                    });
                    setStatus("ok (" + list.size() + " payloads)");
                } catch (final Exception e) {
                    setStatus("failed: " + e.getMessage());
                    ui.post(new Runnable() {
                        @Override public void run() { checkButton.setEnabled(true); }
                    });
                }
            }
        }).start();
    }

    private void onFeedLoaded(List<Payload> list) {
        payloads.clear();
        payloads.addAll(list);
        payloadBox.removeAllViews();
        String saved = prefs().getString(KEY_SELECTED, "r0s-S901BXXSNGZD7");
        selected = -1;
        for (int i = 0; i < payloads.size(); i++) {
            final int idx = i;
            final Payload p = payloads.get(i);
            Button b = new Button(this);
            b.setText(p.id + " — " + p.name);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectPayload(idx); }
            });
            payloadBox.addView(b);
            if (p.id.equals(saved)) {
                selected = i;
            }
        }
        if (selected < 0 && !payloads.isEmpty()) {
            selected = 0;
        }
        if (selected >= 0) {
            selectPayload(selected);
        }
        checkButton.setEnabled(true);
    }

    private void selectPayload(int idx) {
        selected = idx;
        Payload p = payloads.get(idx);
        prefs().edit().putString(KEY_SELECTED, p.id).apply();
        remoteView.setText("Remote: " + p.id + " (" + p.size + " bytes)\n" + p.url);
        compareWithInstalled(p);
    }

    private void compareWithInstalled(final Payload p) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File dl = new File(getFilesDir(), DOWNLOAD_TMP);
                    httpDownload(p.url, dl);
                    final String remoteSha = sha256File(dl);
                    final String localSha = sha256File(new File(getFilesDir(), PAYLOAD_FILE));
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (remoteSha != null && remoteSha.equals(localSha)) {
                                remoteView.append("\nUp to date (" + shortSha(remoteSha) + ")");
                                installButton.setEnabled(false);
                            } else {
                                remoteView.append("\nUPDATE AVAILABLE\n remote=" +
                                        shortSha(remoteSha) + "\n local =" +
                                        shortSha(localSha));
                                pendingSha = remoteSha;
                                installButton.setEnabled(true);
                            }
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            remoteView.append("\ndownload failed: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    // ---------- install = stage + prompt restart ----------

    private void onInstall() {
        File dl = new File(getFilesDir(), DOWNLOAD_TMP);
        File staged = new File(getFilesDir(), PENDING_FILE);
        if (!dl.exists() || !dl.renameTo(staged)) {
            remoteView.append("\nstaging failed");
            return;
        }
        pendingSha = sha256File(staged);
        installButton.setEnabled(false);
        new AlertDialog.Builder(this)
                .setTitle("Update staged")
                .setMessage("New payload " + shortSha(pendingSha) +
                        " downloaded.\n\nRestart the app to install the new file.")
                .setPositiveButton("Restart now", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        finish();
                        startActivity(getIntent());
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    // ---------- changelog: recent commits of the source repos ----------

    private String loadChangelogs() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CHANGELOG_REPOS.length; i++) {
            String repo = CHANGELOG_REPOS[i];
            sb.append("== ").append(repo).append(" ==\n");
            try {
                String json = httpGet(
                        "https://api.github.com/repos/" + repo + "/commits?per_page=8",
                        128 * 1024);
                sb.append(formatCommits(json));
            } catch (Exception e) {
                sb.append("(unavailable: ").append(e.getMessage()).append(")\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatCommits(String json) throws Exception {
        // Minimal reader: array of {commit:{message, committer:{date}}} (+sha).
        StringBuilder sb = new StringBuilder();
        JsonReader r = new JsonReader(
                new InputStreamReader(
                        new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8));
        r.beginArray();
        int n = 0;
        while (r.hasNext() && n < 8) {
            String sha = "", msg = "", date = "";
            r.beginObject();
            while (r.hasNext()) {
                String name = r.nextName();
                if (name.equals("sha") && r.peek() == JsonToken.STRING) {
                    sha = r.nextString();
                } else if (name.equals("commit")) {
                    r.beginObject();
                    while (r.hasNext()) {
                        String cn = r.nextName();
                        if (cn.equals("message") && r.peek() == JsonToken.STRING) {
                            msg = r.nextString();
                        } else if (cn.equals("committer")) {
                            r.beginObject();
                            while (r.hasNext()) {
                                String dn = r.nextName();
                                if (dn.equals("date") && r.peek() == JsonToken.STRING) {
                                    date = r.nextString();
                                } else {
                                    r.skipValue();
                                }
                            }
                            r.endObject();
                        } else {
                            r.skipValue();
                        }
                    }
                    r.endObject();
                } else {
                    r.skipValue();
                }
            }
            r.endObject();
            int nl = msg.indexOf('\n');
            String first = nl < 0 ? msg : msg.substring(0, nl);
            if (first.length() > 100) {
                first = first.substring(0, 100);
            }
            String shortDate = date.length() >= 10 ? date.substring(0, 10) : date;
            String shortId = sha.length() >= 7 ? sha.substring(0, 7) : sha;
            sb.append(shortDate).append(" ").append(shortId).append(" ").append(first).append("\n");
            n++;
        }
        r.endArray();
        r.close();
        return sb.toString();
    }

    // ---------- http helpers ----------

    private static String httpGet(String urlStr, int maxBytes) throws Exception {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "S22-Updater/1.0");
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }
            InputStream in = c.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int total = 0, n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > maxBytes) {
                        throw new Exception("response too large");
                    }
                    out.write(buf, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static void httpDownload(String urlStr, File dest) throws Exception {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "S22-Updater/1.0");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[65536];
                int total = 0, n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw new Exception("file too large");
                    }
                    out.write(buf, 0, n);
                }
            } finally {
                in.close();
                out.close();
            }
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    // ---------- feed parse (schema-v3, tolerant) ----------

    private static List<Payload> parseFeed(String json) throws Exception {
        List<Payload> out = new ArrayList<Payload>();
        JsonReader r = new JsonReader(
                new InputStreamReader(
                        new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8));
        r.beginObject();
        while (r.hasNext()) {
            String name = r.nextName();
            if (name.equals("payloads")) {
                r.beginArray();
                while (r.hasNext()) {
                    Payload p = new Payload();
                    r.beginObject();
                    while (r.hasNext()) {
                        String fn = r.nextName();
                        if (fn.equals("payloadId") && r.peek() == JsonToken.STRING) {
                            p.id = r.nextString();
                        } else if (fn.equals("displayName") && r.peek() == JsonToken.STRING) {
                            p.name = r.nextString();
                        } else if (fn.equals("exploit")) {
                            r.beginObject();
                            while (r.hasNext()) {
                                String en = r.nextName();
                                if (en.equals("url") && r.peek() == JsonToken.STRING) {
                                    p.url = r.nextString();
                                } else if (en.equals("size") && r.peek() == JsonToken.NUMBER) {
                                    try {
                                        p.size = r.nextLong();
                                    } catch (Exception e) {
                                        r.skipValue();
                                    }
                                } else {
                                    r.skipValue();
                                }
                            }
                            r.endObject();
                        } else {
                            r.skipValue();
                        }
                    }
                    r.endObject();
                    if (!p.id.isEmpty() && !p.url.isEmpty()) {
                        out.add(p);
                    }
                }
                r.endArray();
            } else {
                r.skipValue();
            }
        }
        r.endObject();
        r.close();
        if (out.isEmpty()) {
            throw new Exception("no payloads in feed");
        }
        return out;
    }
}
