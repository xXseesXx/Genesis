# Genesis

A deterministic terrain generator for a future Minecraft 1.7.10 / GTNH mod. Development starts with a standalone Java core and a local field viewer, following [Dev-Roadmap.md](Dev-Roadmap.md) and the longer [concept notes](warning_half_baked_concepts.md).

The **M0 foundation, M1 tectonics, and M2a coast/elevation prototype are implemented**. Eighteen fields expose plate structure, crust blending, committed land/sea signs, macro elevation, and bounded coarse drainage diagnostics. Local automated checks pass. M2a is a partial milestone: global ocean connectivity and the hydrology cascade remain open, and unresolved drainage is explicit. Minecraft integration remains deferred. See [tectonics](docs/Tectonics.md) and [coasts/elevation](docs/Coasts-and-Elevation.md) for assumptions and bounds.

An independent **finite hydrology reference** now adds depression filling, D8 flood-tree routing, catchments, exact land-cell runoff, and sampled water connectivity in a separate fixed-region laboratory. It deliberately does not change the production terrain fields. See [finite hydrology contracts](docs/Finite-Hydrology.md).

## Run

Requires JDK 21 (`java`, `javac`, `jar` on PATH) and PowerShell. No downloaded Java dependencies, Gradle installation, Minecraft installation, or npm packages are needed for the standalone prototype.

From this directory:

```powershell
.\genesis.ps1 test       # Compile, package core, run automated gates
.\genesis.ps1 serve      # Local viewer; Ctrl+C stops it
.\genesis.ps1 gallery    # Render golden candidates without replacing baselines
.\genesis.ps1 build      # Compile and produce build/genesis-core.jar
```

