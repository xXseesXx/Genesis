package genesis.oracle;

import genesis.core.hash.Hash64;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** R1b finite research model, NOT a production rainfall field or world-root provider.
 * The complete supplied drainage forest defines spatial ownership and subtree weights.
 * Its terminal budgets are NET RUNOFF, not rainfall or additional boundary inflow.
 * Fine sources are conditioned on these totals with exact integer apportionment.
 */
public final class ConservativeRunoff {
    public static final String VERSION = "conservative-runoff-v1";
    private static final long DOMAIN = 0x52554e4f46464cL;

    public record Cell(int key, long x, long z, int downstream, long referenceWeight) {
        public Cell {
            if (key < 0 || downstream < -1 || referenceWeight < 0)
                throw new IllegalArgumentException("Invalid cell identity, receiver or reference weight");
        }
    }
    public record Weight(int key, BigInteger amount, long tie) {
        public Weight {
            if (key < 0 || amount == null || amount.signum() < 0)
                throw new IllegalArgumentException("Weights require unique nonnegative keys and amounts");
        }
    }
    public record Query(int key, long catchmentUnits, long localUnits, Map<Integer, Long> tributaries,
                        int splits, long slotsVisited) {
        public Query { tributaries = Collections.unmodifiableMap(new TreeMap<>(tributaries)); }
    }
    private record Position(long x, long z) {}
    private record Quota(Weight weight, long floor, BigInteger remainder) {}

    private final List<Cell> cells;
    private final Map<Integer, Cell> byKey;
    private final Map<Position, Integer> byPosition;
    private final Map<Integer, List<Integer>> children;
    private final Map<Integer, BigInteger> referenceTotals;
    private final Map<Integer, Long> terminalBudgets;
    private final long seed;
    private final int maxMultiplier;

    /** Full finite preprocessing is explicit. Max multiplier is a research configuration,
     * 1 disables hashed weight variation; 1..1024 are allowed. No hidden caches or world reads.
     */
    public ConservativeRunoff(long seed, int maxMultiplier, List<Cell> input, Map<Integer, Long> budgets) {
        if (input == null || input.isEmpty() || budgets == null || maxMultiplier < 1 || maxMultiplier > 1024)
            throw new IllegalArgumentException("Complete finite input and valid multiplier required");
        this.seed = Hash64.stream(seed, DOMAIN); this.maxMultiplier = maxMultiplier;
        TreeMap<Integer, Cell> sorted = new TreeMap<>();
        Map<Position, Integer> positions = new HashMap<>();
        for (Cell cell : input) {
            if (cell == null || sorted.putIfAbsent(cell.key(), cell) != null
                || positions.putIfAbsent(new Position(cell.x(), cell.z()), cell.key()) != null)
                throw new IllegalArgumentException("Duplicate/null cell or duplicate spatial ownership");
        }
        cells = List.copyOf(sorted.values()); byKey = Collections.unmodifiableMap(sorted);
        byPosition = Map.copyOf(positions);
        Map<Integer, List<Integer>> incoming = new TreeMap<>();
        Map<Integer, BigInteger> totals = new TreeMap<>();
        Map<Integer, Long> roots = new TreeMap<>();
        for (Cell cell : cells) {
            incoming.put(cell.key(), new ArrayList<>());
            totals.put(cell.key(), BigInteger.valueOf(cell.referenceWeight()));
            if (cell.downstream() < 0) {
                Long budget = budgets.get(cell.key());
                if (budget == null || budget < 0) throw new IllegalArgumentException("Missing or negative terminal budget");
                roots.put(cell.key(), budget);
            }
        }
        if (!roots.keySet().equals(budgets.keySet())) throw new IllegalArgumentException("Budget on a nonterminal/unknown key");
        for (Cell cell : cells) if (cell.downstream() >= 0) {
            List<Integer> list = incoming.get(cell.downstream());
            if (list == null) throw new IllegalArgumentException("Missing downstream receiver");
            list.add(cell.key());
        }
        Map<Integer, Integer> pending = new HashMap<>();
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (Cell cell : cells) {
            int count = incoming.get(cell.key()).size(); pending.put(cell.key(), count);
            if (count == 0) ready.addLast(cell.key());
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            int p = ready.removeFirst(); visited++;
            int q = byKey.get(p).downstream();
            if (q >= 0) {
                totals.put(q, totals.get(q).add(totals.get(p)));
                int remaining = pending.get(q) - 1; pending.put(q, remaining);
                if (remaining == 0) ready.addLast(q);
            }
        }
        if (visited != cells.size()) throw new IllegalArgumentException("Cycle in supplied catchments");
        for (var root : roots.entrySet()) if (root.getValue() > 0 && totals.get(root.getKey()).signum() == 0)
            throw new IllegalArgumentException("Positive runoff has no eligible source cells");
        incoming.replaceAll((key, list) -> List.copyOf(list));
        children = Collections.unmodifiableMap(incoming);
        referenceTotals = Collections.unmodifiableMap(totals);
        terminalBudgets = Collections.unmodifiableMap(roots);
    }

