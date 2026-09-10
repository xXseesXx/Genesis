# Reference oracles

## Authored-land research experiment

`src/genesis/oracle/AuthoredLandmass.java` grows connected coarse land inside a complete canonical maritime domain and classifies water by explicit reserve connection. Its positive-cost growth tree is not a river network. [Contracts and results](../research/authored-landmass-experiment.md) cover exact coverage, independent connectivity checks and unaccepted geographic restrictions. Production fields are unchanged.

## Maritime-domain research experiment

`src/genesis/oracle/MaritimeEnvelope.java` tests bounded jittered-Voronoi ownership and an exact integer ocean-clearance restriction. It is not a production coast or basin generator. [Candidate comparison and evidence](../research/root-terminal-design.md) document the 25-site support argument, finite domain box, continuous-ocean reasoning, remaining discrete terminal obligations, and the visible land loss caused by late clipping. The clipped candidate mask has not been adopted.

## Conservative-source research experiment

`src/genesis/oracle/ConservativeRunoff.java` conditions fine local sources on supplied terminal net-runoff budgets using actual upstream-subtree ownership. It preprocesses a complete finite graph, reuses the core hash primitive, and remains outside the Java8 production JAR. Independent source walks, allocation references and R1a boundary composition check exact results. [Results and limitations](../research/conservative-runoff-experiment.md) include unbalanced query cost and non-monotonic integer apportionment; this is not an infinite-world rainfall generator.

## Boundary-summary research experiment

`src/genesis/oracle/BoundaryFlowSummary.java` implements exact finite DAG reduction to boundary transfers, with nested coarsening and explicit terminals. It is Java21 test/research code, excluded from the production core JAR. The complete finite source graph is an input; no world-root or constant-size summary guarantee is claimed. [Experiment contracts and results](../research/boundary-flow-experiment.md) describe the independent reference tests and code-generated diagnostics.

## Implemented finite reference

`IrregularPlates`, `ContinentalGroups`, `PlateResponse` and `TectonicTerrain` provide the [v3 motion-driven candidate](../research/motion-driven-relief.md): warped weighted plate edges, varied size/age, spatial crust, bounded families of1–4 plates, relative-motion-derived datum/tilt and convergent mountain sutures. A fixed−1700 m bed offset gives29.83% mean land across six wide samples. `CrustProvinces` and v1/v2 measurements remain historical research. The broad response is a bounded proximity heuristic, not exact curved contact mechanics. The candidate is neither a hydrology root provider nor a simulated geological history.

`ContinentalHydrology` now adds a separate [complete-family coarse hydrology experiment](../research/continental-hydrology.md): variable irregular active footprints on a canonical world lattice, prescribed maritime reservoirs, filled spill levels, steepest descent outside flats and exact rainfall-derived flux. `RainfallField.Uniform(1000)` is the intentionally barebones default; spatial providers already work. `ActiveHydrology.solveD8Rivers` adds the steepest-receiver pass without changing the older reference solvers. Family IDs identify work/cache regions, not individual catchments. Independence relies on v3's ocean-separated geography; this is not a generic root provider, fine carved terrain, transient lake simulation or completed production hydrology.

`JunctionForcing` composes existing plate-vector profiles with bounded soft multi-plate weights, avoiding the closest-profile selector. It exposes positive/negative net pair contributions and has 25-versus-121-site, independent reduction and exact-output checks. Convex blending can still lower a drainage divide; this is neither final elevation nor certified face geometry. See [the junction experiment and viewer-oriented next steps](../research/junction-forcing-experiment.md).

`CrestMesh` realizes D4 edge crests as a one-step triangulated surface, with a D8 audit through `ActiveHydrology.solveD8Surface`. Independent tests preserve original spill levels/reachability and total source ledgers, not every receiver or outlet share. Added vertices have no new runoff. See [the mesh contract and limitations](../research/crest-mesh-experiment.md).

`BoundaryForcing` consumes the existing core plate vectors/crust/age and produces canonical shared motion descriptors and illustrative cross-boundary profiles. Tests cover relative motion and profile polarity, plus a rift-forced pass invalidating a drainage divide. Its closest-edge spatial sampling can have junction seams; it is not registered as production elevation. See [the forcing experiment](../research/boundary-forcing-experiment.md).

`ActiveHydrology` adds a separate finite D4 graph oracle with excluded vertices, heterogeneous net runoff and explicit edge crests. It uses full minimax relaxation and exact source accumulation. Three actual mixed-boundary pair fixtures compare unrestricted union solves with independent interiors; lowering a test pass must expose a leak. See [paired drainage](../research/paired-drainage-experiment.md). The existing `FiniteHydrology` and viewer semantics are unchanged; graph saddles are not a fine terrain surface.

`MixedBoundaryEnvelope` and `MixedBoundaryLandmass` add an R2c research fixture: mutual-nearest cells share ridge land while other faces remain ocean-facing. Canonical whole-cell budgets target 53% total land area; shared junctions, actual bridges and water classification are tested. Ridge strength is a constraint indicator, not elevation or a physical divide guarantee. The disjoint-pair restriction is not final continental geography. See [the experiment and next routing contracts](../research/mixed-boundary-experiment.md). These Java21-only classes are excluded from the core JAR and not called by viewer fields.

`src/genesis/oracle/FiniteHydrology.java` is a new Java 21, integer-only, finite D8 priority-flood reference. It is independent of `core/`, used only by tests and the local analysis harness, and excluded from the production core JAR. Run `../genesis.ps1 test` from this directory, or open the viewer's `/hydrology.html` page after starting the server. See [the boundary, numeric, tie and validation contracts](../docs/Finite-Hydrology.md).

This is a whole-raster morphology/reference experiment with explicit terminals, not the original JS oracle and not a coarse-to-fine cascade equivalence check.

## Missing external JavaScript oracle

The user does not have a known original prototype. Stop treating its recovery as a task or prerequisite; develop and document independent references here instead. The confirmed production target is an infinite, order-independent world. The finite reference's crop-dependent outlets are diagnostic boundary conditions only; see [canonical drainage](../docs/Canonical-Drainage.md).

The roadmap says a JavaScript plates → uplift → elevation → potential → D8 flow → river oracle already exists. It was not present in the supplied workspace. No replacement is being represented as that original.

When supplied, place it here with its source/version, run command, parameter defaults, boundary/depression rules, and sample outputs. Keep it independent of `core/` and test-only. Java 64-bit hashes must use JavaScript `BigInt` with explicit 64-bit wrapping if compared bit-for-bit; JavaScript `Number` cannot represent arbitrary long seeds.

Before M3, distinguish an unconstrained D8 morphology reference from a global oracle of the same coarse commitments used by the cascade. See [the roadmap review](../docs/Roadmap-Review.md).
