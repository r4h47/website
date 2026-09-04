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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 2101;
    private TextView status, profileStatus;
    private EditText playerId;
    private MediaProjectionManager projectionManager;
    private String activeId = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1001);

        ScrollView sc=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(24),dp(20),dp(28)); root.setGravity(Gravity.CENTER_HORIZONTAL); sc.addView(root);
        TextView title=text("Carrom Aim Vision v3",28,Color.rgb(10,48,88)); title.setGravity(Gravity.CENTER); root.addView(title,mw());
        TextView sub=text("Local Player ID profile + one-time visual calibration + remembered detector geometry",15,Color.DKGRAY); sub.setGravity(Gravity.CENTER); sub.setPadding(0,dp(6),0,dp(16)); root.addView(sub,mw());

        playerId=new EditText(this); playerId.setHint("Enter your Carrom Player ID (local profile name)"); playerId.setText(ProfileStore.lastProfileId(this)); playerId.setSingleLine(true); root.addView(playerId,mw());
        Button load=button("Load / create local profile"); load.setOnClickListener(v->loadProfile()); root.addView(load,top(8));
        profileStatus=text("No profile loaded",15,Color.DKGRAY); profileStatus.setPadding(dp(10),dp(10),dp(10),dp(10)); root.addView(profileStatus,top(8));

        Button calibrate=button("Calibrate this profile once"); calibrate.setOnClickListener(v->{ if(!ensureProfile())return; Intent i=new Intent(this,CalibrationActivity.class); i.putExtra(CalibrationActivity.EXTRA_PLAYER_ID,activeId); startActivity(i); }); root.addView(calibrate,top(10));
        status=text("",14,Color.DKGRAY); status.setPadding(dp(10),dp(10),dp(10),dp(10)); root.addView(status,top(10));
        Button overlay=button("Grant display-over-apps permission"); overlay.setOnClickListener(v->requestOverlayPermission()); root.addView(overlay,top(8));
        Button start=button("Start profile-guided aim + open Carrom Pool"); start.setOnClickListener(v->beginCapture()); root.addView(start,top(8));
        Button stop=button("Stop visual aim"); stop.setOnClickListener(v->stopService(new Intent(this,AutoAimService.class))); root.addView(stop,top(8));

        TextView info=text("Workflow\n\n1. Enter Player ID and load/create profile.\n2. Calibrate board rectangle, pocket inset, coin radius, striker radius, baseline and your coin side once.\n3. Save. These normalized parameters stay on this phone under that ID.\n4. Start visual aim. The detector uses your saved geometry instead of rediscovering the whole board from scratch every frame.\n\nThe Player ID is only a local profile key. This app does not log in to Miniclip or ask for your password.",15,Color.rgb(45,45,45)); info.setPadding(0,dp(20),0,0); info.setLineSpacing(0,1.12f); root.addView(info,mw());
        setContentView(sc); refresh(); if(!playerId.getText().toString().trim().isEmpty())loadProfile();
    }

    @Override protected void onResume(){super.onResume();refresh(); if(!activeId.isEmpty())showProfile();}
    private void loadProfile(){String id=playerId.getText().toString().trim(); if(id.isEmpty()){Toast.makeText(this,"Enter a Player ID first",Toast.LENGTH_LONG).show();return;} activeId=id; ProfileStore.Profile p=ProfileStore.load(this,id); ProfileStore.save(this,p); showProfile();}
    private boolean ensureProfile(){if(activeId.isEmpty())loadProfile(); return !activeId.isEmpty();}
    private void showProfile(){ProfileStore.Profile p=ProfileStore.load(this,activeId); profileStatus.setText("Active local profile: "+activeId+"\nCalibration: "+(p.calibrated?"SAVED":"NOT YET SAVED")+"   My side: "+p.side); profileStatus.setTextColor(p.calibrated?Color.rgb(0,120,70):Color.rgb(175,90,20));}
    private void requestOverlayPermission(){if(Settings.canDrawOverlays(this))return; startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())));}
    private void beginCapture(){if(!ensureProfile())return; ProfileStore.Profile p=ProfileStore.load(this,activeId); if(!p.calibrated){Toast.makeText(this,"Calibrate this profile first",Toast.LENGTH_LONG).show();return;} if(!Settings.canDrawOverlays(this)){requestOverlayPermission();return;} startActivityForResult(projectionManager.createScreenCaptureIntent(),REQ_CAPTURE);}
    @Override protected void onActivityResult(int rc,int resultCode,Intent data){super.onActivityResult(rc,resultCode,data); if(rc!=REQ_CAPTURE)return; if(resultCode!=RESULT_OK||data==null){Toast.makeText(this,"Screen capture permission is required",Toast.LENGTH_LONG).show();return;} Intent svc=new Intent(this,AutoAimService.class); svc.putExtra(AutoAimService.EXTRA_RESULT_CODE,resultCode); svc.putExtra(AutoAimService.EXTRA_RESULT_DATA,data); svc.putExtra(AutoAimService.EXTRA_PROFILE_ID,activeId); if(Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc); getWindow().getDecorView().postDelayed(()->{Intent launch=getPackageManager().getLaunchIntentForPackage("com.miniclip.carrom"); if(launch!=null)startActivity(launch); else Toast.makeText(this,"Carrom Pool package not found",Toast.LENGTH_LONG).show();},650);}
    private void refresh(){boolean o=Settings.canDrawOverlays(this); status.setText("Display-over-apps: "+(o?"READY":"NOT GRANTED")+"\nScreen analysis: local only   Accessibility: not used"); status.setTextColor(o?Color.rgb(0,120,70):Color.rgb(180,60,30));}
    private TextView text(String s,float sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setMinHeight(dp(50));return b;}
    private LinearLayout.LayoutParams mw(){return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);} private LinearLayout.LayoutParams top(int x){LinearLayout.LayoutParams lp=mw();lp.topMargin=dp(x);return lp;} private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
