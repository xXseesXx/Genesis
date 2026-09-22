package genesis.adapter.terrain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.Executors;

import org.junit.Test;

import genesis.adapter.GenesisBiomeManager;
import genesis.adapter.GenesisDendriticWorldType;
import genesis.adapter.GenesisWorldType;
import genesis.oracle.AdaptiveHydrology;
import genesis.oracle.ContinentalHydrology;
import genesis.oracle.FluvialNetwork;
import genesis.oracle.HydraulicErosion;
import genesis.oracle.HydrologyTuning;
import genesis.oracle.MassWasting;
import genesis.oracle.TerrainSubstrate;

public class TerrainColumnsTest {

    @Test
    public void adapterUsesCurrentMultipleFlowGeneratorAndRealizesSecondaryBranches() {
        var columns = new TerrainColumns(42);
        assertEquals("genesis-columns-v10", TerrainColumns.VERSION);
        assertEquals("continental-hydrology-v6", ContinentalHydrology.VERSION);
        assertEquals("fluvial-network-v5", FluvialNetwork.VERSION);
        assertEquals("continental-erosion-v3", HydraulicErosion.VERSION);
        assertEquals(HydrologyTuning.defaults(), columns.tuning);
        assertSame(columns.tuning, columns.hydrology.tuning);
        assertEquals(1450, columns.terrain.settings.forcingPermille());
        assertEquals(10, columns.terrain.settings.detailHeight());
        assertEquals(18, columns.terrain.settings.plateRelief());
        assertEquals(24, columns.terrain.settings.plateTilt());
        assertEquals(170, columns.terrain.settings.plateRoughnessPermille());

        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        int splitCells = 0, secondaryProfiles = 0, realized = 0;
        for (int p = 0; p < root.size(); p++) {
            if (root.receiverCount(p) > 1) splitCells++;
            long outgoing = 0;
            for (int edge = 0; edge < root.receiverCount(p); edge++) {
                outgoing = Math.addExact(outgoing, root.edgeFlux(p, edge));
                int q = root.receiver(p, edge);
                if (q == root.downstream(p)) continue;
                var profile = root.fluvial()
                    .edgeProfile(p, edge);
                if (profile == null) continue;
                secondaryProfiles++;
                var center = root.fluvial()
                    .edgeCenterline(p, edge, .5);
                var column = columns.sampleBase(Math.round(center.x()), Math.round(center.z()), root);
                if (column.flux() == profile.flux() && column.channelBed()) realized++;
            }
            if (root.receiverCount(p) > 0) assertEquals(root.flux(p), outgoing);
        }
        assertTrue("No multiple-flow cells reached the adapter", splitCells > 0);
        assertTrue("No secondary branch profiles reached the adapter", secondaryProfiles > 0);
        assertTrue("No secondary branch was realized as a Minecraft channel", realized > 0);
    }

    @Test
    public void exposedResistantRidgesCreateRasterizedWaterfalls() {
        var columns = new TerrainColumns(42);
        int candidates = 0, waterfalls = 0;
        for (int[] location : new int[][] { { -1, -1 }, { 0, 0 }, { 8, 6 } }) {
            var root = columns.hydrology.root(columns.terrain.continent(location[0], location[1]));
            for (int p = 0; p < root.size(); p++) for (int edge = 0; edge < root.receiverCount(p); edge++) {
                var profile = root.fluvial()
                    .edgeProfile(p, edge);
                if (profile == null) continue;
                int q = root.receiver(p, edge);
                if ((root.filled(p) - root.filled(q)) / 1000.0 < TerrainColumns.WATERFALL_MIN_DROP) continue;
                candidates++;
                for (double t = .52; t <= .64; t += .01) {
                    var point = root.fluvial()
                        .edgeCenterline(p, edge, t);
                    var column = columns.sampleBase(Math.round(point.x()), Math.round(point.z()), root);
                    if (column.water() != TerrainColumns.Water.RIVER || column.waterY() - column.groundY() < 2) {
                        continue;
                    }
                    byte[] blocks = new byte[16 * 16 * TerrainColumns.HEIGHT];
                    ChunkRaster.fillColumn(blocks, 0, 0, column);
                    for (int y = column.groundY() + 1; y <= column.waterY(); y++) {
                        assertEquals(ChunkRaster.WATER, blocks[ChunkRaster.index(0, y, 0)]);
                    }
                    waterfalls++;
                    break;
                }
            }
        }
        assertTrue("No steep profiled river reaches exercised the waterfall search", candidates > 0);
        assertTrue("No resistant exposed-rock river ridge became a waterfall", waterfalls > 0);
    }

    @Test
    public void adaptiveHydrologyRefinesOnlyCommittedWaterAndConservesParentSource() {
        var columns = new TerrainColumns(42);
        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        int channelParent = -1;
        int dryParent = -1;
        for (int p = 0; p < root.size(); p++) {
            if (!root.active(p) || root.sea(p)) continue;
            if (channelParent < 0 && root.profile(p) != null) channelParent = p;
            if (dryParent < 0 && root.profile(p) == null
                && root.fluvial()
                    .lakeForCell(p) == null)
                dryParent = p;
        }
        assertTrue(channelParent >= 0);
        assertTrue(dryParent >= 0);

        var channel = columns.refinement.sample(root, root.x(channelParent), root.z(channelParent));
        var dry = columns.refinement.sample(root, root.x(dryParent), root.z(dryParent));
        assertEquals(AdaptiveHydrology.Level.FINE, channel.level());
        assertEquals(2, channel.step());
        assertEquals(channelParent, channel.parent());
        assertEquals(root.downstream(channelParent), channel.downstream());
        assertEquals(root.flux(channelParent), channel.parentFlux());
        assertEquals(AdaptiveHydrology.Level.COARSE, dry.level());
        assertEquals(root.step, dry.step());

        long allocated = 0;
        long x0 = root.x(channelParent) - root.step / 2L;
        long z0 = root.z(channelParent) - root.step / 2L;
        for (long z = z0; z < z0 + root.step; z += 2) for (long x = x0; x < x0 + root.step; x += 2) {
            allocated += AdaptiveHydrology.apportionedSource(root, channelParent, x, z, 2);
        }
        assertEquals(root.source(channelParent), allocated);
        // TerrainColumns consults this same immutable refinement before realizing water.
        assertNotNull(columns.sample(root.x(channelParent), root.z(channelParent)));
    }

