package genesis.adapter.terrain;

import genesis.oracle.ClimateField;
import genesis.oracle.ContinentalHydrology;
import genesis.oracle.ContinentalHydrology.Root;
import genesis.oracle.FluvialNetwork;
import genesis.oracle.MassWasting;
import genesis.oracle.TectonicTerrain;
import genesis.oracle.TerrainSubstrate;

/** Minecraft-independent realization of the canonical eroded terrain and fluvial network. */
public final class TerrainColumns {

    public static final String VERSION = "genesis-columns-v5";
    public static final int RAINFALL = 1000;
    public static final int EROSION = 700;
    public static final int HEIGHT = 256;
    static final double MINIMUM_WET_RADIUS = .75;
    public final TectonicTerrain terrain;
    public final ContinentalHydrology hydrology;

    public enum Water {
        NONE,
        OCEAN,
        LAKE,
        RIVER
    }

    /** Quantized channel cross-section before the hydraulic diagnostics are attached. */
    record ChannelSection(int groundY, int waterY, Water water, boolean currentVoxel) {}

    /**
     * Inclusive solid and water block heights; waterY=-1 means a dry column.
     * Hydraulic dimensions use blocks/metres and discharges use cubic metres per second.
     */
    public record Column(int originalY, int erodedY, int groundY, int waterY, Water water, boolean channelBed,
        long flux, double meanDischarge, double bankfullDischarge, double bankfullWidth, double bankfullDepth,
        double bankfullVelocity, double currentWidth, double currentDepth, double currentVelocity, double downstreamX,
        double downstreamZ, int streamOrder, int thread, int threadCount, double threadWidth, double threadDischarge,
        FluvialNetwork.Planform planform, int rainfall, double humidity, double windX, double windZ, double windSpeed,
        double windDirectionRadians, TerrainSubstrate.Rock rock, double soilDepth, double bedrockDepth, int bedrockY,
        int runoffPermille, int infiltrationPermille, TerrainSubstrate.Drainage drainage) {

        public Column {
            if (water == null || planform == null
                || rock == null
                || drainage == null
                || groundY < 1
                || groundY >= HEIGHT
                || bedrockY < 1
                || bedrockY >= HEIGHT
                || waterY >= HEIGHT
                || (waterY != -1 && waterY <= groundY)
                || (water == Water.NONE) != (waterY == -1)
                || flux < 0
                || rainfall < 0
                || rainfall > 10_000
                || runoffPermille < 0
                || runoffPermille > 1000
                || infiltrationPermille < 0
                || infiltrationPermille > 1000
                || runoffPermille + infiltrationPermille > 1000
                || streamOrder < 0
                || threadCount < 0
                || threadCount > 2
                || thread < -1
                || thread > 1
                || !nonnegativeFinite(
                    meanDischarge,
                    bankfullDischarge,
                    bankfullWidth,
                    bankfullDepth,
                    bankfullVelocity,
                    currentWidth,
                    currentDepth,
                    currentVelocity,
                    threadWidth,
                    threadDischarge,
                    humidity,
                    windSpeed,
                    soilDepth,
                    bedrockDepth)
                || humidity > 1
                || soilDepth > 12
                || bedrockDepth > 12
                || !Double.isFinite(windX)
                || !Double.isFinite(windZ)
                || !Double.isFinite(windDirectionRadians)
                || !Double.isFinite(downstreamX)
                || !Double.isFinite(downstreamZ)) {
                throw new IllegalArgumentException("Invalid realized column");
            }
            double directionLength = StrictMath.hypot(downstreamX, downstreamZ);
            if (directionLength != 0 && Math.abs(directionLength - 1) > 1e-6) {
                throw new IllegalArgumentException("Downstream direction must be zero or normalized");
            }
            if (threadCount == 0 && (thread != 0 || threadWidth != 0 || threadDischarge != 0)
                || threadCount > 0 && thread == 0 && planform == FluvialNetwork.Planform.BRAIDED) {
                throw new IllegalArgumentException("Invalid channel-thread diagnostics");
            }
        }

        /** Compatibility constructor for non-hydraulic test and tooling columns. */
        public Column(int originalY, int erodedY, int groundY, int waterY, Water water) {
            this(
                originalY,
                erodedY,
                groundY,
                waterY,
                water,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                FluvialNetwork.Planform.STRAIGHT,
                0,
                0,
                0,
                0,
                0,
                0,
                TerrainSubstrate.Rock.GRANITE,
                0,
                0,
                Math.min(groundY, erodedY),
                0,
                0,
                TerrainSubstrate.Drainage.MODERATE);
        }

        private static boolean nonnegativeFinite(double... values) {
            for (double value : values) if (!Double.isFinite(value) || value < 0) return false;
            return true;
        }
    }

