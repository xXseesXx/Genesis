# R2: root and terminal design comparison

Date: 2026-09-08. Status: **in progress**. R2a's maritime-envelope lookup/separation experiment and R2b's multi-seed whole-object screening baseline are implemented; neither complete world-root candidate is implemented or selected. Production remains `genesis-m5a-v1` with its existing continents.

## What a root must certify

A computational root is not necessarily a watershed. A continental domain can contain several disconnected islands, many drainage basins and interior lakes. Keep these identities separate:

- `domainId`: a canonically bounded region whose coarse information can be computed completely.
- `basinId`: a domain plus a committed terminal/catchment identity, assigned by its drainage construction.
- `terminalKind`: proven ocean connection, or a specifically modeled closed-basin water balance; not merely negative terrain height.

At any supported world coordinate, a production candidate must determine its domain without exploring an arbitrarily long chain of overlapping continent objects. That domain must then provide all coarse sources, boundary behavior, terminal contracts and refinement commitments needed by the query. A viewport, a cache entry's extent and the numeric coordinate guard are never terminal conditions.

This exposes a constraint shared by both algorithm families: a finite solve cannot preserve arbitrary existing land connections across its boundary while assuming zero external inflow and independent ocean access. That missing context must be resolved at a larger canonical scale, or those connections must be restricted by the generative model.

## Two candidate architectures

The comparison below uses the **same proposed maritime-domain contract**, reference geological forcing and source units. This isolates terrain-first versus constructive drainage decisions instead of confounding them with different continents. The maritime layout is itself provisional; R2b must evaluate whether it can produce acceptable continental geography.

| Contract | A: bounded terrain-first coarse solve | B: constructive watershed hierarchy |
| --- | --- | --- |
| Domain lookup | Exact maritime-domain key from bounded site lookup | Same lookup; not a second unrelated set of region borders |
| Coarse geography | Generate continental support and reference relief within a domain whose exterior ocean reserve is committed | Same domain and coastal commitments; drainage can participate earlier in authoring the interior |
| Basin/divide identity | Emerges from the finite coarse routing graph under explicit terminal/depression rules | Committed as a partition and rooted drainage structure before detailed terrain |
| Ocean terminals | Connected water components reaching the reserved ocean, found inside the canonical solve domain | Same certified ocean; committed mouth slots must attach to it, not be arbitrary low points |
| Interior water | Classify connection to the reserve; otherwise lake/depression handling remains explicit | Same distinction, with inherited lake/spill contracts |
| Runoff | Accumulate finite coarse net sources; condition finer sources on committed totals | Allocate conserved budgets over declared catchment ownership; R1b supplies a finite algebraic reference |
| Refinement | Inherit coarse graph, multiple crossings, budgets and bed/divide constraints | Inherit the same kinds of contracts through the ownership hierarchy |
| Resource bound | Fixed world-coordinate box and fixed coarse sampling/work protocol; no expansion to a viewport or neighboring land chain | Domain work is bounded too; cheap finer queries additionally require bounded hierarchy depth and port complexity |
| Main advantage | Existing finite reference methods offer a practical coarse construction baseline | Can make topology, source ownership and fine terrain design agree by construction |
| Main unresolved issue | Coarse solve cost, legitimate lake behavior and faithful drainage-preserving amplification | Realizable spatial partitions, balanced refinement, multiple ports and natural basin morphology |

Candidate A is the lower-risk **first end-to-end baseline to implement after geography acceptance**, not a selected production architecture. Candidate B remains important for local conditioned refinement, but R1b's raw drainage tree is not the balanced constructive hierarchy it would require.

A third option preserves the current arbitrary union of continental objects and discovers upstream context lazily. That retains more geometric freedom but has no demonstrated hard cold-work bound. Memoization does not repair this limitation. Do not present it as an equivalent bounded alternative unless the cost contract is explicitly relaxed.

## R2a experiment: bounded maritime envelopes

Implementation: [MaritimeEnvelope.java](../oracle/src/genesis/oracle/MaritimeEnvelope.java), Java21 research code only. Each lattice address `(i,j)` has one site in the middle half of its S-by-S address cell. Sites depend on seed and absolute coordinates; the layout is not a repeated map tile. Integer squared distance plus lexicographic address ties selects a nearest-site Voronoi domain.

A query returns its exact domain address and whether it lies in an **ocean reserve** around domain boundaries. The complement is eligible continental space, not automatically land. Land would still need its own coherent construction inside that space. The experiment only tests the diagnostic candidate mask:

`candidateLand(x,z) = currentContinentalLand(x,z) AND NOT reservedOcean(x,z)`.

This late clipping is deliberately evaluated as a geographic-cost baseline, not adopted into the generator.

### Exact clearance rule

Let w be the winning site, s a competitor and q the query. Define:

`gap = |q-s|^2 - |q-w|^2`.

The entire axis-aligned box `q + [-m,m]^2` belongs strictly to w if, for every competitor:

`gap > 2*m*(abs(s.x-w.x) + abs(s.z-w.z))`.

