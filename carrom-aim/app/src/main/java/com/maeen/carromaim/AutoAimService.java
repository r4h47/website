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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class AutoAimService extends Service {
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";
    public static final String EXTRA_PROFILE_ID = "profileId";
    private static final String CHANNEL = "carrom_aim_vision_v31";
    private static final int NOTIFY_ID = 52;

    private WindowManager wm;
    private TrajectoryOverlayView overlay;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FrameAnalyzer genericAnalyzer = new FrameAnalyzer();
    private ProfileGuidedAnalyzer profileAnalyzer;
    private String profileId = "";
    private long lastAnalyzeMs = 0;
    private Bitmap rowBitmap;
    private int rowBitmapW = 0, rowBitmapH = 0;
    private int[] framePixels;
    private int localTurnFrames = 0;

    @Override public void onCreate() {
        super.onCreate(); createChannel(); startForeground(NOTIFY_ID, notification("Waiting for screen-capture permission"));
        wm=(WindowManager)getSystemService(WINDOW_SERVICE); addOverlay();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){
            String id=intent.getStringExtra(EXTRA_PROFILE_ID);
            if(id!=null&&!id.trim().isEmpty()){
                profileId=id.trim();
                ProfileStore.Profile p=ProfileStore.load(this,profileId);
                if(p.calibrated)profileAnalyzer=new ProfileGuidedAnalyzer(p);
            }
            if(projection==null){
                int code=intent.getIntExtra(EXTRA_RESULT_CODE,0);
                Intent data=intent.getParcelableExtra(EXTRA_RESULT_DATA);
                if(code!=0&&data!=null)startProjection(code,data);
            }
        }
        return START_NOT_STICKY;
    }

    private void addOverlay(){
        overlay=new TrajectoryOverlayView(this);
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START;
        wm.addView(overlay,lp);
        overlay.setGameVisible(false);
    }

    private void startProjection(int resultCode,Intent data){
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        projection=m.getMediaProjection(resultCode,data);
        if(projection==null){stopSelf();return;}
        projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},main);
        DisplayMetrics dm=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);
        int w=dm.widthPixels,h=dm.heightPixels,density=dm.densityDpi;
        reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        captureThread=new HandlerThread("carrom-vision-capture");captureThread.start();
        captureHandler=new Handler(captureThread.getLooper());
        reader.setOnImageAvailableListener(this::onImage,captureHandler);
        virtualDisplay=projection.createVirtualDisplay("CarromAimVision",w,h,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,captureHandler);
        ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFY_ID,notification("Profile-guided detector running: "+(profileId.isEmpty()?"default":profileId)));
    }

    private void onImage(ImageReader ir){
        Image image=null;
        try{
            image=ir.acquireLatestImage();if(image==null)return;
            long now=System.currentTimeMillis();if(now-lastAnalyzeMs<90)return;lastAnalyzeMs=now;
            Image.Plane plane=image.getPlanes()[0];ByteBuffer buf=plane.getBuffer();
            int pixelStride=plane.getPixelStride(),rowStride=plane.getRowStride(),w=image.getWidth(),h=image.getHeight(),rowPadding=rowStride-pixelStride*w,rawW=w+rowPadding/pixelStride;
            if(rowBitmap==null||rowBitmapW!=rawW||rowBitmapH!=h){
                if(rowBitmap!=null)rowBitmap.recycle();
                rowBitmap=Bitmap.createBitmap(rawW,h,Bitmap.Config.ARGB_8888);rowBitmapW=rawW;rowBitmapH=h;framePixels=new int[w*h];
            }
            buf.rewind();rowBitmap.copyPixelsFromBuffer(buf);rowBitmap.getPixels(framePixels,0,w,0,0,w,h);
            FrameAnalyzer.Result result=profileAnalyzer!=null?profileAnalyzer.analyze(framePixels,w,h):genericAnalyzer.analyze(framePixels,w,h);

            boolean valid=result!=null&&result.board!=null&&result.pockets!=null&&result.pockets.length==4&&result.confidence>=0.55f;
            boolean localTurn=valid&&result.striker!=null&&TurnGate.isLocalTurn(result.board,result.striker);

            if(localTurn)localTurnFrames++;else localTurnFrames=0;
            final boolean show=localTurnFrames>=2;
            main.post(()->{
                if(show){overlay.setResult(result);overlay.setGameVisible(true);}
                else overlay.setGameVisible(false);
            });
        }catch(Throwable ignored){}finally{if(image!=null)image.close();}
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL,"Carrom Aim Vision",NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Local profile-guided visual analysis");
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }
    private Notification notification(String body){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setContentTitle("Carrom Aim Vision v3.1").setContentText(body).setSmallIcon(android.R.drawable.ic_menu_compass).setOngoing(true).setContentIntent(pi).build();
    }
    @Override public void onDestroy(){
        if(reader!=null)try{reader.close();}catch(Exception ignored){}
        if(virtualDisplay!=null)try{virtualDisplay.release();}catch(Exception ignored){}
        if(projection!=null)try{projection.stop();}catch(Exception ignored){}
        if(captureThread!=null)captureThread.quitSafely();
        if(wm!=null&&overlay!=null)try{wm.removeView(overlay);}catch(Exception ignored){}
        if(rowBitmap!=null)rowBitmap.recycle();
        stopForeground(true);super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
