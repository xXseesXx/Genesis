# Climate, ground and drainage revamp

Status: implemented prototype, 2026-09-18. The coupled production path is the Minecraft-native
`TectonicTerrain -> ClimateField/TerrainSubstrate -> ContinentalHydrology -> HydraulicErosion ->
FluvialNetwork -> mc-adapter` path. The older Java 8 `Generator` geology viewer remains a separate
reference and is not silently mixed into Minecraft terrain.

## Decisions

### Wind: a continuous flow, not two independent noise maps

`WindField` uses one analytic stream function:

```text
psi(x,z) = Ux*z - Uz*x + sum Ak/|k| sin(kx*x + kz*z + phase)
wind     = (d psi/dz, -d psi/dx)
```

The mixed derivatives cancel, so `div(wind) = 0`: the procedural field has no sources or sinks.
The prevailing vector has magnitude 1 and the three curl-mode amplitudes sum to 0.40, hence the
triangle inequality gives a strict speed floor of 0.60. That extra bound matters because a
divergence-free field alone may still contain stagnation points. Integer wave vectors and modular
world coordinates make joins continuous at negative coordinates and keep trigonometric arguments
well conditioned far from the origin. The field is global per seed, never rehashed per continent
or viewport.

This follows the streamfunction/curl construction used by Bridson, Houriham and Nordenstam for
fast exactly incompressible procedural flow [1]. Genesis uses only broad stationary modes: it is a
climate driver, not a claim to be an atmospheric fluid simulation.

### Humidity and mountain rainfall

`ClimateField` performs 24 fixed semi-Lagrangian moisture passes over the already bounded canonical
continent support. Inactive maritime reserve and connected sea nodes replenish humidity. Land
traces two climate cells upwind per pass. Condensation increases with the positive directional
terrain rise; a small land-recycling term prevents completely dry numerical interiors. The final
rain map is a bounded function of transported humidity and windward lift. This produces broad wet
windward slopes and lee-side rain shadows at `O(24N)` time and `O(N)` temporary memory.

The physical inspiration is the Smith-Barstad vertically integrated orographic model [2]:

```text
u . grad(qc) = S - qc/tau_c
u . grad(qs) = qc/tau_c - qs/tau_f
P            = qs/tau_f
S            includes max(0, u . grad(h))
```

The current bounded implementation collapses cloud conversion/fallout into one stable
condensation loss instead of pretending to solve full cloud microphysics. Rain is computed from
the pre-incision relief, breaking the climate/erosion recursion. Humidity and rain are cached as
compact 16-bit root arrays; wind is recomputed analytically on demand.

### Rock, soil, infiltration and runoff

`TerrainSubstrate` supplies five broad near-surface lithologies: basalt, granite, shale, limestone
and sandstone. Seeded folded contacts and terrain elevation choose the exposed unit. Each rock has
independent resistance, permeability and weatherability; hardness is deliberately not used as a
synonym for low permeability.

Weatherability, humidity and local final slope produce 0.15--7.5 blocks of soil/regolith. The
model turns permeability, soil storage, saturation and slope into three exact permille shares:
surface runoff, infiltration and evapotranspiration. They sum to 1000 at every land node. Only
surface runoff enters the D8 river accumulator, so permeable/deep ground has fewer and smaller
channels while shallow shale or steep saturated ground responds quickly. NRCS hydrologic soil
groups likewise classify shallow restrictive or slowly infiltrating soils as higher-runoff ground
[3]. The present annual partition is intentionally cheaper than event Green-Ampt infiltration;
the USACE reference describes that future event-scale extension [4].

Root diagnostics retain rainfall, humidity, runoff/infiltration/ET shares, drainage class, soil
depth, parent rock and geologic rockhead. Stream incision uses the same rock resistance. Soil is
re-evaluated on the final post-mass-wasting slope before Minecraft columns are emitted.

### Required ordering and lakes

The implemented ordering is:

1. tectonic surface and immutable lithology;
2. global source-free wind;
3. bounded humidity transport and orographic precipitation on pre-incision relief;
4. ground-dependent rain partition into runoff/infiltration/ET;
5. repeated stream-power incision and rerouting;
6. conservative hardness-dependent mass wasting;
7. final routing, depression filling, lake components and spill levels;
8. curved channel cross-sections, lake backwater and voxel ground composition;
9. Minecraft water, sediment, soil horizons and parent-rock blocks.

Coarse lake surfaces already came from the final post-erosion route. The visible defect was later:
the adapter tested lake wetness against terrain *before* applying its analytic channel thalweg.
The revised realization computes the bounded channel ground first and tests the lake against that
post-carve bed. Flat lake nodes no longer receive fake minimum-slope Manning profiles. An inlet
reach blends its current surface upward to the exact receiving-lake surface, eliminating the dry
or lower-water trench at a mouth. Lake components use face connectivity, and the fine shoreline
uses a component indicator zero contour rather than arbitrary depth-dependent negative weights.
At Minecraft resolution, a fractional fringe is not excavated solely to manufacture a whole water
voxel. Because vanilla source water nevertheless occupies a full voxel, the first dry block column
outside the signed zero contour is filled up to the integer lake stage where it borders the positive
mask. This bounded depositional fringe contains cardinal fluid updates without rasterizing or
flooding the whole continent at block resolution.

