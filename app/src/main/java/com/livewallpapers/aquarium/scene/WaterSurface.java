package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/** The water line near the top of the tank, seen slightly from below. */
final class WaterSurface {
    private final AquariumScene s;
    private final Paint air = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint underside = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path airPath = new Path();
    private final Path band = new Path();
    private final Path linePath = new Path();
    private float time;

    WaterSurface(AquariumScene scene) {
        s = scene;
        float u = s.u;
        air.setShader(new LinearGradient(0, 0, 0, s.surfaceY,
                new int[] {0xFF0E2A33, 0xFF2C6977, 0xFF6FB9C6}, new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        // Total internal reflection makes the underside of the surface look like a bright mirror.
        underside.setShader(new LinearGradient(0, s.surfaceY, 0, s.surfaceY + 26f * u,
                new int[] {0xB0BFF4F4, 0x4078D0DA, 0x00000000}, new float[] {0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.6f * u);
        line.setColor(0xE6F2FFFF);
        glint.setStyle(Paint.Style.STROKE);
        glint.setStrokeWidth(1f * u);
        glint.setColor(0x60FFFFFF);
    }

    void update(float t) {
        time = t;
    }

    float waveY(float x) {
        float u = s.u;
        return s.surfaceY
                + Mathx.sin(x / (38f * u) + time * 1.3f) * 2.2f * u
                + Mathx.sin(x / (17f * u) - time * 2.1f) * 1.1f * u
                + Mathx.sin(x / (71f * u) + time * 0.6f) * 1.6f * u;
    }

    void draw(Canvas c, float x0, float x1) {
        float step = 6f * s.u;
        airPath.reset();
        band.reset();
        linePath.reset();
        airPath.moveTo(x0 - step, 0);
        boolean first = true;
        for (float x = x0 - step; x <= x1 + step; x += step) {
            float y = waveY(x);
            airPath.lineTo(x, y);
            if (first) {
                linePath.moveTo(x, y);
                band.moveTo(x, y);
                first = false;
            } else {
                linePath.lineTo(x, y);
                band.lineTo(x, y);
            }
        }
        airPath.lineTo(x1 + step, 0);
        airPath.close();
        band.lineTo(x1 + step, s.surfaceY + 30f * s.u);
        band.lineTo(x0 - step, s.surfaceY + 30f * s.u);
        band.close();

        c.drawPath(band, underside);
        c.drawPath(airPath, air);
        c.drawPath(linePath, line);

        // Little moving glints just under the surface.
        float u = s.u;
        for (int i = 0; i < 18; i++) {
            float phase = i * 1.7f;
            float gx = x0 + ((i * 97f * u + time * (8f + i % 5) * u) % (x1 - x0 + 60f * u)) - 30f * u;
            float gy = waveY(gx) + (4f + (i % 4) * 3f) * u;
            float len = (6f + (i % 3) * 5f) * u;
            int a = (int) (90 * Math.max(0f, Mathx.sin(time * (0.8f + (i % 4) * 0.3f) + phase)));
            if (a <= 2) continue;
            glint.setAlpha(a);
            c.drawLine(gx - len, gy, gx + len, gy + Mathx.sin(time + phase) * u, glint);
        }
    }
}
