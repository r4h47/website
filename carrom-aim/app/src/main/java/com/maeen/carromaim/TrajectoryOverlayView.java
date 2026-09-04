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
        text.setTextSize(dp(13));
        text.setFakeBoldText(true);
    }

    public void setGameVisible(boolean v) {
        gameVisible = v;
        postInvalidateOnAnimation();
    }

    public void setResult(FrameAnalyzer.Result r) {
        result = r;
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (!gameVisible) return;
        FrameAnalyzer.Result r = result;
        if (r == null) {
            drawStatus(c, "AUTO AIM: waiting for board...");
            return;
        }

        if (r.board != null) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(Color.argb(55,0,255,255));
            c.drawRoundRect(r.board, dp(8), dp(8), p);
        }

        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(Color.argb(175,0,230,255));
        for (PointF q : r.myCoins) c.drawCircle(q.x,q.y,dp(13),p);

        if (r.striker != null) {
            p.setColor(Color.argb(220,0,255,255)); p.setStrokeWidth(dp(3));
            c.drawCircle(r.striker.x,r.striker.y,dp(17),p);
            c.drawLine(r.striker.x-dp(7),r.striker.y,r.striker.x+dp(7),r.striker.y,p);
            c.drawLine(r.striker.x,r.striker.y-dp(7),r.striker.x,r.striker.y+dp(7),p);
        }

        List<FrameAnalyzer.Shot> shots = r.shots;
        for (int i=shots.size()-1; i>=0; i--) drawHeatShot(c, shots.get(i), i);

        String side = r.side == FrameAnalyzer.Side.WHITE ? "WHITE" : (r.side == FrameAnalyzer.Side.BLACK ? "BLACK" : "AUTO");
        String status = "AUTO " + side + "   coins " + r.myCoins.size() + "   lines " + shots.size() +
                "   conf " + Math.round(r.confidence*100) + "%";
        if (r.striker == null) status += "   searching striker";
        drawStatus(c,status);
    }

    private void drawHeatShot(Canvas c, FrameAnalyzer.Shot s, int rank) {
        int core;
        if (!s.clear) core = Color.rgb(255,75,55);
        else if (rank == 0) core = Color.rgb(30,255,105);
        else if (rank == 1) core = Color.rgb(190,255,40);
        else if (rank <= 3) core = Color.rgb(255,205,35);
        else core = Color.rgb(255,115,35);

        float alphaScale = 1f - Math.min(0.55f, rank * 0.09f);
        drawSegments(c,s,withAlpha(core,(int)(45*alphaScale)),dp(15));
        drawSegments(c,s,withAlpha(core,(int)(105*alphaScale)),dp(8));
        drawSegments(c,s,withAlpha(core,(int)(235*alphaScale)),dp(rank==0?3.4f:2.4f));

        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(withAlpha(core,220));
        c.drawCircle(s.ghost.x,s.ghost.y,dp(13),p);
        if (rank==0) {
            p.setStyle(Paint.Style.FILL); p.setColor(withAlpha(core,220));
            c.drawCircle(s.coin.x,s.coin.y,dp(4),p);
            c.drawCircle(s.pocket.x,s.pocket.y,dp(5),p);
        }
    }

    private void drawSegments(Canvas c, FrameAnalyzer.Shot s, int color, float width) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color); p.setStrokeWidth(width);
        c.drawLine(s.striker.x,s.striker.y,s.ghost.x,s.ghost.y,p);
        c.drawLine(s.coin.x,s.coin.y,s.pocket.x,s.pocket.y,p);
    }

    private void drawStatus(Canvas c,String s) {
        float x=dp(10), y=dp(30);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(165,5,24,42));
        float w=Math.min(getWidth()-dp(20), text.measureText(s)+dp(22));
        c.drawRoundRect(new RectF(x,y-dp(20),x+w,y+dp(10)),dp(8),dp(8),p);
        text.setColor(Color.WHITE); c.drawText(s,x+dp(10),y,text);
    }

    private int withAlpha(int color,int a){return Color.argb(Math.max(0,Math.min(255,a)),Color.red(color),Color.green(color),Color.blue(color));}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
