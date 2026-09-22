# Active development roadmap

2026-09-22 lake-outlet continuity: `fluvial-network-v4` retains the real spill-cell-to-outlet reach, so discharge-sized rivers begin inside their source lakes instead of one hydrology cell downstream. Lake-surface composition suppresses the embedded reach's separate river bank/current treatment. The climate/ground/hydrology revamp still feeds `continental-hydrology-v4`; lakes are rebuilt after incision and conservative mass wasting, channel inlets meet the receiving lake stage, and the adapter evaluates lake open volume against the post-channel bed. This is a substantial R3/R4/R6/R7/R9 prototype checkpoint, not completion of calibrated climate, fine drainage proof, lake storage/groundwater, sediment morphodynamics or in-game acceptance.

2026-09-17 adapter scope update: at the user's request, the supplied GTNH starter now hosts an experimental [Minecraft adapter](../mc-adapter/README.md) for current tectonic terrain including erosion and hydrology. Earlier blanket deferrals are superseded for this prototype. M9 acceptance remains open: runtime/pack compatibility, world-save determinism, performance and complete fine water realization are not yet certified.

Historical 2026-09-17 priority checkpoint, superseded in part by the revamp above: [per-continent hydraulic incision](../research/continental-erosion.md) entered the experimental tectonic pipeline using complete family domains, rainfall accumulation, seeded hardness and synthetic plate-age exposure.

Updated: 2026-09-18. This is the authoritative execution order. The original [Dev-Roadmap.md](../Dev-Roadmap.md) remains a historical proposal; conflicting assumptions and schedule estimates there are superseded here. [Research and primary sources](../research/2026-09-08-coherent-world-generation.md) explain the changes.

## Unchanged requirements

- The standalone Java model remains authoritative and independently testable; the Minecraft 1.7.10 / GTNH adapter may prototype it before full integrated acceptance.
- Infinite non-repeating plane, deterministic generation independent of order, cache, crop, and viewer zoom. Numeric support limits are not world boundaries or outlets.
- Continental landmasses and coherent tectonics, geology, climate, water, and surface processes.
- Target about 30% total land area by default (updated user request), not eligible-area coverage or a viewport quota. Preserve mountainous plate joins as well as ocean-facing margins; physical runoff divides require independent verification.
- Immutable typed field contracts, canonical coarse commitments, integer topology decisions, explicit versioned parameters, bounded memoization.
- All new viewer field controls belong in the top bar. Diagnostic images come from generator code. No external JS prototype dependency.
- Preserve this repository and origin. GTNH ExampleMod belongs under `mc-adapter/`, never over the repository root.

## Current baseline

Implemented in the current experimental tectonic path: terrain v8 at 2,048-block base plate spacing, `wind-field-v1`, `terrain-climate-v1`, `terrain-substrate-v1`, complete-family hydrology v4, erosion v2/mass-wasting v1, fluvial-network v4, adaptive two-block water masks, a 46-layer viewer, and adapter columns v8. Vertical height and sea level remain 256/Y63. Older M0–M5a core/field milestones and refinement fixtures remain independently available; production core version remains `genesis-m5a-v1`.

Not implemented: general/global ocean certification beyond prescribed family margins, fine thalweg/cross-divide proof, closed-basin/storage balance, calibrated event flows, groundwater/baseflow or karst routing, seasonal/temperature/snow climate, sediment transport/deposition, full geological histories or pedogenesis/ecology, saved-generator versioning, or in-game GTNH acceptance. Passing focused tests does not imply these are complete.

## Execution sequence

