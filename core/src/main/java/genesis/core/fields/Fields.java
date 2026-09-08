package genesis.core.fields;

public final class Fields {
    private Fields() {}
    public static final FieldId<Integer> CONTINENT_SCAFFOLD = new FieldId<Integer>("continentScaffold", "Continental scaffold (candidate)", "signed macro-object score; not elevation", Integer.class, -1000, 1000);
    public static final FieldId<Integer> CONTINENT_SEA_MASK = new FieldId<Integer>("continentSeaMask", "Continental land / sea (candidate)", "0 land / 1 sea; independent of current drainage", Integer.class, 0, 1);
    public static final FieldId<Double> NOISE = new FieldId<Double>("noise", "Reference fBm", "normalized", Double.class, -1, 1);
    public static final FieldId<Double> RIDGES = new FieldId<Double>("ridges", "Noise ridges (diagnostic)", "normalized", Double.class, 0, 1);
    public static final FieldId<Long> PLATE_ID = new FieldId<Long>("plateId", "Plates", "lattice key", Long.class, 0, 1);
    public static final FieldId<Long> SUB_PLATE_ID = new FieldId<Long>("subPlateId", "Sub-plates", "child key within parent", Long.class, 0, 1);
    public static final FieldId<Integer> VELOCITY_X = new FieldId<Integer>("velocityX", "Velocity X", "model units", Integer.class, -128, 128);
    public static final FieldId<Integer> VELOCITY_Z = new FieldId<Integer>("velocityZ", "Velocity Z", "model units", Integer.class, -128, 128);
    public static final FieldId<Integer> BOUNDARY_TYPE = new FieldId<Integer>("boundaryType", "Boundary type", "-1 divergent / 0 transform / 1 convergent", Integer.class, -1, 1);
    public static final FieldId<Double> BOUNDARY_DISTANCE = new FieldId<Double>("boundaryDistance", "Boundary distance", "blocks (1/256 precision)", Double.class, 0, 8192);
    public static final FieldId<Double> UPLIFT = new FieldId<Double>("uplift", "Uplift / subsidence", "compression signal", Double.class, -2, 2);
    public static final FieldId<Integer> CRUST_TYPE = new FieldId<Integer>("crustType", "Crust type", "0 oceanic / 1 continental", Integer.class, 0, 1);
    public static final FieldId<Integer> CRUST_AGE = new FieldId<Integer>("crustAge", "Crust age", "synthetic Ma", Integer.class, 0, 4500);
    public static final FieldId<Integer> CRUST_FRACTION = new FieldId<Integer>("crustFraction", "Continental crust blend", "permille continental support", Integer.class, 0, 1000);
    public static final FieldId<Double> CONTINENTALITY = new FieldId<Double>("continentality", "Continentality", "signed coarse score", Double.class, -1, 1);
    public static final FieldId<Double> BASE_ELEVATION = new FieldId<Double>("baseElevation", "Macro elevation", "model meters; sea level 0", Double.class, -4000, 4000);
    public static final FieldId<Integer> SEA_MASK = new FieldId<Integer>("seaMask", "Land / sea", "0 land / 1 sea; connectivity not classified", Integer.class, 0, 1);
    public static final FieldId<Integer> SEA_DISTANCE = new FieldId<Integer>("coarseSeaDistance", "Coarse sea distance", "anchor Manhattan blocks; -1 unresolved", Integer.class, 0, 65536);
    public static final FieldId<Integer> DRAINAGE_RANK = new FieldId<Integer>("coarseDrainageRank", "Coarse drainage rank", "anchor steps; -1 unresolved", Integer.class, 0, 32);
    public static final FieldId<Integer> FLOW_DIRECTION = new FieldId<Integer>("coarseFlowDirection", "Coarse flow direction", "-1 unresolved / 0 sea / 1 N / 2 E / 3 S / 4 W", Integer.class, -1, 4);
    public static final FieldId<Integer> CHANNEL_DISTANCE = new FieldId<Integer>("coarseChannelDistance", "Coarse route guides", "blocks to guide; capped at coarseSpacing/4; not carved rivers", Integer.class, 0, 4096);
    public static final FieldId<Integer> PORT_DISTANCE = new FieldId<Integer>("drainagePortDistance", "Shared drainage ports", "blocks to active crossing; capped at coarseSpacing/4", Integer.class, 0, 4096);
}
