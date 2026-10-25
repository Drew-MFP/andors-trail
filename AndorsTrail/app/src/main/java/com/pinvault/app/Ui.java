package com.pinvault.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

/** Shared colors and small view helpers (the app builds its screens in code). */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF12131A;
    static final int SURFACE = 0xFF1E2030;
    static final int FG = 0xFFF2F3F8;
    static final int MUTED = 0xFF9A9DB0;
    static final int ACCENT = 0xFF8C93FF;
    static final int ERROR = 0xFFFF7A7A;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }
}
