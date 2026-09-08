package genesis.core.hash;

/** Integer block coordinates, including floor-correct negative lattice coordinates. */
public strictfp final class Lattice {
    public static final long MAX_COORDINATE = 1L << 40;
    private Lattice() {}

    public static long cell(long coordinate, long spacing) {
        if (spacing <= 0) throw new IllegalArgumentException("Spacing must be positive");
        return Math.floorDiv(coordinate, spacing);
    }

    public static double fraction(long coordinate, long spacing) {
        if (spacing <= 0) throw new IllegalArgumentException("Spacing must be positive");
        return Math.floorMod(coordinate, spacing) / (double) spacing;
    }

    public static void check(long coordinate) {
        if (coordinate < -MAX_COORDINATE || coordinate > MAX_COORDINATE)
            throw new IllegalArgumentException("Coordinate outside supported +/-2^40 block domain");
    }
}
