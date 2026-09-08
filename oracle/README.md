# Reference oracles

## Boundary-summary research experiment

`src/genesis/oracle/BoundaryFlowSummary.java` implements exact finite DAG reduction to boundary transfers, with nested coarsening and explicit terminals. It is Java21 test/research code, excluded from the production core JAR. The complete finite source graph is an input; no world-root or constant-size summary guarantee is claimed. [Experiment contracts and results](../research/boundary-flow-experiment.md) describe the independent reference tests and code-generated diagnostics.

## Implemented finite reference

`src/genesis/oracle/FiniteHydrology.java` is a new Java 21, integer-only, finite D8 priority-flood reference. It is independent of `core/`, used only by tests and the local analysis harness, and excluded from the production core JAR. Run `../genesis.ps1 test` from this directory, or open the viewer's `/hydrology.html` page after starting the server. See [the boundary, numeric, tie and validation contracts](../docs/Finite-Hydrology.md).

This is a whole-raster morphology/reference experiment with explicit terminals, not the original JS oracle and not a coarse-to-fine cascade equivalence check.

## Missing external JavaScript oracle

The user does not have a known original prototype. Stop treating its recovery as a task or prerequisite; develop and document independent references here instead. The confirmed production target is an infinite, order-independent world. The finite reference's crop-dependent outlets are diagnostic boundary conditions only; see [canonical drainage](../docs/Canonical-Drainage.md).

The roadmap says a JavaScript plates → uplift → elevation → potential → D8 flow → river oracle already exists. It was not present in the supplied workspace. No replacement is being represented as that original.

When supplied, place it here with its source/version, run command, parameter defaults, boundary/depression rules, and sample outputs. Keep it independent of `core/` and test-only. Java 64-bit hashes must use JavaScript `BigInt` with explicit 64-bit wrapping if compared bit-for-bit; JavaScript `Number` cannot represent arbitrary long seeds.

Before M3, distinguish an unconstrained D8 morphology reference from a global oracle of the same coarse commitments used by the cascade. See [the roadmap review](../docs/Roadmap-Review.md).
