package com.gdbismi.livewallpapers.spaceimpact;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.gdbismi.livewallpapers.spaceimpact.game.SpaceImpactGame;

/**
 * Scales the game's low resolution pixel buffer up to the screen with crisp, square pixels and
 * draws faint gaps between them so it reads like a monochrome phone LCD.
 */
final class LcdRenderer {
    /** Roughly how many LCD pixels fit across the short side of the screen (the 3310 had 84). */
    private static final int TARGET_COLUMNS = 84;

    private final Paint bitmapPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Rect dst = new Rect();
    private final int[] lut = new int[256];
    private Palette palette = Palette.NOKIA;

    private Bitmap bitmap;
    private int[] colors;
    private float[] gridLines;
    private int pixelSize, offsetX, offsetY, columns, rows;

    LcdRenderer() {
        bitmapPaint.setFilterBitmap(false);
        bitmapPaint.setAntiAlias(false);
        gridPaint.setAntiAlias(false);
        setPalette(palette);
    }

    void setPalette(Palette palette) {
        this.palette = palette;
        // Unlit cells are a touch darker than the gaps between them, like a real LCD.
        lut[0] = palette.mix(0.06f);
        for (int i = 1; i < 256; i++) {
            lut[i] = palette.mix(0.06f + 0.94f * i / 255f);
        }
        gridPaint.setColor(palette.background);
        gridPaint.setAlpha(0xC0);
    }

    /** Lays out the LCD for a surface of the given size and resizes the game to match. */
    void setSurfaceSize(int width, int height, SpaceImpactGame game) {
        pixelSize = Math.max(3, Math.min(width, height) / TARGET_COLUMNS);
        columns = Math.max(1, width / pixelSize);
        rows = Math.max(1, height / pixelSize);
        game.resize(columns, rows);
        // The game enforces a minimum size; use what it actually picked.
        columns = game.getWidth();
        rows = game.getHeight();
        offsetX = (width - columns * pixelSize) / 2;
        offsetY = (height - rows * pixelSize) / 2;
        dst.set(offsetX, offsetY, offsetX + columns * pixelSize, offsetY + rows * pixelSize);

        if (bitmap != null) bitmap.recycle();
        bitmap = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888);
        colors = new int[columns * rows];

        gridPaint.setStrokeWidth(Math.max(1, pixelSize / 6));
        gridLines = new float[(columns + rows + 2) * 4];
        int i = 0;
        for (int x = 0; x <= columns; x++) {
            float gx = offsetX + x * pixelSize;
            gridLines[i++] = gx;
            gridLines[i++] = dst.top;
            gridLines[i++] = gx;
            gridLines[i++] = dst.bottom;
        }
        for (int y = 0; y <= rows; y++) {
            float gy = offsetY + y * pixelSize;
            gridLines[i++] = dst.left;
            gridLines[i++] = gy;
            gridLines[i++] = dst.right;
            gridLines[i++] = gy;
        }
    }

    /** Converts screen coordinates to LCD pixel coordinates; returns false before layout. */
    boolean toLcd(float x, float y, float[] out) {
        if (pixelSize == 0) return false;
        out[0] = (x - offsetX) / pixelSize;
        out[1] = (y - offsetY) / pixelSize;
        return true;
    }

    void draw(Canvas canvas, SpaceImpactGame game) {
        canvas.drawColor(palette.background);
        if (bitmap == null) return;
        int[] intensity = game.getIntensity();
        if (intensity.length != colors.length) return;
        for (int i = 0; i < intensity.length; i++) {
            colors[i] = lut[intensity[i]];
        }
        bitmap.setPixels(colors, 0, columns, 0, 0, columns, rows);
        canvas.drawBitmap(bitmap, null, dst, bitmapPaint);
        canvas.drawLines(gridLines, gridPaint);
    }

    void release() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }
}