    public List<Cell> cells() { return cells; }
    public Map<Integer, Long> terminalBudgets() { return terminalBudgets; }
    public BigInteger referenceTotal(int key) { requireCell(key); return referenceTotals.get(key); }
    private Cell requireCell(int key) {
        Cell cell = byKey.get(key);
        if (cell == null) throw new IllegalArgumentException("Outside supplied catchment graph");
        return cell;
    }
    public Query query(long x, long z) {
        Integer key = byPosition.get(new Position(x, z));
        if (key == null) throw new IllegalArgumentException("Outside supplied spatial domain; no fallback basin");
        return query(key);
    }
    /** Follow the downstream ancestor chain to the explicit terminal, then allocate back down.
     * Unqueried tributary interiors are not materialized, but their weights were preprocessed.
     * Work grows with path depth and fanout; no general logarithmic bound is asserted.
     */
    public Query query(int key) {
        requireCell(key);
        List<Integer> path = new ArrayList<>();
        for (int p = key; p >= 0; p = byKey.get(p).downstream()) path.add(p);
        long amount = terminalBudgets.get(path.getLast()), slots = 0;
        for (int k = path.size() - 1; k >= 0; k--) {
            int p = path.get(k);
            List<Weight> weights = weights(p); slots = Math.addExact(slots, weights.size());
            Map<Integer, Long> allocation = apportion(amount, weights);
            if (k == 0) {
                Map<Integer, Long> tributaries = new TreeMap<>(allocation);
                long local = tributaries.remove(p);
                return new Query(key, amount, local, tributaries, path.size(), slots);
            }
            amount = allocation.get(path.get(k - 1));
        }
        throw new IllegalStateException("Empty query path");
    }
    private List<Weight> weights(int key) {
        List<Weight> weights = new ArrayList<>();
        weights.add(weight(key, key, BigInteger.valueOf(byKey.get(key).referenceWeight())));
        for (int child : children.get(key)) weights.add(weight(key, child, referenceTotals.get(child)));
        return weights;
    }
    private Weight weight(int parent, int slot, BigInteger reference) {
        Cell cell = byKey.get(slot);
        long tie = Hash64.hash(seed, parent, cell.x(), cell.z());
        long factor = 1 + Long.remainderUnsigned(tie, maxMultiplier);
        return new Weight(slot, reference.multiply(BigInteger.valueOf(factor)), tie);
    }

    /** Largest-remainder allocation with canonical unsigned hash/key ties. BigInteger protects
     * budget*weight and total weight; output units remain nonnegative signed long integers.
     * Weights are preferences, not extra water. Zero-weight slots cannot receive runoff.
     * This static quantization rule is not house-monotone: increasing a parent budget can
     * reduce an individual child's allocation by a unit. Do not use as a dynamic rain response.
     */
    public static Map<Integer, Long> apportion(long budget, List<Weight> input) {
        if (budget < 0 || input == null || input.isEmpty()) throw new IllegalArgumentException("Invalid allocation input");
        BigInteger total = BigInteger.ZERO;
        var keys = new HashSet<Integer>();
        for (Weight weight : input) {
            if (weight == null || !keys.add(weight.key())) throw new IllegalArgumentException("Null or duplicate weight");
            total = total.add(weight.amount());
        }
        if (total.signum() == 0 && budget != 0) throw new IllegalArgumentException("Positive budget without eligible weight");
        List<Quota> quotas = new ArrayList<>();
        long assigned = 0;
        for (Weight weight : input) {
            BigInteger[] qr = total.signum() == 0 ? new BigInteger[] {BigInteger.ZERO, BigInteger.ZERO}
                : BigInteger.valueOf(budget).multiply(weight.amount()).divideAndRemainder(total);
            long floor = qr[0].longValueExact(); assigned = Math.addExact(assigned, floor);
            quotas.add(new Quota(weight, floor, qr[1]));
        }
        quotas.sort((a, b) -> {
            int c = b.remainder().compareTo(a.remainder());
            if (c == 0) c = Long.compareUnsigned(a.weight().tie(), b.weight().tie());
            return c == 0 ? Integer.compare(a.weight().key(), b.weight().key()) : c;
        });
        long remaining = budget - assigned;
        if (remaining < 0 || remaining >= quotas.size()) throw new IllegalStateException("Invalid remainder bound");
        Map<Integer, Long> result = new TreeMap<>();
        for (int i = 0; i < quotas.size(); i++) {
            Quota quota = quotas.get(i);
            result.put(quota.weight().key(), Math.addExact(quota.floor(), i < remaining ? 1 : 0));
        }
        return Collections.unmodifiableMap(result);
    }
}
