package genesis.core.elevation;

import genesis.core.Params;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;

/** Floating-point relief conditioned on integer coarse coast signs. */
public strictfp final class MacroElevation {
    private final CoastTopology coast;
    private final Params params;
    private final FieldRegistry inputs;
    public MacroElevation(CoastTopology coast, Params params, FieldRegistry inputs) {
        this.coast = coast; this.params = params; this.inputs = inputs;
    }
    public double continentality(long x, long z) { return coast.numerator(x, z) / (coast.spacing * 1000.0); }
    public double elevation(long x, long z) {
        double score = continentality(x, z);
        if (score <= 0) return score * params.get("oceanDepth");
        double uplift = Math.max(0, inputs.get(Fields.UPLIFT, x, z));
        return score * params.get("landHeight") + score * score * params.get("mountainHeight") * uplift / (1 + uplift);
    }
}
