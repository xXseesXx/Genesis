package genesis.oracle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

/** Experimental finite, lossless, single-receiver flow algebra; NOT a world generator.
 * Eliminate region interiors, retaining each outgoing directed edge and explicit terminal.
 * Retained node keys identify original edge sources, never a region or an invented outlet.
 * Local contributions form b; links between retained ports encode the transfer map T.
 * Construction reads the complete supplied graph. No constant-size or infinite-world claim.
 */
public final class BoundaryFlowSummary {
    public record Node(int key, int downstream, long localRunoff) {
        public Node {
            if (key < 0 || downstream < -1 || localRunoff < 0)
                throw new IllegalArgumentException("Nonnegative keys/runoff; -1 is an explicit terminal only");
        }
    }

    private final List<Node> nodes;
    private final int[] downstream;
    private final int[] order;

    /** Input order has no meaning. Missing receivers, cycles and duplicate identities fail closed. */
    public BoundaryFlowSummary(List<Node> input) {
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("A finite graph is required");
        TreeMap<Integer, Node> canonical = new TreeMap<>();
        for (Node node : input) {
            if (node == null || canonical.putIfAbsent(node.key(), node) != null)
                throw new IllegalArgumentException("Null or duplicate graph node");
        }
        nodes = List.copyOf(canonical.values());
        Map<Integer, Integer> indices = new HashMap<>();
        for (int p = 0; p < nodes.size(); p++) indices.put(nodes.get(p).key(), p);
        downstream = new int[nodes.size()];
        int[] pending = new int[nodes.size()];
        for (int p = 0; p < nodes.size(); p++) {
            int key = nodes.get(p).downstream();
            Integer q = key == -1 ? Integer.valueOf(-1) : indices.get(key);
            if (q == null) throw new IllegalArgumentException("Missing receiver: " + key);
            downstream[p] = q;
            if (q >= 0) pending[q]++;
        }
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int p = 0; p < nodes.size(); p++) if (pending[p] == 0) ready.addLast(p);
        order = new int[nodes.size()];
        int count = 0;
        while (!ready.isEmpty()) {
            int p = ready.removeFirst(); order[count++] = p;
            int q = downstream[p];
            if (q >= 0 && --pending[q] == 0) ready.addLast(q);
        }
        if (count != nodes.size()) throw new IllegalArgumentException("Routing cycle has no terminal closure");
    }

    public List<Node> nodes() { return nodes; }

    /** Exact flux at every supplied edge source/terminal. Overflow is an error, never saturation. */
    public Map<Integer, Long> accumulate() {
        long[] flux = new long[nodes.size()];
        for (int p = 0; p < nodes.size(); p++) flux[p] = nodes.get(p).localRunoff();
        for (int p : order) {
            int q = downstream[p];
            if (q >= 0) flux[q] = Math.addExact(flux[q], flux[p]);
        }
        Map<Integer, Long> result = new TreeMap<>();
        for (int p = 0; p < nodes.size(); p++) result.put(nodes.get(p).key(), flux[p]);
        return Collections.unmodifiableMap(result);
    }

    /**
     * Partition by original node key. The supplied owner function is evaluated once per node.
     * Keep all cross-owner edges and terminals, including zero-flow crossings. For every
     * interior source, deposit its contribution at its first exit, not at every later exit.
     * A second call composes summaries correctly when the new partition coarsens the old one.
     * Refining a summary cannot recover eliminated geometry; retain the source graph for that.
     */
    public BoundaryFlowSummary summarize(IntUnaryOperator ownerOfKey) {
        if (ownerOfKey == null) throw new IllegalArgumentException("Missing partition");
        int n = nodes.size();
        int[] owner = new int[n], exit = new int[n];
        boolean[] keep = new boolean[n];
        for (int p = 0; p < n; p++) owner[p] = ownerOfKey.applyAsInt(nodes.get(p).key());
        for (int p = 0; p < n; p++) {
            int q = downstream[p];
            keep[p] = q < 0 || owner[p] != owner[q];
        }
        for (int k = n - 1; k >= 0; k--) {
            int p = order[k];
            exit[p] = keep[p] ? p : exit[downstream[p]];
        }
        long[] local = new long[n];
        for (int p = 0; p < n; p++)
            local[exit[p]] = Math.addExact(local[exit[p]], nodes.get(p).localRunoff());
        List<Node> ports = new ArrayList<>();
        for (int p = 0; p < n; p++) if (keep[p]) {
            int q = downstream[p];
            ports.add(new Node(nodes.get(p).key(), q < 0 ? -1 : nodes.get(exit[q]).key(), local[p]));
        }
        return new BoundaryFlowSummary(ports);
    }
}
