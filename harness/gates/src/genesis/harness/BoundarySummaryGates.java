package genesis.harness;

import genesis.oracle.BoundaryFlowSummary;
import genesis.oracle.BoundaryFlowSummary.Node;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.IntUnaryOperator;
import javax.imageio.ImageIO;

/** R1a: exact finite summary composition, intentionally independent of world fields. */
final class BoundarySummaryGates {
    private BoundarySummaryGates() {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("Invalid summary contract accepted");
    }
    public static void main(String[] args) throws Exception { run(); }

    static void run() throws Exception {
        adversaries();
        Random random = new Random(20260908L);
        int retained = 0, original = 0;
        // Arbitrary acyclic topology, sparse identities, uneven/zero sources and many terminals.
        for (int trial = 0; trial < 400; trial++) {
            int n = 2 + random.nextInt(127);
            List<Integer> keys = new ArrayList<>();
            for (int p = 0; p < n; p++) keys.add(17 + p * 13);
            Collections.shuffle(keys, random);
            List<Node> nodes = new ArrayList<>();
            for (int p = 0; p < n; p++) {
                int receiver = p == n - 1 || random.nextInt(7) == 0 ? -1 : keys.get(p + 1 + random.nextInt(n - p - 1));
                nodes.add(new Node(keys.get(p), receiver, random.nextInt(1001)));
            }
            Map<Integer, Integer> owners = new TreeMap<>();
            for (int key : keys) owners.put(key, random.nextInt(16));
            IntUnaryOperator fine = key -> owners.get(key);
            IntUnaryOperator parent = key -> owners.get(key) / 4;
            var graph = new BoundaryFlowSummary(nodes);
            var summary = verify(graph, fine);
            verify(graph, parent);
            var composed = summary.summarize(parent);
            check(composed.nodes().equals(graph.summarize(parent).nodes()), "Nested vs direct summary mismatch");
            check(composed.accumulate().equals(graph.summarize(parent).accumulate()), "Nested flow mismatch");
            check(graph.summarize(key -> 0).nodes().equals(composed.summarize(key -> 0).nodes()), "Whole-root composition mismatch");
            check(summary.summarize(fine).nodes().equals(summary.nodes()), "Summary is not idempotent");
            Collections.shuffle(nodes, random);
            check(new BoundaryFlowSummary(nodes).summarize(fine).nodes().equals(summary.nodes()), "Input-order dependency");
            retained += summary.nodes().size(); original += graph.nodes().size();
        }
        var fixture = grid(24, 16);
        IntUnaryOperator tiles = key -> (key % 24 / 6) + (key / 24 / 4) * 4;
        var expected = fixture.summarize(tiles).accumulate();
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int i = 0; i < 128; i++) jobs.add(() -> new BoundaryFlowSummary(fixture.nodes()).summarize(tiles).accumulate().equals(expected));
            for (var result : pool.invokeAll(jobs)) check(result.get(), "Cold concurrent summary mismatch");
        }
        for (int size : new int[] {1, 2, 4, 8, 32}) {
            IntUnaryOperator fine = key -> (key % 24 / size) + (key / 24 / size) * 32;
            IntUnaryOperator parent = key -> (key % 24 / (size * 2)) + (key / 24 / (size * 2)) * 32;
            var summary = verify(fixture, fine);
            check(summary.summarize(parent).nodes().equals(fixture.summarize(parent).nodes()), "Spatial multilevel composition mismatch");
        }
        diagnostics(fixture, tiles, original, retained);
        System.out.println("PASS R1a SUMMARY: 400 finite DAGs, nested partitions, independent source walks, multi-exit/re-entry, exact ledgers, overflow and cold concurrency; NOT infinite-world roots");
    }

    /** Independent deliberately slow O(N * path length) source walk; no topological accumulation. */
    private static Map<Integer, Long> walk(List<Node> nodes) {
        Map<Integer, Node> byKey = new TreeMap<>();
        Map<Integer, Long> flux = new TreeMap<>();
        for (Node node : nodes) { byKey.put(node.key(), node); flux.put(node.key(), 0L); }
        for (Node source : nodes) {
            int steps = 0;
            for (int p = source.key(); p >= 0; p = byKey.get(p).downstream()) {
                check(++steps <= nodes.size(), "Cycle in reference walk");
                flux.put(p, Math.addExact(flux.get(p), source.localRunoff()));
            }
        }
        return flux;
    }
    private static BoundaryFlowSummary verify(BoundaryFlowSummary graph, IntUnaryOperator owner) {
        Map<Integer, Long> reference = walk(graph.nodes());
        check(reference.equals(graph.accumulate()), "Full accumulation differs from source-walk reference");
        var summary = graph.summarize(owner);
        Map<Integer, Long> compressed = summary.accumulate();
        Map<Integer, Node> originalNodes = new TreeMap<>();
        for (Node node : graph.nodes()) originalNodes.put(node.key(), node);
        Map<Integer, Long> firstExitSources = new TreeMap<>();
        for (Node node : graph.nodes()) if (node.downstream() < 0
            || owner.applyAsInt(node.key()) != owner.applyAsInt(node.downstream())) firstExitSources.put(node.key(), 0L);
        check(compressed.keySet().equals(firstExitSources.keySet()), "Missing or invented boundary port/terminal");
        // Independent first-exit walks audit b and T, not merely one resulting flux vector.
        for (Node source : graph.nodes()) {
            int p = source.key();
            while (!firstExitSources.containsKey(p)) p = originalNodes.get(p).downstream();
            firstExitSources.put(p, Math.addExact(firstExitSources.get(p), source.localRunoff()));
        }
        for (Node node : summary.nodes()) {
            check(node.localRunoff() == firstExitSources.get(node.key()), "Interior contribution assigned to wrong exit");
            int q = originalNodes.get(node.key()).downstream();
            while (q >= 0 && !firstExitSources.containsKey(q)) q = originalNodes.get(q).downstream();
            check(node.downstream() == q, "Incoming transfer mapped to wrong exit");
        }
        for (Node node : summary.nodes()) check(compressed.get(node.key()).equals(reference.get(node.key())), "Port flux differs from full graph");
        long input = 0, output = 0, summarySources = 0;
        for (Node node : graph.nodes()) {
            input = Math.addExact(input, node.localRunoff());
            if (node.downstream() < 0) output = Math.addExact(output, reference.get(node.key()));
        }
        for (Node node : summary.nodes()) summarySources = Math.addExact(summarySources, node.localRunoff());
        check(input == output && input == summarySources, "Source ownership / terminal conservation");
        // Audit every region with external-only incoming contributions, including re-entry.
        Map<Integer, Node> byKey = new TreeMap<>();
        Map<Integer, Long> balance = new TreeMap<>();
        for (Node node : graph.nodes()) byKey.put(node.key(), node);
        for (Node node : graph.nodes()) {
            int a = owner.applyAsInt(node.key());
            balance.merge(a, node.localRunoff(), Math::addExact);
            if (node.downstream() < 0) balance.merge(a, -reference.get(node.key()), Math::addExact);
            else {
                int b = owner.applyAsInt(byKey.get(node.downstream()).key());
                if (a != b) {
                    balance.merge(a, -reference.get(node.key()), Math::addExact);
                    balance.merge(b, reference.get(node.key()), Math::addExact);
                }
            }
        }
        for (long value : balance.values()) check(value == 0, "Region rain + incoming != outgoing + terminals");
        return summary;
    }
    private static void adversaries() {
        // Region A -> B -> A -> B is not a river cycle. Keep all distinct crossings.
        var reentry = new BoundaryFlowSummary(List.of(new Node(0, 1, 3), new Node(1, 2, 5),
            new Node(2, 3, 7), new Node(3, -1, 11), new Node(4, 1, 0), new Node(5, -1, 13)));
        var allPorts = verify(reentry, key -> key % 2);
        check(allPorts.nodes().size() == 6, "Re-entry/zero crossing or second terminal disappeared");
        check(allPorts.accumulate().get(3) == 26 && allPorts.accumulate().get(5) == 13, "Explicit mouth totals");
        check(reentry.summarize(key -> 0).nodes().equals(List.of(new Node(3, -1, 26), new Node(5, -1, 13))), "Multi-exit root collapsed to one outlet");
        var zero = new BoundaryFlowSummary(List.of(new Node(0, 1, 0), new Node(1, -1, 0)));
        check(verify(zero, key -> key).nodes().size() == 2, "Zero flow changed topology");
        var max = new BoundaryFlowSummary(List.of(new Node(0, 1, Long.MAX_VALUE), new Node(1, -1, 0)));
        check(max.summarize(key -> 0).accumulate().get(1) == Long.MAX_VALUE, "Valid extreme runoff lost precision");
        var overflow = new BoundaryFlowSummary(List.of(new Node(0, 1, Long.MAX_VALUE), new Node(1, -1, 1)));
        rejects(overflow::accumulate); rejects(() -> overflow.summarize(key -> 0));
        rejects(() -> new BoundaryFlowSummary(List.of()));
        rejects(() -> new BoundaryFlowSummary(null));
        rejects(() -> new BoundaryFlowSummary(List.of(new Node(0, 4, 1))));
        rejects(() -> new BoundaryFlowSummary(List.of(new Node(0, 0, 1))));
        rejects(() -> new BoundaryFlowSummary(List.of(new Node(0, 1, 1), new Node(1, 0, 1))));
        rejects(() -> new BoundaryFlowSummary(List.of(new Node(0, -1, 1), new Node(0, -1, 2))));
        rejects(() -> new Node(-1, -1, 0)); rejects(() -> new Node(0, -2, 0)); rejects(() -> new Node(0, -1, -1));
        rejects(() -> zero.summarize(null));
        List<Node> mutable = new ArrayList<>(zero.nodes());
        var frozen = new BoundaryFlowSummary(mutable); mutable.clear();
        check(frozen.nodes().size() == 2, "Caller list aliases graph");
        try { frozen.nodes().clear(); throw new AssertionError("Mutable node list"); } catch (UnsupportedOperationException expected) { }
        try { frozen.accumulate().clear(); throw new AssertionError("Mutable flux map"); } catch (UnsupportedOperationException expected) { }
        // Same downstream neighborhood, different distant upstream source: locality is not enough.
        var dry = new BoundaryFlowSummary(List.of(new Node(0, 1, 0), new Node(1, 2, 1), new Node(2, -1, 0)));
        var wet = new BoundaryFlowSummary(List.of(new Node(0, 1, 100), new Node(1, 2, 1), new Node(2, -1, 0)));
        check(dry.nodes().subList(1, 3).equals(wet.nodes().subList(1, 3)), "Counterexample local context changed");
        check(dry.accumulate().get(2) == 1 && wet.accumulate().get(2) == 101, "Distant contribution ignored");
        // Alternating owners retain every node: compression has no general constant-size guarantee.
        List<Node> chain = new ArrayList<>();
        for (int p = 0; p < 256; p++) chain.add(new Node(p, p == 255 ? -1 : p + 1, 1));
        check(new BoundaryFlowSummary(chain).summarize(key -> key % 2).nodes().size() == 256, "Worst-case crossing count understated");
    }

    /** Explicit synthetic domain/terminals, not a crop of continental world generation. */
    private static BoundaryFlowSummary grid(int w, int h) {
        Random random = new Random(182731L);
        List<Node> nodes = new ArrayList<>();
        for (int z = 0; z < h; z++) for (int x = 0; x < w; x++) {
            int p = z * w + x, q;
            if (z == h - 1) q = -1; // Supplied terminal row, deliberately finite experiment.
            else {
                int targetX = x < w / 2 ? w / 4 : 3 * w / 4;
                int dx = Integer.compare(targetX, x);
                q = (z + 1) * w + x + (random.nextInt(3) == 0 ? 0 : dx);
            }
            nodes.add(new Node(p, q, z == h - 1 ? 0 : 1 + random.nextInt(8)));
        }
        return new BoundaryFlowSummary(nodes);
    }
    private static void diagnostics(BoundaryFlowSummary graph, IntUnaryOperator owner, int original, int retained) throws Exception {
        var summary = verify(graph, owner);
        Map<Integer, Long> flux = graph.accumulate();
        BufferedImage image = new BufferedImage(1130, 535, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 246, 248)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(25, 34, 46)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        g.drawString("R1a: exact boundary-flow summaries on a supplied finite graph", 22, 28);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("Not continental terrain; terminal row is an explicit fixture input. Lines on the right represent transfers, not river geometry.", 22, 52);
        g.drawString("Full graph: 384 nodes", 22, 80);
        g.drawString("Boundary graph: " + summary.nodes().size() + " ports/terminals; all retained fluxes match", 575, 80);
        for (int panel = 0; panel < 2; panel++) {
            int ox = 22 + panel * 553, oy = 100;
            for (int z = 0; z < 16; z++) for (int x = 0; x < 24; x++) {
                g.setColor(((x / 6 + z / 4) % 2 == 0) ? new Color(228, 234, 239) : new Color(213, 224, 231));
                g.fillRect(ox + x * 21, oy + z * 22, 21, 22);
            }
            for (Node node : panel == 0 ? graph.nodes() : summary.nodes()) {
                int x = ox + (node.key() % 24) * 21 + 10, y = oy + (node.key() / 24) * 22 + 10;
                if (node.downstream() >= 0) {
                    int tx = ox + (node.downstream() % 24) * 21 + 10, ty = oy + (node.downstream() / 24) * 22 + 10;
                    g.setStroke(new BasicStroke((float) (1 + Math.min(4, Math.sqrt(flux.get(node.key())) / 5))));
                    g.setColor(new Color(27, 105, 165)); g.drawLine(x, y, tx, ty);
                }
                g.setColor(node.downstream() < 0 ? new Color(171, 66, 34) : new Color(23, 71, 105));
                g.fillOval(x - 3, y - 3, 6, 6);
            }
        }
        long total = graph.nodes().stream().mapToLong(Node::localRunoff).sum();
        g.setColor(new Color(25, 34, 46));
        g.drawString("Exact source total = terminal discharge = " + total + " units. Alternating-owner adversary retains 256 / 256 nodes.", 22, 483);
        g.drawString("Summaries preserve upstream information; they do not create root budgets or guarantee constant-size compression.", 22, 511);
        g.dispose();
        Path gallery = Path.of("build/gallery"); Files.createDirectories(gallery);
        ImageIO.write(image, "png", gallery.resolve("boundary-summary.png").toFile());
        Files.writeString(Path.of("build/boundary-summary.json"), """
            {
              "experiment": "boundary-summary-v1",
              "scope": "finite supplied DAG; no world roots, rainfall generator or terrain realization",
              "randomSeed": 20260908,
              "randomTrials": 400,
              "randomOriginalNodes": %d,
              "randomRetainedNodes": %d,
              "gridOriginalNodes": 384,
              "gridRetainedNodes": %d,
              "gridRunoffUnits": %d,
              "alternatingOwnerOriginalNodes": 256,
              "alternatingOwnerRetainedNodes": 256,
              "coldConcurrentQueries": 128,
              "exactChecksPassed": true
            }
            """.formatted(original, retained, summary.nodes().size(), total), StandardCharsets.UTF_8);
    }
}
