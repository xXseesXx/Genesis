package genesis.core.hash;

/** Versioned integer hash; constants are algorithm constants, never terrain tuning. */
public strictfp final class Hash64 {
    private Hash64() {}

    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public static long hash(long seed, int level, long i, long j) {
        long h = mix(seed ^ 0x9e3779b97f4a7c15L);
        h = mix(h ^ mix(level ^ 0x632be59bd9b4e019L));
        h = mix(h ^ mix(i ^ 0x8cb92baa3f3d8dd7L));
        return mix(h ^ mix(j ^ 0xdb4f0b9175ae2165L));
    }

    /** Domain separation: adding a consumer never consumes another field's stream. */
    public static long stream(long seed, long domain) { return mix(seed ^ mix(domain)); }

    public static double signedUnit(long hash) {
        return (hash >>> 11) * 0x1.0p-53 * 2.0 - 1.0;
    }
}
