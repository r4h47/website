package com.maeen.carromaim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

public class OverlayService extends Service {
    private static final String CHANNEL_ID = "carrom_aim_overlay";
    private static final int NOTIFICATION_ID = 41;
    private WindowManager wm;
    private AimOverlayView aimView;
    private LinearLayout controlBar;
    private WindowManager.LayoutParams aimParams;
    private Button editPlayButton;
    private Button pocketButton;
    private boolean editMode = true;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        addAimLayer();
        addControlBar();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    private void addAimLayer() {
        aimView = new AimOverlayView(this);
        aimParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        aimParams.gravity = Gravity.TOP | Gravity.START;
        wm.addView(aimView, aimParams);
    }

    private void addControlBar() {
        controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setPadding(dp(4), dp(4), dp(4), dp(4));
        controlBar.setBackgroundColor(0xCC102F50);

        editPlayButton = smallButton("PLAY");
        pocketButton = smallButton("P1");
        Button resetButton = smallButton("RESET");
        Button closeButton = smallButton("X");
        controlBar.addView(editPlayButton);
        controlBar.addView(pocketButton);
        controlBar.addView(resetButton);
        controlBar.addView(closeButton);

        editPlayButton.setOnClickListener(v -> setEditMode(!editMode));
        pocketButton.setOnClickListener(v -> pocketButton.setText("P" + aimView.nextPocket()));
        resetButton.setOnClickListener(v -> { aimView.resetAll(); pocketButton.setText("P1"); });
        closeButton.setOnClickListener(v -> stopSelf());

        WindowManager.LayoutParams controlParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        controlParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        controlParams.y = dp(32);
        wm.addView(controlBar, controlParams);
    }

    private void setEditMode(boolean enabled) {
        editMode = enabled;
        aimView.setEditMode(enabled);
        editPlayButton.setText(enabled ? "PLAY" : "EDIT");
        if (enabled) aimParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else aimParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.updateViewLayout(aimView, aimParams);
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12f);
        b.setAllCaps(false);
        b.setMinWidth(dp(58));
        b.setMinHeight(dp(42));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Carrom aim overlay", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the practice aim guide visible over the game");
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("Carrom Aim Overlay active")
                .setContentText("EDIT positions markers; PLAY makes the guide touch-through")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wm != null) {
            if (aimView != null) try { wm.removeView(aimView); } catch (Exception ignored) {}
            if (controlBar != null) try { wm.removeView(controlBar); } catch (Exception ignored) {}
        }
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