    public TerrainColumns(long seed) {
        this(seed, RAINFALL, EROSION, 12);
    }

    public TerrainColumns(long seed, int rainfall, int erosion, int cacheCapacity) {
        terrain = new TectonicTerrain(seed, TectonicTerrain.defaultPlateParams(), TectonicTerrain.Settings.defaults());
        var climate = new ClimateField(seed, terrain.plateParams.integer("plateSpacing"), rainfall);
        var substrate = TerrainSubstrate.seeded(terrain);
        hydrology = new ContinentalHydrology(terrain, climate, cacheCapacity, erosion, substrate);
    }

    public Column sample(long x, long z) {
        return sample(x, z, rootAt(hydrology.snap(x), hydrology.snap(z)));
    }

    /** Resolves a root from an already-snapped canonical node. Package-private for chunk batching. */
    Root rootAt(long snappedX, long snappedZ) {
        var canonical = terrain.sample(snappedX, snappedZ);
        return hydrology.root(hydrology.group(canonical));
    }

    /** Samples with the canonical root already known. Package-private for chunk batching. */
    Column sample(long x, long z, Root root) {
        Column center = sampleBase(x, z, root);
        return containWater(
            center,
            sampleBase(x - 1, z, rootAt(hydrology.snap(x - 1), hydrology.snap(z))),
            sampleBase(x + 1, z, rootAt(hydrology.snap(x + 1), hydrology.snap(z))),
            sampleBase(x, z - 1, rootAt(hydrology.snap(x), hydrology.snap(z - 1))),
            sampleBase(x, z + 1, rootAt(hydrology.snap(x), hydrology.snap(z + 1))));
    }

