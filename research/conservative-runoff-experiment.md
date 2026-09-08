# R1b: catchment-aligned conservative source hierarchy

Date: 2026-09-08. Version: `conservative-runoff-v1`. Status: implemented finite experiment; not a production rainfall or basin generator.

## Question

Can a fixed coarse runoff budget define consistent fine sources, so that a catchment query does not need to materialize all upstream sources again? Can we demonstrate exact spatial ownership instead of assuming an arbitrary watershed is cheap to integrate over a separate rainfall quadtree?

This experiment answers those questions **after preprocessing a complete supplied finite drainage forest**. It does not solve the infinite-plane root problem or remove the initial upstream-context requirement.

## Spatial ownership and the actual hierarchy

Every supplied cell has a unique integer identity, unique signed-integer coordinate pair, one downstream receiver or explicit terminal, and a nonnegative reference preference. The reference preference is not rainfall or actual runoff. The grid fixture's cells use adjacent grid receivers; arbitrary graph fixtures stress the algebra without claiming physical spatial embedding.

For any node v, its upstream catchment U(v) partitions exactly into:

`U(v) = {v} disjoint-union U(u1) disjoint-union ... disjoint-union U(uk)`

where each ui drains directly to v. No upstream cell belongs to two siblings, and no source is left outside the partition. Terminal catchments partition the complete supplied forest. Unknown coordinates/receivers fail closed instead of creating fallback basins.

This is a hierarchy of **actual upstream subtrees**, not rectangular regions falsely labeled as watersheds. It can be very unbalanced. It is also a fixed finite topology: common-point crop/zoom consistency does not establish invariance under arbitrary addition of new graph nodes or movement of outlets.

## Allocation protocol

Implementation: [ConservativeRunoff.java](../oracle/src/genesis/oracle/ConservativeRunoff.java), Java21 research code. It reuses the existing deterministic hash primitive; no third-party dependency or production field is added.

1. Validate identities, unique coordinate ownership, receivers, cycles, and exactly one supplied budget per terminal.
2. Precompute reference-preference sums over every subtree. This reads the **whole finite graph** and is explicitly part of construction, not a free query operation.
3. A terminal budget is **net runoff contributed by its entire catchment**. It is not rainfall, external-only boundary inflow, or a new source injected on top of fine runoff.
4. At each split, the current cell's weight is its own preference; each tributary's weight is its subtree's reference sum. Multiply each by a deterministic positive integer factor in `[1, maxMultiplier]`, derived from seed, parent identity and slot coordinates. Multiplier 1 disables this weight variation (seeded remainder ties can still matter).
5. Allocate the parent's integer budget in proportion to those weights. Take exact rational floors, then distribute leftover units in descending fractional-remainder order, breaking ties by unsigned hash then slot identity. BigInteger arithmetic protects weight sums and products, even for `Long.MAX_VALUE` budgets.
6. Zero-preference cells and wholly dry subtrees receive zero. All-zero weights accept only a zero budget; positive water without an eligible source fails instead of silently inventing rainfall.
7. A query follows the requested node's downstream chain to its supplied terminal, then applies splits back toward that node. It returns its catchment amount, locally owned source, tributary allocations and work counters. Other tributary interiors are not materialized during that query.

The recursively generated local amounts define the actual source field. They are not an approximation to an independently generated fine rainfall field. Detailed preferences are already inputs, so this does not yet demonstrate an on-demand infinite climate model.

## Verified consistency

`ConservativeRunoffGates` is part of `./genesis.ps1 test`. Reproduce it alone after building:

```powershell
./genesis.ps1 build
java '-Djava.awt.headless=true' -cp build/classes genesis.harness.ConservativeRunoffGates
```

