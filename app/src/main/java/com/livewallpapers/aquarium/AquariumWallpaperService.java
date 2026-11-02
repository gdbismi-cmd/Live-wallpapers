package com.livewallpapers.aquarium;

import android.app.WallpaperManager;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Choreographer;
import android.view.SurfaceHolder;

import com.livewallpapers.aquarium.scene.AquariumScene;
import com.livewallpapers.aquarium.scene.SceneConfig;

/**
 * Live wallpaper entry point. Each engine owns a render thread; the scene is only ever touched on
 * that thread, so no locking is needed around simulation state.
 */
public class AquariumWallpaperService extends WallpaperService {

    private static final String TAG = "AquariumWallpaper";

    @Override
    public Engine onCreateEngine() {
        return new AquariumEngine();
    }

    private final class AquariumEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private HandlerThread thread;
        private Handler handler;
        private Choreographer choreographer;
        private SharedPreferences prefs;

        // Render-thread state.
        private AquariumScene scene;
        private SceneConfig config;
        private int width, height;
        private boolean visible;
        private boolean surfaceReady;
        private boolean frameScheduled;
        private long lastFrameNanos;
        private long minFrameNanos = 1_000_000_000L / 30;
        private boolean hardwareCanvas = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

        private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                frameScheduled = false;
                if (!visible || !surfaceReady) return;
                long elapsed = frameTimeNanos - lastFrameNanos;
                // Allow a little slack so 30 fps on a 60 Hz display does not drop to 20.
                if (lastFrameNanos != 0 && elapsed < minFrameNanos - 4_000_000L) {
                    scheduleFrame();
                    return;
                }
                float dt = lastFrameNanos == 0 ? 0f : elapsed / 1e9f;
                lastFrameNanos = frameTimeNanos;
                if (scene != null) {
                    scene.update(dt);
                    drawFrame();
                }
                scheduleFrame();
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(false);
            setOffsetNotificationsEnabled(true);
            prefs = PreferenceManager.getDefaultSharedPreferences(AquariumWallpaperService.this);
            prefs.registerOnSharedPreferenceChangeListener(this);

            thread = new HandlerThread("AquariumRender", Process.THREAD_PRIORITY_DISPLAY);
            thread.start();
            handler = new Handler(thread.getLooper());
            final SceneConfig initial = readConfig();
            final int fps = readFps();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    choreographer = Choreographer.getInstance();
                    config = initial;
                    minFrameNanos = 1_000_000_000L / fps;
                }
            });
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    visible = false;
                    surfaceReady = false;
                    if (scene != null) scene.release();
                    scene = null;
                }
            });
            thread.quitSafely();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, final int w, final int h) {
            super.onSurfaceChanged(holder, format, w, h);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    width = w;
                    height = h;
                    surfaceReady = true;
                    if (scene == null) {
                        drawPlaceholder();
                        scene = new AquariumScene(config);
                    }
                    scene.setSize(w, h);
                    lastFrameNanos = 0;
                    if (visible) {
                        drawFrame();
                        scheduleFrame();
                    }
                }
            });
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            // Block until the render thread has stopped touching the surface.
            runOnRenderThreadAndWait(new Runnable() {
                @Override
                public void run() {
                    surfaceReady = false;
                }
            });
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(final boolean isVisible) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    visible = isVisible;
                    if (visible) {
                        lastFrameNanos = 0;
                        scheduleFrame();
                    }
                }
            });
        }

        @Override
        public void onOffsetsChanged(final float xOffset, float yOffset, final float xStep, float yStep,
                                     int xPixels, int yPixels) {
            // Launchers that do not page report a step of 0 (or -1); keep the tank centred then.
            if (xStep <= 0f || xStep >= 1f) return;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (scene != null) scene.setScrollOffset(xOffset);
                }
            });
        }

        @Override
        public Bundle onCommand(String action, final int x, final int y, int z, Bundle extras,
                                boolean resultRequested) {
            if (WallpaperManager.COMMAND_TAP.equals(action)) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (scene != null) scene.onTap(x, y);
                    }
                });
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            final SceneConfig newConfig = readConfig();
            final int fps = readFps();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    minFrameNanos = 1_000_000_000L / fps;
                    config = newConfig;
                    if (scene != null) scene.release();
                    scene = new AquariumScene(newConfig);
                    if (width > 0 && height > 0) scene.setSize(width, height);
                    lastFrameNanos = 0;
                }
            });
        }

        // ----------------------------------------------------------------------------------

        private SceneConfig readConfig() {
            SceneConfig c = new SceneConfig();
            c.fishDensity = parseInt(prefs.getString("fish_density", "1"), 1);
            c.bubbles = prefs.getBoolean("bubbles", true);
            c.lightRays = prefs.getBoolean("light_rays", true);
            c.caustics = prefs.getBoolean("caustics", true);
            c.tapToFeed = prefs.getBoolean("tap_to_feed", true);
            c.parallax = prefs.getBoolean("parallax", true);
            c.seed = System.currentTimeMillis();
            return c;
        }

        private int readFps() {
            int fps = parseInt(prefs.getString("frame_rate", "30"), 30);
            return fps >= 60 ? 60 : 30;
        }

        private int parseInt(String s, int fallback) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private void scheduleFrame() {
            if (frameScheduled || choreographer == null) return;
            frameScheduled = true;
            choreographer.postFrameCallback(frameCallback);
        }

        private Canvas lockCanvas(SurfaceHolder holder) {
            if (hardwareCanvas) {
                try {
                    return holder.lockHardwareCanvas();
                } catch (RuntimeException e) {
                    Log.w(TAG, "Hardware canvas unavailable, falling back to software", e);
                    hardwareCanvas = false;
                }
            }
            return holder.lockCanvas();
        }

        private void drawFrame() {
            if (!surfaceReady || scene == null || !scene.isReady()) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = lockCanvas(holder);
                if (canvas != null) scene.draw(canvas);
            } catch (RuntimeException e) {
                Log.w(TAG, "Frame failed", e);
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Could not post frame", e);
                    }
                }
            }
        }

        /** Shows the water colour while the scene is generated (takes a moment on first start). */
        private void drawPlaceholder() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = lockCanvas(holder);
                if (canvas != null) canvas.drawColor(0xFF124F63);
            } catch (RuntimeException ignored) {
                // Surface not ready yet; the real frame follows shortly.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                        // ignore
                    }
                }
            }
        }

        private void runOnRenderThreadAndWait(final Runnable r) {
            if (Thread.currentThread() == thread) {
                r.run();
                return;
            }
            final Object lock = new Object();
            final boolean[] done = {false};
            boolean posted = handler.post(new Runnable() {
                @Override
                public void run() {
                    r.run();
                    synchronized (lock) {
                        done[0] = true;
                        lock.notifyAll();
                    }
                }
            });
            if (!posted) return;
            synchronized (lock) {
                long deadline = System.currentTimeMillis() + 2000;
                while (!done[0]) {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) break;
                    try {
                        lock.wait(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
}