| Stage | Work | Exit evidence | Status |
| --- | --- | --- | --- |
| R0 Research record and revised contracts | Save algorithmic alternatives, primary sources, limitations and proposal | Research indexed; this active plan replaces contradictory assumptions | Complete |
| R1a Boundary-summary experiment | Exact port-level elimination and nested composition on supplied finite DAGs | Independent full-graph equality, conservation, re-entry/multi-exit adversaries, input/order/overflow checks, reproducible report | Complete as a finite experiment; see results below |
| R1b Conservative source hierarchy | Net-runoff allocation with exact remainders and basin-compatible spatial ownership | Parent totals equal all child totals; fine realization and upstream queries agree; loss/rainfall distinction explicit | Complete as a finite supplied-graph experiment; not a production climate model |
| R2 Root/terminal design comparison | Specify two candidates: bounded canonical coarse hydrological regions and constructive watershed hierarchy | Each defines seed/coordinate root lookup, divides, terminal kind, complete upstream budget, domain and work limits | Complete-family coarse prototype now has fixed world support, prescribed maritime terminals and exact rainfall budgets; general/fine ocean certification and alternate architecture comparison remain open |
| R3 End-to-end watershed proof | Compare both candidates on the same forcing/outlet fixtures; implement bounded coarse solving and nested refinement | No missing boundary inflow, stable ports/corridors, multiple crossings, complete roots; exact matched-model oracle | Complete-family rainfall/overflow, stable lake components and deterministic curved corridors implemented; fine descent/divide proof and alternate architecture comparison remain open |
| R4 Conditioned terrain and material coupling | Reference geology + uplift + discharge/area -> channel profiles, divides, hillslopes, exposed rock | Physical bed descent, stable lake surfaces, independent terrain-drainage agreement, no shortcuts; hardness counterfactual | Terrain v8 is incised and rerouted with shared substrate resistance; hydraulic sections/forms, lake-mouth backwater and material-sensitive slope/bank proxies are gated. Fine descent and depth-evolving lithology/mechanics remain open |
| R5 Geological histories and scenarios | Ordered deposition, deformation, faults/intrusions; input-only scenario overrides | Continuity/event-order tests; fold range, batholith and strength-contrast scenarios with counterfactuals | Pending; M5a retained |
| R6 Climate and water state | Coarse climate before dependent commitments; conserved fine runoff; lake storage/spill/closed-basin policy | Explicit atmosphere context; rain-shadow response, exact water balance and no final-field recursion | Bounded wind/humidity/orographic rainfall and exact annual surface-runoff/infiltration/ET partition are implemented before erosion. Seasonal weather, groundwater return, event flow and lake storage/closed-basin balance remain open |
| R7 Sediment, soils, vegetation | Source geology and erosion feed transport/storage/deposition, then ecology potential | Water/sediment ledgers, sedimentary-basin scenario, depositional geometry | Five broad parent-rock classes, slope/moisture-dependent soil depth and compact drainage classes are implemented; local gravity movement conserves/deposits material. Pedogenesis, vegetation and fluvial sediment remain open |
| R8 Integrated standalone acceptance | All realization fields, adversarial worlds, morphology and measured resource budgets | Deterministic canonical output; reviewed code renders; cold work/memory/tail-latency evidence | Targeted invariants, generated-map QA and an ad-hoc resource audit pass; pinned cross-machine acceptance remains pending |
| R9 Minecraft realization | GTNH ExampleMod adapter consumes unchanged pure core/oracle model | Target-runtime compatibility, block/biome order tests, seams, in-game profiling | Adapter columns v4 realize climate-selected biomes, parent rock, soil/drainage horizons, channel sediment and post-carve lakes in JVM tests; actual GTNH client/server, fluid and save-version validation remain open |

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

