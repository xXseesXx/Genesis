# R2b: connected land authored inside a canonical domain

Date: 2026-09-08. Version: `authored-landmass-v1`. Status: finite canonical-domain experiment, **not adopted into production**. It addresses the fragmentation failure in [the root-design comparison](root-terminal-design.md), without claiming finished continental geography.

## Construction

The previous retrofits carved maritime reservations through existing objects or discarded whole objects that did not fit. This experiment instead knows the safe region before constructing land.

1. Obtain one domain's exact `3S x 3S` box from `MaritimeEnvelope`. Use a fixed world-coordinate mesh with 64 cells per spacing: 193 x 193 = 37,249 vertices per complete box, never the current image crop.
2. A vertex is eligible only if it belongs to this domain and is outside the ocean reservation. The caller supplies S, collar, domain identity, seed and a coverage percentage. Require collar >= mesh step. Neither these research controls nor the result are registered production fields.
3. Choose the eligible lattice sample nearest the canonical site, with index tie order. Generate a bounded positive growth resistance from broad integer bilinear hashed preferences in absolute lattice coordinates. This is a shape preference, **not terrain height, tectonics or erodibility**.
4. Compute positive-cost D4 shortest paths from that seed over all reachable eligible samples. Deterministic distance/index queue order commits one complete growth sequence.
5. Select the first `max(1, floor(reachableCount * percentage / 100))` samples as land. Every selected non-seed sample's predecessor was selected earlier because its distance is strictly smaller. Consequently land is connected. Increasing coverage selects a longer prefix without erasing earlier land.

This is finite precomputation followed by reads of immutable samples. The production generator does not call it. `growthParent` is explicitly a **growth witness**, not a downstream receiver: following it ends in the interior, so presenting it as a river would be wrong.

The experiment permits targets 1..90 percent of the seed-reachable safe lattice region. That percentage is not global land coverage. Eligible and reachable counts are reported separately rather than assuming a sampled convex region is always D4 connected. Each tested configuration happened to make its entire safe sample region reachable; the implementation does not rely on that.

## Ocean connection without canvas-edge sinks

Classify the remaining modeled water using D4 flood fill starting at **explicit ocean-reserve vertices**. Traversal may use this domain's water and reserved-ocean samples; another domain's non-reserve interior is unmodeled, not assumed ocean. No outer box edge is automatically a terminal or a water seed.

Codes are 0 land, 1 connected to an explicit reserve seed, 2 unconnected interior water, 3 unmodeled neighboring interior. Class 2 is a potential lake/depression requiring a future spill/storage policy, not proof of a hydrologically closed equilibrium lake. It must not disappear through an invented drain. This classification inherits the maritime model's continuous ocean-reservation premise; it is not a simulation of physical water surfaces or a proof that arbitrary separately generated neighboring terrain preserves all of it.

The complete box is required to fit inside the current numeric query guard. Near that guard construction rejects the whole request; it never clips the box and calls its cut an ocean. Resolving full root halos beyond the supported public coordinate range remains an integration task. Do not claim every supported coordinate already has a constructible production root.

## Implemented tests

[AuthoredLandmass.java](../oracle/src/genesis/oracle/AuthoredLandmass.java) is Java21 oracle/research code. [AuthoredLandmassGates.java](../harness/gates/src/genesis/harness/AuthoredLandmassGates.java) runs in the normal test suite:

```powershell
./genesis.ps1 test
# Or, after building:
java '-Djava.awt.headless=true' -cp build/classes genesis.harness.AuthoredLandmassGates
```

- Ten complete domain configurations: three seeds at 40/65 percent; negative/mixed addresses at 90 percent and a large collar; minimum/maximum spacings at 1 percent with extreme seeds.
- Independent union-find connectivity (rather than the generator's flood-fill queue) verifies one D4 land component and exact water membership in components containing reserve samples.
- Every land sample satisfies the envelope certificate, is away from the canonical box edge, and has a selected strictly earlier adjacent growth parent unless it is the seed.
- Counted land matches the precise coverage rule; 40-percent land is contained in the corresponding 65-percent result.
- Every canonical vertex matches a cold reconstruction for land, water class, distance and growth parent. Shuffled reads, coordinate queries and common points at multiple sampling strides agree.
- Three simultaneous cold constructions agree with the original result. No shared mutable cache or process-dependent random generator participates in world decisions.
- Invalid percentage, collar, coordinates, off-mesh queries and out-of-support complete roots fail closed.

These are tests of an explicit **discrete canonical mesh**. There is no fine coastline interpolation or sub-grid connectivity claim. The six pictures use crops only to display already-computed roots; crop choice does not enter construction.

## Results

Main fixtures: S524288, collar16384, mesh step8192, domain address(0,0). No original terrain is removed because this is a separate authored realization, not an overlay on old continents.

| Seed | Reachable eligible samples | Land at 40% | Land at 65% | Unconnected water at 40% / 65% |
| --- | --- | --- | --- | --- |
| 42 | 4217 | 1686 | 2741 | 0 / 0 |
| -1 | 3333 | 1333 | 2166 | 0 / 1 |
| 137 | 3601 | 1440 | 2340 | 0 / 0 |

Each land count is also the largest and only D4 land component. This demonstrates that coarse bounded domains need not be filled by disconnected remnants of rejected objects. It does not establish equivalence to the former continent silhouettes or a world-scale component distribution. The earlier clipping/rejection audit used different sample supports and should not be compared by raw sample count.

`build/authored-landmass.json` records exact counts and **full construction** timings, including envelope lookup, growth and water classification. An initial local run took approximately 22-64 ms per main fixture, subject to JVM warmup effects but excluding process startup; these are observations, not a latency gate or hardware-independent claim. The nominal mesh has a fixed 37,249-vertex cost floor even though only several thousand vertices belong to the requested safe interior.

Construction is bounded by the canonical mesh: O(N) envelope/preference evaluation, O(N log N) priority-queue growth on a bounded-degree graph, O(N) water classification and storage. Array-backed canonical sample reads are O(1) **after construction**. No cheap cold chunk-generation claim follows until root reuse, cache limits, thread contention and full downstream fields are measured.

The code-generated `build/gallery/authored-landmass.png` was inspected. It shows six connected silhouettes with visible bays/peninsulas and growth between the two coverage levels. Cyan marks the preserved unconnected interior water sample. Gray is unmodeled neighbor interior, not land. Image and JSON are uploaded with CI artifacts.

## Decision and remaining work

This is a positive **connected-land fixture** to use in upcoming drainage experiments, not permission to replace the continental viewer. It is qualitatively better suited to retaining large connected land than the two failed retrofits, but several biases remain:

- One seed and one connected landmass per domain are deliberate fixture assumptions; adopting them globally would impose a one-island-per-domain structure that has not been accepted.
- Single-scale Voronoi ownership still imposes a maximum land extent and an ocean network. Increasing coverage can make coastlines follow the domain shape more strongly.
- Broad hashed growth resistance is not geological continent formation. Its silhouettes and statistics need review over more seeds, scale variation and neighboring-domain mosaics.
- Physical elevation, multiple river basins/mouths, spill heights, conservative runoff and inherited fine terrain remain absent. A connected land silhouette is not completed hydrology.

Next: examine adjoining-domain/scale statistics and use these explicitly provisional fixtures to specify and test actual ocean-connected terminals, interior-depression treatment, and bounded coarse routing. The root architecture remains open until continental extent and topology are acceptable. Production geography must not be changed merely because the fixture's tests pass.
