package com.maeen.carromaim;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

public class AimOverlayView extends View {
    private static final String PREFS = "carrom_aim_prefs";
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PointF striker = new PointF();
    private final PointF target = new PointF();
    private final PointF[] pockets = new PointF[]{new PointF(), new PointF(), new PointF(), new PointF()};
    private final SharedPreferences prefs;
    private int selectedPocket = 0;
    private int activeMarker = -1;
    private boolean initialized = false;
    private boolean editMode = true;
    private float discRadius;
    private float touchRadius;

    public AimOverlayView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        float density = getResources().getDisplayMetrics().density;
        discRadius = 18f * density;
        touchRadius = 42f * density;
        linePaint.setStrokeWidth(3.2f * density);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        markerPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(13f * density);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        ghostPaint.setStyle(Paint.Style.STROKE);
        ghostPaint.setStrokeWidth(2.5f * density);
        ghostPaint.setColor(Color.rgb(255, 193, 7));
        ghostPaint.setPathEffect(new DashPathEffect(new float[]{8f * density, 6f * density}, 0));
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(2f * density);
        guidePaint.setPathEffect(new DashPathEffect(new float[]{12f * density, 8f * density}, 0));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!initialized && w > 0 && h > 0) {
            loadOrCreateDefaults(w, h);
            initialized = true;
        }
    }

    private void loadOrCreateDefaults(int w, int h) {
        if (prefs.contains("p0x")) {
            for (int i = 0; i < 4; i++) {
                pockets[i].set(prefs.getFloat("p" + i + "x", defaultPocketX(i, w)), prefs.getFloat("p" + i + "y", defaultPocketY(i, h)));
            }
        } else {
            for (int i = 0; i < 4; i++) pockets[i].set(defaultPocketX(i, w), defaultPocketY(i, h));
        }
        striker.set(prefs.getFloat("sx", w * 0.50f), prefs.getFloat("sy", h * 0.78f));
        target.set(prefs.getFloat("tx", w * 0.50f), prefs.getFloat("ty", h * 0.48f));
        selectedPocket = prefs.getInt("selected", 0);
    }

    private float defaultPocketX(int i, int w) { return (i == 0 || i == 2) ? w * 0.085f : w * 0.915f; }
    private float defaultPocketY(int i, int h) { return (i == 0 || i == 1) ? h * 0.22f : h * 0.72f; }
    public void setEditMode(boolean enabled) { editMode = enabled; invalidate(); }

    public int nextPocket() {
        selectedPocket = (selectedPocket + 1) % 4;
        prefs.edit().putInt("selected", selectedPocket).apply();
        invalidate();
        return selectedPocket + 1;
    }

    public void resetAll() {
        prefs.edit().clear().apply();
        initialized = false;
        if (getWidth() > 0 && getHeight() > 0) {
            loadOrCreateDefaults(getWidth(), getHeight());
            initialized = true;
        }
        selectedPocket = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!initialized) return;
        PointF pocket = pockets[selectedPocket];
        float dx = pocket.x - target.x;
        float dy = pocket.y - target.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        float ux = dx / len;
        float uy = dy / len;
        PointF ghost = new PointF(target.x - ux * discRadius * 2.0f, target.y - uy * discRadius * 2.0f);

        linePaint.setColor(Color.rgb(0, 220, 120));
        linePaint.setStrokeWidth(dp(3.2f));
        linePaint.setPathEffect(null);
        canvas.drawLine(target.x, target.y, pocket.x, pocket.y, linePaint);

        linePaint.setColor(Color.rgb(255, 204, 0));
        linePaint.setStrokeWidth(dp(3.6f));
        canvas.drawLine(striker.x, striker.y, ghost.x, ghost.y, linePaint);

        float sdx = ghost.x - striker.x;
        float sdy = ghost.y - striker.y;
        float slen = (float) Math.sqrt(sdx * sdx + sdy * sdy);
        if (slen > 1f) {
            float sux = sdx / slen;
            float suy = sdy / slen;
            guidePaint.setColor(Color.argb(190, 255, 235, 59));
            canvas.drawLine(ghost.x, ghost.y, ghost.x + sux * dp(90f), ghost.y + suy * dp(90f), guidePaint);
        }

        canvas.drawCircle(ghost.x, ghost.y, discRadius, ghostPaint);
        drawCrosshair(canvas, ghost.x, ghost.y, Color.rgb(255, 193, 7));

        for (int i = 0; i < 4; i++) {
            boolean selected = i == selectedPocket;
            markerPaint.setColor(selected ? Color.rgb(255, 214, 10) : Color.argb(210, 0, 180, 110));
            canvas.drawCircle(pockets[i].x, pockets[i].y, dp(selected ? 16f : 13f), markerPaint);
            textPaint.setColor(Color.BLACK);
            canvas.drawText("P" + (i + 1), pockets[i].x, pockets[i].y + dp(4f), textPaint);
        }

        markerPaint.setColor(Color.argb(210, 244, 67, 54));
        canvas.drawCircle(target.x, target.y, discRadius, markerPaint);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("T", target.x, target.y + dp(4f), textPaint);

        markerPaint.setColor(Color.argb(215, 33, 150, 243));
        canvas.drawCircle(striker.x, striker.y, discRadius, markerPaint);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("S", striker.x, striker.y + dp(4f), textPaint);

        float cut = computeCutAngle(ghost, target, pocket);
        drawInfoBadge(canvas, String.format(Locale.US, "P%d  CUT %.1f°", selectedPocket + 1, cut));
        if (editMode) drawEditHint(canvas);
    }

    private float computeCutAngle(PointF ghost, PointF targetPoint, PointF pocket) {
        float ax = targetPoint.x - ghost.x;
        float ay = targetPoint.y - ghost.y;
        float bx = pocket.x - targetPoint.x;
        float by = pocket.y - targetPoint.y;
        float al = (float) Math.sqrt(ax * ax + ay * ay);
        float bl = (float) Math.sqrt(bx * bx + by * by);
        if (al < 1f || bl < 1f) return 0f;
        float dot = (ax * bx + ay * by) / (al * bl);
        dot = Math.max(-1f, Math.min(1f, dot));
        return (float) Math.toDegrees(Math.acos(dot));
    }

    private void drawInfoBadge(Canvas canvas, String text) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.argb(190, 15, 47, 80));
        float cx = getWidth() * 0.5f;
        float y = getHeight() - dp(72f);
        float halfW = dp(82f);
        canvas.drawRoundRect(cx - halfW, y - dp(22f), cx + halfW, y + dp(16f), dp(10f), dp(10f), bg);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(12f));
        canvas.drawText(text, cx, y + dp(3f), textPaint);
    }

    private void drawEditHint(Canvas canvas) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.argb(175, 0, 0, 0));
        float cx = getWidth() * 0.5f;
        float y = getHeight() - dp(116f);
        float halfW = dp(155f);
        canvas.drawRoundRect(cx - halfW, y - dp(20f), cx + halfW, y + dp(16f), dp(8f), dp(8f), bg);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(11f));
        canvas.drawText("EDIT: drag S, T, or P1–P4 • tap PLAY when aligned", cx, y + dp(3f), textPaint);
    }

    private void drawCrosshair(Canvas canvas, float x, float y, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStrokeWidth(dp(1.5f));
        float r = discRadius * 0.55f;
        canvas.drawLine(x - r, y, x + r, y, p);
        canvas.drawLine(x, y - r, x, y + r, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editMode || !initialized) return false;
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeMarker = findNearestMarker(x, y);
                return activeMarker >= 0;
            case MotionEvent.ACTION_MOVE:
                if (activeMarker >= 0) { moveMarker(activeMarker, x, y); invalidate(); return true; }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeMarker >= 0) { moveMarker(activeMarker, x, y); saveState(); activeMarker = -1; invalidate(); return true; }
                break;
        }
        return false;
    }

    private int findNearestMarker(float x, float y) {
        int best = -1;
        float bestD2 = touchRadius * touchRadius;
        float d2 = dist2(x, y, striker.x, striker.y);
        if (d2 <= bestD2) { best = 0; bestD2 = d2; }
        d2 = dist2(x, y, target.x, target.y);
        if (d2 <= bestD2) { best = 1; bestD2 = d2; }
        for (int i = 0; i < 4; i++) {
            d2 = dist2(x, y, pockets[i].x, pockets[i].y);
            if (d2 <= bestD2) { best = i + 2; bestD2 = d2; }
        }
        return best;
    }

    private void moveMarker(int marker, float x, float y) {
        float margin = dp(10f);
        x = Math.max(margin, Math.min(getWidth() - margin, x));
        y = Math.max(margin, Math.min(getHeight() - margin, y));
        if (marker == 0) striker.set(x, y);
        else if (marker == 1) target.set(x, y);
        else if (marker >= 2 && marker <= 5) pockets[marker - 2].set(x, y);
    }

    private void saveState() {
        SharedPreferences.Editor e = prefs.edit();
        e.putFloat("sx", striker.x).putFloat("sy", striker.y);
        e.putFloat("tx", target.x).putFloat("ty", target.y);
        for (int i = 0; i < 4; i++) {
            e.putFloat("p" + i + "x", pockets[i].x);
            e.putFloat("p" + i + "y", pockets[i].y);
        }
        e.putInt("selected", selectedPocket).apply();
    }

    private float dist2(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
