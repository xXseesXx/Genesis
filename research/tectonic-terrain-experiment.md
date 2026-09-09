# Tectonic absolute terrain and correlated continental crust

Date: 2026-09-09. Versions: `tectonic-terrain-v1`, `crust-provinces-v1`. Java21 reference candidate for a separate tectonic viewer. Not production terrain, certified ocean, infinite-world hydrology or a geological time simulation.

## What changed and why

The first elevation recipe interpolated existing independent binary plate-crust labels and combined them with the shared junction forcing. Its six 32-plate-wide samples averaged 54.73% land, but the inspected image was sponge-like: many plate-sized water pockets inside a large connected land network. Reaching a percentage and having a large connected component are not enough to establish continental morphology.

The next candidate introduces **static crust provinces at four times plate spacing**. Each existing plate inherits the nearest province's crust type at its site. Both broad elevation and all pairwise motion profiles consume that SAME candidate crust assignment. Existing plate identities, positions, vectors and synthetic ages remain unchanged. This deliberately replaces the production model's independent crust labels in the candidate; never overlay the old crust/regime fields and label them as the candidate's geology.

Provinces are correlated input attributes, not moving plates, one-continent-per-cell reservations, watersheds, hydrological roots or certified ocean domains. Adjacent continental provinces can join; no province boundary is forced to sea. Individual plates still have one crust type in this version. Plates containing mixed continental/oceanic crust, geological histories and multiscale province distributions remain future work.

`crustProvinceScale=1` retains the independent-crust screening baseline. Scale4 is the candidate default. These are the only supported settings in v1, not an implied continuously variable physical parameter.

## Complete reference recipe

`TectonicTerrain` takes a seed, immutable core `Params` for the plate inputs, and immutable reference `Settings`. Production Params defaults and both existing world models are unchanged. The candidate plate preset sets spacing S=65536 and `continentalPercent=53`; its other plate attributes use existing defaults. With correlated mode this percentage is the probability of continental **province** labels, not an exact fraction of plates or land.

Reference Settings defaults:

| Input | Default | Meaning |
| --- | --- | --- |
| crustProvinceScale | 4 | Province spacing / plate spacing; 1 is independent-crust baseline |
| crustRadiusPermille | 1500 | Compact crust interpolation radius / S |
| seaThreshold | 500 | Continental fraction at zero base elevation, in permille |
| landHeight | 1800 | Base elevation at entirely continental support |
| oceanDepth | 4200 | Negative base elevation magnitude at entirely oceanic support |
| forcingPermille | 1000 | Gain on the already versioned junction profiles |
| detailHeight | 180 | Bounded subordinate noise amplitude |
| seaLevel | 0 | Fixed absolute datum for this world configuration |

For every query:

1. Reconstruct the same existing local plates, substituting the correlated crust labels when enabled.
2. Interpolate crust fraction f using normalized compact weights `(1-distanceSquared/radiusSquared)^3`, zero outside the radius. Sort by exact unsigned plate ID before reduction.
3. With threshold t, set c=(f-t)/t below t and c=(f-t)/(1-t) above it. Base elevation is c*oceanDepth below zero and c*landHeight above zero. This deliberately asymmetric two-endmember recipe is a model choice, not a calibrated isostatic calculation.
4. Add the existing domain-separated Noise primitive at wavelength S/8, four octaves, amplitude `detailHeight*c*c`. Noise provides subordinate interior detail and vanishes at the base coast; it does not author the continental layout. There is no erosion interpretation.
5. Add separately exposed positive and negative net pair contributions from `JunctionForcing`, computed from the candidate plates. Unit gain is exactly identity.
6. `land = elevation > seaLevel`; equality belongs to below-sea-level classification. There is no second, mismatching land mask. Motion is allowed to change the provisional coast. Raising sea level changes classification, not bed elevation.

This constructs a single absolute reference surface. Elevation values are model units, not Minecraft block Y or physically calibrated heights. Below-sea-level pockets can be enclosed; this code does not label them globally ocean, drain them through the viewport, or determine their actual water surface.

## Determinism, support and work

Province sites lie in the central half of a jittered lattice cell. A home site supplies distanceSquared <=9*provinceSpacing^2/8; a site outside the nearest 3x3 neighborhood is at least5*provinceSpacing/4 away along one axis, so cannot own the point. Exact integer distances and unsigned exact IDs break ties. A larger 7x7 reference checks this lookup. Province site construction retains a bounded internal halo for legal world queries and required plate centers beyond the public numeric guard.

Crust interpolation uses 49 candidates in a 7x7 plate neighborhood. At radius >=1.1S the home-site bound guarantees nonzero support. At radius <=2.5S all omitted sites, at least3.25S away on one axis, have zero weight. The same candidate list supplies junction forcing; its tighter 25-site support result is unchanged. Complete input support, not the rendered rectangle, determines each value.

