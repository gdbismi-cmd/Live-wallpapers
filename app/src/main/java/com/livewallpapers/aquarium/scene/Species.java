package com.livewallpapers.aquarium.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Describes the anatomy, colouring and temperament of a kind of fish. Body markings are polygons in
 * body space: {@code u} runs from the tail base (0) to the nose (1), {@code v} from the top contour
 * (-1) through the spine (0) to the belly contour (+1). Fish deform the body every frame and the
 * markings follow automatically.
 */
final class Species {

    /** A body marking. */
    static final class Pattern {
        final float[] uv; // polygon (u,v pairs) or dot centres
        final int color;
        final float radius; // dots only, relative to body length
        final boolean dots;

        Pattern(float[] uv, int color, float radius, boolean dots) {
            this.uv = uv;
            this.color = color;
            this.radius = radius;
            this.dots = dots;
        }
    }

    String name;
    // Size, in scene units.
    float lengthMin, lengthMax;
    // Body profile.
    float topH = 0.15f, bellyH = 0.13f, maxAt = 0.55f, peduncle = 0.3f, noseRound = 0.55f;
    // Colours.
    int back, side, belly, finColor, iris = 0xFFC0C8C0;
    float finAlpha = 0.35f;
    float eyeSize = 0.05f, eyeU = 0.86f;
    boolean scales;
    // Caudal fin: length and half height relative to body length; notch < 1 forked, > 1 rounded.
    float tailLen = 0.3f, tailH = 0.16f, notch = 0.55f;
    int tailColor;
    float tailAlpha = -1f;
    // Dorsal / anal fins: u start, u end, height, sweep (relative to length).
    float dorsalA, dorsalB, dorsalH, dorsalSweep;
    float analA, analB, analH, analSweep;
    boolean adipose;
    float pelvicFilament; // 0 = none, else length relative to body
    int filamentColor = 0x80E0E0D0;
    float pectoral = 0.15f;
    boolean barbels;
    final List<Pattern> patterns = new ArrayList<>();
    final List<Pattern> finPatterns = new ArrayList<>();
    // Behaviour.
    float cruise = 30f, burst = 80f, tailFreq = 2.5f;
    float zMin = 0.1f, zMax = 0.9f;
    boolean schooling, bottomDweller;

    // --------------------------------------------------------------------------------------
    // Pattern helpers
    // --------------------------------------------------------------------------------------

    /** Horizontal band with tapered ends, centred on {@code vc} with half width {@code hw}. */
    Species hband(float u0, float u1, float vc, float hw, int color, float taper) {
        int n = 16;
        float[] uv = new float[(n + 1) * 4];
        int i = 0;
        for (int k = 0; k <= n; k++) {
            float t = k / (float) n;
            float tp = Mathx.pow(Mathx.sin(Mathx.PI * t), taper);
            uv[i++] = Mathx.lerp(u0, u1, t);
            uv[i++] = vc - hw * tp;
        }
        for (int k = n; k >= 0; k--) {
            float t = k / (float) n;
            float tp = Mathx.pow(Mathx.sin(Mathx.PI * t), taper);
            uv[i++] = Mathx.lerp(u0, u1, t);
            uv[i++] = vc + hw * tp;
        }
        patterns.add(new Pattern(uv, color, 0f, false));
        return this;
    }

    /** Vertical bar spanning v0..v1, centred at {@code uc} with half width {@code uw}. */
    Species vband(float uc, float uw, float v0, float v1, float slant, int color) {
        int n = 10;
        float[] uv = new float[(n + 1) * 4];
        int i = 0;
        for (int k = 0; k <= n; k++) {
            float v = Mathx.lerp(v0, v1, k / (float) n);
            float w = uw * (0.75f + 0.25f * Mathx.sin(Mathx.PI * k / n));
            uv[i++] = uc - w + slant * v;
            uv[i++] = v;
        }
        for (int k = n; k >= 0; k--) {
            float v = Mathx.lerp(v0, v1, k / (float) n);
            float w = uw * (0.75f + 0.25f * Mathx.sin(Mathx.PI * k / n));
            uv[i++] = uc + w + slant * v;
            uv[i++] = v;
        }
        patterns.add(new Pattern(uv, color, 0f, false));
        return this;
    }

