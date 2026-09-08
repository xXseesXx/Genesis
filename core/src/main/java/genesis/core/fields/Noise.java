package genesis.core.fields;

import genesis.core.Params;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;

/** Smooth value noise for M0 diagnostics only; it makes no drainage commitments. */
public strictfp final class Noise implements Field<Double> {
    // Stable stream identifier is part of the hash protocol, not a tuning parameter.
    private static final long DOMAIN = 0x4e4f495345L;
    private final long seed;
    private final Params params;
    public Noise(long seed, Params params) { this.seed = Hash64.stream(seed, DOMAIN); this.params = params; }

    @Override public Double sample(long x, long z) {
        long spacing = params.integer("wavelength");
        double weight = 1, total = 0, weights = 0;
        for (int octave = 0; octave < params.integer("octaves"); octave++) {
            long i = Lattice.cell(x, spacing), j = Lattice.cell(z, spacing);
            double u = fade(Lattice.fraction(x, spacing)), v = fade(Lattice.fraction(z, spacing));
            double a = Hash64.signedUnit(Hash64.hash(seed, octave, i, j));
            double b = Hash64.signedUnit(Hash64.hash(seed, octave, i + 1, j));
            double c = Hash64.signedUnit(Hash64.hash(seed, octave, i, j + 1));
            double d = Hash64.signedUnit(Hash64.hash(seed, octave, i + 1, j + 1));
            total += lerp(lerp(a, b, u), lerp(c, d, u), v) * weight;
            weights += weight;
            weight *= params.get("persistence");
            spacing = Math.max(1, spacing / params.integer("lacunarity"));
        }
        return total / weights * params.get("amplitude");
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    // Quintic interpolation coefficients are mathematical constants, exempt from G4 tuning lint.
    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
}
