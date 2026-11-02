package com.livewallpapers.aquarium.scene;

import java.util.Random;

/** Small math helpers shared by the scene. */
final class Mathx {
    private Mathx() {}

    static final float PI = (float) Math.PI;
    static final float TAU = (float) (Math.PI * 2.0);

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static float smoothstep(float e0, float e1, float x) {
        float t = clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    static float sin(float a) {
        return (float) Math.sin(a);
    }

    static float cos(float a) {
        return (float) Math.cos(a);
    }

    static float sqrt(float a) {
        return (float) Math.sqrt(a);
    }

    static float pow(float a, float b) {
        return (float) Math.pow(a, b);
    }

    static float range(Random r, float lo, float hi) {
        return lo + r.nextFloat() * (hi - lo);
    }

    /** Moves {@code v} towards {@code target} by at most {@code step}. */
    static float approach(float v, float target, float step) {
        if (v < target) return Math.min(v + step, target);
        return Math.max(v - step, target);
    }

    /** Exponential smoothing factor that is frame-rate independent. */
    static float damp(float rate, float dt) {
        return 1f - (float) Math.exp(-rate * dt);
    }

    static int argb(int a, int r, int g, int b) {
        return (clampi(a) << 24) | (clampi(r) << 16) | (clampi(g) << 8) | clampi(b);
    }

    static int clampi(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    static int alpha(int c) {
        return c >>> 24;
    }

    static int red(int c) {
        return (c >> 16) & 0xFF;
    }

    static int green(int c) {
        return (c >> 8) & 0xFF;
    }

    static int blue(int c) {
        return c & 0xFF;
    }

    static int withAlpha(int c, int a) {
        return (clampi(a) << 24) | (c & 0x00FFFFFF);
    }

    static int withAlpha(int c, float a) {
        return withAlpha(c, Math.round(a * 255f));
    }

    static int mix(int c1, int c2, float t) {
        t = clamp(t, 0f, 1f);
        return argb(
                Math.round(lerp(alpha(c1), alpha(c2), t)),
                Math.round(lerp(red(c1), red(c2), t)),
                Math.round(lerp(green(c1), green(c2), t)),
                Math.round(lerp(blue(c1), blue(c2), t)));
    }

    /** Multiplies the RGB channels (not alpha) by {@code f}. */
    static int shade(int c, float f) {
        return argb(alpha(c), Math.round(red(c) * f), Math.round(green(c) * f), Math.round(blue(c) * f));
    }

    // ---------------------------------------------------------------------------------------
    // Value noise
    // ---------------------------------------------------------------------------------------

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    /** Smooth 2D value noise in [0,1]. */
    static float noise(float x, float y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float xf = x - xi;
        float yf = y - yi;
        float u = xf * xf * (3f - 2f * xf);
        float v = yf * yf * (3f - 2f * yf);
        float a = hash(xi, yi, seed);
        float b = hash(xi + 1, yi, seed);
        float c = hash(xi, yi + 1, seed);
        float d = hash(xi + 1, yi + 1, seed);
        return lerp(lerp(a, b, u), lerp(c, d, u), v);
    }

    /** Fractal noise in roughly [0,1]. */
    static float fbm(float x, float y, int seed, int octaves) {
        float sum = 0f;
        float amp = 0.5f;
        float norm = 0f;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x, y, seed + i * 31) * amp;
            norm += amp;
            x *= 2.03f;
            y *= 2.03f;
            amp *= 0.5f;
        }
        return sum / norm;
    }
}
