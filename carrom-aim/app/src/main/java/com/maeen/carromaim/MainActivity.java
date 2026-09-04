package com.maeen.carromaim;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        TextView title = new TextView(this);
        title.setText("Carrom Aim Overlay");
        title.setTextSize(28f);
        title.setTextColor(Color.rgb(16, 52, 91));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Pocket geometry guide for private/offline practice\nStandalone companion — does not modify the game APK");
        subtitle.setTextSize(15f);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(20));
        root.addView(subtitle, matchWrap());

        status = new TextView(this);
        status.setTextSize(15f);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(status, matchWrap());

        Button permissionButton = makeButton("1. Grant overlay permission");
        permissionButton.setOnClickListener(v -> requestOverlayPermission());
        root.addView(permissionButton, matchWrapWithTop(12));

        Button startButton = makeButton("2. Start aim overlay");
        startButton.setOnClickListener(v -> startOverlay());
        root.addView(startButton, matchWrapWithTop(10));

        Button stopButton = makeButton("Stop overlay");
        stopButton.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        root.addView(stopButton, matchWrapWithTop(10));

        TextView guide = new TextView(this);
        guide.setText(
                "How to use\n\n" +
                "1) Start the overlay, then open Carrom Disc Pool.\n" +
                "2) In EDIT mode, drag P1–P4 to the four pocket centers. You only need to calibrate once.\n" +
                "3) Drag S onto your striker and T onto the disc you want to pocket.\n" +
                "4) Tap the pocket button to cycle P1 → P2 → P3 → P4.\n" +
                "5) The GREEN line is the target-disc path to the pocket.\n" +
                "6) The YELLOW line is the striker aim line toward the ghost-contact point.\n" +
                "7) Tap PLAY. The drawing stays visible, but touches pass through to the game.\n" +
                "8) Tap EDIT on the floating control bar whenever you need to move the markers.\n\n" +
                "This version is a manual geometric practice overlay. It does not automatically detect coins, read game memory, inject input, or alter the game."
        );
        guide.setTextSize(15f);
        guide.setTextColor(Color.rgb(45,45,45));
        guide.setLineSpacing(0f, 1.15f);
        guide.setPadding(0, dp(24), 0, 0);
        root.addView(guide, matchWrap());

        setContentView(scroll);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show();
            refreshStatus();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        Toast.makeText(this, "Overlay started. Open the game now.", Toast.LENGTH_LONG).show();
    }

    private void refreshStatus() {
        boolean granted = Settings.canDrawOverlays(this);
        status.setText(granted ? "Overlay permission: GRANTED" : "Overlay permission: NOT GRANTED");
        status.setTextColor(granted ? Color.rgb(0, 120, 70) : Color.rgb(190, 55, 30));
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16f);
        b.setAllCaps(false);
        b.setMinHeight(dp(52));
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithTop(int topDp) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(topDp);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
