package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;

/** Anything planted in the substrate (plants, rocks, wood). Sorted back-to-front by {@link #baseY}. */
abstract class Decor {
    /** Y of the point where the object meets the sand. Larger = closer to the viewer. */
    float baseY;
    /** Foreground items are drawn in front of the fish. */
    boolean foreground;
    /** Horizontal extent in world coordinates, used to skip drawing when off screen. */
    float left = -Float.MAX_VALUE, right = Float.MAX_VALUE;

    void update(float time, float dt) {}

    abstract void draw(Canvas canvas, float time);

    void release() {}
}