    @Test
    public void forcedTwoBlockWaterMasksFollowTerrainAndRetainLakeSeeds() {
        var columns = new TerrainColumns(42);
        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        int coastDifferences = 0;
        boolean foundBoundary = false;
        int width = root.bounds.width();
        for (int p = 0; p < root.size() && coastDifferences == 0; p++) {
            if (!root.sea(p)) continue;
            int px = p % width, pz = p / width;
            for (int[] direction : new int[][] { { 1, 0 }, { 0, 1 } }) {
                int nx = px + direction[0], nz = pz + direction[1];
                if (nx >= width || nz >= root.bounds.height()) continue;
                int q = nz * width + nx;
                if (!root.active(q) || root.sea(q)) continue;
                foundBoundary = true;
                long minX = Math.min(root.x(p), root.x(q));
                long minZ = Math.min(root.z(p), root.z(q));
                for (long z = minZ; z <= minZ + root.step; z += 2) for (long x = minX; x <= minX + root.step; x += 2) {
                    boolean fine = columns.refinement.seaAt(root, x, z);
                    if (fine) assertTrue(
                        columns.terrain.sample(x, z)
                            .elevation() <= columns.terrain.seaLevel());
                    if (fine != root.seaAt(x, z)) coastDifferences++;
                }
                if (coastDifferences > 0) break;
            }
        }
        assertTrue("No sampled maritime boundary found", foundBoundary);
        assertTrue("Two-block coast mask did not differ from coarse interpolation", coastDifferences > 0);

        FluvialNetwork.Lake chosen = null;
        for (var lake : root.fluvial()
            .lakes()) if (lake.flux() > 0) {
                chosen = lake;
                break;
            }
        assertNotNull(chosen);
        int retainedSeeds = 0;
        for (int p = 0; p < root.size(); p++) {
            var lake = root.fluvial()
                .lakeForCell(p);
            if (lake == null || lake.id() != chosen.id()) continue;
            double bed = root.bed(p) / 1000.0;
            assertNotNull(columns.refinement.lakeAt(root, root.x(p), root.z(p), bed));
            retainedSeeds++;
        }
        assertTrue(retainedSeeds > 0);
    }

    @Test
    public void dendriticForkIsDeterministicDistinctAndKeepsCanonicalHydrology() {
        var classic = new TerrainColumns(42, TerrainColumns.Style.CLASSIC);
        var dendritic = new TerrainColumns(42, TerrainColumns.Style.DENDRITIC);
        var cold = new TerrainColumns(42, TerrainColumns.Style.DENDRITIC);
        var classicRoot = classic.hydrology.root(classic.terrain.continent(-1, -1));
        var dendriticRoot = dendritic.hydrology.root(dendritic.terrain.continent(-1, -1));
        var coldRoot = cold.hydrology.root(cold.terrain.continent(-1, -1));
        int changed = 0;
        int compared = 0;
        long changedX = 0, changedZ = 0;
        for (int p = 0; p < dendriticRoot.size() && compared < 256; p += 17) {
            if (!dendriticRoot.active(p) || dendriticRoot.sea(p)) continue;
            long x = dendriticRoot.x(p), z = dendriticRoot.z(p);
            var base = classic.sampleBase(x, z, classicRoot);
            var shaped = dendritic.sampleBase(x, z, dendriticRoot);
            assertEquals(shaped, cold.sampleBase(x, z, coldRoot));
            assertEquals(base.flux(), shaped.flux());
            assertEquals(base.streamOrder(), shaped.streamOrder());
            assertEquals(base.water(), shaped.water());
            if (base.erodedY() != shaped.erodedY()) {
                changed++;
                changedX = x;
                changedZ = z;
            }
            compared++;
        }
        assertEquals(TerrainColumns.Style.CLASSIC, classic.style);
        assertEquals(TerrainColumns.Style.DENDRITIC, dendritic.style);
        assertTrue("Dendritic fork did not alter any sampled surface columns", changed > 0);
        int chunkX = Math.toIntExact(Math.floorDiv(changedX, 16));
        int chunkZ = Math.toIntExact(Math.floorDiv(changedZ, 16));
        var warmRaster = ChunkRaster.generate(dendritic, chunkX, chunkZ);
        var coldRaster = ChunkRaster.generate(cold, chunkX, chunkZ);
        assertArrayEquals(warmRaster.blocks(), coldRaster.blocks());
        assertArrayEquals(warmRaster.metadata(), coldRaster.metadata());
        assertArrayEquals(warmRaster.columns(), coldRaster.columns());
    }

