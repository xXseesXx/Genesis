package genesis.core.fields;

import genesis.core.Generator;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Optional bounded memoization scoped to a single immutable generator configuration. */
public final class TileCache {
    private static final class Key {
        private final String field;
        private final long[] geometry;
        Key(String field, long x, long z, long step, int w, int h) {
            this.field = field; this.geometry = new long[] { x, z, step, w, h };
        }
        @Override public int hashCode() { return 31 * field.hashCode() + Arrays.hashCode(geometry); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return field.equals(key.field) && Arrays.equals(geometry, key.geometry);
        }
    }
    private final Generator generator;
    private final int capacity;
    private final Map<Key, Object[]> tiles = new LinkedHashMap<Key, Object[]>(16, 0.75f, true);
    public TileCache(Generator generator, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Cache capacity must be positive");
        this.generator = generator; this.capacity = capacity;
    }
    public synchronized double[] tile(FieldId<?> id, long x, long z, long step, int w, int h) {
        if (id.type == Long.class) throw new IllegalArgumentException("Use exact values for 64-bit identifiers");
        Object[] values = values(id, x, z, step, w, h);
        double[] result = new double[values.length];
        for (int i = 0; i < result.length; i++) result[i] = ((Number) values[i]).doubleValue();
        return result;
    }
    public synchronized Object[] values(FieldId<?> id, long x, long z, long step, int w, int h) {
        // Resolve identity first: a forged ID cannot retrieve another field's cached tile.
        if (generator.fields.find(id.name) != id) throw new IllegalArgumentException("Unregistered field identity");
        Key key = new Key(id.name, x, z, step, w, h);
        Object[] result = tiles.get(key);
        if (result == null) {
            result = generator.fields.values(id, x, z, step, w, h);
            tiles.put(key, result);
            if (tiles.size() > capacity) tiles.remove(tiles.keySet().iterator().next());
        }
        return result.clone();
    }
}
