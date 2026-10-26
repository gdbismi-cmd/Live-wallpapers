package com.gdbismi.livewallpapers.spaceimpact.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A self-playing, Space Impact style shoot 'em up that renders into a low resolution
 * monochrome pixel buffer. It has no Android dependencies so it can be tested on the JVM.
 *
 * <p>Call {@link #resize} once, then {@link #tick} and {@link #render} once per frame and read
 * {@link #getIntensity()}: 255 is a fully lit LCD pixel, 0 is unlit, and values in between are
 * the fading "ghost" a slow LCD leaves behind moving objects.
 */
public final class SpaceImpactGame {
    public static final int TICKS_PER_SECOND = 30;

    private static final int TITLE_TICKS = 5 * TICKS_PER_SECOND;
    private static final int LEVEL_INTRO_TICKS = 3 * TICKS_PER_SECOND;
    private static final int GAME_OVER_TICKS = 5 * TICKS_PER_SECOND;
    private static final int RESPAWN_TICKS = 45;
    private static final int INVULNERABLE_TICKS = 90;
    private static final int START_LIVES = 3;
    private static final int START_MISSILES = 3;
    private static final int MAX_MISSILES = 9;
    private static final int FIRE_COOLDOWN = 6;
    private static final float PLAYER_SPEED = 1.6f;
    private static final float SHOT_SPEED = 3f;
    private static final float MISSILE_SPEED = 2.5f;
    private static final float STALK_SPEED = 0.45f;
    private static final int DODGE_HORIZON = 12; // ticks of warning the autopilot needs
    private static final int NEAR_MISS_PER_MILLE = 2; // chance per tick while not lined up
    private static final float PLAYER_DRIFT = 0.4f; // max horizontal speed while bobbing
    private static final int GHOST_DECAY = 125; // out of 256, applied per frame to unlit pixels

    private static final int PW = Sprites.PLAYER.w;
    private static final int PH = Sprites.PLAYER.h;

    private enum Phase { TITLE, LEVEL_INTRO, PLAYING, GAME_OVER }

    private enum Pattern { STRAIGHT, SINE, ZIGZAG, BOSS }

    private enum EnemyType {
        BUG(Sprites.BUG, 1, 0.8f, Pattern.SINE, 5, true),
        ORB(Sprites.ORB, 2, 0.6f, Pattern.STRAIGHT, 10, true),
        JET(Sprites.JET, 1, 1.5f, Pattern.STRAIGHT, 5, false),
        ROCK(Sprites.ROCK, 3, 0.5f, Pattern.ZIGZAG, 15, false),
        BOSS(null, 30, 0.5f, Pattern.BOSS, 100, true); // frames are generated per boss

        final Sprite[] frames;
        final int hp;
        final float speed;
        final Pattern pattern;
        final int score;
        final boolean fires;

        EnemyType(Sprite[] frames, int hp, float speed, Pattern pattern, int score, boolean fires) {
            this.frames = frames;
            this.hp = hp;
            this.speed = speed;
            this.pattern = pattern;
            this.score = score;
            this.fires = fires;
        }
    }

    /** How a boss moves up and down once it has arrived. */
    private enum BossMove { WAVE, BOUNCE, WOBBLE, STALK }

    /** Boss volleys; each boss alternates between two of these. */
    private enum BossAttack { SPREAD, FAN, AIMED, BURST, WALL }

    private static final EnemyType[] WAVE_TYPES = {
            EnemyType.BUG, EnemyType.ORB, EnemyType.JET, EnemyType.ROCK
    };

    private static final class Enemy {
        final EnemyType type;
        float x, y, baseY, vy, phase;
        int hp, age, fireTimer, flash, volleys;
        boolean dead;
        Sprite[] frames;
        // Boss only: randomized for every boss.
        BossMove move;
        float moveFreq;
        BossAttack[] attacks;
        int fireInterval;

        Enemy(EnemyType type) {
            this.type = type;
            this.hp = type.hp;
            this.frames = type.frames;
        }

        Sprite frame() {
            return frames[(age / 8) % frames.length];
        }

        int w() {
            return frames[0].w;
        }

        int h() {
            return frames[0].h;
        }

        float cy() {
            return y + h() / 2f;
        }
    }

    private enum ShotKind { PLAYER, MISSILE, ENEMY }

    private static final class Shot {
        final ShotKind kind;
        float x, y;
        final float vx, vy;
        Enemy target; // the enemy a player shot was aimed at
        boolean hit, dead;

        Shot(ShotKind kind, float x, float y, float vx, float vy) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }

        Sprite sprite() {
            switch (kind) {
                case MISSILE: return Sprites.MISSILE;
                case ENEMY: return Sprites.ENEMY_SHOT;
                default: return Sprites.PLAYER_SHOT;
            }
        }
    }

    private static final class Explosion {
        final int x, y;
        int delay, age;

        Explosion(int x, int y, int delay) {
            this.x = x;
            this.y = y;
            this.delay = delay;
        }

        boolean done() {
            return age >= Sprites.EXPLOSION.length * 4;
        }
    }

    private static final class Pickup {
        float x, y;
        boolean dead;
    }

    private static final class Star {
        float x;
        int y;
        final boolean near;

        Star(float x, int y, boolean near) {
            this.x = x;
            this.y = y;
            this.near = near;
        }
    }

    private final Random rng;

    private int width, height;
    private byte[] lit = new byte[0];
    private int[] intensity = new int[0];

    private int hudTop, playTop, playBottom, terrainMax;
    private int[] terrain = new int[0];
    private int terrainSlope, terrainScroll;
    private final List<Star> stars = new ArrayList<>();

    private Phase phase = Phase.TITLE;
    private int phaseTimer, tick;
    private int score, lives, missiles, level, kills;

    private float playerX, playerY;
    private boolean playerAlive;
    private int respawnTimer, invulnerable, fireCooldown;
    private float manualY;
    private int manualTimer;
    private float[] dangerT = new float[64];
    private float[] dangerTop = new float[64];
    private float[] dangerBottom = new float[64];
    private int dangerCount;

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Shot> shots = new ArrayList<>();
    /** Player bullets and missiles fired, and how many left the screen without hitting. */
    int playerShotsFired, playerShotsMissed;
    private final List<Explosion> explosions = new ArrayList<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private Enemy boss;
    private boolean bossPending;
    private int spawnTimer;

    public SpaceImpactGame() {
        this(new Random());
    }

    public SpaceImpactGame(Random rng) {
        this.rng = rng;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** Per-pixel brightness, row-major, {@code width * height} entries in 0..255. */
    public int[] getIntensity() {
        return intensity;
    }

    public int getScore() {
        return score;
    }

    public int getLevel() {
        return level;
    }

    /** Sets the size of the virtual LCD in pixels. Restarts the game when the size changes. */
    public void resize(int w, int h) {
        w = Math.max(w, 48);
        h = Math.max(h, 48);
        if (w == width && h == height) return;
        width = w;
        height = h;
        lit = new byte[w * h];
        intensity = new int[w * h];

        hudTop = Math.max(2, Math.round(h * 0.05f));
        playTop = hudTop + 9;
        terrainMax = Math.max(4, h / 14);
        playBottom = h - terrainMax - 3;

        terrain = new int[w + 1];
        int t = terrainMax / 2;
        for (int i = 0; i < terrain.length; i++) {
            t = nextTerrain(t);
            terrain[i] = t;
        }

        stars.clear();
        int count = Math.max(6, w * h / 400);
        for (int i = 0; i < count; i++) {
            stars.add(new Star(rng.nextFloat() * w, randomStarY(), rng.nextInt(3) == 0));
        }

        startTitle();
    }

    /**
     * Handles a tap on the wallpaper at virtual pixel coordinates: the ship steers towards the
     * tapped row for a moment and launches a missile if it has one.
     */
    public void onTap(float x, float y) {
        if (phase != Phase.PLAYING || !playerAlive) return;
        manualY = y;
        manualTimer = 2 * TICKS_PER_SECOND;
        if (missiles > 0) {
            fireMissile(aimedTarget(ShotKind.MISSILE, 0f));
        } else if (fireCooldown == 0) {
            fireShot(aimedTarget(ShotKind.PLAYER, 0f));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Simulation
    // ---------------------------------------------------------------------------------------

    public void tick() {
        if (width == 0) return;
        tick++;
        scrollBackground();
        switch (phase) {
            case TITLE:
                if (--phaseTimer <= 0) {
                    resetRun();
                    startLevel(true);
                }
                break;
            case LEVEL_INTRO:
                updatePlayer(false);
                updateWorld();
                if (--phaseTimer <= 0) phase = Phase.PLAYING;
                break;
            case PLAYING:
                if (playerAlive) {
                    updatePlayer(true);
                } else if (--respawnTimer <= 0) {
                    respawn();
                }
                updateSpawning();
                updateWorld();
                break;
            case GAME_OVER:
                updateWorld();
                if (--phaseTimer <= 0) startTitle();
                break;
        }
    }

    private void startTitle() {
        phase = Phase.TITLE;
        phaseTimer = TITLE_TICKS;
        enemies.clear();
        shots.clear();
        explosions.clear();
        pickups.clear();
        boss = null;
        bossPending = false;
        playerAlive = false;
    }

    private void resetRun() {
        score = 0;
        lives = START_LIVES;
        missiles = START_MISSILES;
        level = 1;
        kills = 0;
    }

    private void startLevel(boolean placePlayer) {
        phase = Phase.LEVEL_INTRO;
        phaseTimer = LEVEL_INTRO_TICKS;
        spawnTimer = 20;
        if (placePlayer) {
            playerAlive = true;
            playerX = -PW;
            playerY = (playTop + playBottom - PH) / 2f;
            invulnerable = 0;
        }
    }

    private void respawn() {
        playerAlive = true;
        playerX = -PW;
        playerY = (playTop + playBottom - PH) / 2f;
        invulnerable = INVULNERABLE_TICKS;
        manualTimer = 0;
    }

    private void scrollBackground() {
        for (Star s : stars) {
            s.x -= s.near ? 0.5f : 0.2f;
            if (s.x < 0) {
                s.x += width;
                s.y = randomStarY();
            }
        }
        if (tick % 2 == 0) {
            System.arraycopy(terrain, 1, terrain, 0, terrain.length - 1);
            terrain[terrain.length - 1] = nextTerrain(terrain[terrain.length - 2]);
            terrainScroll++;
        }
    }

    private int randomStarY() {
        return playTop + rng.nextInt(Math.max(1, playBottom - playTop));
    }

    private int nextTerrain(int t) {
        if (rng.nextInt(100) < 12) terrainSlope = rng.nextInt(3) - 1;
        if (rng.nextBoolean()) t += terrainSlope;
        if (t <= 2 || t >= terrainMax) terrainSlope = -terrainSlope;
        return Math.max(2, Math.min(terrainMax, t));
    }

    private void updatePlayer(boolean allowFire) {
        float homeX = 4 + 3 * (float) Math.sin(tick * 0.03);
        if (playerX < homeX - 1) {
            playerX += 1f; // flying in from the left edge
        } else {
            playerX += clamp(homeX - playerX, -PLAYER_DRIFT, PLAYER_DRIFT);
        }

        float target;
        if (manualTimer > 0) {
            manualTimer--;
            target = manualY - PH / 2f;
        } else {
            target = aiTargetY();
        }
        playerY += clamp(target - playerY, -PLAYER_SPEED, PLAYER_SPEED);
        playerY = clamp(playerY, playTop, playBottom - PH);

        if (invulnerable > 0) invulnerable--;
        if (!allowFire) return;
        if (fireCooldown > 0) {
            fireCooldown--;
        } else {
            Enemy shotTarget = aimedTarget(ShotKind.PLAYER, 0f);
            // Now and then the autopilot is trigger-happy and fires when almost lined up, so
            // roughly 1% of its shots miss, like a human player's would.
            if (shotTarget == null && rng.nextInt(1000) < NEAR_MISS_PER_MILLE) {
                shotTarget = aimedTarget(ShotKind.PLAYER, 3f);
            }
            if (shotTarget != null) fireShot(shotTarget);
        }
        if (boss != null && missiles > 0 && rng.nextInt(45) == 0
                && aimedTarget(ShotKind.MISSILE, 0f) == boss) {
            fireMissile(boss);
        }
    }

    /**
     * The autopilot. Every reachable row is scored: rows where an enemy or bullet is predicted
     * to meet the ship (allowing for the time the ship needs to get there) are ruled out, and
     * among the safe ones it prefers lining up with the next enemy or pickup.
     */
    private float aiTargetY() {
        collectDangers();
        float goal = goalY();
        float best = playerY;
        float bestCost = Float.MAX_VALUE;
        for (float y = playTop; y <= playBottom - PH; y += 1f) {
            float cost = Math.abs(y - goal) * 0.5f + Math.abs(y - playerY) * 0.05f;
            for (int i = 0; i < dangerCount; i++) {
                float t = dangerT[i];
                float py = shipYAt(y, t);
                if (dangerTop[i] < py + PH + 2 && dangerBottom[i] > py - 2) {
                    cost += 500 + 500 / (1 + t);
                }
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = y;
            }
        }
        return best;
    }

    /**
     * Samples, for each nearby enemy and bullet, the rows it will occupy while it is level with
     * the ship. Closing speeds include the ship's own drift towards the right.
     */
    private void collectDangers() {
        dangerCount = 0;
        float front = playerX + PW;
        float speedMul = speedMul();
        for (Enemy e : enemies) {
            float dx = e.x - front;
            if (e.dead || dx > 50 || e.x + e.w() < playerX) continue;
            float speed = PLAYER_DRIFT + (e != boss ? e.type.speed * speedMul
                    : e.x > width - e.w() - 3 ? e.type.speed : 0f);
            float t0 = Math.max(0f, dx / speed);
            // Not a threat if the shots in flight will destroy it, or if there is time to line up
            // and shoot it down first (the autopilot dodges if that stops being true).
            int hitsNeeded = e.hp - pendingDamage(e);
            if (hitsNeeded <= 0) continue;
            if (e != boss && t0 > hitsNeeded * FIRE_COOLDOWN + 4) continue;
            float t1 = Math.min(t0 + 40, (e.x + e.w() - playerX) / speed);
            for (float t = t0; ; t += 2) {
                float ey = predictY(e, Math.min(t, t1));
                addDanger(Math.min(t, t1), ey, ey + e.h());
                if (t >= t1) break;
            }
        }
        for (Shot s : shots) {
            float dx = s.x - front;
            if (s.dead || s.kind != ShotKind.ENEMY || dx > 50
                    || s.x + Sprites.ENEMY_SHOT.w < playerX) continue;
            float speed = PLAYER_DRIFT - s.vx;
            float t0 = Math.max(0f, dx / speed);
            // Keep shooting until a bullet is close; there is still time to sidestep it.
            if (t0 > DODGE_HORIZON) continue;
            float t1 = (s.x + Sprites.ENEMY_SHOT.w - playerX) / speed;
            for (float t = t0; ; t += 1) {
                float sy = s.y + s.vy * Math.min(t, t1);
                addDanger(Math.min(t, t1), sy, sy + 1);
                if (t >= t1) break;
            }
        }
    }

    private void addDanger(float t, float top, float bottom) {
        if (dangerCount == dangerT.length) {
            dangerT = Arrays.copyOf(dangerT, dangerCount * 2);
            dangerTop = Arrays.copyOf(dangerTop, dangerCount * 2);
            dangerBottom = Arrays.copyOf(dangerBottom, dangerCount * 2);
        }
        dangerT[dangerCount] = t;
        dangerTop[dangerCount] = top;
        dangerBottom[dangerCount] = bottom;
        dangerCount++;
    }

    /** Where the autopilot would like to be if nothing were in the way. */
    private float goalY() {
        for (Pickup p : pickups) {
            float dx = p.x - playerX;
            if (!p.dead && dx > 0 && dx < 45) return p.y + Sprites.PICKUP.h / 2f - PH / 2f;
        }
        float goal = (playTop + playBottom - PH) / 2f + 12 * (float) Math.sin(tick * 0.02);
        float front = playerX + PW;
        float speedMul = speedMul();
        float bestDx = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            float dx = e.x - front;
            if (e.dead || dx < 6 || e.x > width || dx >= bestDx) continue;
            if (pendingDamage(e) >= e.hp) continue; // already taken care of
            bestDx = dx;
            // Line the gun up with where the enemy will be when a bullet fired now arrives.
            float speed = e != boss ? e.type.speed * speedMul : 0f;
            float t = dx / (SHOT_SPEED + speed);
            goal = predictY(e, t) + e.h() / 2f - (PH / 2 + 0.5f);
        }
        return goal;
    }

    private float shipYAt(float targetY, float t) {
        return playerY + clamp(targetY - playerY, -PLAYER_SPEED * t, PLAYER_SPEED * t);
    }

    private float predictY(Enemy e, float t) {
        float y;
        switch (e.type.pattern) {
            case SINE:
                y = e.baseY + 10 * (float) Math.sin((e.age + t) * 0.08 + e.phase);
                break;
            case ZIGZAG: {
                // Unfold the bounces between the top and bottom of the playfield.
                float range = Math.max(1f, playBottom - e.h() - playTop);
                float d = (e.y - playTop + e.vy * t) % (2 * range);
                if (d < 0) d += 2 * range;
                y = playTop + (d > range ? 2 * range - d : d);
                break;
            }
            case BOSS:
                y = bossY(e, e.age + t);
                break;
            default:
                y = e.y;
                break;
        }
        return clamp(y, playTop, playBottom - e.h());
    }

    /**
     * The enemy a straight shot fired right now would hit, predicting where each enemy will be
     * when the shot reaches it, or null if the shot would miss. Enemies that the shots already
     * in flight will finish off are skipped so no bullet is wasted. A positive {@code slack}
     * also accepts shots that would pass up to that many pixels wide of the enemy.
     */
    private Enemy aimedTarget(ShotKind kind, float slack) {
        Sprite ss = kind == ShotKind.MISSILE ? Sprites.MISSILE : Sprites.PLAYER_SHOT;
        float shotSpeed = kind == ShotKind.MISSILE ? MISSILE_SPEED : SHOT_SPEED;
        float x0 = kind == ShotKind.MISSILE ? playerX + PW - 2 : playerX + PW;
        float y0 = kind == ShotKind.MISSILE ? playerY + PH / 2 - 1 : playerY + PH / 2;
        float speedMul = speedMul();
        Enemy best = null;
        float bestT = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.dead || e.x > width - 2 || e.x + e.w() < x0) continue;
            if (pendingDamage(e) >= e.hp) continue;
            float speed = e != boss ? e.type.speed * speedMul
                    : e.x > width - e.w() - 3 ? e.type.speed : 0f;
            float t = Math.max(0f, (e.x - (x0 + ss.w)) / (shotSpeed + speed));
            float ey = predictY(e, t);
            // Without slack, require half a pixel to spare so rounding can't cause a miss.
            float margin = 0.5f - slack;
            if (y0 + ss.h <= ey + margin || y0 >= ey + e.h() - margin) continue;
            if (t < bestT) {
                bestT = t;
                best = e;
            }
        }
        return best;
    }

    private int pendingDamage(Enemy e) {
        int damage = 0;
        for (Shot s : shots) {
            if (!s.dead && s.target == e) damage += s.kind == ShotKind.MISSILE ? 5 : 1;
        }
        return damage;
    }

    private void fireShot(Enemy target) {
        Shot s = new Shot(ShotKind.PLAYER, playerX + PW, playerY + PH / 2, SHOT_SPEED, 0f);
        s.target = target;
        shots.add(s);
        playerShotsFired++;
        fireCooldown = FIRE_COOLDOWN;
    }

    private void fireMissile(Enemy target) {
        missiles--;
        Shot s = new Shot(ShotKind.MISSILE, playerX + PW - 2, playerY + PH / 2 - 1,
                MISSILE_SPEED, 0f);
        s.target = target;
        shots.add(s);
        playerShotsFired++;
    }

    private void updateSpawning() {
        if (boss != null) return;
        if (kills >= 15 + 5 * level) bossPending = true;
        if (bossPending) {
            if (enemies.isEmpty()) {
                spawnBoss();
                bossPending = false;
            }
            return;
        }
        if (--spawnTimer > 0) return;
        spawnFormation();
        spawnTimer = 35 + rng.nextInt(50) - Math.min(25, level * 4);
    }

    private void spawnFormation() {
        EnemyType type = WAVE_TYPES[rng.nextInt(WAVE_TYPES.length)];
        int count = type == EnemyType.ROCK ? 1 + rng.nextInt(2) : 2 + rng.nextInt(3);
        int h = type.frames[0].h;
        int margin = type.pattern == Pattern.SINE ? 10 : 0;
        int range = Math.max(1, playBottom - playTop - h - 2 * margin);
        float baseY = playTop + margin + rng.nextInt(range);
        for (int i = 0; i < count; i++) {
            Enemy e = new Enemy(type);
            e.x = width + i * (type.frames[0].w + 6);
            e.baseY = baseY;
            e.y = baseY;
            e.phase = i * 0.7f;
            e.vy = rng.nextBoolean() ? 0.6f : -0.6f;
            e.fireTimer = 30 + rng.nextInt(60);
            enemies.add(e);
        }
    }

    /** Every boss is new: its look, movement, pair of attacks, fire rate and health. */
    private void spawnBoss() {
        Enemy b = new Enemy(EnemyType.BOSS);
        b.frames = Bosses.create(rng);
        BossMove[] moves = BossMove.values();
        b.move = moves[rng.nextInt(moves.length)];
        b.moveFreq = 0.018f + rng.nextFloat() * 0.017f;
        BossAttack[] all = BossAttack.values();
        BossAttack first = all[rng.nextInt(all.length)];
        BossAttack second = all[rng.nextInt(all.length - 1)];
        if (second.ordinal() >= first.ordinal()) second = all[second.ordinal() + 1];
        b.attacks = new BossAttack[] {first, second};
        b.fireInterval = 45 + rng.nextInt(20);
        b.hp = Math.round((20 + 10 * level) * (0.8f + 0.4f * rng.nextFloat()));
        b.age = rng.nextInt(1000); // start each movement pattern at a random point
        b.x = width + 2;
        b.y = b.move == BossMove.STALK ? (playTop + playBottom - b.h()) / 2f : bossY(b, b.age);
        b.fireTimer = 60;
        boss = b;
        enemies.add(b);
    }

    private void updateWorld() {
        updateEnemies();
        updateShots();
        collide();
        updatePickups();
        updateExplosions();
        // Plain loops rather than removeIf lambdas keep the bytecode dexable by any toolchain.
        for (int i = enemies.size() - 1; i >= 0; i--) {
            if (enemies.get(i).dead) enemies.remove(i);
        }
        for (int i = shots.size() - 1; i >= 0; i--) {
            if (shots.get(i).dead) shots.remove(i);
        }
        for (int i = pickups.size() - 1; i >= 0; i--) {
            if (pickups.get(i).dead) pickups.remove(i);
        }
        for (int i = explosions.size() - 1; i >= 0; i--) {
            if (explosions.get(i).done()) explosions.remove(i);
        }
    }

    private void updateEnemies() {
        float speedMul = speedMul();
        boolean canFire = phase == Phase.PLAYING && playerAlive;
        for (Enemy e : enemies) {
            e.age++;
            if (e.flash > 0) e.flash--;
            if (e.type == EnemyType.BOSS) {
                updateBoss(e, canFire);
                continue;
            }
            e.x -= e.type.speed * speedMul;
            switch (e.type.pattern) {
                case SINE:
                    e.y = e.baseY + 10 * (float) Math.sin(e.age * 0.08 + e.phase);
                    break;
                case ZIGZAG:
                    e.y += e.vy;
                    if (e.y < playTop || e.y > playBottom - e.h()) e.vy = -e.vy;
                    break;
                default:
                    break;
            }
            e.y = clamp(e.y, playTop, playBottom - e.h());
            if (e.type.fires && canFire && e.x < width - 4 && e.x > playerX + 40
                    && --e.fireTimer <= 0) {
                enemyShot(e.x - 2, e.cy(), -(1.6f + 0.15f * level), 0f);
                e.fireTimer = 50 + rng.nextInt(70);
            }
            if (e.x + e.w() < -2) e.dead = true;
        }
    }

    private void updateBoss(Enemy b, boolean canFire) {
        float homeX = width - b.w() - 3;
        boolean arrived = b.x <= homeX;
        if (!arrived) b.x -= b.type.speed;
        if (b.move == BossMove.STALK) {
            b.y = stalkY(b, 1);
        } else {
            b.y = bossY(b, b.age);
        }
        if (!arrived || !canFire || --b.fireTimer > 0) return;

        float vx = -(1.5f + 0.1f * level);
        float x = b.x - 2;
        float y = b.cy();
        switch (b.attacks[b.volleys++ % 2]) {
            case SPREAD:
                for (int i = -1; i <= 1; i++) enemyShot(x, y, vx, 0.45f * i);
                break;
            case FAN:
                for (int i = -2; i <= 2; i++) enemyShot(x, y, vx, 0.4f * i);
                break;
            case AIMED:
                enemyShot(x, y, vx, aimAtPlayer(x, y, vx));
                break;
            case BURST: // a tight line of three aimed shots
                float vy = aimAtPlayer(x, y, vx);
                for (int i = 0; i < 3; i++) enemyShot(x + 5 * i, y + vy * 5 * i / vx, vx, vy);
                break;
            case WALL: // a column of shots with one gap to slip through
                int gap = rng.nextInt(5);
                for (int i = 0; i < 5; i++) {
                    if (i != gap) enemyShot(x, y + (i - 2) * 5, vx, 0f);
                }
                break;
        }
        b.fireTimer = Math.max(25, b.fireInterval - level * 5);
    }

    private float aimAtPlayer(float x, float y, float vx) {
        float frames = (x - playerX) / -vx;
        return clamp((playerY + PH / 2f - y) / frames, -1f, 1f);
    }

    /** Where a stalking boss will be in {@code t} ticks, closing in on the ship's current row. */
    private float stalkY(Enemy b, float t) {
        float target = playerY + PH / 2f - b.h() / 2f;
        float step = STALK_SPEED * t;
        return clamp(b.y + clamp(target - b.y, -step, step), playTop, playBottom - b.h());
    }

    /** The boss's row at a given age. */
    private float bossY(Enemy b, float age) {
        float mid = (playTop + playBottom - b.h()) / 2f;
        float amp = (playBottom - playTop - b.h()) / 2f;
        float a = age * b.moveFreq;
        switch (b.move) {
            case BOUNCE: { // triangle wave: straight runs between the top and bottom
                float p = a / (float) (2 * Math.PI);
                return mid + amp * (4 * Math.abs(p - (float) Math.floor(p + 0.5f)) - 1);
            }
            case WOBBLE:
                return mid + amp * (0.65f * (float) Math.sin(a)
                        + 0.35f * (float) Math.sin(a * 2.7f + 1));
            case STALK:
                return stalkY(b, age - b.age);
            default:
                return mid + amp * (float) Math.sin(a);
        }
    }

    private float speedMul() {
        return Math.min(2f, 1 + 0.12f * (level - 1));
    }

    private void enemyShot(float x, float y, float vx, float vy) {
        shots.add(new Shot(ShotKind.ENEMY, x, y, vx, vy));
    }

    private void updateShots() {
        for (Shot s : shots) {
            s.x += s.vx;
            s.y += s.vy;
            if (s.x < -4 || s.x > width + 4 || s.y < playTop - 2 || s.y > playBottom + 4) {
                s.dead = true;
                if (s.kind != ShotKind.ENEMY && !s.hit) playerShotsMissed++;
            }
        }
    }

    private void collide() {
        for (Shot s : shots) {
            if (s.dead || s.kind == ShotKind.ENEMY) continue;
            Sprite ss = s.sprite();
            for (Enemy e : enemies) {
                if (e.dead || e.x > width) continue;
                if (!overlaps(s.x, s.y, ss.w, ss.h, e.x, e.y, e.w(), e.h())) continue;
                s.hit = true;
                if (e != s.target) swapTargets(s, e);
                if (s.kind == ShotKind.MISSILE) {
                    e.hp -= 5;
                    if (e == boss) s.dead = true; // missiles punch through everything else
                } else {
                    e.hp--;
                    s.dead = true;
                }
                e.flash = 3;
                if (e.hp <= 0) killEnemy(e);
                if (s.dead) break;
            }
        }

        if (!playerAlive || invulnerable > 0 || phase == Phase.GAME_OVER) return;
        float px = playerX + 1, py = playerY + 1;
        int pw = PW - 2, ph = PH - 2;
        for (Shot s : shots) {
            if (!s.dead && s.kind == ShotKind.ENEMY
                    && overlaps(s.x, s.y, Sprites.ENEMY_SHOT.w, 1, px, py, pw, ph)) {
                s.dead = true;
                playerHit();
                return;
            }
        }
        for (Enemy e : enemies) {
            if (!e.dead && overlaps(e.x + 1, e.y + 1, e.w() - 2, e.h() - 2, px, py, pw, ph)) {
                if (e != boss) killEnemy(e);
                playerHit();
                return;
            }
        }
    }

    /**
     * Shot {@code s} struck {@code e} on its way to another enemy. Hand that other enemy to a shot
     * that was heading for {@code e}, so neither enemy ends up with too many or too few shots.
     */
    private void swapTargets(Shot s, Enemy e) {
        for (Shot other : shots) {
            if (other != s && !other.dead && other.target == e && other.kind == s.kind) {
                other.target = s.target;
                return;
            }
        }
    }

    private void killEnemy(Enemy e) {
        e.dead = true;
        score += e.type.score * (e == boss ? level : 1);
        if (e == boss) {
            for (int i = 0; i < 9; i++) {
                explode(e.x + rng.nextInt(e.w()), e.y + rng.nextInt(e.h()), i * 4);
            }
            boss = null;
            kills = 0;
            for (Shot s : shots) {
                if (s.kind == ShotKind.ENEMY) s.dead = true;
            }
            level++;
            startLevel(false);
            phaseTimer += 30;
            return;
        }
        explode(e.x + e.w() / 2f, e.cy(), 0);
        kills++;
        if (rng.nextInt(100) < 7) {
            Pickup p = new Pickup();
            p.x = e.x;
            p.y = clamp(e.y, playTop, playBottom - Sprites.PICKUP.h);
            pickups.add(p);
        }
    }

    private void playerHit() {
        explode(playerX + PW / 2f, playerY + PH / 2f, 0);
        explode(playerX + PW / 2f - 3, playerY + PH / 2f - 2, 5);
        playerAlive = false;
        respawnTimer = RESPAWN_TICKS;
        lives--;
        if (lives <= 0) {
            phase = Phase.GAME_OVER;
            phaseTimer = GAME_OVER_TICKS;
        }
    }

    private void explode(float cx, float cy, int delay) {
        Sprite s = Sprites.EXPLOSION[0];
        explosions.add(new Explosion(Math.round(cx) - s.w / 2, Math.round(cy) - s.h / 2, delay));
    }

    private void updatePickups() {
        for (Pickup p : pickups) {
            p.x -= 0.5f;
            if (p.x < -Sprites.PICKUP.w) {
                p.dead = true;
            } else if (playerAlive && overlaps(p.x, p.y, Sprites.PICKUP.w, Sprites.PICKUP.h,
                    playerX, playerY, PW, PH)) {
                p.dead = true;
                missiles = Math.min(MAX_MISSILES, missiles + 1);
                score += 20;
            }
        }
    }

    private void updateExplosions() {
        for (Explosion x : explosions) {
            if (x.delay > 0) {
                x.delay--;
            } else {
                x.age++;
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------

    public void render() {
        if (width == 0) return;
        Arrays.fill(lit, (byte) 0);

        for (Star s : stars) {
            if (s.near || (tick / 20 + s.y) % 5 != 0) set(Math.round(s.x), s.y);
        }
        drawTerrain();

        if (phase == Phase.TITLE) {
            drawTitle();
        } else {
            drawPlayfield();
            drawHud();
            if (phase == Phase.LEVEL_INTRO && phaseTimer < LEVEL_INTRO_TICKS) {
                drawTextCentered("LEVEL " + level, playTop + (playBottom - playTop) / 3, 2);
            } else if (phase == Phase.GAME_OVER) {
                int y = playTop + (playBottom - playTop) / 3;
                drawTextCentered("GAME OVER", y, 2);
                drawTextCentered("SCORE " + pad(score, 5), y + 16, 1);
            }
        }

        for (int i = 0; i < lit.length; i++) {
            intensity[i] = lit[i] != 0 ? 255 : (intensity[i] * GHOST_DECAY) >> 8;
        }
    }

    private void drawTerrain() {
        int prevTop = height - terrain[0];
        for (int x = 0; x < width; x++) {
            int top = height - terrain[x];
            for (int y = Math.min(top, prevTop); y <= Math.max(top, prevTop); y++) set(x, y);
            for (int y = top + 2; y < height; y++) {
                if (((x + terrainScroll + y) & 3) == 0) set(x, y);
            }
            prevTop = top;
        }
    }

    private void drawPlayfield() {
        for (Pickup p : pickups) {
            if ((tick / 6) % 2 == 0) draw(Sprites.PICKUP, p.x, p.y);
        }
        for (Enemy e : enemies) {
            if (e.flash % 2 == 0) draw(e.frame(), e.x, e.y);
        }
        for (Shot s : shots) {
            draw(s.sprite(), s.x, s.y);
        }
        if (playerAlive && (invulnerable == 0 || (invulnerable / 3) % 2 == 0)) {
            draw(Sprites.PLAYER, playerX, playerY);
        }
        for (Explosion x : explosions) {
            if (x.delay == 0 && !x.done()) draw(Sprites.EXPLOSION[x.age / 4], x.x, x.y);
        }
    }

    private void drawHud() {
        for (int i = 0; i < lives; i++) {
            draw(Sprites.HEART, 2 + i * 6, hudTop);
        }
        int mx = width / 2 - 5;
        draw(Sprites.MISSILE, mx, hudTop + 1);
        drawText(Integer.toString(missiles), mx + Sprites.MISSILE.w + 2, hudTop, 1);
        String s = pad(score, 5);
        drawText(s, width - Font.width(s) - 2, hudTop, 1);
    }

    private void drawTitle() {
        int elapsed = TITLE_TICKS - phaseTimer;
        int scale = Math.max(1, Math.min(3, (width - 6) / Font.width("IMPACT")));
        int y = playTop + (playBottom - playTop) / 4;
        // The title drops in from above, like the original intro.
        int drop = Math.max(0, 40 - elapsed * 2);
        drawTextCentered("SPACE", y - drop, scale);
        drawTextCentered("IMPACT", y + (Font.HEIGHT + 2) * scale - drop, scale);
        int below = y + (Font.HEIGHT + 2) * scale * 2 + 6;
        if (elapsed > 30 && (elapsed / 10) % 4 != 0) drawTextCentered("LIVE WALLPAPER", below, 1);

        float shipX = elapsed * 1.2f - PW - 10;
        float shipY = below + 16 + 4 * (float) Math.sin(elapsed * 0.1);
        draw(Sprites.PLAYER, shipX, shipY);
        if ((elapsed / 4) % 2 == 0) set(Math.round(shipX) - 1, Math.round(shipY) + 3);
        Sprite bug = Sprites.BUG[(elapsed / 8) % 2];
        draw(bug, width - elapsed * 0.6f + 20, shipY - 1 + 6 * (float) Math.sin(elapsed * 0.08));
    }

    private void drawTextCentered(String text, int y, int scale) {
        drawText(text, (width - Font.width(text) * scale) / 2, y, scale);
    }

    private void drawText(String text, int x, int y, int scale) {
        for (int i = 0; i < text.length(); i++) {
            Sprite g = Font.glyph(text.charAt(i));
            drawScaled(g, x, y, scale);
            x += (g.w + 1) * scale;
        }
    }

    private void draw(Sprite s, float x, float y) {
        drawScaled(s, Math.round(x), Math.round(y), 1);
    }

    private void drawScaled(Sprite s, int x0, int y0, int scale) {
        for (int y = 0; y < s.h; y++) {
            for (int x = 0; x < s.w; x++) {
                if (!s.get(x, y)) continue;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        set(x0 + x * scale + dx, y0 + y * scale + dy);
                    }
                }
            }
        }
    }

    private void set(int x, int y) {
        if (x >= 0 && y >= 0 && x < width && y < height) lit[y * width + x] = 1;
    }

    // ---------------------------------------------------------------------------------------

    private static boolean overlaps(float ax, float ay, int aw, int ah,
                                    float bx, float by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static String pad(int value, int digits) {
        StringBuilder sb = new StringBuilder(Integer.toString(Math.min(value, 99999)));
        while (sb.length() < digits) sb.insert(0, '0');
        return sb.toString();
    }
}
