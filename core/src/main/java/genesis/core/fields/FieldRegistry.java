package genesis.core.fields;

import genesis.core.hash.Lattice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registration finishes before publication; consumers only receive a frozen registry. */
public final class FieldRegistry {
    public static final class Builder {
        private final Map<FieldId<?>, Field<?>> entries = new LinkedHashMap<FieldId<?>, Field<?>>();
        public Builder include(FieldRegistry registry) {
            for (FieldId<?> id : registry.ids()) includeOne(registry, id);
            return this;
        }
        private <T> void includeOne(FieldRegistry registry, FieldId<T> id) { add(id, (x, z) -> registry.get(id, x, z)); }
        public <T> Builder add(FieldId<T> id, Field<T> field) {
            for (FieldId<?> existing : entries.keySet())
                if (existing.name.equals(id.name)) throw new IllegalArgumentException("Duplicate field: " + id.name);
            entries.put(id, field);
            return this;
        }
        public FieldRegistry build() { return new FieldRegistry(entries); }
    }

    private final Map<FieldId<?>, Field<?>> entries;
    private final List<FieldId<?>> ids;
    private FieldRegistry(Map<FieldId<?>, Field<?>> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<FieldId<?>, Field<?>>(entries));
        this.ids = Collections.unmodifiableList(new ArrayList<FieldId<?>>(entries.keySet()));
    }
    public List<FieldId<?>> ids() { return ids; }
    public FieldId<?> find(String name) {
        for (FieldId<?> id : ids) if (id.name.equals(name)) return id;
        throw new IllegalArgumentException("Unknown field: " + name);
    }
    public <T> T get(FieldId<T> id, long x, long z) {
        Lattice.check(x); Lattice.check(z);
        Field<?> field = entries.get(id);
        if (field == null) throw new IllegalArgumentException("Unregistered field: " + id.name);
        return id.type.cast(field.sample(x, z));
    }
    public double scalar(FieldId<?> id, long x, long z) {
        if (id.type == Long.class) throw new IllegalArgumentException("Use exact get/values for 64-bit identifiers");
        Object value = get(id, x, z);
        if (!(value instanceof Number)) throw new IllegalArgumentException("Not a scalar field: " + id.name);
        return ((Number) value).doubleValue();
    }
    public double[] tile(FieldId<?> id, long x, long z, long step, int width, int height) {
        if (id.type == Long.class) throw new IllegalArgumentException("Use exact values for 64-bit identifiers");
        if (!Number.class.isAssignableFrom(id.type)) throw new IllegalArgumentException("Use exact get/values for structured fields");
        Object[] values = values(id, x, z, step, width, height);
        double[] result = new double[values.length];
        for (int i = 0; i < result.length; i++) result[i] = ((Number) values[i]).doubleValue();
        return result;
    }
    /** Exact boxed values; Long field identities are never converted to floating point. */
    public Object[] values(FieldId<?> id, long x, long z, long step, int width, int height) {
        if (width <= 0 || height <= 0 || width > 2048 || height > 2048 || step <= 0)
            throw new IllegalArgumentException("Invalid tile geometry (maximum 2048 per side)");
        Lattice.check(x); Lattice.check(z);
        Lattice.check(Math.addExact(x, Math.multiplyExact(step, width - 1L)));
        Lattice.check(Math.addExact(z, Math.multiplyExact(step, height - 1L)));
        Object[] result = new Object[Math.multiplyExact(width, height)];
        for (int row = 0; row < height; row++)
            for (int col = 0; col < width; col++)
                result[row * width + col] = get(id, x + col * step, z + row * step);
        return result;
    }
}