    @Test
    public void chunkRasterHasDistinctFullHeightColumns() {
        boolean[] visited = new boolean[16 * 16 * TerrainColumns.HEIGHT];
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 256; y++) {
            int index = ChunkRaster.index(x, y, z);
            assertTrue("Aliased block-array index", !visited[index]);
            visited[index] = true;
        }
        for (boolean present : visited) assertTrue("Unused block-array index", present);
        assertEquals(0, ChunkRaster.index(0, 0, 0));
        assertEquals(256, ChunkRaster.index(0, 0, 1));
        assertEquals(4096, ChunkRaster.index(1, 0, 0));
        assertEquals(65535, ChunkRaster.index(15, 255, 15));
    }

    @Test
    public void minecraftSpawnFuzzIsValidRandomBound() {
        assertTrue(GenesisWorldType.SPAWN_FUZZ > 0);
        new Random(42).nextInt(GenesisWorldType.SPAWN_FUZZ);
    }

    @Test
    public void dendriticWorldTypeIdFitsMinecraftLimit() {
        assertTrue(GenesisDendriticWorldType.NAME.length() <= 16);
    }

    @Test
    public void chunkLocalRootCacheMatchesPublicSamplingAcrossSignedSnapBoundaries() {
        var columns = new TerrainColumns(42);
        int[][] chunks = { { 3, 3 }, { 4, 4 }, { -4, -4 }, { -5, -5 }, { 3, -4 }, { -5, 4 } };
        for (int[] chunk : chunks) {
            var cache = new ChunkRaster.RootCache(columns);
            var expectedColumns = new TerrainColumns.Column[256];
            byte[] expectedBlocks = new byte[16 * 16 * TerrainColumns.HEIGHT];
            byte[] expectedMetadata = new byte[expectedBlocks.length];
            ContinentalHydrology.Root first = null;
            for (int z = 15; z >= 0; z--) for (int x = 15; x >= 0; x--) {
                long worldX = chunk[0] * 16L + x, worldZ = chunk[1] * 16L + z;
                var root = cache.resolve(worldX, worldZ);
                if (first == null) first = root;
                else assertSame("One canonical root per default-size chunk", first, root);
                var expected = columns.sample(worldX, worldZ);
                assertEquals(expected, columns.sample(worldX, worldZ, root));
                expectedColumns[z * 16 + x] = expected;
                ChunkRaster.fillColumn(expectedBlocks, expectedMetadata, x, z, expected);
            }
            assertEquals("One expensive root resolution per chunk", 1, cache.resolutionCount());
            var actual = ChunkRaster.generate(columns, chunk[0], chunk[1]);
            assertArrayEquals(expectedColumns, actual.columns());
            assertArrayEquals(expectedBlocks, actual.blocks());
            assertArrayEquals(expectedMetadata, actual.metadata());
        }
    }

    @Test
    public void spawnIsDryAndHasHeadroom() {
        for (long seed : new long[] { 42, 0, -1, Long.MIN_VALUE }) {
            var columns = new TerrainColumns(seed);
            var spawn = SpawnSearch.find(columns);
            var column = columns.sample(spawn.x(), spawn.z());
            assertEquals(TerrainColumns.Water.NONE, column.water());
            assertEquals(column.groundY() + 1, spawn.y());
            assertTrue(spawn.y() < 255);
            assertEquals(spawn, SpawnSearch.find(columns));
        }
    }

    @Test
    public void defaultColumnsExposeClimateSubstrateAndGroundDiagnostics() {
        var columns = new TerrainColumns(42);
        assertNotNull(columns.hydrology.climate);
        assertNotNull(columns.hydrology.substrate);
        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        var rainfallValues = new HashSet<Integer>();
        var humidityValues = new HashSet<Integer>();
        var rocks = EnumSet.noneOf(TerrainSubstrate.Rock.class);
        var drainage = EnumSet.noneOf(TerrainSubstrate.Drainage.class);
        int sampled = 0;
        for (int p = 0; p < root.size(); p++) {
            if (!root.active(p) || root.sea(p)) continue;
            rainfallValues.add(root.rainfall(p));
            humidityValues.add((int) Math.round(root.humidity(p) * 1000));
            rocks.add(root.rock(p));
            drainage.add(root.drainage(p));
            assertTrue(root.rainfall(p) >= 0 && root.rainfall(p) <= 10_000);
            assertTrue(root.humidity(p) >= 0 && root.humidity(p) <= 1);
            assertTrue(root.runoffPermille(p) >= 0 && root.runoffPermille(p) <= 1000);
            assertTrue(root.infiltrationPermille(p) >= 0 && root.infiltrationPermille(p) <= 1000);
            assertTrue(root.soilDepth(p) >= 0 && root.soilDepth(p) <= 12);
            long effectiveRain = Math.floorDiv((long) root.rainfall(p) * root.runoffPermille(p) + 500, 1000);
            assertEquals(Math.multiplyExact(effectiveRain, (long) root.step * root.step), root.source(p));

            if (sampled >= 64 || p % 97 != 0) continue;
            var column = columns.sample(root.x(p), root.z(p), root);
            assertEquals(root.rainfall(p), column.rainfall());
            assertEquals(root.humidity(p), column.humidity(), 0);
            assertEquals(root.runoffPermille(p), column.runoffPermille());
            assertEquals(root.infiltrationPermille(p), column.infiltrationPermille());
            if (column.water() == TerrainColumns.Water.NONE && !column.channelBed()) {
                assertEquals(root.soilDepth(p), column.soilDepth(), 0);
                assertEquals(root.soilDepth(p), column.bedrockDepth(), 0);
            }
            assertEquals(root.rock(p), column.rock());
            assertEquals(root.drainage(p), column.drainage());
            assertTrue(column.bedrockY() <= column.erodedY());
            assertEquals(column.windSpeed(), StrictMath.hypot(column.windX(), column.windZ()), 1e-12);
            assertTrue(column.windSpeed() >= .6);
            assertNotNull(GenesisBiomeManager.biome(column));
            assertEquals(Math.min(1, column.rainfall() / 2000.0), GenesisBiomeManager.rainfall(column), 1e-7);
            sampled++;
        }
        assertTrue("Climate rainfall varies across the complete root", rainfallValues.size() > 1);
        assertTrue("Climate humidity varies across the complete root", humidityValues.size() > 1);
        assertTrue("Substrate exposes multiple rock families", rocks.size() > 1);
        assertTrue("Substrate exposes multiple drainage classes", drainage.size() > 1);
        assertTrue("Fine adapter diagnostic samples", sampled > 0);
    }

    @Test
    public void vanillaPaletteCoversEveryRockAndDrainageClass() {
        assertEquals(ChunkRaster.STAINED_HARDENED_CLAY, ChunkRaster.rockBlock(TerrainSubstrate.Rock.BASALT));
        assertEquals(ChunkRaster.BLACK_CLAY_METADATA, ChunkRaster.rockMetadata(TerrainSubstrate.Rock.BASALT));
        assertEquals(ChunkRaster.STONE, ChunkRaster.rockBlock(TerrainSubstrate.Rock.GRANITE));
        assertEquals(ChunkRaster.STAINED_HARDENED_CLAY, ChunkRaster.rockBlock(TerrainSubstrate.Rock.SHALE));
        assertEquals(ChunkRaster.GRAY_CLAY_METADATA, ChunkRaster.rockMetadata(TerrainSubstrate.Rock.SHALE));
        assertEquals(ChunkRaster.STAINED_HARDENED_CLAY, ChunkRaster.rockBlock(TerrainSubstrate.Rock.LIMESTONE));
        assertEquals(ChunkRaster.SANDSTONE, ChunkRaster.rockBlock(TerrainSubstrate.Rock.SANDSTONE));
        assertEquals(ChunkRaster.SAND, ChunkRaster.subsoilBlock(TerrainSubstrate.Drainage.WELL_DRAINED));
        assertEquals(ChunkRaster.DIRT, ChunkRaster.subsoilBlock(TerrainSubstrate.Drainage.MODERATE));
        assertEquals(ChunkRaster.CLAY, ChunkRaster.subsoilBlock(TerrainSubstrate.Drainage.POOR));
    }

    @Test
    public void erosionAndWaterComeFromTheCanonicalSolve() {
        var columns = new TerrainColumns(42);
        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        int changed = 0, lakes = 0, rivers = 0, oceans = 0, exactSea = 0;
        for (int p = 0; p < root.size(); p++) {
            if (!root.active(p)) continue;
            boolean erosion = root.erosionDepth(p) >= 1 && changed < 32;
            boolean lake = root.lakeDepth(p) >= 2 && lakes < 32;
            boolean ocean = root.sea(p)
                && (oceans < 32 || exactSea == 0 && root.originalBed(p) == columns.terrain.seaLevel() * 1000);
            boolean river = root.profile(p) != null && root.lakeDepth(p) == 0 && rivers < 32;
            if (!erosion && !lake && !ocean && !river) continue;
            long x = root.x(p), z = root.z(p);
            var c = columns.sample(x, z);
            assertEquals(
                columns.terrain.sample(x, z)
                    .elevation(),
                c.originalY());
            assertEquals((int) Math.floor(root.bed(p) / 1000.0), c.erodedY());
            assertTrue(c.groundY() <= c.erodedY());
            if (erosion) {
                assertTrue(c.erodedY() < c.originalY());
                changed++;
            }
            if (lake && root.flux(p) > 0) {
                assertEquals(TerrainColumns.Water.LAKE, c.water());
                assertTrue(c.waterY() > c.groundY());
                lakes++;
            }
            if (ocean) {
                assertEquals(TerrainColumns.Water.OCEAN, c.water());
                assertEquals(63, c.waterY());
                assertTrue(c.groundY() < c.waterY());
                byte[] blocks = new byte[16 * 16 * TerrainColumns.HEIGHT];
                ChunkRaster.fillColumn(blocks, 0, 0, c);
                assertEquals(ChunkRaster.WATER, blocks[ChunkRaster.index(0, c.waterY(), 0)]);
                assertEquals(ChunkRaster.SAND, blocks[ChunkRaster.index(0, c.groundY(), 0)]);
                if (c.originalY() == 63) exactSea++;
                oceans++;
            }
            if (river && c.originalY() > 63) {
                var channel = root.channelAt(x, z);
                assertNotNull(channel);
                assertEquals(
                    root.river(x, z)
                        .distance(),
                    channel.centerDistance(),
                    0);
                assertEquals(
                    root.river(x, z)
                        .flux(),
                    channel.profile()
                        .flux());
                assertEquals(
                    channel.profile()
                        .meanDischarge(),
                    c.meanDischarge(),
                    0);
                assertTrue(c.channelBed());
                assertEquals(TerrainColumns.Water.RIVER, c.water());
                assertTrue(c.waterY() > c.groundY());
                rivers++;
            }
        }
        assertTrue("Erosion fixture", changed > 0);
        assertTrue("Lake fixture", lakes > 0);
        assertTrue("River fixture", rivers > 0);
        assertTrue("Ocean fixture", oceans > 0);
        assertTrue("Exact-sea maritime fixture", exactSea > 0);
    }

    @Test
    public void mixedCoastChunkFillsTheContinuousConnectedOceanMask() {
        var columns = new TerrainColumns(42);
        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        boolean found = false;
        int width = root.bounds.width();
        for (int p = 0; p < root.size() && !found; p++) {
            if (!root.active(p)) continue;
            for (int q : new int[] { p + 1, p + width }) {
                if (q >= root.size() || !root.active(q)
                    || p / width != q / width && q != p + width
                    || root.sea(p) == root.sea(q)) continue;
                long middleX = (root.x(p) + root.x(q)) / 2;
                long middleZ = (root.z(p) + root.z(q)) / 2;
                int chunkX = Math.toIntExact(Math.floorDiv(middleX, 16));
                int chunkZ = Math.toIntExact(Math.floorDiv(middleZ, 16));
                var raster = ChunkRaster.generate(columns, chunkX, chunkZ);
                int land = 0, ocean = 0, formerNearestNodeHole = 0;
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    long worldX = chunkX * 16L + x, worldZ = chunkZ * 16L + z;
                    var raw = columns.terrain.sample(worldX, worldZ);
                    var localRoot = columns.rootAt(columns.hydrology.snap(worldX), columns.hydrology.snap(worldZ));
                    var column = raster.columns()[z * 16 + x];
                    if (raw.land()) land++;
                    if (!raw.land() && localRoot.seaAt(worldX, worldZ)) {
                        ocean++;
                        if (!localRoot.sea(localRoot.index(worldX, worldZ))) formerNearestNodeHole++;
                        assertEquals(TerrainColumns.Water.OCEAN, column.water());
                        assertEquals(columns.terrain.seaLevel(), column.waterY());
                        assertEquals(
                            ChunkRaster.WATER,
                            raster.blocks()[ChunkRaster.index(x, columns.terrain.seaLevel(), z)]);
                    }
                }
                found = land > 0 && ocean > 0 && formerNearestNodeHole > 0;
            }
        }
        assertTrue("No mixed coastal chunk exercised the continuous sea mask", found);
    }

    @Test
    public void voxelWaterBoundaryRaisesEveryExposedDryCardinalFace() {
        var dry = new TerrainColumns.Column(42, 40, 40, -1, TerrainColumns.Water.NONE);
        var lowDry = new TerrainColumns.Column(35, 35, 35, -1, TerrainColumns.Water.NONE);
        var wet = new TerrainColumns.Column(48, 36, 36, 48, TerrainColumns.Water.LAKE);

        var contained = TerrainColumns.containWater(dry, wet, lowDry, lowDry, lowDry);
        assertEquals(48, contained.groundY());
        assertEquals(40, contained.erodedY());
        assertEquals(TerrainColumns.Water.NONE, contained.water());
        assertEquals(-1, contained.waterY());
        assertSame(wet, TerrainColumns.containWater(wet, dry, dry, dry, dry));
        assertSame(dry, TerrainColumns.containWater(dry, lowDry, lowDry, lowDry, lowDry));
    }

    @Test
    public void canonicalWaterColumnsAgreeAcrossRealChunkSeams() {
        var terrain = new TerrainColumns(42);
        var root = terrain.hydrology.root(terrain.terrain.continent(-1, -1));
        int waterCell = -1;
        for (int p = 0; p < root.size(); p++) {
            if (root.active(p) && (root.sea(p) || root.lakeDepth(p) > 0 || root.profile(p) != null)) {
                waterCell = p;
                break;
            }
        }
        assertTrue("Natural water fixture", waterCell >= 0);

        // Canonical nodes are multiples of the 32-block hydro step and therefore sit on
        // both an X and a Z chunk seam. Generate all four independently as Minecraft does.
        int eastChunk = Math.toIntExact(Math.floorDiv(root.x(waterCell), 16));
        int southChunk = Math.toIntExact(Math.floorDiv(root.z(waterCell), 16));
        var northwest = ChunkRaster.generate(terrain, eastChunk - 1, southChunk - 1);
        var northeast = ChunkRaster.generate(terrain, eastChunk, southChunk - 1);
        var southwest = ChunkRaster.generate(terrain, eastChunk - 1, southChunk);
        var southeast = ChunkRaster.generate(terrain, eastChunk, southChunk);

        int wetFaces = 0;
        for (int along = 0; along < 16; along++) {
            wetFaces += assertContainedFace(northwest.columns()[along * 16 + 15], northeast.columns()[along * 16]);
            wetFaces += assertContainedFace(southwest.columns()[along * 16 + 15], southeast.columns()[along * 16]);
            wetFaces += assertContainedFace(northwest.columns()[15 * 16 + along], southwest.columns()[along]);
            wetFaces += assertContainedFace(northeast.columns()[15 * 16 + along], southeast.columns()[along]);
        }
        assertTrue("Water fixture did not exercise a chunk face", wetFaces > 0);
    }

    @Test
    public void fineShallowLakeHasFlatWaterAndSedimentBed() {
        var columns = new TerrainColumns(42);
        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        LakeFixture fixture = findShallowLake(columns, root);
        assertNotNull("Fine shallow lake fixture", fixture);
        assertTrue(
            fixture.x != root.x(root.index(fixture.x, fixture.z))
                || fixture.z != root.z(root.index(fixture.x, fixture.z)));
        assertTrue(fixture.sample.depth() > 0 && fixture.sample.depth() < 1);
        assertEquals(
            fixture.sample.lake()
                .surface(),
            fixture.nodeSample.surface(),
            0);
        assertEquals(
            fixture.sample.lake()
                .id(),
            fixture.nodeSample.lake()
                .id());
        assertEquals(TerrainColumns.Water.LAKE, fixture.column.water());
        assertEquals((int) Math.floor(fixture.sample.surface()), fixture.column.waterY());
        assertTrue(fixture.column.waterY() > fixture.column.groundY());

        byte[] blocks = new byte[16 * 16 * TerrainColumns.HEIGHT];
        ChunkRaster.fillColumn(blocks, 0, 0, fixture.column);
        assertEquals(ChunkRaster.WATER, blocks[ChunkRaster.index(0, fixture.column.waterY(), 0)]);
        byte bed = blocks[ChunkRaster.index(0, fixture.column.groundY(), 0)];
        assertTrue(bed == ChunkRaster.SAND || bed == ChunkRaster.CLAY);
        assertNotEquals(ChunkRaster.DIRT, bed);
        assertNotEquals(ChunkRaster.GRASS, bed);
        for (int y = 0; y <= fixture.column.groundY(); y++) {
            assertNotEquals(
                "Wet column contains an underground air cavity at Y=" + y,
                ChunkRaster.AIR,
                blocks[ChunkRaster.index(0, y, 0)]);
        }
    }

    @Test
    public void finalLakeStageBackfillsAnIncisedRiverMouth() {
        var columns = new TerrainColumns(42);
        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        boolean found = false;
        for (int p = 0; p < root.size() && !found; p++) {
            var lake = root.fluvial()
                .receivingLake(p);
            if (lake == null || root.profile(p) == null) continue;
            int q = root.downstream(p);
            var mouth = root.channelAt(root.x(q), root.z(q));
            if (mouth == null || mouth.sourceSegment() != p) continue;

            assertEquals(lake.surface(), mouth.waterSurface(), 0);
            assertTrue("Incised inlet bed should sit below final lake stage", mouth.bedElevation() < lake.surface());
            var column = columns.sample(root.x(q), root.z(q), root);
            assertEquals(TerrainColumns.Water.LAKE, column.water());
            assertEquals((int) Math.floor(lake.surface()), column.waterY());
            assertTrue(column.groundY() < column.waterY());
            assertFalse("Lake surface should not expose a river-bank classification", column.channelBed());
            found = true;
        }
        assertTrue("Natural profiled lake inlet fixture", found);
    }

    @Test
    public void riverOutletStartsAtLakeSpillWithoutBanksAcrossTheLakeSurface() {
        var columns = new TerrainColumns(42);
        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        boolean found = false;
        for (var lake : root.fluvial()
            .lakes()) {
            int spill = lake.spillCell();
            if (root.profile(spill) == null) continue;
            assertEquals(lake.outletCell(), root.downstream(spill));
            var start = root.fluvial()
                .centerline(spill, 0);
            assertEquals(root.x(spill), start.x(), 0);
            assertEquals(root.z(spill), start.z(), 0);
            var column = columns.sampleBase(root.x(spill), root.z(spill), root);
            // A sub-voxel spill lip can have no whole water voxel, but it must never
            // turn into a separately bordered river column inside the lake component.
            assertTrue(column.water() == TerrainColumns.Water.LAKE || column.water() == TerrainColumns.Water.NONE);
            assertFalse("Outlet banks must be hidden beneath the source lake surface", column.channelBed());
            found = true;
            break;
        }
        assertTrue("Natural profiled lake outlet fixture", found);
    }

    @Test
    public void lakeVoxelShorelineContainsVanillaSourceWater() {
        var columns = new TerrainColumns(42);
        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        LakeFixture fixture = findShallowLake(columns, root);
        assertNotNull("Fine lake containment fixture", fixture);
        var lake = fixture.sample.lake();
        int fringe = 0, level = (int) Math.floor(lake.surface());
        long margin = root.step;
        for (long z = lake.minZ() - margin; z <= lake.maxZ() + margin; z++) {
            for (long x = lake.minX() - margin; x <= lake.maxX() + margin; x++) {
                var mask = root.fluvial()
                    .lakeMaskAt(x, z);
                if (mask == null || mask.lake()
                    .id() != lake.id()) continue;
                var raw = columns.terrain.sample(x, z);
                double bed = root.elevation(x, z, raw.elevation(), columns.terrain.seaLevel());
                if (columns.refinement.lakeAt(root, x, z, bed) != null || !positiveFineNeighbor(columns, x, z))
                    continue;
                var column = columns.sample(x, z, root);
                if (column.water() == TerrainColumns.Water.NONE) {
                    assertTrue(
                        "Dry lake fringe is lower than source-water stage at " + x + "," + z,
                        column.groundY() >= level);
                } else {
                    assertTrue(
                        "Wet lake fringe drops below source-water stage at " + x + "," + z,
                        column.waterY() >= level);
                }
                fringe++;
            }
        }
        assertTrue("Natural lake fixture has no sampled zero-contour fringe", fringe > 0);
    }

    @Test
    public void subVoxelLakeStageDoesNotExcavateAWholeShoreBlock() {
        assertFalse(TerrainColumns.hasOpenWaterVoxel(70, 70.999));
        assertTrue(TerrainColumns.hasOpenWaterVoxel(69, 70.001));
        assertFalse(TerrainColumns.hasOpenWaterVoxel(70, 70));
    }

    @Test
    public void dischargeControlsFiniteMonotoneHydraulicsAndHardBanksStayNarrower() {
        int step = 128;
        assertEquals(8, FluvialNetwork.CHANNEL_WIDTH_MULTIPLIER, 0);
        long smallFlux = FluvialNetwork.minimumChannelFlux(step);
        var small = FluvialNetwork.hydraulicProfile(smallFlux, step, .004, .4, 2);
        var large = FluvialNetwork.hydraulicProfile(Math.multiplyExact(smallFlux, 16), step, .004, .4, 2);
        assertTrue(small.bankfullWidth() >= .75 * 8);
        assertTrue(small.currentWidth() >= .35 * 8);
        assertTrue(large.meanDischarge() > small.meanDischarge());
        assertTrue(large.bankfullDischarge() > small.bankfullDischarge());
        assertTrue(large.bankfullWidth() >= small.bankfullWidth());
        assertTrue(large.bankfullDepth() >= small.bankfullDepth());
        assertTrue(large.currentWidth() >= small.currentWidth());
        assertTrue(large.currentDepth() >= small.currentDepth());
        assertTrue(large.bankfullVelocity() >= small.bankfullVelocity());
        assertTrue(large.currentVelocity() >= small.currentVelocity());
        assertTrue(Double.isFinite(large.bankfullVelocity()) && Double.isFinite(large.currentVelocity()));

        assertTrue(MassWasting.stableAngleDegrees(1) > MassWasting.stableAngleDegrees(0));
        assertTrue(
            TerrainColumns.bankRunout(large.bankfullDepth(), 1) < TerrainColumns.bankRunout(large.bankfullDepth(), 0));
        assertTrue(TerrainColumns.bankOuterRadius(large, 1) < TerrainColumns.bankOuterRadius(large, 0));
    }

    @Test
    public void generatedCurrentHasAnAdjacentSolidCompoundBank() {
        var columns = new TerrainColumns(42);
        var root = columns.hydrology.root(columns.terrain.continent(-1, -1));
        boolean found = false;
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int p = 0; p < root.size() && !found; p++) {
            if (root.profile(p) == null || root.lakeDepth(p) > 0 || root.originalBed(p) <= 63000) continue;
            long originX = root.x(p), originZ = root.z(p);
            var origin = columns.sample(originX, originZ, root);
            if (origin.water() != TerrainColumns.Water.RIVER) continue;
            for (int[] direction : directions) {
                TerrainColumns.Column previous = origin;
                for (int distance = 1; distance <= 64; distance++) {
                    long x = originX + (long) direction[0] * distance;
                    long z = originZ + (long) direction[1] * distance;
                    var candidate = columns.sample(x, z, root);
                    if (candidate.water() == TerrainColumns.Water.RIVER) {
                        previous = candidate;
                        continue;
                    }
                    if (previous.water() != TerrainColumns.Water.RIVER || candidate.water() != TerrainColumns.Water.NONE
                        || candidate.flux() == 0) {
                        break;
                    }
                    var localChannel = root.channelAt(x, z);
                    assertNotNull(localChannel);
                    int localLevel = Math.max(1, Math.min(255, (int) Math.floor(localChannel.waterSurface())));
                    assertTrue(candidate.groundY() >= localLevel);
                    assertTrue(candidate.groundY() >= previous.waterY());

                    byte[] blocks = new byte[16 * 16 * TerrainColumns.HEIGHT];
                    ChunkRaster.fillColumn(blocks, 0, 0, candidate);
                    byte containment = blocks[ChunkRaster.index(0, previous.waterY(), 0)];
                    assertNotEquals(ChunkRaster.AIR, containment);
                    assertNotEquals(ChunkRaster.WATER, containment);
                    found = true;
                    break;
                }
                if (found) break;
            }
        }
        assertTrue("Natural wet-current/dry-bank transition fixture", found);
    }

    @Test
    public void controlledBraidedThreadsHaveASolidDryBarAndRunout() {
        var left = TerrainColumns.realizeChannelSection(105, braidedChannel(-1, 3, .25), .25);
        var right = TerrainColumns.realizeChannelSection(105, braidedChannel(1, 3, .25), .25);
        var bar = TerrainColumns.realizeChannelSection(105, braidedChannel(-1, 0, 2.25), .25);
        var runout = TerrainColumns.realizeChannelSection(105, braidedChannel(1, 10, 5), .25);

        assertEquals(TerrainColumns.Water.RIVER, left.water());
        assertEquals(TerrainColumns.Water.RIVER, right.water());
        assertTrue(left.currentVoxel());
        assertTrue(right.currentVoxel());
        assertEquals(2, braidedChannel(-1, 3, .25).threadCount());
        assertEquals(-1, braidedChannel(-1, 3, .25).thread());
        assertEquals(1, braidedChannel(1, 3, .25).thread());

        assertEquals(TerrainColumns.Water.NONE, bar.water());
        assertEquals(TerrainColumns.Water.NONE, runout.water());
        assertTrue(!bar.currentVoxel());
        assertTrue(!runout.currentVoxel());
        assertTrue(bar.groundY() >= left.waterY());
        assertTrue(runout.groundY() > bar.groundY());
        assertEquals(bar, TerrainColumns.realizeChannelSection(105, braidedChannel(-1, 0, 2.25), .25));

        assertSolidAt(left.waterY(), bar);
        assertSolidAt(left.waterY(), runout);

        var lowSource = TerrainColumns.realizeChannelSection(96, braidedChannel(-1, 0, 2.25), .25);
        assertEquals("A low source surface is inundated, never raised", 96, lowSource.groundY());
        assertEquals(TerrainColumns.Water.RIVER, lowSource.water());
        assertTrue(!lowSource.currentVoxel());
    }

    @Test
    public void chunkOrderColdCachesAndNegativeCoordinatesAgree() throws Exception {
        var warm = new TerrainColumns(42);
        var root = warm.hydrology.root(warm.terrain.continent(-1, -1));
        int riverCell = -1;
        for (int p = 0; p < root.size(); p++) {
            if (root.active(p) && !root.sea(p)
                && root.originalBed(p) > 63000
                && root.flux(p) >= 16_000L * root.step * root.step
                && root.downstream(p) >= 0) {
                riverCell = p;
                break;
            }
        }
        assertTrue("River chunk fixture", riverCell >= 0);
        int riverX = (int) Math.floorDiv(root.x(riverCell), 16);
        int riverZ = (int) Math.floorDiv(root.z(riverCell), 16);
        int[][] coords = { { -1, -1 }, { 0, -1 }, { -1, 0 }, { 0, 0 }, { riverX, riverZ }, { riverX - 1, riverZ } };
        var expected = new ArrayList<ChunkRaster.Raster>();
        for (int[] c : coords) expected.add(ChunkRaster.generate(warm, c[0], c[1]));
        var order = new ArrayList<>(java.util.List.of(0, 1, 2, 3, 4, 5));
        Collections.shuffle(order, new Random(31));
        var cold = new TerrainColumns(42, 1000, 700, 1);
        for (int index : order) {
            int[] c = coords[index];
            var actual = ChunkRaster.generate(cold, c[0], c[1]);
            assertArrayEquals(
                expected.get(index)
                    .blocks(),
                actual.blocks());
            assertArrayEquals(
                expected.get(index)
                    .metadata(),
                actual.metadata());
            assertArrayEquals(
                expected.get(index)
                    .columns(),
                actual.columns());
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                var column = actual.columns()[z * 16 + x];
                assertEquals(warm.sample(c[0] * 16L + x, c[1] * 16L + z), column);
                int offset = (x * 16 + z) * 256;
                assertEquals(ChunkRaster.BEDROCK, actual.blocks()[offset]);
                assertNotEquals(ChunkRaster.AIR, actual.blocks()[offset + column.groundY()]);
                if (column.waterY() >= 0) assertEquals(ChunkRaster.WATER, actual.blocks()[offset + column.waterY()]);
                int top = Math.max(column.groundY(), column.waterY());
                if (top < 255) assertEquals(ChunkRaster.AIR, actual.blocks()[offset + top + 1]);
            }
        }
        cold.hydrology.clear();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> ChunkRaster.generate(cold, -1, -1));
            var b = pool.submit(() -> ChunkRaster.generate(cold, 0, 0));
            assertArrayEquals(
                expected.get(0)
                    .blocks(),
                a.get()
                    .blocks());
            assertArrayEquals(
                expected.get(0)
                    .metadata(),
                a.get()
                    .metadata());
            assertArrayEquals(
                expected.get(3)
                    .blocks(),
                b.get()
                    .blocks());
            assertArrayEquals(
                expected.get(3)
                    .metadata(),
                b.get()
                    .metadata());
        }
    }

    @Test
    public void zeroRainDisablesFluvialIncisionAndInlandWater() {
        var dry = new TerrainColumns(42, 0, 700, 1);
        var root = dry.hydrology.root(dry.terrain.continent(-1, -1));
        int land = 0;
        for (int p = 0; p < root.size(); p++) {
            if (!root.active(p) || root.sea(p) || root.originalBed(p) <= 63000) continue;
            assertEquals(0, root.flux(p));
            assertEquals(null, root.profile(p));
            if (land++ % 100 != 0) continue;
            var c = dry.sample(root.x(p), root.z(p));
            assertEquals((int) Math.floor(root.bed(p) / 1000.0), c.erodedY());
            assertEquals(c.erodedY(), c.groundY());
            assertEquals(TerrainColumns.Water.NONE, c.water());
            assertEquals(0, c.flux());
            assertEquals(0, c.meanDischarge(), 0);
            assertEquals(0, c.currentVelocity(), 0);
        }
        assertTrue(land > 0);
    }

    private static LakeFixture findShallowLake(TerrainColumns columns, ContinentalHydrology.Root root) {
        int width = root.bounds.width();
        for (int p = 0; p < root.size(); p++) {
            var lake = root.fluvial()
                .lakeForCell(p);
            if (lake == null || lake.flux() <= 0) continue;
            int px = p % width, pz = p / width;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                int qx = px + dx, qz = pz + dz;
                if (qx < 0 || qz < 0 || qx >= width || qz >= root.bounds.height()) continue;
                int q = qz * width + qx;
                var other = root.fluvial()
                    .lakeForCell(q);
                if (other != null && other.id() == lake.id()) continue;
                for (int part = 1; part < 32; part++) {
                    double t = part / 32.0;
                    long x = Math.round((1 - t) * root.x(p) + t * root.x(q));
                    long z = Math.round((1 - t) * root.z(p) + t * root.z(q));
                    var raw = columns.terrain.sample(x, z);
                    double eroded = root.elevation(x, z, raw.elevation(), columns.terrain.seaLevel());
                    var sample = root.lakeAt(x, z, eroded);
                    if (sample == null || sample.lake()
                        .id() != lake.id() || sample.depth() <= 0 || sample.depth() >= 1) continue;
                    var column = columns.sample(x, z);
                    if (column.water() != TerrainColumns.Water.LAKE) continue;
                    long nodeX = root.x(p), nodeZ = root.z(p);
                    var nodeRaw = columns.terrain.sample(nodeX, nodeZ);
                    double nodeBed = root.elevation(nodeX, nodeZ, nodeRaw.elevation(), columns.terrain.seaLevel());
                    var nodeSample = root.lakeAt(nodeX, nodeZ, nodeBed);
                    if (nodeSample != null && nodeSample.lake()
                        .id() == lake.id()) {
                        return new LakeFixture(x, z, sample, nodeSample, column);
                    }
                }
            }
        }
        return null;
    }

    private static boolean positiveFineNeighbor(TerrainColumns columns, long x, long z) {
        for (int[] direction : new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) {
            long nx = x + direction[0], nz = z + direction[1];
            var root = columns.rootAt(columns.hydrology.snap(nx), columns.hydrology.snap(nz));
            if (columns.sampleBase(nx, nz, root)
                .water() == TerrainColumns.Water.LAKE) return true;
        }
        return false;
    }

    private static FluvialNetwork.Channel braidedChannel(int thread, double centerDistance, double threadDistance) {
        var profile = new FluvialNetwork.Profile(
            1,
            1,
            12,
            16,
            3,
            .25,
            4,
            1,
            .25,
            .002,
            .035,
            3,
            FluvialNetwork.Planform.BRAIDED,
            2,
            100);
        return new FluvialNetwork.Channel(
            0,
            1,
            .5,
            thread,
            centerDistance,
            threadDistance,
            0,
            0,
            0,
            0,
            1,
            0,
            98,
            97,
            100,
            profile);
    }

    private static void assertSolidAt(int y, TerrainColumns.ChannelSection section) {
        var column = new TerrainColumns.Column(105, 105, section.groundY(), section.waterY(), section.water());
        byte[] blocks = new byte[16 * 16 * TerrainColumns.HEIGHT];
        ChunkRaster.fillColumn(blocks, 0, 0, column);
        byte block = blocks[ChunkRaster.index(0, y, 0)];
        assertNotEquals(ChunkRaster.AIR, block);
        assertNotEquals(ChunkRaster.WATER, block);
    }

    private static int assertContainedFace(TerrainColumns.Column first, TerrainColumns.Column second) {
        if (first.water() == TerrainColumns.Water.NONE && second.water() != TerrainColumns.Water.NONE) {
            assertTrue(first.groundY() >= second.waterY());
        }
        if (second.water() == TerrainColumns.Water.NONE && first.water() != TerrainColumns.Water.NONE) {
            assertTrue(second.groundY() >= first.waterY());
        }
        return first.water() != TerrainColumns.Water.NONE || second.water() != TerrainColumns.Water.NONE ? 1 : 0;
    }

    private record LakeFixture(long x, long z, FluvialNetwork.LakeSample sample, FluvialNetwork.LakeSample nodeSample,
        TerrainColumns.Column column) {}
}
