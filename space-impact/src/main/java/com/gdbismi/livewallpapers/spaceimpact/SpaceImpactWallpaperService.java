package com.gdbismi.livewallpapers.spaceimpact;

import android.app.WallpaperManager;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import com.gdbismi.livewallpapers.spaceimpact.game.SpaceImpactGame;

/** A live wallpaper that plays a self-running, Space Impact style shoot 'em up. */
public class SpaceImpactWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new SpaceImpactEngine();
    }

    private final class SpaceImpactEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final long FRAME_MS = 1000L / SpaceImpactGame.TICKS_PER_SECOND;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable frame = this::drawFrame;
        private final SpaceImpactGame game = new SpaceImpactGame();
        private final LcdRenderer renderer = new LcdRenderer();
        private final float[] tap = new float[2];
        private SharedPreferences prefs;
        private boolean visible;
        private boolean hasSurface;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            prefs = getSharedPreferences(Palette.PREFS_NAME, MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(this);
            renderer.setPalette(Palette.load(prefs));
            setOffsetNotificationsEnabled(false);
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(frame);
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            renderer.release();
            super.onDestroy();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (Palette.KEY_PALETTE.equals(key)) {
                renderer.setPalette(Palette.load(sharedPreferences));
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            schedule();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            renderer.setSurfaceSize(width, height, game);
            hasSurface = true;
            schedule();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            hasSurface = false;
            handler.removeCallbacks(frame);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras,
                                boolean resultRequested) {
            if (WallpaperManager.COMMAND_TAP.equals(action) && renderer.toLcd(x, y, tap)) {
                game.onTap(tap[0], tap[1]);
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        private void schedule() {
            handler.removeCallbacks(frame);
            if (visible && hasSurface) handler.post(frame);
        }

        private void drawFrame() {
            if (!visible || !hasSurface) return;
            long start = SystemClock.uptimeMillis();
            game.tick();
            game.render();

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) renderer.draw(canvas, game);
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (IllegalStateException | IllegalArgumentException ignored) {
                        // The surface went away mid-frame; the next surface callback recovers.
                    }
                }
            }

            long elapsed = SystemClock.uptimeMillis() - start;
            handler.postDelayed(frame, Math.max(0, FRAME_MS - elapsed));
        }
    }
}
