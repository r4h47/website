package com.maeen.carromaim;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 4101;
    private MediaProjectionManager projectionManager;
    private EditText playerId;
    private EditText intervalMs;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);

        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        sc.addView(root);

        TextView title = text("Carrom Data Logger", 28, Color.rgb(18, 58, 92));
        title.setGravity(Gravity.CENTER);
        root.addView(title, mw());

        TextView sub = text("Collect gameplay frames locally for offline analysis and detector development", 15, Color.DKGRAY);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(16));
        root.addView(sub, mw());

        playerId = new EditText(this);
        playerId.setHint("Player ID / local session label");
        playerId.setSingleLine(true);
        root.addView(playerId, mw());

        intervalMs = new EditText(this);
        intervalMs.setHint("Frame interval in ms (default 700)");
        intervalMs.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        intervalMs.setText("700");
        root.addView(intervalMs, top(8));

        Button start = button("Start data collection");
        start.setOnClickListener(v -> beginCapture());
        root.addView(start, top(12));

        Button stop = button("Stop data collection");
        stop.setOnClickListener(v -> {
            Intent s = new Intent(this, DataLoggerService.class);
            s.setAction(DataLoggerService.ACTION_STOP);
            startService(s);
            Toast.makeText(this, "Data collection stopped", Toast.LENGTH_SHORT).show();
            refreshStatus();
        });
        root.addView(stop, top(8));

        status = text("", 14, Color.DKGRAY);
        status.setPadding(dp(10), dp(12), dp(10), dp(12));
        root.addView(status, top(12));

        TextView info = text(
                "What it stores\n\n" +
                "• sampled full-screen JPEG frames\n" +
                "• frame timestamps and elapsed time\n" +
                "• display width/height\n" +
                "• session start/end metadata\n\n" +
                "The app does not draw aim lines, automate input, read game memory, or intercept network traffic. " +
                "It only records frames after you approve Android screen capture.\n\n" +
                "For best results, record 2–3 complete matches and include several of your turns where you move the striker, aim, and shoot. " +
                "Then copy the generated session folder to a ZIP and upload it here.",
                15, Color.rgb(45,45,45));
        info.setPadding(0, dp(16), 0, 0);
        info.setLineSpacing(0, 1.12f);
        root.addView(info, mw());

        setContentView(sc);
        refreshStatus();
    }

    @Override protected void onResume() { super.onResume(); refreshStatus(); }

    private void beginCapture() {
        String id = playerId.getText().toString().trim();
        if (id.isEmpty()) {
            Toast.makeText(this, "Enter a Player ID or session label", Toast.LENGTH_LONG).show();
            return;
        }
        long interval = 700;
        try { interval = Long.parseLong(intervalMs.getText().toString().trim()); } catch (Exception ignored) {}
        interval = Math.max(250, Math.min(3000, interval));
        intervalMs.setText(String.valueOf(interval));
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Screen capture permission is required", Toast.LENGTH_LONG).show();
            return;
        }
        long interval = 700;
        try { interval = Long.parseLong(intervalMs.getText().toString().trim()); } catch (Exception ignored) {}
        Intent svc = new Intent(this, DataLoggerService.class);
        svc.putExtra(DataLoggerService.EXTRA_RESULT_CODE, resultCode);
        svc.putExtra(DataLoggerService.EXTRA_RESULT_DATA, data);
        svc.putExtra(DataLoggerService.EXTRA_PLAYER_ID, playerId.getText().toString().trim());
        svc.putExtra(DataLoggerService.EXTRA_INTERVAL_MS, interval);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        Toast.makeText(this, "Recording started. Open Carrom Pool and play normally.", Toast.LENGTH_LONG).show();
        refreshStatus();
    }

    private void refreshStatus() {
        File root = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File logger = new File(root, "CarromLogger");
        status.setText("Saved locally under:\n" + logger.getAbsolutePath() +
                "\n\nEach session contains frames/ + metadata.csv + README.txt");
    }

    private TextView text(String s, float sp, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(16); b.setMinHeight(dp(50)); return b; }
    private LinearLayout.LayoutParams mw() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams top(int x) { LinearLayout.LayoutParams lp = mw(); lp.topMargin = dp(x); return lp; }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
}
