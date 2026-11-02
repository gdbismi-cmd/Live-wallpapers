package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * A fully procedural freshwater aquarium. The scene only depends on {@code android.graphics}, so it
 * can be driven by the wallpaper engine or any other canvas.
 *
 * <p>Coordinate system: the tank ("world") is wider than the screen so that the launcher's home
 * screen scrolling can pan across it. Layers further away pan less, which gives parallax depth.
 */
public final class AquariumScene {

    /** Water colour that distant objects fade into. */
    static final int FOG = 0xFF17576A;

    final SceneConfig config;
    final Random rnd;

    // Geometry -----------------------------------------------------------------------------
    int width;
    int height;
    /** One "unit" is 1/400 of the shorter screen side. All sizes are expressed in units. */
    float u;
    float worldW;
    float surfaceY;
    float sandBaseY;
    private int sandSeed;

    // Camera -------------------------------------------------------------------------------
    private float scrollOffset = 0.5f;
    private float camX;
    float time;

    // Layers -------------------------------------------------------------------------------
    private Background background;
    private WaterSurface surface;
    private LightRays rays;
    private Sand sand;
    private final List<Decor> backDecor = new ArrayList<>();
    private final List<Decor> frontDecor = new ArrayList<>();
    final List<Fish> fish = new ArrayList<>();
    private final List<Fish> drawOrder = new ArrayList<>();
    Bubbles bubbles;
    private Particles particles;
    final Food food = new Food(this);
    private final Paint vignette = new Paint();

    private static final Comparator<Fish> BY_DEPTH = new Comparator<Fish>() {
        @Override
        public int compare(Fish a, Fish b) {
            return Float.compare(b.z, a.z);
        }
    };

    public AquariumScene(SceneConfig config) {
        this.config = config.copy();
        this.rnd = new Random(config.seed);
    }

    // --------------------------------------------------------------------------------------
    // Public API
    // --------------------------------------------------------------------------------------

    public boolean isReady() {
        return width > 0 && height > 0;
    }

    public void setSize(int w, int h) {
        if (w <= 0 || h <= 0 || (w == width && h == height)) return;
        release();
        width = w;
        height = h;
        u = Math.min(w, h) / 400f;
        worldW = config.parallax ? Math.round(w * 1.35f) : w;
        surfaceY = h * 0.055f;
        sandBaseY = h * 0.80f;
        sandSeed = rnd.nextInt(10000);
        build();
    }

    /** Launcher scroll position in [0,1]. */
    public void setScrollOffset(float offset) {
        scrollOffset = Mathx.clamp(offset, 0f, 1f);
    }

    /** Called with screen coordinates when the user taps on an empty part of the home screen. */
    public void onTap(float sx, float sy) {
        if (!isReady() || !config.tapToFeed) return;
        food.drop(sx + camX, sy);
    }

    public void update(float dt) {
        if (!isReady()) return;
        dt = Mathx.clamp(dt, 0f, 0.05f);
        time += dt;
        camX = scrollOffset * (worldW - width);

        surface.update(time);
        food.update(dt);
        for (int i = 0; i < backDecor.size(); i++) backDecor.get(i).update(time, dt);
        for (int i = 0; i < frontDecor.size(); i++) frontDecor.get(i).update(time, dt);
        for (int i = 0; i < fish.size(); i++) fish.get(i).think(dt);
        for (int i = 0; i < fish.size(); i++) fish.get(i).move(dt);
        if (bubbles != null) bubbles.update(dt);
        particles.update(dt);
    }

    public void draw(Canvas c) {
        if (!isReady()) return;
        float bgX = -camX * Background.PARALLAX;
        background.draw(c, bgX);
        if (rays != null) rays.draw(c, bgX, time);

        c.save();
        c.translate(-camX, 0f);
        surface.draw(c, camX, camX + width);
        sand.draw(c, time, camX, camX + width);
        drawDecor(c, backDecor);
        food.draw(c);

        drawOrder.clear();
        drawOrder.addAll(fish);
        Collections.sort(drawOrder, BY_DEPTH);
        float left = camX - width * 0.2f;
        float right = camX + width * 1.2f;
        for (int i = 0; i < drawOrder.size(); i++) {
            Fish f = drawOrder.get(i);
            if (f.x > left && f.x < right) f.draw(c);
        }
        if (bubbles != null) bubbles.draw(c);
        drawDecor(c, frontDecor);
        particles.draw(c, camX);
        c.restore();

        c.drawRect(0, 0, width, height, vignette);
    }

