package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** The porous air stone that feeds the bubble curtain, with its air line running up the back. */
final class AirStone extends Decor {
    private final float x;
    private final float r;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topCap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tube = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tubeHi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float h;
    private final float surfaceY;

    AirStone(AquariumScene s, float x, float baseY) {
        this.x = x;
        this.baseY = baseY;
        float u = s.u;
        r = 9f * u;
        h = 10f * u;
        surfaceY = s.surfaceY;
        body.setShader(new LinearGradient(x - r, 0, x + r, 0,
                new int[] {0xFF5C6164, 0xFF9AA0A0, 0xFF6E7476, 0xFF3A3E40}, new float[] {0f, 0.35f, 0.7f, 1f},
                Shader.TileMode.CLAMP));
        topCap.setShader(new RadialGradient(x - r * 0.2f, baseY - h, r,
                new int[] {0xFFB4BAB8, 0xFF7D8484}, null, Shader.TileMode.CLAMP));
        tube.setStyle(Paint.Style.STROKE);
        tube.setStrokeWidth(3.2f * u);
        tube.setColor(0x5590B8B8);
        tubeHi.setStyle(Paint.Style.STROKE);
        tubeHi.setStrokeWidth(0.8f * u);
        tubeHi.setColor(0x60E8FFFF);
        shadow.setColor(0x50000000);
    }

    /** World position where bubbles emerge. */
    float emitY() {
        return baseY - h;
    }

    @Override
    void draw(Canvas c, float time) {
        // Airline tubing coming down from the surface behind the stone.
        float tx = x + r * 0.6f;
        c.drawLine(tx + 18f * r / 9f, surfaceY - 10f, tx, baseY - h, tube);
        c.drawLine(tx + 18f * r / 9f - 0.8f, surfaceY - 10f, tx - 0.8f, baseY - h, tubeHi);
        c.drawOval(x - r * 1.5f, baseY - r * 0.35f, x + r * 1.6f, baseY + r * 0.4f, shadow);
        c.drawRect(x - r, baseY - h, x + r, baseY, body);
        c.drawOval(x - r, baseY - r * 0.3f, x + r, baseY + r * 0.3f, body);
        c.drawOval(x - r, baseY - h - r * 0.3f, x + r, baseY - h + r * 0.3f, topCap);
    }
}