A world instance memoizes at most64 immutable plate neighborhoods, each containing49 candidate plates. A cold neighborhood requires49 existing plate reconstructions and, in correlated mode,49 bounded nine-site province lookups. Detail has four noise octaves. No continent-component discovery, upstream traversal or precomputed global raster occurs during a query. Eviction affects cost only; cache keys are exact plate-lattice identities and the instance is scoped to its complete immutable configuration.

Java21 strict floating-point arithmetic, canonical reduction order and the shared `StrictMath` projection recipe define the reference output. Preserve explicit strict arithmetic when later porting to Java8. A versioned fingerprint covers exact outputs over540 configuration/coordinate cases; this is not an integer coastline-topology commitment for future refinement.

## Executable evidence

`TectonicTerrainGates` runs in the full suite:

- 540 terrain cases across three seeds, minimum/default/maximum plate spacing, minimum/default/maximum crust radius, negative coordinates, query-lattice seams and near all four numeric-domain corners. Crust49-site results equal121-site results exactly and match an independently reduced weight calculation within tolerance. Junction forcing uses the SAME candidate plates and agrees with its larger-support composition.
- 480 province owner queries compare9-site lookup with49-site integer reference, including extreme coordinates. Original plate identity/geometry/vector/age preservation is independently checked; crust replacement is deliberate.
- Base/detail/forcing summation and coast agreement; zero-forcing/detail counterfactuals leave crust unchanged. Higher sea level leaves elevation unchanged and cannot create land. All-oceanic/all-continental probability endpoints remain valid configurations, not land-target presets.
- Caller-order, cold reconstruction, overlapping coordinate samples and shared-instance concurrency; immutable bounded-neighborhood memoization. Versioned output fingerprint: `10dafe06b67a3c6a1dc165aff64a8d563e3df2af6cf04b5c7ae52fd14bd8141b`.
- Independent sampled D8 floods measure land/water components, crop cuts and number of plate owners within the largest land component. Synthetic diagonal and crop-edge fixtures check the metric. These are display-limited components, not globally identified continents or ocean certificates.

The six seed42/-1/137/8675309/0/Long.MIN_VALUE **wide-area** audits each use65,536 fixed, jittered strata across256S on each axis. All use the same preset, same sample protocol and fixed sea level. Resulting land percentages are **53.94, 51.44, 53.60, 52.28, 52.07 and 52.23**, mean **52.59%**. All six lie in the50-55% screening target. These finite deterministic samples do not prove exact infinite-world area fractions, arbitrary-seed coverage, or absence of sampling error. The candidate preset was not adjusted per seed or per viewport.

Separate257-square morphology images span32S at S/8 spacing. Their local land fractions range35.88-57.20%; that variation is allowed and is not corrected with local sea-level changes. Largest sampled land components span20.125-32 plate spacings and include144-635 distinct plate owners. Each crop has genuine cross-plate land connections. Most large components are cut by the crop: their global extent is unknown. Land and water may connect diagonally in these sampled metrics; no claim of continuous ocean connectivity follows.

`build/tectonic-terrain.json` records both sampling scales, output fingerprint, component statistics, coast changes and total audit timing. Timing includes both rasters and component analysis; it is not a promised viewer latency. Code-generated and inspected images:

- `build/gallery/tectonic-crust-comparison.png`: independent versus correlated crust at identical geometry, velocities and coordinates.
- `build/gallery/tectonic-terrain.png`: six regional height maps, with local land percentages explicitly labeled.
- `build/gallery/tectonic-terrain-layers.png`: same-coordinate composed elevation, broad base, signed forcing and continuous crust fraction.

## Decision and next step

Keep the independent-crust version as a negative morphology baseline. Advance the correlated candidate to a **separate experimental tectonic viewer**, not production-architecture acceptance. It now has a shared absolute surface and aligned crust/forcing/coast fields worth exploring interactively. Flat interior plateaus, straight active-boundary geometry, single-scale province correlations and provisional highland placement remain visible limitations. This is not a finished continent model merely because the wide-area percentage passes.

Next implement a dedicated page/API using this exact Java21 candidate, with top-bar fields, full seed/configuration in requests, coordinate inspector and pan/zoom. Keep old viewers and golden fields unchanged. Display candidate crust and candidate boundary regimes consistently, not legacy labels. Explicitly omit global rivers until root/terminal and drainage-preserving refinement contracts exist; do not silently reuse old river guides against new terrain. Code-generated image checks and HTTP/common-point integration tests must accompany the viewer.

Subsequent checkpoint: that [dedicated viewer/API](../docs/Tectonic-Viewer.md) is now implemented with17 candidate-only layers and HTTP/common-point/application-logic tests. Browser visual verification remains unavailable. The candidate remains exploratory; next use the common surface to improve and compare mountain belts, interiors and coast morphology.
