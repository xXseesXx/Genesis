# R1a: exact finite boundary-flow summaries

Date: 2026-09-08. Version: `boundary-summary-v1`. Status: implemented and standalone experiment checks passed. This is a finite graph-algebra experiment, not an infinite-world basin generator.

## Question and scope

Can we remove a region's detailed interior, preserve each boundary transfer and internally generated contribution, and reproduce exact discharge? Does composing finer summaries give the same result as directly summarizing a coarser partition?

The experiment starts from an explicitly supplied complete finite, acyclic, single-receiver graph with nonnegative integer net-runoff contributions and explicit terminals. There is no terrain, ocean search, climate solver, storage, or unrepresented external inflow. It does not discover the complete upstream context: that context is an input.

Implementation: [BoundaryFlowSummary.java](../oracle/src/genesis/oracle/BoundaryFlowSummary.java), Java21 oracle/harness only. Production Java8 core fields, generator version, viewer and historical golden images are unchanged.

## Representation and algorithm

Each immutable node has a stable original key, one receiver key (or explicit terminal `-1`), and a locally owned runoff contribution. A retained nonterminal node represents the original directed edge from that node to its original receiver; a retained terminal is not an artificial tile-edge outlet.

1. Validate unique keys, nonnegative contributions, known receivers and acyclicity. Sort identities so caller order cannot change results.
2. Evaluate the supplied partition once per node. Retain every edge crossing between owners and every explicit terminal, including zero-flow crossings.
3. In reverse topological order, find the first retained exit reached from each interior node.
4. Assign each local source to that first exit exactly once. These aggregated local contributions are `b` in `q_out = T q_in + b`.
5. Connect each retained crossing to the first retained exit reached after entering its receiving region. These links encode the transfer map `T`.
6. Accumulate only the reduced port graph. Compare each retained flux against an independent full-graph source walk.

Summarizing again using a **coarsening** of the original partition composes the transfer model. A summary is not a replacement for the eliminated geometry when refining or repartitioning more finely; retain/regenerate the source graph for that.

Complete construction is not a cold local query. Canonical sorting costs O(N log N); graph traversal and reduction cost O(N), with O(N) working memory. Subsequent accumulation is O(P), where P is the retained-port/terminal count. In the worst case P = N. Root generation and root lookup are not implemented.

## Validation

`BoundarySummaryGates` runs in the ordinary `./genesis.ps1 test` suite. It can also be reproduced after building:

```powershell
./genesis.ps1 build
java '-Djava.awt.headless=true' -cp build/classes genesis.harness.BoundarySummaryGates
```

Checks include:

- 400 seeded arbitrary DAGs, sparse shuffled identities, nonuniform/zero runoff, multiple terminals, random partitions and their parents.
- Independent O(N * path length) source-by-source walks, rather than reusing the topological accumulation algorithm.
- Independent first-exit walks verify the complete port set, each local contribution `b`, and each transfer link `T`, not only one resulting flux vector.
- Exact equality at every retained crossing and terminal, plus each region's local + external incoming = outgoing + terminal ledger.
- Direct coarse reduction equals composed nested reduction, including whole-root closure; repeated same-partition reduction is idempotent.
- Spatial grid partitions at multiple scales; cold reconstruction, input-order invariance, caller-mutation isolation and 128 concurrent computations.
- A -> B -> A -> B re-entry through distinct ports. Collapsing each region to one node would invent a cycle that the actual graph does not contain.
- Multiple exits and zero-discharge crossings remain represented; `Long.MAX_VALUE` is exact when valid, overflow fails instead of wrapping.
- Duplicate IDs, missing receivers, null inputs, negative sources and real cycles fail closed.
- A local-context counterexample: identical downstream nodes have mouth discharge 1 or 101 when a distant upstream contribution changes.
- An alternating-owner chain retains 256 of 256 nodes: summaries do not offer a general constant-size guarantee.

## Reproducible results

The fixed random seed is 20260908. `build/boundary-summary.json` records computed values, not timing promises:

| Fixture | Original nodes | Retained nodes | Result |
| --- | --- | --- | --- |
| 400 arbitrary DAGs, combined | 25,797 | 24,422 | Exact retained flux and nested composition |
| 24 x 16 spatial fixture, 6 x 4 tiles | 384 | 105 | Exact 1,608 units total runoff/discharge |
| Alternating-owner chain | 256 | 256 | No compression, as expected |

The spatial fixture is deliberately synthetic, with a supplied terminal row and two converging trunks. Its code-generated `build/gallery/boundary-summary.png` compares the full graph and summary. The right-hand links show transfers, not reconstructed river geometry. The image was inspected for legibility and agreement with the recorded counts. It is diagnostic output, not a replacement golden baseline.

## Interpretation and next experiment

The finite affine transfer idea works for these tested contracts, including nested composition and region re-entry. That supports the boundary-summary building block, not the proposed complete architecture. Randomly assigned regions compress poorly; even the useful grid reduction required complete upstream input first.

Next is R1b: define a conservative net-runoff hierarchy aligned to catchment ownership, independently materialize its fine sources, and compare all queried coarse/tributary totals. Then R2 must supply actual finite root and terminal contracts across the infinite plane. Neither result can be inferred from R1a.
