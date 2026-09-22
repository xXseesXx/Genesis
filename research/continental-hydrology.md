# Complete-continent climate, drainage and overflow experiment

2026-09-10, updated 2026-09-22. `continental-hydrology-v4` is layered on Minecraft-native `tectonic-terrain-v8`. It solves a complete bounded continental family, couples the production path to `terrain-climate-v1` and `terrain-substrate-v1`, composes optional hydraulic incision and conservative slope relaxation, then builds `fluvial-network-v4` from the final terrain. The original no-erosion/uniform-rain constructors remain for compatibility fixtures; they are not the viewer or adapter defaults.

The detailed climate/material choices are recorded in [climate, ground and drainage](climate-ground-hydrology.md), the morphology target and implementation checklist in [the fluvial hydrology revamp](fluvial-hydrology-revamp.md), and incision mechanics in [the erosion contract](continental-erosion.md).

The fine maritime realization bilinearly interpolates the explicit connected-sea nodes. It replaces
the adapter's old nearest-node decision, which could leave below-sea air in a chunk straddling a
continental coast. Elevation must still be at or below sea level, so the continuous mask does not
turn enclosed above-sea terrain into ocean.

## What defines a solve

The cache/work unit is a **complete irregular continental plate family**, not a display rectangle, single plate or drainage basin ID. It includes all of that family's sampled land and submerged interior, possibly several islands. Rivers can cross member-plate boundaries and have many sea mouths. The 1–4-plate family bounds the work; actual ownership determines active vertices. This is a consequence of the current ocean-separated geography, not permission to treat arbitrary plate boundaries as watershed divides.

The hydrological lattice has a fixed world origin and spacing `ceil(plateSpacing / 64)`, independent of crop and zoom. With the v8 default plate spacing 2,048, step is 32 blocks; it was 128 at the prior 8,192 preset. Each member contributes the same normalized support enclosure, so a default single still allocates about 449² vertices and a multi-plate family can allocate about 513². The fourfold X/Z shrink therefore does not multiply work inside one root. It can expose 16 times as many roots over an equal explored world area, making LRU churn during fast travel a distinct risk.

For each member lattice cell `(i,j)`, a conservative enclosing box spans `[i-3, i+4] × [j-3, j+4]` plate spacings. The family box encloses their union and rounds outward to the canonical lattice. Rectangle edges are inactive padding, **never outlets**. A wider reference that disables the ownership shortcut must reproduce active terrain, spill levels, receivers and flux. Near the numeric coordinate guard, an incomplete whole-family enclosure is unavailable; no cropped fallback or artificial edge terminal is invented.

## Maritime boundary contract—not a generic ocean detector

The terrain retains a deep submerged band between families under all allowed settings. This experiment prescribes that band as a sea-level maritime reservoir. Submerged active vertices touching the outside of the family seed a D8 flood; only their submerged connected component becomes absorbing maritime water. Interior low basins are not automatically sea and may overflow a higher saddle.

This classification belongs to the canonical sampled model. It is not a proof of fine-scale global ocean connectivity. Changing geography to connect land between families invalidates the independence contract and requires a different root grouping or explicit ports. Maritime nodes route at sea surface rather than along seabed. Precipitation on maritime nodes is outside the terrestrial source ledger. A component without a terminal remains unresolved; a dry boundary is never promoted to an outlet.

## Coupled build ordering

1. Sample raw v8 terrain and immutable near-surface lithology on every owned node; quantize height to integer millimetres while preserving the land/sea sign.
2. Classify the connected maritime component.
3. Evaluate global analytic wind and run 24 fixed semi-Lagrangian humidity passes over the canonical support. Maritime/inactive cells replenish moisture; directional terrain rise enhances windward rain and creates a lee shadow. Climate sees pre-incision relief, avoiding a final-field recursion.
4. Convert rock permeability/weatherability, humidity and local slope into soil depth plus exact runoff/infiltration/evapotranspiration shares summing to 1,000 permille. Only surface runoff becomes a routing source.
5. Run six bounded implicit stream-power incision/reroute passes, then up to four conservative, early-exit mass-wasting passes.
6. Recompute final soil depth on the relaxed slope and solve the final D8 priority flood/routing graph.
7. Derive area/order, face-connected lake components and bounded channel geometry. Rasterization composes channel carving before lake wetness; an inlet water profile approaches the receiving lake stage.

The priority flood gives each resolved node the lowest sampled maximum elevation on a path to a terminal. Outside flats, routing prefers the steepest strictly descending filled-surface neighbor using integer cardinal/diagonal lengths 1000/1414. On flats and in filled depressions it retains the terminating flood predecessor with canonical row-major ties. Every receiver has an earlier flood order, so cycles cannot form.

## Rain, ground partition and exact units

`ClimateField` produces 0–10,000 model millimetres/year and a 0–1 humidity fraction. `TerrainSubstrate` provides basalt, granite, shale, limestone and sandstone with separate resistance, permeability and weatherability. Its annual land-node partition is exact:

`runoffPermille + infiltrationPermille + evapotranspirationPermille = 1000`.

