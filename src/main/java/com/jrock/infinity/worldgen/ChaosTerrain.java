package com.jrock.infinity.worldgen;

import java.util.Arrays;

/**
 * Chaotic terrain for Endless Portals dimensions, in the spirit of 20w14&infin;'s
 * "whacky terrain ... and funky flora made of random blocks".
 *
 * <p><b>No Minecraft imports on purpose</b>: it returns abstract material codes, and
 * {@code InfinityChunkGenerator} maps them to block states. That keeps the whole thing runnable (and
 * testable) with plain {@code java}.
 *
 * <p>Everything is a pure function of {@code (seed, style, x, z)}: no mutable state, so it is safe
 * to call from several worldgen threads and neighbouring chunks always line up.
 *
 * <h3>Styles</h3>
 * <ol start="0">
 *   <li><b>Patchwork</b>  - Voronoi regions that each pick a different landform (plains, hills, mesa,
 *       lake, spires, lava pit, bottomless chasm, terraces), blended at the borders, in mismatched colors.</li>
 *   <li><b>Warped</b>     - domain-warped mountains with real 3-D overhangs and arches.</li>
 *   <li><b>Swiss</b>      - ground riddled with spaghetti tunnels, huge caverns, lava and bottomless shafts.</li>
 *   <li><b>Shattered</b>  - lens-shaped islands drifting at many altitudes over the void.</li>
 *   <li><b>Strata</b>     - terraced mesas and canyons striped in bands of random blocks.</li>
 *   <li><b>Spires</b>     - needle peaks rising out of a sea, pierced by natural arches.</li>
 *   <li><b>Mixed sea</b>  - continents and archipelagos; each ocean is water, lava or ice-capped.</li>
 *   <li><b>Cracked</b>    - a tiled plateau split by deep V-shaped, lava-bottomed canyons.</li>
 * </ol>
 * Land-based styles (0, 6, 7) also grow flora out of random blocks: poles, blob trees, mushrooms,
 * crystals and boulders.
 */
public final class ChaosTerrain {

    public static final int STYLE_COUNT = 8;
    /** Out of 256: how often a dimension uses a chaos style instead of one of the 18 classic ones. */
    public static final int CHAOS_CHANCE_256 = 166; // about 65 %

    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    public static final int MAX_Y = MIN_Y + HEIGHT - 1;

    // ---- material codes (the chunk generator maps these to blocks) ----------------------------
    public static final int AIR = 0, BEDROCK = 1, WATER = 2, LAVA = 3, ICE = 4;
    public static final int PRIMARY = 5, SURFACE = 6, SUBSURFACE = 7, GLOW = 8;
    public static final int STRATA_BASE = 9, STRATA_COUNT = 8;   // 9..16
    public static final int FLORA_BASE = 17, FLORA_COUNT = 4;    // 17..20
    public static final int MATERIAL_COUNT = 21;

    private static final int SOLID = -1; // internal, replaced by shade()

    /** Return-portal landing pad, always carved out and floored. */
    private static final int PAD_X = 8, PAD_Z = 8, PAD_R = 8, PAD_Y = 63;

    private final long seed;
    private final int style;
    private final double hs;       // horizontal scale multiplier (per dimension)
    private final double vs;       // vertical amplitude multiplier (per dimension)
    private final int seaLevel;
    private final int lavaLevel;

    public ChaosTerrain(long seed, int style) {
        this.seed = seed;
        this.style = Math.floorMod(style, STYLE_COUNT);
        long h = mix(seed ^ 0xA5A5A5A5DEADBEEFL);
        this.hs = 0.7 + 0.9 * unit(h);
        this.vs = 0.8 + 0.5 * unit(mix(h));
        this.seaLevel = 52 + (int) (unit(mix(h + 1)) * 26);
        this.lavaLevel = -6 + (int) (unit(mix(h + 2)) * 26);
    }

    public int style() { return style; }

    // ============================================================================== public API

