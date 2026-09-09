# R2c/R3 building block: active drainage and shared crest saddles

Date: 2026-09-09. Implemented finite D4 graph experiment, **not production hydrology, a terrain mesh, or completed R3**.

## Solver contract

`ActiveHydrology` is a separate Java21 oracle. It does not change `FiniteHydrology` or the existing hydrology viewer. Inputs are a complete finite raster, integer node bed heights, an explicit active mask, absorbing terminal flags, nonnegative long net-runoff contributions, and integer undirected east/south edge crest heights. A crest represents the highest intervening elevation along that graph connection; Integer.MIN_VALUE introduces no additional barrier beyond endpoint heights. Inactive vertices have no edges and cannot carry runoff or terminals. Dry raster edges are never automatic terminals.

The solver minimizes the maximum bed/crest elevation along a path to a supplied terminal. It uses Dijkstra relaxation with deterministic level/index priority and strict-improvement updates. Edge saddles require relaxation: marking a node final when first discovered, as in a simple vertex-only priority flood, would fail when a lower saddle path is found later. An explicit four-node detour tests this distinction.

Receivers lead to previously finalized nodes. Filled elevation is nonincreasing downstream, and each crossed crest is no higher than the source node's filled level. This describes **eventual depression overflow**, not strict bed descent, a transient lake simulation, or storage/loss modeling. The exact routing ledger is supplied = discharged + unresolved; it assumes no ongoing storage change or losses. Source units have no calibrated time or physical discharge unit yet. `fillDepthSum` sums node fill depths, not a reconstructed lake volume or crest geometry.

The immutable result exposes indexed reads but no mutable arrays. Unresolved heights cannot be read as solved values. Checked long arithmetic rejects input-total overflow; int-extreme height differences are accumulated as longs. The finite grid cap is 1,048,576 vertices. Bounded-degree Dijkstra takes O(N log N) work and O(N) storage; cold construction remains necessary before indexed reads.

## Independent tests

`ActiveHydrologyGates` compares 300 randomized small grids with an independent repeated-relaxation minimax reference. The cases vary active masks, terminals, bed heights, crests, flats and heterogeneous runoff. Independent per-source receiver walks verify all accumulated fluxes and terminal identities. Additional tests cover a cheaper alternative saddle path, excluded barriers, D4 diagonal isolation, unresolved sources, extreme depths/flux, malformed inputs, overflow, caller-array mutation after solving and concurrent cold solves.

This is explicitly D4. Rejecting a diagonal move in a D4 graph is **not** proof that a finer D8 or continuous terrain has no diagonal shortcut.

## Paired-cell experiment

`PairedDrainageGates` builds three real cell pairs from [the mixed ridge/ocean prototype](mixed-boundary-experiment.md), seeds42/-1/137, S524288, collar16384, 53% total-owned-area land. Each union rectangle contains both complete 193-square cell boxes; actual owned vertices are strictly inside its outer edge. Other cells are excluded. Terminals are the two cells' reserve-connected water samples, all at level zero. Interior unconnected water is not a terminal.

Synthetic land beds are 40 + a hashed integer in [0,60] + ridgeStrength/8, bounded by 228. Interior water beds are -12. Land contributes 1-9 synthetic net-runoff units per vertex. These are fixture inputs, **not geology, climate or erosion results**. Every active nonterminal cross-owner edge has a finite shared crest of 600. The combined solve may cross those edges; it has no owner-based routing prohibition.

Compare that unrestricted union against two restricted local solves with exactly the same shared terminal geometry and crest inputs. Each local solve excludes the other cell's nonterminal interior and sources; ocean terminal samples from the pair remain shared boundary data. The test verifies identical nonterminal filled levels and receivers, and union flux equal to the sum of the local fluxes at every vertex, including terminals. Thus the result establishes independence of the interiors **conditional on the shared boundary data**, not a claim that a cold world query needs no neighboring boundary information.

All local filled levels remain below 600, so no minimal overflow route needs the shared crest. This is a numeric barrier in the terrain graph, not an infinite wall or an owner filter. It can be interpreted as an intervening ridge peak, but an actual continuous/triangulated crest surface is not constructed here.

| Seed | Pair | Union vertices | Shared crest edges | Supplied = discharged | Unresolved | Node fill-depth sum |
| --- | --- | --- | --- | --- | --- | --- |
| 42 | (-1,-1), (-1,-2) | 49601 | 48 | 22005 | 0 | 30998 |
| -1 | (0,-1), (1,-1) | 49601 | 71 | 22673 | 0 | 32185 |
| 137 | (0,-1), (0,0) | 49601 | 75 | 21464 | 0 | 33951 |

An analytic 9-by-5 counterexample supplies a basin whose westward spill is 80 and whose dividing crest is 120. Lower one pass to 30: the unrestricted solve spills across that pass at 30, while an isolated-cell solve incorrectly retains level80. The gate requires this disagreement and actual transferred runoff. It demonstrates why a cell mask cannot validate the physical divide that supposedly justifies it.

Artifacts: `build/paired-drainage.json` and inspected code-rendered `build/gallery/paired-drainage.png`. Orange is a schematic crest-edge indicator; blue runoff pixels are not calibrated river widths. Initial measured union solves took about 1.9-4.1 ms, **excluding** paired mask/bed construction, validation, local solves and rendering. These are observations, not chunk-latency gates. Cold repeat solves and reversed strided result reads agree.

## Geological direction and next work

The user's geological intuition is useful, with important distinctions. Continental collision can thicken crust and build mountain belts; oceanic subduction combines a descending slab, a trench and often an adjacent volcanic arc. Continental extension can create fault-bounded rift basins, while oceanic spreading creates mid-ocean ridges, sometimes with axial rift valleys. Transform motion is primarily sideways. These are different processes, not one signed uplift scalar. [USGS: understanding plate motions](https://pubs.usgs.gov/gip/dynamic/understanding.html), [USGS: tectonic processes](https://www.usgs.gov/centers/whcmsc/science/tectonic-processes).

Genesis design implication: reference relief should eventually depend on relative plate motion, crust type, boundary side and geological history. Rifts are structural basins, while river canyons also require drainage and erosion. A geological boundary is not automatically a hydrological divide; some rivers follow or cross fault/rift zones. This checkpoint adds no new tectonic fields and does not interpret the synthetic 600-unit crest as simulated plate collision.

Next: realize shared crests in a common finer mesh, specify diagonal connectivity and independent terrain-based routing, and test for both same-height saddles and lowered-pass leaks. Then vary crest profiles/forcing toward geologically informed relief while preserving explicit outlet and water-budget contracts. General multi-cell continent groups, erosion, lake storage, terrain-preserving refinement and integrated acceptance remain open. Production version, viewer fields and old goldens are unchanged.
