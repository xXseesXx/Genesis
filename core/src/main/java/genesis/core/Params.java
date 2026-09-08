package genesis.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable complete configuration. Generator tuning lives only in this registry. */
public final class Params {
    public static final class Spec {
        public final String id, description;
        public final double defaultValue, min, max, step;
        public final boolean integer;
        private Spec(String id, double value, double min, double max, double step,
                     boolean integer, String description) {
            this.id = id; this.defaultValue = value; this.min = min; this.max = max;
            this.step = step; this.integer = integer; this.description = description;
        }
    }

    public static final Map<String, Spec> SPECS;
    static {
        Map<String, Spec> specs = new LinkedHashMap<String, Spec>();
        add(specs, new Spec("wavelength", 4096, 16, 1048576, 16, true, "Largest noise wavelength in blocks"));
        add(specs, new Spec("octaves", 6, 1, 10, 1, true, "Number of detail octaves"));
        add(specs, new Spec("persistence", 0.5, 0.05, 0.95, 0.01, false, "Amplitude retained per octave"));
        add(specs, new Spec("lacunarity", 2, 2, 4, 1, true, "Integer frequency multiplier per octave"));
        add(specs, new Spec("amplitude", 1, 0.1, 2, 0.05, false, "Output amplitude (not terrain elevation)"));
        add(specs, new Spec("plateSpacing", 16384, 8192, 262144, 4096, true, "Mean tectonic plate spacing in blocks"));
        add(specs, new Spec("plateJitter", 50, 0, 50, 1, true, "Site jitter as percent of cell width; capped for bounded queries"));
        add(specs, new Spec("subdivisions", 4, 2, 8, 1, true, "Sub-plate lattice divisions per parent spacing"));
        add(specs, new Spec("plateSpeed", 64, 1, 128, 1, true, "Maximum absolute plate velocity component in model units"));
        add(specs, new Spec("transformRatio", 35, 0, 100, 1, true, "Transform when normal motion is this percent or less of shear"));
        add(specs, new Spec("continentalPercent", 45, 0, 100, 1, true, "Probability of continental crust per plate, not land fraction"));
        add(specs, new Spec("crustAgeMax", 3000, 100, 4500, 100, true, "Maximum synthetic plate crust age in Ma"));
        add(specs, new Spec("upliftRadius", 1.25, 1.1, 2, 0.05, false, "Smooth velocity support radius divided by plate spacing"));
        add(specs, new Spec("upliftStrength", 1, 0, 2, 0.05, false, "Compression signal amplitude; not elevation"));
        add(specs, new Spec("coarseSpacing", 4096, 1024, 16384, 1024, true, "Canonical coastline anchor spacing in blocks"));
        add(specs, new Spec("continentScale", 65536, 16384, 1048576, 16384, true, "Wavelength of integer continental variation"));
        add(specs, new Spec("crustInfluence", 700, 0, 1000, 25, true, "Crust contribution to continental score in permille"));
        add(specs, new Spec("crustBlendRadius", 1500, 1100, 2500, 50, true, "Integer crust blending radius in permille of plate spacing"));
        add(specs, new Spec("seaThreshold", 500, 200, 800, 10, true, "Fixed sea threshold in permille; never a viewport quantile"));
        add(specs, new Spec("landHeight", 2200, 100, 6000, 100, true, "Continental elevation scale in model meters"));
        add(specs, new Spec("oceanDepth", 4200, 100, 10000, 100, true, "Bathymetry scale in model meters"));
        add(specs, new Spec("mountainHeight", 2600, 0, 8000, 100, true, "Uplift contribution, tapered to zero at committed coasts"));
        add(specs, new Spec("seaSearchRadius", 12, 1, 32, 1, true, "Maximum coarse Manhattan search radius; unresolved stays explicit"));
        SPECS = Collections.unmodifiableMap(specs);
    }
    private static void add(Map<String, Spec> specs, Spec spec) { specs.put(spec.id, spec); }
    private final Map<String, Double> values;

    public Params(Map<String, Double> overrides) {
        for (String key : overrides.keySet())
            if (!SPECS.containsKey(key)) throw new IllegalArgumentException("Unknown parameter: " + key);
        Map<String, Double> checked = new LinkedHashMap<String, Double>();
        for (Spec spec : SPECS.values()) {
            double v = overrides.containsKey(spec.id) ? overrides.get(spec.id) : spec.defaultValue;
            if (!Double.isFinite(v) || v < spec.min || v > spec.max || (spec.integer && v != Math.rint(v)))
                throw new IllegalArgumentException("Invalid parameter: " + spec.id);
            checked.put(spec.id, v);
        }
        values = Collections.unmodifiableMap(checked);
    }

    public static Params defaults() { return new Params(Collections.<String, Double>emptyMap()); }
    public double get(String id) {
        Double value = values.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown parameter: " + id);
        return value;
    }
    public int integer(String id) { return (int) get(id); }
    public Map<String, Double> values() { return values; }
    @Override public boolean equals(Object other) { return other instanceof Params && values.equals(((Params) other).values); }
    @Override public int hashCode() { return values.hashCode(); }
}
