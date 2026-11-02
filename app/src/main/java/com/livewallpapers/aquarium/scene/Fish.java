package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import java.util.Random;

/**
 * A single fish. Seen from the side, swimming is conveyed by the tail sweeping towards and away
 * from the viewer (foreshortening), fluttering fins and smooth turns where the fish briefly faces
 * the viewer. Steering is a simple wander/arrive behaviour plus boids for schooling species.
 */
final class Fish {
    private static final int N = 20;

    final AquariumScene s;
    final Species sp;
    final int school;
    private final Random r;

    // Simulation state (world coordinates).
    float x, y, z;
    private float vx, vy;
    private float tx, ty, tz;
    private float speedFactor = 1f;
    private float wanderTimer, restTimer;
    private float facing = 1f;
    private float pitch;
    private float phase;
    private float tailAmp = 0.4f;
    private float finPhase;
    private int foodTarget = -1;
    private float hungerCooldown;
    private float offX, offY;
    private boolean dashing;

    // Body dimensions, in pixels.
    final float L;
    private final float depthFog;

    // Geometry scratch (local coordinates, fish facing +x).
    private final float[] gx = new float[N + 1];
    private final float[] gTop = new float[N + 1];
    private final float[] gBot = new float[N + 1];
    private final float[] gMid = new float[N + 1];
    private float tailScale = 1f;

