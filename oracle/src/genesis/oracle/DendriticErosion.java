package genesis.oracle;

import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;

/**
 * Point-evaluable, drainage-guided dendritic relief for the experimental terrain fork.
 *
 * <p>The directional stripe construction is inspired by Rune Skovbo Johansen's Phacelle erosion
 * filter. It is deliberately used only for terrain morphology: the canonical continental root
 * remains authoritative for drainage topology, lakes and channel discharge. Final channel carving
 * therefore happens after this field is sampled and cannot be blocked by a decorative gully.
 *
 * <p>This is an independent Java implementation rather than a shader translation. Every sample is
 * derived from absolute world coordinates and immutable root state; request windows and chunks are
 * never inputs.
 */
public final class DendriticErosion {
    public static final String VERSION = "dendritic-erosion-v2";
    // 128, 64, 32 and 16 block wavelengths at the default hydrology spacing.
    // An 8-block fifth octave looked granular instead of dendritic in overview renders.
    public static final int OCTAVES = 4;
    private static final long DOMAIN = 0x44454e4452495449L;
    private static final double TWO_PI = StrictMath.PI * 2;

    /** Final surface plus useful diagnostics for viewer and determinism gates. */
    public record Sample(double height, double delta, double ridgeMap, double guideX, double guideZ,
                         double strength) {
        public Sample {
            if (!Double.isFinite(height) || !Double.isFinite(delta) || !Double.isFinite(ridgeMap)
                || !Double.isFinite(guideX) || !Double.isFinite(guideZ) || !Double.isFinite(strength)
                || strength < 0 || strength > 1) {
                throw new IllegalArgumentException("Invalid dendritic erosion sample");
            }
        }
    }

    private record Stripe(double value, double derivative, double coherence) {}
    private record Vector(double x, double z) {}

    private final TectonicTerrain world;
    private final long seed;
    private final HydrologyTuning tuning;

    public DendriticErosion(TectonicTerrain world) {
        this(world,HydrologyTuning.defaults());
    }

    public DendriticErosion(TectonicTerrain world,HydrologyTuning tuning) {
        if (world == null) throw new IllegalArgumentException("Terrain required");
        if (tuning == null) throw new IllegalArgumentException("Hydrology tuning required");
        this.world = world;this.tuning=tuning;
        seed = Hash64.stream(world.seed, DOMAIN);
    }

    /**
     * Applies bounded dendritic detail to the already eroded continental surface.
     * Maritime cells, lake cells and low coastal ground are unchanged.
     */
    public Sample sample(ContinentalHydrology.Root root, long x, long z, double baseHeight) {
        Lattice.check(x);
        Lattice.check(z);
        if (root == null || !Double.isFinite(baseHeight)) throw new IllegalArgumentException("Root and finite base height required");
        if (!tuning.dendriticRelief() || tuning.dendriticStrengthPercent() == 0)
            return new Sample(baseHeight, 0, 0, 0, 0, 0);
        int p = root.index(x, z);
        if (!root.active(p) || root.sea(p) || root.status(p) != 1 || root.filled(p) > root.bed(p)) {
            return new Sample(baseHeight, 0, 0, 0, 0, 0);
        }

        int gradientStep = Math.max(2, root.step / 8);
        double west = base(root, x - gradientStep, z);
        double east = base(root, x + gradientStep, z);
        double north = base(root, x, z - gradientStep);
        double south = base(root, x, z + gradientStep);
        double gx = (east - west) / (2.0 * gradientStep);
        double gz = (south - north) / (2.0 * gradientStep);
        double slope = StrictMath.hypot(gx, gz);

        Vector receiver = guide(root, x, z);
        Vector descent = normalized(-gx, -gz);
        Vector guide = normalized(descent.x * .58 + receiver.x * .42, descent.z * .58 + receiver.z * .42);
        if (length(guide) == 0) guide = receiver;
        if (length(guide) == 0) return new Sample(baseHeight, 0, 0, 0, 0, 0);

        double coast = smoothstep(world.seaLevel() + 2.0 * world.settings.size(),
                                  world.seaLevel() + 18.0 * world.settings.size(), baseHeight);
        double slopeMask = smoothstep(.012, .16, slope);
        double material = .48 + .52 * (1 - root.hardness(p));
        double rain = .58 + .42 * Math.min(1, root.rainfall(p) / 1800.0);
        double strength = clamp(coast * slopeMask * material * rain, 0, 1);
        if (strength <= 1e-9) return new Sample(baseHeight, 0, 0, guide.x, guide.z, 0);

        double wavelength = root.step * 4.0;
        double amplitude = Math.min(9.0 * world.settings.size(), wavelength * .065) * strength * tuning.dendriticStrength();
        double delta = 0;
        double ridge = 0;
        double combiMask = 1;
        double fadeTarget = clamp((baseHeight - world.seaLevel() - 28.0 * world.settings.size())
                                  / (58.0 * world.settings.size()), -1, 1);
        double currentGx = gx, currentGz = gz;

        for (int octave = 0; octave < OCTAVES && wavelength >= 4; octave++) {
            Vector localDescent = normalized(-currentGx, -currentGz);
            double guideWeight = Math.max(.24, .62 - octave * .085);
            Vector direction = normalized(localDescent.x * (1 - guideWeight) + guide.x * guideWeight,
                                          localDescent.z * (1 - guideWeight) + guide.z * guideWeight);
            if (length(direction) == 0) direction = guide;

            Stripe stripe = stripe(x, z, direction, wavelength, octave);
            double octaveSlope = StrictMath.hypot(currentGx, currentGz);
            double active = easeOut(Math.min(1, octaveSlope / (.055 + octave * .012))) * combiMask;
            double shaped = fadeTarget + (stripe.value - fadeTarget) * active;

            // A slight negative bias makes this read as incision rather than embossed noise.
            delta += amplitude * (.58 * shaped - .20);
            ridge = shaped;

            double nx = -direction.z, nz = direction.x;
            double derivative = amplitude * .58 * stripe.derivative * active;
            currentGx += nx * derivative;
            currentGz += nz * derivative;

            // Later octaves fade at established creases/ridges instead of shredding them.
            double newMask = easeOut(Math.min(1, Math.abs(stripe.derivative) * wavelength * .40));
            combiMask = inversePower(combiMask, 1.45) * newMask;
            fadeTarget = shaped;
            wavelength *= .5;
            amplitude *= .52;
        }

        // Preserve coast sign and prevent fine decoration from overwhelming macro relief.
        double cap = Math.min(14.0 * world.settings.size(), Math.max(0, (baseHeight - world.seaLevel()) * .32));
        delta = clamp(delta, -cap, cap);
        double result = Math.max(world.seaLevel() + .001, baseHeight + delta);
        return new Sample(result, result - baseHeight, ridge, guide.x, guide.z, strength);
    }

