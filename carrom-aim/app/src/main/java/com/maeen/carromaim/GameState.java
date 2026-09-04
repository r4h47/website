package com.maeen.carromaim;

public final class GameState {
    private GameState() {}
    public static volatile boolean carromForeground = false;
    public static volatile long lastEventMs = 0L;
}
