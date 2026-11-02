package com.livewallpapers.aquarium.scene;

/** User adjustable options for the aquarium scene. */
public final class SceneConfig {
    /** 0 = few, 1 = normal, 2 = many. */
    public int fishDensity = 1;
    public boolean bubbles = true;
    public boolean lightRays = true;
    public boolean caustics = true;
    public boolean tapToFeed = true;
    public boolean parallax = true;
    public long seed = 20261102L;

    public SceneConfig copy() {
        SceneConfig c = new SceneConfig();
        c.fishDensity = fishDensity;
        c.bubbles = bubbles;
        c.lightRays = lightRays;
        c.caustics = caustics;
        c.tapToFeed = tapToFeed;
        c.parallax = parallax;
        c.seed = seed;
        return c;
    }
}
