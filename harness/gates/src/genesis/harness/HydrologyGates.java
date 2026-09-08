package genesis.harness;

import genesis.oracle.FiniteHydrology;
import genesis.oracle.WindowFlux;
import java.util.Arrays;
import java.util.Random;

/** Independent exhaustive relaxation validates the heap solver's minimax spill heights. */
final class HydrologyGates {
    private HydrologyGates() {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void run() {
        int[] bowl = {9,9,9,9,9, 9,3,3,3,9, 9,3,-5,3,4, 9,3,3,3,9, 9,9,9,9,9};
        boolean[] sea = new boolean[25]; sea[12] = true;
        var r = FiniteHydrology.solve(5, 5, bowl, sea, FiniteHydrology.edgeTerminals(5, 5));
        check(r.filled()[12] == 4 && r.water()[12] == 2, "Enclosed lake spill or water class");
        check(r.discharged() == 24, "Water cells must not contribute land runoff");
        sea[6] = true; sea[0] = true;
        r = FiniteHydrology.solve(5, 5, bowl, sea, FiniteHydrology.edgeTerminals(5, 5));
        check(r.water()[12] == 1, "D8 diagonal connection to edge");
        var closed = FiniteHydrology.solve(5, 5, bowl, sea, new boolean[25]);
        check(closed.discharged() == 0 && Arrays.stream(closed.order()).allMatch(v -> v == -1)
            && Arrays.stream(closed.outlet()).allMatch(v -> v == -1), "No outlet must remain unresolved");
        int[] extreme = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE};
        r = FiniteHydrology.solve(3, 1, extreme, new boolean[3], new boolean[]{true,false,true});
        check((long) r.filled()[1] - r.original()[1] == 4294967295L, "Spill depth overflow");
        var flat = FiniteHydrology.solve(3, 3, new int[9], new boolean[9], new boolean[]{true,false,false,false,false,false,false,false,false});
        check(Arrays.equals(flat.downstream(), new int[]{-1,0,1,0,0,1,3,3,4}), "Versioned flat tie protocol changed");
        boolean[] allSea = new boolean[9]; Arrays.fill(allSea, true);
        var ocean = FiniteHydrology.solve(3, 3, new int[9], allSea, FiniteHydrology.edgeTerminals(3, 3));
        check(ocean.landCells() == 0 && ocean.discharged() == 0 && Arrays.stream(ocean.water()).allMatch(v -> v == 1), "All-water fixture");
        outletPolicies();
        Random random = new Random(718201);
        for (int trial = 0; trial < 300; trial++) {
            int w = 1 + random.nextInt(9), h = 1 + random.nextInt(9), n = w * h;
            int[] elevation = new int[n]; boolean[] water = new boolean[n], terminals = new boolean[n];
            for (int p = 0; p < n; p++) {
                elevation[p] = trial % 5 == 0 ? 7 : random.nextInt(31) - 15;
                water[p] = elevation[p] <= 0; terminals[p] = random.nextInt(9) == 0;
            }
            verify(w, h, elevation, water, terminals);
        }
        // Full roadmap-scale finite domain: exact flux and graph invariants, no timing assertion.
        int n = 1024 * 1024; int[] terrain = new int[n]; boolean[] dry = new boolean[n];
        for (int p = 0; p < n; p++) terrain[p] = (p * 31 ^ p / 1024 * 17) & 1023;
        r = FiniteHydrology.solve(1024, 1024, terrain, dry, FiniteHydrology.edgeTerminals(1024, 1024));
        invariants(r, FiniteHydrology.edgeTerminals(1024, 1024), dry);
        check(r.discharged() == n, "1024-square mass conservation");
        try { FiniteHydrology.solve(2, 2, new int[3], new boolean[4], new boolean[4]); throw new AssertionError("Dimensions accepted"); }
        catch (IllegalArgumentException expected) { /* correct */ }
        System.out.println("PASS HYDRO reference: outlet policies, unresolved budgets, crossing ownership, 300 independent minimax/window comparisons, 1024-square DAG and exact flux");
    }
    private static void outletPolicies() {
        int[] terrain = {8,8,8,8,8, 8,4,4,4,8, 8,4,-5,4,8, 8,4,4,4,8, -2,8,8,8,8};
        boolean[] sea = new boolean[25]; sea[12] = true; sea[20] = true;
        boolean[] terminals = FiniteHydrology.terminals(5, 5, sea, FiniteHydrology.Boundary.CONNECTED_WATER);
        check(terminals[20] && !terminals[12] && !terminals[0], "Connected-water policy made lake/dry edge a terminal");
        var graph = FiniteHydrology.solve(5, 5, terrain, sea, terminals);
        for (int p = 0; p < 25; p++) check(graph.outlet()[p] == 20, "Route fails to reach connected water");
        invariants(graph, terminals, sea);
        sea[20] = false;
        terminals = FiniteHydrology.terminals(5, 5, sea, FiniteHydrology.Boundary.CONNECTED_WATER);
        graph = FiniteHydrology.solve(5, 5, terrain, sea, terminals);
        check(graph.terminalCount() == 0 && graph.unresolvedLand() == 24 && graph.discharged() == 0, "No-water policy silently falls back to dry outlets");
        check(Arrays.stream(graph.status()).allMatch(v -> v == 0), "Unresolved routing status");
        var ledger = WindowFlux.audit(graph, 1, 1, 4, 4);
        check(ledger.rain() == 8 && ledger.unresolvedRain() == 8 && ledger.balanced(), "Unresolved window ledger");
        sea = new boolean[9]; sea[0] = true; sea[4] = true;
        terminals = FiniteHydrology.terminals(3, 3, sea, FiniteHydrology.Boundary.CONNECTED_WATER);
        check(terminals[4], "Diagonal connected water not terminal");
        graph = FiniteHydrology.solve(3, 3, new int[9], sea, terminals);
        check(graph.downstream()[4] == -1 && graph.status()[4] == 2, "Route continues across connected water floor");
        var diagonal = FiniteHydrology.solve(2, 2, new int[4], new boolean[4], new boolean[]{true,false,false,false});
        ledger = WindowFlux.audit(diagonal, 1, 1, 2, 2);
        check(ledger.rain() == 1 && ledger.outflow() == 1 && ledger.outgoingEdges() == 1 && ledger.inflow() == 0 && ledger.balanced(), "Diagonal corner transfer counted twice");
        try { WindowFlux.audit(diagonal, 0, 0, 3, 2); throw new AssertionError("Invalid audit accepted"); }
        catch (IllegalArgumentException expected) { /* correct */ }
    }
    private static void verify(int w, int h, int[] height, boolean[] water, boolean[] terminals) {
        var actual = FiniteHydrology.solve(w, h, height, water, terminals);
        long[] cost = new long[height.length]; Arrays.fill(cost, Long.MAX_VALUE);
        for (int p = 0; p < cost.length; p++) if (terminals[p]) cost[p] = height[p];
        // Bellman-style whole-raster relaxation: no heap, visited set, or solver route reuse.
        boolean changed;
        do {
            changed = false; long[] next = cost.clone();
            for (int p = 0; p < cost.length; p++) {
                int x = p % w, z = p / w;
                for (int q = 0; q < cost.length; q++) {
                    if (p == q || Math.abs(x - q % w) > 1 || Math.abs(z - q / w) > 1 || cost[q] == Long.MAX_VALUE) continue;
                    long candidate = Math.max(height[p], cost[q]);
                    if (candidate < next[p]) { next[p] = candidate; changed = true; }
                }
            }
            cost = next;
        } while (changed);
        for (int p = 0; p < cost.length; p++) {
            if (cost[p] == Long.MAX_VALUE) check(actual.order()[p] == -1, "Unreachable reference cell");
            else check(actual.filled()[p] == cost[p], "Independent minimax mismatch");
        }
        invariants(actual, terminals, water);
        for (int x0 = 0; x0 < w; x0++) for (int z0 = 0; z0 < h; z0++) {
            var ledger = WindowFlux.audit(actual, x0, z0, w, h);
            long localRain = 0;
            for (int z = z0; z < h; z++) for (int x = x0; x < w; x++) if (!water[z * w + x]) localRain++;
            check(ledger.rain() == localRain && ledger.balanced(), "Arbitrary-origin window conservation");
        }
        if (w > 1 && h > 1) {
            long incoming = 0, outgoing = 0, rain = 0, discharge = 0, unresolved = 0;
            for (int q = 0; q < 4; q++) {
                var ledger = WindowFlux.audit(actual, q % 2 == 0 ? 0 : w / 2, q / 2 == 0 ? 0 : h / 2,
                    q % 2 == 0 ? w / 2 : w, q / 2 == 0 ? h / 2 : h);
                incoming += ledger.inflow(); outgoing += ledger.outflow(); rain += ledger.rain();
                discharge += ledger.terminalDischarge(); unresolved += ledger.unresolvedRain();
            }
            check(incoming == outgoing && rain == actual.landCells() && discharge == actual.discharged()
                && unresolved == actual.unresolvedLand(), "Partition duplicates boundary flow or rainfall");
        }
        var repeat = FiniteHydrology.solve(w, h, height, water, terminals);
        check(Arrays.equals(actual.downstream(), repeat.downstream()) && Arrays.equals(actual.accumulation(), repeat.accumulation()), "Reference repeat determinism");
        int original = actual.original()[0]; height[0]++;
        check(actual.original()[0] == original, "Reference retained input array"); height[0]--;
    }
    private static void invariants(FiniteHydrology.Result r, boolean[] terminals, boolean[] sea) {
        int n = r.original().length; long[] expected = new long[n]; long roots = 0;
        boolean[] seenOrder = new boolean[n];
        for (int p = 0; p < n; p++) {
            expected[p] += sea[p] ? 0 : 1;
            check(r.filled()[p] >= r.original()[p], "Filling lowered terrain");
            if (r.order()[p] < 0) { check(r.outlet()[p] == -1 && r.downstream()[p] == -1, "Unresolved sentinel"); continue; }
            check(!seenOrder[r.order()[p]], "Duplicate flood order"); seenOrder[r.order()[p]] = true;
            int q = r.downstream()[p];
            if (q < 0) { check(terminals[p] && r.outlet()[p] == p, "Invented terminal"); roots += r.accumulation()[p]; }
            else {
                check(Math.abs(p % r.width() - q % r.width()) <= 1 && Math.abs(p / r.width() - q / r.width()) <= 1 && p != q, "Non-D8 edge");
                check(r.order()[q] < r.order()[p] && r.filled()[q] <= r.filled()[p], "Route cycle or uphill filled edge");
                check(r.outlet()[p] == r.outlet()[q] && terminals[r.outlet()[p]], "Outlet ownership");
                expected[q] += r.accumulation()[p];
            }
        }
        check(Arrays.equals(expected, r.accumulation()) && roots == r.discharged(), "Local flux recurrence");
        check(r.discharged() + r.unresolvedLand() == r.landCells(), "Resolved plus unresolved budget");
        check(WindowFlux.audit(r, 0, 0, r.width(), r.height()).balanced(), "Whole-raster ledger");
        if (Arrays.stream(r.order()).allMatch(v -> v >= 0)) check(roots == r.landCells(), "Global flux conservation");
    }
}
