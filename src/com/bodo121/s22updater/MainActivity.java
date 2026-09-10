package com.bodo121.s22updater;

import android.app.Activity;
import android.app.Dialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String FEED = "https://raw.githubusercontent.com/Bodo121/IONSTACK-S22/main/support/targets-v3.json";
    private static final String[] MANAGERS = {"com.rifsxd.ksunext", "me.weishu.kernelsu", "me.tongfei.kerneldebug"};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Button> actions = new ArrayList<>();
    private final List<Payload> payloads = new ArrayList<>();
    private final LinearLayout[] pages = new LinearLayout[4];
    private final Button[] tabs = new Button[4];
    private TextView status, local, remote, rootStatus, shizukuStatus, managerStatus,
            kernelStatus, activityLog, changelog, appUpdateStatus;
    private ProgressBar progress;
    private LinearLayout choices;
    private EditText feedInput;
    private Button download, deploy, runExploit, export, loadKernel, checkAppUpdate;
    private Switch automaticKernel, automaticAppUpdate;
    private SharedPreferences preferences;
    private Payload selected;
    private File exportFile;
    private boolean busy, rootGranted, shizukuGranted;
    private volatile boolean closed;
    private int bg, surface, ink, muted, accent, border, pageIndex;

    static final String HELPER_DEVICE_PATH = "/data/local/tmp/cve-2026-43499-root";
    static final String EXPLOIT_DEVICE_PATH = "/data/local/tmp/cve-2026-43499";
    static final String EXPLOIT_LOG_PATH = "/data/local/tmp/cve-exploit.log";

    private static final class Payload {
        final String id, name, url, sha;
        final long size;
        final String helperUrl, helperSha;
        final long helperSize;
        Payload(JSONObject item) throws Exception {
            id = item.getString("payloadId");
            if (!id.matches("[A-Za-z0-9._-]{1,120}")) throw new IOException("Invalid payload ID");
            name = item.optString("displayName", id);
            JSONObject artifact = item.getJSONObject("exploit");
            url = Network.validate(artifact.getString("url")).toString();
            size = artifact.optLong("size", 0);
            sha = artifact.optString("sha256", "").trim();
            if (size < 0 || size > 64 * 1024 * 1024) throw new IOException("Invalid artifact size");
            if (!sha.isEmpty() && !sha.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid SHA-256");
            JSONObject helper = item.optJSONObject("helper");
            if (helper != null) {
                helperUrl = Network.validate(helper.getString("url")).toString();
                helperSize = helper.optLong("size", 0);
                helperSha = helper.optString("sha256", "").trim();
                if (helperSize < 0 || helperSize > 64 * 1024 * 1024)
                    throw new IOException("Invalid helper size");
                if (!helperSha.isEmpty() && !helperSha.matches("[a-fA-F0-9]{64}"))
                    throw new IOException("Invalid helper SHA-256");
            } else {
                helperUrl = "";
                helperSize = 0;
                helperSha = "";
            }
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("s22updater", MODE_PRIVATE);
        if (state != null && state.containsKey("export_file"))
            exportFile = new File(getFilesDir(), state.getString("export_file"));
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        bg = Color.parseColor(dark ? "#0D1420" : "#F3F6FB");
        surface = Color.parseColor(dark ? "#182333" : "#FFFFFF");
        ink = Color.parseColor(dark ? "#EDF3FF" : "#15243B");
        muted = Color.parseColor(dark ? "#AABBD1" : "#566980");
        accent = Color.parseColor(dark ? "#99BBFF" : "#2459CE");
        border = Color.parseColor(dark ? "#2B3C52" : "#DFE7F1");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(surface);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout shell = column();
        shell.setBackgroundColor(bg);
        LinearLayout header = column();
        header.setPadding(dp(22), dp(18), dp(22), dp(12));
        text(header, "S22 / CONTROL CENTER", 12, accent, true);
        text(header, "Your device. Your updates.", 25, ink, true);
        text(header, "IONSTACK • Version 4.5", 12, muted, false);
        shell.addView(header);
        FrameLayout content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i = 0; i < pages.length; i++) {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            pages[i] = column();
            pages[i].setPadding(dp(18), dp(8), dp(18), dp(24));
            scroll.addView(pages[i]);
            content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        }
        buildHome();
        buildUpdates();
        buildActivity();
        buildSettings();
        LinearLayout navigation = new LinearLayout(this);
        navigation.setBackgroundColor(surface);
        navigation.setPadding(dp(6), dp(8), dp(6), dp(8));
        String[] names = {"Home", "Updates", "Activity", "Settings"};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i] = makeButton(names[i], false);
            tabs[i].setTextSize(12);
            tabs[i].setPadding(0, dp(8), 0, dp(8));
            tabs[i].setOnClickListener(v -> showPage(index));
            navigation.addView(tabs[i], new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        shell.addView(navigation);
        setContentView(shell);
        showPage(state == null ? 0 : state.getInt("page", 0));
        updateManager();
        refreshControls();
        watchShizukuBinder();
        log("Ready. Feed checks and root requests run when you tap their buttons.");
        if (preferences.getBoolean("auto_app_update", false)) checkAppUpdate();
    }

    @Override protected void onResume() {
        super.onResume();
        pollShizuku();
    }

    /** Notices the moment Shizuku delivers (or loses) its binder, even across restarts. */
    private void watchShizukuBinder() {
        Shell.requestShizukuBinder(this);
        try {
            rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(
                    new rikka.shizuku.Shizuku.OnBinderReceivedListener() {
                        @Override public void onBinderReceived() {
                            post(() -> {
                                log("Shizuku binder received");
                                pollShizuku();
                                if (preferences.getBoolean("shizuku_wanted", false)
                                        && !shizukuGranted) {
                                    checkShizuku();
                                }
                            });
                        }
                    });
            rikka.shizuku.Shizuku.addBinderDeadListener(
                    new rikka.shizuku.Shizuku.OnBinderDeadListener() {
                        @Override public void onBinderDead() {
                            post(() -> {
                                shizukuGranted = false;
                                shizukuStatus.setText("Shizuku shell: binder died");
                                log("Shizuku binder died — restart Shizuku if needed");
                            });
                        }
                    });
        } catch (Throwable ignored) {
        }
        pollShizuku();
    }

    /**
     * Tests every step of the Shizuku handshake and reports exactly which one
     * fails, so a "not detected" report becomes actionable.
     */
    private void diagnoseShizuku() {
        job("Diagnosing Shizuku handshake…", () -> {
            StringBuilder report = new StringBuilder();
            String authority = getPackageName() + ".shizuku";
            boolean managerInstalled = isPackageInstalled("moe.shizuku.manager");
            report.append("0. Shizuku Manager installed: ")
                    .append(managerInstalled ? "yes" : "NO").append('\n');
            try {
                android.content.pm.ProviderInfo info = getPackageManager()
                        .resolveContentProvider(authority, 0);
                report.append("1. provider registered: ")
                        .append(info == null ? "NO" : "yes (" + info.name + ")").append('\n');
            } catch (Exception e) {
                report.append("1. provider registered: NO (").append(e.getMessage()).append(")\n");
            }
            try {
                android.net.Uri uri = android.net.Uri.parse("content://" + authority);
                android.os.Bundle reply = getContentResolver().call(uri, "getBinder", null,
                        new android.os.Bundle());
                report.append("2. direct provider call: ")
                        .append(reply == null ? "null reply (no binder held)" : "binder held")
                        .append('\n');
            } catch (Exception e) {
                report.append("2. direct provider call: FAILED (").append(e.getMessage()).append(")\n");
            }
            boolean ping = Shell.awaitShizukuRunning(this, 4000);
            report.append("3. binder alive (pingBinder): ").append(ping ? "yes" : "NO").append('\n');
            if (ping) {
                try {
                    moe.shizuku.server.IShizukuService service = Shell.shizukuService();
                    int version = service.getVersion();
                    report.append("4. server version query: OK (v").append(version).append(")\n");
                } catch (Throwable t) {
                    report.append("4. server version query: FAILED (")
                            .append(t.getMessage()).append(")\n");
                }
                boolean granted = Shell.shizukuGranted();
                report.append("5. authorized: ").append(granted ? "yes" : "NO").append('\n');
                try {
                    moe.shizuku.server.IShizukuService service = Shell.shizukuService();
                    int serverVersion = service.getVersion();
                    report.append("6. server API version: ").append(serverVersion).append('\n');
                } catch (Throwable t) {
                    report.append("6. server API version: FAILED (")
                            .append(t.getMessage()).append(")\n");
                }
                try {
                    moe.shizuku.server.IShizukuService service = Shell.shizukuService();
                    moe.shizuku.server.IRemoteProcess probe = service.newProcess(
                            new String[]{"/system/bin/sh", "-c", "true"}, null, null);
                    if (probe == null) {
                        report.append("7. newProcess probe: server returned null — this "
                                + "Shizuku version does not support shell processes\n");
                    } else {
                        boolean streams = probe.getInputStream() != null
                                && probe.getOutputStream() != null
                                && probe.getErrorStream() != null;
                        try {
                            probe.destroy();
                        } catch (Throwable ignored) {
                        }
                        report.append("7. newProcess probe: process created, streams "
                                + (streams ? "OK" : "MISSING") + "\n");
                    }
                } catch (Throwable t) {
                    report.append("7. newProcess probe: FAILED (")
                            .append(t.getClass().getSimpleName())
                            .append(t.getMessage() == null ? "" : ": " + t.getMessage())
                            .append(")\n");
                }
                if (granted) {
                    try {
                        moe.shizuku.server.IShizukuService service = Shell.shizukuService();
                        String id = new Shell.ShizukuShell(service)
                                .run("id", getCacheDir());
                        report.append("8. shell exec test: OK (").append(id).append(")\n");
                    } catch (Exception e) {
                        report.append("8. shell exec test: FAILED (")
                                .append(e.getMessage()).append(")\n");
                    }
                }
            } else {
                report.append("6-8. skipped: no binder, so Shizuku manager has not delivered "
                        + "one to this app. Start Shizuku, then reopen this app.\n");
            }
            final String text = report.toString();
            post(() -> {
                status.setText("Handshake diagnosis complete");
                log("Shizuku diagnosis:\n" + text);
                showSheet("Shizuku diagnosis", text, "Copy", () -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                            "Shizuku diagnosis", text));
                    Toast.makeText(MainActivity.this, "Copied", Toast.LENGTH_SHORT).show();
                }, "Close", null);
            });
        });
    }

    /** Passive presence check: never requests permission, safe on every resume. */
    private void pollShizuku() {
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean running = Shell.awaitShizukuRunning(MainActivity.this, 1000);
                final boolean granted = running && Shell.shizukuGranted();
                post(() -> {
                    shizukuGranted = granted;
                    if (granted) shizukuStatus.setText("Shizuku shell: authorized");
                    else if (running) shizukuStatus.setText("Shizuku shell: awaiting authorization");
                    else shizukuStatus.setText(isPackageInstalled("moe.shizuku.manager")
                            ? "Shizuku shell: service not connected"
                            : "Shizuku shell: manager not installed");
                });
            }
        }).start();
    }

    private void buildHome() {
        LinearLayout device = card(pages[0], "DEVICE");
        text(device, Build.MODEL, 23, ink, true);
        text(device, "Android " + Build.VERSION.RELEASE + " • " + Build.DISPLAY, 13, muted, false);
        LinearLayout root = card(pages[0], "ROOT ACCESS");
        rootStatus = text(root, "Not checked", 20, ink, true);
        text(root, "Check whether this app has superuser access. Approve the request in your root manager.", 14, muted, false);
        action(root, "Check root access", this::checkRoot);
        shizukuStatus = text(root, "Shizuku shell: not checked", 14, muted, false);
        action(root, "Authorize Shizuku shell", this::checkShizuku);
        action(root, "Open Shizuku app", this::openShizuku);
        action(root, "Diagnose Shizuku handshake", this::diagnoseShizuku);
        text(root, "Without root, the exploit can still run through a Shizuku shell "
                + "(install Shizuku, start it via wireless debugging, then authorize this app).", 13, muted, false);
        LinearLayout manager = card(pages[0], "KERNELSU");
        managerStatus = text(manager, "Checking manager…", 18, ink, true);
        kernelStatus = text(manager, "Kernel module: check root first", 14, muted, false);
        action(manager, "Open KernelSU Manager", this::openManager);
        loadKernel = action(manager, "Load matching KernelSU module", () -> {
            final Shell.Transport transport = pickTransport();
            if (transport == null) {
                status.setText("No privileged shell for KernelSU setup");
                log("KernelSU setup needs root or an authorized Shizuku shell.");
                return;
            }
            job("Setting up KernelSU…", () -> setupKernel(transport));
        });
        text(manager, "Late-load setup uses your SM-S901B / S901BXXSNGZD7 module, checks its SHA-256 and confirms /sys/module/kernelsu. Module state lasts for the current boot.", 13, muted, false);
    }

    private void buildUpdates() {
        LinearLayout feed = card(pages[1], "UPDATE CENTER");
        status = text(feed, "Ready to check", 21, ink, true);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminateTintList(ColorStateList.valueOf(accent));
        progress.setProgressTintList(ColorStateList.valueOf(accent));
        progress.setVisibility(View.GONE);
        feed.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        action(feed, "Check for updates", this::checkFeed);
        choices = card(pages[1], "AVAILABLE PAYLOADS");
        text(choices, "Load the feed to choose a payload.", 14, muted, false);
        LinearLayout detail = card(pages[1], "SELECTED UPDATE");
        remote = text(detail, "No payload selected", 16, ink, true);
        local = text(detail, "Local copy: not checked", 13, muted, false);
        local.setTextIsSelectable(true);
        download = action(detail, "Download update", this::download);
        deploy = action(detail, "Install downloaded file with root", this::deploy);
        runExploit = action(detail, "Run exploit", this::runExploit);
        export = action(detail, "Export downloaded file", this::export);
        text(detail, "Download saves a private copy. Root install copies it to /data/local/tmp/cve-2026-43499 "
                + "and verifies its hash. Run exploit executes the staged payload through root or a Shizuku "
                + "shell, then asks whether to install KernelSU.", 13, muted, false);
    }

    private void buildActivity() {
        LinearLayout history = card(pages[2], "SESSION ACTIVITY");
        activityLog = text(history, "", 13, ink, false);
        activityLog.setTypeface(Typeface.MONOSPACE);
        activityLog.setTextIsSelectable(true);
        action(history, "Copy diagnostics", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("S22 diagnostics", Build.MODEL + " / " + Build.DISPLAY
                    + "\n" + activityLog.getText()));
            Toast.makeText(this, "Diagnostics copied", Toast.LENGTH_SHORT).show();
        });
        LinearLayout changes = card(pages[2], "PROJECT CHANGELOG");
        action(changes, "Refresh changelog", this::loadChangelog);
        changelog = text(changes, "Recent commits appear here after refreshing.", 14, muted, false);
    }

    private void buildSettings() {
        LinearLayout settings = card(pages[3], "FEED SETTINGS");
        text(settings, "Targets feed URL", 17, ink, true);
        feedInput = new EditText(this);
        feedInput.setText(preferences.getString("feed_url", FEED));
        feedInput.setTextColor(ink);
        feedInput.setHintTextColor(muted);
        feedInput.setTextSize(14);
        feedInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        feedInput.setBackground(shape(bg, 12));
        feedInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        settings.addView(feedInput, new LinearLayout.LayoutParams(-1, -2));
        action(settings, "Save feed URL", () -> {
            try {
                String url = Network.validate(feedInput.getText().toString().trim()).toString();
                preferences.edit().putString("feed_url", url).apply();
                resetFeed();
                log("Feed URL saved. Check for updates to reload.");
                Toast.makeText(this, "Feed saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { error(e); }
        });
        action(settings, "Restore default feed", () -> {
            feedInput.setText(FEED);
            preferences.edit().putString("feed_url", FEED).apply();
            resetFeed();
            log("Default feed restored.");
        });
        LinearLayout updater = card(pages[3], "APP UPDATES");
        appUpdateStatus = text(updater, "S22 Updater " + AppUpdate.installedName(this)
                + " — app update status unknown", 14, muted, false);
        checkAppUpdate = action(updater, "Check for app updates", this::checkAppUpdate);
        automaticAppUpdate = new Switch(this);
        automaticAppUpdate.setText("Check for app updates at startup");
        automaticAppUpdate.setTextColor(ink);
        automaticAppUpdate.setPadding(0, dp(12), 0, dp(12));
        automaticAppUpdate.setChecked(preferences.getBoolean("auto_app_update", false));
        automaticAppUpdate.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean("auto_app_update", checked).apply());
        updater.addView(automaticAppUpdate, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout about = card(pages[3], "ABOUT THIS BUILD");
        automaticKernel = new Switch(this);
        automaticKernel.setText("Load KernelSU after a successful root check");
        automaticKernel.setTextColor(ink);
        automaticKernel.setPadding(0, dp(12), 0, dp(12));
        automaticKernel.setChecked(preferences.getBoolean("auto_kernel", false));
        automaticKernel.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean("auto_kernel", checked).apply());
        settings.addView(automaticKernel, new LinearLayout.LayoutParams(-1, -2));
        text(settings, "After running IONSTACK, return to Home and check root. When enabled, a successful check also loads the matching module. It skips a module already loaded.", 13, muted, false);
        text(about, "S22 Updater 4.5", 20, ink, true);
        text(about, "System light/dark theme • Android 9+\nDownloads stay local until you install or export them. Existing v2 files are preserved.", 14, muted, false);
    }

    private void resetFeed() {
        selected = null;
        payloads.clear();
        renderChoices();
        remote.setText("No payload selected");
        local.setText("Local copy: not checked");
        status.setText("Feed changed — check for updates");
        refreshControls();
    }

    private void showPage(int page) {
        pageIndex = Math.max(0, Math.min(3, page));
        for (int i = 0; i < pages.length; i++) {
            ((View) pages[i].getParent()).setVisibility(i == pageIndex ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(i == pageIndex ? accent : muted);
            tabs[i].setTypeface(null, i == pageIndex ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private interface Work { void run() throws Exception; }
    private void job(String message, Work work) {
        if (busy || closed) return;
        busy = true;
        status.setText(message);
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        log(message);
        refreshControls();
        worker.execute(() -> {
            try { work.run(); }
            catch (Exception e) { post(() -> error(e)); }
            finally { post(() -> {
                busy = false;
                progress.setVisibility(View.GONE);
                refreshControls();
            }); }
        });
    }

    private void post(Runnable runnable) {
        if (!closed) ui.post(() -> { if (!closed && !isFinishing()) runnable.run(); });
    }

    private void error(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        status.setText(message);
        log("Failed: " + message);
        showSheet("Action failed", message, "OK", null, null, null);
    }

    private void showSheet(String title, String message, String primary, final Runnable primaryAction,
                           String secondary, final Runnable secondaryAction) {
        if (closed || isFinishing()) return;
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = column();
        panel.setBackground(shape(surface, 24));
        panel.setPadding(dp(20), dp(18), dp(20), dp(16));
        TextView eyebrow = text(panel, "S22 UPDATER", 11, accent, true);
        eyebrow.setLetterSpacing(0.08f);
        text(panel, title, 21, ink, true);
        TextView body = text(panel, message, 14, muted, false);
        body.setTextIsSelectable(message.length() > 180);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(10), 0, 0);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (secondary != null && !secondary.isEmpty()) {
            Button second = makeButton(secondary, false);
            second.setOnClickListener(v -> {
                dialog.dismiss();
                if (secondaryAction != null) secondaryAction.run();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
            row.addView(second, params);
        }
        if (primary != null && !primary.isEmpty()) {
            Button first = makeButton(primary, true);
            first.setOnClickListener(v -> {
                dialog.dismiss();
                if (primaryAction != null) primaryAction.run();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
            if (row.getChildCount() > 0) params.leftMargin = dp(10);
            row.addView(first, params);
        }
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        dialog.setContentView(panel);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                        Color.TRANSPARENT));
                window.setDimAmount(0.42f);
                window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(520)),
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private void log(String value) {
        String previous = activityLog.getText().toString();
        String next = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()) + "  " + value + "\n\n" + previous;
        activityLog.setText(next.substring(0, Math.min(12000, next.length())));
    }

    private File localFile(Payload p) throws Exception { return PayloadStore.file(getFilesDir(), p.id); }

    private void refreshControls() {
        for (Button button : actions) { button.setEnabled(!busy); button.setAlpha(busy ? .5f : 1f); }
        feedInput.setEnabled(!busy);
        automaticKernel.setEnabled(!busy);
        automaticAppUpdate.setEnabled(!busy);
        boolean hasFile = false;
        try { hasFile = selected != null && localFile(selected).isFile(); } catch (Exception ignored) { }
        boolean transport = rootGranted || shizukuGranted;
        download.setEnabled(!busy && selected != null);
        deploy.setEnabled(!busy && hasFile && rootGranted);
        runExploit.setEnabled(!busy && hasFile && transport);
        export.setEnabled(!busy && hasFile);
        loadKernel.setEnabled(!busy && transport);
        for (Button b : new Button[]{download, deploy, runExploit, export, loadKernel, checkAppUpdate})
            b.setAlpha(b.isEnabled() ? 1f : .45f);
        for (int i = 0; i < choices.getChildCount(); i++) choices.getChildAt(i).setEnabled(!busy);
    }

    private void checkFeed() {
        final String url = preferences.getString("feed_url", FEED);
        job("Loading targets feed…", () -> {
            JSONObject feed = new JSONObject(Network.text(url));
            if (feed.optInt("schemaVersion", 3) != 3) throw new IOException("Only schema-v3 feeds are supported");
            JSONArray entries = feed.getJSONArray("payloads");
            List<Payload> loaded = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < entries.length(); i++) {
                Payload p = new Payload(entries.getJSONObject(i));
                if (!ids.add(p.id)) throw new IOException("Duplicate payload ID");
                loaded.add(p);
            }
            if (loaded.isEmpty()) throw new IOException("Feed contains no payloads");
            String saved = preferences.getString("selected_payload", "r0s-S901BXXSNGZD7");
            Payload preferred = loaded.get(0);
            for (Payload p : loaded) if (p.id.equals(saved)) preferred = p;
            final Payload chosen = preferred;
            final String detail = describeLocal(chosen);
            post(() -> {
                payloads.clear(); payloads.addAll(loaded); selected = chosen;
                renderChoices(); showSelection(detail);
                status.setText("Feed ready • " + loaded.size() + " payload(s)");
                log("Feed loaded. Select your firmware before downloading.");
            });
        });
    }

    private void renderChoices() {
        choices.removeAllViews();
        text(choices, "AVAILABLE PAYLOADS", 12, accent, true);
        if (payloads.isEmpty()) text(choices, "Load the feed to choose a payload.", 14, muted, false);
        for (Payload p : payloads) {
            Button b = makeButton((p == selected ? "✓  " : "") + p.name + "\n" + p.id, false);
            b.setTextSize(13);
            b.setOnClickListener(v -> job("Checking local copy…", () -> {
                String detail = describeLocal(p);
                post(() -> {
                    selected = p;
                    preferences.edit().putString("selected_payload", p.id).apply();
                    renderChoices(); showSelection(detail);
                    status.setText("Payload selected");
                });
            }));
            choices.addView(b, spaced());
        }
    }

    private String describeLocal(Payload p) throws Exception {
        File file = localFile(p);
        if (!file.isFile()) return "Local copy: not downloaded";
        String hash = PayloadStore.hash(file);
        String comparison = p.sha.isEmpty() ? "Feed has no reference hash" :
                (hash.equalsIgnoreCase(p.sha) ? "Matches feed SHA-256" : "Update available: hash differs");
        return "Local copy: " + file.length() + " bytes\nSHA-256: " + hash + "\n" + comparison;
    }

    private void showSelection(String detail) {
        remote.setText(selected.name + "\n" + selected.size + " bytes\n" +
                (selected.sha.isEmpty() ? "Feed SHA-256: not provided" : "Feed SHA-256: " + selected.sha));
        local.setText(detail);
    }

    private void download() {
        final Payload p = selected;
        if (p == null) return;
        job("Downloading update…", () -> {
            File target = localFile(p);
            File part = File.createTempFile("download-", ".part", getFilesDir());
            final int[] last = {-1};
            try {
                Network.download(p.url, part, (read, total) -> {
                    int percent = total > 0 ? (int) (read * 100 / total) : -1;
                    if (percent == last[0]) return;
                    last[0] = percent;
                    post(() -> {
                        progress.setIndeterminate(total <= 0);
                        progress.setProgress(percent);
                        status.setText("Downloading • " + (total > 0 ? percent + "%" : read + " bytes"));
                    });
                });
                PayloadStore.verify(part, p.size, p.sha);
                PayloadStore.replace(part, target);
                String detail = describeLocal(p);
                post(() -> {
                    showSelection(detail);
                    status.setText("Download saved");
                    log("Saved " + p.id + (p.sha.isEmpty() ? "; size checked, no reference SHA-256 in feed." : "; SHA-256 verified."));
                });
            } finally { part.delete(); }
        });
    }

    private void checkRoot() {
        final boolean autoKernel = preferences.getBoolean("auto_kernel", false);
        job("Checking root access…", () -> {
            boolean granted = false;
            String result;
            try {
                result = Shell.runLocal(new String[]{"su", "-c", "id"}, getCacheDir(), 15000);
                granted = result.matches("(?s).*\\buid=0\\b.*");
            } catch (Exception e) { result = e.getMessage(); }
            final boolean ok = granted;
            final String detail = result;
            post(() -> {
                rootGranted = ok;
                rootStatus.setText(ok ? "Access granted" : "Not granted / unavailable");
                status.setText(ok ? "Root check passed" : "Root access not granted");
                updateManager();
                log("Root check: " + detail);
                if (!ok) kernelStatus.setText("Kernel module: root access needed to check");
            });
            if (ok) {
                Shell.Transport transport = new Shell.Su();
                if (autoKernel) setupKernel(transport);
                else {
                    String loaded = KernelSetup.status(transport, getCacheDir());
                    post(() -> kernelStatus.setText("loaded".equals(loaded)
                            ? "Kernel module: loaded" : "Kernel module: not loaded"));
                }
            }
        });
    }

    private void openShizuku() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.manager");
            if (intent == null) throw new ActivityNotFoundException();
            startActivity(intent);
        } catch (Exception e) {
            showSheet("Shizuku not installed", "Install Shizuku, start it via wireless "
                    + "debugging, then return here and tap Authorize Shizuku shell.",
                    "OK", null, null, null);
        }
    }

    private void checkShizuku() {
        preferences.edit().putBoolean("shizuku_wanted", true).apply();
        job("Checking Shizuku shell…", () -> {
            if (!Shell.awaitShizukuRunning(this, 4000)) {
                post(() -> {
                    shizukuGranted = false;
                    shizukuStatus.setText(isPackageInstalled("moe.shizuku.manager")
                            ? "Shizuku shell: service not connected"
                            : "Shizuku shell: manager not installed");
                    status.setText(Shell.describeShizukuState());
                    log(Shell.describeShizukuState());
                    showSheet("Shizuku not connected", "Shizuku Manager is installed, but "
                            + "this app did not receive Shizuku's binder after waiting. Open "
                            + "Shizuku, confirm the service says Running, then return here. If "
                            + "S22 Updater is not listed in Shizuku's Apps screen, install this "
                            + "v4.2 build fresh so the provider permission is registered.",
                            "Open Shizuku", this::openShizuku, "Diagnose", this::diagnoseShizuku);
                });
                return;
            }
            if (Shell.shizukuGranted()) {
                post(() -> {
                    shizukuGranted = true;
                    shizukuStatus.setText("Shizuku shell: authorized");
                    status.setText("Shizuku shell ready");
                    log("Shizuku shell ready");
                });
                return;
            }
            post(() -> {
                shizukuStatus.setText("Shizuku shell: requesting authorization…");
                log("Requesting Shizuku authorization — approve it in Shizuku/Manager.");
            });
            Shell.requestShizukuPermission(new Shell.PermissionCallback() {
                @Override public void onResult(final boolean granted) {
                    post(() -> {
                        shizukuGranted = granted;
                        shizukuStatus.setText(granted ? "Shizuku shell: authorized"
                                : "Shizuku shell: authorization denied");
                        status.setText(granted ? "Shizuku shell ready"
                                : "Shizuku authorization denied");
                        log(granted ? "Shizuku authorized" : "Shizuku authorization denied");
                    });
                }
            });
        });
    }

    /** Prefers an authorized su session; falls back to a Shizuku shell. */
    private Shell.Transport pickTransport() {
        if (rootGranted) return new Shell.Su();
        if (shizukuGranted) {
            moe.shizuku.server.IShizukuService service = Shell.shizukuService();
            if (service != null) return new Shell.ShizukuShell(service);
        }
        return null;
    }

    private boolean isPackageInstalled(String id) {
        try {
            getPackageManager().getPackageInfo(id, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void setupKernel(Shell.Transport transport) throws Exception {
        try {
            String helper = null;
            try {
                transport.run("test -x " + Shell.quote(HELPER_DEVICE_PATH), getCacheDir());
                helper = HELPER_DEVICE_PATH;
            } catch (Exception ignored) {
            }
            String result = KernelSetup.load(Build.MODEL, transport, getCacheDir(), helper);
            post(() -> { kernelStatus.setText(result); status.setText(result); log(result); });
        } catch (Exception e) {
            post(() -> kernelStatus.setText("KernelSU setup failed: " + e.getMessage()));
            throw e;
        }
    }

    private void updateManager() {
        String found = null;
        for (String id : MANAGERS) {
            try { getPackageManager().getPackageInfo(id, 0); found = id; break; }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        }
        managerStatus.setText(found == null ? "Manager not detected" : "Manager installed\n" + found);
    }

    private void openManager() {
        updateManager();
        for (String id : MANAGERS) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(id);
            if (intent != null) {
                try { startActivity(intent); return; } catch (ActivityNotFoundException ignored) { }
            }
        }
        showSheet("Manager not found", "Install your matching KernelSU Manager APK first, "
                + "then return here.", "OK", null, null, null);
    }

    private void deploy() {
        final Payload p = selected;
        if (p == null || !rootGranted) return;
        job("Installing downloaded file…", () -> {
            File file = localFile(p);
            String hash = PayloadStore.verify(file, p.size, p.sha);
            String destination = "/data/local/tmp/cve-2026-43499";
            String temp = destination + ".s22-update";
            String qTemp = Shell.quote(temp);
            String command = "set -e; trap 'rm -f " + qTemp + "' EXIT; cp " + Shell.quote(file.getAbsolutePath())
                    + " " + qTemp + "; chmod 755 " + qTemp
                    + "; actual=$(sha256sum " + qTemp + "); [ \"${actual%% *}\" = " + Shell.quote(hash)
                    + " ]; mv -f " + qTemp + " " + Shell.quote(destination);
            Shell.runLocal(new String[]{"su", "-c", command}, getCacheDir(), 60000);
            post(() -> { status.setText("File installed and verified"); log("Installed " + p.id + " to " + destination + ". Execution was not started."); });
        });
    }

    private void runExploit() {
        final Payload p = selected;
        if (p == null) return;
        final Shell.Transport transport = pickTransport();
        if (transport == null) {
            showSheet("No privileged shell", "Running the exploit needs root or an "
                    + "authorized Shizuku shell.\n\n" + Shell.describeShizukuState()
                    + "\n\nGrant root in Home, or authorize Shizuku and try again. "
                    + "Without either, use support/deploy.sh from a computer.",
                    "Authorize Shizuku", this::checkShizuku, "Close", null);
            return;
        }
        job("Running exploit via " + transport.name() + "…", () -> {
            File payloadFile = localFile(p);
            if (!payloadFile.isFile()) {
                post(() -> status.setText("Downloading payload first…"));
                File part = File.createTempFile("download-", ".part", getFilesDir());
                try {
                    Network.download(p.url, part, null);
                    PayloadStore.verify(part, p.size, p.sha);
                    PayloadStore.replace(part, payloadFile);
                } finally {
                    part.delete();
                }
            }
            String hash = PayloadStore.verify(payloadFile, p.size, p.sha);
            if (transport instanceof Shell.ShizukuShell && !Shell.shizukuGranted()) {
                post(() -> {
                    shizukuGranted = false;
                    shizukuStatus.setText("Shizuku shell: authorization lost");
                    showSheet("Shizuku not authorized", "Shizuku permission was revoked "
                            + "or never granted. Authorize this app in Shizuku, then run "
                            + "the exploit again.", "Authorize Shizuku", this::checkShizuku,
                            "Diagnose", this::diagnoseShizuku);
                });
                return;
            }
            String helperPath = resolveHelper(p, transport);
            stageExecutable(transport, payloadFile, EXPLOIT_DEVICE_PATH, hash);
            post(() -> {
                status.setText("Exploit running — keep the phone idle…");
                log("Exploit started via " + transport.name() + ". Attempt budget 24, watchdog 15 min. Helper: " + helperPath);
            });
            String env = "EXPLOIT_ATTEMPTS=24 CVE43499_ROOT_HELPER=" + Shell.quote(helperPath);
            transport.run("rm -f " + Shell.quote(EXPLOIT_LOG_PATH), getCacheDir());
            transport.run("set -e; " + env + " LD_PRELOAD=" + Shell.quote(EXPLOIT_DEVICE_PATH)
                    + " /system/bin/sh > " + Shell.quote(EXPLOIT_LOG_PATH)
                    + " 2>&1 & echo started", getCacheDir());
            watchExploitLog(transport);
            final String helper = helperPath;
            String probe;
            try {
                probe = transport.run(Shell.quote(helper) + " -c 'id; getenforce'", getCacheDir());
            } catch (Exception e) {
                probe = "";
            }
            final boolean rooted = probe.contains("uid=0");
            final String probeText = probe;
            post(() -> {
                if (rooted) {
                    rootGranted = rootGranted || transport instanceof Shell.Su;
                    status.setText("Exploit completed — root verified");
                    log("Exploit success marker seen; helper reports:\n" + probeText);
                    promptKernelSu(transport, helper);
                } else {
                    status.setText("Exploit finished without root");
                    log("No success marker/root. Reboot for clean slabs, close apps, "
                            + "keep the screen unlocked and idle, then run again.");
                }
            });
        });
    }

    /** Returns a device path for the root helper, staging it when the feed provides one. */
    private String resolveHelper(Payload p, Shell.Transport transport) throws Exception {
        if (p.helperUrl != null && !p.helperUrl.isEmpty()) {
            File cached = new File(getCacheDir(), "helper-" + p.id);
            boolean ok = false;
            try {
                if (cached.isFile()) PayloadStore.verify(cached, p.helperSize, p.helperSha);
                else {
                    File part = File.createTempFile("helper-", ".part", getCacheDir());
                    try {
                        Network.download(p.helperUrl, part, null);
                        PayloadStore.verify(part, p.helperSize, p.helperSha);
                        PayloadStore.replace(part, cached);
                    } finally {
                        part.delete();
                    }
                }
                ok = true;
            } catch (Exception e) {
                log("Helper download failed: " + e.getMessage());
            }
            if (ok) {
                stageExecutable(transport, cached, HELPER_DEVICE_PATH,
                        PayloadStore.hash(cached));
                return HELPER_DEVICE_PATH;
            }
        }
        try {
            transport.run("test -x " + Shell.quote(HELPER_DEVICE_PATH), getCacheDir());
            return HELPER_DEVICE_PATH;
        } catch (Exception e) {
            throw new IOException("No root helper: the feed has no helper artifact and "
                    + HELPER_DEVICE_PATH + " is missing. Stage it with support/deploy.sh first.");
        }
    }

    private void stageExecutable(Shell.Transport transport, File source, String dest, String hash)
            throws Exception {
        String temp = dest + ".s22-stage";
        String qTemp = Shell.quote(temp);
        if (transport instanceof Shell.Su) {
            transport.run("set -e; trap 'rm -f " + qTemp + "' EXIT; cp "
                    + Shell.quote(source.getAbsolutePath()) + " " + qTemp + "; chmod 755 " + qTemp
                    + "; actual=$(sha256sum " + qTemp + "); [ \"${actual%% *}\" = '" + hash + "' ]; mv -f "
                    + qTemp + " " + Shell.quote(dest), getCacheDir());
        } else {
            transport.writeFile(source, temp, "755");
            transport.run("set -e; actual=$(sha256sum " + qTemp + "); "
                    + "[ \"${actual%% *}\" = '" + hash + "' ]; mv -f " + qTemp + " "
                    + Shell.quote(dest), getCacheDir());
        }
    }

    /** Polls the on-device exploit log for the success marker with stall/total watchdogs. */
    private void watchExploitLog(Shell.Transport transport) throws Exception {
        final long totalMs = 15 * 60 * 1000;
        final long stallMs = 120 * 1000;
        long start = System.currentTimeMillis();
        long lastProgress = start;
        String lastTail = "";
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
            String tail;
            try {
                tail = transport.run("tail -c 4096 " + Shell.quote(EXPLOIT_LOG_PATH) + " 2>/dev/null || true",
                        getCacheDir());
            } catch (Exception e) {
                tail = "";
            }
            if (!tail.equals(lastTail)) {
                lastTail = tail;
                lastProgress = System.currentTimeMillis();
                final String snapshot = tail.length() > 600 ? tail.substring(tail.length() - 600) : tail;
                post(() -> {
                    status.setText("Exploit running…");
                    log("exploit: " + snapshot.replace("\n", " | "));
                });
            }
            if (tail.contains("exploit completed")) return;
            long now = System.currentTimeMillis();
            if (now - lastProgress > stallMs)
                throw new IOException("Exploit stalled (no log progress for 120s). "
                        + "Reboot for clean slabs and try again.");
            if (now - start > totalMs)
                throw new IOException("Exploit timed out after 15 minutes.");
            Thread.sleep(2000);
        }
    }

    private void promptKernelSu(final Shell.Transport transport, final String helperPath) {
        post(() -> showSheet("Exploit completed", "Root verified through the exploit helper.\n\n"
                + "Install KernelSU now?", "Install KernelSU", () ->
                job("Setting up KernelSU…", () -> {
                    try {
                        String result = KernelSetup.load(Build.MODEL, transport,
                                getCacheDir(), helperPath);
                        post(() -> {
                            kernelStatus.setText(result);
                            status.setText(result);
                            log(result);
                        });
                    } catch (Exception e) {
                        post(() -> kernelStatus.setText(
                                "KernelSU setup failed: " + e.getMessage()));
                        throw new RuntimeException(e);
                    }
                }), "Later", null));
    }

    private void checkAppUpdate() {
        job("Checking for app updates…", () -> {
            final int installed = AppUpdate.installedCode(this);
            final AppUpdate.Info info;
            try {
                info = AppUpdate.check();
            } catch (java.io.IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("404")) {
                    post(() -> {
                        appUpdateStatus.setText("No app release published yet");
                        status.setText("No app release published yet");
                        log("App update check: releases/latest returned 404 — publish a "
                                + "release (push a vX.Y tag) before this check can work.");
                        showSheet("No app release yet", "This check reads the repo's latest "
                                + "GitHub release, and none is published. Open the releases "
                                + "page to confirm.", "Open releases",
                                () -> AppUpdate.openReleases(MainActivity.this), "Later", null);
                    });
                    return;
                }
                throw e;
            }
            if (info.versionCode <= installed || installed == 0) {
                final String current = AppUpdate.installedName(this);
                post(() -> {
                    appUpdateStatus.setText("S22 Updater " + current + " is up to date");
                    status.setText("App is up to date");
                    log("App update check: installed=" + installed + " latest=" + info.versionCode);
                });
                return;
            }
            post(() -> {
                appUpdateStatus.setText("S22 Updater " + info.versionName + " available");
                status.setText("Downloading app update…");
                log("App update " + info.tag + " found, downloading " + info.apkName);
            });
            final File apk = AppUpdate.download(this, info, new Network.Progress() {
                @Override public void update(final long read, final long total) {
                    post(() -> {
                        progress.setIndeterminate(total <= 0);
                        if (total > 0) progress.setProgress((int) (read * 1000 / total));
                        status.setText("Downloading app update • " + read + " bytes");
                    });
                }
            });
            post(() -> {
                try {
                    preferences.edit().putString("last_app_update", info.tag).apply();
                } catch (Exception ignored) {
                }
                status.setText("App update downloaded");
                log("App update verified, asking to install.");
            });
            final AppUpdate.SignatureReport signature = AppUpdate.checkInstallCompatibility(this, apk);
            if (!signature.compatible) {
                post(() -> showUpdateConflict(info, signature));
                return;
            }
            if (!AppUpdate.canRequestPackageInstalls(this)) {
                post(() -> showSheet("Allow app installs", "Android blocks S22 Updater "
                        + "from installing APK updates right now. Enable 'Allow from this "
                        + "source', then return here and run Check for app updates again.",
                        "Open setting", () -> AppUpdate.openInstallPermission(MainActivity.this),
                        "Later", null));
                return;
            }
            post(() -> showUpdateInstall(info, apk, signature));
        });
    }

    private void showUpdateInstall(final AppUpdate.Info info, final File apk,
                                   AppUpdate.SignatureReport signature) {
        String body = "S22 Updater " + info.versionName + " is downloaded and verified.\n\n"
                + "Package: " + getPackageName() + "\n"
                + "Signer: " + shortCert(signature.updateCert) + "\n\n"
                + "Android will ask you to confirm the install.";
        showSheet("Install app update", body, "Install", () -> {
            try {
                AppUpdate.install(MainActivity.this, apk);
            } catch (Exception e) {
                showSheet("Installer unavailable", "Android could not open the package "
                        + "installer for this APK. Open the release page and install the APK "
                        + "manually.", "Open releases", () -> AppUpdate.openReleases(MainActivity.this),
                        "Close", null);
            }
        }, "Later", null);
    }

    private void showUpdateConflict(AppUpdate.Info info, AppUpdate.SignatureReport signature) {
        appUpdateStatus.setText("Update blocked by package signature conflict");
        status.setText("Update signature conflict");
        log("App update conflict: " + signature.problem + " installed="
                + signature.installedCert + " update=" + signature.updateCert);
        String body = "Android rejects in-place updates when the installed app and "
                + "update APK are signed by incompatible certificates.\n\n"
                + "Installed signer: " + shortCert(signature.installedCert) + "\n"
                + "Update signer: " + shortCert(signature.updateCert) + "\n\n"
                + "v4.2 includes Android 9+ key-rotation lineage from the old local "
                + "debug key to the release key. If Android still says package conflict, "
                + "your installed build used a different lost key. Uninstall S22 Updater "
                + "once, then install v4.2 from Releases. After that, future updates will "
                + "install normally.";
        showSheet("Package conflict", body, "Open releases",
                () -> AppUpdate.openReleases(MainActivity.this), "App info", this::openSelfInfo);
    }

    private String shortCert(String digest) {
        if (digest == null || digest.isEmpty()) return "unknown";
        return digest.length() <= 16 ? digest : digest.substring(0, 16) + "...";
    }

    private void openSelfInfo() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
        }
    }

    private void export() {
        try {
            exportFile = localFile(selected);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, selected.id + ".so");
            startActivityForResult(intent, 100);
        } catch (Exception e) { error(e); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 100 || result != RESULT_OK || data == null || data.getData() == null || exportFile == null) return;
        final File source = exportFile;
        final Uri uri = data.getData();
        job("Exporting file…", () -> {
            try (InputStream in = new FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IOException("Unable to open export destination");
                Network.transfer(in, out, 64 * 1024 * 1024, source.length(), null);
            }
            post(() -> { status.setText("File exported"); log("Export complete."); });
        });
    }

    private void loadChangelog() {
        job("Loading changelog…", () -> {
            StringBuilder output = new StringBuilder();
            for (String repo : new String[]{"Bodo121/S22-Updater", "Bodo121/IONSTACK-S22", "Bodo121/KSU-S22"}) {
                output.append(repo).append("\n\n");
                try {
                    JSONArray commits = new JSONArray(Network.text("https://api.github.com/repos/" + repo + "/commits?per_page=5"));
                    for (int i = 0; i < commits.length(); i++) {
                        JSONObject c = commits.getJSONObject(i);
                        output.append("• ").append(c.getJSONObject("commit").getString("message").split("\n", 2)[0]).append("\n");
                    }
                } catch (Exception e) { output.append("Unavailable: ").append(e.getMessage()).append("\n"); }
                output.append("\n");
            }
            post(() -> { changelog.setText(output.toString()); status.setText("Changelog refreshed"); });
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), border);
        return drawable;
    }
    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10); return params;
    }
    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout card = column();
        card.setBackground(shape(surface, 20)); card.setPadding(dp(18), dp(16), dp(18), dp(18));
        parent.addView(card, spaced()); text(card, heading, 12, accent, true); return card;
    }
    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setPadding(0, dp(5), 0, dp(6)); view.setLineSpacing(dp(3), 1f);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private Button makeButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setMinHeight(dp(48)); button.setMinimumWidth(0);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setTextColor(primary ? bg : accent);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337A9FFF), shape(primary ? accent : bg, 12), null));
        return button;
    }
    private Button action(LinearLayout parent, String label, Runnable callback) {
        Button button = makeButton(label, true);
        button.setOnClickListener(v -> callback.run()); parent.addView(button, spaced()); actions.add(button); return button;
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("page", pageIndex);
        if (exportFile != null) state.putString("export_file", exportFile.getName());
    }
    @Override protected void onDestroy() {
        closed = true; ui.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy();
    }
}
