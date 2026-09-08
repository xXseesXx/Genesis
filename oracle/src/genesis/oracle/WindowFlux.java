package genesis.oracle;

/** Read-only audit of a committed finite graph. Half-open rectangles own sources;
 * a crossing is counted once using its directed source -> destination edge.
 * This does not solve a crop independently or invent boundary inflow.
 */
public final class WindowFlux {
    private WindowFlux() {}
    public record Ledger(long rain, long inflow, long outflow, long terminalDischarge,
                         long unresolvedRain, int incomingEdges, int outgoingEdges) {
        public boolean balanced() { return rain + inflow == outflow + terminalDischarge + unresolvedRain; }
    }
    public static Ledger audit(FiniteHydrology.Result graph, int x0, int z0, int x1, int z1) {
        if (x0 < 0 || z0 < 0 || x1 > graph.width() || z1 > graph.height() || x0 >= x1 || z0 >= z1)
            throw new IllegalArgumentException("Audit rectangle must be nonempty and inside the reference raster");
        long rain = 0, inflow = 0, outflow = 0, discharge = 0, unresolved = 0;
        int incoming = 0, outgoing = 0;
        for (int p = 0; p < graph.original().length; p++) {
            boolean inside = contains(p, graph.width(), x0, z0, x1, z1);
            int q = graph.downstream()[p];
            if (inside) {
                if (graph.water()[p] == 0) {
                    rain++;
                    if (graph.status()[p] == 0) unresolved++;
                }
                if (graph.status()[p] == 2) discharge += graph.accumulation()[p];
                if (q >= 0 && !contains(q, graph.width(), x0, z0, x1, z1)) {
                    outflow += graph.accumulation()[p]; outgoing++;
                }
            } else if (q >= 0 && contains(q, graph.width(), x0, z0, x1, z1)) {
                inflow += graph.accumulation()[p]; incoming++;
            }
        }
        return new Ledger(rain, inflow, outflow, discharge, unresolved, incoming, outgoing);
    }
    private static boolean contains(int p, int width, int x0, int z0, int x1, int z1) {
        int x = p % width, z = p / width;
        return x >= x0 && x < x1 && z >= z0 && z < z1;
    }
}
