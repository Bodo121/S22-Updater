package com.bodo121.s22updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private static final String DEFAULT_FEED_URL =
            "https://raw.githubusercontent.com/Bodo121/IONSTACK-S22/main/support/targets-v3.json";
    private static final String CHANGELOG_REPOS[] = {
            "Bodo121/IONSTACK-S22",
            "Bodo121/KSU-S22",
    };
    private static final int MAX_DOWNLOAD_BYTES = 32 * 1024 * 1024;
    private static final String ROOT_PACKAGE = "me.tongfei.kerneldebug";
    private static final String EXPLOIT_DEST = "/data/local/tmp/cve-2026-43499";
    private static final String KSU_MODULE_NAME = "ksud-r0s-S901BXXSNGZD7-kdp.ko";
    private static final String KSU_MODULE_DEST = "/data/kernelsu/modules/" + KSU_MODULE_NAME;
    private static final String KSU_MODULE_FALLBACK = "/data/adb/kernelsu/modules/" + KSU_MODULE_NAME;

    private static final String PREFS = "s22updater";
    private static final String KEY_FEED_URL = "feed_url";
    private static final String KEY_SELECTED = "selected_payload";

    private static final String PAYLOAD_FILE = "cve-2026-43499-app.so";
    private static final String PENDING_FILE = "cve-2026-43499-app.so.pending";
    private static final String DOWNLOAD_TMP = "cve-2026-43499-app.so.download";
    private static final String KSU_FILE = "ksud-r0s-S901BXXSNGZD7-kdp.ko";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Payload> payloads = new ArrayList<Payload>();

    private TextView statusView;
    private TextView installedView;
    private TextView remoteView;
    private TextView rootView;
    private TextView changelogView;
    private EditText feedEdit;
    private LinearLayout payloadBox;
    private Button checkButton;
    private Button installButton;
    private Button runButton;
    private ProgressBar progress;
    private int selected = -1;
    private String pendingSha = null;

    private static final class Payload {
        String id = "";
        String name = "";
        String url = "";
        String kernelsuUrl = "";
        long size = 0;
        long kernelsuSize = 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File pending = new File(getFilesDir(), PENDING_FILE);
        final boolean justUpdated = pending.exists() && pending.renameTo(new File(getFilesDir(), PAYLOAD_FILE));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(titleCard("IONSTACK-S22", "Payload updater + KernelSU"));
        root.addView(statusCard());
        root.addView(label("Feed URL (schema-v3 targets JSON)"));
        feedEdit = new EditText(this);
        feedEdit.setText(prefs().getString(KEY_FEED_URL, DEFAULT_FEED_URL));
        feedEdit.setSingleLine(true);
        feedEdit.setTextSize(12);
        feedEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        feedEdit.setBackground(textBackground());
        root.addView(feedEdit);

        root.addView(sectionCard("Payload"));
        payloadBox = new LinearLayout(this);
        payloadBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(payloadBox);

        root.addView(statusRow("Installed", "checking..."));
        root.addView(statusRow("Remote", "unknown"));
        root.addView(statusRow("Root", "not checked"));
        root.addView(statusRow("KernelSU Manager", "not checked"));

        root.addView(actionCard());
        root.addView(sectionCard("Changelog"));
        changelogView = new TextView(this);
        changelogView.setText("Checking the latest commits...");
        changelogView.setTextSize(12);
        changelogView.setTypeface(android.graphics.Typeface.MONOSPACE);
        changelogView.setPadding(0, dp(2), 0, dp(4));
        root.addView(changelogView);
        root.addView(noteCard("The app installs payload files and KernelSU modules from the feed. " +
                "Running the exploit is an explicit root action. No root is required for feed checks."));

        setContentView(scroll);
        refreshInstalled(justUpdated);
        checkRootInBackground();
        ui.postDelayed(new Runnable() {
            @Override public void run() { onCheck(); }
        }, 250);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout titleCard(String title, String subtitle) {
        LinearLayout card = card(Color.parseColor("#16213e"), 18);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(25);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, dp(4), 0, dp(4));
        card.addView(t);
        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextSize(13);
        s.setTextColor(Color.parseColor("#b8c7ff"));
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(s);
        return card;
    }

    private LinearLayout card(int color, int radius) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(radius);
        bg.setColor(color);
        bg.setStroke(dp(1), Color.parseColor("#d9e2f1"));
        c.setBackground(bg);
        return c;
    }

    private LinearLayout statusCard() {
        LinearLayout c = card(Color.parseColor("#eef4ff"), 16);
        TextView h = new TextView(this);
        h.setText("STATUS");
        h.setTextSize(12);
        h.setTextColor(Color.parseColor("#52617a"));
        h.setPadding(0, 0, 0, dp(8));
        c.addView(h);
        statusView = new TextView(this);
        statusView.setText("Loading...");
        statusView.setTextSize(14);
        statusView.setTextColor(Color.parseColor("#172033"));
        c.addView(statusView);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0, false);
        c.addView(progress);
        return c;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setPadding(0, dp(10), 0, dp(2));
        t.setTextColor(Color.parseColor("#52617a"));
        return t;
    }

    private LinearLayout sectionCard(String s) {
        LinearLayout c = card(Color.WHITE, 16);
        TextView t = new TextView(this);
        t.setText(s.toUpperCase());
        t.setTextSize(12);
        t.setTextColor(Color.parseColor("#2457d6"));
        t.setPadding(0, 0, 0, dp(8));
        c.addView(t);
        return c;
    }

    private LinearLayout statusRow(String key, String value) {
        LinearLayout c = card(Color.WHITE, 14);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView k = new TextView(this);
        k.setText(key);
        k.setTextSize(13);
        k.setGravity(Gravity.CENTER_VERTICAL);
        k.setPadding(0, dp(7), dp(10), dp(7));
        k.setTextColor(Color.parseColor("#52617a"));
        row.addView(k, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(4), dp(7), 0, dp(7));
        v.setTextColor(Color.parseColor("#172033"));
        row.addView(v);
        c.addView(row);
        return c;
    }

    private LinearLayout actionCard() {
        LinearLayout c = card(Color.parseColor("#16213e"), 16);
        checkButton = button("Check for updates", Color.parseColor("#2457d6"));
        c.addView(checkButton);
        installButton = button("Install from app", Color.parseColor("#16a34a"));
        installButton.setEnabled(false);
        c.addView(installButton);
        runButton = button("Run exploit (root)", Color.parseColor("#dc2626"));
        runButton.setEnabled(false);
        c.addView(runButton);
        return c;
    }

    private Button button(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(true);
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        bg.setColor(color);
        b.setBackground(bg);
        b.setTextColor(Color.WHITE);
        return b;
    }

    private LinearLayout noteCard(String s) {
        LinearLayout c = card(Color.parseColor("#fff7ed"), 14);
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(Color.parseColor("#9a3412"));
        t.setPadding(dp(2), dp(2), dp(2), dp(2));
        c.addView(t);
        return c;
    }

    private GradientDrawable textBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        bg.setColor(Color.parseColor("#f4f7fb"));
        bg.setStroke(dp(1), Color.parseColor("#d9e2f1"));
        return bg;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void setStatus(final String s) {
        ui.post(new Runnable() {
            @Override public void run() {
                statusView.setText(s);
                updateProgress(s.contains("failed") || s.contains("error") ? 1000 : 350);
            }
        });
    }

    private void updateProgress(final int value) {
        ui.post(new Runnable() {
            @Override public void run() {
                progress.setProgress(value);
            }
        });
    }

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
                        installedView.setText(shortSha(sha) + (justUpdated ? "  updated" : ""));
                    }
                });
            }
        }).start();
    }

    private void onCheck() {
        final String url = feedEdit.getText().toString().trim();
        if (url.isEmpty()) {
            setStatus("Feed URL is empty");
            return;
        }
        prefs().edit().putString(KEY_FEED_URL, url).apply();
        setStatus("Loading feed...");
        checkButton.setEnabled(false);
        installButton.setEnabled(false);
        runButton.setEnabled(false);
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
                    setStatus("Feed ready (" + list.size() + " payload" + (list.size() == 1 ? "" : "s") + ")");
                } catch (final Exception e) {
                    setStatus("Feed failed: " + e.getMessage());
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
            b.setText(p.id + "  •  " + p.name);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setTextSize(13);
            b.setTextColor(Color.parseColor("#172033"));
            b.setBackground(new GradientDrawable() {
                { setCornerRadius(12);
                  setColor(Color.WHITE);
                  setStroke(dp(1), Color.parseColor("#d9e2f1")); }
            });
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
        remoteView.setText(p.id + "  •  " + p.name);
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
                            remoteView.append("\n" + remoteSha + "  •  " + p.size + " B");
                            if (remoteSha != null && remoteSha.equals(localSha)) {
                                setStatus("Payload is up to date");
                                installButton.setEnabled(false);
                                runButton.setEnabled(false);
                            } else {
                                setStatus("Update available");
                                pendingSha = remoteSha;
                                installButton.setEnabled(true);
                                runButton.setEnabled(false);
                            }
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            setStatus("Download failed: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void onInstall() {
        if (selected < 0 || payloads.isEmpty()) {
            return;
        }
        final Payload p = payloads.get(selected);
        final File dl = new File(getFilesDir(), DOWNLOAD_TMP);
        try {
            if (!dl.exists()) {
                httpDownload(p.url, dl);
            }
        } catch (Exception e) {
            setStatus("Download failed: " + e.getMessage());
            return;
        }
        if (!isRootAvailable()) {
            File staged = new File(getFilesDir(), PENDING_FILE);
            if (!dl.renameTo(staged)) {
                setStatus("Could not stage update");
                return;
            }
            pendingSha = sha256File(staged);
            new AlertDialog.Builder(this)
                    .setTitle("Root required")
                    .setMessage("The update is staged in the app. Automatic install needs root.\n\nUse support/deploy.sh from adb, or install KernelSU first.")
                    .setPositiveButton("OK", null)
                    .show();
            installButton.setEnabled(false);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Install from app")
                .setMessage("Install payload " + shortSha(pendingSha) + " and KernelSU module from the feed?\n\nThis writes to /data/local/tmp and /data/kernelsu/modules.")
                .setPositiveButton("Install", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { installFromRoot(p, dl); }
                })
                .setNegativeButton("Download only", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        File staged = new File(getFilesDir(), PENDING_FILE);
                        if (dl.renameTo(staged)) {
                            pendingSha = sha256File(staged);
                            setStatus("Update staged for root install");
                        }
                    }
                })
                .show();
    }

    private void installFromRoot(final Payload p, final File downloaded) {
        installButton.setEnabled(false);
        runButton.setEnabled(false);
        setStatus("Installing payload...");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    installPayload(downloaded);
                    setStatus("Installing KernelSU module...");
                    if (p.kernelsuUrl != null && !p.kernelsuUrl.isEmpty()) {
                        File ksu = new File(getFilesDir(), KSU_FILE);
                        httpDownload(p.kernelsuUrl, ksu);
                        installKernelsuModule(ksu);
                    } else {
                        setStatus("Payload installed; feed has no KernelSU module");
                    }
                    copyFile(downloaded, new File(getFilesDir(), PAYLOAD_FILE));
                    refreshInstalled(false);
                    ui.post(new Runnable() {
                        @Override public void run() {
                            setStatus("Payload + KernelSU installed");
                            installButton.setEnabled(false);
                            runButton.setEnabled(true);
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            setStatus("Install failed: " + e.getMessage());
                            installButton.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void runExploit() {
        if (!isRootAvailable()) {
            setStatus("Root is required to run the exploit");
            Toast.makeText(this, "Root is required", Toast.LENGTH_SHORT).show();
            return;
        }
        runButton.setEnabled(false);
        setStatus("Running exploit...");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    runRootCommand("test -x " + shellQuote(EXPLOIT_DEST));
                    setStatus("Exploit running...");
                    runRootCommand("EXPLOIT_ATTEMPTS=24 LD_PRELOAD=" + shellQuote(EXPLOIT_DEST) + " sh");
                    setStatus("Exploit finished; checking root...");
                    if (isRootAvailable()) {
                        installKernelsuModuleIfRoot();
                    }
                    ui.post(new Runnable() {
                        @Override public void run() {
                            setStatus("Exploit finished");
                            runButton.setEnabled(true);
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            setStatus("Exploit failed: " + e.getMessage());
                            runButton.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private boolean isRootAvailable() {
        try {
            String output = runCommand(new String[] {"su", "-c", "id"}, 15000);
            return output.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private void checkRootInBackground() {
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean root = isRootAvailable();
                final boolean manager = isKernelSUManagerInstalled();
                ui.post(new Runnable() {
                    @Override public void run() {
                        rootView.setText(root ? "available" : "not available");
                        rootView.setTextColor(root ? Color.parseColor("#15803d") : Color.parseColor("#b91c1c"));
                        rootView.setText(rootView.getText() + "  •  " + (manager ? "KernelSU installed" : "KernelSU manager not found"));
                    }
                });
            }
        }).start();
    }

    private boolean isKernelSUManagerInstalled() {
        try {
            getPackageManager().getPackageInfo(ROOT_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void installKernelsuModuleIfRoot() {
        if (!isRootAvailable()) {
            setStatus("Root disappeared before KernelSU install");
            return;
        }
        try {
            if (isKernelsuModuleInstalled()) {
                setStatus("KernelSU module already installed");
                return;
            }
            File ksu = new File(getFilesDir(), KSU_FILE);
            Payload p = selected >= 0 && selected < payloads.size() ? payloads.get(selected) : null;
            if (p == null || p.kernelsuUrl == null || p.kernelsuUrl.isEmpty()) {
                setStatus("No KernelSU module URL in the feed");
                return;
            }
            httpDownload(p.kernelsuUrl, ksu);
            installKernelsuModule(ksu);
            setStatus("KernelSU module installed");
        } catch (Exception e) {
            setStatus("KernelSU install failed: " + e.getMessage());
        }
    }

    private boolean isKernelsuModuleInstalled() {
        try {
            runRootCommand("test -f " + shellQuote(KSU_MODULE_DEST) + " || test -f " + shellQuote(KSU_MODULE_FALLBACK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void installPayload(File downloaded) throws Exception {
        runRootCommand("mkdir -p /data/local/tmp && cp " + shellQuote(downloaded.getAbsolutePath()) +
                " " + shellQuote(EXPLOIT_DEST) + " && chmod 644 " + shellQuote(EXPLOIT_DEST));
    }

    private void installKernelsuModule(File downloaded) throws Exception {
        runRootCommand("mkdir -p /data/kernelsu/modules /data/adb/kernelsu/modules && cp " +
                shellQuote(downloaded.getAbsolutePath()) + " " + shellQuote(KSU_MODULE_DEST) +
                " && cp " + shellQuote(downloaded.getAbsolutePath()) + " " + shellQuote(KSU_MODULE_FALLBACK) +
                " && chmod 644 " + shellQuote(KSU_MODULE_DEST) + " " + shellQuote(KSU_MODULE_FALLBACK));
        try {
            runRootCommand("if [ -x /data/adb/ksud ]; then /data/adb/ksud reload; elif [ -x /data/kernelsu/ksud ]; then /data/kernelsu/ksud reload; fi");
        } catch (Exception ignored) {
        }
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private static String runCommand(String[] command, long timeoutMs) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        final StringBuilder output = new StringBuilder();
        final AtomicBoolean finished = new AtomicBoolean(false);
        Thread reader = new Thread(new Runnable() {
            @Override public void run() {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                } finally {
                    finished.set(true);
                }
            }
        });
        reader.start();
        boolean alive = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!alive) {
            p.destroyForcibly();
            reader.join(1000);
            throw new Exception("command timed out");
        }
        reader.join();
        int exit = p.exitValue();
        if (exit != 0) {
            throw new Exception("exit " + exit + ": " + output.toString().trim());
        }
        return output.toString();
    }

    private static void runRootCommand(String command) throws Exception {
        runCommand(new String[] {"su", "-c", command}, 60000);
    }

    private static void copyFile(File from, File to) throws IOException {
        InputStream in = new FileInputStream(from);
        OutputStream out = new FileOutputStream(to);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
            out.close();
        }
    }

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

    private static String httpGet(String urlStr, int maxBytes) throws Exception {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "S22-Updater/2.0");
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
            c.setRequestProperty("User-Agent", "S22-Updater/2.0");
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
                        } else if (fn.equals("kernelsu")) {
                            r.beginObject();
                            while (r.hasNext()) {
                                String kn = r.nextName();
                                if (kn.equals("url") && r.peek() == JsonToken.STRING) {
                                    p.kernelsuUrl = r.nextString();
                                } else if (kn.equals("size") && r.peek() == JsonToken.NUMBER) {
                                    try {
                                        p.kernelsuSize = r.nextLong();
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
