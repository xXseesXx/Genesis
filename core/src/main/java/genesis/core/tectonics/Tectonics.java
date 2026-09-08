package genesis.core.tectonics;

import genesis.core.Params;

/** Continuous values only. Ownership/classification live in the integer-only PlateTopology. */
public strictfp final class Tectonics {
    private final PlateTopology topology;
    private final Params params;
    public Tectonics(long seed, Params params) { this.topology = new PlateTopology(seed, params); this.params = params; }
    public PlateTopology.Sample sample(long x, long z) { return topology.sample(x, z); }
    public long child(long x, long z) { return topology.child(sample(x, z).owner, x, z); }
    public int crustFraction(long x, long z) { return topology.crustFraction(x, z); }

    /** Negative divergence of a compact, smoothly interpolated plate velocity field. */
    public double uplift(long x, long z) {
        PlateTopology.Sample sample = sample(x, z);
        double radius = topology.spacing * params.get("upliftRadius"), radius2 = radius * radius;
        double weights = 0, wx = 0, wz = 0, vx = 0, vz = 0, dvx = 0, dvz = 0;
        for (PlateTopology.Site site : sample.neighborhood.sites) {
            double dx = x - site.x, dz = z - site.z;
            double t = 1 - (dx * dx + dz * dz) / radius2;
            if (t <= 0) continue;
            double w = t * t * t;
            double gx = -6 * dx / radius2 * t * t, gz = -6 * dz / radius2 * t * t;
            weights += w; wx += gx; wz += gz;
            vx += w * site.vx; vz += w * site.vz;
            dvx += gx * site.vx; dvz += gz * site.vz;
        }
        double divergence = (dvx * weights - vx * wx + dvz * weights - vz * wz) / (weights * weights);
        return -divergence * topology.spacing / params.get("plateSpeed") * params.get("upliftStrength");
    }
}
