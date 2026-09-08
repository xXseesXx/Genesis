# Viewport-independent coarse runoff

`coarse-runoff-v1`, introduced in `genesis-m3a-v5`, adds exact runoff accounting to the **existing resolved world graph** in both legacy and continental models. It is independent of the finite hydrology laboratory and receives no image dimensions, crop boundaries, or zoom level.

This is not a solution for global ocean connectivity, unresolved continental interiors, natural basin organization, or physical riverbeds. Existing terrain, coast, routing and port fields are unchanged. The world version records three new fields.

## Source and transfer contract

Each resolved land **anchor** supplies one integer unit of rain. A sea anchor supplies zero; it retains the sum discharged into it. Each land anchor has one outgoing edge. Its outgoing transfer is its own rain plus incoming transfers, counted once each. There are no losses, evaporation or storage in this accounting stage.

This is uniform rainfall on the coarse graph, not rainfall per land block: cells straddling a coast still use their anchor classification. Units are not cubic metres per second or physical catchment area. Climate-weighted rainfall requires a later, versioned ownership contract.

An unresolved anchor returns `-1`, not zero rainfall and not a new terminal. Resolved catchments touching unknown neighbors are marked open, with that flag propagated downstream. Their reported units exclude the unknown rainfall. The totals are exact for the resolved graph, not asserted complete for a future graph with different routing.

| Field | Meaning |
| --- | --- |
| `coarseRunoff` | Sum of unit rain over resolved upstream land anchors, including self; sea anchors retain discharge; `-1` unresolved |
| `coarseRunoffStatus` | `-1` unresolved, `0` closed under current routing, `1` upstream region touches unresolved routing or the numeric support limit |
| `coarseChannelFlow` | Transfer on the nearest canonical guide within the existing distance cap; `0` absent; equal-distance ties take the larger transfer |

The status is deliberately conservative: adjacency to an unknown anchor does not claim that it will eventually enter this catchment. “Closed” here neither classifies ocean connectivity nor proves that a terminal is a physical river mouth. At the numerical coordinate limit, missing neighbors mark the catchment open; the limit never becomes a runoff outlet. The inward-anchor convention for non-dividing spacing matches existing coarse fields.

## Why a point query has bounded upstream work

The committed graph guarantees that an outgoing edge decreases integer rank by **exactly one**, and that resolved ranks are between zero and `R = seaSearchRadius`. Reverse traversal therefore increases rank by exactly one and terminates after at most `R - r` edges from a query of rank `r`.

Every upstream anchor lies in the Manhattan diamond of radius `R - r`. Its area is `1 + 2(R-r)(R-r+1)`. Single-outgoing-edge ownership prevents duplicate ancestry: the upstream dependency graph of one node is a tree. Recursion depth is at most `R + 1`; the largest configured diamond contains 2,113 anchors at `R = 32`.

`CoarseRunoff` consumes registered rank/direction fields and rejects inconsistent edges. It accumulates with checked integers and stores at most 4,096 immutable results in a per-instance synchronized LRU. A cold maximal-diamond fixture needs exactly 10,565 rank reads (five per visited anchor); no upstream data comes from a rendered tile or finite fill operation. Reading ranks itself still invokes the existing bounded coast search and its caches. This is a bound for **this partial graph**, not a complexity proof for the eventual multilevel cascade.

Changing the search radius extends resolution without changing previously resolved routes. Tests check that existing runoff cannot decrease, and that closed catchments keep identical totals. This is not permission to increase the radius without limit as a substitute for a world hierarchy.

## Geometry and viewer

The new layers are in the top bar of both field viewers. In `/continents.html`, check **Flow-weighted river guides** alongside continental terrain. **Resolved upstream runoff** shows accumulated rain; **Catchment completeness** uses teal for closed, amber for open, and pink for unresolved anchors.

At a shared port, both guide halves use the outgoing **source** cell's transfer, not the destination's confluence total. At a hub, the outgoing segment includes local rain and all tributaries. Nearest-segment distance uses the same integer geometry as existing guides, including the fixed halo and distance cap. Display color and stroke width scale with flow; display width is not a physical channel-width field. Fine guides may alias in distant overview samples, but common world-coordinate values never depend on zoom.

Routes still follow nearest-sea coarse ranks rather than a basin hierarchy or terrain slope. The code-generated `build/gallery/continental-runoff.png` shows seed -1 at x=-131072, z=-122880, step=256 in all four panels. It exposes partial interiors and the current excessive parallel coastal routing. Rivers are not carved and may cross uphill relief; displaying flow does not fix that.

## Verification

- Independent **source-forward** accumulation of the same world graph, compared to reverse accumulation in both models and three seeds. The reference reads a halo large enough to include every possible ancestor; its outer edge is never an outlet.
- Arbitrary-window ownership: `local rain + incoming = outgoing + sea discharge + unresolved local rain`. Boundary transfers are counted once. Unknown rain remains explicitly on the ledger.
- Maximal upstream diamonds at radii 1, 12, 32 with instrumented dependency counts; zero-rain water, numeric cutoffs, invalid non-descending inputs, and search-radius extension.
- Shared-port and both-side flow checks, including confluences where using destination flow would overcount tributaries.
- 5,000 shuffled concurrent points, bounded-cache eviction, odd spacing 1025, nonstandard continent scale 16385, extreme coordinates/seeds, plus the existing all-field cold crop/common-point zoom suite.
- HTTP new-layer images/inspection match direct core output. Optional viewer-logic smoke tests exercise all three top-bar View buttons against live HTTP.
- All 160 previous PNG baselines and continental/refinement baseline hashes remain unchanged. Timings now include 512-square runoff and terrain-plus-flow rendering.

## Next hydrology work

Use these exact ledgers to test explicit multilevel basin/parent commitments, then condition channel beds and valley terrain on those commitments. A constructive infinite-plane terminal/root policy still needs to resolve merged continental interiors without viewport outlets or unlimited upstream searches. Do not hide that missing policy behind larger local searches, ordinary noise, or a finite D8 solve. This module remains useful as an exact reference for the current partial world graph; it does not declare M3 complete.
