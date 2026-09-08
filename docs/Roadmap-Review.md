# Roadmap review and proposed refinements

The original `Dev-Roadmap.md` is preserved. These are design proposals and explicit M0 implementation choices, not claims that the later milestones are solved.

## Keep the core idea

Keep the standalone Java core, immutable queries, canonical coarse commitments, and a harness as the first product. Conditioning terrain on a drainage network is a promising architectural direction. Preserve the separation between physical structure and the Minecraft block renderer.

## Resolve these before M3

1. **A potential formula does not prove drainage.** Suppose a land node has elevation 0 and distance-to-ocean 10, while every neighbor has elevation 100 and distance at least 9. With λ=1, its potential is 10 and all neighbors are ≥109. Local descent is stuck, even with a correct ocean-distance field. More generally, no finite fixed λ handles arbitrary relief without an additional bound. A spill-direction hash cannot manufacture a lower neighbor. Prefer a committed parent/outlet graph with a bounded, nonnegative integer rank that strictly decreases to an ocean terminal; elevation can then be conditioned on that graph. If preserving `elevation + λ·distance`, specify the graph metric and prove the necessary elevation-gradient bound. Test every terminal's type as well as cycle absence.

2. **Decouple reference routing from the proposed cascade.** First define ocean masks, closed-depression handling, plateau/tie rules, open boundary ports, and rainfall units. An unconstrained D8 oracle on independently generated elevation is not the same model as a coarse-committed graph. Demanding near-identical topology between those different models is not automatically meaningful. Use an independent global implementation of the same commitments for exact invariant comparisons, and retain unconstrained D8 as a morphology comparison. Measure shared interior regions with matched boundary inputs; a finite oracle's artificial edges should not become rivers.

3. **Canonical edge ownership comes before window accumulation.** Identify each parent cell, outlet, shared edge port, and corridor by integer keys. Adjacent queries must derive the same shared-edge data from those keys. For each child partition, count each rainfall source once and each boundary transfer once. Coarse inflow that already includes a child's rainfall must not be counted again in the child's accumulation. Specify exact disaggregation and leftover-unit allocation. H3 should audit this ownership ledger, including arbitrary window origins and corner crossings.

4. **Hack's exponent is a diagnostic, not a complexity proof.** A fitted relation between river length and basin area does not bound the number of upstream dependencies needed for an exact query. Compact basins alone do not eliminate nonlocal information. Keep the proposed range as a provisional morphology target, with fit method, sample selection, scale range, and uncertainty recorded. Gate complexity using instrumented node evaluations, bounded hierarchy depth, cache bytes, and adversarial layouts. Do not claim O(W² log R) until the boundary summary algorithm and its information requirements are explicit.

5. **Move coarse geology and climate inputs ahead of dependent commitments.** Hardness cannot alter an already committed routing graph without creating a new generator version/world configuration. Likewise, rain shadows derived from the final carved terrain and rainfall-driven incision can form a dependency cycle. One workable first version is tectonics + coarse geology → macro relief/ocean topology → coarse wind/precipitation → committed routing and flux → incision and exposed strata → local climate/surface detail. Fine climate may alter soil/vegetation without retroactively changing canonical rainfall. Uniform rainfall is a legitimate early stage if later rainfall changes are explicitly versioned.

6. **Define an implementable world domain.** “Infinite world” and “virtual planet with latitude” need a geometry decision. A finite periodic planet, repeated latitude bands, and a nonperiodic plane have different edge and hydrology behavior. Specify the maximum hierarchy level and what roots do. Strictly decreasing values on an unbounded domain do not by themselves guarantee reaching a terminal. M0 uses a documented ±2^40-block coordinate domain, without pretending it is a planetary topology solution.

7. **Make coarse stability measurable.** Specify which objects are immutable (outlets, adjacency, ocean connectivity, corridor IDs), which geometry may refine, and in which metric displacement is measured. A low-resolution image cannot sample every narrow feature; visible pixel differences during zoom are not automatically a topology failure. Test object identity and containment using common world-space coordinates in addition to renders.

## Make the gates more precise