Open [http://127.0.0.1:8787](http://127.0.0.1:8787). Use `serve -Port 8788` if the default port is occupied. On Linux CI, run the same script with `pwsh`.

The viewer puts **all field controls in a horizontal top bar above the maps**, with “View” shortcuts, checkboxes, opacity, and field-specific legends. Boundary strokes composite last. Drag to pan, scroll to zoom, or click to inspect exact field values. All parameters have sliders and numeric inputs. Synchronized A/B maps accept separate seeds and B JSON overrides such as `{"upliftStrength":1.5}`. PNG export also downloads the exact request configuration as JSON; your browser may prompt to allow multiple downloads. Export describes the last successfully displayed frame.

Macro elevation is the initial view, with land hillshade, bathymetry colors, and sampled viewport land fraction. Pink coarse drainage cells mean unresolved within the search radius. Coarse rank/distance/direction describe the cell anchor, not the exact fine column, and are not yet carved rivers.

Use **Open finite hydrology reference** above the main map, or open [the hydrology laboratory](http://127.0.0.1:8787/hydrology.html). Its nine diagnostic layers stay in a horizontal top bar. Compare connected-water terminals against artificial edge outlets, inspect downstream paths, audit quadrant water budgets, adjust the runoff overlay, and export the exact result/configuration as JSON. With no connected water, that policy shows unresolved drainage in pink instead of inventing mouths. Boundary-connected water is explicitly not called globally proven ocean.

The Java 21 harness serves only on IPv4 loopback. The core is compiled with `--release 8`, uses strict floating-point evaluation, and has no Minecraft, HTTP, rendering, or third-party dependencies. That preserves a conservative bytecode target for eventual reuse; actual GTNH compatibility still requires M9 integration tests.

## Structure

| Path | Purpose |
| --- | --- |
| `core/src/main/java/genesis/core/` | Immutable generator configuration, typed fields, integer hash/lattice, optional tile memoization |
| `harness/server/src/` | Read-only HTTP server and fixed-palette PNG renderer |
| `harness/viewer/` | Local HTML/CSS/JS viewer |
| `harness/gates/src/` | Executable correctness, rendering, API, lint, and timing checks |
| `harness/golden/` | Versioned parameters, reference PNGs, and exact pixel hash baselines |
| `oracle/` | Independent finite Java reference; the original external JS oracle is still missing |
| `mc-adapter/` | Integration plan only; GTNH ExampleMod starter is deferred to M9 |
| `docs/Roadmap-Review.md` | Proposed clarifications and hydrology risks |

## Field contract

```java
Generator world = new Generator(42L, Params.defaults());
double point = world.fields.get(Fields.NOISE, -1L, 20L);
double[] tile = world.fields.tile(Fields.NOISE, -16L, 0L, 1L, 16, 16);
```

Coordinates are signed integer block positions. Bulk tiles are row-major and sample `x + column * step`, `z + row * step`. The supported domain is ±2^40 blocks; tile endpoints and arithmetic overflow are checked. A larger pixel step changes sampling density, never the function at a given coordinate. This tests sample consistency, not the future hydrology cascade commitment rules. For Long plate/child IDs use exact `get()` or `values()` instead of `tile()`; HTTP returns these identities as strings.

`Params` copies and validates overrides, rejects unknown keys/non-finite values, and exposes an immutable complete registry. Seed + complete params + generator version define a world. JavaScript keeps seeds as strings to avoid loss of 64-bit precision.

`TileCache` is optional, bounded by tile count, thread-safe, scoped to one immutable generator, and returns defensive copies. Tectonics also memoizes at most 64 immutable site neighborhoods per generator. The viewer creates a complete immutable configuration per request, so changing parameters cannot reuse stale cached values. Subsystems consume the field registry; `Generator` is the composition root.

## Validation and limits

`test` runs:

- DET: all 18 fields over a 64×64-chunk region in raster, shuffled cached, and cold per-chunk order; additional extreme seeds/coordinates.
- API contracts: negative lattice math, chunk seams, scalar/tile/zoom equivalence, parameter validation, cache eviction, caller mutation, and concurrent reads.
- HASH: fixed mixing vector, domain/coordinate influence, and a 64×64 matrix of marginal output bit-flip frequencies. This is not a full chi-square independence test.
- GOLD: 144 reference PNGs: 16 unchanged M0, 72 unchanged M1, and 56 M2a; exact row-major ARGB SHA-256 against versioned baselines. Output PNGs are generated in `build/gallery/`; a mismatch produces a highlighted pixel-difference image.
- TECT: larger-window ownership/distance reference, symmetric boundary classes, sub-plate refinement stability, sampled uplift continuity, extreme parameters, and concurrent/evicted memoization.
- COAST: analytic coast fixture, exact land/height sign agreement, coast-preserving relief changes, zoom/edge stability, independent nearest-terminal rank reference, monotone resolved paths, explicit unresolved behavior, and memoization checks.
- HYDRO reference: analytic spill/water fixtures, 300 independent minimax comparisons, exact runoff conservation and acyclic outlet ownership, and a 1024² synthetic raster. This does not yet compare a production cascade to a global oracle.
- LINT: state/dependency guards, cross-subsystem imports, numeric allowlists, and an integer-only topology source check. These are guardrails, not a formal whole-program purity proof. Future hydrology must extend them.
- HTTP: assets, field metadata, inspection, image composition against direct core output, and malformed request rejection.
- BUD: all current fields, p95 cold ≤25 ms/chunk, p95 cached ≤1 ms/chunk, and median of five 512² macro-elevation renders <1 s after JVM warmup. Results and environment are recorded in `build/budget.json`. This excludes browser painting, PNG transport, and future terrain fields.

CI targets Windows and Linux with JDK 21 and uploads gallery images, timing reports, and the core JAR. Browser automation was unavailable during implementation, so the HTTP contract and generated PNGs were checked, but interactive layout and gestures still need browser verification. Lint/hash checks are targeted guards rather than formal proofs.

The inspector has scalar values and exact plate/child identities. Scenario loading, stratigraphy, cascade windows, full scientific terrain metrics, arbitrary-size stitched exports, and production cascade/oracle comparison gates arrive with their owning milestones. HTTP image requests currently cap each dimension at 1024; core tiles cap at 2048; finite hydrology API requests cap each dimension at 256. There is no substituted external oracle or claim of global drainage correctness.

To intentionally change golden output, run `gallery`, inspect the candidate PNGs, then explicitly update the corresponding reference PNGs and `.properties` manifest under `harness/golden/m0`, `m1`, or `m2a` in the same reviewed change as the generator update. The gallery command never changes these baselines automatically. Prior milestones' references remain independent of added fields.

## Next

Complete global ocean/inland-sea classification and root/outlet contracts, then replace bounded local distance searches with the coarse-to-fine hydrology cascade described in the [roadmap review](docs/Roadmap-Review.md). Import the existing JS oracle when available. The longer concept notes are reviewed, including the fourth sedimentary-basin scenario. Preserve this repository and its `origin`; eventual mod scaffolding belongs under `mc-adapter/` using the official GTNH starter.
