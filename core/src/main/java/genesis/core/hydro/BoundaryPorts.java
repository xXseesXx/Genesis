package genesis.core.hydro;

import genesis.core.Params;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;

/** Canonical shared-face geometry, independent of any requested tile or generation order. */
public final class BoundaryPorts {
    private static final long DOMAIN = 0x504f525453L;
    public static final int MAX_LEVEL = 20;
    public static final int VERTICAL = 0, HORIZONTAL = 1;
    private final long seed;
    private final int baseSpacing;
    public BoundaryPorts(long seed, Params params) {
        this.seed = Hash64.stream(seed, DOMAIN); baseSpacing = params.integer("coarseSpacing");
    }
    public long spacing(int level) {
        if (level < 0 || level > MAX_LEVEL) throw new IllegalArgumentException("Port level must be 0..20");
        return (long) baseSpacing << level;
    }
    /** Exact identity is (level, axis, i, j), not a potentially colliding hash. */
    public static final class Face {
        public final int level, axis;
        public final long i, j;
        public Face(int level, int axis, long i, long j) {
            if (axis != VERTICAL && axis != HORIZONTAL) throw new IllegalArgumentException("Invalid face axis");
            this.level = level; this.axis = axis; this.i = i; this.j = j;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Face)) return false;
            Face f = (Face) other; return level == f.level && axis == f.axis && i == f.i && j == f.j;
        }
        @Override public int hashCode() { return (int) Hash64.hash(level, axis, i, j); }
    }
    public static final class Port {
        public final Face owner;
        public final long x, z;
        private Port(Face owner, long x, long z) { this.owner = owner; this.x = x; this.z = z; }
    }
    /** Cell-side convention: 1 north, 2 east, 3 south, 4 west. */
    public Face face(int level, long i, long j, int side) {
        spacing(level);
        switch (side) {
            case 1: return new Face(level, HORIZONTAL, i, j);
            case 2: return new Face(level, VERTICAL, Math.addExact(i, 1), j);
            case 3: return new Face(level, HORIZONTAL, i, Math.addExact(j, 1));
            case 4: return new Face(level, VERTICAL, i, j);
            default: throw new IllegalArgumentException("Side must be 1..4");
        }
    }
    public Port port(Face face) {
        long s = spacing(face.level), x = Math.multiplyExact(face.i, s), z = Math.multiplyExact(face.j, s);
        Lattice.check(x); Lattice.check(z);
        Lattice.check(Math.addExact(face.axis == VERTICAL ? z : x, s));
        long hash = Hash64.hash(Hash64.stream(seed, face.axis), face.level, face.i, face.j);
        // Central-half support avoids outer corners. Standard spacings produce odd
        // offsets. Exclude base multiples for arbitrary custom spacings as well:
        // every finer grid spacing is itself a multiple of baseSpacing.
        long offset = s / 4 + 1 + 2 * Math.floorMod(hash, s / 4);
        if (offset % baseSpacing == 0) offset++;
        return new Port(face, face.axis == VERTICAL ? x : x + offset, face.axis == VERTICAL ? z + offset : z);
    }
    /** Find the unique child face containing an inherited commitment. Keep this Port's
     * original identity and coordinates: hashing childFace again would create a NEW port.
     */
    public Face childFace(Port inherited, int childLevel) {
        if (childLevel < 0 || childLevel >= inherited.owner.level)
            throw new IllegalArgumentException("Child level must be finer than the port owner");
        long s = spacing(childLevel);
        return new Face(childLevel, inherited.owner.axis, Math.floorDiv(inherited.x, s), Math.floorDiv(inherited.z, s));
    }
}
