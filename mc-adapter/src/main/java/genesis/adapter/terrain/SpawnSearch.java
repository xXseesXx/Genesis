package genesis.adapter.terrain;

/** Bounded deterministic dry-land search in a world with large ocean regions. */
public final class SpawnSearch {

    public record Position(int x, int y, int z) {}

    private SpawnSearch() {}

    public static Position find(TerrainColumns columns) {
        for (int ring = 0; ring <= 256; ring++) {
            int count = ring == 0 ? 1 : 64;
            for (int n = 0; n < count; n++) {
                double angle = 2 * Math.PI * n / count;
                int x = (int) Math.round(ring * 256 * StrictMath.cos(angle));
                int z = (int) Math.round(ring * 256 * StrictMath.sin(angle));
                if (!columns.terrain.sample(x, z)
                    .land()) continue;
                var column = columns.sample(x, z);
                if (column.water() == TerrainColumns.Water.NONE && column.groundY() <= 253) {
                    return new Position(x, column.groundY() + 1, z);
                }
            }
        }
        throw new IllegalStateException("Genesis could not find dry spawn land within 65536 blocks");
    }
}