    /** Analytic pre-voxel column. Chunk generation evaluates this once over a one-block halo. */
    Column sampleBase(long x, long z, Root root) {
        if (root == null) throw new IllegalArgumentException("Canonical hydrology root required");
        var raw = terrain.sample(x, z);
        int p = root.index(x, z);
        double eroded = root.elevation(x, z, raw.elevation(), terrain.seaLevel());
        int erodedY = height(eroded), ground = erodedY, water = -1;
        Water kind = Water.NONE;
        boolean channelBed = false;
        long flux = 0;
        double meanDischarge = 0, bankfullDischarge = 0;
        double bankfullWidth = 0, bankfullDepth = 0, bankfullVelocity = 0;
        double currentWidth = 0, currentDepth = 0, currentVelocity = 0;
        double downstreamX = 0, downstreamZ = 0, threadWidth = 0, threadDischarge = 0;
        int streamOrder = 0, thread = 0, threadCount = 0;
        FluvialNetwork.Planform planform = FluvialNetwork.Planform.STRAIGHT;
        int rainfall = root.rainfall(p), runoffPermille = root.runoffPermille(p);
        int infiltrationPermille = root.infiltrationPermille(p);
        double humidity = root.humidity(p), soilDepth = root.soilDepth(p), bedrockDepth = soilDepth;
        int bedrockY = Math.min(erodedY, height(eroded - bedrockDepth));
        TerrainSubstrate.Rock rock = root.rock(p);
        TerrainSubstrate.Drainage drainage = root.drainage(p);
        var wind = root.wind(x, z);
        double windX = wind == null ? 0 : wind.x();
        double windZ = wind == null ? 0 : wind.z();
        double windSpeed = wind == null ? 0 : wind.speed();
        double windDirection = wind == null ? 0 : wind.directionRadians();

        // Sea membership comes from the connected maritime solve, not elevation alone.
        // At the integer sea datum, replace the top solid voxel with a water voxel.
        if (root.seaAt(x, z) && !raw.land()) {
            water = terrain.seaLevel();
            ground = Math.min(ground, Math.max(1, water - 1));
            kind = Water.OCEAN;
        } else {
            // Compose the analytic channel cut before asking whether the final open volume belongs to a lake.
            // This lets a lake back-fill an inlet thalweg without lowering a sub-voxel shoreline just to show water.
            var channel = root.channelAt(x, z);
            boolean realizedChannel = false;
            if (channel != null) {
                var profile = channel.profile();
                double hardness = root.hardness(p);
                double outerRadius = bankOuterRadius(profile, hardness);
                if (channel.centerDistance() <= outerRadius) {
                    var section = realizeChannelSection(ground, channel, hardness);
                    ground = section.groundY();
                    water = section.waterY();
                    kind = section.water();
                    channelBed = channel.insideBankfull();
                    flux = profile.flux();
                    meanDischarge = profile.meanDischarge();
                    bankfullDischarge = profile.bankfullDischarge();
                    bankfullWidth = profile.bankfullWidth();
                    bankfullDepth = profile.bankfullDepth();
                    bankfullVelocity = profile.bankfullVelocity();
                    currentWidth = profile.currentWidth();
                    currentDepth = profile.currentDepth();
                    currentVelocity = profile.currentVelocity();
                    downstreamX = channel.tangentX();
                    downstreamZ = channel.tangentZ();
                    streamOrder = profile.order();
                    thread = channel.thread();
                    threadCount = channel.threadCount();
                    threadWidth = channel.threadWidth();
                    threadDischarge = channel.threadDischarge();
                    planform = profile.planform();
                    realizedChannel = true;
                }
            }
            double composedBed = realizedChannel ? Math.min(eroded, ground) : eroded;
            var lake = root.lakeAt(x, z, composedBed);
            if (lake != null && lake.wet()
                && lake.lake()
                    .flux() > 0) {
                int level = height(lake.surface());
                if (hasOpenWaterVoxel(ground, lake.surface())) {
                    water = level;
                    kind = Water.LAKE;
                    if (!realizedChannel) {
                        flux = lake.lake()
                            .flux();
                        meanDischarge = FluvialNetwork.meanDischarge(flux);
                        bankfullDischarge = meanDischarge * FluvialNetwork.BANKFULL_MULTIPLIER;
                    }
                }
            }
        }
        if (ground < bedrockY) {
            bedrockY = ground;
            bedrockDepth = 0;
            soilDepth = 0;
        }
        return new Column(
            raw.elevation(),
            erodedY,
            ground,
            water,
            kind,
            channelBed,
            flux,
            meanDischarge,
            bankfullDischarge,
            bankfullWidth,
            bankfullDepth,
            bankfullVelocity,
            currentWidth,
            currentDepth,
            currentVelocity,
            downstreamX,
            downstreamZ,
            streamOrder,
            thread,
            threadCount,
            threadWidth,
            threadDischarge,
            planform,
            rainfall,
            humidity,
            windX,
            windZ,
            windSpeed,
            windDirection,
            rock,
            soilDepth,
            bedrockDepth,
            bedrockY,
            runoffPermille,
            infiltrationPermille,
            drainage);
    }

    /**
     * Converts analytic water boundaries to a closed Minecraft block boundary. A vanilla
     * source can only spread horizontally through a cardinal face, so every dry column next
     * to water is brought up to that neighbor's integer stage. This rule uses only immutable
     * world-coordinate samples and therefore gives the same result on either side of a chunk
     * boundary, independent of generation order.
     */
    static Column containWater(Column center, Column west, Column east, Column north, Column south) {
        if (center == null || west == null || east == null || north == null || south == null) {
            throw new IllegalArgumentException("Center and four cardinal columns required");
        }
        if (center.water() != Water.NONE) return center;
        int containmentY = Math.max(Math.max(west.waterY(), east.waterY()), Math.max(north.waterY(), south.waterY()));
        if (containmentY <= center.groundY()) return center;
        return withGround(center, containmentY);
    }

