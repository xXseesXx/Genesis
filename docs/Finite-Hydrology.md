# Finite hydrology reference (M2b groundwork)

`oracle/src/genesis/oracle/FiniteHydrology.java` is a new, independent Java 21 reference experiment. It has no production-core imports and is excluded from the Java 8 core JAR. It is **not** the missing pre-existing JavaScript oracle, a production cascade, or proof of infinite-world drainage.

## Boundary and numeric contract

- Input is a finite, complete rectangular raster, integer elevations, an explicit sea-level mask, and an explicit terminal mask. Maximum reference size is 1024 × 1024 cells. The API accepts each dimension from 2 to 256 to bound interactive cost.
- The harness samples existing `baseElevation` and `seaMask`, rounds model metres to millimetres, and preserves signs (land at least +1 mm, sea at most 0 mm). Floating elevation is an input conversion only; routing comparisons are integer-only. Existing core fields are unchanged.
- Reference v2 offers two explicit policies. `outlets=edges` declares **every edge cell an outlet**, including dry edges, preserving v1 routing. `outlets=connectedWater` closes dry edges and makes **every cell in boundary-connected water a terminal**, so routes end on entry to that component rather than crossing the sea floor. All terminals retain their sampled elevations; this is not a water-surface simulation. API requests without `outlets` retain `edges`; the viewer explicitly defaults to `connectedWater`.
- The standalone solver also accepts arbitrary explicit terminals. With none, routes/outlet/order remain -1 and discharge is zero. `status` is 0 unresolved, 1 routed, 2 terminal. The unchanged `filled` array and local-source `accumulation` in unresolved cells are not solved drainage values; the viewer colors them pink and hides misleading scalar readouts. `discharged + unresolvedLandCells == landCells` must hold; unresolved land is never silently assigned a dry-edge outlet.
- Water components use D8 adjacency, including corner contact. Class 0 is land, 1 is boundary-connected sampled water, 2 is enclosed sampled water. This is independent of the terminal mask. Enclosed water need not stay enclosed on a finer grid; boundary-connected water is not necessarily ocean.
- The full raster, origin, sample spacing, generator seed/params/version, boundary policy, and reference version define the experiment. Moving or resampling it may change every derived diagnostic. No window-dependent result is registered as a pure world field.

## Solver and tie rules

The implementation is an integer priority flood in the family described by [Barnes, Lehman and Mulla, Priority-Flood](https://arxiv.org/abs/1511.04463). It is our simple reference implementation, not a port or claim to implement their optimized queue variants.

Initialize all terminals in a heap sorted by `(filled elevation, row-major cell index)`. Pop the minimum cell and discover its unvisited D8 neighbours in fixed dz/dx order. A neighbour receives `max(original neighbour height, popped filled height)` and routes to that popped cell. Each cell is inserted once. On equal-height plateaus the row-major tie introduces orientation bias; it is reproducible, not a claim of natural river morphology.

The resulting filled value is the minimum, over paths to any terminal, of the maximum input elevation along that path. The downstream predecessor has no greater filled height and a strictly smaller removal index. This gives a terminating acyclic graph without floating epsilon gradients. Routes are a **flood tree**, not steepest-descent D8 slope selection. Original terrain can rise along a route through a filled depression; no carving has taken place.

Each original land cell contributes one runoff unit; sea-mask cells contribute zero. Traverse reverse removal order to accumulate these units downstream. Every outlet stores its exact contributing land-cell count. The sum at outlets equals the number of reachable land cells. These units are neither rainfall nor physical discharge. Sea-floor routing and dry-edge fragmentation are artifacts of `edges`, not the connected-water policy. Enclosed water is not a terminal under either policy unless it touches an edge; it can be filled through a spill route if another terminal exists.

Time is O(N log N), storage O(N), for N raster cells. This does not meet the future cascade's bounded random-access requirements. Heights are signed int; differences use long, avoiding overflow for extreme integer fixtures. Arrays in a returned result belong to the caller and are not cached or shared between experiments.

## Viewer

Open `/hydrology.html`, or use **Open finite hydrology reference** above the main field explorer. The link carries world A's seed, complete parameters, and origin, sampling approximately the same footprint with 128 samples at four times the main pixel spacing (capped at the analysis limit).

All nine reference layers are in the horizontal **top bar**: elevation, filled elevation, depression fill depth, water connectivity, runoff accumulation, outlet catchments, flow direction, flood order, and routing status. The runoff overlay has a land-cell threshold and excludes unresolved cells. Clicking a cell traces its entire downstream path and exposes exact diagnostic values. Changing colors or threshold reuses the same solved raster.

Region inputs do not silently move the displayed experiment. Edit them and click **Analyze region**; the old map remains labeled with its original region until the new result succeeds. Export writes the last successful analysis plus its exact full query and versions to JSON, even if inputs were subsequently changed. There is no browser pan/zoom that accidentally changes drainage boundaries.

## Subregion ownership ledger

`WindowFlux.audit` checks any nonempty, half-open rectangle inside the committed raster without rerouting it. A cell's runoff belongs to exactly one partition; a boundary crossing belongs to its directed source-to-destination edge, including diagonal corner crossings. For each rectangle:

`local runoff + incoming flux = outgoing flux + terminal discharge + unresolved local runoff`

Unresolved runoff is an accounting category, not a storage/deposition simulation. Incoming contributions are read from the full reference graph, not estimated or independently generated. An audit scans O(N) cells. The API includes four quadrant ledgers; selecting a quadrant in the viewer draws its boundary and displays the exact budget. Partition totals cancel internal incoming/outgoing transfers and preserve total runoff and discharge.

This is an H3 **accounting precursor**, not a production window solver or coarse-to-fine boundary-injection implementation. It deliberately does not claim that an independently solved crop would match the full graph.

## Checks and remaining work

`genesis.ps1 test` includes bowl/spill, diagonal water connectivity, no-terminal, flat, and extreme-integer fixtures; 300 small rasters compared to a separate whole-grid minimax relaxation; exact local flux recurrence; route adjacency, non-increasing fill height, strict order descent, terminal ownership; and a 1024² synthetic-raster graph/mass check. HTTP gates compare complete analysis arrays with direct output, verify both new assets, and reject oversized/overflowing/out-of-domain requests. Existing 144 pixel baselines remain unchanged.

V2 adds terminal-policy fixtures (enclosed lake versus connected water, closed dry edges, no fallback, stopping within connected water), diagonal crossing ownership, arbitrary-origin rectangle conservation, four-way partition cancellation, and full/unresolved ledgers. HTTP checks both policies and rejects treating `outlets` as a world-generation parameter.

These tests establish the stated finite reference contract, **not** cascade equivalence. The next prerequisite is a world-level root/port and water-connectivity contract, followed by a production solver constrained to those commitments and compared to an independent global calculation with the same inputs and ties. Physical rainfall, endorheic lakes, erosion, channel geometry, and Minecraft integration remain later work.

Browser UI verification was attempted using the computer-use skill, but its helper stopped because it could not confidently determine the current browser URL. A later retry with the confirmed main address reported no available browser connection. The loopback server itself works. JS syntax and HTTP contracts were checked; interactive layout, route selection, and exports still need live browser verification.