- Separate tunable parameters from mathematical/algorithm constants and infrastructure limits. Hash multipliers, interpolation coefficients, array indices, HTTP limits, and test sample sizes should not become terrain sliders. M0 centralizes all five noise tuning values and allowlists algorithm literals; future generators must extend that guard explicitly.
- Integer tie-breaking alone does not make a topology decision deterministic if the ordering before the tie uses unstable floating-point comparisons. For plates, use bounded integer/fixed-point positions and squared-distance comparisons with an overflow policy. For drainage, compare complete integer ranks/keys. Guard both the primary comparison and tie-break.
- Version worlds with seed, full parameters, and generator algorithm version. Document allowed numerical operations. M0 emits Java 8 bytecode with strict evaluation for floating-point field math; cross-architecture behavior still needs actual tests, especially after adding transcendental functions.
- Treat valley/river checks as geometric and hydraulic assertions. Concavity must be measured across the channel, not blindly in all directions. Audit downstream channel-bed and water-surface profiles explicitly; a monotone abstract potential alone does not prevent a visually uphill river after carving.
- Compare canonical block/biome arrays in Minecraft order tests. Region files contain storage metadata such as timestamps and compression/layout details; binary-identical region files are a stronger and potentially unrelated requirement.
- Budget all realization fields together and include allocations, tail latency, thread contention, and bounded cache memory. M0's cold measurement means no generator/tile memoization after JVM warmup; it is not process startup time. Keep shared-runner regressions distinct from calibrated target-hardware acceptance.
- Avoid declaring CI a proof of purity. Import/literal guards catch common mistakes; adversarial tests and design review are still necessary. The current lint deliberately stops at new tectonic/hydrology modules until integer topology checks are added.

## A smaller next sequence

| Stage | Concrete exit |
| --- | --- |
| M0 foundation | Runnable core/viewer plus honest coverage and performance baselines; finish browser QA and stronger hash/lint gates |
| M1 | Integer plate ownership, symmetric boundary classification, continuous uplift, reviewed golden images |
| M2a | Explicit domain, coarse ocean connectivity and root/outlet contracts |
| M2b/M3a | Independent global routing oracle with specified depression and boundary treatment |
| M3b | One refinement level with exact shared-edge ownership, conservation and corridor containment |
| M3c | Multiple levels, query-order/overlap/adversarial tests and measured scaling |
| M4 onward | Conditioned terrain, geology, climate and sediment once routing contracts hold |

This sequence preserves the intended milestones while testing the highest-risk assumptions before investing in visual terrain detail. No schedule estimate here assumes those research questions already have a solution.

## GTNH integration

Use the official [GTNH ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10) project starter at M9. Its current README explicitly directs new mods to the starter archive rather than cloning/forking the example repository. Record the chosen starter version and checksum when importing it under `mc-adapter/`, preserve its applicable notices, and adapt the dependency/build extension points to consume the single core source/library. Do not replace Genesis's `.git`, remote, roadmap, or root files with the example repository. No starter files or Minecraft dependencies have been imported at M0.

## Reconciliation with the longer concept notes

The longer [warning_half_baked_concepts.md](../warning_half_baked_concepts.md) appeared during the M0 build and has been read in full. It supplies the roadmap's §11, §35–36, and §39–42 references. It uses the name Viridium; this repository and implementation retain the requested name Genesis. Its broad causal vision is preserved, while its illustrative equations and material numbers remain design hypotheses.

- §11 provides a compact primordial-crust → deposition → intrusion → orogeny → metamorphism → erosion → glaciation → modern-deposition event vocabulary. Before M5, define event ordering, spatial ownership, and how deformation composes with erosion queries. Adding event types should not require voxelizing their history.
- §39–41 support the three M6 scenarios. They call for morphology to follow geological inputs; scenario identifiers should select inputs only and must never appear in generator branches. Validate counterfactuals too: removing strength contrast, uplift, or forcing should change the claimed forms.
- **§42 adds a fourth scenario absent from the named M6 suite:** a subsiding sedimentary basin receiving sediment from a river network. Add `scenario_sedimentary_basin` at M8, with exact sediment accounting, measurable accumulation/young surface deposits, and floodplain/delta geometry. Validate that sediment delivered from upstream is not independently created again in the basin.
- §35–36 leave machine learning as an optional offline research tool. No ML dependency belongs in M0 or on the eventual chunk-generation path.
- §37–38 reinforce the standalone field-map prototype. The M0 noise layer is explicitly a harness diagnostic; structural mountain and river models still begin at their proper milestones.

Only the existing JS oracle remains missing. `oracle/README.md` records the expected handoff.
