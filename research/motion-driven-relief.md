# Motion-driven plate relief and the 30% land preset

2026-09-10, experimental `tectonic-terrain-v3`. This updates the [v2 sparse-plate construction](sparse-irregular-plates.md), not the production Java8 model. It is an implemented kinematic heuristic, not a new claim about computational geodynamics.

## Broad height and tilt

The old hash-selected datum and tilt no longer contribute to terrain. `PlateResponse` uses relative velocity projected toward nearby plate anchors. With distance measured in plate spacings S, the compact weight is `(1 - distance/2)^3` for distances below 2S. Normal motion is divided by the fixed reference speed 64, **not** the configured speed limit. Consequently faster relative motion produces more response.

The weighted mean normal motion controls plate datum. Weighted normal motion times the unit direction toward the neighbor controls the two tilt coefficients. A compressed side rises; an opening side lowers. Equal compression from opposite sides raises the mean but cancels tilt. Common translational velocity has no effect; pure tangential motion produces no broad vertical response. Existing multi-pair boundary profiles still provide localized collision/rift/subduction relief.

The response uses a canonical unsigned-ID sum over 5×5 anchor sites. Jitter is at most 0.25S per axis, so omitted sites three cells away are at least 2.5S away and outside the 2S kernel. Each plate's response is independent of query position. A synchronized 1,024-entry LRU stores immutable results only; eviction and concurrent queries cannot affect heights. Existing soft score weights join the resulting planes. Actual world-space x/z relative to each anchor determine the plane.

**Approximation:** this is a proximity stencil, not an exact graph of touching curved plates. It can include non-touching or geometrically suppressed anchor sites. Site-separation normals remain approximate on warped edges. Those are explicit next improvements, not evidence that a mechanical equilibrium, isostatic balance, rock strength or plate history has been solved. Synthetic fracture/age descriptors remain synthetic. Legacy random plane fields in the partition descriptor remain for fixture compatibility but are unused by v3 terrain.

## Flooding and continental connections

The default globally adds **−1700 model metres** to the final bed's base component, keeping sea level at zero. No runtime quantile, area count, flood-from-canvas-edge, seed-specific tuning or viewport adjustment occurs. All other vertical terms are unchanged by changing this offset. `elevationOffset` accepts −4000 through 0. The old `continentalPercent=53` is retained as a **crust-extent input**, not a promise of 53% exposed land.

An offline `TectonicCalibration` utility screens fixed stratified world points to choose a single preset. Six seeds, 16,384 points per seed over 128S, produce:

| Seed | Land % |
| --- | ---: |
| 42 | 30.0903 |
| -1 | 30.1270 |
| 137 | 29.5715 |
| 8675309 | 30.3040 |
| 0 | 29.2480 |
| -9223372036854775808 | 29.6509 |

Mean **29.83195%**, not an exact universal area theorem. Local views may differ substantially. The lower datum leaves 84.8–88.8% of well-sampled plates in the six morphology windows containing both exposed and submerged terrain; more plates are fully submerged than in v2. The mixed-plate gate is now 80%, replacing v2's 85%, explicitly reflecting the requested additional flooding.

Within each existing 1–4-plate continental family, the shared crust bridge now widens under convergence and narrows under extension. Local collision profiles can therefore remain mountainous across an ownership boundary after global lowering. An independent ownership-transition scan bisects crossings to adjacent integer coordinates: seed42 has **60 sampled convergent mountain joins**, with both sides above250 m and positive relief above100 m. One example is x4430577, z3408189, height1268.874 m. These are terrain connections, **not certified hydrological divides**.

Inter-family deep-water margins are retained. This is the conservative way to honor the prior maximum-five-plate continent request: actual land remains within at most four-member families. Arbitrarily allowing mountain bridges between families would remove that guarantee and can produce enormous connected continents. The remaining cellular ocean-network tendency is not fixed by lowering terrain; family construction/shape is still a research limitation.

## Height-map rendering

`heightMap` adds high-contrast fixed elevation colors and contour strokes. Colors/contour levels reference the configured sea datum; inspection still reports the same absolute bed elevation as `elevation`. Top-bar `contourInterval` is a **display-only** setting: 0 disables lines, 5–1000 selects requested spacing in model metres. Every fifth line is heavier.

At wide zoom, the displayed spacing doubles until it is at least `step * 8000 / S`; the UI reports this actual interval. A forward world-coordinate halo estimates local slope, making same-scale crops agree, including the last canvas row/column. Halo coordinates clamp only at the public numeric support guard; that is a one-sided display derivative, not a terrain boundary. Near-flat exact contour plateaus are not blackened; lines too dense for a steep pixel are suppressed. This is a sampled, antialiased contour approximation, not a vector isoline mesh. Height and unadorned colors stay common-point zoom invariant; line thickness/LOD deliberately do not. No claim of physical metre calibration to Minecraft is made.

## Evidence and next work

Gates cover single/opposed compression, opening, shear, speed scaling, common velocity, canonical order, compact versus larger response support, global-offset counterfactuals, cache eviction/concurrency, actual mountain joins and extreme inter-family flooding barriers. The existing288 full-field support/cold fixtures and six morphology audits remain. V3 fingerprint: `320631683b8b458d4603d86873efe8d65a07c5a643b17d247ce6c77f42c31413`.

HTTP checks cover27 layers, unadorned common-point colors, contoured crop agreement, 5 m availability, numeric-edge halos, invalid settings, and exact inspection. Node tests cover18 world parameters plus separate contour state, legend, URL/export and stale requests. Code-generated default height and mountain-detail PNGs were inspected; browser painting/gesture QA is not claimed.

Next: actual curved contact normals and contact-aware broad loading, smoother mixed-crust forcing transitions, less cellular bounded-family geography and richer mountain interiors. Then connect certified hydrological contracts; do not use plate/family IDs as drainage roots or treat all low bed as connected ocean.
