package com.bodo121.s22updater;

import android.app.Activity;
import android.app.Dialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
    private final LinearLayout[] pages = new LinearLayout[3];
    private final Button[] tabs = new Button[3];
    private TextView status, flowHint, rootStatus, shizukuStatus, managerStatus,
            kernelStatus, activityLog, changelog, appUpdateStatus, installPermStatus,
            stepFeed, stepPayload, stepRoot, stepKsu, deviceInfo, homeLog,
            rootChip, appearanceStatus, logEmpty;
    private LinearLayout statusCardView;
    private FlowStepper flowStepper;
    private ProgressBar progress;
    private EditText feedInput;
    private Button flowAction, checkAppUpdate;
    private Switch automaticKernel, automaticAppUpdate;
    private SharedPreferences preferences;
    private Payload selected;
    private File exportFile;
    private boolean busy, rootGranted, shizukuGranted, exploitRooted, ksuLoaded, ksuWorking;
    private final StringBuilder runLog = new StringBuilder();
    private volatile boolean closed;
    private int bg, surface, ink, muted, accent, border, success, danger, warning, pageIndex;
    private boolean restoredProgress;
    private String failedStep = "";

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
        applyPalette(dark);
        restoreDurableState();
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(surface);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout shell = column();
        shell.setBackgroundColor(bg);
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(18), dp(22), dp(12));
        LinearLayout title = column();
        text(title, "S22 / CONTROL CENTER", 12, accent, true);
        text(title, "Your device. Your updates.", 25, ink, true);
        text(title, "IONSTACK • Version " + AppUpdate.installedName(this), 12, muted, false);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        rootChip = chip("Root: Not granted", muted);
        header.addView(rootChip, new LinearLayout.LayoutParams(-2, dp(40)));
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
        buildLog();
        buildSettings();
        LinearLayout navigation = new LinearLayout(this);
        navigation.setBackgroundColor(surface);
        navigation.setPadding(dp(6), dp(8), dp(6), dp(8));
        String[] names = {"Home", "Log", "Settings"};
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
        applyRestoredState();
        showPage(state == null ? pageIndex : state.getInt("page", pageIndex));
        updateManager();
        refreshControls();
        watchShizukuBinder();
        if (restoredProgress) log("Progress restored for this boot.");
        else log("Ready. One button walks the whole flow: update info, payload, exploit, KernelSU.");
        if (preferences.getBoolean("auto_app_update", false)) checkAppUpdate();
    }

    @Override protected void onResume() {
        super.onResume();
        pollShizuku();
        refreshInstallPermission();
    }

    private void applyPalette(boolean dark) {
        String theme = preferences.getString("theme_color", "system");
        int chosen;
        switch (theme) {
            case "blue": chosen = Color.parseColor("#3F6FE5"); break;
            case "green": chosen = Color.parseColor("#2E7D32"); break;
            case "purple": chosen = Color.parseColor("#7B4DFF"); break;
            case "orange": chosen = Color.parseColor("#C86418"); break;
            default: chosen = resolveColor(android.R.attr.colorAccent,
                    Color.parseColor(dark ? "#99BBFF" : "#2459CE")); break;
        }
        accent = chosen;
        bg = blend(accent, dark ? Color.BLACK : Color.WHITE, dark ? .88f : .92f);
        surface = blend(accent, dark ? Color.BLACK : Color.WHITE, dark ? .76f : .985f);
        ink = Color.parseColor(dark ? "#F1F4FA" : "#14171F");
        muted = blend(ink, surface, .42f);
        border = blend(accent, surface, dark ? .68f : .78f);
        success = Color.parseColor(dark ? "#81C784" : "#2E7D32");
        danger = Color.parseColor(dark ? "#FFB4AB" : "#B3261E");
        warning = Color.parseColor(dark ? "#FFD180" : "#A15C00");
    }

    private int resolveColor(int attr, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        return getTheme().resolveAttribute(attr, value, true) ? value.data : fallback;
    }

    private int blend(int from, int to, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        int r = (int) (Color.red(from) * (1f - a) + Color.red(to) * a);
        int g = (int) (Color.green(from) * (1f - a) + Color.green(to) * a);
        int b = (int) (Color.blue(from) * (1f - a) + Color.blue(to) * a);
        return Color.rgb(r, g, b);
    }

    private String bootId() {
        try {
            return Shell.runLocal(new String[]{"/system/bin/sh", "-c",
                    "cat /proc/sys/kernel/random/boot_id 2>/dev/null || uptime"},
                    getCacheDir(), 5000).trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean sameBoot() {
        String stored = preferences.getString("boot_id", "");
        String current = bootId();
        if (!stored.equals(current)) {
            preferences.edit()
                    .putString("boot_id", current)
                    .remove("root_granted")
                    .remove("exploit_rooted")
                    .remove("ksu_loaded")
                    .remove("ksu_working")
                    .remove("shizuku_granted")
                    .apply();
            return false;
        }
        return true;
    }

    private void restoreDurableState() {
        restoredProgress = false;
        boolean boot = sameBoot();
        rootGranted = boot && preferences.getBoolean("root_granted", false);
        exploitRooted = boot && preferences.getBoolean("exploit_rooted", false);
        ksuLoaded = boot && preferences.getBoolean("ksu_loaded", false);
        ksuWorking = boot && preferences.getBoolean("ksu_working", false);
        shizukuGranted = boot && preferences.getBoolean("shizuku_granted", false);
        pageIndex = preferences.getInt("page", 0);
        String savedRunLog = preferences.getString("run_log", "");
        if (!savedRunLog.isEmpty()) runLog.append(savedRunLog);
        String feedJson = preferences.getString("feed_json", "");
        String savedPayload = preferences.getString("selected_payload", "r0s-S901BXXSNGZD7");
        if (!feedJson.isEmpty()) {
            try {
                restorePayloads(new JSONObject(feedJson), savedPayload);
                restoredProgress = selected != null;
            } catch (Exception e) {
                preferences.edit().remove("feed_json").apply();
            }
        }
        restoredProgress |= rootGranted || exploitRooted || ksuLoaded || ksuWorking || runLog.length() > 0;
    }

    private void applyRestoredState() {
        if (status == null) return;
        String savedStatus = preferences.getString("status_text", "Ready to check");
        status.setText(savedStatus);
        rootStatus.setText(rooted() ? "Root: granted" : "Root: not checked");
        shizukuStatus.setText(shizukuGranted ? "Shizuku: authorized" : "Shizuku: not checked");
        kernelStatus.setText(ksuWorking ? "KernelSU: working"
                : ksuLoaded ? "Kernel module: loaded, su not granted"
                : rooted() ? "Kernel module: not loaded" : "Kernel module: check root first");
        if (runLog.length() > 0) {
            String all = runLog.toString();
            homeLog.setText(all.length() > 2500 ? "…" + all.substring(all.length() - 2499) : all);
        }
        String session = preferences.getString("activity_log", "");
        if (!session.isEmpty()) activityLog.setText(session);
        updateLogEmpty();
        updateFlow();
    }

    private void saveVolatileState() {
        preferences.edit()
                .putString("boot_id", bootId())
                .putBoolean("root_granted", rootGranted)
                .putBoolean("exploit_rooted", exploitRooted)
                .putBoolean("ksu_loaded", ksuLoaded)
                .putBoolean("ksu_working", ksuWorking)
                .putBoolean("shizuku_granted", shizukuGranted)
                .apply();
    }

    private void saveStatus(String value) {
        preferences.edit().putString("status_text", value).apply();
    }

    private void restorePayloads(JSONObject feed, String savedPayload) throws Exception {
        JSONArray entries = feed.getJSONArray("payloads");
        List<Payload> loaded = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) loaded.add(new Payload(entries.getJSONObject(i)));
        if (loaded.isEmpty()) return;
        Payload preferred = loaded.get(0);
        for (Payload p : loaded) if (p.id.equals(savedPayload)) preferred = p;
        payloads.clear();
        payloads.addAll(loaded);
        selected = preferred;
    }

    /** Shows whether Android currently lets this app install APK updates. */
    private void refreshInstallPermission() {
        if (installPermStatus == null) return;
        boolean allowed = AppUpdate.canRequestPackageInstalls(this);
        installPermStatus.setText(allowed ? "Install permission: granted — updates can install"
                : "Install permission: MISSING — tap 'Allow app installs' below");
        installPermStatus.setTextColor(allowed ? muted : 0xFFB00020);
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
                                saveVolatileState();
                                shizukuStatus.setText("Shizuku: binder died");
                                log("Shizuku binder died — restart Shizuku if needed");
                                updateFlow();
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
                    saveVolatileState();
                    if (granted) shizukuStatus.setText("Shizuku: authorized");
                    else if (running) shizukuStatus.setText("Shizuku: awaiting authorization");
                    else shizukuStatus.setText(isPackageInstalled("moe.shizuku.manager")
                            ? "Shizuku: service not connected"
                            : "Shizuku: not installed");
                    updateFlow();
                });
            }
        }).start();
    }

    private void buildHome() {
        statusCardView = card(pages[0], "ROOT FLOW");
        status = text(statusCardView, "Ready to check", 24, ink, true);
        flowHint = text(statusCardView, "Six steps, one action. The active step is highlighted; failures stay local with retry.", 13, muted, false);
        flowStepper = new FlowStepper(this);
        flowStepper.setColors(ink, muted, accent, success, danger, surface);
        statusCardView.addView(flowStepper, new LinearLayout.LayoutParams(-1, dp(238)));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setIndeterminateTintList(ColorStateList.valueOf(accent));
        progress.setProgressTintList(ColorStateList.valueOf(accent));
        progress.setVisibility(View.GONE);
        statusCardView.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        flowAction = action(statusCardView, "Check for updates", this::primaryAction);
        LinearLayout steps = card(pages[0], "PROGRESS");
        stepFeed = text(steps, "○  Update info", 14, muted, false);
        stepPayload = text(steps, "○  Payload", 14, muted, false);
        stepRoot = text(steps, "○  Root access", 14, muted, false);
        stepKsu = text(steps, "○  KernelSU", 14, muted, false);
        LinearLayout access = card(pages[0], "ACCESS");
        rootStatus = text(access, "Root: not checked", 14, muted, false);
        action(access, "Check root", this::checkRoot);
        shizukuStatus = text(access, "Shizuku: not checked", 14, muted, false);
        action(access, "Authorize Shizuku", this::checkShizuku);
        text(access, "No root yet? Install Shizuku, start it via wireless debugging, then authorize.", 13, muted, false);
        LinearLayout manager = card(pages[0], "KERNELSU");
        managerStatus = text(manager, "Checking manager…", 16, ink, true);
        kernelStatus = text(manager, "Kernel module: check root first", 14, muted, false);
        action(manager, "Open KernelSU Manager", this::openManager);
        LinearLayout device = card(pages[0], "DEVICE");
        deviceInfo = text(device, "", 13, muted, false);
        deviceInfo.setTextIsSelectable(true);
        LinearLayout live = card(pages[0], "LIVE LOG");
        homeLog = text(live, "Run output appears here while the exploit runs.", 12, ink, false);
        homeLog.setTypeface(Typeface.MONOSPACE);
    }

    private void buildLog() {
        LinearLayout history = card(pages[1], "SESSION LOG");
        logEmpty = text(history, "No activity yet — run the flow or check for updates.", 13, muted, false);
        activityLog = text(history, "", 13, ink, false);
        activityLog.setTypeface(Typeface.MONOSPACE);
        activityLog.setTextIsSelectable(true);
        updateLogEmpty();
        action(history, "Copy diagnostics", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("S22 diagnostics", Build.MODEL + " / " + Build.DISPLAY
                    + "\n" + activityLog.getText()));
            Toast.makeText(this, "Diagnostics copied", Toast.LENGTH_SHORT).show();
        });
        action(history, "Share diagnostics", this::shareDiagnostics);
        LinearLayout changes = card(pages[1], "PROJECT CHANGELOG");
        action(changes, "Refresh changelog", this::loadChangelog);
        changelog = text(changes, "No changelog loaded yet. Tap Refresh changelog.", 14, muted, false);
    }

    private void buildSettings() {
        LinearLayout settings = card(pages[2], "FEED SETTINGS");
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
        LinearLayout appearance = card(pages[2], "APPEARANCE");
        appearanceStatus = text(appearance, "Theme color: " + themeLabel(), 14, muted, false);
        LinearLayout themeRow = row();
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        String[] themeNames = {"System", "Blue", "Green", "Purple", "Orange"};
        String[] themeValues = {"system", "blue", "green", "purple", "orange"};
        for (int i = 0; i < themeNames.length; i++) {
            final String name = themeNames[i];
            final String value = themeValues[i];
            Button button = makeButton(name, false);
            button.setTextSize(11);
            button.setOnClickListener(v -> setThemeChoice(value));
            actions.add(button);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
            if (i > 0) params.leftMargin = dp(6);
            themeRow.addView(button, params);
        }
        appearance.addView(themeRow, spaced());
        LinearLayout updater = card(pages[2], "APP UPDATES");
        appUpdateStatus = text(updater, "S22 Updater " + AppUpdate.installedName(this)
                + " — app update status unknown", 14, muted, false);
        installPermStatus = text(updater, "", 13, muted, false);
        action(updater, "Allow app installs", () -> {
            AppUpdate.openInstallPermission(MainActivity.this);
            Toast.makeText(MainActivity.this, "Enable 'Allow from this source', then return",
                    Toast.LENGTH_LONG).show();
        });
        checkAppUpdate = action(updater, "Check for app updates", this::checkAppUpdate);
        automaticAppUpdate = new Switch(this);
        automaticAppUpdate.setText("Check for app updates at startup");
        automaticAppUpdate.setTextColor(ink);
        automaticAppUpdate.setPadding(0, dp(12), 0, dp(12));
        automaticAppUpdate.setChecked(preferences.getBoolean("auto_app_update", false));
        automaticAppUpdate.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean("auto_app_update", checked).apply());
        updater.addView(automaticAppUpdate, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout tools = card(pages[2], "SHIZUKU TOOLS");
        action(tools, "Open Shizuku app", this::openShizuku);
        action(tools, "Diagnose Shizuku handshake", this::diagnoseShizuku);
        LinearLayout payloadCard = card(pages[2], "PAYLOAD FILE");
        action(payloadCard, "Export downloaded payload", () -> {
            if (selected == null) {
                Toast.makeText(this, "Check for updates first", Toast.LENGTH_SHORT).show();
                return;
            }
            export();
        });
        text(payloadCard, "Saves the verified payload to a file you choose.", 13, muted, false);
        LinearLayout options = card(pages[2], "OPTIONS");
        automaticKernel = new Switch(this);
        automaticKernel.setText("Load KernelSU after a successful root check");
        automaticKernel.setTextColor(ink);
        automaticKernel.setPadding(0, dp(12), 0, dp(12));
        automaticKernel.setChecked(preferences.getBoolean("auto_kernel", false));
        automaticKernel.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean("auto_kernel", checked).apply());
        options.addView(automaticKernel, new LinearLayout.LayoutParams(-1, -2));
        text(options, "When enabled, a successful root check also loads the matching module. It skips a module already loaded.", 13, muted, false);
        LinearLayout about = card(pages[2], "ABOUT THIS BUILD");
        text(about, "S22 Updater " + AppUpdate.installedName(this), 20, ink, true);
        TextView identity = text(about, "com.bodo121.s22updater", 13, muted, false);
        identity.setTextIsSelectable(true);
        text(about, "System light/dark theme • Android 9+\nDownloads stay local until staged. Root is volatile: reboot clears it.", 14, muted, false);
    }

    private void resetFeed() {
        selected = null;
        payloads.clear();
        preferences.edit().remove("feed_json").remove("selected_payload").apply();
        status.setText("Feed changed — check for updates");
        saveStatus("Feed changed — check for updates");
        refreshControls();
    }

    private String themeLabel() {
        String value = preferences.getString("theme_color", "system");
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private void setThemeChoice(String value) {
        if (busy) {
            Toast.makeText(this, "Wait for the current action to finish", Toast.LENGTH_SHORT).show();
            return;
        }
        preferences.edit().putString("theme_color", value).apply();
        Toast.makeText(this, "Theme color: " + value, Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void showPage(int page) {
        pageIndex = Math.max(0, Math.min(2, page));
        preferences.edit().putInt("page", pageIndex).apply();
        for (int i = 0; i < pages.length; i++) {
            View parent = (View) pages[i].getParent();
            boolean visible = i == pageIndex;
            parent.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                parent.setAlpha(0f);
                parent.setTranslationY(dp(8));
                parent.animate().alpha(1f).translationY(0f).setDuration(180).start();
            }
            tabs[i].setTextColor(i == pageIndex ? accent : muted);
            tabs[i].setTypeface(null, i == pageIndex ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private interface Work { void run() throws Exception; }
    private void job(String message, Work work) {
        job(message, false, work);
    }
    private void flowJob(String message, Work work) {
        job(message, true, work);
    }
    private void job(String message, boolean flow, Work work) {
        if (busy || closed) return;
        busy = true;
        if (flow) failedStep = "";
        status.setText(message);
        saveStatus(message);
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        log(message);
        refreshControls();
        worker.execute(() -> {
            try { work.run(); }
            catch (Exception e) { post(() -> error(e, flow)); }
            finally { post(() -> {
                busy = false;
                progress.setVisibility(View.GONE);
                try {
                    getWindow().clearFlags(
                            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } catch (Exception ignored) {
                }
                refreshControls();
            }); }
        });
    }

    private void post(Runnable runnable) {
        if (!closed) ui.post(() -> { if (!closed && !isFinishing()) runnable.run(); });
    }

    private void error(Exception e) {
        error(e, false);
    }

    private void error(Exception e, boolean flow) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (flow) failedStep = flowStep();
        status.setText(message);
        saveStatus(message);
        log("Failed: " + message);
        updateFlow();
        if (statusCardView != null) statusCardView.animate().translationX(dp(5)).setDuration(55)
                .withEndAction(() -> statusCardView.animate().translationX(-dp(5)).setDuration(55)
                        .withEndAction(() -> statusCardView.animate().translationX(0).setDuration(55).start()).start())
                .start();
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
        activityLog.setAlpha(.72f);
        activityLog.animate().alpha(1f).setDuration(180).start();
        preferences.edit().putString("activity_log", activityLog.getText().toString()).apply();
        updateLogEmpty();
    }

    private void updateLogEmpty() {
        if (logEmpty == null || activityLog == null) return;
        boolean empty = activityLog.getText().toString().trim().isEmpty();
        logEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        activityLog.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void shareDiagnostics() {
        try {
            File dir = new File(getCacheDir(), "share");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("No share cache");
            File file = new File(dir, "diagnostics.txt");
            String body = "S22 Updater diagnostics\n"
                    + "Version: " + AppUpdate.installedName(this) + "\n"
                    + "Device: " + Build.MODEL + " / " + Build.DISPLAY + "\n"
                    + "Boot-scoped state: root=" + rootGranted + " exploit=" + exploitRooted
                    + " ksuLoaded=" + ksuLoaded + " ksuWorking=" + ksuWorking
                    + " shizuku=" + shizukuGranted + "\n"
                    + "Selected payload: " + (selected == null ? "none" : selected.id) + "\n\n"
                    + "--- visible status ---\n" + status.getText() + "\n\n"
                    + "--- run log ---\n" + runLog + "\n\n"
                    + "--- session log ---\n" + activityLog.getText() + "\n";
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "S22 Updater diagnostics");
            intent.putExtra(Intent.EXTRA_STREAM, ApkProvider.diagnosticsUri(this, file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share diagnostics"));
        } catch (Exception e) {
            error(e);
        }
    }

    private File localFile(Payload p) throws Exception { return PayloadStore.file(getFilesDir(), p.id); }

    private void refreshControls() {
        for (Button button : actions) { button.setEnabled(!busy); button.setAlpha(busy ? .5f : 1f); }
        feedInput.setEnabled(!busy);
        automaticKernel.setEnabled(!busy);
        automaticAppUpdate.setEnabled(!busy);
        updateFlow();
    }

    private boolean feedReady() {
        return selected != null && !payloads.isEmpty();
    }

    private boolean payloadReady() {
        if (!feedReady()) return false;
        try {
            File file = localFile(selected);
            if (!file.isFile()) return false;
            String hash = PayloadStore.hash(file);
            return selected.sha.isEmpty() || hash.equalsIgnoreCase(selected.sha);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean transportReady() {
        return rootGranted || shizukuGranted;
    }

    private boolean rooted() {
        return rootGranted || exploitRooted;
    }

    /** One step at a time: feed → download → transport → run → ksu → done. */
    private String flowStep() {
        if (!feedReady()) return "feed";
        if (!payloadReady()) return "download";
        if (!transportReady()) return "transport";
        if (!rooted()) return "run";
        if (!ksuWorking) return "ksu";
        return "done";
    }

    private void primaryAction() {
        if (busy) return;
        switch (flowStep()) {
            case "feed": checkFeed(); break;
            case "download": download(); break;
            case "transport": checkRoot(); break;
            case "run": runExploit(); break;
            case "ksu": {
                if (ksuLoaded && !ksuWorking) {
                    checkRoot();
                    break;
                }
                final Shell.Transport transport = pickTransport();
                if (transport == null) {
                    status.setText("No privileged shell for KernelSU setup");
                    log("KernelSU setup needs root or an authorized Shizuku shell.");
                    showSheet("No privileged shell", "Load KernelSU needs root or an "
                            + "authorized Shizuku shell.", "Authorize Shizuku",
                            this::checkShizuku, "Close", null);
                    return;
                }
                flowJob("Setting up KernelSU…", () -> setupKernel(transport));
                break;
            }
            default: openManager(); break;
        }
    }

    private void markStep(TextView view, boolean done, boolean current, String label) {
        view.setText((done ? "✓  " : current ? "●  " : "○  ") + label);
        view.setTextColor(done ? success : current ? accent : muted);
        view.setTypeface(null, done || current ? Typeface.BOLD : Typeface.NORMAL);
    }

    private int stepIndex(String step) {
        switch (step) {
            case "feed": return 0;
            case "download": return 1;
            case "transport": return 2;
            case "run": return 3;
            case "ksu": return 4;
            case "done": return 5;
            default: return 0;
        }
    }

    private void updateFlow() {
        if (flowAction == null) return;
        String step = busy ? "" : flowStep();
        String stateStep = step.isEmpty() ? flowStep() : step;
        switch (step) {
            case "feed": flowAction.setText("Check for updates"); break;
            case "download": flowAction.setText("Download payload"); break;
            case "transport": flowAction.setText("Check root access"); break;
            case "run": flowAction.setText("Run exploit"); break;
            case "ksu": flowAction.setText(ksuLoaded ? "Check KernelSU root" : "Load KernelSU"); break;
            case "done": flowAction.setText("Open KernelSU Manager"); break;
            default: flowAction.setText("Working…"); break;
        }
        applyIcon(flowAction, flowAction.getText().toString(), true);
        flowHint.setText(ksuWorking ? "KernelSU is active for this boot."
                : ksuLoaded ? "KernelSU module is live. Approve this app in Manager, then check root."
                : rooted() ? "Root verified. Next: load KernelSU."
                : transportReady() ? "Shell ready. Next: run the exploit."
                : payloadReady() ? "Payload verified. Next: root access."
                : feedReady() ? "Update info ready. Next: download."
                : "One button walks the whole flow: update info, payload, exploit, KernelSU.");
        markStep(stepFeed, feedReady(), "feed".equals(step),
                "Update info" + (feedReady() ? " — " + selected.id : ""));
        markStep(stepPayload, payloadReady(), "download".equals(step),
                "Payload" + (payloadReady() ? " — verified" : ""));
        markStep(stepRoot, rooted(), "transport".equals(step) || "run".equals(step),
                "Root access" + (rooted() ? " — granted" : ""));
        markStep(stepKsu, ksuWorking, "ksu".equals(step),
                "KernelSU" + (ksuWorking ? " — working" : ksuLoaded ? " — waiting for su" : ""));
        if (flowStepper != null) {
            boolean manager = managerInstalled();
            flowStepper.setState(feedReady(), payloadReady(), transportReady(), rooted(),
                    ksuWorking, manager && ksuWorking, stepIndex(stateStep),
                    failedStep.isEmpty() ? -1 : stepIndex(failedStep));
            if (busy) flowStepper.pulse();
        }
        if (statusCardView != null) {
            int fill = failedStep.isEmpty() ? (ksuWorking ? blend(success, surface, .9f)
                    : busy ? blend(accent, surface, .9f) : surface)
                    : blend(danger, surface, .9f);
            statusCardView.setBackground(shape(fill, 22));
            if (busy) statusCardView.animate().alpha(.94f).setDuration(250)
                    .withEndAction(() -> statusCardView.animate().alpha(1f).setDuration(320).start()).start();
        }
        updateRootChip();
        String device = Build.MODEL + " • Android " + Build.VERSION.RELEASE + "\n" + Build.DISPLAY;
        if (feedReady()) device += "\nPayload: " + selected.id;
        deviceInfo.setText(device);
    }

    private void updateRootChip() {
        if (rootChip == null) return;
        int color;
        if (ksuWorking) {
            rootChip.setText("  KSU Root Active  ");
            color = success;
            rootChip.setBackground(shape(blend(success, surface, .86f), 999));
        } else if (ksuLoaded) {
            rootChip.setText("  KSU Loaded: Grant su  ");
            color = warning;
            rootChip.setBackground(shape(blend(warning, surface, .86f), 999));
        } else if (rootGranted || exploitRooted) {
            rootChip.setText("  Root Active  ");
            color = success;
            rootChip.setBackground(shape(blend(success, surface, .88f), 999));
        } else if (shizukuGranted) {
            rootChip.setText("  Shizuku Ready  ");
            color = accent;
            rootChip.setBackground(shape(blend(accent, surface, .88f), 999));
        } else {
            rootChip.setText("  Root: Not granted  ");
            color = muted;
            rootChip.setBackground(shape(blend(muted, surface, .9f), 999));
        }
        rootChip.setTextColor(color);
        Drawable[] drawables = rootChip.getCompoundDrawables();
        if (drawables[0] != null) drawables[0].setTint(color);
    }

    private void updateProgress(long read, long total, String label) {
        progress.setAlpha(.45f);
        progress.setIndeterminate(total <= 0);
        if (total > 0) progress.setProgress((int) (read * 1000 / total));
        progress.animate().alpha(1f).setDuration(160).start();
        status.setText(label);
        saveStatus(label);
    }

    private void runAppend(String line) {
        runLog.append(line).append('\n');
        if (runLog.length() > 6000) runLog.delete(0, runLog.length() - 6000);
        String all = runLog.toString();
        homeLog.setText(all.length() > 2500 ? "…" + all.substring(all.length() - 2499) : all);
        preferences.edit().putString("run_log", all).apply();
    }

    private void checkFeed() {
        final String url = preferences.getString("feed_url", FEED);
        flowJob("Loading targets feed…", () -> {
            String feedText = Network.text(url);
            JSONObject feed = new JSONObject(feedText);
            if (feed.optInt("schemaVersion", 3) != 3) throw new IOException("Only schema-v3 feeds are supported");
            String saved = preferences.getString("selected_payload", "r0s-S901BXXSNGZD7");
            JSONArray entries = feed.getJSONArray("payloads");
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < entries.length(); i++) {
                Payload p = new Payload(entries.getJSONObject(i));
                if (!ids.add(p.id)) throw new IOException("Duplicate payload ID");
            }
            restorePayloads(feed, saved);
            if (selected == null) throw new IOException("Feed contains no payloads");
            final Payload chosen = selected;
            final int count = payloads.size();
            post(() -> {
                preferences.edit()
                        .putString("selected_payload", chosen.id)
                        .putString("feed_json", feed.toString())
                        .apply();
                status.setText("Feed ready • " + chosen.name);
                saveStatus("Feed ready • " + chosen.name);
                log("Feed loaded: " + chosen.id + " selected (" + count + " target(s)).");
            });
        });
    }

    private String describeLocal(Payload p) throws Exception {
        File file = localFile(p);
        if (!file.isFile()) return "Local copy: not downloaded";
        String hash = PayloadStore.hash(file);
        String comparison = p.sha.isEmpty() ? "Feed has no reference hash" :
                (hash.equalsIgnoreCase(p.sha) ? "Matches feed SHA-256" : "Update available: hash differs");
        return "Local copy: " + file.length() + " bytes\nSHA-256: " + hash + "\n" + comparison;
    }

    private void download() {
        final Payload p = selected;
        if (p == null) return;
        flowJob("Downloading update…", () -> {
            File target = localFile(p);
            File part = File.createTempFile("download-", ".part", getFilesDir());
            final int[] last = {-1};
            try {
                Network.download(p.url, part, (read, total) -> {
                    int percent = total > 0 ? (int) (read * 100 / total) : -1;
                    if (percent == last[0]) return;
                    last[0] = percent;
                    post(() -> updateProgress(read, total,
                            "Downloading • " + (total > 0 ? percent + "%" : read + " bytes")));
                });
                PayloadStore.verify(part, p.size, p.sha);
                PayloadStore.replace(part, target);
                String detail = describeLocal(p);
                post(() -> {
                    status.setText("Download saved and verified");
                    saveStatus("Download saved and verified");
                    log("Saved " + p.id + (p.sha.isEmpty() ? "; size checked, no reference SHA-256 in feed." : "; SHA-256 verified."));
                    log(detail.replace("\n", " | "));
                });
            } finally { part.delete(); }
        });
    }

    private void checkRoot() {
        final boolean autoKernel = preferences.getBoolean("auto_kernel", false);
        flowJob("Checking root access…", () -> {
            boolean granted = false;
            String result;
            try {
                result = Shell.suRootProbe(getCacheDir());
                granted = Shell.outputGrantsRoot(result);
            } catch (Exception e) { result = e.getMessage(); }
            final boolean ksuSeen = result != null && result.contains("KSU=loaded");
            final boolean ok = granted;
            final String detail = result;
            post(() -> {
                rootGranted = ok;
                if (ok && ksuSeen) {
                    ksuLoaded = true;
                    ksuWorking = true;
                } else {
                    ksuWorking = false;
                }
                saveVolatileState();
                rootStatus.setText(ok ? "Root: granted" : "Root: not granted");
                status.setText(ok ? "Root check passed" : "Root access not granted"
                        + (isPackageInstalled("moe.shizuku.manager") && !shizukuGranted
                        ? " — no su? Tap Authorize Shizuku below." : ""));
                saveStatus(status.getText().toString());
                if (ok) rootStatus.animate().scaleX(1.04f).scaleY(1.04f).setDuration(110)
                        .withEndAction(() -> rootStatus.animate().scaleX(1f).scaleY(1f)
                                .setDuration(130).start()).start();
                updateManager();
                log("Root check: " + detail);
                if (ok && ksuSeen) kernelStatus.setText("KernelSU: working");
                else if (!ok) kernelStatus.setText("Kernel module: root access needed to check");
            });
            if (ok) {
                Shell.Transport transport = new Shell.Su();
                // Root granted: if the module is already live, autocomplete the
                // KernelSU stage instead of re-running the loader.
                if (KernelSetup.isLoaded(transport, getCacheDir())) ksuLoaded = true;
                if (ksuLoaded && Shell.suGrantsRoot(getCacheDir())) ksuWorking = true;
                saveVolatileState();
                if (autoKernel && !ksuLoaded) {
                    setupKernel(transport);
                } else {
                    final boolean working = ksuWorking;
                    final boolean present = ksuLoaded;
                    post(() -> kernelStatus.setText(working ? "KernelSU: working"
                            : present ? "Kernel module: loaded, su not granted"
                            : "Kernel module: not loaded"));
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
        flowJob("Checking Shizuku shell…", () -> {
            if (!Shell.awaitShizukuRunning(this, 4000)) {
                post(() -> {
                    shizukuGranted = false;
                    saveVolatileState();
                    shizukuStatus.setText(isPackageInstalled("moe.shizuku.manager")
                            ? "Shizuku: service not connected"
                            : "Shizuku: not installed");
                    status.setText(Shell.describeShizukuState());
                    log(Shell.describeShizukuState());
                    updateFlow();
                    showSheet("Shizuku not connected", "Shizuku Manager is installed, but "
                            + "this app did not receive Shizuku's binder after waiting. Open "
                    + "Shizuku, confirm the service says Running, then return here. If "
                            + "S22 Updater is not listed in Shizuku's Apps screen, install the "
                            + "current build fresh so the provider permission is registered.",
                            "Open Shizuku", this::openShizuku, "Diagnose", this::diagnoseShizuku);
                });
                return;
            }
            if (Shell.shizukuGranted()) {
                post(() -> {
                    shizukuGranted = true;
                    saveVolatileState();
                    shizukuStatus.setText("Shizuku: authorized");
                    status.setText("Shizuku shell ready");
                    log("Shizuku shell ready");
                    updateFlow();
                });
                return;
            }
            post(() -> {
                shizukuStatus.setText("Shizuku: requesting authorization…");
                log("Requesting Shizuku authorization — approve it in Shizuku/Manager.");
            });
            Shell.requestShizukuPermission(new Shell.PermissionCallback() {
                @Override public void onResult(final boolean granted) {
                    post(() -> {
                        shizukuGranted = granted;
                        saveVolatileState();
                        shizukuStatus.setText(granted ? "Shizuku: authorized"
                                : "Shizuku: denied");
                        status.setText(granted ? "Shizuku shell ready"
                                : "Shizuku authorization denied");
                        log(granted ? "Shizuku authorized" : "Shizuku authorization denied");
                        updateFlow();
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
            adoptKsuSu(result);
        } catch (Exception e) {
            String hint = (transport instanceof Shell.ShizukuShell) && !rootGranted
                    ? " Loading needs real root (su) — a Shizuku shell cannot insmod. "
                      + "Grant root first, then retry."
                    : "";
            final String message = "KernelSU setup failed: " + e.getMessage() + hint;
            post(() -> kernelStatus.setText(message));
            throw new IOException(message, e);
        }
    }

    private void adoptKsuSu(String result) {
        ksuLoaded = true;
        boolean suNow = false;
        try {
            suNow = Shell.suGrantsRoot(getCacheDir());
        } catch (Exception ignored) {
        }
        if (suNow) rootGranted = true;
        ksuWorking = suNow;
        saveVolatileState();
        final boolean suReady = suNow;
        post(() -> {
            updateManager();
            kernelStatus.setText(result);
            status.setText(result);
            saveStatus(result);
            log(result);
            if (suReady) {
                rootStatus.setText("Root: granted (KernelSU su)");
                kernelStatus.setText("KernelSU: working");
                rootStatus.animate().scaleX(1.04f).scaleY(1.04f).setDuration(110)
                        .withEndAction(() -> rootStatus.animate().scaleX(1f).scaleY(1f)
                                .setDuration(130).start()).start();
                log("Privileged shell is now KernelSU su; bootstrap root stepped aside.");
            } else {
                log("KernelSU loaded, but su has not granted this app yet. Tap Check root once Manager grants it.");
            }
        });
    }

    private void updateManager() {
        String found = managerPackage();
        managerStatus.setText(found == null ? "Manager not detected" : "Manager installed\n" + found);
    }

    private boolean managerInstalled() {
        return managerPackage() != null;
    }

    private String managerPackage() {
        for (String id : MANAGERS) {
            try { getPackageManager().getPackageInfo(id, 0); return id; }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        }
        return null;
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
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        flowJob("Running exploit via " + transport.name() + "…", () -> {
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
                    shizukuStatus.setText("Shizuku: authorization lost");
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
                saveStatus("Exploit running — keep the phone idle…");
                log("Exploit started via " + transport.name() + ". Attempt budget 24, watchdog 15 min. Helper: " + helperPath);
                runAppend("$ LD_PRELOAD=" + EXPLOIT_DEVICE_PATH + " sh  (attempts=24)");
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
                    exploitRooted = true;
                    saveVolatileState();
                    status.setText("Exploit completed — root verified");
                    saveStatus("Exploit completed — root verified");
                    log("Exploit success marker seen; helper reports:\n" + probeText);
                    runAppend("[exploit completed] " + probeText.replace("\n", " | "));
                    promptKernelSu(transport, helper);
                } else {
                    status.setText("Exploit finished without root");
                    saveStatus("Exploit finished without root");
                    log("No success marker/root. Reboot for clean slabs, close apps, "
                            + "keep the screen unlocked and idle, then run again.");
                    runAppend("[no root] reboot for clean slabs, then run again");
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
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                post(() -> log("Helper download failed: " + message));
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
                final String snapshot = tail.length() > 1000 ? tail.substring(tail.length() - 1000) : tail;
                String lastLine = "";
                for (String line : snapshot.split("\n")) {
                    String clean = line.replaceAll("\u001B\\[[0-?]*[ -/]*[@-~]", "").trim();
                    if (!clean.isEmpty()) lastLine = clean;
                }
                if (lastLine.length() > 140) lastLine = "…" + lastLine.substring(lastLine.length() - 139);
                final String live = lastLine;
                post(() -> {
                    status.setText(live.isEmpty() ? "Exploit running…" : "Exploit: " + live);
                    saveStatus(status.getText().toString());
                    log("exploit: " + snapshot.replace("\n", " | "));
                    if (!live.isEmpty()) runAppend(live);
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
                flowJob("Setting up KernelSU…", () -> {
                    try {
                        String result = KernelSetup.load(Build.MODEL, transport,
                                getCacheDir(), helperPath);
                        adoptKsuSu(result);
                    } catch (Exception e) {
                        post(() -> kernelStatus.setText(
                                "KernelSU setup failed: " + e.getMessage()));
                        throw e;
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
            if (!AppUpdate.canRequestPackageInstalls(this)) {
                post(() -> {
                    appUpdateStatus.setText("S22 Updater " + info.versionName
                            + " available — install permission missing");
                    refreshInstallPermission();
                    showSheet("Allow app installs", "S22 Updater " + info.versionName
                            + " is available, but Android blocks this app from installing "
                            + "APKs right now. Enable 'Allow from this source' first — "
                            + "nothing was downloaded yet.",
                            "Open setting", () -> AppUpdate.openInstallPermission(MainActivity.this),
                            "Later", null);
                });
                return;
            }
            post(() -> {
                appUpdateStatus.setText("S22 Updater " + info.versionName + " available");
                status.setText("Downloading app update…");
                saveStatus("Downloading app update…");
                log("App update " + info.tag + " found, downloading " + info.apkName);
            });
            final File apk = AppUpdate.download(this, info, new Network.Progress() {
                @Override public void update(final long read, final long total) {
                    post(() -> updateProgress(read, total,
                            "Downloading app update • " + read + " bytes"));
                }
            });
            post(() -> {
                try {
                    preferences.edit().putString("last_app_update", info.tag).apply();
                } catch (Exception ignored) {
                }
                status.setText("App update downloaded");
                saveStatus("App update downloaded");
                log("App update verified, asking to install.");
            });
            final AppUpdate.SignatureReport signature = AppUpdate.checkInstallCompatibility(this, apk);
            if (!signature.compatible) {
                post(() -> showUpdateConflict(info, signature));
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
            if (!AppUpdate.canRequestPackageInstalls(MainActivity.this)) {
                appUpdateStatus.setText("Install permission was revoked — re-allow, then retry");
                refreshInstallPermission();
                AppUpdate.openInstallPermission(MainActivity.this);
                Toast.makeText(MainActivity.this, "Enable 'Allow from this source', then check again",
                        Toast.LENGTH_LONG).show();
                return;
            }
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
                + "update APK are signed by incompatible certificates. This is a "
                + "platform rule, not an updater bug, and it happens once per "
                + "signing-key change.\n\n"
                + "Installed signer: " + shortCert(signature.installedCert) + "\n"
                + "Update signer: " + shortCert(signature.updateCert) + "\n\n"
                + "Uninstall S22 Updater below, then install the current release "
                + "fresh. Builds signed by the same key after that update normally.";
        showSheet("Package conflict", body, "Uninstall old app", this::openUninstall,
                "Open releases", () -> AppUpdate.openReleases(MainActivity.this));
    }

    private void openUninstall() {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            openSelfInfo();
        }
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
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), border);
        return drawable;
    }
    private TextView chip(String value, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(12);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setTextColor(color);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setBackground(shape(blend(color, surface, .9f), 999));
        int icon = drawableId("ic_ms_security");
        if (icon != 0) {
            Drawable d = getResources().getDrawable(icon);
            d = d.mutate();
            d.setTint(color);
            view.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null);
            view.setCompoundDrawablePadding(dp(6));
        }
        return view;
    }
    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10); return params;
    }
    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout card = column();
        card.setBackground(shape(surface, 22));
        card.setElevation(dp(2));
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
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
        button.setElevation(primary ? dp(2) : 0);
        int fill = primary ? accent : blend(accent, bg, .88f);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(blend(accent, surface, .55f)),
                shape(fill, 14), null));
        applyIcon(button, label, primary);
        return button;
    }

    private void applyIcon(Button button, String label, boolean primary) {
        int icon = iconFor(label);
        if (icon == 0) {
            button.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            return;
        }
        try {
            Drawable drawable = getResources().getDrawable(icon).mutate();
            drawable.setTint(primary ? bg : accent);
            button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            button.setCompoundDrawablePadding(dp(8));
        } catch (Exception ignored) {
        }
    }

    private int iconFor(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        if (l.equals("home")) return drawableId("ic_ms_home");
        if (l.equals("log")) return drawableId("ic_ms_article");
        if (l.equals("settings")) return drawableId("ic_ms_settings");
        if (l.equals("system") || l.equals("blue") || l.equals("green")
                || l.equals("purple") || l.equals("orange"))
            return drawableId("ic_ms_palette");
        if (l.contains("theme") || l.contains("color")) return drawableId("ic_ms_palette");
        if (l.contains("changelog") || l.contains("change log")) return drawableId("ic_ms_article");
        if (l.contains("update") || l.contains("refresh") || l.contains("check for")
                || l.contains("restore"))
            return drawableId("ic_ms_sync");
        if (l.contains("uninstall")) return drawableId("ic_ms_close");
        if (l.contains("save") || l.contains("export")) return drawableId("ic_ms_save");
        if (l.contains("root") || l.contains("authorize") || l.contains("permission")
                || l.contains("allow") || l.contains("shizuku") || l.contains("manager"))
            return drawableId("ic_ms_security");
        if (l.contains("download") || l.contains("load") || l.contains("install"))
            return drawableId("ic_ms_download");
        if (l.contains("run") || l.contains("exploit")) return drawableId("ic_ms_play");
        if (l.contains("open") || l.contains("launch")) return drawableId("ic_ms_play");
        if (l.contains("copy") || l.contains("share")) return drawableId("ic_ms_share");
        if (l.contains("close") || l.equals("later")
                || l.equals("ok") || l.contains("cancel") || l.contains("dismiss"))
            return drawableId("ic_ms_close");
        return drawableId("ic_ms_settings");
    }
    private int drawableId(String name) { return getResources().getIdentifier(name, "drawable", getPackageName()); }
    private Button action(LinearLayout parent, String label, Runnable callback) {
        Button button = makeButton(label, true);
        button.setOnClickListener(v -> {
            v.animate().scaleX(.985f).scaleY(.985f).setDuration(70)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(110).start())
                    .start();
            callback.run();
        }); parent.addView(button, spaced()); actions.add(button); return button;
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("page", pageIndex);
        if (exportFile != null) state.putString("export_file", exportFile.getName());
    }
    @Override protected void onDestroy() {
        closed = true; ui.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy();
    }
}
