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
        private void reload() {
        PackageManager pm = getPackageManager();
        Set<String> saved = Prefs.getApps(this);
        Set<String> stillInstalled = new java.util.HashSet<>();
        items.clear();
        for (String pkg : saved) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                items.add(new AppItem(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai)));
                stillInstalled.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
                // App was uninstalled; drop it from the vault
            }
        }
        if (stillInstalled.size() != saved.size()) Prefs.setApps(this, stillInstalled);
        Collections.sort(items, (a, b) -> a.label.compareToIgnoreCase(b.label));
        adapter.notifyDataSetChanged();
    }

    private void launch(AppItem item) {
        Intent i = getPackageManager().getLaunchIntentForPackage(item.pkg);
        if (i == null) {
            Toast.makeText(this, "Can't open " + item.label, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(i);
    }

    private void confirmRemove(AppItem item) {
        if (dialogShowing()) return;
        openDialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Remove " + item.label + "?")
                .setMessage("It will be removed from the vault only, not uninstalled.")
                .setPositiveButton("Remove", (d, w) -> {
                    d.dismiss();
                    Set<String> apps = Prefs.getApps(this);
                    apps.remove(item.pkg);
                    Prefs.setApps(this, apps);
                    reload();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddDialog() {
        if (dialogShowing()) return;
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
        Set<String> current = Prefs.getApps(this);

        Map<String, String> choices = new LinkedHashMap<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || current.contains(pkg) || choices.containsKey(pkg)) continue;
            choices.put(pkg, ri.loadLabel(pm).toString());
        }
        if (choices.isEmpty()) {
            Toast.makeText(this, "No more apps to add", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Map.Entry<String, String>> sorted = new ArrayList<>(choices.entrySet());
        Collections.sort(sorted, (a, b) -> a.getValue().compareToIgnoreCase(b.getValue()));
        String[] labels = new String[sorted.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = sorted.get(i).getValue();
        boolean[] checked = new boolean[labels.length];

        openDialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add apps to vault")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Add", (d, w) -> {
                    d.dismiss();
                    Set<String> apps = Prefs.getApps(this);
                    for (int i = 0; i < checked.length; i++) if (checked[i]) apps.add(sorted.get(i).getKey());
                    Prefs.setApps(this, apps);
                    reload();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Change PIN");
        menu.getMenu().add(0, 2, 1, "Lock now");
        menu.setOnMenuItemClickListener(mi -> {
            if (mi.getItemId() == 1) {
                startActivity(new Intent(this, PinActivity.class).putExtra(PinActivity.EXTRA_CHANGE, true));
            }
            finish();
            return true;
        });
        menu.show();
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int pos) { return items.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convert == null) {
                cell = new LinearLayout(VaultActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(0, Ui.dp(VaultActivity.this, 6), 0, Ui.dp(VaultActivity.this, 6));
                icon = new ImageView(VaultActivity.this);
                int s = Ui.dp(VaultActivity.this, 54);
                cell.addView(icon, new LinearLayout.LayoutParams(s, s));
                label = Ui.text(VaultActivity.this, "", 12, Ui.FG, false);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(Ui.dp(VaultActivity.this, 4), Ui.dp(VaultActivity.this, 6),
                        Ui.dp(VaultActivity.this, 4), 0);
                cell.addView(label, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                cell = (LinearLayout) convert;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            }
            AppItem item = items.get(pos);
            icon.setImageDrawable(item.icon);
            label.setText(item.label);
            return cell;
        }
    }
                    }
