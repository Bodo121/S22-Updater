package com.bodo121.s22updater;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Compact Material-style vertical stepper for the one-button Home flow. */
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
        setMinimumHeight(dp(224));
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
        int min = dp(224);
        int height = resolveSize(min, heightSpec);
        setMeasuredDimension(resolveSize(dp(280), widthSpec), Math.max(min, height));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int left = dp(24);
        int top = dp(18);
        int gap = Math.max(dp(34), (getHeight() - dp(36)) / Math.max(1, labels.length - 1));
        int textLeft = dp(58);
        int radius = dp(11);
        paint.setStrokeWidth(dp(2));
        for (int i = 0; i < labels.length - 1; i++) {
            paint.setColor(done[i] && done[i + 1] ? success : blend(muted, surface, .52f));
            canvas.drawLine(left, top + i * gap + radius + dp(2), left,
                    top + (i + 1) * gap - radius - dp(2), paint);
        }
        for (int i = 0; i < labels.length; i++) {
            int y = top + i * gap;
            boolean isFailed = i == failed;
            boolean isActive = i == active && !done[i] && failed < 0;
            int color = isFailed ? danger : done[i] ? success : isActive ? accent : muted;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blend(color, surface, done[i] || isActive || isFailed ? .82f : .92f));
            canvas.drawCircle(left, y, isActive ? radius * pulse : radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(color);
            canvas.drawCircle(left, y, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(dp(2));
            if (done[i]) drawCheck(canvas, left, y, color);
            else if (isFailed) drawCross(canvas, left, y, color);
            else {
                paint.setColor(color);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(11));
                paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                canvas.drawText(String.valueOf(i + 1), left, y + dp(4), paint);
            }
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(dp(isActive || done[i] ? 15 : 14));
            paint.setTypeface(Typeface.create("sans-serif", isActive || done[i] ? Typeface.BOLD : Typeface.NORMAL));
            paint.setColor(isActive || done[i] || isFailed ? ink : muted);
            canvas.drawText(labels[i], textLeft, y + dp(5), paint);
            if (isActive || isFailed) {
                paint.setTextSize(dp(11));
                paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                paint.setColor(isFailed ? danger : accent);
                canvas.drawText(isFailed ? "Needs retry" : "In progress", textLeft, y + dp(21), paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCheck(Canvas c, int x, int y, int color) {
        paint.setColor(color);
        paint.setStrokeWidth(dp(2));
        c.drawLine(x - dp(5), y, x - dp(1), y + dp(4), paint);
        c.drawLine(x - dp(1), y + dp(4), x + dp(6), y - dp(5), paint);
    }

    private void drawCross(Canvas c, int x, int y, int color) {
        paint.setColor(color);
        paint.setStrokeWidth(dp(2));
        c.drawLine(x - dp(5), y - dp(5), x + dp(5), y + dp(5), paint);
        c.drawLine(x + dp(5), y - dp(5), x - dp(5), y + dp(5), paint);
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
