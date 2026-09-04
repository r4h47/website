package com.maeen.carromaim;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class CalibrationActivity extends Activity {
    public static final String EXTRA_PLAYER_ID = "playerId";
    private ProfileStore.Profile p;
    private Preview preview;
    private TextView values;
    private Spinner side;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent().getStringExtra(EXTRA_PLAYER_ID);
        if (id == null || id.trim().isEmpty()) { finish(); return; }
        p = ProfileStore.load(this, id.trim());

        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        sc.addView(root);

        TextView title = text("Calibrate profile: " + p.playerId, 24, Color.rgb(10,48,88));
        root.addView(title, mw());
        TextView help = text("Adjust these once so the detector uses your actual board geometry. The preview is normalized; values are saved locally under this Player ID.", 14, Color.DKGRAY);
        help.setPadding(0,dp(6),0,dp(10)); root.addView(help,mw());

        preview = new Preview();
        root.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(330)));

        values = text("", 13, Color.DKGRAY); values.setPadding(0,dp(8),0,dp(8)); root.addView(values,mw());

        side = new Spinner(this);
        side.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"AUTO","WHITE","BLACK"}));
        side.setSelection("WHITE".equals(p.side)?1:("BLACK".equals(p.side)?2:0));
        root.addView(label("My coin side"),mw()); root.addView(side,mw());

        addSlider(root,"Board left",1,18,Math.round(p.boardLeft*100),v->p.boardLeft=v/100f);
        addSlider(root,"Board top",8,45,Math.round(p.boardTop*100),v->p.boardTop=v/100f);
        addSlider(root,"Board right",82,99,Math.round(p.boardRight*100),v->p.boardRight=v/100f);
        addSlider(root,"Board bottom",55,99,Math.round(p.boardBottom*100),v->p.boardBottom=v/100f);
        addSlider(root,"Pocket inset",2,9,Math.round(p.pocketInset*100),v->p.pocketInset=v/100f);
        addSlider(root,"Coin radius",15,38,Math.round(p.coinRadius*1000),v->p.coinRadius=v/1000f);
        addSlider(root,"Striker radius",20,48,Math.round(p.strikerRadius*1000),v->p.strikerRadius=v/1000f);
        addSlider(root,"Striker baseline Y",72,96,Math.round(p.baselineY*100),v->p.baselineY=v/100f);

        Button save = button("Save calibration");
        save.setOnClickListener(v->{
            if (p.boardRight-p.boardLeft < .65f || p.boardBottom-p.boardTop < .50f) {
                Toast.makeText(this,"Board rectangle is too small",Toast.LENGTH_LONG).show(); return;
            }
            p.side = side.getSelectedItem().toString(); p.calibrated=true; ProfileStore.save(this,p);
            Toast.makeText(this,"Calibration saved for " + p.playerId,Toast.LENGTH_LONG).show(); finish();
        }); root.addView(save,top(14));

        Button reset = button("Reset this profile to defaults");
        reset.setOnClickListener(v->{ ProfileStore.delete(this,p.playerId); p=ProfileStore.load(this,p.playerId); recreate(); });
        root.addView(reset,top(8));
        setContentView(sc); updatePreview();
    }

    private interface Setter { void set(int v); }
    private void addSlider(LinearLayout root,String name,int min,int max,int initial,Setter s) {
        TextView lab = label(name); root.addView(lab,top(8));
        SeekBar bar = new SeekBar(this); bar.setMax(max-min); bar.setProgress(Math.max(0,Math.min(max-min,initial-min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar x,int pr,boolean f){s.set(min+pr);updatePreview();} public void onStartTrackingTouch(SeekBar x){} public void onStopTrackingTouch(SeekBar x){} });
        root.addView(bar,mw());
    }

    private void updatePreview(){ if(preview!=null)preview.invalidate(); if(values!=null) values.setText(String.format(java.util.Locale.US,"Board L/T/R/B: %.2f / %.2f / %.2f / %.2f   Pocket: %.3f   Coin r: %.3f   Striker r: %.3f   Baseline: %.2f",p.boardLeft,p.boardTop,p.boardRight,p.boardBottom,p.pocketInset,p.coinRadius,p.strikerRadius,p.baselineY)); }

    private class Preview extends View {
        Paint q=new Paint(Paint.ANTI_ALIAS_FLAG); Preview(){super(CalibrationActivity.this);setBackgroundColor(Color.rgb(35,35,35));}
        protected void onDraw(Canvas c){super.onDraw(c); float w=getWidth(),h=getHeight(); RectF b=new RectF(p.boardLeft*w,p.boardTop*h,p.boardRight*w,p.boardBottom*h); q.setStyle(Paint.Style.FILL);q.setColor(Color.rgb(228,190,120));c.drawRect(b,q); q.setStyle(Paint.Style.STROKE);q.setStrokeWidth(dp(3));q.setColor(Color.CYAN);c.drawRect(b,q); float d=Math.min(b.width(),b.height())*p.pocketInset; float[][] pts={{b.left+d,b.top+d},{b.right-d,b.top+d},{b.left+d,b.bottom-d},{b.right-d,b.bottom-d}}; q.setStyle(Paint.Style.FILL);q.setColor(Color.BLACK);for(float[]z:pts)c.drawCircle(z[0],z[1],Math.max(dp(8),b.width()*.035f),q); float by=b.top+p.baselineY*b.height();q.setStyle(Paint.Style.STROKE);q.setStrokeWidth(dp(3));q.setColor(Color.MAGENTA);c.drawLine(b.left+b.width()*.15f,by,b.right-b.width()*.15f,by,q);q.setColor(Color.WHITE);c.drawCircle(b.centerX(),b.centerY(),b.width()*p.coinRadius,q);q.setColor(Color.GREEN);c.drawCircle(b.centerX(),by,b.width()*p.strikerRadius,q); }
    }

    private TextView label(String s){TextView t=text(s,14,Color.rgb(30,30,30));t.setPadding(0,dp(4),0,0);return t;}
    private TextView text(String s,float sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setMinHeight(dp(50));return b;}
    private LinearLayout.LayoutParams mw(){return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams top(int x){LinearLayout.LayoutParams lp=mw();lp.topMargin=dp(x);return lp;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