- 500 allocation cases compare the sorting implementation to an independent unsorted repeated-selection reference. Tests cover shuffled inputs, hash/key ties, zero weights, weights larger than 64 bits and maximal budgets.
- 200 finite forests have varied topology, multiple terminals, shuffled sparse identities, heterogeneous reference weights and budgets, including independent roots each receiving `Long.MAX_VALUE`.
- Independently materialize every local source, then walk it downstream and sum its contributions using BigInteger. **Every** catchment query and every tributary allocation must match those independent upstream integrals.
- Independently sum source preferences along the same walks to check preprocessing and spatial ownership.
- Feed the materialized local sources into R1a's boundary-flow reduction and verify retained-port discharge. Root budget is not injected again as local water.
- Test dry roots/subtrees, proportional no-jitter counterfactuals, invalid receivers/cycles/coordinates/configurations, copied inputs, immutable results, extreme signed coordinate/seed values and 384 concurrent cold comparisons.
- Spatial common-point checks use several sampling steps and coordinate queries over the same fixed 24 x 16 domain. Out-of-domain queries fail; viewport edges never become terminals.

## Results and diagnostics

`build/conservative-runoff.json` and `build/gallery/conservative-runoff.png` are reproducible outputs uploaded by CI. The image was inspected: four panels show reference preferences, local sources for seeds 42 and -1 on a shared scale, and seed-42 accumulated discharge. The red circle marks the explicit fixture terminal, not an inferred ocean. Bottom-row local source weights are zero; nonzero flow there comes from upstream.

| Fixture | Measured/resulting quantity |
| --- | --- |
| Spatial domain | 384 cells; one supplied terminal |
| Root runoff, both seeds | Exactly 1,000,003 units |
| Spatial maximum query work | 22 splits; maximum 73 slots visited |
| 1,024-node chain, upstream-end query | 1,024 splits; 2,047 slots visited |
| 1,024-node star, root query | One split; 1,024 slots visited |

Construction keeps O(N) graph/weight entries with sorting/map and BigInteger costs. A query visits only its ancestor chain, but each split handles and sorts its k = fanout + 1 slots. Its work is proportional to `sum over ancestors of k * (1 + log k)`, plus map lookup and integer-arithmetic costs; path storage is O(depth). Full cold construction is not included in the query counters. There is **no** generic O(log N) claim. The star is a non-grid adversary; bounded grid adjacency restricts fanout but not downstream depth.

## Important limitations and decisions still needed

1. **Precomputed context remains essential.** Subtree membership and reference sums come from a complete finite graph. R2 must make the necessary large-scale context constructible and bounded across the plane; this experiment cannot certify current merged continent objects.
2. **The drainage tree is not automatically a balanced generation hierarchy.** Long trunks can create long query chains. A balanced ownership/refinement representation must preserve the same source accounting and port commitments; do not assume a quadtree solves this automatically.
3. **Largest-remainder allocation is not budget-monotone.** With weights `[1500,1500,900,500,500,200]`, a 25-unit budget allocates 3 units to each 500-weight slot, but a 26-unit budget allocates 2. This exact counterexample is a regression fixture and `budgetMonotonicityGuaranteed` is false in the report. The current rule is a static quantization experiment, not a physical rainfall-response model. Before climate coupling, choose a monotone alternative or explicitly accept and bound quantization effects at the chosen unit scale.
4. **Changing topology or preferences defines different inputs.** Preserving a terminal total does not by itself preserve all earlier tributary commitments under a newly refined graph. R3 needs inherited identities, conserved reference weights and parent constraints, not arbitrary graph reconstruction.
5. **No weather or water losses are simulated.** Reference preference and actual runoff are distinct. Evaporation, infiltration, storage, lake spills and sediment require their own contracts. No conservation claim equates net runoff with raw rainfall.
6. **Visual coherence is not established.** Hashed multipliers produce visibly uneven sources in the deliberately simple fixture. Smooth climate structure and realistic basin geometry must be evaluated separately; exact accounting alone does not validate them.

## Next

R1a and R1b now provide tested finite algebra for exact transfer and source ownership. Start R2 with explicit candidate root/terminal contracts and bounded construction costs, including how mainland divides and ocean connectivity work when continental objects merge. R3 then compares end-to-end watershed constructions under matched forcing and terminals. Production terrain and viewer stay unchanged until those integration contracts are justified.
