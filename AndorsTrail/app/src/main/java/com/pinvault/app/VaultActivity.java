package com.pinvault.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The unlocked vault: a grid of the apps you've added. Closes (re-locks) as soon as you leave it. */
public class VaultActivity extends Activity {

    private static class AppItem {
        final String pkg; final String label; final Drawable icon;
        AppItem(String pkg, String label, Drawable icon) { this.pkg = pkg; this.label = label; this.icon = icon; }
    }

    private final List<AppItem> items = new ArrayList<>();
    private AppAdapter adapter;
    private AlertDialog openDialog;

    /** True if a popup is already showing, so a double-tap can't stack a second copy. */
    private boolean dialogShowing() {
        return openDialog != null && openDialog.isShowing();
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        // Top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 16);
        bar.setPadding(p + Ui.dp(this, 4), p, Ui.dp(this, 8), p);
        TextView title = Ui.text(this, "Vault", 24, Ui.FG, true);
        title.setGravity(Gravity.START);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView add = barButton("+", 28);
        add.setOnClickListener(v -> showAddDialog());
        bar.addView(add);
        TextView more = barButton("⋮", 24);
        more.setOnClickListener(this::showMenu);
        bar.addView(more);
        root.addView(bar);

        // App grid + empty message
        FrameLayout body = new FrameLayout(this);
        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Ui.dp(this, 84));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.dp(this, 18));
        grid.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 24));
        grid.setClipToPadding(false);
        grid.setSelector(android.R.color.transparent);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> launch(items.get(pos)));
        grid.setOnItemLongClickListener((parent, view, pos, id) -> { confirmRemove(items.get(pos)); return true; });

        TextView empty = Ui.text(this, "Your vault is empty.\nTap + to add apps.", 16, Ui.MUTED, false);
        body.addView(grid);
        body.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        grid.setEmptyView(empty);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private TextView barButton(String s, float sp) {
        TextView b = Ui.text(this, s, sp, Ui.FG, false);
        int size = Ui.dp(this, 48);
        b.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        b.setClickable(true);
        b.setBackground(Ui.round(Ui.SURFACE, size / 2f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = Ui.dp(this, 8);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    // Leaving the vault (opening an app, going home, screen off) closes it, so the PIN is needed again.
    @Override
    protected void onStop() {
        super.onStop();
        if (dialogShowing()) openDialog.dismiss();
        if (!isChangingConfigurations()) finish();
          }
