package genesis.oracle;

/** Immutable coordinate-local rainfall input.
 * Values are integer model mm/year, 0..10000. Terrain-aware implementations may additionally
 * solve humidity over a complete continental support; ground composition then partitions rain
 * between infiltration and routed surface runoff.
 */
@FunctionalInterface
public interface RainfallField {
    int millimetresPerYear(long x,long z);
    record Uniform(int amount) implements RainfallField {
        public Uniform {if(amount<0||amount>10000)throw new IllegalArgumentException("Rainfall must be 0..10000 model mm/year");}
        @Override public int millimetresPerYear(long x,long z){return amount;}
    }
}
