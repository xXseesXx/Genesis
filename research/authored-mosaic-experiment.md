# R2b: adjoining-domain and scale audit

Date: 2026-09-08. Executable experiment: `authored-mosaic-v1`. **Decision: do not adopt the current single-scale, one-landmass-per-maritime-domain layout as production continents.** Retain it as a complete finite input for drainage experiments.

## What was tested

[AuthoredMosaicGates.java](../harness/gates/src/genesis/harness/AuthoredMosaicGates.java) assembles samples from [complete authored land roots](authored-landmass-experiment.md). Each world sample first resolves its canonical owner, then reads that owner's fully constructed 193-by-193 mesh. A test-local map avoids rebuilding the same root within one audit; it is not a production cache or a new field API.

Twelve configurations: seeds 42, -1 and 137, spacing S65536 or S524288, target land coverage 65 or 90 percent of each root's reachable safe area, collar S/32. Each display samples a 257-by-257 vertex grid over [-S,3S] on each axis, step S/64. Thus all images cover four domain spacings, while physical extent changes with S. Different S means a different world recipe, **not viewer zoom**, a mixed-scale world, or an exact rescaling of site coordinates; modulo-based site jitter also changes with S.

The display crop never becomes a growth boundary or a water seed. Every encountered owner, including water-only owners, is built in full: 28-30 roots per configuration. Bounds, alignment and numeric-support guards remain the root's own contract.

Checks include:

- No land in the maritime reserve; every sample reads its own owner, never another root's unmodeled neighbor placeholder.
- No D8-adjacent land pair has different owners. This also excludes D4 cross-domain land paths. The collar certificate explains the restriction: the safe square around a land vertex contains any mesh neighbor, so that neighbor cannot safely belong to a different owner.
- Every assembled D4/D8 land component has one owner. Independent display flood fills count components, largest size, and boundary contact. Synthetic diagonal and boundary fixtures validate these counters.
- Increasing safe-area coverage preserves every previously selected land vertex across the assembled mosaic.
- A cold reconstruction queried through an interior crop, two strides, shuffled coordinates and outer-corner samples agrees with the full display. Two parallel cold reconstructions with opposite read orders also agree. These checks preserve shared canonical samples; they do not assert sub-grid coastline interpolation.

## Measurements and visual result

Reports: `build/authored-mosaic.json` and inspected code-rendered `build/gallery/authored-mosaic.png`. The main image shows S524288, three seeds, 65/90-percent rows.

| S | Target safe-area coverage | Sampled land fraction, three seeds | Sampled D4 components | Largest sampled D4 component |
| --- | --- | --- | --- | --- |
| 65536 | 65% | 54.77-56.34% | 23-28 | 2622-2919 |
| 65536 | 90% | 76.10-77.73% | 22-31 | 3632-3906 |
| 524288 | 65% | 55.37-56.19% | 23-28 | 2798-2973 |
| 524288 | 90% | 76.96-77.56% | 25-28 | 3795-4117 |

All twelve sampled D8 component counts equal their D4 counts. The largest component occupies only about 4.0-6.2% of the 66,049 sampled vertices. Counts are finite-window measurements, not world-area estimates or global continent statistics. Many components touch the crop; one connected root can appear split when its connecting path lies outside the display. The report records edge-touching and complete components separately, plus min/max land counts from complete roots. Growth can therefore increase the number of *visible* components without fragmenting a full root.

Unconnected interior water remains present: 1-140 sampled vertices per configuration. Those counts are inherited from complete-root reserve-path classification, not a flood seeded at the display edge. They are not a count of equilibrium lakes, and they need not decrease monotonically as land grows.

An initial standalone run took approximately 0.48-0.66 seconds per full audit, including root construction, sample assembly and component counting but excluding rendering and the later cold/concurrency checks. This is observational timing, not a latency gate. The root map retains O(KN) data with K encountered roots and N=37,249 vertices per root. Finite test reuse says nothing about production chunk-query cache limits.

The image exposes a near-cellular ocean network, especially at high coverage. Increasing S changes the physical size, but not the rule that land cannot join across root ownership. High coverage brings shorelines closer to the domain boundary and makes that signature stronger. This is a structural consequence, not a crop seam or generation-order bug.

## Architectural decision

Do not keep tuning coverage or noise under the assumption that they can remove the restriction. The exact fixture is unsuitable as the general production continent layout. This does **not** reject every possible bounded continental root or maritime construction; it rejects promoting this particular layout merely because it has bounded computation and connected land.

Separate the future roles:

1. **Continental geography:** large-scale connected land and ocean structure, with an explicitly reviewed distribution of extents and shapes.
2. **Hydrological roots:** complete upstream commitments, potentially separated by actual inland divides rather than ocean. Their bounds must follow a construction contract, not an arbitrary display or chunk rectangle.
3. **Computational partitions:** internal storage/refinement regions can cross land and rivers. They require inherited ports and exact external inflow summaries, not fabricated coastlines.

Larger/variable continental domains and constructive inland watershed hierarchies remain alternatives to compare. Neither is implemented here. Removing the reserve without replacing its completeness certificate would simply restore the missing-upstream-context problem.

## Next executable step: explicit active-domain drainage

Stop morphology tuning on this fixture. Use it provisionally to test a bounded routing solver with the following contract:

- Active samples are the requested owner's interior plus explicit reserve-connected water. Another owner's non-reserve interior is **excluded**, not assigned a very high elevation: a high value remains traversable in a fill solver and is not a wall.
- A caller supplies integer bed elevations and nonnegative net-runoff contributions. Initial elevation/forcing fixtures must be labeled synthetic rather than tectonic or climate results.
- Only certified ocean-connected water samples are terminals, with an explicit water-surface elevation. Outer solve-box edges do not become terminals, and growth parents are never used as river receivers.
- Interior unconnected water is not a terminal. First test an explicit open-overflow/depression-fill policy: route to a genuine spill when reachable, retain fill height and depression volume as diagnostics, and label components without a reachable terminal unresolved. Filling is an eventual-overflow routing model, **not** a transient lake water balance or proof that rainfall sustains a lake.
- Use a documented neighbor model consistently. Test inactive barriers, dry edges, enclosed components, known saddle heights, flat/tie routing and multiple ocean mouths. Physical filled elevation must be nonincreasing downstream, with a separate acyclic tie order for flats; strict bed descent awaits terrain realization.
- Independently compare minimax spill levels and source-forward mass totals. Resolved ocean discharge plus unresolved contributions must equal total input; no invented loss, storage or boundary inflow.
- Measure full cold solve work. Sample only after the complete canonical graph is committed; cropped displays must read the same graph.

The existing `FiniteHydrology` traverses every raster cell and assumes one runoff unit per non-sea cell. It cannot yet express excluded neighbor interiors or general source arrays. Do not pass an unmodeled rectangle to it and call the result complete root hydrology. The next implementation should add a separate explicit-active-graph experiment or a carefully tested overload without changing existing viewer semantics. R2 and R3 remain incomplete.
