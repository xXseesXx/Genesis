package genesis.core;

import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;
import genesis.core.fields.Noise;
import genesis.core.tectonics.Tectonics;
import genesis.core.elevation.CoastTopology;
import genesis.core.elevation.MacroElevation;
import genesis.core.elevation.ContinentalScaffold;
import genesis.core.hydro.CoarseChannels;

/** Composition root: only here may concrete field implementations be wired together. */
public strictfp final class Generator {
    public static final String VERSION = "genesis-m3a-v4";
    public enum Model {
        LEGACY("legacy"), CONTINENTAL("continental");
        public final String id;
        Model(String id) { this.id = id; }
    }
    public final long seed;
    public final Model model;
    public final Params params;
    public final FieldRegistry fields;

    public Generator(long seed, Params params) {
        this(seed, params, Model.LEGACY);
    }
    public Generator(long seed, Params params, Model model) {
        if (model == null) throw new IllegalArgumentException("Missing world model");
        this.model = model;
        this.seed = seed; this.params = params;
        final FieldRegistry base = new FieldRegistry.Builder().add(Fields.NOISE, new Noise(seed, params)).build();
        final Tectonics tectonics = new Tectonics(seed, params);
        final FieldRegistry tectonicFields = new FieldRegistry.Builder()
            .add(Fields.NOISE, (x, z) -> base.get(Fields.NOISE, x, z))
            .add(Fields.RIDGES, (x, z) -> 1 - Math.abs(base.get(Fields.NOISE, x, z)) / params.get("amplitude"))
            .add(Fields.PLATE_ID, (x, z) -> tectonics.sample(x, z).owner.id)
            .add(Fields.SUB_PLATE_ID, tectonics::child)
            .add(Fields.VELOCITY_X, (x, z) -> tectonics.sample(x, z).owner.vx)
            .add(Fields.VELOCITY_Z, (x, z) -> tectonics.sample(x, z).owner.vz)
            .add(Fields.BOUNDARY_TYPE, (x, z) -> tectonics.sample(x, z).boundaryType)
            .add(Fields.BOUNDARY_DISTANCE, (x, z) -> tectonics.sample(x, z).distanceQ / (double) genesis.core.tectonics.PlateTopology.Q)
            .add(Fields.UPLIFT, tectonics::uplift)
            .add(Fields.CRUST_TYPE, (x, z) -> tectonics.sample(x, z).owner.crust)
            .add(Fields.CRUST_AGE, (x, z) -> tectonics.sample(x, z).owner.age)
            .add(Fields.CRUST_FRACTION, tectonics::crustFraction)
            .build();
        final ContinentalScaffold continents = new ContinentalScaffold(seed, params);
        final FieldRegistry coastInputs = new FieldRegistry.Builder().include(tectonicFields)
            .add(Fields.CONTINENT_SCAFFOLD, continents::score).build();
        final CoastTopology coast = new CoastTopology(seed, params, coastInputs,
            model == Model.CONTINENTAL ? Fields.CONTINENT_SCAFFOLD : null);
        final MacroElevation elevation = new MacroElevation(coast, params, coastInputs, model == Model.CONTINENTAL);
        final FieldRegistry coastFields = new FieldRegistry.Builder().include(coastInputs)
            .add(Fields.CONTINENTALITY, elevation::continentality)
            .add(Fields.BASE_ELEVATION, elevation::elevation)
            .add(Fields.TERRAIN_DETAIL, elevation::detail)
            .add(Fields.TECTONIC_RELIEF, elevation::tectonicRelief)
            .add(Fields.SEA_MASK, (x, z) -> coast.numerator(x, z) <= 0 ? 1 : 0)
            .add(Fields.SEA_DISTANCE, (x, z) -> { int rank = coast.at(x, z).rank; return rank < 0 ? -1 : rank * coast.spacing; })
            .add(Fields.DRAINAGE_RANK, (x, z) -> coast.at(x, z).rank)
            .add(Fields.FLOW_DIRECTION, (x, z) -> coast.at(x, z).direction)
            .build();
        final CoarseChannels channels = new CoarseChannels(seed, params, coastFields);
        this.fields = new FieldRegistry.Builder().include(coastFields)
            .add(Fields.CONTINENT_SEA_MASK, (x, z) -> model == Model.CONTINENTAL ? coastFields.get(Fields.SEA_MASK, x, z) : continents.score(x, z) <= 0 ? 1 : 0)
            .add(Fields.CHANNEL_DISTANCE, channels::channelDistance)
            .add(Fields.PORT_DISTANCE, channels::portDistance)
            .build();
    }
}