Reason: the squared-distance difference is affine in q, and its largest possible decrease over that box is exactly the right-hand side. A failed or equal comparison reserves the point as ocean. This avoids floating-point square roots and handles exact Voronoi ties conservatively.

The selected experiment uses S divisible by 64, `64 <= S <= 2^20`, and `1 <= m <= S/8`. These are research configuration bounds, not new terrain Params or viewer sliders.

### Why bounded lookup is sufficient

The site's position in the query's own address cell differs by at most `3S/4` on either axis. It therefore provides a nearest-distance upper bound of `9S^2/8`.

For every point in the clearance box, that same site's per-axis distance is at most `7S/8`, since `m <= S/8`. Any site omitted from the fixed 5-by-5 address neighborhood is at least `17S/8` away along one axis. It cannot beat the included own-cell site anywhere in the clearance box. Thus 25 sites suffice for both ownership and the complete clearance decision, independent of world position, query order or continent shape.

The implementation's bounded coordinate differences keep distance and clearance products within signed 64-bit integers at the permitted maximum S. A larger 9-by-9 BigInteger reference verifies the arithmetic and support separately.

### Why each computational domain is bounded

Suppose the winning site has address `(i,j)`. Every point it owns has squared distance at most `9S^2/8` from it. A point at or beyond `(i-1)S` or `(i+2)S` along x is at least `5S/4` from this site's possible x positions, already exceeding that distance bound; the same holds along z.

Therefore all owned points lie strictly inside:

`((i-1)S, (i+2)S) x ((j-1)S, (j+2)S)`.

This 3S-by-3S box is a **computation bound**, not an artificial coastline. Its edges are not automatically ocean terminals. At a proposed 64 coarse cells per spacing, its inclusive grid has `193^2 = 37,249` vertices. That is a node cap for a future solve, not a measured chunk-latency promise. Future root computation near the numeric guard must evaluate its required halo without clipping it into a terminal; that full solve policy is not implemented here.

### What the ocean-reserve argument does and does not prove

Under the mathematical construction extended to every lattice cell on the plane, Voronoi cells are bounded, convex and locally finite. Their boundaries form a connected unbounded network: each finite cell has a connected boundary, and neighboring cells share boundary edges/vertices. Reserving a positive-width neighborhood of that network supplies a connected continuous ocean route between domains. Fine generation must never fill or bridge this reservation.

This is a geometric construction argument, not a claim that CI has proved infinite connectivity. It also does not classify every below-sea-level pocket as ocean. Interior water needs connection classification against this certified reserve within the canonical coarse domain.

For sampled routing, two nonreserved anchors separated by at most m in each coordinate cannot have different owners: the second would lie in the first's certified ownership box. The D8 separator test is exact for that mesh spacing. It does **not** establish all final interpolation, riverbed, water-surface or discrete ocean-component properties. Those need independent R3/R4 validation; the report leaves `globalSampledWaterConnectivityProved` false.

## Evidence and visible geographic cost

Run `./genesis.ps1 test`, or build and run `genesis.harness.MaritimeEnvelopeGates` directly. Tests include 1,280 larger-support BigInteger/corner-reference comparisons across five seeds, four spacings and narrow/maximal collars; extreme numeric coordinates; nonempty safe interiors; owner bounds; independent crop/common-point zoom; malformed input and overflow checks; 256 concurrent cold queries.

The geographic audit samples the existing continental model with seed 42 and default `genesis-m5a-v1` parameters over a fixed 256-by-256 image, origin `(-1048576,-1048576)`, step 8192. All candidates use `S=524288`; results are sample counts, **not exact world-area statistics or a representative multi-seed morphology study**.

| Ocean collar m | Existing land samples removed | Share of sampled land | Cross-owner land edges before / after |
| --- | --- | --- | --- |
| 8,192 | 935 / 17,626 | About 5.3% | 1,040 / 0 |
| 16,384 | 1,851 / 17,626 | About 10.5% | 1,040 / 0 |
| 32,768 | 3,608 / 17,626 | About 20.5% | 1,040 / 0 |

The audit checks all adjacent D8 eligible-land owner transitions, not only a chosen river graph. Existing land actually crosses these proposed domain boundaries; assigning IDs without changing geography would not establish independence. Wider collars monotonically remove more sampled land.

`build/maritime-envelope.json` records the configuration and counts. The code-generated `build/gallery/maritime-envelope.png` was inspected. It shows the current coast, red land that late clipping would turn into sea, and domain/reserve geometry. The red cuts through landmasses and the angular maritime network make the tradeoff explicit.

**Decision:** do not adopt this late-clipped mask. It demonstrates bounded separation, not acceptable continental geography. One fixed seed is insufficient to accept or reject every reservation-based layout, but enough to expose the failure mode we must design around.

## R2b screening: whole-object admission versus clipping

A second policy tests whether avoiding cuts is enough: admit an active five-lobe object only if every corner of every lobe's bounding square is safely inside the same eroded Voronoi domain. The safe domain is an intersection of half-planes and hence convex; safe corners certify the whole square and its disk. Reject other objects in their entirety. This is conservative (it can reject disks that actually fit) and is **not** a new continental placement/growth algorithm. Removing overlapping objects can expose different aggregate coastlines even though retained disks are uncut.

