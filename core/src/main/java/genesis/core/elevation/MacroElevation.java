package genesis.core.elevation;

import genesis.core.Params;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;

/** Floating-point relief conditioned on integer coarse coast signs. */
public strictfp final class MacroElevation {
    private final CoastTopology coast;
    private final Params params;
    private final FieldRegistry inputs;
    private final boolean continental;
    public MacroElevation(CoastTopology coast, Params params, FieldRegistry inputs) {
        this(coast, params, inputs, false);
    }
    public MacroElevation(CoastTopology coast, Params params, FieldRegistry inputs, boolean continental) {
        this.coast = coast; this.params = params; this.inputs = inputs;
        this.continental = continental;
    }
    public double continentality(long x, long z) { return coast.numerator(x, z) / (coast.spacing * 1000.0); }
    public double elevation(long x, long z) {
        double score = continentality(x, z);
        if (score <= 0) return score * params.get("oceanDepth");
        return score * params.get("landHeight") + tectonicRelief(score, x, z) + detail(score, x, z);
    }
    public double tectonicRelief(long x, long z) { return tectonicRelief(continentality(x, z), x, z); }
    public double detail(long x, long z) { return detail(continentality(x, z), x, z); }
    private double tectonicRelief(double score, long x, long z) {
        if (score <= 0) return 0;
        double uplift = Math.max(0, inputs.get(Fields.UPLIFT, x, z));
        return (continental ? score : score * score) * params.get("mountainHeight") * uplift / (1 + uplift);
    }
    private double detail(double score, long x, long z) {
        if (!continental || score <= 0 || params.get("terrainDetailHeight") == 0) return 0;
        double ridge = inputs.get(Fields.RIDGES, x, z);
        return score * params.get("terrainDetailHeight") * ridge * ridge;
    }
}
