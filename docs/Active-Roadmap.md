# Active development roadmap

Updated: 2026-09-09. This is the authoritative execution order. The original [Dev-Roadmap.md](../Dev-Roadmap.md) remains a historical proposal; conflicting assumptions and schedule estimates there are superseded here. [Research and primary sources](../research/2026-09-08-coherent-world-generation.md) explain the changes.

## Unchanged requirements

- Standalone Java core first; Minecraft 1.7.10 / GTNH only after integrated acceptance.
- Infinite non-repeating plane, deterministic generation independent of order, cache, crop, and viewer zoom. Numeric support limits are not world boundaries or outlets.
- Continental landmasses and coherent tectonics, geology, climate, water, and surface processes.
- Target 50-55% total land area, not eligible-area coverage. Explore shared ridge/ocean cell boundaries so connected land can span computational ownership while genuine divides separate runoff.
- Immutable typed field contracts, canonical coarse commitments, integer topology decisions, explicit versioned parameters, bounded memoization.
- All new viewer field controls belong in the top bar. Diagnostic images come from generator code. No external JS prototype dependency.
- Preserve this repository and origin. GTNH ExampleMod belongs under `mc-adapter/`, never over the repository root.

## Current baseline

Implemented: M0 harness/core, M1 tectonic foundation, M2a coast/elevation and integrated continental prototype, partial M3a coastward routing/runoff and canonical ports, finite oracle, four-child refinement fixture, M5a material columns. Thirty-two scalar fields and one structured column; production version remains `genesis-m5a-v1`.

Not implemented: complete hydrological roots/ocean connectivity, arbitrary-depth world-conditioned hydrology, drainage-preserving terrain, full geological histories, coupled climate/runoff, sediment and soils, Minecraft realization. Passing existing tests does not imply these are complete.

## Execution sequence

| Stage | Work | Exit evidence | Status |
| --- | --- | --- | --- |
| R0 Research record and revised contracts | Save algorithmic alternatives, primary sources, limitations and proposal | Research indexed; this active plan replaces contradictory assumptions | Complete |
| R1a Boundary-summary experiment | Exact port-level elimination and nested composition on supplied finite DAGs | Independent full-graph equality, conservation, re-entry/multi-exit adversaries, input/order/overflow checks, reproducible report | Complete as a finite experiment; see results below |
| R1b Conservative source hierarchy | Net-runoff allocation with exact remainders and basin-compatible spatial ownership | Parent totals equal all child totals; fine realization and upstream queries agree; loss/rainfall distinction explicit | Complete as a finite supplied-graph experiment; not a production climate model |
| R2 Root/terminal design comparison | Specify two candidates: bounded canonical coarse hydrological regions and constructive watershed hierarchy | Each defines seed/coordinate root lookup, divides, terminal kind, complete upstream budget, domain and work limits | In progress: mixed ridge/ocean pairs now join land at 53% total-cell coverage; physical divide/routing and general continent topology remain open |
| R3 End-to-end watershed proof | Compare both candidates on the same forcing/outlet fixtures; implement bounded coarse solving and nested refinement | No missing boundary inflow, stable ports/corridors, multiple crossings, complete roots; exact matched-model oracle | Building blocks: active D4 pair agreement plus one triangulated refinement preserves spills/mass under D8; outlet/port-preserving cascade and candidate comparison pending |
| R4 Conditioned terrain and material coupling | Reference geology + uplift + discharge/area -> channel profiles, divides, hillslopes, exposed rock | Physical bed descent, stable lake surfaces, independent terrain-drainage agreement, no shortcuts; hardness counterfactual | Plate-vector profiles now have a bounded soft junction composition; broad elevation and integrated terrain/material/water coupling pending |
| R5 Geological histories and scenarios | Ordered deposition, deformation, faults/intrusions; input-only scenario overrides | Continuity/event-order tests; fold range, batholith and strength-contrast scenarios with counterfactuals | Pending; M5a retained |
| R6 Climate and water state | Coarse climate before dependent commitments; conserved fine runoff; lake storage/spill/closed-basin policy | Explicit atmosphere context; rain-shadow response, exact water balance and no final-field recursion | Pending; minimal coarse climate inputs enter R2/R3 |
| R7 Sediment, soils, vegetation | Source geology and erosion feed transport/storage/deposition, then ecology potential | Water/sediment ledgers, sedimentary-basin scenario, depositional geometry | Pending |
| R8 Integrated standalone acceptance | All realization fields, adversarial worlds, morphology and measured resource budgets | Deterministic canonical output; reviewed code renders; cold work/memory/tail-latency evidence | Pending |
| R9 Minecraft realization | GTNH ExampleMod adapter consumes unchanged pure core | Target-runtime compatibility, block/biome order tests, seams, in-game profiling | Deferred until R8 |

R1 is a building-block experiment, not a complete implementation of either R2 candidate. Do not mark R2/R3 complete merely because finite summaries compose correctly. Independent geology work is still useful, but adding unrelated layers must not postpone the shared root, water-budget and terrain-realizability contracts again.

## Revised hydrology acceptance contracts

