package genesis.oracle;

import genesis.core.Params;
import genesis.core.fields.Noise;
import genesis.core.hash.Hash64;
import java.util.Map;

/** Immutable coordinate-local resistance, 0 (soft) through 1 (resistant).
 * The seeded implementation remains a compatibility field; production terrain may pass a
 * {@link TerrainSubstrate} so the same material controls erosion, soil and block realization.
 */
@FunctionalInterface
public interface TerrainHardness {
    double resistance(long x,long z);
    static TerrainHardness seeded(TectonicTerrain world) {
        if(world==null)throw new IllegalArgumentException("Terrain required");
        var noise=new Noise(Hash64.stream(world.seed,0x45524f53494f4e48L),new Params(Map.of(
            "wavelength",(double)Math.max(16,world.basePlateParams.integer("plateSpacing")/8),"octaves",3.0)));
        int scale=world.settings.size();
        return (x,z)->Math.max(.05,Math.min(.95,.5+.4*noise.sample(Math.floorDiv(x,scale),Math.floorDiv(z,scale))));
    }
}
