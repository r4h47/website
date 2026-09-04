package com.maeen.carromaim;

import android.graphics.PointF;
import android.graphics.RectF;

public final class TurnGate {
    private TurnGate() {}

    /**
     * Local-player turn gate. We only allow the overlay when the detected striker
     * is inside the lower baseline band of the calibrated board.
     */
    public static boolean isLocalTurn(RectF board, PointF striker) {
        if (board == null || striker == null) return false;
        float bw = board.width();
        float bh = board.height();
        float nx = (striker.x - board.left) / Math.max(1f, bw);
        float ny = (striker.y - board.top) / Math.max(1f, bh);
        return nx >= 0.16f && nx <= 0.84f && ny >= 0.77f && ny <= 0.92f;
    }

    public static boolean isOpponentTurn(RectF board, PointF striker) {
        if (board == null || striker == null) return false;
        float bw = board.width();
        float bh = board.height();
        float nx = (striker.x - board.left) / Math.max(1f, bw);
        float ny = (striker.y - board.top) / Math.max(1f, bh);
        return nx >= 0.16f && nx <= 0.84f && ny >= 0.04f && ny <= 0.23f;
    }
}
