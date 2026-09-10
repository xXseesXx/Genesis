# Complete-continent rainfall and overflow experiment

2026-09-10. `continental-hydrology-v1`, layered on unchanged `tectonic-terrain-v3` heights. Implements the user's request to replace viewer-sized hydrology windows with continent-dependent solving and an expandable rainfall input. Based on the existing [finite reference](../docs/Finite-Hydrology.md) and [active hydrology experiments](paired-drainage-experiment.md); no new external algorithm or physical-climate claim is made.

## What defines a solve

The cache/work unit is a **complete irregular continental plate family**, not a fixed display rectangle, a single plate, or a drainage basin ID. It includes all of that family's sampled land and submerged interior, possibly several islands. Rivers can cross member-plate boundaries and have many independent sea mouths. The family's 1–4-plate construction bounds the work; its actual ownership shape determines active vertices. This is an experimental consequence of the existing ocean-separated geography, not permission to treat arbitrary plate boundaries as watershed divides.

The hydrological lattice has a fixed world origin and spacing `ceil(plateSpacing / 64)`, independent of display zoom. For each member lattice cell `(i,j)`, a conservative enclosing box spans `[i-3, i+4] × [j-3, j+4]` plate spacings. The family box encloses the union and rounds outward to the canonical hydrological lattice. Singles usually allocate449² vertices; multi-plate families can allocate513² at the default spacing. Only owned vertices are active: measured examples have884,7271 and18438 active vertices. Rectangle edges are inactive padding, **never outlets**. Enlarging the box and disabling the fast ownership rejection produces identical active terrain, spill levels, receivers and flux.

Completeness follows from the existing bounded partition: owner sites are searched in the query's7×7 neighborhood, and the score bounds place all possible owned points strictly inside this conservative enclosure. The optimized `mayOwn` compares each family site's score with the query's home site's score. If every family score is greater, no family member can win; ties are retained for the normal exact owner selection. The reference test samples the entire larger box without this optimization.

Near the numeric coordinate guard, an incomplete whole-family enclosure is unavailable. There is no cropped fallback and no artificial terminal at the guard. The inspector reports status0 and a null spill surface; diagnostic maps mark it pink.

## Maritime boundary contract—not a generic ocean detector

The v3 terrain already constructs a deep submerged band between different families, even with the allowed maximum relief/minimum sea level. This experiment **prescribes that band as a sea-level maritime reservoir**. Submerged active vertices adjacent to the outside of the family seed a D8 water-component flood. Only their submerged connected component becomes absorbing maritime water. Other low basins are not automatically terminals and may fill through a higher spill.

This classification belongs to the canonical coarse sampled model. It is not a proof of fine-scale seawater connectivity through every unsampled pixel, nor a reconstruction of a global ocean. A coarse edge may straddle narrow geography. Neighbouring families are not needed for incoming river discharge because their separating margin is prescribed absorbing sea, not a dry arbitrary partition. Changing the geography to allow inter-family mountain connections invalidates that independence contract and requires a different/coarser solve grouping or explicit ports.

Maritime nodes route at the configured **sea surface**, not along the submerged seabed. Rain landing directly on maritime nodes is outside the terrestrial runoff ledger. Rain over enclosed basins is included. A component without a terminal remains unresolved; no dry-edge outlet is invented.

## Fill, spill and downstream flow

1. Sample v3 bed at every owned lattice vertex and quantize to integer millimetres, preserving the land/sea sign.
2. Use `ActiveHydrology` D8 minimax priority flood from explicit maritime terminals. The filled level is the lowest maximum elevation on any sampled path to a terminal: depressions fill to their lowest escape saddle.
3. Prefer the steepest strictly descending neighbor on the filled surface. Integer lengths1000/1414 represent cardinal/diagonal edges. On flats and filled lakes retain the terminating flood predecessor, with canonical row-major ties. Every receiver has an earlier flood order; cycles cannot form.
4. Accumulate every local rainfall source in reverse order. All upstream source contributions are available because the complete continent work region was solved, even if most lies outside the viewer.

Two explicit fixtures verify basin A spilling into basin B, then to sea, and a lowered alternate saddle redirecting overflow to the other mouth. This is **eventual overflow under sustained input**, not a transient filling animation. Storage volume, evaporation, infiltration and endorheic equilibrium are not modeled. Flood surfaces can lie above the original bed; the terrain has not been carved. The renderer's blue paths are coarse graph segments, not yet valid fine-resolution riverbeds. D8 angularity and flat-area tie bias remain visible.