The shoreline remains a bounded coarse-to-fine reconstruction rather than a cached block-resolution
flood of an entire continent. With the new 32-block hydrology step it is four times finer in each
horizontal direction than the previous 128-block default. A future adaptive shoreline bitset can
refine only straddling cells if in-game fluid-update testing reveals leakage.

### Fourfold horizontal shrink

The Minecraft-native plate spacing changes from 8192 to 2048 blocks while vertical height and sea
level stay at 256 and Y63. Every dependent horizontal scale (plate geometry, terrain detail,
hydrology step, river corridor and wind modes) derives from plate spacing. The canonical root still
has the same bounded grid dimensions, so one continent remains `O(N)` with essentially unchanged
asymptotic cost. There are sixteen times as many continent supports per equal explored world area;
LRU pressure during very fast exploration is therefore the main scale-related performance risk.

## Minecraft 1.7.10 representation

Vanilla 1.7.10 has no granite/diorite/andesite stone variants. The adapter uses deterministic
block-plus-metadata fallbacks rather than referencing newer blocks:

- grass/dirt or podzol-like surface for humid developed soil;
- sand/dirt/clay subsoil for well/moderate/poor drainage;
- black stained hardened clay for basalt, stone for granite, gray stained hardened clay for shale,
  white stained hardened clay for limestone, and sandstone for sandstone parent rock;
- sand/red sand for dry permeable sandstone ground;
- clay for poorly drained soil and quiet lake sediment;
- gravel/cobblestone for alluvium, talus and energetic channels;
- unbreakable `Blocks.bedrock` only at the world bottom, never at the shallow geologic rockhead;
- stationary `Blocks.water` metadata 0 for generated lake and river water.

These are gameplay-safe visual proxies, not a promise that vanilla block hardness reproduces every
modeled material coefficient.

## Performance and acceptance contracts

The gate suite checks:

- numerical divergence near zero, a strict nonzero wind-speed bound, continuity, signed coordinates
  and cold determinism;
- windward rain greater than lee rain over a controlled ridge, bounded humidity, spatially varying
  integrated rain and a zero-rain counterfactual;
- greater runoff from impermeable shale than permeable limestone, thinner soil on steep slopes, and
  exact per-node runoff/infiltration/ET shares;
- exact conversion from local effective-runoff millimetres to the integer root water ledger;
- final face-connected level lake components, no hydraulic profiles on flat lake nodes, and exact
  lake stage at profiled river mouths;
- unchanged bounded root dimensions after the fourfold X/Z shrink;
- chunk order/cache invariance and vanilla block-array contracts in the adapter.
- a natural lake's sampled zero-contour fringe has no lower dry cardinal neighbor, and wet columns
  contain no underground air gap between parent rock and their sediment cap.

The fixed-work bounds are 24 climate sweeps, six incision passes, at most four gravity passes and
fixed 5-by-5/16-chord channel queries. The default root keeps its prior vertex dimensions after the
scale reduction. Compact climate/substrate state adds about 11 retained bytes per support vertex,
raising the estimate to about 80.5 bytes/vertex; the planned 4-million-vertex viewer guard is
322,000,000 retained-array bytes. Temporary climate arrays, raster/map storage and JVM overhead are
additional. The fourfold smaller plate spacing creates 16 times as many possible roots per equal
explored area, so current cold-root/warm-chunk/cache-churn measurements remain an acceptance task.

## Deliberate limits / next steps

- no temperature, latitude, season, snowpack or vegetation feedback yet;
- no explicit cloud-water/falling-hydrometeor reservoirs or atmospheric mass ledger;
- infiltration is an annual partition, not Green-Ampt storms; infiltrated water is not yet delayed
  back into rivers as Darcy groundwater/baseflow [5];
- limestone does not yet create a separate karst conduit graph;
- soil horizons are compact depth classes, not pedogenesis through simulated time;
- lake shorelines need a real GTNH fluid-update soak test before being called leak-proof;
- vanilla still water encodes stage but not physical force/velocity on entities.

## Sources

1. Bridson, Houriham & Nordenstam (2007), *Curl-Noise for Procedural Fluid Flow*.
   <https://doi.org/10.1145/1276377.1276435>
2. Smith & Barstad (2004), *A Linear Theory of Orographic Precipitation*.
   <https://doi.org/10.1175/1520-0469(2004)061%3C1377:ALTOOP%3E2.0.CO;2>
3. USDA NRCS, *National Engineering Handbook, Chapter 7: Hydrologic Soil Groups*.
   <https://directives.nrcs.usda.gov/sites/default/files2/1712930597/11905.pdf>
4. USACE HEC-RAS, *Green-Ampt infiltration* technical reference.
   <https://www.hec.usace.army.mil/confluence/rasdocs/ras1dtechref/latest/overview-of-optional-capabilities/modeling-precipitation-and-infiltration/green-ampt>
5. USGS, *Ground-Water Hydraulics* (Darcy-flow reference).
   <https://water.usgs.gov/ogw/pubs/TWRI3-B2/TWRI3-B2-with-links.pdf>
6. Beven & Kirkby (1979), *A physically based, variable contributing area model of basin
   hydrology (TOPMODEL)*. <https://doi.org/10.1080/02626667909491834>