    // Paints.
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ray = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeIris = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeHi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] patternColors;
    private final int[] finPatternColors;
    private final Path path = new Path();
    private final Path tailPath = new Path();

    Fish(AquariumScene scene, Species species, int school) {
        s = scene;
        sp = species;
        this.school = school;
        r = new Random(scene.rnd.nextLong());
        L = Mathx.range(r, sp.lengthMin, sp.lengthMax) * scene.u;
        z = Mathx.range(r, sp.zMin, sp.zMax);
        tz = z;
        depthFog = z * 0.5f;
        phase = r.nextFloat() * Mathx.TAU;
        finPhase = r.nextFloat() * Mathx.TAU;
        offX = Mathx.range(r, -45f, 45f) * scene.u;
        offY = Mathx.range(r, -28f, 28f) * scene.u;

        float top = -sp.topH * L, bot = sp.bellyH * L;
        body.setShader(new LinearGradient(0, top, 0, bot,
                new int[] {fog(Mathx.shade(sp.back, 0.85f)), fog(sp.back), fog(sp.side), fog(sp.belly), fog(Mathx.shade(sp.belly, 0.8f))},
                new float[] {0f, 0.18f, 0.5f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        fin.setColor(Mathx.withAlpha(fog(sp.finColor), sp.finAlpha));
        int tc = sp.tailColor != 0 ? sp.tailColor : sp.finColor;
        float ta = sp.tailAlpha >= 0f ? sp.tailAlpha : sp.finAlpha;
        tailPaint.setColor(Mathx.withAlpha(fog(tc), ta));
        ray.setStyle(Paint.Style.STROKE);
        ray.setStrokeWidth(Math.max(0.6f, L * 0.006f));
        ray.setColor(Mathx.withAlpha(fog(Mathx.shade(sp.finColor, 0.7f)), Math.min(0.6f, sp.finAlpha + 0.1f)));
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(Math.max(0.7f, L * 0.012f));
        outline.setColor(Mathx.withAlpha(fog(Mathx.shade(sp.back, 0.45f)), 0.55f));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(0.6f, L * 0.01f));
        line.setStrokeCap(Paint.Cap.ROUND);
        eyeIris.setColor(fog(sp.iris));
        eyeDark.setColor(fog(0xFF0A0A0C));
        eyeHi.setColor(0xD0FFFFFF);
        shade.setShader(new LinearGradient(0, top, 0, bot,
                new int[] {0x00000000, 0x00000000, 0x30000000}, new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        patternColors = new int[sp.patterns.size()];
        for (int i = 0; i < patternColors.length; i++) {
            int c = sp.patterns.get(i).color;
            patternColors[i] = Mathx.withAlpha(fog(c), Mathx.alpha(c));
        }
        finPatternColors = new int[sp.finPatterns.size()];
        for (int i = 0; i < finPatternColors.length; i++) {
            int c = sp.finPatterns.get(i).color;
            finPatternColors[i] = Mathx.withAlpha(fog(c), Mathx.alpha(c));
        }
    }

    private int fog(int c) {
        return Mathx.withAlpha(Mathx.mix(c | 0xFF000000, AquariumScene.FOG, depthFog), 255);
    }

    float depthScale() {
        return Mathx.lerp(1f, 0.58f, z);
    }

    void spawn(float px, float py) {
        x = px;
        y = sp.bottomDweller ? bottomY(px) : py;
        tx = x;
        ty = y;
        facing = r.nextBoolean() ? 1f : -1f;
        vx = facing * sp.cruise * s.u * 0.5f;
        wanderTimer = r.nextFloat() * 3f;
    }

    private float bottomY(float px) {
        return Mathx.clamp(s.sandTop(px) + 22f * s.u + offY * 0.6f, s.sandTop(px) + 8f * s.u, s.height - 26f * s.u);
    }

    private Fish leader() {
        for (int i = 0; i < s.fish.size(); i++) {
            Fish f = s.fish.get(i);
            if (f.school == school) return f;
        }
        return this;
    }

    // --------------------------------------------------------------------------------------
    // Behaviour
    // --------------------------------------------------------------------------------------

    void think(float dt) {
        float u = s.u;
        hungerCooldown -= dt;
        wanderTimer -= dt;

        // Food takes priority over everything.
        if (s.food.any() && hungerCooldown <= 0f) {
            float mx = mouthX(), my = y;
            int idx = s.food.nearest(mx, my, (sp.bottomDweller ? 90f : 190f) * u);
            if (idx >= 0 && sp.bottomDweller && s.food.y[idx] < s.sandTop(s.food.x[idx])) idx = -1;
            foodTarget = idx;
            if (idx >= 0) {
                tx = s.food.x[idx] - facing * L * 0.4f;
                ty = s.food.y[idx];
                speedFactor = sp.burst / sp.cruise * 0.6f;
                float dx = s.food.x[idx] - mx, dy = s.food.y[idx] - my;
                if (dx * dx + dy * dy < (L * 0.35f) * (L * 0.35f)) {
                    s.food.eat(idx);
                    hungerCooldown = Mathx.range(r, 0.6f, 2.5f);
                    wanderTimer = 0.6f;
                    foodTarget = -1;
                }
                return;
            }
        } else {
            foodTarget = -1;
        }

        if (sp.bottomDweller) {
            thinkBottom(dt);
            return;
        }

        if (sp.schooling) {
            Fish lead = leader();
            if (lead == this) {
                if (wanderTimer <= 0f || near(tx, ty, 30f * u)) pickTarget();
            } else {
                tx = lead.tx + offX;
                ty = Mathx.clamp(lead.ty + offY, s.swimCeiling(), s.swimFloor(lead.tx));
                speedFactor = lead.speedFactor;
                if (wanderTimer <= 0f) {
                    offX = Mathx.range(r, -45f, 45f) * u;
                    offY = Mathx.range(r, -28f, 28f) * u;
                    wanderTimer = Mathx.range(r, 3f, 7f);
                }
            }
        } else if (wanderTimer <= 0f || near(tx, ty, 20f * u)) {
            pickTarget();
        }
    }

    private void thinkBottom(float dt) {
        float u = s.u;
        if (dashing) {
            if (y < s.swimCeiling() + 10f * u) {
                dashing = false;
                tx = x + Mathx.range(r, -80f, 80f) * u;
                ty = bottomY(tx);
                speedFactor = 2.5f;
            }
            return;
        }
        if (restTimer > 0f) {
            restTimer -= dt;
            tx = x;
            ty = bottomY(x);
            return;
        }
        if (near(tx, ty, 6f * u) || wanderTimer <= 0f) {
            if (r.nextInt(30) == 0) {
                // Corydoras occasionally dart to the surface for a gulp of air.
                dashing = true;
                tx = x + Mathx.range(r, -30f, 30f) * u;
                ty = s.surfaceY;
                speedFactor = sp.burst / sp.cruise;
            } else {
                restTimer = Mathx.range(r, 0.8f, 4f);
                tx = Mathx.clamp(x + Mathx.range(r, -90f, 90f) * u, 20f * u, s.worldW - 20f * u);
                ty = bottomY(tx);
                speedFactor = Mathx.range(r, 0.6f, 1.3f);
            }
            wanderTimer = Mathx.range(r, 4f, 9f);
        }
    }

    private void pickTarget() {
        float u = s.u;
        tx = Mathx.range(r, 0.04f, 0.96f) * s.worldW;
        // Keep big fish away from the extremes.
        float top = s.swimCeiling() + (sp.topH * L);
        float bottom = s.swimFloor(tx) - L * 0.5f;
        float bias = r.nextFloat();
        ty = Mathx.lerp(top, bottom, sp.schooling ? 0.2f + 0.6f * bias : bias);
        tz = Mathx.range(r, sp.zMin, sp.zMax);
        speedFactor = r.nextInt(7) == 0 ? Mathx.range(r, 1.5f, 2.2f) : Mathx.range(r, 0.55f, 1.15f);
        wanderTimer = Mathx.range(r, 4f, 11f);
    }

    private boolean near(float px, float py, float d) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy < d * d;
    }

    float mouthX() {
        return x + Math.signum(facing) * L * 0.5f * depthScale();
    }

    void move(float dt) {
        float u = s.u;
        float cruise = sp.cruise * u * speedFactor * Mathx.lerp(1f, 0.7f, z);
        float dx = tx - x, dy = ty - y;
        float dist = (float) Math.hypot(dx, dy);
        float slow = Mathx.clamp(dist / (35f * u), 0.15f, 1f);
        if (restTimer > 0f) slow = 0f;
        float dvx = dist > 0.01f ? dx / dist * cruise * slow : 0f;
        float dvy = dist > 0.01f ? dy / dist * cruise * slow * 0.7f : 0f;

        float ax = (dvx - vx) * 1.4f;
        float ay = (dvy - vy) * 1.4f;

        if (school != 0) {
            float sepX = 0, sepY = 0, avx = 0, avy = 0, cx = 0, cy = 0;
            int n = 0;
            float sepR = L * 1.1f;
            float viewR = 70f * u;
            for (int i = 0; i < s.fish.size(); i++) {
                Fish o = s.fish.get(i);
                if (o == this || o.school != school) continue;
                float ox = o.x - x, oy = o.y - y;
                float d2 = ox * ox + oy * oy;
                if (d2 > viewR * viewR) continue;
                float d = (float) Math.sqrt(d2) + 0.01f;
                if (d < sepR) {
                    float push = (1f - d / sepR);
                    sepX -= ox / d * push;
                    sepY -= oy / d * push;
                }
                avx += o.vx;
                avy += o.vy;
                cx += o.x;
                cy += o.y;
                n++;
            }
            if (n > 0) {
                ax += sepX * 120f * u;
                ay += sepY * 120f * u;
                ax += (avx / n - vx) * 0.9f;
                ay += (avy / n - vy) * 0.9f;
                ax += (cx / n - x) * 0.25f;
                ay += (cy / n - y) * 0.25f;
            }
        }

        // Soft walls.
        float ceil = s.swimCeiling();
        float floor = sp.bottomDweller ? s.height - 18f * u : s.swimFloor(x);
        if (y < ceil) ay += (ceil - y) * 4f;
        if (y > floor) ay -= (y - floor) * 4f;
        if (x < 10f * u) ax += (10f * u - x) * 3f;
        if (x > s.worldW - 10f * u) ax -= (x - (s.worldW - 10f * u)) * 3f;

        float maxA = sp.burst * u * 1.6f;
        float am = (float) Math.hypot(ax, ay);
        if (am > maxA) {
            ax *= maxA / am;
            ay *= maxA / am;
        }
        vx += ax * dt;
        vy += ay * dt;
        float maxV = sp.burst * u;
        float vm = (float) Math.hypot(vx, vy);
        if (vm > maxV) {
            vx *= maxV / vm;
            vy *= maxV / vm;
        }
        // Fish swim forwards: while turning, they cannot move much sideways.
        float turnBrake = 0.35f + 0.65f * Math.abs(facing);
        x += vx * dt * turnBrake;
        y += vy * dt * turnBrake;
        z += (tz - z) * Mathx.damp(0.15f, dt);

        // Facing and turning.
        if (Math.abs(vx) > 3f * u) {
            float want = Math.signum(vx);
            facing = Mathx.approach(facing, want, dt * 2.6f);
        }
        float targetPitch = (float) Math.atan2(vy, Math.abs(vx) + 6f * u);
        if (sp.bottomDweller && restTimer > 0f) targetPitch = 0.18f;
        targetPitch = Mathx.clamp(targetPitch, -0.55f, 0.55f);
        pitch += (targetPitch - pitch) * Mathx.damp(4f, dt);

        // Tail beat speeds up with speed and when turning or accelerating.
        float speed = vm / (sp.cruise * u);
        float effort = Mathx.clamp(speed * 0.7f + am / (sp.burst * u) * 0.6f + (1f - Math.abs(facing)) * 0.8f, 0.15f, 2.2f);
        phase += dt * Mathx.TAU * sp.tailFreq * (0.35f + 0.65f * effort);
        tailAmp += (Mathx.clamp(0.22f + effort * 0.32f, 0.2f, 0.8f) - tailAmp) * Mathx.damp(3f, dt);
        finPhase += dt * Mathx.TAU * (1.6f + effort * 1.2f);
    }

    // --------------------------------------------------------------------------------------
    // Rendering
    // --------------------------------------------------------------------------------------

    void draw(Canvas c) {
        float ds = depthScale();
        float f = facing;
        float sx = Math.signum(f) * Math.max(0.08f, Math.abs(f));
        // During a turn the visible length shrinks; ease it so it reads as a rotation.
        sx = Math.signum(sx) * Mathx.sin(Math.abs(sx) * Mathx.PI / 2f);
        if (sx == 0f) sx = 0.08f;

        c.save();
        c.translate(x, y);
        c.rotate((float) Math.toDegrees(Math.signum(sx) * pitch));
        c.scale(sx * ds, ds);
        buildBody();

        drawDorsal(c);
        drawAnal(c);
        drawPectoral(c, false);
        drawTail(c);

        // Body.
        bodyPath(path);
        c.drawPath(path, body);
        drawPatterns(c);
        if (sp.scales) drawScales(c);
        c.drawPath(path, shade);
        c.drawPath(path, outline);

        // Gill cover.
        line.setColor(Mathx.withAlpha(fog(Mathx.shade(sp.side, 0.55f)), 0.6f));
        path.reset();
        float gu = sp.eyeU - 0.11f;
        path.moveTo(xAt(gu + 0.02f), yAt(gu + 0.02f, -0.7f));
        path.quadTo(xAt(gu - 0.035f), yAt(gu - 0.035f, 0f), xAt(gu + 0.015f), yAt(gu + 0.015f, 0.75f));
        c.drawPath(path, line);

        drawEye(c);
        drawMouth(c);
        drawPectoral(c, true);
        drawPelvic(c);
        c.restore();
    }

    /** Computes body contour arrays for this frame, applying tail foreshortening. */
    private void buildBody() {
        float theta = tailAmp * Mathx.sin(phase);
        tailScale = Mathx.cos(theta * 1.25f);
        float seg = L / N;
        float xc = L * 0.5f;
        float bob = L * 0.008f;
        for (int k = N; k >= 0; k--) {
            float uu = k / (float) N;
            gx[k] = xc;
            float bend = theta * Mathx.pow(1f - uu, 2.2f);
            xc -= seg * Mathx.cos(bend);
            float prof = profile(uu);
            float mid = bob * Mathx.sin(phase - uu * 2f) * (1f - uu) * (1f - uu);
            gMid[k] = mid;
            gTop[k] = mid - sp.topH * L * prof;
            gBot[k] = mid + sp.bellyH * L * prof;
        }
    }

    private float profile(float uu) {
        if (uu < sp.maxAt) {
            float t = uu / sp.maxAt;
            return sp.peduncle + (1f - sp.peduncle) * Mathx.pow(Mathx.sin(Mathx.PI * 0.5f * t), 1.15f);
        }
        float t = (uu - sp.maxAt) / (1f - sp.maxAt);
        return Mathx.pow(Math.max(0f, 1f - Mathx.pow(t, 2.2f)), sp.noseRound);
    }

    private float xAt(float uu) {
        if (uu < 0f) return gx[0] + uu * L * tailScale;
        float fi = Mathx.clamp(uu, 0f, 1f) * N;
        int i = Math.min(N - 1, (int) fi);
        return Mathx.lerp(gx[i], gx[i + 1], fi - i);
    }

    private float yAt(float uu, float v) {
        float fi = Mathx.clamp(uu, 0f, 1f) * N;
        int i = Math.min(N - 1, (int) fi);
        float t = fi - i;
        float mid = Mathx.lerp(gMid[i], gMid[i + 1], t);
        if (v < 0f) return mid + v * (mid - Mathx.lerp(gTop[i], gTop[i + 1], t));
        return mid + v * (Mathx.lerp(gBot[i], gBot[i + 1], t) - mid);
    }

    private void bodyPath(Path p) {
        p.reset();
        p.moveTo(gx[0], gTop[0]);
        for (int k = 1; k <= N; k++) p.lineTo(gx[k], gTop[k]);
        for (int k = N; k >= 0; k--) p.lineTo(gx[k], gBot[k]);
        p.close();
    }

    private void drawTail(Canvas c) {
        float T = sp.tailLen * L * tailScale;
        float H = sp.tailH * L;
        float bx = gx[0] + L * 0.01f;
        float hb = Math.max(gBot[0] - gTop[0], L * 0.04f) * 0.5f;
        float my = gMid[0];
        // Fin edge lags behind the sweep; this tilts the lobes a little.
        float lag = Mathx.cos(phase) * tailAmp * H * 0.12f;
        float flutter = Mathx.sin(phase * 2f) * H * 0.04f;
        float n = sp.notch;
        tailPath.reset();
        tailPath.moveTo(bx, my - hb);
        if (n <= 1f) {
            tailPath.quadTo(bx - T * 0.45f, my - H * 0.62f + lag, bx - T, my - H + lag + flutter);
            tailPath.quadTo(bx - T * (n + 0.15f), my - H * 0.25f + lag, bx - T * n, my + lag * 0.5f);
            tailPath.quadTo(bx - T * (n + 0.15f), my + H * 0.25f + lag, bx - T, my + H + lag - flutter);
            tailPath.quadTo(bx - T * 0.45f, my + H * 0.62f + lag, bx, my + hb);
        } else {
            tailPath.quadTo(bx - T * 0.5f, my - H * 1.05f + lag, bx - T, my - H * 0.55f + lag + flutter);
            tailPath.quadTo(bx - T * n, my + lag, bx - T, my + H * 0.55f + lag - flutter);
            tailPath.quadTo(bx - T * 0.5f, my + H * 1.05f + lag, bx, my + hb);
        }
        tailPath.close();
        c.drawPath(tailPath, tailPaint);

        // Fin rays.
        path.reset();
        int rays = 9;
        for (int i = 0; i < rays; i++) {
            float t = (i / (float) (rays - 1)) * 2f - 1f;
            float ex, ey;
            if (n <= 1f) {
                float a = Math.abs(t);
                ex = bx - T * Mathx.lerp(n, 1f, a) * 0.95f;
                ey = my + t * H * 0.95f + lag * (1f - a * 0.5f);
            } else {
                ex = bx - T * Mathx.lerp(n, 0.9f, t * t) * 0.92f;
                ey = my + t * H * 0.6f + lag;
            }
            path.moveTo(bx, my + t * hb * 0.8f);
            path.lineTo(ex, ey);
        }
        c.drawPath(path, ray);

        if (!sp.finPatterns.isEmpty()) {
            c.save();
            c.clipPath(tailPath);
            for (int p = 0; p < sp.finPatterns.size(); p++) {
                Species.Pattern pat = sp.finPatterns.get(p);
                mark.setColor(finPatternColors[p]);
                for (int i = 0; i < pat.uv.length; i += 2) {
                    float pu = pat.uv[i];
                    float frac = Mathx.clamp(-pu / sp.tailLen, 0f, 1f);
                    float px = bx + pu * L * tailScale;
                    float py = my + pat.uv[i + 1] * Mathx.lerp(hb, H, frac) + lag * frac;
                    c.drawCircle(px, py, pat.radius * L, mark);
                }
            }
            c.restore();
        }
    }

    private void drawDorsal(Canvas c) {
        if (sp.dorsalH <= 0f) {
            return;
        }
        float flut = Mathx.sin(finPhase * 0.5f) * L * 0.015f;
        float ax = xAt(sp.dorsalA), bx = xAt(sp.dorsalB);
        float ay = yAt(sp.dorsalA, -0.92f), by = yAt(sp.dorsalB, -0.92f);
        float h = sp.dorsalH * L, sw = sp.dorsalSweep * L;
        path.reset();
        path.moveTo(bx, by);
        path.quadTo(bx - sw * 0.25f, by - h * 0.95f, bx - sw, by - h + flut);
        path.quadTo(ax - sw * 0.35f, Mathx.lerp(ay, by - h, 0.35f), ax, ay + L * 0.01f);
        path.close();
        c.drawPath(path, fin);
        drawFinRays(c, ax, ay, bx, by, bx - sw, by - h + flut);
        if (sp.adipose) {
            float px = xAt(0.12f), py = yAt(0.12f, -0.95f);
            path.reset();
            path.moveTo(px + L * 0.04f, py + L * 0.005f);
            path.quadTo(px, py - L * 0.06f, px - L * 0.04f, py + L * 0.01f);
            path.close();
            c.drawPath(path, fin);
        }
    }

    private void drawAnal(Canvas c) {
        if (sp.analH <= 0f) return;
        float flut = Mathx.sin(finPhase * 0.5f + 1.3f) * L * 0.015f;
        float ax = xAt(sp.analA), bx = xAt(sp.analB);
        float ay = yAt(sp.analA, 0.92f), by = yAt(sp.analB, 0.92f);
        float h = sp.analH * L, sw = sp.analSweep * L;
        path.reset();
        path.moveTo(bx, by);
        path.quadTo(bx - sw * 0.25f, by + h * 0.95f, bx - sw, by + h + flut);
        path.quadTo(ax - sw * 0.35f, Mathx.lerp(ay, by + h, 0.35f), ax, ay - L * 0.01f);
        path.close();
        c.drawPath(path, fin);
        drawFinRays(c, ax, ay, bx, by, bx - sw, by + h + flut);
    }

    /** Rays fan from the fin base towards its outer edge. */
    private void drawFinRays(Canvas c, float ax, float ay, float bx, float by, float tipX, float tipY) {
        path.reset();
        int n = 6;
        for (int i = 1; i <= n; i++) {
            float t = i / (float) (n + 1);
            float sx = Mathx.lerp(bx, ax, t), sy = Mathx.lerp(by, ay, t);
            float k = 1f - t * 0.75f;
            path.moveTo(sx, sy);
            path.lineTo(Mathx.lerp(sx, tipX, k) - (1f - k) * L * 0.02f, Mathx.lerp(sy, tipY, k));
        }
        c.drawPath(path, ray);
    }

    private void drawPectoral(Canvas c, boolean near) {
        float pu = sp.eyeU - 0.18f;
        float px = xAt(pu), py = yAt(pu, near ? 0.35f : 0.2f);
        float len = sp.pectoral * L * (near ? 1f : 0.8f);
        float ang = -0.5f + 0.45f * Mathx.sin(finPhase + (near ? 0f : 1.6f));
        float scaleW = 0.5f + 0.5f * Math.abs(Mathx.cos(finPhase + (near ? 0f : 1.6f)));
        float ex = px - Mathx.cos(ang) * len, ey = py - Mathx.sin(ang) * len;
        float nx = Mathx.sin(ang) * len * 0.3f * scaleW, ny = Mathx.cos(ang) * len * 0.3f * scaleW;
        path.reset();
        path.moveTo(px, py);
        path.quadTo((px + ex) / 2f + nx, (py + ey) / 2f + ny, ex, ey);
        path.quadTo((px + ex) / 2f - nx * 0.3f, (py + ey) / 2f - ny * 0.3f, px, py);
        path.close();
        int a = fin.getAlpha();
        fin.setAlpha((int) (a * (near ? 0.85f : 0.6f)));
        c.drawPath(path, fin);
        fin.setAlpha(a);
    }

    private void drawPelvic(Canvas c) {
        float pu = sp.eyeU - 0.3f;
        float px = xAt(pu), py = yAt(pu, 0.9f);
        float len = L * 0.12f;
        float sway = Mathx.sin(finPhase * 0.7f) * 0.15f;
        path.reset();
        path.moveTo(px, py);
        path.lineTo(px - len * 0.9f, py + len * (0.7f + sway));
        path.lineTo(px - len * 0.35f, py + len * 0.15f);
        path.close();
        c.drawPath(path, fin);
        if (sp.pelvicFilament > 0f) {
            line.setColor(Mathx.withAlpha(fog(sp.filamentColor), Mathx.alpha(sp.filamentColor)));
            float fl = sp.pelvicFilament * L;
            path.reset();
            path.moveTo(px, py);
            float w1 = Mathx.sin(finPhase * 0.4f) * fl * 0.12f;
            float w2 = Mathx.sin(finPhase * 0.4f + 1.2f) * fl * 0.18f;
            path.cubicTo(px - fl * 0.2f, py + fl * 0.35f + w1, px - fl * 0.5f + w2, py + fl * 0.6f,
                    px - fl * 0.75f + w2, py + fl * 0.75f + w1);
            c.drawPath(path, line);
        }
    }

    private void drawPatterns(Canvas c) {
        for (int p = 0; p < sp.patterns.size(); p++) {
            Species.Pattern pat = sp.patterns.get(p);
            mark.setColor(patternColors[p]);
            if (pat.dots) {
                for (int i = 0; i < pat.uv.length; i += 2) {
                    c.drawCircle(xAt(pat.uv[i]), yAt(pat.uv[i], pat.uv[i + 1]), pat.radius * L, mark);
                }
            } else {
                path.reset();
                for (int i = 0; i < pat.uv.length; i += 2) {
                    float pu = pat.uv[i];
                    float pv = Mathx.clamp(pat.uv[i + 1], -0.97f, 0.97f);
                    float px = xAt(Mathx.clamp(pu, 0f, 1f)), py = yAt(pu, pv);
                    if (i == 0) path.moveTo(px, py);
                    else path.lineTo(px, py);
                }
                path.close();
                c.drawPath(path, mark);
            }
        }
        // Restore the body path for the subsequent shading passes.
        bodyPath(path);
    }

    private void drawScales(Canvas c) {
        Path scalesPath = tailPath;
        scalesPath.reset();
        float step = 0.065f;
        float rr = L * 0.03f;
        int row = 0;
        for (float v = -0.75f; v <= 0.8f; v += 0.28f, row++) {
            for (float uu = 0.12f + (row % 2) * step * 0.5f; uu < sp.eyeU - 0.14f; uu += step) {
                float px = xAt(uu), py = yAt(uu, v);
                scalesPath.moveTo(px - rr * 0.2f, py - rr);
                scalesPath.quadTo(px - rr * 1.2f, py, px - rr * 0.2f, py + rr);
            }
        }
        line.setColor(Mathx.withAlpha(fog(Mathx.shade(sp.side, 0.65f)), 0.35f));
        c.drawPath(scalesPath, line);
    }

    private void drawEye(Canvas c) {
        float ex = xAt(sp.eyeU), ey = yAt(sp.eyeU, -0.3f);
        float er = sp.eyeSize * L;
        c.drawCircle(ex, ey, er * 1.12f, eyeDark);
        c.drawCircle(ex, ey, er, eyeIris);
        c.drawCircle(ex + er * 0.12f, ey, er * 0.62f, eyeDark);
        c.drawCircle(ex + er * 0.35f, ey - er * 0.35f, er * 0.25f, eyeHi);
    }

    private void drawMouth(Canvas c) {
        float mx = gx[N], my = gMid[N];
        line.setColor(Mathx.withAlpha(fog(Mathx.shade(sp.back, 0.4f)), 0.7f));
        c.drawLine(mx - L * 0.045f, my + L * 0.01f, mx, my - L * 0.002f, line);
        if (sp.barbels) {
            float w = Mathx.sin(finPhase) * L * 0.01f;
            c.drawLine(mx - L * 0.01f, my + L * 0.04f, mx + L * 0.03f, my + L * 0.085f + w, line);
            c.drawLine(mx - L * 0.03f, my + L * 0.05f, mx - L * 0.0f, my + L * 0.1f - w, line);
        }
    }
}