## Rainfall input and exact units

`RainfallField` supplies a pure coordinate-local integer value in0–10000 **model mm/year**. The default `Uniform(1000)` deliberately gives a featureless rainfall map. The interface already accepts spatial providers; a west/east500/1500 fixture verifies that heterogeneous inputs affect local and accumulated sources. A future climate model can replace this provider without changing drainage topology or the accumulation machinery, provided its identity/configuration participates in caching/versioning.

For each non-maritime active vertex, source = rainfall × step². Units are **rain-mm × model-block²/year**, not calibrated cubic metres per second. Every canonical vertex represents a sampled square of area step²; the coastline/area integral is a coarse discretization, not an exact continuous land-area measure. Presently all rainfall becomes runoff. Later losses should be explicit and separately conserved, rather than silently interpreted as rainfall.

The exact ledger is `supplied = discharged + unresolved`. Doubling rainfall doubles every source and flux exactly while keeping spill/receivers unchanged. Zero rainfall gives zero flow; the potential fill-depth field remains a topographic diagnostic, but the river/lake overlay requires positive accumulated input. Long integer source/flux/ledger values are sent as decimal strings in the inspector. The discharge color layer shows billions of these units for readability. River visibility uses a fixed source threshold equivalent to16 default-rain cells, and width grows with discharge; neither uses the visible crop's maximum.

## Viewer, performance and evidence

Open [Rivers + lakes](http://127.0.0.1:8787/tectonics.html?layer=riverMap). Six new top-bar layers: rivers/lakes, rainfall, discharge, depression fill, spill surface and drainage status. There are33 total layers and19 world parameters, including `rainfallMm`. The inspector shows the canonical sampled node, its receiver, exact flux and the complete family ledger. Terrain version remainsv3; hydrology has its own version in metadata, PNG headers, inspection, saved URLs and exported configuration.

Rendering evaluates already committed world-coordinate river segments and node fields; zoom does not recompute the graph. The nearest hydrological node chooses the family for coarse diagnostics, avoiding mismatched ownership at sub-grid family borders. Lake fill is displayed only below its coarse spill surface with positive inflow. Existing height contours remain display-only. Actual nonzero-river crop/common-point zoom tests pass, not merely water-only samples.

Each immutable root uses finite O(N) arrays; the priority flood is O(N log N). A context keeps at most12 roots, and the server keeps2 configuration contexts keyed by seed, all plate/terrain settings and rainfall. A render temporarily pins its needed roots, with a preflight cap of24 families /8 million support vertices. Larger views must zoom in; no partially solved continent is displayed. These are research resource bounds, not a Minecraft chunk-latency guarantee. Example cold builds took roughly0.2–0.4s locally, including geometry/sampling/solve; timings vary.

Gates cover three seed42 complete families plus additional seeds, independent source-by-source downstream walks, exact ledgers, no cycles/teleports, larger support without fast rejection, cache eviction/clear/concurrency, dry/doubled/heterogeneous rainfall, missing-terminal and alternate-spill fixtures, numeric guards, API limits, exact large-integer inspection and actual river crop/zoom. The standalone suite retains its separate independent minimax reference tests. Pinned hydro fingerprint: `9d986dab09b1e116a9d6d011e19276d172f1fb1050fd07f66860b3c9718ef5d7`. Report: `build/continental-hydrology.json`. Code-generated `tectonic-rivers.png` and `tectonic-rivers-detail.png` were inspected; browser-layout/gesture QA is not claimed.

## Next milestones

- Preserve the committed coarse spills, sea mouths and runoff totals while refining river corridors and lake shorelines into a finer shared surface; remove D8 visual angularity without moving hydrological commitments.
- Make channel beds physically downhill and test cross-divide leaks against actual finer terrain. Cosmetic smoothing alone is insufficient.
- Introduce explicit basin/lake spill objects and, later, storage/loss balances before modeling seasonal or closed-basin water.
- Expand the rainfall provider into climate/runoff maps when ready. Keep source units, area ownership and loss ledgers explicit.
- Retain terminal-completeness and refinement work in the active roadmap; this is a useful continent-scale prototype, not completed infinite-world hydrology or Minecraft integration.
