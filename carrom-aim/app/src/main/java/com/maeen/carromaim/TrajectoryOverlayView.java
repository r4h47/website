package com.maeen.carromaim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.View;

import java.util.List;

public class TrajectoryOverlayView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile FrameAnalyzer.Result result;
    private volatile boolean gameVisible = false;

    public TrajectoryOverlayView(Context c) {
        super(c);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        text.setTextSize(dp(12));
        text.setFakeBoldText(true);
    }

    public void setGameVisible(boolean v) { gameVisible=v; postInvalidateOnAnimation(); }
    public void setResult(FrameAnalyzer.Result r) { result=r; postInvalidateOnAnimation(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if(!gameVisible)return;
        FrameAnalyzer.Result r=result;
        if(r==null||r.striker==null||r.board==null)return;
        if(!TurnGate.isLocalTurn(r.board,r.striker))return;

        List<FrameAnalyzer.Shot> shots=r.shots;
        if(shots==null||shots.isEmpty()){
            drawTurnBadge(c,"YOUR TURN — searching clear shot");
            return;
        }

        // Draw only the three strongest clear paths. No diagnostic circles or false UI markers.
        for(int i=shots.size()-1;i>=0;i--)drawHeatShot(c,shots.get(i),i);
        drawTurnBadge(c,"YOUR TURN   " + shots.size() + " clear path" + (shots.size()==1?"":"s"));
    }

    private void drawHeatShot(Canvas c,FrameAnalyzer.Shot s,int rank){
        int core=rank==0?Color.rgb(45,255,115):(rank==1?Color.rgb(255,220,45):Color.rgb(255,145,35));
        float glow=rank==0?1f:(rank==1?.82f:.70f);

        drawSegments(c,s,withAlpha(core,(int)(42*glow)),dp(rank==0?22:17));
        drawSegments(c,s,withAlpha(core,(int)(95*glow)),dp(rank==0?12:9));
        drawSegments(c,s,withAlpha(core,(int)(245*glow)),dp(rank==0?4.5f:3.2f));

        // Ghost-contact ring: exactly where striker center should arrive.
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(dp(rank==0?4:2.5f));p.setColor(withAlpha(core,235));
        c.drawCircle(s.ghost.x,s.ghost.y,dp(rank==0?15:12),p);

        // Destination pocket glow helps the line terminate at the real pocket center.
        p.setStrokeWidth(dp(rank==0?6:3));p.setColor(withAlpha(core,rank==0?215:145));
        c.drawCircle(s.pocket.x,s.pocket.y,dp(rank==0?22:16),p);

        if(rank==0){
            p.setStyle(Paint.Style.FILL);p.setColor(withAlpha(core,245));
            c.drawCircle(s.coin.x,s.coin.y,dp(4.5f),p);
        }
    }

    private void drawSegments(Canvas c,FrameAnalyzer.Shot s,int color,float width){
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);p.setStrokeWidth(width);
        c.drawLine(s.striker.x,s.striker.y,s.ghost.x,s.ghost.y,p);
        c.drawLine(s.coin.x,s.coin.y,s.pocket.x,s.pocket.y,p);
    }

    private void drawTurnBadge(Canvas c,String s){
        float x=dp(10),y=dp(30);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(185,8,28,40));
        float w=Math.min(getWidth()-dp(20),text.measureText(s)+dp(20));
        c.drawRoundRect(new RectF(x,y-dp(19),x+w,y+dp(9)),dp(8),dp(8),p);
        text.setColor(Color.WHITE);c.drawText(s,x+dp(9),y,text);
    }

    private int withAlpha(int color,int a){return Color.argb(Math.max(0,Math.min(255,a)),Color.red(color),Color.green(color),Color.blue(color));}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
