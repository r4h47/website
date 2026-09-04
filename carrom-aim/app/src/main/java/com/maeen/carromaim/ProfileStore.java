package com.maeen.carromaim;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProfileStore {
    private static final String PREFS = "carrom_profiles_v3";
    private static final String LAST = "last_profile_id";

    public static final class Profile {
        public String playerId = "";
        public String side = "AUTO";
        public boolean calibrated = false;
        public float boardLeft = 0.03f;
        public float boardTop = 0.25f;
        public float boardRight = 0.97f;
        public float boardBottom = 0.92f;
        public float pocketInset = 0.048f;
        public float coinRadius = 0.0235f;
        public float strikerRadius = 0.029f;
        public float baselineY = 0.885f;
        public int coinColor = 0;
        public int strikerColor = 0;
    }

    private static String safe(String id) {
        if (id == null) return "default";
        String x = id.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return x.isEmpty() ? "default" : x;
    }

    private static String k(String id, String field) {
        return "p_" + safe(id) + "_" + field;
    }

    public static void save(Context c, Profile p) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString(k(p.playerId, "id"), p.playerId);
        e.putString(k(p.playerId, "side"), p.side);
        e.putBoolean(k(p.playerId, "cal"), p.calibrated);
        e.putFloat(k(p.playerId, "bl"), p.boardLeft);
        e.putFloat(k(p.playerId, "bt"), p.boardTop);
        e.putFloat(k(p.playerId, "br"), p.boardRight);
        e.putFloat(k(p.playerId, "bb"), p.boardBottom);
        e.putFloat(k(p.playerId, "pi"), p.pocketInset);
        e.putFloat(k(p.playerId, "cr"), p.coinRadius);
        e.putFloat(k(p.playerId, "sr"), p.strikerRadius);
        e.putFloat(k(p.playerId, "by"), p.baselineY);
        e.putInt(k(p.playerId, "cc"), p.coinColor);
        e.putInt(k(p.playerId, "sc"), p.strikerColor);
        e.putString(LAST, p.playerId);
        e.apply();
    }

    public static Profile load(Context c, String playerId) {
        SharedPreferences s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Profile p = new Profile();
        p.playerId = playerId == null ? "" : playerId.trim();
        p.side = s.getString(k(p.playerId, "side"), "AUTO");
        p.calibrated = s.getBoolean(k(p.playerId, "cal"), false);
        p.boardLeft = s.getFloat(k(p.playerId, "bl"), p.boardLeft);
        p.boardTop = s.getFloat(k(p.playerId, "bt"), p.boardTop);
        p.boardRight = s.getFloat(k(p.playerId, "br"), p.boardRight);
        p.boardBottom = s.getFloat(k(p.playerId, "bb"), p.boardBottom);
        p.pocketInset = s.getFloat(k(p.playerId, "pi"), p.pocketInset);
        p.coinRadius = s.getFloat(k(p.playerId, "cr"), p.coinRadius);
        p.strikerRadius = s.getFloat(k(p.playerId, "sr"), p.strikerRadius);
        p.baselineY = s.getFloat(k(p.playerId, "by"), p.baselineY);
        p.coinColor = s.getInt(k(p.playerId, "cc"), 0);
        p.strikerColor = s.getInt(k(p.playerId, "sc"), 0);
        return p;
    }

    public static String lastProfileId(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST, "");
    }

    public static void delete(Context c, String playerId) {
        SharedPreferences s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = s.edit();
        String prefix = "p_" + safe(playerId) + "_";
        for (String key : s.getAll().keySet()) if (key.startsWith(prefix)) e.remove(key);
        if (playerId != null && playerId.equals(s.getString(LAST, ""))) e.remove(LAST);
        e.apply();
    }
}