    private void drawDecor(Canvas c, List<Decor> list) {
        float viewL = camX, viewR = camX + width;
        for (int i = 0; i < list.size(); i++) {
            Decor d = list.get(i);
            if (d.right < viewL || d.left > viewR) continue;
            d.draw(c, time);
        }
    }

    public void release() {
        if (background != null) background.release();
        if (sand != null) sand.release();
        for (Decor d : backDecor) d.release();
        for (Decor d : frontDecor) d.release();
        if (bubbles != null) bubbles.release();
        backDecor.clear();
        frontDecor.clear();
        fish.clear();
        food.clear();
    }

    // --------------------------------------------------------------------------------------
    // Geometry helpers
    // --------------------------------------------------------------------------------------

    /** Y of the back edge of the sand (the "horizon" of the substrate) at world x. */
    float sandTop(float x) {
        float n = Mathx.fbm(x / (140f * u), 0.5f, sandSeed, 3) - 0.5f;
        return sandBaseY + n * 34f * u;
    }

    /** Lowest y a free swimming fish may reach at world x. */
    float swimFloor(float x) {
        return sandTop(x) + 6f * u;
    }

    float swimCeiling() {
        return surfaceY + 22f * u;
    }

    // --------------------------------------------------------------------------------------
    // Construction
    // --------------------------------------------------------------------------------------

