package com.pinvault.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** PIN screen: creates a PIN on first launch, then asks for it every time the vault opens. */
public class PinActivity extends Activity {
    static final String EXTRA_CHANGE = "change_pin";
    private static final int MIN_LEN = 4, MAX_LEN = 8;

    private final StringBuilder entry = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView title, dots, hint;
    private boolean settingPin;
    private String firstPin;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // Hide contents from screenshots and the recent-apps preview
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        settingPin = !Prefs.hasPin(this) || getIntent().getBooleanExtra(EXTRA_CHANGE, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 24);
        root.setPadding(pad, pad, pad, pad);

        title = Ui.text(this, "", 22, Ui.FG, true);
        dots = Ui.text(this, "", 30, Ui.ACCENT, true);
        dots.setLetterSpacing(0.35f);
        dots.setMinHeight(Ui.dp(this, 56));
        dots.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
        hint = Ui.text(this, "", 14, Ui.MUTED, false);
        hint.setMinHeight(Ui.dp(this, 40));

        root.addView(title);
        root.addView(dots);
        root.addView(hint);
        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        padLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(buildKeypad(), padLp);
        setContentView(root);

        showPrompt(null);
        tickLockout();
    }

    private GridLayout buildKeypad() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(0, Ui.dp(this, 16), 0, 0);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK"};
        int size = Ui.dp(this, 76), margin = Ui.dp(this, 8);
        for (String k : keys) {
            TextView key = Ui.text(this, k, k.equals("OK") ? 18 : 26,
                    k.equals("OK") ? Ui.ACCENT : Ui.FG, k.equals("OK"));
            key.setBackground(Ui.round(Ui.SURFACE, size / 2f));
            key.setClickable(true);
            key.setOnClickListener(v -> press(k));
            if (k.equals("⌫")) {
                key.setOnLongClickListener(v -> { entry.setLength(0); updateDots(); return true; });
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height =
