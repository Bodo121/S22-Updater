package com.bodo121.s22updater;

import android.app.Activity;
import android.app.AlertDialog;
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
    private TextView status, local, remote, rootStatus, managerStatus, kernelStatus, activityLog, changelog;
    private ProgressBar progress;
    private LinearLayout choices;
    private EditText feedInput;
    private Button download, deploy, export, loadKernel;
    private Switch automaticKernel;
    private SharedPreferences preferences;
    private Payload selected;
    private File exportFile;
    private boolean busy, rootGranted;
    private volatile boolean closed;
    private int bg, surface, ink, muted, accent, border, pageIndex;

    private static final class Payload {
        final String id, name, url, sha;
        final long size;
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
        text(header, "IONSTACK • Version 3.0", 12, muted, false);
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
        log("Ready. Feed checks and root requests run when you tap their buttons.");
    }

    private void buildHome() {
        LinearLayout device = card(pages[0], "DEVICE");
        text(device, Build.MODEL, 23, ink, true);
        text(device, "Android " + Build.VERSION.RELEASE + " • " + Build.DISPLAY, 13, muted, false);
        LinearLayout root = card(pages[0], "ROOT ACCESS");
        rootStatus = text(root, "Not checked", 20, ink, true);
        text(root, "Check whether this app has superuser access. Approve the request in your root manager.", 14, muted, false);
        action(root, "Check root access", this::checkRoot);
        LinearLayout manager = card(pages[0], "KERNELSU");
        managerStatus = text(manager, "Checking manager…", 18, ink, true);
        kernelStatus = text(manager, "Kernel module: check root first", 14, muted, false);
        action(manager, "Open KernelSU Manager", this::openManager);
        loadKernel = action(manager, "Load matching KernelSU module", () -> job("Setting up KernelSU…", this::setupKernel));
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
        export = action(detail, "Export downloaded file", this::export);
        text(detail, "Download saves a private copy. Root install copies it to /data/local/tmp/cve-2026-43499 and verifies its hash. It does not run the exploit or activate KernelSU.", 13, muted, false);
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
        text(about, "S22 Updater 3.0", 20, ink, true);
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
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
        boolean hasFile = false;
        try { hasFile = selected != null && localFile(selected).isFile(); } catch (Exception ignored) { }
        download.setEnabled(!busy && selected != null);
        deploy.setEnabled(!busy && hasFile && rootGranted);
        export.setEnabled(!busy && hasFile);
        loadKernel.setEnabled(!busy && rootGranted);
        for (Button b : new Button[]{download, deploy, export, loadKernel}) b.setAlpha(b.isEnabled() ? 1f : .45f);
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
                result = RootShell.run("id", getCacheDir());
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
                if (autoKernel) setupKernel();
                else {
                    String loaded = KernelSetup.status(getCacheDir());
                    post(() -> kernelStatus.setText("loaded".equals(loaded) ? "Kernel module: loaded" : "Kernel module: not loaded"));
                }
            }
        });
    }

    private void setupKernel() throws Exception {
        try {
            String result = KernelSetup.load(Build.MODEL, getCacheDir());
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
        new AlertDialog.Builder(this).setTitle("Manager not found")
                .setMessage("Install your matching KernelSU Manager APK first, then return here.")
                .setPositiveButton("OK", null).show();
    }

    private void deploy() {
        final Payload p = selected;
        if (p == null || !rootGranted) return;
        job("Installing downloaded file…", () -> {
            File file = localFile(p);
            String hash = PayloadStore.verify(file, p.size, p.sha);
            String destination = "/data/local/tmp/cve-2026-43499";
            String temp = destination + ".s22-update";
            String qTemp = RootShell.quote(temp);
            String command = "set -e; trap 'rm -f " + qTemp + "' EXIT; cp " + RootShell.quote(file.getAbsolutePath())
                    + " " + qTemp + "; chmod 755 " + qTemp
                    + "; actual=$(sha256sum " + qTemp + "); [ \"${actual%% *}\" = " + RootShell.quote(hash)
                    + " ]; mv -f " + qTemp + " " + RootShell.quote(destination);
            RootShell.run(command, getCacheDir());
            post(() -> { status.setText("File installed and verified"); log("Installed " + p.id + " to " + destination + ". Execution was not started."); });
        });
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