    /** Fills {@code out[0..HEIGHT)} with the material codes of column (x, z); index = y - MIN_Y. */
    public void column(int x, int z, int[] out) {
        Arrays.fill(out, 0, HEIGHT, AIR);
        int[] top = {-1};
        switch (style) {
            case 0 -> patchwork(x, z, out, top);
            case 1 -> warped(x, z, out);
            case 2 -> swiss(x, z, out);
            case 3 -> shattered(x, z, out);
            case 4 -> strata(x, z, out);
            case 5 -> spires(x, z, out);
            case 6 -> mixedSea(x, z, out);
            default -> cracked(x, z, out, top);
        }
        shade(x, z, out, top[0]);
        if (style == 0 || style == 6 || style == 7) flora(x, z, out);
        bedrock(x, z, out);
        landingPad(x, z, out);
    }

    /** Y of the highest solid block (ignoring fluids), or MIN_Y if the column is empty. */
    public int surfaceY(int x, int z) {
        int[] col = new int[HEIGHT];
        column(x, z, col);
        for (int i = HEIGHT - 1; i >= 0; i--) {
            int m = col[i];
            if (m != AIR && m != WATER && m != LAVA) return MIN_Y + i;
        }
        return MIN_Y;
    }

    // ================================================================================ hashing

    /** SplitMix64 finaliser. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static double unit(long h) { return (h >>> 11) * (1.0 / (1L << 53)); }

    private double rnd(long x, long y, long z, int salt) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL)
                 ^ (z * 0x165667B19E3779F9L) ^ ((long) salt * 0xD6E8FEB86659FD93L);
        return unit(mix(h));
    }

    // =================================================================================== noise

    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + t * (b - a); }
    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }

    /** 2-D value noise in [-1, 1]. */
    private double vn2(double x, double z, int salt) {
        long ix = (long) Math.floor(x), iz = (long) Math.floor(z);
        double fx = smooth(x - ix), fz = smooth(z - iz);
        double a = rnd(ix, 0, iz, salt), b = rnd(ix + 1, 0, iz, salt);
        double c = rnd(ix, 0, iz + 1, salt), d = rnd(ix + 1, 0, iz + 1, salt);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fz) * 2 - 1;
    }

    /** 3-D value noise in [-1, 1]. */
    private double vn3(double x, double y, double z, int salt) {
        long ix = (long) Math.floor(x), iy = (long) Math.floor(y), iz = (long) Math.floor(z);
        double fx = smooth(x - ix), fy = smooth(y - iy), fz = smooth(z - iz);
        double c000 = rnd(ix, iy, iz, salt),         c100 = rnd(ix + 1, iy, iz, salt);
        double c010 = rnd(ix, iy + 1, iz, salt),     c110 = rnd(ix + 1, iy + 1, iz, salt);
        double c001 = rnd(ix, iy, iz + 1, salt),     c101 = rnd(ix + 1, iy, iz + 1, salt);
        double c011 = rnd(ix, iy + 1, iz + 1, salt), c111 = rnd(ix + 1, iy + 1, iz + 1, salt);
        double x00 = lerp(c000, c100, fx), x10 = lerp(c010, c110, fx);
        double x01 = lerp(c001, c101, fx), x11 = lerp(c011, c111, fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz) * 2 - 1;
    }

    /** Fractal 2-D noise, roughly [-1, 1]. Each octave is rotated so the lattice never shows. */
    private double fbm2(double x, double z, int octaves, int salt) {
        double sum = 0, amp = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * vn2(x, z, salt + i * 31);
            norm += amp;
            double nx = x * 1.6 - z * 1.2 + 31.7, nz = x * 1.2 + z * 1.6 - 17.3; // rotate + 2.0x freq
            x = nx; z = nz;
            amp *= 0.5;
        }
        return sum / norm;
    }

    private double fbm3(double x, double y, double z, int octaves, int salt) {
        double sum = 0, amp = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * vn3(x, y, z, salt + i * 37);
            norm += amp;
            double nx = x * 1.6 - z * 1.2 + 11.3, nz = x * 1.2 + z * 1.6 - 29.1;
            x = nx; z = nz; y = y * 2.0 + 7.9;
            amp *= 0.5;
        }
        return sum / norm;
    }

    /** Ridged noise in [0, 1]: sharp crests where the underlying noise crosses zero. */
    private double ridged2(double x, double z, int octaves, int salt) {
        double sum = 0, amp = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            double r = 1 - Math.abs(vn2(x, z, salt + i * 41));
            sum += amp * r * r;
            norm += amp;
            double nx = x * 1.6 - z * 1.2 + 5.1, nz = x * 1.2 + z * 1.6 + 23.9;
            x = nx; z = nz;
            amp *= 0.5;
        }
        return sum / norm;
    }

    // ================================================================================ voronoi

    /** Jittered-grid Voronoi: nearest two feature points. */
    private static final class Vor {
        double d1 = 1e18, d2 = 1e18;
        long c1x, c1z, c2x, c2z;
    }

    private Vor voronoi(double x, double z, double cell, int salt) {
        Vor v = new Vor();
        long gx = (long) Math.floor(x / cell), gz = (long) Math.floor(z / cell);
        for (long dz = -1; dz <= 1; dz++) {
            for (long dx = -1; dx <= 1; dx++) {
                long cx = gx + dx, cz = gz + dz;
                double px = (cx + 0.15 + 0.7 * rnd(cx, 1, cz, salt)) * cell;
                double pz = (cz + 0.15 + 0.7 * rnd(cx, 2, cz, salt)) * cell;
                double d = Math.hypot(px - x, pz - z);
                if (d < v.d1) {
                    v.d2 = v.d1; v.c2x = v.c1x; v.c2z = v.c1z;
                    v.d1 = d; v.c1x = cx; v.c1z = cz;
                } else if (d < v.d2) {
                    v.d2 = d; v.c2x = cx; v.c2z = cz;
                }
            }
        }
        return v;
    }

    // ============================================================================== styles

    private static void solidTo(int[] out, int topY) {
        int hi = Math.min(topY, MAX_Y);
        for (int y = MIN_Y; y <= hi; y++) out[y - MIN_Y] = SOLID;
    }

    private static void fluid(int[] out, int fromY, int toY, int mat) {
        int lo = Math.max(fromY, MIN_Y), hi = Math.min(toY, MAX_Y);
        for (int y = lo; y <= hi; y++) if (out[y - MIN_Y] == AIR) out[y - MIN_Y] = mat;
    }

    // ---- 0: PATCHWORK -------------------------------------------------------------------------
    private int patchKind(long cx, long cz) { return (int) (rnd(cx, 9, cz, 101) * 8); }

    private double patchHeight(int kind, long cx, long cz, double x, double z) {
        return switch (kind) {
            case 0 -> 66 + 5 * vs * fbm2(x * 0.02 * hs, z * 0.02 * hs, 3, 111);                       // plains
            case 1 -> 72 + 34 * vs * fbm2(x * 0.011 * hs, z * 0.011 * hs, 5, 112);                     // hills
            case 2 -> 120 + 3 * fbm2(x * 0.05, z * 0.05, 2, 113);                                      // mesa
            case 3 -> 44 + 7 * fbm2(x * 0.03, z * 0.03, 3, 114);                                       // lake basin
            case 4 -> 38 + 125 * vs * Math.pow(ridged2(x * 0.012 * hs, z * 0.012 * hs, 4, 115), 3.0);  // spires
            case 5 -> 34 + 6 * fbm2(x * 0.04, z * 0.04, 3, 116);                                       // lava pit
            case 6 -> MIN_Y;                                                                            // chasm
            default -> {                                                                                // terraces
                double v = fbm2(x * 0.009 * hs, z * 0.009 * hs, 4, 117) * 0.5 + 0.5;
                yield 52 + Math.floor(v * 9) * 7.5 * vs;
            }
        };
    }

    private void patchwork(int x, int z, int[] out, int[] top) {
        Vor v = voronoi(x, z, 84 * hs, 100);
        int k1 = patchKind(v.c1x, v.c1z), k2 = patchKind(v.c2x, v.c2z);
        double h1 = patchHeight(k1, v.c1x, v.c1z, x, z), h2 = patchHeight(k2, v.c2x, v.c2z, x, z);
        double w1 = 0.5 + 0.5 * smooth(clamp((v.d2 - v.d1) / 14.0, 0, 1)); // 0.5 on the border, 1 inside
        double h = h1 * w1 + h2 * (1 - w1);
        solidTo(out, (int) Math.floor(h));
        if (k1 == 3) fluid(out, MIN_Y, 62, WATER);
        if (k1 == 5) fluid(out, MIN_Y, 45, LAVA);
        // every region gets its own top color about half the time
        double c = rnd(v.c1x, 7, v.c1z, 102);
        top[0] = c < 0.55 ? STRATA_BASE + (int) (c / 0.55 * STRATA_COUNT) : -1;
    }

    private double patchFloraGround(double x, double z) {
        Vor v = voronoi(x, z, 84 * hs, 100);
        int k = patchKind(v.c1x, v.c1z);
        if (k == 3 || k == 5 || k == 6 || k == 4) return Double.NaN;
        if (v.d2 - v.d1 < 10) return Double.NaN; // not on region borders
        double h1 = patchHeight(k, v.c1x, v.c1z, x, z);
        return Math.floor(h1);
    }

    // ---- 1: WARPED ----------------------------------------------------------------------------
    private void warped(int x, int z, int[] out) {
        double wx = x + 70 * fbm2(x * 0.004, z * 0.004, 3, 201);
        double wz = z + 70 * fbm2(x * 0.004 + 9.1, z * 0.004 + 5.3, 3, 202);
        double base = 92 + 82 * vs * fbm2(wx * 0.0065 * hs, wz * 0.0065 * hs, 5, 203);
        double amp3 = 44 * vs;
        int lo = (int) Math.floor(base - amp3 * 1.1) - 1, hi = (int) Math.ceil(base + amp3 * 1.1) + 1;
        solidTo(out, lo);
        for (int y = Math.max(lo + 1, MIN_Y); y <= Math.min(hi, MAX_Y); y++) {
            double d = base - y + amp3 * fbm3(wx * 0.021 * hs, y * 0.026, wz * 0.021 * hs, 3, 204);
            if (d > 0) out[y - MIN_Y] = SOLID;
        }
        fluid(out, MIN_Y, seaLevel - 6, WATER);
    }

    // ---- 2: SWISS -----------------------------------------------------------------------------
    private void swiss(int x, int z, int[] out) {
        double h = 96 + 62 * vs * fbm2(x * 0.0065 * hs, z * 0.0065 * hs, 4, 301);
        int top = (int) Math.floor(h);
        solidTo(out, top);
        boolean shaft = vn2(x * 0.016, z * 0.016, 302) > 0.80;
        for (int y = MIN_Y + 3; y <= Math.min(top, MAX_Y); y++) {
            boolean carve = shaft && y > lavaLevel;
            if (!carve) {
                double n1 = vn3(x * 0.028, y * 0.038, z * 0.028, 303);
                double n2 = vn3(x * 0.028, y * 0.038, z * 0.028, 304);
                carve = Math.abs(n1) < 0.16 && Math.abs(n2) < 0.16;                      // spaghetti
                if (!carve) carve = fbm3(x * 0.011, y * 0.015, z * 0.011, 3, 305) > 0.17; // caverns
            }
            if (carve) out[y - MIN_Y] = AIR;
        }
        fluid(out, MIN_Y + 3, lavaLevel, LAVA);
    }

    // ---- 3: SHATTERED -------------------------------------------------------------------------
    private void shattered(int x, int z, int[] out) {
        for (int y = 24; y <= 250; y++) {
            double n = fbm3(x * 0.011 * hs, y * 0.019, z * 0.011 * hs, 3, 401);
            double detail = 0.10 * vn3(x * 0.08, y * 0.08, z * 0.08, 402);
            // mild preference for mid altitudes so the cloud of islands has a "layer" feel
            double bias = 0.00004 * (y - 135) * (y - 135);
            if (n + detail - bias > 0.265) out[y - MIN_Y] = SOLID;
        }
    }

    // ---- 4: STRATA ----------------------------------------------------------------------------
    private void strata(int x, int z, int[] out) {
        double v = fbm2(x * 0.0055 * hs, z * 0.0055 * hs, 4, 501) * 0.5 + 0.5;
        v = Math.pow(clamp(v, 0, 1), 1.3);
        double t = v * 8, f = Math.floor(t), fr = t - f;
        double step = f + smooth(clamp((fr - 0.78) / 0.22, 0, 1));          // flat tiers, steep risers
        double h = 28 + step / 8.0 * 150 * vs;
        int top = (int) Math.floor(h);
        solidTo(out, top);
        // canyons: where a ridged field peaks, cut a trench
        double r = 1 - Math.abs(fbm2(x * 0.0075 * hs, z * 0.0075 * hs, 3, 502));
        if (r > 0.935) {
            int floor = 14 + (int) ((1 - (r - 0.935) / 0.065) * 40);
            for (int y = Math.max(floor, MIN_Y); y <= Math.min(top, MAX_Y); y++) out[y - MIN_Y] = AIR;
            fluid(out, MIN_Y, 12, LAVA);
        }
    }

    // ---- 5: SPIRES ----------------------------------------------------------------------------
    private void spires(int x, int z, int[] out) {
        double rd = ridged2(x * 0.0065 * hs, z * 0.0065 * hs, 4, 601);
        double h = 20 + 235 * vs * Math.pow(clamp(rd, 0, 1), 2.15);
        int top = (int) Math.floor(h);
        solidTo(out, top);
        for (int y = 36; y <= Math.min(top, 170); y++) {                      // natural arches
            if (vn3(x * 0.034, y * 0.048, z * 0.034, 602) > 0.56
                    && vn3(x * 0.012, y * 0.02, z * 0.012, 603) > -0.1) out[y - MIN_Y] = AIR;
        }
        fluid(out, MIN_Y, Math.min(seaLevel, 46), WATER);
    }

    // ---- 6: MIXED SEA -------------------------------------------------------------------------
    private int mixedSeaLevel() { return Math.max(seaLevel, 60); }

    private double mixedSeaHeight(double x, double z) {
        double cont = fbm2(x * 0.0045 * hs, z * 0.0045 * hs, 5, 701);
        double isl = vn2(x * 0.03, z * 0.03, 702);
        double mtn = Math.max(0, fbm2(x * 0.010 * hs, z * 0.010 * hs, 4, 704) + 0.04);
        return 62 + 44 * vs * cont + 330 * vs * mtn * mtn + (isl > 0.55 ? (isl - 0.55) * 40 : 0);
    }

    private void mixedSea(int x, int z, int[] out) {
        double h = mixedSeaHeight(x, z);
        solidTo(out, (int) Math.floor(h));
        int sea = mixedSeaLevel();
        double region = vn2(x * 0.0016, z * 0.0016, 703);
        int mat = region < -0.22 ? LAVA : WATER;
        fluid(out, MIN_Y, sea, mat);
        if (region > 0.38 && sea >= MIN_Y && sea <= MAX_Y) {                  // ice-capped ocean
            for (int y = sea; y >= sea - 2 && y >= MIN_Y; y--) if (out[y - MIN_Y] == WATER) out[y - MIN_Y] = ICE;
        }
    }

    private double mixedFloraGround(double x, double z) {
        double h = mixedSeaHeight(x, z);
        return h > mixedSeaLevel() + 1.5 ? Math.floor(h) : Double.NaN;
    }

    // ---- 7: CRACKED ---------------------------------------------------------------------------
    private double crackWidth(double x, double z) { return 6 + 6 * (vn2(x * 0.01, z * 0.01, 801) * 0.5 + 0.5); }

    private void cracked(int x, int z, int[] out, int[] top) {
        Vor v = voronoi(x, z, 96 * hs, 800);
        double plateau = 96 + 20 * vs * fbm2(x * 0.012, z * 0.012, 4, 802) + 44 * (rnd(v.c1x, 3, v.c1z, 803) - 0.5);
        double edge = v.d2 - v.d1;
        double w = crackWidth(x, z);
        double h = plateau;
        if (edge < w) {
            double k = edge / w;                                     // 0 at the crack, 1 at the lip
            h = lerp(8, plateau, k * k);                             // V-shaped canyon
        }
        solidTo(out, (int) Math.floor(h));
        fluid(out, MIN_Y, 16, LAVA);
        double c = rnd(v.c1x, 7, v.c1z, 804);
        top[0] = c < 0.7 ? STRATA_BASE + (int) (c / 0.7 * STRATA_COUNT) : -1;
    }

    private double crackedFloraGround(double x, double z) {
        Vor v = voronoi(x, z, 96 * hs, 800);
        if (v.d2 - v.d1 < crackWidth(x, z) + 4) return Double.NaN;
        double plateau = 96 + 20 * vs * fbm2(x * 0.012, z * 0.012, 4, 802) + 44 * (rnd(v.c1x, 3, v.c1z, 803) - 0.5);
        return Math.floor(plateau);
    }

    // ================================================================================= shading

    /**
     * Turns the SOLID placeholder into real materials: grass-like top, a few subsurface layers, and an
     * interior that is plain stone with colorful veins (or full stripes for the Strata style).
     */
    private void shade(int x, int z, int[] out, int topOverride) {
        int depth = 0;
        int above = AIR;
        for (int y = MAX_Y; y >= MIN_Y; y--) {
            int i = y - MIN_Y;
            int m = out[i];
            if (m == SOLID) {
                boolean wet = above == WATER || above == LAVA || above == ICE;
                int mat;
                if (style == 4) {
                    // tiers are striped in bands; the very top of a tier uses SURFACE
                    mat = depth == 0 && !wet ? SURFACE : strataBand(x, y, z);
                } else if (depth == 0) {
                    mat = wet ? SUBSURFACE : (topOverride >= 0 ? topOverride : SURFACE);
                } else if (depth <= 3) {
                    mat = topOverride >= 0 && depth == 1 && !wet ? topOverride : SUBSURFACE;
                } else {
                    mat = interior(x, y, z);
                }
                out[i] = mat;
                depth++;
            } else {
                depth = 0;
            }
            above = out[i];
        }
    }

    private int strataBand(int x, int y, int z) {
        double warp = 3.5 * vn2(x * 0.03, z * 0.03, 901) + 1.5 * vn2(x * 0.11, z * 0.11, 902);
        int band = (int) Math.floor((y + warp) / 3.0);
        return STRATA_BASE + Math.floorMod(band * 5 + 3, STRATA_COUNT);
    }

    private int interior(int x, int y, int z) {
        // colorful veins of "random blocks" running through the stone
        double v = vn3(x * 0.075, y * 0.075, z * 0.075, 910);
        if (v > 0.72) return STRATA_BASE + Math.floorMod((int) Math.floor(vn3(x * 0.02, y * 0.02, z * 0.02, 911) * 40), STRATA_COUNT);
        if (rnd(x, y, z, 912) > 0.9992) return GLOW;
        return PRIMARY;
    }

    private void bedrock(int x, int z, int[] out) {
        out[0] = BEDROCK;
        if (rnd(x, 1, z, 920) < 0.5) out[1] = BEDROCK;
        if (rnd(x, 2, z, 921) < 0.25) out[2] = BEDROCK;
    }

    private void landingPad(int x, int z, int[] out) {
        int dx = x - PAD_X, dz = z - PAD_Z;
        int d2 = dx * dx + dz * dz;
        if (d2 > PAD_R * PAD_R) return;
        for (int y = PAD_Y + 1; y <= PAD_Y + 90; y++) out[y - MIN_Y] = AIR;     // headroom for the portal
        out[PAD_Y - MIN_Y] = SURFACE;
        for (int y = PAD_Y - 1; y >= PAD_Y - 3; y--) out[y - MIN_Y] = SUBSURFACE;
        for (int y = PAD_Y - 4; y >= PAD_Y - 12; y--) {
            int i = y - MIN_Y;
            if (out[i] == AIR || out[i] == WATER || out[i] == LAVA) out[i] = PRIMARY;
        }
    }

    // =================================================================================== flora

    private static final int FLORA_CELL = 7;

    private double floraGround(double x, double z) {
        return switch (style) {
            case 0 -> patchFloraGround(x, z);
            case 6 -> mixedFloraGround(x, z);
            default -> crackedFloraGround(x, z);
        };
    }

    private int floraMat(int variant) { return FLORA_BASE + Math.floorMod(variant, FLORA_COUNT); }

    private void flora(int x, int z, int[] out) {
        long gx = Math.floorDiv(x, FLORA_CELL), gz = Math.floorDiv(z, FLORA_CELL);
        for (long cz = gz - 1; cz <= gz + 1; cz++) {
            for (long cx = gx - 1; cx <= gx + 1; cx++) {
                if (rnd(cx, 50, cz, 950) > 0.34) continue;
                int fx = (int) (cx * FLORA_CELL + (int) (rnd(cx, 51, cz, 951) * FLORA_CELL));
                int fz = (int) (cz * FLORA_CELL + (int) (rnd(cx, 52, cz, 952) * FLORA_CELL));
                int dx = x - fx, dz = z - fz;
                if (Math.abs(dx) > 4 || Math.abs(dz) > 4) continue;
                if (Math.hypot(fx - PAD_X, fz - PAD_Z) < PAD_R + 4) continue;
                double g = floraGround(fx, fz);
                if (Double.isNaN(g)) continue;
                int gy = (int) g;
                if (gy < 50 || gy > 200) continue;
                growFlora(cx, cz, gy, dx, dz, out);
            }
        }
    }

    private void put(int[] out, int y, int mat) {
        if (y < MIN_Y || y > MAX_Y) return;
        if (out[y - MIN_Y] == AIR) out[y - MIN_Y] = mat;
    }

    private void growFlora(long cx, long cz, int gy, int dx, int dz, int[] out) {
        int kind = (int) (rnd(cx, 60, cz, 960) * 5);
        int v = (int) (rnd(cx, 61, cz, 961) * 1000);
        double r = rnd(cx, 62, cz, 962);
        switch (kind) {
            case 0 -> { // POLE: a tall striped pillar
                if (dx != 0 || dz != 0) return;
                int hgt = 6 + (int) (r * 18);
                for (int k = 1; k <= hgt; k++) put(out, gy + k, floraMat(v + k / 2));
            }
            case 1 -> { // BLOB TREE
                int trunk = 4 + (int) (r * 6), rad = 2 + (v % 2);
                if (dx == 0 && dz == 0) for (int k = 1; k <= trunk; k++) put(out, gy + k, floraMat(v));
                int cy = gy + trunk + 1;
                for (int y = cy - rad; y <= cy + rad; y++) {
                    double d = dx * dx + dz * dz + (y - cy) * (y - cy) * 1.5;
                    if (d <= rad * rad + 0.5) put(out, y, floraMat(v + 1));
                }
            }
            case 2 -> { // MUSHROOM
                int stem = 3 + (int) (r * 6), rad = 3;
                if (dx == 0 && dz == 0) for (int k = 1; k <= stem; k++) put(out, gy + k, floraMat(v + 2));
                int d2 = dx * dx + dz * dz;
                if (d2 <= rad * rad) put(out, gy + stem + 1, floraMat(v + 3));
                if (d2 <= (rad - 1) * (rad - 1) + 1 && d2 >= 1) put(out, gy + stem, floraMat(v + 3));
            }
            case 3 -> { // CRYSTAL SPIKE, glowing at the core
                int hgt = 4 + (int) (r * 8);
                for (int k = 1; k <= hgt; k++) {
                    double rr = 2.4 * (1.0 - (double) k / hgt);
                    if (dx * dx + dz * dz <= rr * rr) {
                        put(out, gy + k, dx == 0 && dz == 0 && k % 3 == 0 ? GLOW : floraMat(v + 3));
                    }
                }
            }
            default -> { // BOULDER, half sunk into the ground
                int rad = 2 + (v % 3);
                for (int y = gy - 1; y <= gy + rad; y++) {
                    double d = dx * dx + dz * dz + (y - gy) * (y - gy) * 1.2;
                    if (d <= rad * rad) put(out, y, PRIMARY);
                }
            }
        }
    }
}
