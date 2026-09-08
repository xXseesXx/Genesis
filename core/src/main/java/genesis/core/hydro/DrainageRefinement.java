package genesis.core.hydro;

import genesis.core.Params;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import java.util.Arrays;

/** One conditioned 2x2 subdivision. Parent topology, ports, and external-only flux
 * are inputs, not discovered from a viewport. No global root or terrain policy lives here.
 */
public final class DrainageRefinement {
    public static final String VERSION = "refinement-v1";
    private static final long DOMAIN = 0x524546494e45L;
    private static final int CHILDREN = 4;
    private final long seed;
    private final BoundaryPorts ports;
    public DrainageRefinement(long seed, Params params) {
        this.seed = Hash64.stream(seed, DOMAIN); ports = new BoundaryPorts(seed, params);
    }
    public static final class Inflow {
        public final BoundaryPorts.Port port;
        public final long units;
        public Inflow(BoundaryPorts.Port port, long units) {
            if (port == null || units < 0) throw new IllegalArgumentException("Inflow needs a port and nonnegative external units");
            this.port = port; this.units = units;
        }
    }
    public static final class Child {
        public final long i, j, distance, localRain, externalInflow, outflow;
        public final int level, downstream, resistance;
        public final BoundaryPorts.Port exit;
        private Child(long i, long j, int level, int downstream, int resistance, long distance,
                      long rain, long external, long outflow, BoundaryPorts.Port exit) {
            this.i = i; this.j = j; this.level = level; this.downstream = downstream;
            this.resistance = resistance; this.distance = distance; localRain = rain;
            externalInflow = external; this.outflow = outflow; this.exit = exit;
        }
    }
    public static final class Result {
        private final Child[] children;
        private final Inflow[] entries;
        public final int level, root;
        public final long i, j, localRain, externalInflow, outflow;
        public final BoundaryPorts.Port exit;
        private Result(int level, long i, long j, int root, Child[] children, Inflow[] entries,
                       long rain, long external, long outflow, BoundaryPorts.Port exit) {
            this.level = level; this.i = i; this.j = j; this.root = root; this.children = children;
            this.entries = entries; localRain = rain; externalInflow = external; this.outflow = outflow; this.exit = exit;
        }
        public Child child(int index) { return children[index]; }
        public Child[] children() { return children.clone(); }
        public Inflow[] entries() { return entries.clone(); }
    }
    /** localRain belongs to THIS parent only. committedOutflow must equal localRain plus
     * the external inflows. Inputs must not inject a parent's own rainfall a second time.
     * Resistance is four nonnegative integers in NW,NE,SW,SE order, not floating elevation.
     */
    public Result refine(int level, long i, long j, BoundaryPorts.Port exit, Inflow[] incoming,
                         long localRain, long committedOutflow, int[] resistance) {
        if (level < 1 || localRain < 0 || committedOutflow < 0 || incoming == null || incoming.length > CHILDREN - 1
            || resistance == null || resistance.length != CHILDREN)
            throw new IllegalArgumentException("Invalid parent refinement contract");
        long s = ports.spacing(level), x = Math.multiplyExact(i, s), z = Math.multiplyExact(j, s);
        Lattice.check(x); Lattice.check(z); Lattice.check(Math.addExact(x,s)); Lattice.check(Math.addExact(z,s));
        int exitSide = side(level,i,j,exit);
        Inflow[] bySide = new Inflow[CHILDREN];
        int[] costs = resistance.clone();
        for (int cost : costs) if (cost < 0) throw new IllegalArgumentException("Negative child resistance");
        long external = 0;
        for (Inflow entry : incoming.clone()) {
            if (entry == null) throw new IllegalArgumentException("Null inflow");
            int side = side(level,i,j,entry.port);
            if (side == exitSide || bySide[side - 1] != null) throw new IllegalArgumentException("Duplicate or bidirectional boundary port");
            bySide[side - 1] = entry; external = Math.addExact(external,entry.units);
        }
        if (Math.addExact(localRain,external) != committedOutflow)
            throw new IllegalArgumentException("Parent outflow does not equal local rain plus external inflow");
        Inflow[] entries = new Inflow[incoming.length]; int count = 0;
        for (Inflow entry : bySide) if (entry != null) entries[count++] = entry;
        int root = owner(exit,exitSide,x,z,s);
        long[] rain = new long[CHILDREN], injection = new long[CHILDREN], distance = new long[CHILDREN];
        int[] downstream = new int[CHILDREN], removal = new int[CHILDREN], tieOrder = new int[CHILDREN];
        boolean[] settled = new boolean[CHILDREN];
        Arrays.fill(distance,Long.MAX_VALUE); Arrays.fill(downstream,-1); distance[root] = 0;
        for (int c = 0; c < CHILDREN; c++) tieOrder[c] = c;
        for (int a = 0; a < CHILDREN; a++) for (int b = a + 1; b < CHILDREN; b++)
            if (compare(level,i,j,tieOrder[b],tieOrder[a]) < 0) { int v=tieOrder[a]; tieOrder[a]=tieOrder[b]; tieOrder[b]=v; }
        // Balanced integer disaggregation: only the remainder depends on canonical child keys.
        for (int c = 0; c < CHILDREN; c++) rain[tieOrder[c]] = localRain / CHILDREN + (c < localRain % CHILDREN ? 1 : 0);
        for (Inflow entry : entries) {
            int c = owner(entry.port,side(level,i,j,entry.port),x,z,s);
            injection[c] = Math.addExact(injection[c],entry.units);
        }
        // Four-node Dijkstra, positive integer edge costs. No heap or floating slope decisions.
        for (int k = 0; k < CHILDREN; k++) {
            int p = -1;
            for (int c = 0; c < CHILDREN; c++) if (!settled[c] && (p < 0 || distance[c] < distance[p]
                || (distance[c] == distance[p] && compare(level,i,j,c,p) < 0))) p = c;
            settled[p] = true; removal[k] = p;
            for (int mask = 1; mask <= 2; mask++) {
                int q = p ^ mask;
                if (settled[q]) continue;
                long candidate = Math.addExact(distance[p],(long)costs[p] + costs[q] + 1);
                if (candidate < distance[q] || (candidate == distance[q] && compare(level,i,j,p,downstream[q]) < 0)) {
                    distance[q] = candidate; downstream[q] = p;
                }
            }
        }
        long[] flux = new long[CHILDREN];
        for (int c = 0; c < CHILDREN; c++) flux[c] = Math.addExact(rain[c],injection[c]);
        for (int k = CHILDREN - 1; k >= 0; k--) {
            int p = removal[k], q = downstream[p];
            if (q >= 0) flux[q] = Math.addExact(flux[q],flux[p]);
        }
        if (flux[root] != committedOutflow) throw new IllegalStateException("Refinement violated its boundary budget");
        Child[] children = new Child[CHILDREN];
        for (int c = 0; c < CHILDREN; c++) {
            long ci = i * 2 + c % 2, cj = j * 2 + c / 2;
            int q = downstream[c]; BoundaryPorts.Port childExit = exit;
            if (q >= 0) {
                int direction = q == c + 1 ? 2 : q == c - 1 ? 4 : q == c + 2 ? 3 : 1;
                childExit = ports.port(ports.face(level - 1,ci,cj,direction));
            }
            children[c] = new Child(ci,cj,level - 1,q,costs[c],distance[c],rain[c],injection[c],flux[c],childExit);
        }
        return new Result(level,i,j,root,children,entries,localRain,external,committedOutflow,exit);
    }
    private int side(int level, long i, long j, BoundaryPorts.Port port) {
        if (port == null) throw new IllegalArgumentException("Missing inherited port");
        if (port.owner.level < level) throw new IllegalArgumentException("Inherited port is finer than this parent");
        BoundaryPorts.Port expected = ports.port(port.owner);
        if (expected.x != port.x || expected.z != port.z) throw new IllegalArgumentException("Port belongs to a different world configuration");
        BoundaryPorts.Face localFace = port.owner.level == level ? port.owner : ports.childFace(port,level);
        for (int side = 1; side <= CHILDREN; side++) if (ports.face(level,i,j,side).equals(localFace)) return side;
        throw new IllegalArgumentException("Inherited port is not on this parent boundary");
    }
    private static int owner(BoundaryPorts.Port port, int side, long x, long z, long s) {
        int cx = side == 2 ? 1 : side == 4 ? 0 : (port.x - x < s / 2 ? 0 : 1);
        int cz = side == 3 ? 1 : side == 1 ? 0 : (port.z - z < s / 2 ? 0 : 1);
        return cz * 2 + cx;
    }
    private int compare(int level, long i, long j, int a, int b) {
        if (b < 0) return -1;
        long ah = Hash64.hash(seed,level - 1,i * 2 + a % 2,j * 2 + a / 2);
        long bh = Hash64.hash(seed,level - 1,i * 2 + b % 2,j * 2 + b / 2);
        int order = Long.compareUnsigned(ah,bh);
        return order == 0 ? Integer.compare(a,b) : order;
    }
}