For each non-maritime node, effective surface millimetres are the rounded product of precipitation and runoff share; source is then `effectiveMm × step²`. The exact routing ledger remains in **runoff-mm × model-block²/year** and closes as `supplied = discharged + unresolved`. Infiltration and evapotranspiration are explicit source-side losses from precipitation, not hidden multipliers in the downstream graph. Infiltrated water is not stored or returned as baseflow in v4.

`FluvialNetwork.meanDischarge` converts accumulated effective-runoff flux to cubic metres per second under the adapter's declared size-1 convention that one horizontal block is one metre. This makes mean discharge unit-consistent, but the annual partition and the fixed 12-times-mean bankfull proxy are uncalibrated Genesis models, not observed flood-frequency predictions. Compatibility constructors that receive an arbitrary `RainfallField` without `TerrainSubstrate` retain runoff share 1,000 for old controlled tests.

## Lakes, mouths and fine queries

Lake components are same-level, **face-connected** final depressions with stable ID, surface, spill/outlet and flux metadata. Fill elevation remains a routing/water surface; it never replaces the solid bed. A visible open lake still represents eventual overflow under sustained input. There is no hypsometry, storage change, evaporation balance or closed-basin equilibrium.

The coarse lake solve already follows incision and mass wasting. The adapter defect occurred later: lake open volume was tested before the analytic channel cut. Columns now realize the bounded channel ground first and query lake wetness against that composed bed, so a deeply cut inlet can fill to lake stage. Lake cells no longer receive a fake minimum-slope Manning profile, and a profiled incoming reach blends to the exact receiving-lake surface. The fine shoreline uses the zero contour of a component-membership indicator; it does not force a fractional fringe down merely to manufacture one water voxel. At the Minecraft voxel stage, the first dry cardinal fringe outside that contour is instead filled to the integer lake stage. This narrow deterministic bank contains whole-block vanilla source-water updates without changing the canonical lake component or running a block-resolution continent flood.

Point channel work is fixed: at most a 5-by-5 coarse segment neighborhood and 16 chords per centerline/thread candidate. This preserves crop/chunk/order independence without scanning every reach. It remains a bounded curved corridor over a coarse D8 commitment, not proof that every block thalweg descends or that no fine divide is crossed.

## Viewer, performance and evidence

Open [Rivers + lakes](http://localhost:8787/tectonics.html?layer=riverMap). The 46-layer viewer adds wind direction/speed, humidity, terrain-aware rainfall, soil depth, rockhead elevation/depth, rock type, infiltration, runoff fraction and drainage. Inspection reports all six subsystem versions, canonical receiver/flux/area/order, climate/wind, ground partition, hydraulic dimensions/form and lake metadata. Physical fields are world-coordinate commitments; only the coarse-zoom river visibility cue is display dependent.

Each immutable root uses finite arrays. The primitive-heap priority flood is `O(N log N)`; climate is exactly 24 `O(N)` sweeps; routing accumulations, area/order/lake derivation and each erosion/gravity pass are `O(N)` with fixed pass caps. Equal cold requests coalesce, at most four distinct roots build concurrently and a context retains at most 12 completed roots.

The coupled compact arrays add about 11 retained bytes per support vertex to the prior 69.5-byte estimate, for about **80.5 bytes/vertex**. The viewer keeps its 20-family / 4,000,000-vertex work ceiling and uses a **322,000,000-byte** retained-array guard; temporary climate solve arrays, raster/map allocations and JVM overhead are additional. The default 384 view remains below the ceiling. These are hard work/memory bounds, not portable latency guarantees. The current full-suite climate root fixture completed in roughly 0.47 seconds on the development machine; a pinned warm-chunk/memory benchmark remains open.

Gates cover full-support topology and independent source walks, exact ledgers, climate windward/lee and zero-rain fixtures, wind divergence/speed/continuity, material runoff/soil counterfactuals, final face-connected level lakes, no lake-cell hydraulic profiles, exact inlet lake stage, wider support, cache clearing/coalescing/concurrency, numeric guards and unchanged root dimensions after the scale reduction. Adapter gates additionally cover exact-sea water, fractional lake shores, a sealed natural-lake zero contour, post-channel lake filling, no underground air beneath wet sediment, compound-bank containment, braided bars, material palettes, order-independent chunks and dry worlds.

## Explicit limits and next work

- Prove fine thalweg descent, cross-divide exclusion, confluence/mouth continuity and adjacent-chunk behavior against the rasterized bed.
- Add lake depression hierarchy/hypsometry, explicit dry/closed/open storage states and evaporation/loss balance without destabilizing IDs/spills.
- Calibrate annual effective runoff and replace the fixed bankfull multiplier with an event provider and independent ledger.
- Add groundwater storage/baseflow, event infiltration and an explicit karst conduit policy; none exists in v4.
- Add temperature, latitude, seasons, snowpack and vegetation feedback to the present stationary humidity/rain model.
- Add sediment mobility, bars/floodplains/deltas, valley-constrained bends and toe-erosion/pore-pressure/cohesion mechanics.
- Run a new-world GTNH client/server fluid-update and save-version soak. Generated water remains static vanilla metadata-0 water; computed velocity is diagnostic.
