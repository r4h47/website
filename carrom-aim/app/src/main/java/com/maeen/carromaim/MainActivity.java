package com.maeen.carromaim;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

        TextView title = text("Carrom Aim Vision v2.1", 28, Color.rgb(11, 52, 91));
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = text(
                "Visual-only board/coin/striker detection + trajectory heat guide\n" +
                "No Accessibility service and no automatic input control", 15, Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle, matchWrap());

        status = text("", 15, Color.DKGRAY);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(status, matchWrap());

        Button overlay = button("1. Grant display-over-apps permission");
        overlay.setOnClickListener(v -> requestOverlayPermission());
        root.addView(overlay, top(12));

        Button start = button("2. Start visual aim + open Carrom Pool");
        start.setOnClickListener(v -> beginCapture());
        root.addView(start, top(8));

        Button stop = button("Stop visual aim");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, AutoAimService.class));
            Toast.makeText(this, "Visual aim stopped", Toast.LENGTH_SHORT).show();
        });
        root.addView(stop, top(8));

        TextView info = text(
                "How v2.1 works\n\n" +
                "• You explicitly approve Android screen capture each time you start the detector.\n" +
                "• Frames are processed locally in memory and are not uploaded or saved.\n" +
                "• The app looks for a strong carrom-board visual signature before showing the overlay.\n" +
                "• It estimates pockets, black/white discs and the lower-baseline striker, then ranks direct-pot trajectories.\n" +
                "• Multiple translucent green/yellow/orange/red lines form the heat-style guide.\n" +
                "• The overlay is hidden when the captured frame does not look like an active carrom board.\n\n" +
                "This build intentionally removes the Accessibility service that caused Android to classify the earlier build as requesting sensitive access.",
                15, Color.rgb(45,45,45));
        info.setPadding(0, dp(22), 0, 0);
        info.setLineSpacing(0, 1.12f);
        root.addView(info, matchWrap());

        setContentView(scroll);
        refreshStatus();
    }

    @Override protected void onResume() { super.onResume(); refreshStatus(); }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return;
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }

    private void beginCapture() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant display-over-apps permission first", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
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
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.miniclip.carrom");
            if (launch != null) startActivity(launch);
            else Toast.makeText(this, "Carrom Pool package not found", Toast.LENGTH_LONG).show();
        }, 650);
    }

    private void refreshStatus() {
        boolean o = Settings.canDrawOverlays(this);
        status.setText("Display-over-apps permission: " + (o ? "READY" : "NOT GRANTED") +
                "\nSensitive Accessibility access: NOT USED");
        status.setTextColor(o ? Color.rgb(0,125,70) : Color.rgb(180,60,30));
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
