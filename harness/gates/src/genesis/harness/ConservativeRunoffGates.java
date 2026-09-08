package genesis.harness;

import genesis.core.hash.Hash64;
import genesis.oracle.BoundaryFlowSummary;
import genesis.oracle.ConservativeRunoff;
import genesis.oracle.ConservativeRunoff.Cell;
import genesis.oracle.ConservativeRunoff.Weight;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Finite catchment-aligned source allocation; no new production fields. */
final class ConservativeRunoffGates {
    private ConservativeRunoffGates() {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("Invalid runoff contract accepted");
    }
    public static void main(String[] args) throws Exception { run(); }
    static void run() throws Exception {
        allocations(); invalidAndExtreme();
        Random random = new Random(20260909L);
        for (int trial = 0; trial < 200; trial++) {
            int n = 2 + random.nextInt(79);
            List<Integer> keys = new ArrayList<>();
            for (int p = 0; p < n; p++) keys.add(p * 11 + 3);
            Collections.shuffle(keys, random);
            List<Cell> cells = new ArrayList<>(); Map<Integer, Long> budgets = new TreeMap<>();
            for (int p = 0; p < n; p++) {
                int key = keys.get(p);
                int next = p == n - 1 || random.nextInt(8) == 0 ? -1 : keys.get(p + 1 + random.nextInt(n - p - 1));
                // Positive reference weights here; dry branches have separate explicit fixtures.
                cells.add(new Cell(key, key - 1024L, 917L - key * 3L, next, 1 + random.nextInt(1000)));
                if (next < 0) budgets.put(key, trial % 17 == 0 ? Long.MAX_VALUE : (long) random.nextInt(100001));
            }
            long seed = random.nextLong(); int multiplier = 1 + random.nextInt(16);
            var hierarchy = new ConservativeRunoff(seed, multiplier, cells, budgets);
            verify(hierarchy);
            Collections.shuffle(cells, random);
            var reordered = new ConservativeRunoff(seed, multiplier, cells, budgets);
            for (Cell cell : cells) check(hierarchy.query(cell.key()).equals(reordered.query(cell.key())), "Input order changed allocation");
        }
        var cells = grid(); int terminal = 15 * 24 + 12;
        Map<Integer, Long> budgets = Map.of(terminal, 1_000_003L);
        var a = new ConservativeRunoff(42, 8, cells, budgets);
        var b = new ConservativeRunoff(-1, 8, cells, budgets);
        verify(a); verify(b);
        check(cells.stream().anyMatch(c -> a.query(c.key()).localUnits() != b.query(c.key()).localUnits()), "Seed does not influence fine sources");
        check(a.query(terminal).catchmentUnits() == b.query(terminal).catchmentUnits(), "Fine seed variation changed supplied root budget");
        List<Cell> shuffled = new ArrayList<>(cells); Collections.shuffle(shuffled, new Random(901));
        // Cold per-point construction, coordinate lookup, common-point crop/zoom and concurrent reads.
        for (int i = 0; i < 48; i++) {
            Cell cell = shuffled.get(i);
            var cold = new ConservativeRunoff(42, 8, shuffled, budgets);
            check(cold.query(cell.x(), cell.z()).equals(a.query(cell.key())), "Cold coordinate query changed source");
        }
        for (int step : new int[] {1, 2, 3, 7}) for (int z = 0; z < 16; z += step) for (int x = 0; x < 24; x += step) {
            Cell cell = cells.get(z * 24 + x);
            check(a.query(cell.x(), cell.z()).equals(a.query(z * 24 + x)), "Common-point crop/zoom ownership");
        }
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (Cell cell : shuffled) jobs.add(() -> a.query(cell.x(), cell.z()).equals(new ConservativeRunoff(42, 8, cells, budgets).query(cell.key())));
            for (var result : pool.invokeAll(jobs)) check(result.get(), "Concurrent/cold source mismatch");
        }
        workAdversaries(); diagnostics(a, b);
        System.out.println("PASS R1b RUNOFF: 500 exact weighted splits, 200 finite catchment forests, independent source/weight walks, R1a composition, dry/extreme/cold/order/spatial/concurrency and work adversaries; NOT world roots");
    }

    private static void allocations() {
        var equal = List.of(new Weight(3, BigInteger.ONE, 7), new Weight(1, BigInteger.ONE, 7),
            new Weight(0, BigInteger.ONE, 7), new Weight(2, BigInteger.ONE, 7));
        check(ConservativeRunoff.apportion(2, equal).equals(Map.of(0, 1L, 1, 1L, 2, 0L, 3, 0L)), "Canonical key remainder tie");
        var unsigned = List.of(new Weight(0, BigInteger.ONE, -1), new Weight(1, BigInteger.ONE, 0));
        check(ConservativeRunoff.apportion(1, unsigned).get(1) == 1, "Unsigned hash tie protocol");
        // Record, rather than conceal, largest remainder's non-monotonic integer response.
        var paradox = new ArrayList<Weight>();
        int[] proportions = {1500, 1500, 900, 500, 500, 200};
        for (int i = 0; i < proportions.length; i++) paradox.add(new Weight(i, BigInteger.valueOf(proportions[i]), i));
        var small = ConservativeRunoff.apportion(25, paradox);
        var larger = ConservativeRunoff.apportion(26, paradox);
        check(small.get(3) == 3 && larger.get(3) == 2, "Quantization tradeoff fixture changed; review allocation protocol");
        Random random = new Random(617211L);
        for (int trial = 0; trial < 500; trial++) {
            List<Weight> weights = new ArrayList<>();
            int count = 1 + random.nextInt(12);
            for (int p = 0; p < count; p++) weights.add(new Weight(p * 7, p == 0 ? BigInteger.ONE
                : BigInteger.valueOf(random.nextInt(31)).shiftLeft(random.nextInt(180)), random.nextLong()));
            long budget = trial % 13 == 0 ? Long.MAX_VALUE : random.nextLong() & Long.MAX_VALUE;
            var actual = ConservativeRunoff.apportion(budget, weights);
            check(actual.equals(referenceAllocation(budget, weights)), "Exact allocation differs from independent remainder selection");
            BigInteger sum = BigInteger.ZERO;
            for (Weight w : weights) {
                long amount = actual.get(w.key()); check(amount >= 0, "Negative allocation");
                if (w.amount().signum() == 0) check(amount == 0, "Zero-weight slot received water");
                sum = sum.add(BigInteger.valueOf(amount));
            }
            check(sum.equals(BigInteger.valueOf(budget)), "Weighted split lost units");
            Collections.shuffle(weights, random);
            check(actual.equals(ConservativeRunoff.apportion(budget, weights)), "Weight list order affects allocation");
        }
    }
    /** Independent unsorted O(K^2) largest-remainder reference, not the production sort. */
    private static Map<Integer, Long> referenceAllocation(long budget, List<Weight> weights) {
        BigInteger total = weights.stream().map(Weight::amount).reduce(BigInteger.ZERO, BigInteger::add);
        Map<Integer, Long> result = new TreeMap<>();
        Map<Integer, BigInteger> remainders = new HashMap<>();
        long assigned = 0;
        for (Weight weight : weights) {
            BigInteger product = BigInteger.valueOf(budget).multiply(weight.amount());
            long floor = product.divide(total).longValueExact();
            result.put(weight.key(), floor); assigned += floor;
            remainders.put(weight.key(), product.mod(total));
        }
        for (long unit = assigned; unit < budget; unit++) {
            Weight best = null;
            for (Weight weight : weights) if (remainders.containsKey(weight.key())) {
                if (best == null) { best = weight; continue; }
                int cmp = remainders.get(weight.key()).compareTo(remainders.get(best.key()));
                if (cmp > 0 || (cmp == 0 && (Long.compareUnsigned(weight.tie(), best.tie()) < 0
                    || (weight.tie() == best.tie() && weight.key() < best.key())))) best = weight;
            }
            result.put(best.key(), result.get(best.key()) + 1); remainders.remove(best.key());
        }
        return result;
    }
    private static void verify(ConservativeRunoff hierarchy) {
        Map<Integer, Cell> byKey = new TreeMap<>();
        Map<Integer, Long> local = new TreeMap<>();
        Map<Integer, BigInteger> sum = new TreeMap<>(), weightSum = new TreeMap<>();
        for (Cell cell : hierarchy.cells()) {
            byKey.put(cell.key(), cell); sum.put(cell.key(), BigInteger.ZERO); weightSum.put(cell.key(), BigInteger.ZERO);
            var query = hierarchy.query(cell.key()); local.put(cell.key(), query.localUnits());
            long allocated = query.localUnits();
            for (long amount : query.tributaries().values()) allocated = Math.addExact(allocated, amount);
            check(allocated == query.catchmentUnits(), "Parent split not conservative");
            if (cell.referenceWeight() == 0) check(query.localUnits() == 0, "Ineligible cell received local runoff");
        }
        // Every fine cell walks to its actual terminal: independently sums spatial membership.
        for (Cell source : hierarchy.cells()) for (int p = source.key(); p >= 0; p = byKey.get(p).downstream()) {
            sum.put(p, sum.get(p).add(BigInteger.valueOf(local.get(source.key()))));
            weightSum.put(p, weightSum.get(p).add(BigInteger.valueOf(source.referenceWeight())));
        }
        var materialized = new ArrayList<BoundaryFlowSummary.Node>();
        for (Cell cell : hierarchy.cells()) {
            var query = hierarchy.query(cell.key());
            check(sum.get(cell.key()).equals(BigInteger.valueOf(query.catchmentUnits())), "Fine source integral != queried upstream budget");
            check(weightSum.get(cell.key()).equals(hierarchy.referenceTotal(cell.key())), "Reference support sum incorrect");
            var expectedChildren = new TreeMap<Integer, Long>();
            for (Cell child : hierarchy.cells()) if (child.downstream() == cell.key()) expectedChildren.put(child.key(), sum.get(child.key()).longValueExact());
            check(query.tributaries().equals(expectedChildren), "Tributary ownership differs from supplied drainage");
            materialized.add(new BoundaryFlowSummary.Node(cell.key(), cell.downstream(), query.localUnits()));
        }
        var graph = new BoundaryFlowSummary(materialized);
        var reduced = graph.summarize(key -> key % 7);
        for (var port : reduced.accumulate().entrySet()) check(port.getValue() == sum.get(port.getKey()).longValueExact(), "R1a summary changed conditioned flux");
        for (var terminal : hierarchy.terminalBudgets().entrySet()) check(sum.get(terminal.getKey()).equals(BigInteger.valueOf(terminal.getValue())), "Root closure failed");
    }
    private static void invalidAndExtreme() {
        var one = new Cell(0, Long.MIN_VALUE, Long.MAX_VALUE, -1, 1);
        var full = new ConservativeRunoff(Long.MIN_VALUE, 1024, List.of(one), Map.of(0, Long.MAX_VALUE));
        check(full.query(one.x(), one.z()).localUnits() == Long.MAX_VALUE, "Full-width coordinate/seed/budget exactness");
        var zero = new Cell(0, 0, 0, -1, 0);
        check(new ConservativeRunoff(0, 1, List.of(zero), Map.of(0, 0L)).query(0).localUnits() == 0, "All-dry zero budget");
        var dryBranch = List.of(new Cell(0, 0, 0, 2, 0), new Cell(1, 1, 0, 2, 5), new Cell(2, 2, 0, -1, 0));
        var dry = new ConservativeRunoff(42, 8, dryBranch, Map.of(2, 71L)); verify(dry);
        check(dry.query(0).catchmentUnits() == 0 && dry.query(1).localUnits() == 71, "Dry subtree not preserved");
        var hugeWeights = List.of(new Cell(0, 0, 0, 2, Long.MAX_VALUE), new Cell(1, 1, 0, 2, Long.MAX_VALUE), new Cell(2, 2, 0, -1, Long.MAX_VALUE));
        verify(new ConservativeRunoff(-1, 1024, hugeWeights, Map.of(2, Long.MAX_VALUE)));
        // With no jitter and an exactly proportional root budget, materialization recovers preferences.
        var exact = new ConservativeRunoff(42, 1, List.of(new Cell(0, 0, 0, 2, 12), new Cell(1, 1, 0, 2, 10), new Cell(2, 2, 0, -1, 8)), Map.of(2, 30L));
        for (Cell cell : exact.cells()) check(exact.query(cell.key()).localUnits() == cell.referenceWeight(), "No-jitter proportional counterfactual");
        rejects(() -> new ConservativeRunoff(0, 1, List.of(zero), Map.of(0, 1L)));
        rejects(() -> new ConservativeRunoff(0, 0, List.of(one), Map.of(0, 1L)));
        rejects(() -> new ConservativeRunoff(0, 1025, List.of(one), Map.of(0, 1L)));
        rejects(() -> new ConservativeRunoff(0, 1, null, Map.of()));
        rejects(() -> new ConservativeRunoff(0, 1, List.of(one), null));
        rejects(() -> new ConservativeRunoff(0, 1, List.of(), Map.of()));
        rejects(() -> new ConservativeRunoff(0, 1, List.of(one), Map.of()));
        rejects(() -> new ConservativeRunoff(0, 1, List.of(one), Map.of(0, -1L)));
        rejects(() -> new ConservativeRunoff(0, 1, List.of(one), Map.of(0, 1L, 9, 1L)));
        rejects(() -> new ConservativeRunoff(0, 1, List.of(one, one), Map.of(0, 1L)));
        rejects(() -> new ConservativeRunoff(0, 1, List.of(one, new Cell(1, one.x(), one.z(), -1, 1)), Map.of(0, 1L, 1, 1L)));
        rejects(() -> new ConservativeRunoff(0, 1, List.of(new Cell(0, 0, 0, 9, 1)), Map.of()));
        rejects(() -> new ConservativeRunoff(0, 1, List.of(new Cell(0, 0, 0, 0, 1)), Map.of()));
        rejects(() -> new ConservativeRunoff(0, 1, List.of(new Cell(0, 0, 0, 1, 1), new Cell(1, 1, 0, 0, 1)), Map.of()));
        rejects(() -> full.query(-1)); rejects(() -> full.query(0L, 0L));
        rejects(() -> new Cell(-1, 0, 0, -1, 0)); rejects(() -> new Cell(0, 0, 0, -2, 0)); rejects(() -> new Cell(0, 0, 0, -1, -1));
        rejects(() -> new Weight(0, BigInteger.valueOf(-1), 0));
        rejects(() -> ConservativeRunoff.apportion(1, List.of(new Weight(0, BigInteger.ZERO, 0))));
        rejects(() -> ConservativeRunoff.apportion(-1, List.of(new Weight(0, BigInteger.ONE, 0))));
        rejects(() -> ConservativeRunoff.apportion(1, List.of()));
        rejects(() -> ConservativeRunoff.apportion(1, null));
        rejects(() -> ConservativeRunoff.apportion(1, List.of(new Weight(0, BigInteger.ONE, 0), new Weight(0, BigInteger.ONE, 1))));
        var mutableCells = new ArrayList<>(dryBranch); var mutableBudget = new TreeMap<>(Map.of(2, 71L));
        var frozen = new ConservativeRunoff(42, 8, mutableCells, mutableBudget);
        mutableCells.clear(); mutableBudget.put(2, 99L);
        check(frozen.query(2).equals(dry.query(2)), "Caller mutation changes hierarchy");
        try { frozen.query(2).tributaries().clear(); throw new AssertionError("Mutable query result"); } catch (UnsupportedOperationException expected) { }
        try { frozen.cells().clear(); throw new AssertionError("Mutable cells"); } catch (UnsupportedOperationException expected) { }
        try { frozen.terminalBudgets().clear(); throw new AssertionError("Mutable roots"); } catch (UnsupportedOperationException expected) { }
    }
    private static void workAdversaries() {
        List<Cell> chain = new ArrayList<>(), star = new ArrayList<>();
        for (int p = 0; p < 1024; p++) {
            chain.add(new Cell(p, p, 0, p == 1023 ? -1 : p + 1, 1));
            star.add(new Cell(p, p, 0, p == 1023 ? -1 : 1023, 1));
        }
        var deep = new ConservativeRunoff(1, 1, chain, Map.of(1023, 1024L)).query(0);
        var wide = new ConservativeRunoff(1, 1, star, Map.of(1023, 1024L)).query(1023);
        check(deep.splits() == 1024 && deep.slotsVisited() == 2047, "Unbalanced depth cost hidden");
        check(wide.splits() == 1 && wide.slotsVisited() == 1024, "Unbounded fanout cost hidden");
    }
    private static List<Cell> grid() {
        List<Cell> cells = new ArrayList<>();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 24; x++) {
            int p = z * 24 + x, q;
            if (z == 15) q = x == 12 ? -1 : p + Integer.compare(12, x);
            else {
                int target = x < 12 ? 6 : 18;
                int dx = (Hash64.mix(p) & 3) == 0 ? 0 : Integer.compare(target, x);
                q = (z + 1) * 24 + x + dx;
            }
            long preference = z == 15 ? 0 : (x < 12 ? 8 : 2) + (x + z) % 3;
            cells.add(new Cell(p, x - 100L, z - 60L, q, preference));
        }
        return cells;
    }
    private static void diagnostics(ConservativeRunoff a, ConservativeRunoff b) throws Exception {
        long[][] values = new long[4][384];
        int maxSplits = 0; long maxSlots = 0;
        for (Cell cell : a.cells()) {
            var query = a.query(cell.key());
            values[0][cell.key()] = cell.referenceWeight(); values[1][cell.key()] = query.localUnits();
            values[2][cell.key()] = b.query(cell.key()).localUnits(); values[3][cell.key()] = query.catchmentUnits();
            maxSplits = Math.max(maxSplits, query.splits()); maxSlots = Math.max(maxSlots, query.slotsVisited());
        }
        BufferedImage image = new BufferedImage(1080, 880, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 247, 249)); g.fillRect(0, 0, 1080, 880);
        g.setColor(new Color(25, 34, 46)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        g.drawString("R1b: catchment-aligned conservative runoff", 22, 27);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("Finite supplied drainage fixture; not continental rainfall. Both seeds preserve the same 1,000,003-unit root budget.", 22, 51);
        String[] titles = {"Reference preference (not rainfall)", "Allocated local runoff | seed 42", "Allocated local runoff | seed -1", "Upstream discharge | seed 42"};
        long sharedMax = 1;
        for (int p = 0; p < 384; p++) sharedMax = Math.max(sharedMax, Math.max(values[1][p], values[2][p]));
        for (int panel = 0; panel < 4; panel++) {
            int ox = 22 + panel % 2 * 540, oy = 105 + panel / 2 * 360;
            long max = panel == 0 ? 10 : panel == 3 ? 1_000_003L : sharedMax;
            g.setColor(new Color(25, 34, 46)); g.drawString(titles[panel], ox, oy - 27);
            g.drawString((panel == 3 ? "Log color scale" : "Linear color scale") + ": 0 (pale) to " + max + " (dark)", ox, oy - 9);
            for (Cell cell : a.cells()) {
                long value = values[panel][cell.key()];
                double t = panel == 3 ? Math.log1p(value) / Math.log1p(max) : value / (double) max;
                g.setColor(new Color((int) (238 - 210 * t), (int) (244 - 151 * t), (int) (248 - 95 * t)));
                g.fillRect(ox + cell.key() % 24 * 20, oy + cell.key() / 24 * 19, 20, 19);
                if (cell.downstream() < 0) {
                    g.setColor(new Color(185, 53, 24)); g.setStroke(new BasicStroke(2));
                    g.drawOval(ox + cell.key() % 24 * 20 + 4, oy + cell.key() / 24 * 19 + 3, 12, 12);
                }
            }
        }
        g.setColor(new Color(25, 34, 46));
        g.drawString("Red circle: explicitly supplied terminal. Bottom-row sources are zero; water arriving there is upstream flow.", 22, 814);
        g.drawString("All queried catchment totals equal sums of materialized fine sources. No viewport bounds enter queries.", 22, 841);
        g.dispose();
        Files.createDirectories(Path.of("build/gallery"));
        ImageIO.write(image, "png", Path.of("build/gallery/conservative-runoff.png").toFile());
        Files.writeString(Path.of("build/conservative-runoff.json"), """
            {
              "experiment": "conservative-runoff-v1",
              "scope": "complete supplied finite forest and net-runoff root budgets; not world roots or climate",
              "allocationTrials": 500,
              "forestTrials": 200,
              "fixtureCells": 384,
              "rootBudgetUnits": 1000003,
              "seeds": [42, -1],
              "maxMultiplier": 8,
              "fineSourcesEqualEveryQueriedCatchment": true,
              "fixtureMaxQuerySplits": %d,
              "fixtureMaxQuerySlots": %d,
              "chain1024QuerySplits": 1024,
              "chain1024QuerySlots": 2047,
              "star1024RootQuerySlots": 1024,
              "constructionRequiresCompleteFiniteGraph": true,
              "budgetMonotonicityGuaranteed": false
            }
            """.formatted(maxSplits, maxSlots), StandardCharsets.UTF_8);
    }
}
