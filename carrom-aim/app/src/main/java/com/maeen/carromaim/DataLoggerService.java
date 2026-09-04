package com.maeen.carromaim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataLoggerService extends Service {
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";
    public static final String EXTRA_PLAYER_ID = "playerId";
    public static final String EXTRA_INTERVAL_MS = "intervalMs";
    public static final String ACTION_STOP = "com.maeen.carromlogger.STOP";

    private static final String CHANNEL = "carrom_data_logger";
    private static final int NOTIFY_ID = 71;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private final Handler main = new Handler(Looper.getMainLooper());

    private Bitmap rowBitmap;
    private int rowBitmapW = 0, rowBitmapH = 0;
    private long lastSavedMs = 0;
    private long intervalMs = 700;
    private long frameIndex = 0;

    private File sessionDir;
    private File framesDir;
    private File metadataFile;
    private BufferedWriter metadataWriter;
    private String playerId = "unknown";
    private long sessionStartMs;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFY_ID, notification("Waiting for screen-capture permission"));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection == null) {
            playerId = clean(intent.getStringExtra(EXTRA_PLAYER_ID));
            intervalMs = Math.max(250, Math.min(3000, intent.getLongExtra(EXTRA_INTERVAL_MS, 700)));
            int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (code != 0 && data != null) startProjection(code, data);
        }
        return START_NOT_STICKY;
    }

    private void startProjection(int resultCode, Intent data) {
        try {
            prepareSession();
            MediaProjectionManager m = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = m.getMediaProjection(resultCode, data);
            if (projection == null) { stopSelf(); return; }
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){ stopSelf(); }
            }, main);

            WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            int w = dm.widthPixels, h = dm.heightPixels, density = dm.densityDpi;

            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            captureThread = new HandlerThread("carrom-data-capture");
            captureThread.start();
            captureHandler = new Handler(captureThread.getLooper());
            reader.setOnImageAvailableListener(this::onImage, captureHandler);
            virtualDisplay = projection.createVirtualDisplay(
                    "CarromDataLogger", w, h, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, captureHandler);

            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                    NOTIFY_ID, notification("Recording frames locally • " + playerId));
            writeMeta("session_start", 0, w, h, "interval_ms=" + intervalMs);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void prepareSession() throws Exception {
        sessionStartMs = System.currentTimeMillis();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(sessionStartMs));
        File root = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        sessionDir = new File(root, "CarromLogger/" + stamp + "_" + playerId);
        framesDir = new File(sessionDir, "frames");
        if (!framesDir.mkdirs() && !framesDir.exists()) throw new Exception("Cannot create session folder");
        metadataFile = new File(sessionDir, "metadata.csv");
        metadataWriter = new BufferedWriter(new FileWriter(metadataFile, false));
        metadataWriter.write("frame_index,timestamp_ms,elapsed_ms,width,height,event,note,filename\n");
        metadataWriter.flush();
        File info = new File(sessionDir, "README.txt");
        try (FileOutputStream fos = new FileOutputStream(info)) {
            String text = "Carrom Data Logger session\nPlayer/profile: " + playerId + "\nStarted: " + stamp +
                    "\nFrames are sampled screenshots for offline analysis only.\nNo aim overlay, input automation, memory access, or network interception is used.\n";
            fos.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void onImage(ImageReader ir) {
        Image image = null;
        try {
            image = ir.acquireLatestImage();
            if (image == null) return;
            long now = System.currentTimeMillis();
            if (now - lastSavedMs < intervalMs) return;
            lastSavedMs = now;

            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int w = image.getWidth(), h = image.getHeight();
            int rowPadding = rowStride - pixelStride * w;
            int rawW = w + rowPadding / pixelStride;

            if (rowBitmap == null || rowBitmapW != rawW || rowBitmapH != h) {
                if (rowBitmap != null) rowBitmap.recycle();
                rowBitmap = Bitmap.createBitmap(rawW, h, Bitmap.Config.ARGB_8888);
                rowBitmapW = rawW; rowBitmapH = h;
            }
            buf.rewind();
            rowBitmap.copyPixelsFromBuffer(buf);
            Bitmap cropped = Bitmap.createBitmap(rowBitmap, 0, 0, w, h);

            long idx = frameIndex++;
            String name = String.format(Locale.US, "frame_%06d_%d.jpg", idx, now);
            File out = new File(framesDir, name);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                cropped.compress(Bitmap.CompressFormat.JPEG, 88, fos);
            }
            cropped.recycle();
            writeMeta("frame", idx, w, h, name);

            if (idx % 20 == 0) {
                ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                        NOTIFY_ID, notification("Recording locally • frames " + (idx + 1)));
            }
        } catch (Throwable ignored) {
        } finally {
            if (image != null) image.close();
        }
    }

    private synchronized void writeMeta(String event, long idx, int w, int h, String note) {
        if (metadataWriter == null) return;
        try {
            long now = System.currentTimeMillis();
            long elapsed = Math.max(0, now - sessionStartMs);
            String filename = event.equals("frame") ? note : "";
            String safeNote = event.equals("frame") ? "" : note.replace(',', ';');
            metadataWriter.write(idx + "," + now + "," + elapsed + "," + w + "," + h + "," + event + "," + safeNote + "," + filename + "\n");
            metadataWriter.flush();
        } catch (Exception ignored) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Carrom Data Logger", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Local screenshot sampling for offline analysis");
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification notification(String body) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, DataLoggerService.class); stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("Carrom Data Logger")
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .setContentIntent(content)
                .addAction(new Notification.Action.Builder(null, "Stop", stopPi).build())
                .build();
    }

    private String clean(String s) {
        if (s == null || s.trim().isEmpty()) return "unknown";
        return s.trim().replaceAll("[^A-Za-z0-9_-]", "_");
    }

    @Override public void onDestroy() {
        try { writeMeta("session_end", frameIndex, 0, 0, "frames=" + frameIndex); } catch (Exception ignored) {}
        try { if (metadataWriter != null) metadataWriter.close(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) {}
        try { if (projection != null) projection.stop(); } catch (Exception ignored) {}
        if (captureThread != null) captureThread.quitSafely();
        if (rowBitmap != null) rowBitmap.recycle();
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
