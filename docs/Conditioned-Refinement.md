# One conditioned refinement step

`DrainageRefinement` is a Java 8 core kernel with version `refinement-v1`. It subdivides **one already-committed parent** into four children. It is not a root generator, continentality function, ocean classifier, rainfall model, or complete infinite-world cascade.

## Input contract

- Parent identity: `(level, i, j)`, level 1..20, within the numerical coordinate support. Child level is one lower; row-major order is NW, NE, SW, SE.
- One inherited outgoing `BoundaryPorts.Port` and zero to three incoming ports, at most one crossing per parent side. A side cannot be both incoming and outgoing.
- Ports may originate at this parent level or an older ancestor level. The original identity and coordinates remain unchanged; finer face ownership is located with `childFace`, never by rehashing the inherited port.
- Incoming units are **external to this parent**. `localRain` belongs to this parent only. `committedOutflow == sum(external inflow) + localRain` is required, not silently recomputed to excuse an inconsistent parent ledger.
- Four nonnegative integer resistance values. They are explicit fixture/consumer inputs, not floating-point height comparisons. Future composition can sample appropriate registered world fields to supply them. No subsystem internals or finite reference arrays are imported.

The caller owns the meaning and provenance of the supplied budgets. The kernel rejects duplicate sides, wrong-parent or wrong-world port geometry, negative units/costs, malformed arrays, numeric overflow, and inconsistent totals. It cannot infer that an upstream system mislabeled an internally consistent total as external inflow.

## Routing and ownership

The outgoing port determines its unique child owner. That child is the local root, not necessarily an ocean mouth. Child adjacency is the four cardinal edges of a 2×2 square. Each edge has the strictly positive integer cost `resistance[a] + resistance[b] + 1`.

A four-node Dijkstra calculation obtains exact shortest distances to that root. Equal priorities and equal-distance predecessor candidates use unsigned hashes of canonical child coordinates, with exact index fallback. Every internal route strictly decreases distance; every child reaches the inherited outlet in at most three internal edges. The distance is a graph cost, not height or physical length.

An internal child edge receives a canonical child-level crossing shared by both child cells. The exit child keeps the original inherited outgoing port. Each inherited incoming port is injected into its unique child exactly once. Local rain is divided by four; remaining units are allocated in canonical hash order, so the four child amounts differ by at most one and sum exactly to the parent amount. Changing runoff does not change routing or crossings.

Accumulate child-local rain, external injections, and upstream-child outflows in reverse distance order. The exit child's outflow must equal the parent commitment exactly. All arithmetic is integer with checked long sums. Geometry may be drawn as child-center-to-port segments inside owning cells; **parent containment is tested, not a narrower river-corridor/H4 guarantee**.

This kernel has a fixed four-node workload, no memo cache, and immutable outputs with defensive array access. This does not prove the cost of obtaining its parent commitments in an infinite world.

## Two-parent laboratory

Open `/refinement.html` or **Open conditioned refinement** above the main viewer. Diagnostic layers are in its top bar: network, local runoff, external inflow, accumulated flow, resistance, and drainage distance. The diagram is generated directly by Java, with an exact per-child ledger below it.

The explicit example connects parent A eastward to parent B. Synthetic resistance is hashed from child coordinates; it is not terrain height or a proposed geology model. Each parent has the chosen local runoff. B is generated first from its committed incoming budget, then A is generated independently.

Default budget:

`A: 100 incoming + 11 local = 111 outgoing`

`B: 111 incoming + 11 local = 122 outgoing`

The shared port has the same original identity and coordinates on both sides. The external entrance and exit are fixture inputs, **not** outlets created by the canvas edges. The selected world point chooses canonical parent A; the diagram shows its full box and neighboring B rather than solving a crop. No output is registered as a pure world field because the world-level parent ledger is not implemented yet.

HTTP endpoints `/api/refinement` (JSON) and `/api/refinement.png` (image) accept `seed`, `x`, `z`, `level`, `coarseSpacing`, `rain`, `inflow`, and `layer`. These fixture controls are separate from world generation parameters. Coordinates, graph distances, and runoff units are serialized as decimal strings to preserve long precision in JavaScript. Inputs and the two-parent final budget must fit signed long. The browser computes the overall balance with BigInt. Exports describe the last successful result, not subsequently edited inputs.

## Verification

- 600 seeds/parent configurations, all directions, zero and maximal resistance, multiple port inputs, negative coordinates, even/odd custom spacing: compare distances to exhaustive simple-path enumeration.
- Root reachability, strict integer distance descent, inherited exit identity, internal shared-face identity, parent containment, unique incoming ownership, exact child recurrence and parent conservation.
- Input permutation, fresh kernel construction, concurrent requests, generating B before A, defensive copies, rejection cases, zero runoff and `Long.MAX_VALUE` budgets.
- An additional composed-subdivision smoke test carries older ancestor ports unchanged through a second application and audits each child budget. This is not a full global multilevel cascade gate.
- Eight exact JSON SHA-256 baselines capture topology, resistance, budgets and port identities without platform-dependent fonts or timing values. The gallery command prints candidates but does not overwrite baselines.
- API JSON/image equivalence, full-width long transport, malformed/oversized/foreign parameter rejection. Existing 160 world-image baselines remain unchanged.
- Code-generated `build/gallery/refinement-{network,rain,inflow,flux,cost,distance}.png` images for inspection. The network image was inspected directly; browser gestures are not part of that claim.

## Port corner fix and versioning

Inspection exposed an older assumption: Params permits any integer `coarseSpacing` in its bounds, not only slider-step multiples. Custom spacing can put a port offset on a child grid corner (including odd spacings and some nonstandard even spacings). `BoundaryPorts` now excludes offsets divisible by the finest base spacing; all finer grid sizes are multiples of that base. Existing standard slider-step placements are unchanged. `genesis-m3a-v2` records this fix; prior default world-field image baselines remain valid.

## Next: continents and world commitments

The user explicitly wants coherent **continental landmasses**, not a standard noise heightfield with extra detail. The current crust-blended continental score is a prototype, not the accepted final continent model. Small noise should perturb structure, not supply the primary continent/basin organization.

The next major design must jointly establish continental interiors, persistent ocean separations/coarse coastline commitments, and world-coordinate basin terminal/root ancestry on the non-repeating infinite plane. Useful acceptance checks include sampled land-component size distributions, interior-to-coast distances, ocean-separation persistence, and stability under crop/zoom/query order. Finite-window connectivity must remain labeled conditional rather than promoted to global ocean truth.

That is still unresolved. Adding fine tributaries or more noise layers does not solve it. Once those parent commitments exist, this refinement kernel supplies a tested boundary/rainfall accounting step; weighted rainfall disaggregation, multiple ports per side, lake terminals, explicit corridor conditioning, channel beds and full hierarchy complexity still need their own work.