    private double base(ContinentalHydrology.Root root, long x, long z) {
        Lattice.check(x);
        Lattice.check(z);
        var raw = world.sample(x, z);
        return root.elevation(x, z, raw.elevation(), world.seaLevel());
    }

    /** Bilinearly blends canonical receiver directions, avoiding angle-wrap seams. */
    private static Vector guide(ContinentalHydrology.Root root, long x, long z) {
        long gx = Math.floorDiv(x - root.bounds.x(), root.step);
        long gz = Math.floorDiv(z - root.bounds.z(), root.step);
        double tx = Math.floorMod(x - root.bounds.x(), root.step) / (double) root.step;
        double tz = Math.floorMod(z - root.bounds.z(), root.step) / (double) root.step;
        double vx = 0, vz = 0, total = 0;
        for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++) {
            long ix = gx + dx, iz = gz + dz;
            if (ix < 0 || iz < 0 || ix >= root.bounds.width() || iz >= root.bounds.height()) continue;
            int p = Math.toIntExact(iz * root.bounds.width() + ix);
            if (!root.active(p) || root.sea(p)) continue;
            int q = root.downstream(p);
            if (q < 0) continue;
            Vector edge = normalized(root.x(q) - root.x(p), root.z(q) - root.z(p));
            double weight = (dx == 0 ? 1 - tx : tx) * (dz == 0 ? 1 - tz : tz);
            vx += edge.x * weight;
            vz += edge.z * weight;
            total += weight;
        }
        return total == 0 ? new Vector(0, 0) : normalized(vx / total, vz / total);
    }

    /** Compact 5x5 directional phase-cell blend with partial amplitude normalization. */
    private Stripe stripe(long x, long z, Vector direction, double wavelength, int octave) {
        double cell = wavelength * .72;
        long ci = (long) StrictMath.floor(x / cell), cj = (long) StrictMath.floor(z / cell);
        double normalX = -direction.z, normalZ = direction.x;
        double cosine = 0, sine = 0, weights = 0;
        // With anchor jitter <= .42 cells, an anchor outside this 5x5 window is at least
        // 2.08 cells away when window membership changes. A 2-cell support radius therefore
        // reaches zero before an anchor enters or leaves, eliminating phase-cell edge seams.
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            long i = ci + dx, j = cj + dz;
            long h = Hash64.hash(seed, octave, i, j);
            double ox = Hash64.signedUnit(Hash64.mix(h + 0x9e3779b97f4a7c15L)) * .42;
            double oz = Hash64.signedUnit(Hash64.mix(h + 0x632be59bd9b4e019L)) * .42;
            double ax = (i + .5 + ox) * cell, az = (j + .5 + oz) * cell;
            double rx = x - ax, rz = z - az;
            double distance = StrictMath.hypot(rx, rz) / cell;
            double t = Math.max(0, 1 - distance / 2.0);
            double weight = t * t * (3 - 2 * t);
            if (weight == 0) continue;
            double phase = (rx * normalX + rz * normalZ) * TWO_PI / wavelength;
            cosine += StrictMath.cos(phase) * weight;
            sine += StrictMath.sin(phase) * weight;
            weights += weight;
        }
        if (weights == 0) return new Stripe(0, 0, 0);
        cosine /= weights;
        sine /= weights;
        double length = StrictMath.hypot(cosine, sine);
        if (length <= 1e-12) return new Stripe(0, 0, 0);
        // Lengths below .5 remain subdued; stronger phase agreement normalizes to one.
        double target = Math.min(1, length * 2);
        double normalization = target / length;
        cosine *= normalization;
        sine *= normalization;
        return new Stripe(cosine, -sine * TWO_PI / wavelength, target);
    }

    private static Vector normalized(double x, double z) {
        double length = StrictMath.hypot(x, z);
        return length <= 1e-12 ? new Vector(0, 0) : new Vector(x / length, z / length);
    }

    private static double length(Vector value) { return StrictMath.hypot(value.x, value.z); }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private static double smoothstep(double low, double high, double value) {
        double t = clamp((value - low) / (high - low), 0, 1);
        return t * t * (3 - 2 * t);
    }
    private static double easeOut(double value) { double v = 1 - clamp(value, 0, 1); return 1 - v * v; }
    private static double inversePower(double value, double power) {
        return 1 - StrictMath.pow(1 - clamp(value, 0, 1), power);
    }
}