Object admission depends only on seed, envelope configuration and the whole object's canonical geometry. A local raw-land sample still examines its original fixed 3x3 scaffold support. Test-side memoization of admission decisions is checked against cold, shuffled object reconstruction. The comparison checks that every admitted land sample remains outside the reserve; it never inserts cropped fragments or fallback land.

The audit uses seeds 42, -1 and 137 at object-presence coverage 45 and 100, with S524288, collar16384, origin(-1048576,-1048576), and a 128x128 grid at step16384. Unlike the earlier coast audit, these three policies all start from **raw lobe support before coarse coast interpolation**. Do not directly compare their counts against the earlier viewer-coast counts.

| Seed / object coverage | Land samples: original / clipped / whole-object | Largest D8 component: original / clipped / whole-object |
| --- | --- | --- |
| 42 / 45 | 4417 / 3965 / 2181 | 237 / 219 / 219 |
| -1 / 45 | 4144 / 3593 / 1720 | 283 / 183 / 162 |
| 137 / 45 | 4469 / 3744 / 1659 | 355 / 201 / 167 |
| 42 / 100 | 9501 / 8318 / 4149 | 7702 / 580 / 294 |
| -1 / 100 | 9632 / 8390 / 4238 | 8510 / 563 / 287 |
| 137 / 100 | 9556 / 8071 / 3920 | 4619 / 501 / 331 |

These are crop-limited sampled components, not globally identified continents or exact areas. The report records how many components touch the crop boundary. A small set of synthetic fixtures separately checks D8 diagonal connectivity and boundary flags. The six samples are screening evidence, not distributional validation over all seeds.

`build/maritime-object-admission.json` stores all metrics and support-object counts. The inspected `build/gallery/maritime-object-admission.png` shows the high-coverage seed42 case. Whole-object admission avoids cutting disks but loses roughly half or more of the original sampled land across these cases. At high coverage, **both policies break large connected regions into much smaller pieces**. Preserving coastline fragments is not sufficient to preserve the continental design goal.

**Decision:** neither late clipping nor whole-object rejection is an acceptable production retrofit at these tested scales. Do not compensate by merely increasing object coverage: the coverage100 cases already reveal severe fragmentation. Keep both as explicit negative baselines. This does not disprove every maritime reservation design, but prevents presenting mathematically bounded lookup as an acceptable terrain outcome on its own.

## R2b: next comparison and acceptance obligations

1. Compare **continental authoring inside reserved maritime space** against this late-clipping baseline. The construction should place and connect continental structures with the reservation already known, rather than slice finished land along its borders. A domain may contain multiple watersheds or islands; do not force one island or one river mouth per cell.
2. Evaluate ways to vary domain scales and shoreline clearance without losing bounded lookup, nonempty interiors, separation or ocean connectivity. Merely warping an image does not prove the transformed coordinate map is valid or invertible.
3. Extend the six-case screening above with geography actually authored to fit and with multiple domain scales. Record lost land, largest connected components, coast shape, domain-scale signatures, and whether desired large landmasses fit. The current single-scale Voronoi network is a baseline with visible structure, not the final continental design. Continent extent is a separate acceptance criterion from basin extent: forcing every computational root to be surrounded by sea couples them unnecessarily unless that root represents a deliberately continental-scale domain.
4. Specify interior-water classification and actual terminal identity. An outer reserved ocean can supply a certificate, but it is not permission to label all water inside a domain ocean or to use the canonical box edges as outlets.
5. Only after selecting a geographic contract, implement A's bounded coarse solve and B's constructive watershed alternative on matched forcing/terminal fixtures in R3. Measure full cold construction and retained-port work, not just the 25-site lookup.

R2 remains open until these choices and contracts are sufficiently complete. R2a is a tested feasibility/cost result, not a replacement for R3's end-to-end watershed proof.

### Subsequent R2b positive fixture

[Authored connected land](authored-landmass-experiment.md) now constructs a connected growth prefix inside the safe domain instead of clipping/rejecting old objects. Ten tested configurations preserve exact connected coverage and independently verified reserve-connected water classification. Main seed42 fixture grows from 1686 to 2741 connected samples at 40/65 percent. This is a separate canonical coarse realization, not preservation of the old coast. Its one-landmass-per-domain assumption, single-scale bias, lack of physical elevation/drainage and full-root numeric-halo limits remain explicit. R2 is still open; the fixture can support bounded routing research without silently promoting its geography.

### Subsequent adjoining-domain decision

[Twelve mosaic/scale audits](authored-mosaic-experiment.md) now show the full cellular-ocean signature at 65/90-percent safe-area coverage. The exact single-scale island layout is rejected as production geography, not merely awaiting a noise adjustment. Keep it as a provisional complete finite routing fixture. Next implement explicit active-cell routing/terminal/depression contracts; excluded neighbor interiors must be barriers, not high traversable terrain. Continental geography and inland hydrological divides remain separate open design work.
