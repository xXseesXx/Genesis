# Finite hydrology reference (M2b groundwork)

`oracle/src/genesis/oracle/FiniteHydrology.java` is a new, independent Java 21 reference experiment. It has no production-core imports and is excluded from the Java 8 core JAR. It is **not** the missing pre-existing JavaScript oracle, a production cascade, or proof of infinite-world drainage.

## Boundary and numeric contract

- Input is a finite, complete rectangular raster, integer elevations, an explicit sea-level mask, and an explicit terminal mask. Maximum reference size is 1024 × 1024 cells. The API accepts each dimension from 2 to 256 to bound interactive cost.
- The harness samples existing `baseElevation` and `seaMask`, rounds model metres to millimetres, and preserves signs (land at least +1 mm, sea at most 0 mm). Floating elevation is an input conversion only; routing comparisons are integer-only. Existing core fields are unchanged.
- The viewer declares **every edge cell an outlet**, at its sampled elevation. This is an open-boundary experiment, including dry edge cells. The standalone solver accepts arbitrary explicit terminals; with none, routes and outlet labels remain -1, discharge is zero, and the unchanged `filled` array is not a solved spill surface.
- Water components use D8 adjacency, including corner contact. Class 0 is land, 1 is boundary-connected sampled water, 2 is enclosed sampled water. This is independent of the terminal mask. Enclosed water need not stay enclosed on a finer grid; boundary-connected water is not necessarily ocean.
- The full raster, origin, sample spacing, generator seed/params/version, boundary policy, and reference version define the experiment. Moving or resampling it may change every derived diagnostic. No window-dependent result is registered as a pure world field.

## Solver and tie rules

The implementation is an integer priority flood in the family described by [Barnes, Lehman and Mulla, Priority-Flood](https://arxiv.org/abs/1511.04463). It is our simple reference implementation, not a port or claim to implement their optimized queue variants.

Initialize all terminals in a heap sorted by `(filled elevation, row-major cell index)`. Pop the minimum cell and discover its unvisited D8 neighbours in fixed dz/dx order. A neighbour receives `max(original neighbour height, popped filled height)` and routes to that popped cell. Each cell is inserted once. On equal-height plateaus the row-major tie introduces orientation bias; it is reproducible, not a claim of natural river morphology.

The resulting filled value is the minimum, over paths to any terminal, of the maximum input elevation along that path. The downstream predecessor has no greater filled height and a strictly smaller removal index. This gives a terminating acyclic graph without floating epsilon gradients. Routes are a **flood tree**, not steepest-descent D8 slope selection. Original terrain can rise along a route through a filled depression; no carving has taken place.

Each original land cell contributes one runoff unit; sea-mask cells contribute zero. Traverse reverse removal order to accumulate these units downstream. Every outlet stores its exact contributing land-cell count. The sum at outlets equals the number of reachable land cells. These units are neither rainfall nor physical discharge. Sea-floor routing and edge fragmentation are expected artifacts of this boundary policy.

Time is O(N log N), storage O(N), for N raster cells. This does not meet the future cascade's bounded random-access requirements. Heights are signed int; differences use long, avoiding overflow for extreme integer fixtures. Arrays in a returned result belong to the caller and are not cached or shared between experiments.

## Viewer

Open `/hydrology.html`, or use **Open finite hydrology reference** above the main field explorer. The link carries world A's seed, complete parameters, and origin, sampling approximately the same footprint with 128 samples at four times the main pixel spacing (capped at the analysis limit).

All eight reference layers are in the horizontal **top bar**: elevation, filled elevation, depression fill depth, water connectivity, runoff accumulation, outlet catchments, flow direction, and flood order. The runoff overlay has a land-cell threshold. Clicking a cell traces its entire downstream path and exposes exact diagnostic values. Changing colors or threshold reuses the same solved raster.

Region inputs do not silently move the displayed experiment. Edit them and click **Analyze region**; the old map remains labeled with its original region until the new result succeeds. Export writes the last successful analysis plus its exact full query and versions to JSON, even if inputs were subsequently changed. There is no browser pan/zoom that accidentally changes drainage boundaries.

## Checks and remaining work

`genesis.ps1 test` includes bowl/spill, diagonal water connectivity, no-terminal, flat, and extreme-integer fixtures; 300 small rasters compared to a separate whole-grid minimax relaxation; exact local flux recurrence; route adjacency, non-increasing fill height, strict order descent, terminal ownership; and a 1024² synthetic-raster graph/mass check. HTTP gates compare complete analysis arrays with direct output, verify both new assets, and reject oversized/overflowing/out-of-domain requests. Existing 144 pixel baselines remain unchanged.

These tests establish the stated finite reference contract, **not** cascade equivalence. The next prerequisite is a world-level root/port and water-connectivity contract, followed by a production solver constrained to those commitments and compared to an independent global calculation with the same inputs and ties. Physical rainfall, endorheic lakes, erosion, channel geometry, and Minecraft integration remain later work.

Browser UI verification was attempted using the computer-use skill, but its helper stopped because it could not confidently determine the current browser URL. JS syntax and HTTP contracts were checked; interactive layout, route selection, and exports still need live browser verification.