- The current experimental candidate uses the complete bounded 1–4-plate family as its root at `ceil(plateSpacing/64)` spacing. Its prescribed maritime separation makes the solve complete for this geography; it is not a general root proof for future cross-family land.
- Current families are an accepted work provider only under that explicit maritime contract, not globally certified ocean/basin identities. Geography changes must preserve or deliberately replace the terminal/root contract.
- Production viewer/adapter roots use the coupled climate and substrate partition. Explicit uniform/all-surface-runoff inputs remain only for compatibility and controlled fixtures; independent fine rainfall must not be bolted onto committed flux later.
- Coupled geology/climate/erosion is permitted inside a finite canonical versioned stage with defined initial/boundary conditions and iteration protocol. Recursive final-field feedback is not.
- Keep experimental finite algorithms in the Java21 oracle/harness until their contracts justify a pure Java8 core implementation. Do not add experimental state or implicit global precomputation to chunk queries.
- Fixed work bounds and local measurements now exist, but no portable latency claim precedes a pinned cross-machine benchmark. No schedule estimate assumes the open research questions have already been solved.
- R1b's largest-remainder rule conserves units but is not budget-monotone; a documented 25-to-26-unit counterexample decreases some child allocations. Before physical climate coupling, choose a monotone rule or explicitly accept bounded quantization at a defined unit scale. The supplied drainage tree can also have linear query depth; it is not yet a balanced generation hierarchy.
- R2a gives an experimental 25-site domain lookup and 3S bounding-box argument, but its middle-width ocean collar removes about 10.5% of land in the inspected seed-42 sample. Do not adopt late clipping as a continent fix. Computational-domain IDs are not watershed IDs, and reserved ocean is not a classification of all interior water. See [the candidate comparison and proof obligations](../research/root-terminal-design.md).
- R2b screening across three seeds/two coverages rejects whole-object admission as a retrofit too: in the high-coverage seed42 raw sample, largest D8 land component goes from 7702 to 580 under clipping and 294 under whole-object rejection. Preserve continental-scale extent as an acceptance criterion distinct from basin size; no automatic one-island-per-root architecture.
- R2b now has a positive [connected-growth fixture](../research/authored-landmass-experiment.md) with reserve-connected water classification. Its growth tree is NOT a river graph. One connected landmass per domain and a single maritime scale are fixture restrictions, not accepted production geography. Complete boxes near numeric support fail closed rather than clipping into ocean boundaries.
- R2b [mosaic audit](../research/authored-mosaic-experiment.md) exposes the cellular ocean network across twelve seed/scale/coverage configurations. Even at 76-78% sampled land, no component joins domains. Reject this exact layout for production; retain finite fixtures for drainage. Distinguish continental structure, actual hydrological divides, and computational partitions. Changing S is not viewer zoom and does not remove the topology restriction.

## Next concrete action

The immediate priority is to validate and deepen the coupled [climate/ground](../research/climate-ground-hydrology.md) and [fluvial](../research/fluvial-hydrology-revamp.md) checkpoint. Terrain v8 uses the 2,048-block native preset with derivative-damped detail and linear height transfer, hydrology v4 owns the effective-runoff final eroded DAG, and fluvial-network v4 supplies stable post-erosion lake components, lake-mouth backwater, spill-origin outlets and bounded current/bankfull geometry to both the 46-layer viewer and adapter.

Next engineering/science work, in order:

1. Pin a reproducible performance suite across representative hardware, seeds and maximum supports. Record cold-root CPU/wall/peak memory, cache behavior and warm chunk p95 after the climate/substrate work. The 24 moisture passes, fixed erosion/gravity passes, bounded root dimensions and fixed 5-by-5 channel query are hard work bounds; pre-revamp local timings are historical baselines, not current acceptance results. The fourfold spacing reduction also creates 16 times as many roots per equal explored X/Z area, so include fast-travel cache churn.
2. Prove fine thalweg descent, cross-divide exclusion, confluence/mouth continuity and adjacent-chunk seams against the actual rasterized bed. Curved endpoints and deterministic queries are necessary but not sufficient.
3. Calibrate the implemented climate/material effective-runoff partition and replace the fixed 12-times-mean bankfull proxy with an event provider and independent ledger. Add lake hypsometry plus explicit dry/closed/open storage/loss states, groundwater/baseflow and an explicit karst policy.
4. Calibrate shaped cross-sections and valley-constrained cascade/step-pool/plane-bed/pool-riffle/meander/braid families. Add sediment mobility, bars/floodplains/deltas and toe-erosion-coupled bank failure before claiming Earth-like morphodynamics.
5. Run new-world GTNH client/server QA: world-type loading, fluid updates, chunk seams/order, save/version behavior, memory and flight-speed generation. Computed velocity currently remains metadata over static vanilla source water.

The [one-step crest mesh](../research/crest-mesh-experiment.md) still preserves coarse minimax spills and total mass under fine D8, not receiver identities or tied outlet shares. It remains a useful comparison for the fine-descent proof. Contact-aware tectonic normals, smoother mixed-crust forcing and less cellular family geography also remain useful, but any geography change must preserve or deliberately replace the complete-family terminal contract. R2–R4, R8 and R9 therefore remain incomplete despite the working prototype.
