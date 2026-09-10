# M1 tectonics model

Implemented in the standalone Java core. No Minecraft integration or height generation is involved.

## Fields and identities

| Field | Meaning |
| --- | --- |
| `plateId` | Exact signed 64-bit packed `(cellX, cellZ)` key |
| `subPlateId` | Exact child lattice key; full identity is `(plateId, subPlateId)` |
| `velocityX`, `velocityZ` | Integer components of synthetic plate motion |
| `boundaryType` | Nearest boundary classification: −1 divergent, 0 transform, +1 convergent |
| `boundaryDistance` | Distance to the closest Voronoi cell face, quantized in 1/256-block units |
| `uplift` | Smooth negative divergence of interpolated plate velocity; positive compression, negative extension |
| `crustType` | 0 oceanic or 1 continental, sampled once per plate |
| `crustAge` | Synthetic age sampled once per plate, in model Ma |

Long identities use `fields.get()` or exact `fields.values()` bulk queries. The old double-valued `tile()`/`scalar()` APIs reject Long fields. The inspector serializes them as decimal strings, so JavaScript cannot silently round them. Map colors hash the exact key only for display. Sub-plate colors include their parent key.

Child Voronoi tessellations are seeded by the parent and clipped by querying the parent ownership first. Refining the child spacing cannot move the parent's ownership or uplift. Sub-plates currently inherit parent motion and crust properties; they are a structural subdivision, not a separate dynamic simulation.

## Bounded integer topology

Each lattice cell contains one site in its central half. Position jitter uses integer hashes and floor-correct negative coordinates. Spacing is bounded to 8192–1048576 blocks (expanded for the v2 experimental viewer; production defaults are unchanged). With at most eight subdivisions, child keys remain within signed 32-bit component bounds over the supported ±2^40-block domain, including the search halo.

An owning site is within the query cell's 3×3 neighborhood: the home site's distance is at most approximately `sqrt(2) * 0.75 * spacing`, while an omitted site is at least approximately `1.25 * spacing` away. Integer squared distances select the nearest site. Equal distances use a hashed integer ordering with an exact-key fallback.

The distance to a Voronoi face is computed from the squared-distance difference to a competing site divided by twice the separation between sites. All candidate faces in a 7×7 query-cell neighborhood are considered. This is deliberately larger than the nearest-site search. Excluded sites are at least approximately `3.25 * spacing` from the query, putting their bisectors farther away than the maximum radius of an owning cell. The integer-coordinate rounding margins are small relative to the minimum spacing.

Distances use `Q=256` fixed point and an integer square root. Denominator rounding and final truncation make this a fixed-point approximation to geometric distance; the larger-window reference gate currently allows <0.02 block absolute error. Equal quantized distances use the same integer site ordering. Nearest-boundary association can change at interior bisectors and triple junctions; this is expected and does not change plate ownership.

At the expanded largest spacing, the largest face numerator and scaled squared separation stay below 3×10^18, below signed-long overflow. Absolute world positions are subtracted before squaring. The tests compare the bounded query against an 11×11 geometric reference, including extreme world coordinates, maximum spacing, and synthetic regular-grid edges.

Boundary classification uses dot and cross products of integer relative velocity with the site separation vector. Reversing both plate order and the normal leaves classification unchanged. The transform threshold compares absolute normal and shear components using integer percentages; no floating-point comparison determines plate ownership or class.

The topology's 64-cell LRU stores only immutable site/denominator neighborhoods. It is scoped to one immutable seed/parameter configuration and synchronized. Eviction and concurrent queries are tested against fresh generators. It is memoization, not generated-world state.

## Continuous uplift

Selecting the nearest boundary's uplift strength directly would introduce discontinuities where different boundaries become nearest. Instead, plate velocities are interpolated with compact weights:

`w = max(0, 1 - distance² / radius²)³`

Uplift is the negative analytic divergence of the normalized velocity field, scaled by spacing, configured speed, and `upliftStrength`. The radius is 1.1–2 times spacing. The lower bound ensures a nonzero total weight everywhere under the jitter bound; the upper bound keeps all supported sites inside the 7×7 neighborhood. The compact kernel and its first two derivatives vanish at its support edge. All nonzero contributors are summed in the same absolute lattice order across query-cell boundaries.

This gives a continuous compression/extension signal driven by plate motion. It is not a physical uplift rate, mountain height, or geological-history simulation. The current gradient regression gate samples a bound of `64 / spacing` per block at default radius/strength, across seeds, spacing limits, and lattice seams. The observed bound is not presented as a proof across every possible parameter configuration; extreme configurations are also checked for finite outputs.

Crust probability does not calibrate land fraction, and synthetic crust age is not a spreading-age reconstruction. Oceans, elevation, and drainage are M2/M3 work. No ocean connectivity is inferred from the crust category alone.

## Viewer and verification

All 11 layers are in a horizontally scrollable top bar above the maps. “View” selects one field; checkboxes and opacity combine fields in bar order. Boundary type is last so its two-pixel display strokes can overlay the other fields. Stroke width depends on sample spacing for readability and is not a hydrology corridor commitment. The viewer starts at a wider planetary extent for plate inspection.

Parameter sliders retain numeric inputs for precise values. IDs and categorical classes have appropriate legends instead of the former noise-only legend. A/B compares full independent seeds with optional parameter overrides and keeps the viewport synchronized.

Run `.\genesis.ps1 test`. It retains the 16 M0 reference images and adds 72 M1 reference images, exact identity checks, larger-window geometry comparisons, refinement stability, symmetry, continuity sampling, memo eviction/concurrency, HTTP precision checks, and timings for all current fields. The render budget now measures the uplift layer over a 131072-block-wide viewport. Browser automation is unavailable in this session; HTTP and PNGs are verified, but interactive browser visual QA remains outstanding.

Mathematical/infrastructure literals in this implementation are explicitly allowlisted by LINT: lattice neighborhood dimensions, integer key bit layout, fixed-point scale, hash domain, percentage conversion, memo capacity, and the derivative coefficient 6. Tuning values live in `Params`. `PlateTopology.java` additionally rejects floating types and floating square roots in the source guard. This is a targeted guard, not a whole-program proof.
