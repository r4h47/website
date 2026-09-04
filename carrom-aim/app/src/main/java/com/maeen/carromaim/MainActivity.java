package com.maeen.carromaim;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 2101;
    private TextView status;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = text("Carrom Auto Aim v2", 28, Color.rgb(11, 52, 91));
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = text(
                "Automatic board/coin/striker detection + multi-line shot heat guide\n" +
                "Only displays while Carrom Pool is foreground", 15, Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle, matchWrap());

        status = text("", 15, Color.DKGRAY);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(status, matchWrap());

        Button overlay = button("1. Grant display-over-apps permission");
        overlay.setOnClickListener(v -> requestOverlayPermission());
        root.addView(overlay, top(12));

        Button access = button("2. Enable Carrom game detector");
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(access, top(8));

        Button start = button("3. Start automatic aim + open Carrom Pool");
        start.setOnClickListener(v -> beginCapture());
        root.addView(start, top(8));

        Button stop = button("Stop automatic aim");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, AutoAimService.class));
            Toast.makeText(this, "Auto aim stopped", Toast.LENGTH_SHORT).show();
        });
        root.addView(stop, top(8));

        TextView info = text(
                "How v2 works\n\n" +
                "• The accessibility service reads only the foreground app package name. It does not inspect or click game UI.\n" +
                "• Android screen-capture permission supplies frames locally to the detector. No screenshot is uploaded or saved.\n" +
                "• The detector estimates the board from corner pockets, detects black/white discs, infers your colour from the local-player HUD, and tracks the striker near the lower baseline.\n" +
                "• Up to six feasible direct-pot trajectories are ranked. Green is the best geometric option, then yellow/orange/red.\n" +
                "• Each candidate shows striker → ghost-contact and coin → pocket. The lines update as the striker moves.\n\n" +
                "If AUTO cannot infer your disc colour in a particular arena/theme, it still highlights detected discs and continues to search on later frames.",
                15, Color.rgb(45,45,45));
        info.setPadding(0, dp(22), 0, 0);
        info.setLineSpacing(0, 1.12f);
        root.addView(info, matchWrap());

        setContentView(scroll);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return;
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }

    private boolean accessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName mine = new ComponentName(this, GameWatchService.class);
        String flat = mine.flattenToString();
        String shortFlat = mine.flattenToShortString();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String s : splitter) {
            if (flat.equalsIgnoreCase(s) || shortFlat.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    private void beginCapture() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant display-over-apps permission first", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        if (!accessibilityEnabled()) {
            Toast.makeText(this, "Enable Carrom game detector first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Screen capture permission is required", Toast.LENGTH_LONG).show();
            return;
        }
        Intent svc = new Intent(this, AutoAimService.class);
        svc.putExtra(AutoAimService.EXTRA_RESULT_CODE, resultCode);
        svc.putExtra(AutoAimService.EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);

        getWindow().getDecorView().postDelayed(() -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(GameWatchService.TARGET_PACKAGE);
            if (launch != null) startActivity(launch);
            else Toast.makeText(this, "Carrom Pool package not found", Toast.LENGTH_LONG).show();
        }, 650);
    }

    private void refreshStatus() {
        boolean o = Settings.canDrawOverlays(this);
        boolean a = accessibilityEnabled();
        status.setText("Overlay permission: " + (o ? "READY" : "NOT GRANTED") +
                "\nGame detector: " + (a ? "READY" : "NOT ENABLED"));
        status.setTextColor(o && a ? Color.rgb(0,125,70) : Color.rgb(180,60,30));
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextSize(16); b.setAllCaps(false); b.setMinHeight(dp(52));
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams top(int dpTop) {
        LinearLayout.LayoutParams lp = matchWrap(); lp.topMargin = dp(dpTop); return lp;
    }

    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
}
