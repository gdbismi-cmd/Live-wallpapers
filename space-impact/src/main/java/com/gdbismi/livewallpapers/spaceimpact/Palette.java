package com.gdbismi.livewallpapers.spaceimpact;

import android.content.SharedPreferences;

/** LCD color schemes. The first is the classic Nokia 3310 look. */
public enum Palette {
    NOKIA(R.string.palette_nokia, 0xFFC7F0D8, 0xFF43523D),
    NIGHT(R.string.palette_night, 0xFF06120A, 0xFF6CFF8E),
    AMBER(R.string.palette_amber, 0xFF140B00, 0xFFFFB000),
    POCKET(R.string.palette_pocket, 0xFF9BBC0F, 0xFF0F380F);

    static final String PREFS_NAME = "space_impact";
    static final String KEY_PALETTE = "palette";

    final int labelRes;
    final int background;
    final int ink;

    Palette(int labelRes, int background, int ink) {
        this.labelRes = labelRes;
        this.background = background;
        this.ink = ink;
    }

    static Palette load(SharedPreferences prefs) {
        String name = prefs.getString(KEY_PALETTE, NOKIA.name());
        for (Palette p : values()) {
            if (p.name().equals(name)) return p;
        }
        return NOKIA;
    }

    /** Linear blend between background (t = 0) and ink (t = 1). */
    int mix(float t) {
        int a = background, b = ink;
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