    Species poly(int color, float... uv) {
        patterns.add(new Pattern(uv, color, 0f, false));
        return this;
    }

    Species dots(int color, float radius, int count, float u0, float u1, float v0, float v1, long seed, boolean onFins) {
        Random r = new Random(seed);
        float[] uv = new float[count * 2];
        for (int i = 0; i < count; i++) {
            uv[i * 2] = Mathx.range(r, u0, u1);
            uv[i * 2 + 1] = Mathx.range(r, v0, v1);
        }
        (onFins ? finPatterns : patterns).add(new Pattern(uv, color, radius, true));
        return this;
    }

    // --------------------------------------------------------------------------------------
    // The cast
    // --------------------------------------------------------------------------------------

    /** Small schooling tetra with an electric blue stripe and a red lower flank. */
    static Species azureTetra() {
        Species s = new Species();
        s.name = "Azure tetra";
        s.lengthMin = 22.4f;
        s.lengthMax = 26.6f;
        s.topH = 0.135f;
        s.bellyH = 0.125f;
        s.maxAt = 0.55f;
        s.peduncle = 0.32f;
        s.back = 0xFF5E6448;
        s.side = 0xFFA9B6B2;
        s.belly = 0xFFE4E6DE;
        s.finColor = 0xFFD8E4E4;
        s.finAlpha = 0.22f;
        s.iris = 0xFF9FD0E0;
        s.eyeSize = 0.07f;
        s.tailLen = 0.32f;
        s.tailH = 0.17f;
        s.notch = 0.5f;
        s.dorsalA = 0.42f;
        s.dorsalB = 0.53f;
        s.dorsalH = 0.13f;
        s.dorsalSweep = 0.07f;
        s.analA = 0.16f;
        s.analB = 0.42f;
        s.analH = 0.08f;
        s.analSweep = 0.05f;
        s.adipose = true;
        s.cruise = 34f;
        s.burst = 95f;
        s.tailFreq = 3.4f;
        s.schooling = true;
        s.zMin = 0.1f;
        s.zMax = 0.85f;
        // Red lower rear flank.
        s.poly(0xF0D3302A,
                0.0f, 0.15f, 0.12f, 0.05f, 0.35f, 0.02f, 0.58f, 0.08f, 0.62f, 0.3f,
                0.58f, 0.75f, 0.35f, 0.92f, 0.1f, 0.9f, 0.0f, 0.75f);
        // Iridescent stripe: soft glow then a bright core.
        s.hband(0.06f, 0.86f, -0.15f, 0.28f, 0x5550B8FF, 0.35f);
        s.hband(0.08f, 0.84f, -0.15f, 0.15f, 0xF02BE2FF, 0.35f);
        s.hband(0.15f, 0.75f, -0.18f, 0.05f, 0x90D8FFFF, 0.5f);
        return s;
    }

    /** Copper-pink rasbora with a black wedge on the rear half. */
    static Species copperRasbora() {
        Species s = new Species();
        s.name = "Copper rasbora";
        s.lengthMin = 26.6f;
        s.lengthMax = 30.8f;
        s.topH = 0.165f;
        s.bellyH = 0.15f;
        s.maxAt = 0.55f;
        s.peduncle = 0.3f;
        s.back = 0xFF8C6650;
        s.side = 0xFFD39070;
        s.belly = 0xFFEED8C8;
        s.finColor = 0xFFE88A58;
        s.finAlpha = 0.4f;
        s.iris = 0xFFD8B070;
        s.eyeSize = 0.065f;
        s.tailLen = 0.33f;
        s.tailH = 0.18f;
        s.notch = 0.42f;
        s.dorsalA = 0.45f;
        s.dorsalB = 0.56f;
        s.dorsalH = 0.15f;
        s.dorsalSweep = 0.07f;
        s.analA = 0.25f;
        s.analB = 0.4f;
        s.analH = 0.09f;
        s.analSweep = 0.05f;
        s.cruise = 38f;
        s.burst = 100f;
        s.tailFreq = 3.2f;
        s.schooling = true;
        s.zMin = 0.15f;
        s.zMax = 0.9f;
        s.poly(0xF0181212,
                0.04f, -0.08f, 0.2f, -0.2f, 0.4f, -0.45f, 0.56f, -0.55f, 0.58f, 0.0f,
                0.55f, 0.55f, 0.4f, 0.35f, 0.2f, 0.18f, 0.04f, 0.1f);
        s.hband(0.2f, 0.82f, -0.45f, 0.14f, 0x40FFE0C0, 0.6f);
        return s;
    }