    private static Column withGround(Column value, int groundY) {
        return new Column(
            value.originalY(),
            value.erodedY(),
            groundY,
            -1,
            Water.NONE,
            value.channelBed(),
            value.flux(),
            value.meanDischarge(),
            value.bankfullDischarge(),
            value.bankfullWidth(),
            value.bankfullDepth(),
            value.bankfullVelocity(),
            value.currentWidth(),
            value.currentDepth(),
            value.currentVelocity(),
            value.downstreamX(),
            value.downstreamZ(),
            value.streamOrder(),
            value.thread(),
            value.threadCount(),
            value.threadWidth(),
            value.threadDischarge(),
            value.planform(),
            value.rainfall(),
            value.humidity(),
            value.windX(),
            value.windZ(),
            value.windSpeed(),
            value.windDirectionRadians(),
            value.rock(),
            value.soilDepth(),
            value.bedrockDepth(),
            Math.min(value.bedrockY(), groundY),
            value.runoffPermille(),
            value.infiltrationPermille(),
            value.drainage());
    }

    static double bankRunout(double verticalDepth, double hardness) {
        if (!Double.isFinite(verticalDepth) || verticalDepth < 0) {
            throw new IllegalArgumentException("Finite nonnegative bank depth required");
        }
        if (verticalDepth == 0) return 0;
        double angle = MassWasting.stableAngleDegrees(hardness);
        return verticalDepth / StrictMath.tan(StrictMath.toRadians(angle));
    }

    static double bankOuterRadius(FluvialNetwork.Profile profile, double hardness) {
        if (profile == null) throw new IllegalArgumentException("Hydraulic profile required");
        return profile.bankfullWidth() / 2 + bankRunout(profile.bankfullDepth(), hardness);
    }

    static boolean insideCurrentVoxel(FluvialNetwork.Channel channel) {
        if (channel == null) throw new IllegalArgumentException("Channel required");
        return channel.threadDistance() <= currentVoxelRadius(channel);
    }

    /**
     * Builds a compound section around the nearest current thread. Dry bars and banks start no lower than the
     * current water voxel, then rise at the local material's stable angle. A naturally lower source surface is
     * inundated instead of being raised, preserving both fluid containment and the never-raise terrain contract.
     */
    static ChannelSection realizeChannelSection(int sourceGround, FluvialNetwork.Channel channel, double hardness) {
        if (sourceGround < 1 || sourceGround >= HEIGHT || channel == null) {
            throw new IllegalArgumentException("Valid source ground and channel required");
        }
        int level = height(channel.waterSurface());
        boolean current = insideCurrentVoxel(channel);
        if (current) {
            int bed = Math.min(height(channel.bedElevation()), Math.max(1, level - 1));
            int ground = Math.min(sourceGround, bed);
            return level > ground ? new ChannelSection(ground, level, Water.RIVER, true)
                : new ChannelSection(ground, -1, Water.NONE, true);
        }

        double distanceFromCurrent = channel.threadDistance() - currentVoxelRadius(channel);
        double target = channel.waterSurface() + MassWasting.stableRise(hardness, distanceFromCurrent);
        int ground = Math.min(sourceGround, Math.max(level, height(target)));
        if (ground < level) {
            return new ChannelSection(ground, level, Water.RIVER, false);
        }
        return new ChannelSection(ground, -1, Water.NONE, false);
    }

    private static double currentVoxelRadius(FluvialNetwork.Channel channel) {
        return Math.max(channel.threadWidth() / 2, MINIMUM_WET_RADIUS);
    }

    static boolean hasOpenWaterVoxel(int groundY, double surface) {
        return groundY >= 1 && groundY < HEIGHT && Double.isFinite(surface) && height(surface) > groundY;
    }

    private static int height(double value) {
        return Math.max(1, Math.min(HEIGHT - 1, (int) Math.floor(value)));
    }
}
