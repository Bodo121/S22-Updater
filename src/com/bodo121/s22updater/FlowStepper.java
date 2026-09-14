package com.bodo121.s22updater;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/** Compact six-stage segmented strip matching the React design. */
final class FlowStepper extends View {
    private final String[] labels = {
            "Check feed", "Download", "Root", "Exploit", "KernelSU", "Manager"};
    private final boolean[] done = new boolean[labels.length];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int active = 0;
    private int failed = -1;
    private int ink = Color.BLACK, muted = Color.GRAY, accent = Color.BLUE,
            success = 0xff2e7d32, danger = 0xffb3261e, surface = Color.WHITE;
    private float pulse = 1f;

    FlowStepper(Context context) {
        super(context);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        setMinimumHeight(dp(86));
    }

    void setColors(int ink, int muted, int accent, int success, int danger, int surface) {
        this.ink = ink;
        this.muted = muted;
        this.accent = accent;
        this.success = success;
        this.danger = danger;
        this.surface = surface;
        invalidate();
    }

    void setState(boolean feed, boolean payload, boolean transport, boolean exploit,
                  boolean ksu, boolean manager, int active, int failed) {
        done[0] = feed;
        done[1] = payload;
        done[2] = transport;
        done[3] = exploit;
        done[4] = ksu;
        done[5] = manager;
        this.active = Math.max(0, Math.min(labels.length - 1, active));
        this.failed = failed;
        invalidate();
    }

    void pulse() {
        animate().scaleX(1.01f).scaleY(1.01f).setDuration(180)
                .withEndAction(() -> animate().scaleX(1f).scaleY(1f).setDuration(220).start())
                .start();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int min = dp(86);
        int height = resolveSize(min, heightSpec);
        setMeasuredDimension(resolveSize(dp(280), widthSpec), Math.max(min, height));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth() - dp(8);
        int left = dp(4);
        int segmentGap = dp(4);
        int segmentWidth = Math.max(dp(22), (width - segmentGap * (labels.length - 1)) / labels.length);
        int top = dp(12);
        int barHeight = dp(5);
        for (int i = 0; i < labels.length; i++) {
            int x = left + i * (segmentWidth + segmentGap);
            boolean isFailed = i == failed;
            boolean isActive = i == active && !done[i] && failed < 0;
            int color = isFailed ? danger : done[i] ? success : isActive ? accent : muted;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(done[i] ? blend(color, surface, .45f) : isActive || isFailed ? color : blend(muted, surface, .62f));
            canvas.drawRoundRect(x, top, x + segmentWidth, top + barHeight, dp(3), dp(3), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(10));
            paint.setTypeface(Typeface.create("sans-serif", isActive || done[i] ? Typeface.BOLD : Typeface.NORMAL));
            paint.setColor(isActive || done[i] || isFailed ? ink : muted);
            canvas.drawText(shortLabel(labels[i]), x + segmentWidth / 2f, top + dp(28), paint);
            if (isActive || isFailed) {
                paint.setTextSize(dp(11));
                paint.setTypeface(Typeface.MONOSPACE);
                paint.setColor(isFailed ? danger : accent);
                canvas.drawText(isFailed ? "retry" : "active", x + segmentWidth / 2f, top + dp(45), paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private String shortLabel(String label) {
        if (label.equals("Check feed")) return "Feed";
        if (label.equals("KernelSU")) return "KSU";
        return label;
    }

    private int blend(int from, int to, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        return Color.rgb((int) (Color.red(from) * (1f - a) + Color.red(to) * a),
                (int) (Color.green(from) * (1f - a) + Color.green(to) * a),
                (int) (Color.blue(from) * (1f - a) + Color.blue(to) * a));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