    /** Tall, disc-shaped angelfish with sweeping fins and vertical bars. */
    static Species angelfish() {
        Species s = new Species();
        s.name = "Angelfish";
        s.lengthMin = 50.4f;
        s.lengthMax = 58.8f;
        s.topH = 0.4f;
        s.bellyH = 0.4f;
        s.maxAt = 0.5f;
        s.peduncle = 0.16f;
        s.noseRound = 0.75f;
        s.back = 0xFFB8A472;
        s.side = 0xFFD9D9D0;
        s.belly = 0xFFEDEDE6;
        s.finColor = 0xFFB8B8AE;
        s.finAlpha = 0.72f;
        s.iris = 0xFFC04030;
        s.eyeSize = 0.055f;
        s.eyeU = 0.84f;
        s.tailLen = 0.42f;
        s.tailH = 0.34f;
        s.notch = 0.8f;
        s.dorsalA = 0.28f;
        s.dorsalB = 0.62f;
        s.dorsalH = 0.62f;
        s.dorsalSweep = 0.42f;
        s.analA = 0.22f;
        s.analB = 0.6f;
        s.analH = 0.6f;
        s.analSweep = 0.4f;
        s.pelvicFilament = 0.85f;
        s.filamentColor = 0xC0E8E8E0;
        s.cruise = 20f;
        s.burst = 55f;
        s.tailFreq = 1.6f;
        s.zMin = 0.2f;
        s.zMax = 0.7f;
        s.vband(0.86f, 0.035f, -0.95f, 0.95f, 0.04f, 0xC0242424);
        s.vband(0.6f, 0.045f, -1.02f, 1.02f, 0.03f, 0xB0202020);
        s.vband(0.3f, 0.04f, -1.02f, 1.02f, 0.02f, 0xA0222222);
        s.vband(0.1f, 0.025f, -1.02f, 1.02f, 0f, 0x70303030);
        s.hband(0.2f, 0.85f, -0.6f, 0.2f, 0x30FFFFFF, 0.6f);
        return s;
    }

    /** Red-orange gourami with slanted turquoise bars and long feeler fins. */
    static Species dwarfGourami() {
        Species s = new Species();
        s.name = "Dwarf gourami";
        s.lengthMin = 42.0f;
        s.lengthMax = 47.6f;
        s.topH = 0.27f;
        s.bellyH = 0.25f;
        s.maxAt = 0.48f;
        s.peduncle = 0.32f;
        s.noseRound = 0.6f;
        s.back = 0xFF9C3A26;
        s.side = 0xFFD45530;
        s.belly = 0xFFE89458;
        s.finColor = 0xFFC8503A;
        s.finAlpha = 0.72f;
        s.iris = 0xFFE07040;
        s.eyeSize = 0.055f;
        s.scales = true;
        s.tailLen = 0.3f;
        s.tailH = 0.2f;
        s.notch = 1.15f;
        s.dorsalA = 0.22f;
        s.dorsalB = 0.6f;
        s.dorsalH = 0.17f;
        s.dorsalSweep = 0.2f;
        s.analA = 0.04f;
        s.analB = 0.62f;
        s.analH = 0.2f;
        s.analSweep = 0.18f;
        s.pelvicFilament = 0.7f;
        s.filamentColor = 0xD0F0A060;
        s.cruise = 22f;
        s.burst = 60f;
        s.tailFreq = 1.8f;
        s.zMin = 0.2f;
        s.zMax = 0.75f;
        for (int i = 0; i < 9; i++) {
            float uc = 0.12f + i * 0.075f;
            s.vband(uc, 0.018f, -0.95f, 0.95f, 0.07f, 0xD838A8D8);
        }
        s.hband(0.6f, 0.95f, 0.45f, 0.35f, 0x9040B0E0, 0.5f);
        s.dots(0xC040B8E8, 0.016f, 22, -0.3f, -0.02f, -1f, 1f, 7, true);
        return s;
    }

