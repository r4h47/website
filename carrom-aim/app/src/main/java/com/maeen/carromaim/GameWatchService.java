package com.maeen.carromaim;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class GameWatchService extends AccessibilityService {
    public static final String TARGET_PACKAGE = "com.miniclip.carrom";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        GameState.carromForeground = TARGET_PACKAGE.contentEquals(pkg);
        GameState.lastEventMs = System.currentTimeMillis();
    }

    @Override
    public void onInterrupt() {
        GameState.carromForeground = false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        GameState.carromForeground = false;
        GameState.lastEventMs = System.currentTimeMillis();
    }
}
