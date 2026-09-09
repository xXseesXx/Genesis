# R2c: shared ridge/ocean boundaries

Date: 2026-09-09. Status: implemented Java21-only **boundary and land-connection experiment**, not production terrain or river routing. User direction: neighboring cells may join as land across mountain divides, while other boundaries connect through ocean; target 50-55% total land area.

## Construction and bounded scope

`MixedBoundaryEnvelope` reuses the jittered sites of `MaritimeEnvelope`. Each site finds its nearest other site, resolving distance ties by canonical site key. Two sites are partners only if that choice is mutual. This is a deterministic matching: a cell has at most one partner, with no traversal through an arbitrarily long dependency chain.

The shared edge ID is the sorted pair of endpoint keys. A real Voronoi face between partners has type `RIDGE`; other faces have type `OCEAN`. Calling the type function on arbitrary keys does not prove that they share a face. Mutual-nearest pairs do share one: their midpoint has a strictly empty diameter disk, leaving an open neighborhood of the perpendicular bisector where they are the two closest sites. Gates separately check that certificate against a larger site neighborhood.

Nearest-neighbor lookup examines 24 other sites in a 5-by-5 address neighborhood. An adjacent address supplies a site at distance at most sqrt(2.5)S, while an omitted address is at least 2.5S away along one axis. Thus the support suffices for the inherited middle-half jitter. Partner lookup uses two such searches. Sampling uses 25 owner candidates plus those two searches. This is bounded local work, not upstream discovery.

The **pair restriction is deliberate and provisional**. Merging only disjoint adjacent pairs leaves each maritime group as one or two bounded Voronoi cells, without ridge loops or unbounded group lookup. In the ideal continuous planar face network, erasing the shared edge of each pair merges two disk-like faces; the remaining outer face boundaries still supply a connected network. Each cell retains ocean-facing faces. This motivates the fixture; it is not an all-seed proof of the final sampled water mask, shoreline interpolation, or physical terminals.

At a query, the original nearest owner remains authoritative. Its partner is exempted from the ocean-clearance test; all other competitors retain the conservative collar test. Near the partner bisector, integer `ridgeStrength` ranges up to 1024 and describes a mandatory land strip. **Ocean wins if ridge proximity and an ocean collar overlap**, preventing a ridge cap from being drawn over an ocean junction. This scalar is not mountain elevation or a physical no-flow proof.

The main experiment uses collar S/32 and mesh step S/64. Each cell is still computed in its complete canonical 193-by-193 box. The paired interior is not imported as extra source area. Near numeric support, an incomplete full box is rejected, never clipped into an outlet. General supported-coordinate root/partner halo coverage remains open.

## Land and water realization

`MixedBoundaryLandmass` counts **all owned mesh vertices**, including ocean-reserved ones, then targets `floor(ownedCount * percent / 100)` land vertices (minimum one for the general constructor). The experiment sets percent to 53, within the user's 50-55% target. This replaces the earlier percentage of eligible land-growing area.

All own non-ocean ridge-strip samples are mandatory growth seeds. If none exist, use the safe sample nearest the site. Positive-cost, canonically ordered D4 growth fills the remaining budget. Costs are broad integer-interpolated hashed preferences, not elevation, geology, or erosion. This multi-source growth order is not a river network. Impossible budgets fail explicitly rather than deleting a ridge, filling an ocean, or silently changing the requested fraction. Feasibility for every seed/parameter combination has not been proven.

After land is committed, D4 water classification starts from explicit mixed-envelope ocean reserves. Other-owner non-reserve interiors are unmodeled; own water disconnected from reserves stays class 2. Independent connectivity checks verify the classification. A water path to the experimental reserve is not a solved lake balance. No elevations, lake spill surfaces, or discharge totals exist here.

## Evidence

`MixedBoundaryGates` runs in the normal suite and writes `build/mixed-boundary.json` and `build/gallery/mixed-boundary.png`. The code-rendered image was inspected.

- 144 nearest-neighbor cases across four spacings, three seeds including Long.MIN_VALUE, and signed/distant coordinates: 9-by-9 BigInteger distance reference, partner symmetry, sorted edge identities, empty-midpoint-disk certificates and actual midpoint ridge samples.
- Independent larger-support four-corner clearance comparisons check the ocean collar without reusing the gap formula.
- Four mosaics: seeds42/-1/137 at S524288, plus seed42 at S65536. Each reads a 257-square window [-S,3S] at step S/64 but constructs each queried cell in full. In total, 116 complete cell realizations undergo owned-area, connected-land and independent union-find water checks.
- Actual D4 cross-owner land bridges occur. Every D8 cross-owner land adjacency is restricted to a RIDGE pair. Every tested cell's land has an adjacent reserve-connected water candidate. This is terminal availability, not actual routing.
- Each sampled water component containing a reserve vertex touches the display boundary. These flags are an independent finite diagnostic, never water seeds in the generator; boundary contact alone does not certify a global ocean.
- A 50-to-55% complete-cell comparison preserves prior land. Shuffled, strided crop reads from two opposite-order parallel cold reconstructions agree with the original mosaic.
- Invalid edge identities, unresolved collars, off-mesh/out-of-box queries and out-of-support complete roots fail closed.

| Seed | S | Sampled display land | Complete-cell land | D4 ridge joins in display | Unconnected water samples |
| --- | --- | --- | --- | --- | --- |
| 42 | 524288 | 52.7533% | 52.9886% | 7 | 19 |
| -1 | 524288 | 52.5307% | 52.9865% | 7 | 26 |
| 137 | 524288 | 52.2309% | 52.9878% | 5 | 76 |
| 42 | 65536 | 51.2695% | 52.9860% | 6 | 23 |

The small difference below 53% in complete-cell accounting is integer rounding. Display crops need not have the same fraction, and local regions need not all have 50-55% land. Four samples do not establish a world-wide empirical distribution. Changing S is a different recipe, not viewer zoom.

Initial standalone full audits took about 2.5-2.7 seconds each, including construction, mosaic reads and full-root validation, but excluding later cold/concurrency checks and rendering. No production timing gate is asserted. Each full cell is O(N log N) growth plus O(N) bounded envelope evaluation/classification, N=37,249. The test-local map retains O(KN) data for K cells and is discarded with the fixture; no unbounded production cache is added.

## Decision and next step

This gives a positive executable result for the user's idea: **land can span cell ownership while the proposed hydrological boundary is a divide**. The previous rejection concerned all-ocean separation, not every bounded-cell model. But the brown strip is only a constraint to realize; calling it a mountain does not yet make independent runoff calculation correct.

The fixture still has a cellular scale and at most two cells per connected land group. Do not adopt that restriction as the continental model. Future bounded multi-cell groups or constructive divide networks need explicit junction, ocean-connectivity and lookup contracts; independent edge coin flips are not a substitute. Curved shared geometry, variable ridge heights and tectonic coupling remain absent.

Next implement explicit-active-graph drainage on paired inputs with supplied elevations and runoff, using the [active-domain contract](authored-mosaic-experiment.md#next-executable-step-explicit-active-domain-drainage). Add a physical divide test: both cells must agree on the crest, and independent drainage must not leak through saddles or diagonally cross an unsampled crest. Blocking cross-owner graph edges establishes only an imposed boundary condition, not compatibility with terrain. Lake spill levels must remain below divides assumed impermeable, or the cell must remain unresolved/change policy. Do not infer independence for climate, subsurface flow, or erosion from a surface-routing divide.

Production core/version, Params, viewer fields and old goldens are unchanged. R2/R3 remain open; Minecraft integration is deferred.