1. **Terminal completeness:** every production drainage node has a certified downstream terminal of an allowed kind. Real-valued descent and below-sea-level anchors alone are insufficient. Closed basins require explicit storage/loss balance, not hashed spill guesses.
2. **Topology:** prove termination using the constructed model; test cycles independently. Region-level adjacency may cycle through distinct ports even when the river DAG does not.
3. **Mass:** outgoing + storage change + modeled losses = local contributions + external-only incoming. Assign every source and directed transfer exactly once. Refinement cannot generate additional unbudgeted water.
4. **Cross-scale geometry:** committed identities, crossings, corridor bounds, divides and bed/water constraints are inherited. Sampling a narrower feature at a new zoom is not itself a topology change.
5. **Physical realization:** use actual terrain and water elevations, not abstract rank, to audit downhill channels. Test unselected neighboring routes for cross-basin leaks.
6. **Oracle meaning:** exact comparisons use the same committed graph and boundary contracts. Unconstrained D8 on different terrain is a morphology comparison, not a mandatory 99.5% topology match.
7. **Complexity:** state root lookup, dependency depth, maximum ports and upstream summary decomposition. Instrument cold node work and memory. Hack statistics do not prove O(log R); caching does not close unknown context.

## Decision boundaries

- The initial preferred candidate uses a bounded hydrological root-region scale. The scale and construction are not selected yet. Regions must encode real divides/outlets, not one island or artificial sink per square.
- Current continents remain a visual/structural scaffold, not a certified root provider. Coast polish can proceed independently only while preserving committed coast contracts.
- Root runoff may initially use explicit uniform or fixture inputs. The source contract must already support conservative heterogeneous contributions; independent fine rainfall must not be bolted onto committed flux later.
- Coupled geology/climate/erosion is permitted inside a finite canonical versioned stage with defined initial/boundary conditions and iteration protocol. Recursive final-field feedback is not.
- Keep experimental finite algorithms in the Java21 oracle/harness until their contracts justify a pure Java8 core implementation. Do not add experimental state or implicit global precomputation to chunk queries.
- No hard latency claim before cold measurement and construction bounds. No schedule estimate assumes the open research questions have already been solved.
- R1b's largest-remainder rule conserves units but is not budget-monotone; a documented 25-to-26-unit counterexample decreases some child allocations. Before physical climate coupling, choose a monotone rule or explicitly accept bounded quantization at a defined unit scale. The supplied drainage tree can also have linear query depth; it is not yet a balanced generation hierarchy.
- R2a gives an experimental 25-site domain lookup and 3S bounding-box argument, but its middle-width ocean collar removes about 10.5% of land in the inspected seed-42 sample. Do not adopt late clipping as a continent fix. Computational-domain IDs are not watershed IDs, and reserved ocean is not a classification of all interior water. See [the candidate comparison and proof obligations](../research/root-terminal-design.md).
- R2b screening across three seeds/two coverages rejects whole-object admission as a retrofit too: in the high-coverage seed42 raw sample, largest D8 land component goes from 7702 to 580 under clipping and 294 under whole-object rejection. Preserve continental-scale extent as an acceptance criterion distinct from basin size; no automatic one-island-per-root architecture.
- R2b now has a positive [connected-growth fixture](../research/authored-landmass-experiment.md) with reserve-connected water classification. Its growth tree is NOT a river graph. One connected landmass per domain and a single maritime scale are fixture restrictions, not accepted production geography. Complete boxes near numeric support fail closed rather than clipping into ocean boundaries.
- R2b [mosaic audit](../research/authored-mosaic-experiment.md) exposes the cellular ocean network across twelve seed/scale/coverage configurations. Even at 76-78% sampled land, no component joins domains. Reject this exact layout for production; retain finite fixtures for drainage. Distinguish continental structure, actual hydrological divides, and computational partitions. Changing S is not viewer zoom and does not remove the topology restriction.

## Next concrete action

User priority: work toward a separate tectonic generation viewer. [Soft junction composition](../research/junction-forcing-experiment.md) now supplies a bounded continuous-in-real-arithmetic multi-plate anomaly, with 720 larger-support checks and explicit proof that blending can weaken a divide. It is not certified shared-face topology or constrained ridge realization.

The [absolute terrain candidate](../research/tectonic-terrain-experiment.md) now correlates crust across4S provinces, feeds the SAME candidate labels into base elevation and boundary forcing, and classifies land from composed height at fixed sea level. Six wide-area samples yield51.4-53.9% land (mean52.6%); smaller local regions vary. The independent-crust baseline looked sponge-like and is not adopted. The correlated version still has simple plateaus/straight boundaries and is not final geography.

Next expose this exact Java21 candidate in a separate experimental tectonic viewer, with all field controls in the top bar, full configuration/coordinate inspection and no misleading claim of completed rivers. Do not normalize to viewport land fraction or overlay old crust/regime labels on new terrain. Existing production viewers remain available.

The [one-step crest mesh](../research/crest-mesh-experiment.md) preserves coarse minimax spills and total mass under fine D8, not receiver identities or tied outlet shares. Shared hydrological crest constraints, larger connected root layouts, terminal completeness and outlet/port-preserving refinement still need work before an integrated river cascade. An exploratory tectonic viewer need not wait for all of that research; promoting its architecture does. R2/R3/R4 remain incomplete.
