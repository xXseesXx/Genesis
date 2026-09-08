package genesis.oracle;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/** Independent finite raster reference, deliberately not a random-access world field.
 * Integer priority flood with explicit terminal cells. No imports from the production core.
 */
public final class FiniteHydrology {
    public static final String VERSION = "finite-hydrology-v2";
    public enum Boundary { OPEN_EDGES, CONNECTED_WATER }
    private FiniteHydrology() {}
    private record Entry(int cell, int height) {}
    public record Result(int width, int height, int[] original, int[] filled,
                         int[] downstream, int[] outlet, int[] order, int[] water,
                         long[] accumulation, long landCells, long discharged,
                         long unresolvedLand, int[] status, int terminalCount) {}

    /** D8 connectivity includes diagonals. Water: 0 land, 1 edge-connected,
     * 2 enclosed within this raster. This classification does NOT identify a global ocean.
     * Each non-water cell contributes one unit of runoff. Terminals retain their input height.
     * Cells with no reachable terminal have downstream/outlet/order -1, not invented drainage.
     */
    public static Result solve(int width, int height, int[] elevation, boolean[] sea, boolean[] terminals) {
        if (width < 1 || height < 1 || (long) width * height > 1_048_576)
            throw new IllegalArgumentException("Reference grid must contain 1..1048576 cells");
        int n = width * height;
        if (elevation.length != n || sea.length != n || terminals.length != n)
            throw new IllegalArgumentException("Reference array dimensions do not match");
        int[] original = elevation.clone(), filled = elevation.clone(), down = new int[n], outlet = new int[n];
        int[] order = new int[n], sequence = new int[n], water = classify(width, height, sea);
        Arrays.fill(down, -1); Arrays.fill(outlet, -1); Arrays.fill(order, -1);
        boolean[] visited = new boolean[n];
        PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparingInt(Entry::height).thenComparingInt(Entry::cell));
        for (int i = 0; i < n; i++) if (terminals[i]) {
            visited[i] = true; outlet[i] = i; queue.add(new Entry(i, original[i]));
        }
        int count = 0;
        while (!queue.isEmpty()) {
            Entry current = queue.remove(); int p = current.cell;
            order[p] = count; sequence[count++] = p;
            int x = p % width, z = p / width;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, nz = z + dz;
                if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx >= width || nz >= height) continue;
                int q = nz * width + nx;
                if (visited[q]) continue;
                visited[q] = true; filled[q] = Math.max(original[q], filled[p]);
                down[q] = p; outlet[q] = outlet[p]; queue.add(new Entry(q, filled[q]));
            }
        }
        long[] flux = new long[n]; long land = 0, discharged = 0;
        for (int i = 0; i < n; i++) if (!sea[i]) { flux[i] = 1; land++; }
        for (int k = count - 1; k >= 0; k--) {
            int p = sequence[k];
            if (down[p] >= 0) flux[down[p]] += flux[p]; else discharged += flux[p];
        }
        int[] status = new int[n]; long unresolvedLand = 0; int terminalCount = 0;
        for (int p = 0; p < n; p++) {
            if (order[p] < 0) { if (!sea[p]) unresolvedLand++; }
            else if (terminals[p]) { status[p] = 2; terminalCount++; }
            else status[p] = 1;
        }
        return new Result(width, height, original, filled, down, outlet, order, water, flux, land, discharged,
            unresolvedLand, status, terminalCount);
    }

    /** CONNECTED_WATER closes dry edges and makes every edge-connected water cell
     * a terminal, so a land route ends on entering that component, not on the sea floor.
     * No connected water means no terminal; never fall back to fabricated dry-edge mouths.
     * This remains a finite sampled-grid policy, not global ocean identification.
     */
    public static boolean[] terminals(int width, int height, boolean[] sea, Boundary boundary) {
        boolean[] result = edgeTerminals(width, height);
        if (sea.length != result.length) throw new IllegalArgumentException("Reference array dimensions do not match");
        if (boundary == null) throw new IllegalArgumentException("Boundary policy is required");
        if (boundary == Boundary.OPEN_EDGES) return result;
        int[] water = classify(width, height, sea);
        for (int p = 0; p < result.length; p++) result[p] = water[p] == 1;
        return result;
    }

    public static boolean[] edgeTerminals(int width, int height) {
        if (width < 1 || height < 1 || (long) width * height > 1_048_576)
            throw new IllegalArgumentException("Invalid reference dimensions");
        boolean[] edge = new boolean[width * height];
        for (int z = 0; z < height; z++) for (int x = 0; x < width; x++)
            edge[z * width + x] = x == 0 || z == 0 || x == width - 1 || z == height - 1;
        return edge;
    }

    private static int[] classify(int width, int height, boolean[] sea) {
        int n = sea.length; int[] water = new int[n], queue = new int[n];
        for (int start = 0; start < n; start++) {
            if (!sea[start] || water[start] != 0) continue;
            int head = 0, tail = 1; boolean edge = false; queue[0] = start; water[start] = 2;
            while (head < tail) {
                int p = queue[head++], x = p % width, z = p / width;
                edge |= x == 0 || z == 0 || x == width - 1 || z == height - 1;
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= width || nz >= height) continue;
                    int q = nz * width + nx;
                    if (sea[q] && water[q] == 0) { water[q] = 2; queue[tail++] = q; }
                }
            }
            if (edge) for (int k = 0; k < tail; k++) water[queue[k]] = 1;
        }
        return water;
    }
}
