package com.livewallpapers.aquarium.scene;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/** Bubble curtain from the air stone plus the odd oxygen bubble rising from the plants. */
final class Bubbles {
    private static final int MAX = 220;
    private static final int SPRITE = 64;

    private final AquariumScene s;
    private final Random r;
    private final boolean airStone;
    private final float[] x = new float[MAX], y = new float[MAX], rad = new float[MAX];
    private final float[] vy = new float[MAX], phase = new float[MAX], freq = new float[MAX], baseX = new float[MAX];
    private final boolean[] alive = new boolean[MAX];
    private Bitmap sprite;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final float emitX, emitY;
    private float emitTimer, plantTimer, burstTimer;
    private boolean burst;

    Bubbles(AquariumScene scene, boolean airStone) {
        s = scene;
        this.airStone = airStone;
        r = new Random(scene.rnd.nextLong());
        emitX = scene.worldW * 0.86f;
        emitY = scene.sandTop(emitX) + 30f * scene.u - 10f * scene.u;
        buildSprite();
    }

    private void buildSprite() {
        sprite = Bitmap.createBitmap(SPRITE, SPRITE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sprite);
        float h = SPRITE / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(h, h, h,
                new int[] {0x18E0FFFF, 0x20D0F8FF, 0x70E8FFFF, 0xE0FFFFFF, 0x00FFFFFF},
                new float[] {0f, 0.6f, 0.82f, 0.94f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(h, h, h, p);
        p.setShader(new RadialGradient(h * 0.68f, h * 0.62f, h * 0.3f,
                new int[] {0xF0FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(h * 0.68f, h * 0.62f, h * 0.3f, p);
        p.setShader(new RadialGradient(h * 1.35f, h * 1.4f, h * 0.22f,
                new int[] {0x70FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(h * 1.35f, h * 1.4f, h * 0.22f, p);
    }

    private void spawn(float sx, float sy, float radius) {
        for (int i = 0; i < MAX; i++) {
            if (alive[i]) continue;
            alive[i] = true;
            x[i] = baseX[i] = sx;
            y[i] = sy;
            rad[i] = radius;
            vy[i] = 0f;
            phase[i] = r.nextFloat() * Mathx.TAU;
            freq[i] = Mathx.range(r, 5f, 9f);
            return;
        }
    }

    /** Lets fish or food add a few bubbles (for example when a fish gulps food at the surface). */
    void puff(float px, float py, int n) {
        for (int i = 0; i < n; i++) spawn(px + Mathx.range(r, -2f, 2f) * s.u, py, Mathx.range(r, 0.6f, 1.4f) * s.u);
    }

    void update(float dt) {
        float u = s.u;
        if (airStone) {
            burstTimer -= dt;
            if (burstTimer <= 0f) {
                burst = !burst;
                burstTimer = burst ? Mathx.range(r, 0.6f, 2f) : Mathx.range(r, 0.1f, 0.5f);
            }
            emitTimer -= dt;
            while (emitTimer <= 0f) {
                emitTimer += burst ? Mathx.range(r, 0.02f, 0.06f) : Mathx.range(r, 0.08f, 0.16f);
                float big = r.nextFloat();
                float radius = (big > 0.92f ? Mathx.range(r, 3.5f, 5.5f) : Mathx.range(r, 0.8f, 3f)) * u;
                spawn(emitX + Mathx.range(r, -5f, 5f) * u, emitY, radius);
            }
        }
        plantTimer -= dt;
        if (plantTimer <= 0f) {
            plantTimer = Mathx.range(r, 0.6f, 2.2f);
            float px = r.nextFloat() * s.worldW;
            float py = Mathx.lerp(s.height * 0.45f, s.sandTop(px), r.nextFloat());
            int n = 1 + r.nextInt(4);
            for (int i = 0; i < n; i++) spawn(px, py + i * 4f * u, Mathx.range(r, 0.6f, 1.3f) * u);
        }

        for (int i = 0; i < MAX; i++) {
            if (!alive[i]) continue;
            float rr = rad[i] / u;
            float terminal = (28f + rr * 14f) * u;
            vy[i] = Mathx.approach(vy[i], terminal, (60f + rr * 30f) * u * dt);
            y[i] -= vy[i] * dt;
            phase[i] += freq[i] * dt;
            float wobble = (0.4f + rr * 0.55f) * u;
            baseX[i] += Mathx.sin(s.time * 0.4f + y[i] * 0.004f) * 3f * u * dt;
            x[i] = baseX[i] + Mathx.sin(phase[i]) * wobble;
            if (y[i] < s.surfaceY + 2f * u) alive[i] = false;
        }
    }

    void draw(Canvas c) {
        for (int i = 0; i < MAX; i++) {
            if (!alive[i]) continue;
            float r0 = rad[i];
            float squash = r0 > 2.5f * s.u ? 0.12f * Mathx.sin(phase[i] * 1.3f) : 0f;
            float rw = r0 * (1.08f + squash);
            float rh = r0 * (0.92f - squash);
            dst.set(x[i] - rw, y[i] - rh, x[i] + rw, y[i] + rh);
            c.drawBitmap(sprite, null, dst, paint);
        }
    }

    void release() {
        if (sprite != null) sprite.recycle();
        sprite = null;
    }
}