    private void build() {
        background = new Background(this);
        surface = new WaterSurface(this);
        rays = config.lightRays ? new LightRays(this) : null;
        sand = new Sand(this);
        buildDecor();
        buildFish();
        bubbles = new Bubbles(this, config.bubbles);
        particles = new Particles(this);

        vignette.setShader(new RadialGradient(width * 0.5f, height * 0.45f,
                (float) Math.hypot(width, height) * 0.62f,
                new int[] {0x00000000, 0x00000000, 0x30000814, 0x8A000610},
                new float[] {0f, 0.55f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        camX = scrollOffset * (worldW - width);
        // Let the simulation settle so the first frame does not look "freshly spawned".
        for (int i = 0; i < 90; i++) update(1f / 30f);
    }

    private void buildDecor() {
        List<Decor> all = new ArrayList<>();
        float W = worldW;

        // Background planting: a dense hedge of eelgrass and stem plants along the back.
        for (float x = -10f * u; x < W + 10f * u; x += Mathx.range(rnd, 22f, 46f) * u) {
            float depth = Mathx.range(rnd, 0f, 1f);
            float base = sandTop(x) + Mathx.lerp(2f, 14f, depth) * u;
            float fog = Mathx.lerp(0.45f, 0.2f, depth);
            int kind = rnd.nextInt(10);
            if (kind < 5) {
                all.add(new Vallisneria(this, x, base, Mathx.range(rnd, 0.22f, 0.5f) * height,
                        rnd.nextInt(5) + 5, fog, rnd.nextInt(4) == 0 ? 1 : 0));
            } else if (kind < 7) {
                all.add(new StemPlant(this, x, base, Mathx.range(rnd, 0.2f, 0.42f) * height,
                        rnd.nextInt(3) + 3, fog, rnd.nextInt(3) == 0));
            }
        }

        // Rocks: one large hero stone group and a few smaller ones.
        float heroX = W * Mathx.range(rnd, 0.28f, 0.4f);
        all.add(new Rock(this, heroX, sandTop(heroX) + 30f * u, 150f * u, 92f * u, rnd.nextInt()));
        all.add(new Rock(this, heroX + 95f * u, sandTop(heroX) + 46f * u, 84f * u, 50f * u, rnd.nextInt()));
        all.add(new Rock(this, heroX - 88f * u, sandTop(heroX) + 52f * u, 64f * u, 32f * u, rnd.nextInt()));
        float x2 = W * Mathx.range(rnd, 0.7f, 0.8f);
        all.add(new Rock(this, x2, sandTop(x2) + 40f * u, 110f * u, 62f * u, rnd.nextInt()));
        all.add(new Rock(this, x2 + 64f * u, sandTop(x2) + 54f * u, 50f * u, 26f * u, rnd.nextInt()));

        // Driftwood branch.
        float dwX = W * Mathx.range(rnd, 0.5f, 0.6f);
        all.add(new Driftwood(this, dwX, sandTop(dwX) + 26f * u, rnd.nextInt()));

        // Mid-ground sword plants and stem clumps.
        for (int i = 0; i < 3; i++) {
            float x = W * (0.15f + 0.33f * i + Mathx.range(rnd, -0.06f, 0.06f));
            all.add(new SwordPlant(this, x, sandTop(x) + Mathx.range(rnd, 26f, 40f) * u,
                    Mathx.range(rnd, 0.17f, 0.24f) * height, 0.08f));
        }
        for (int i = 0; i < 3; i++) {
            float x = Mathx.range(rnd, 0.05f, 0.95f) * W;
            all.add(new StemPlant(this, x, sandTop(x) + Mathx.range(rnd, 18f, 32f) * u,
                    Mathx.range(rnd, 0.25f, 0.4f) * height, rnd.nextInt(3) + 4, 0.1f, rnd.nextBoolean()));
        }

        // Air stone feeding the bubble curtain.
        float airX = W * 0.86f;
        all.add(new AirStone(this, airX, sandTop(airX) + 30f * u));

        // Foreground: tall eelgrass rising from the bottom corners, in front of the fish.
        Vallisneria fl = new Vallisneria(this, W * 0.02f, height + 30f * u, height * 0.6f, 7, 0f, 0);
        fl.foreground = true;
        all.add(fl);
        Vallisneria fr = new Vallisneria(this, W * 0.98f, height + 30f * u, height * 0.52f, 6, 0f, 0);
        fr.foreground = true;
        all.add(fr);
        SwordPlant fs = new SwordPlant(this, W * 0.8f, height + 14f * u, height * 0.15f, 0f);
        fs.foreground = true;
        all.add(fs);

        Collections.sort(all, new Comparator<Decor>() {
            @Override
            public int compare(Decor a, Decor b) {
                return Float.compare(a.baseY, b.baseY);
            }
        });
        for (Decor d : all) (d.foreground ? frontDecor : backDecor).add(d);
    }

    private void buildFish() {
        int[][] roster = {
                // tetra, rasbora, angel, gourami, platy, cory
                {9, 0, 1, 1, 2, 2},
                {14, 9, 2, 2, 3, 3},
                {20, 13, 3, 3, 4, 4},
        };
        int[] counts = roster[(int) Mathx.clamp(config.fishDensity, 0, 2)];
        Species[] species = {
                Species.azureTetra(), Species.copperRasbora(), Species.angelfish(),
                Species.dwarfGourami(), Species.sunsetPlaty(), Species.corydoras()};
        int school = 0;
        for (int s = 0; s < species.length; s++) {
            Species sp = species[s];
            int schoolId = sp.schooling ? ++school : 0;
            float sx = Mathx.range(rnd, 0.2f, 0.8f) * worldW;
            float sy = Mathx.range(rnd, 0.3f, 0.6f) * height;
            for (int i = 0; i < counts[s]; i++) {
                Fish f = new Fish(this, sp, schoolId);
                if (sp.schooling) {
                    f.spawn(sx + Mathx.range(rnd, -40f, 40f) * u, sy + Mathx.range(rnd, -30f, 30f) * u);
                } else {
                    f.spawn(Mathx.range(rnd, 0.1f, 0.9f) * worldW, Mathx.range(rnd, 0.25f, 0.65f) * height);
                }
                fish.add(f);
            }
        }
    }
}