    /** Sunset-orange platy with a rounded tail. */
    static Species sunsetPlaty() {
        Species s = new Species();
        s.name = "Sunset platy";
        s.lengthMin = 33.6f;
        s.lengthMax = 39.2f;
        s.topH = 0.24f;
        s.bellyH = 0.2f;
        s.maxAt = 0.52f;
        s.peduncle = 0.42f;
        s.noseRound = 0.55f;
        s.back = 0xFFC0501A;
        s.side = 0xFFEC8228;
        s.belly = 0xFFF6C468;
        s.finColor = 0xFFE05A1C;
        s.finAlpha = 0.7f;
        s.iris = 0xFF404040;
        s.eyeSize = 0.06f;
        s.scales = true;
        s.tailLen = 0.32f;
        s.tailH = 0.22f;
        s.notch = 1.1f;
        s.tailColor = 0xFFD8401A;
        s.tailAlpha = 0.8f;
        s.dorsalA = 0.4f;
        s.dorsalB = 0.62f;
        s.dorsalH = 0.2f;
        s.dorsalSweep = 0.1f;
        s.analA = 0.3f;
        s.analB = 0.42f;
        s.analH = 0.1f;
        s.analSweep = 0.06f;
        s.cruise = 26f;
        s.burst = 70f;
        s.tailFreq = 2.4f;
        s.zMin = 0.1f;
        s.zMax = 0.75f;
        s.hband(0.2f, 0.85f, -0.5f, 0.2f, 0x40FFF0C0, 0.6f);
        return s;
    }

    /** Armoured bottom-dwelling catfish with barbels and speckles. */
    static Species corydoras() {
        Species s = new Species();
        s.name = "Corydoras";
        s.lengthMin = 30.8f;
        s.lengthMax = 35.0f;
        s.topH = 0.24f;
        s.bellyH = 0.11f;
        s.maxAt = 0.62f;
        s.peduncle = 0.34f;
        s.noseRound = 0.7f;
        s.back = 0xFF6E6A5A;
        s.side = 0xFFB0AA92;
        s.belly = 0xFFE4DECA;
        s.finColor = 0xFFD8D4C4;
        s.finAlpha = 0.4f;
        s.iris = 0xFFC8B880;
        s.eyeSize = 0.05f;
        s.eyeU = 0.84f;
        s.tailLen = 0.3f;
        s.tailH = 0.2f;
        s.notch = 0.55f;
        s.dorsalA = 0.5f;
        s.dorsalB = 0.66f;
        s.dorsalH = 0.24f;
        s.dorsalSweep = 0.1f;
        s.analA = 0.18f;
        s.analB = 0.3f;
        s.analH = 0.09f;
        s.analSweep = 0.05f;
        s.adipose = true;
        s.barbels = true;
        s.pectoral = 0.18f;
        s.cruise = 12f;
        s.burst = 45f;
        s.tailFreq = 2.6f;
        s.bottomDweller = true;
        s.zMin = 0f;
        s.zMax = 0.05f;
        s.dots(0xB0302A22, 0.022f, 46, 0.02f, 0.82f, -0.85f, 0.45f, 3, false);
        s.dots(0x90302A22, 0.012f, 14, -0.25f, 0.0f, -0.9f, 0.9f, 5, true);
        s.hband(0.05f, 0.8f, 0.02f, 0.08f, 0x50404030, 1f);
        return s;
    }
}
