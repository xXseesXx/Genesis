# Genesis

The experimental [Minecraft adapter](mc-adapter/README.md) builds a selectable **Genesis** world type from the supplied GTNH starter. Its current path is terrain `tectonic-terrain-v7`, climate `terrain-climate-v1`, substrate `terrain-substrate-v1`, hydrology `continental-hydrology-v4`, fluvial interpretation `fluvial-network-v2`, erosion v2 and mass wasting v1. From the repository root, build with `.\mc-adapter\gradlew.bat build`; Java 21 and a modern-Java GTNH runtime are required. JVM adapter tests pass, but in-game GTNH behavior and saved-world versioning remain unverified; use a new world because old chunks are not regenerated.

The climate/ground stage uses a global analytic stream-function wind: it is continuous, divergence-free, defined everywhere and has a strict nonzero speed floor. A fixed 24-pass moisture solve replenishes humidity over maritime water, produces orographic windward rain and lee-side rain shadows, then partitions precipitation into surface runoff, infiltration and evapotranspiration from rock permeability, weatherability, slope and soil depth. Only effective surface runoff feeds erosion and river discharge. See the [climate, ground and drainage record](research/climate-ground-hydrology.md).

Hydrology v4 owns the shared `fluvial-network-v2`: accumulated effective runoff yields distinct mean/bankfull discharge, width, depth and velocity, while contributing area, Strahler order, slope and material resistance inform reach form. Minecraft channel widths are scaled eightfold at the hydraulic-profile level while discharge remains conserved, producing broader and generally shallower rivers. Bounded curved reaches select cascade, straight, meandering or two-thread braided geometry. Face-connected depressions expose flat lake surfaces and stable spill/outlet records after incision and gravity erosion; inlet water profiles meet the receiving lake stage instead of cutting a lower-water trench. The viewer and adapter consume the same immutable objects. See the [research-backed fluvial target and status checklist](research/fluvial-hydrology-revamp.md).

Adapter columns v5 rasterize each chunk from a shared one-block world-coordinate halo. The final voxel pass closes every dry cardinal face beside ocean, lake or river water at the adjacent integer stage, so chunk order and chunk borders cannot create a different shoreline or a vanilla-fluid escape gap.

Terrain v7 keeps the v6 stronger tapering height curve and shrinks the immediately prior 8,192-block X/Z preset by another factor of four to **2,048-block plate spacing**, without changing the 256-block height or sea Y63. The hydrology lattice is correspondingly 32 blocks instead of 128, while each complete-continent root retains the same bounded vertex dimensions. More roots occur per explored area, so cache pressure during fast travel remains a performance concern.

The current tectonic viewer defaults to **Eroded terrain** and exposes wind, humidity, terrain-aware rain, rock, soil/rockhead and drainage alongside water diagnostics. Deterministic whole-continent hydraulic incision consumes effective runoff and substrate resistance; a fixed-pass gravity stage conserves and deposits locally moved slope material. Rivers and lake stages are rebuilt from the final post-erosion terrain. Fluvial sediment transport, lake storage/groundwater/karst, seasonal climate and proof of fine thalweg descent remain open. Set **Erosion exposure** to zero for comparison; run `check rivers` or `check tectonic-viewer`.

A deterministic terrain generator with a standalone Java research core/viewer and an experimental Minecraft 1.7.10 / GTNH adapter. The original direction is preserved in [Dev-Roadmap.md](Dev-Roadmap.md) and the longer [concept notes](warning_half_baked_concepts.md); the active roadmap supersedes their schedule and deferrals.

The [active roadmap](docs/Active-Roadmap.md) now defines execution order and corrected acceptance criteria; the original proposal remains preserved. The [research directory](research/README.md) records the scientific sources, algorithmic alternatives, and the proposed coarse-basin/conservative-refinement architecture.

The **M0 foundation, M1 tectonics, and M2a coast/elevation prototype are implemented**, with M3a drainage groundwork and an independent M5a geology foundation. Thirty-two scalar fields plus a structured stratigraphic column expose terrain, tectonics, partial drainage/runoff, and layered bedrock. The target is an **infinite, non-repeating world, deterministic regardless of generation order**. Global ocean connectivity and the complete hydrology cascade remain open; an experimental Minecraft adapter now exists, while full M9 validation remains open. See [tectonics](docs/Tectonics.md), [coasts/elevation](docs/Coasts-and-Elevation.md), and [canonical drainage](docs/Canonical-Drainage.md) for contracts and limitations.

The separate [continental terrain viewer](http://localhost:8787/continents.html) now connects continental shapes to elevation, land/sea, coast-distance ranks, route guides and shared ports. All layers remain in the top bar. Tectonic relief and ridged detail add actual height; A/B, inspection, exports and the finite hydrology link retain the continental world model. The original viewer keeps its original world. Coast shapes remain provisional and rivers are still uncarved guides. See [the connected continental contract](docs/Continental-Scaffold.md) and `build/gallery/continental-world-layers.png`.

An independent **finite hydrology reference** now adds depression filling, D8 flood-tree routing, catchments, exact land-cell runoff, and sampled water connectivity in a separate fixed-region laboratory. It deliberately does not change the production terrain fields. See [finite hydrology contracts](docs/Finite-Hydrology.md).

**World runoff** now accumulates unit rain over the resolved coarse graph without canvas boundaries. Enable **Flow-weighted river guides** over terrain in the top bar; use **Resolved upstream runoff** and **Catchment completeness** to inspect amounts and missing dependencies. Amber catchments touch unknown interiors, pink anchors remain unresolved. This is exact partial-graph accounting, not completed global hydrology or carved riverbeds. See [world runoff](docs/World-Runoff.md) and the code-rendered `build/gallery/continental-runoff.png`.

A **conditioned four-child refinement kernel** now preserves inherited crossings and exact parent flow budgets. Its [two-parent laboratory](http://localhost:8787/refinement.html) has top-bar diagnostic layers and exact ledgers. This is a tested local building block, not a complete infinite-world cascade. See [the refinement contract and continental-landmass requirement](docs/Conditioned-Refinement.md).

**M5a stratigraphy** can progress before finished water: a basement and layered sediment material volume, broad seeded folds, surface rock/resistance/weatherability/age maps, and a click-to-inspect stratigraphic column. All five new layers are in the top bar. Geology samples the current surface without changing terrain or river routing. These are idealized strata, not completed event geology, faults or differential erosion. See [stratigraphy and dependency order](docs/Stratigraphy.md) and `build/gallery/geology-strata.png`.

## Run

Requires JDK 21 (`java`, `javac`, `jar` on PATH). No downloaded Java dependencies, Gradle installation, Minecraft installation, or npm packages are needed for the standalone prototype.

PowerShell:

```powershell
.\genesis.ps1 check tectonic          # One terrain generator; also the check default
.\genesis.ps1 check rivers            # Current continent hydrology only
.\genesis.ps1 check tectonic-viewer   # Current terrain + rivers + HTTP viewer
.\genesis.ps1 serve                   # Local viewer; Ctrl+C stops it
.\genesis.ps1 build                   # Java 8 core JAR only
```

Linux:

```bash
./genesis.sh check tectonic
./genesis.sh check rivers
./genesis.sh check tectonic-viewer
./genesis.sh serve 8787
./genesis.sh build
```

Run `genesis.ps1 list` or `./genesis.sh list` for every target: `tectonic`, `rivers`, and `tectonic-viewer` cover the current generator; `continents`, `hydrology-labs`, `refinement`, `geology`, and `core` isolate older research areas. `check` defaults to `tectonic`; the complete historical suite is explicit as `check all`. The old `test` command remains an alias for the full suite. Successful commands emit only timed `OK` lines and write detailed output to `build/logs/`; failures show only the last 80 lines. Add `-VerboseOutput` on PowerShell or `--verbose` on Linux to stream everything. `gallery` defaults to the current `tectonic-viewer` vertical and accepts the same targets.

Open [http://localhost:8787](http://localhost:8787). Use `serve -Port 8788` on PowerShell or `serve 8788` on Linux if the default port is occupied.

The viewer puts **all field controls in a horizontal top bar above the maps**, with “View” shortcuts, checkboxes, opacity, and field-specific legends. Boundary strokes composite last. Drag to pan, scroll to zoom, or click to inspect exact field values. All parameters have sliders and numeric inputs. Synchronized A/B maps accept separate seeds and B JSON overrides such as `{"upliftStrength":1.5}`. PNG export also downloads the exact request configuration as JSON; your browser may prompt to allow multiple downloads. Export describes the last successfully displayed frame.

Macro elevation is the initial view, with land hillshade, bathymetry colors, and sampled viewport land fraction. Pink coarse drainage cells mean unresolved within the search radius. Coarse rank/distance/direction describe the cell anchor, not the exact fine column, and are not yet carved rivers.

Enable **Coarse route guides** and **Shared drainage ports** in the main top bar for world-coordinate cyan guides and gold crossings over elevation. These use the same canonical geometry in every crop; neither receives canvas boundaries as generation input. They are a realization of the partial coarse graph, not completed river morphology. Code-generated seam and relief diagnostics are written to `build/gallery/canonical-*.png` during tests.

Use **Open finite hydrology reference** above the main map, or open [the hydrology laboratory](http://localhost:8787/hydrology.html). Its nine diagnostic layers stay in a horizontal top bar. Compare connected-water terminals against artificial edge outlets, inspect downstream paths, audit quadrant water budgets, adjust the runoff overlay, and export the exact result/configuration as JSON. With no connected water, that policy shows unresolved drainage in pink instead of inventing mouths. Boundary-connected water is explicitly not called globally proven ocean.

The Java 21 harness serves only on IPv4 loopback. The core is compiled with `--release 8`, uses strict floating-point evaluation, and has no Minecraft, HTTP, rendering, or third-party dependencies. That preserves a conservative bytecode target for eventual reuse; actual GTNH compatibility still requires M9 integration tests.

## Structure

| Path | Purpose |
| --- | --- |
| `core/src/main/java/genesis/core/` | Immutable generator configuration, typed fields, integer hash/lattice, optional tile memoization |
| `harness/server/src/` | Read-only HTTP server and fixed-palette PNG renderer |
| `harness/viewer/` | Local HTML/CSS/JS viewer |
| `harness/gates/src/` | Executable correctness, rendering, API, lint, and timing checks |
| `harness/golden/` | Versioned parameters, reference PNGs, and exact pixel hash baselines |
| `oracle/` | Independent finite Java reference; the unavailable original JS prototype is not required |
| `mc-adapter/` | GTNH starter-based world type consuming tectonic terrain, erosion and hydrology |
| `docs/Roadmap-Review.md` | Proposed clarifications and hydrology risks |

## Field contract

```java
Generator world = new Generator(42L, Params.defaults());
double point = world.fields.get(Fields.NOISE, -1L, 20L);
double[] tile = world.fields.tile(Fields.NOISE, -16L, 0L, 1L, 16, 16);
```

Coordinates are signed integer block positions. Bulk tiles are row-major and sample `x + column * step`, `z + row * step`. Current numeric support is ±2^40 blocks; this is not a finite-map coast or drainage outlet. Tile endpoints and arithmetic overflow are checked. A larger pixel step changes sampling density, never the function at a given coordinate. This tests sample consistency, not the full future cascade. For Long plate/child IDs use exact `get()` or `values()` instead of `tile()`; HTTP returns these identities as strings.

`Params` copies and validates overrides, rejects unknown keys/non-finite values, and exposes an immutable complete registry. Seed + complete params + model + generator version define a world. The optional third constructor argument selects `Generator.Model.CONTINENTAL`; the default is `LEGACY`. HTTP uses `model=continental|legacy`. JavaScript keeps seeds as strings to avoid loss of 64-bit precision.

`TileCache` is optional, bounded by tile count, thread-safe, scoped to one immutable generator, and returns defensive copies. Tectonics also memoizes at most 64 immutable site neighborhoods per generator. The viewer creates a complete immutable configuration per request, so changing parameters cannot reuse stale cached values. Subsystems consume the field registry; `Generator` is the composition root.

## Validation and limits

`check all` (and the compatibility alias `test`) runs:

- DET: all 32 scalar fields in the legacy model over a 64×64-chunk region in raster, shuffled cached, and cold per-chunk order; additional extreme seeds/coordinates. The continental composition gate separately checks all fields with cold crops/common-point zoom and relief cache/concurrency tests.
- API contracts: negative lattice math, chunk seams, scalar/tile/zoom equivalence, parameter validation, cache eviction, caller mutation, and concurrent reads.
- HASH: fixed mixing vector, domain/coordinate influence, and a 64×64 matrix of marginal output bit-flip frequencies. This is not a full chi-square independence test.
- GOLD: 160 reference PNGs: 16 unchanged M0, 72 unchanged M1, 56 unchanged M2a, and 16 new M3a geometry images; exact row-major ARGB SHA-256 against versioned baselines. Output PNGs are generated in `build/gallery/`; a mismatch produces a highlighted pixel-difference image.
- TECT: larger-window ownership/distance reference, symmetric boundary classes, sub-plate refinement stability, sampled uplift continuity, extreme parameters, and concurrent/evicted memoization.
- COAST: analytic coast fixture, exact land/height sign agreement, coast-preserving relief changes, zoom/edge stability, independent nearest-terminal rank reference, monotone resolved paths, explicit unresolved behavior, and memoization checks.
- CONTINENT: connected lobe objects, larger-support BigInteger reference, parameter/coordinate extremes, crop/zoom/order/cache/concurrency checks, eight exact integer-grid baselines, and code-rendered old/new comparisons.
- CONTINENT WORLD: common continental coast across height/mask/drainage/ports, real height components, stable coast under relief changes, exact resolved rank descent, numeric extremes, eight connected-model field baselines, and rendered terrain/layer diagnostics.
- HYDRO reference: analytic spill/water fixtures, 300 independent minimax comparisons, exact runoff conservation and acyclic outlet ownership, and a 1024² synthetic raster. This does not yet compare a production cascade to a global oracle.
- R1a SUMMARY: 400 finite DAGs compared with independent source walks, direct/nested boundary reduction, region re-entry, multiple terminals, exact runoff ledgers, overflow/input guards and cold concurrency. Writes `build/boundary-summary.json` and a code-rendered comparison image; no production world-root claim.
- R1b RUNOFF: 500 exact weighted splits and 200 supplied catchment forests, independently materialized upstream integrals, spatial ownership, dry/extreme/order/concurrency checks, R1a composition and explicit depth/fanout/quantization adversaries. Writes `build/conservative-runoff.json` and a code-rendered four-panel diagnostic; no production rainfall/root claim.
- R2a ENVELOPE: 1,280 larger-support BigInteger/corner checks, bounded root-domain ownership, clearance, cold concurrency and current-continent D8 separation audits at three collar widths. Writes `build/maritime-envelope.json` and a geographic-cost image. The experimental clipped mask is not adopted; R2 remains open.
- R2b screening: three seeds at default/high object coverage compare raw land, late clipping and whole-object admission, including sampled component sizes and crop flags. Writes `build/maritime-object-admission.json` and an inspected diagnostic. Both retrofit policies harm continental extent and remain test-only.
- R2b AUTHORED: ten canonical-domain fixtures grow exact connected land coverage without clipping old objects. Independent union-find checks land connectivity and ocean-reserve water paths; closed pockets remain explicit. Writes `build/authored-landmass.json` and an inspected six-panel image. Growth parents are not river receivers; geography remains test-only.
- R2b MOSAIC: twelve adjacent-domain/scale/coverage fixtures verify D4/D8 separation, cold crop/order/stride/concurrency and nested land growth. `build/authored-mosaic.json` and an inspected image expose the cellular-ocean layout; this exact geography is rejected for production, not a finished continent model.
- R2c MIXED: shared mutual-nearest ridge pairs join land across cell ownership while other faces retain ocean collars. Four mosaics/116 full roots, 144 larger-support nearest references, junction/corner checks, independent water connectivity and 53% total-cell land budgets. Writes `build/mixed-boundary.json` and an inspected image; ridge strips are not yet physically realized drainage divides.
- ACTIVE/PAIRED HYDRO: separate D4 active-cell solver with edge crests and heterogeneous runoff; 300 independent minimax/source-walk tests and three paired union/local equivalence checks. A lowered pass must reveal cross-divide flow. Writes `build/paired-drainage.json` and a code-rendered runoff image; graph saddles are not yet a continuous terrain surface.
- BOUNDARY FORCING: existing plate vectors/crust drive canonical collision, subduction, rift and spreading profiles; 300 BigInteger/core checks and speed/common-motion/side invariants. A rift-forced pass must invalidate an old divide. Writes `build/boundary-forcing.json` and a map/profile diagnostic; multi-edge continuity is not yet solved.
- CREST MESH: one triangulated refinement preserves original D4 spill levels under fine D8 routing; 150 independent reference/source-walk cases and all three actual pairs. Added vertices receive no extra runoff. Writes `build/crest-mesh.json` and a section diagnostic; receiver identities and individual outlet shares are not yet preserved.
- JUNCTION FORCING: bounded soft composition of plate-vector profiles; 720 larger-support/independent cases, shared junctions, common-motion/order/crop/concurrency and a versioned exact-output fingerprint. Writes `build/junction-forcing.json` and an inspected comparison image. A blend-induced divide leak is detected; continuity is not drainage preservation.
- TECTONIC TERRAIN V7: Minecraft-native size 1 uses a 256-block column and sea Y63; sizes 2–4 exactly upscale seeded horizontal features, relief and drainage. The 2,048-block plate preset retains sparse irregular 1–4-plate families, motion-driven mountain joins and the v6 concave height fit. See [the native scale contract](research/minecraft-native-tectonic-terrain.md).
- CLIMATE / GROUND: analytic stream-function wind, 24-pass bounded humidity transport, orographic rain and five material families produce exact runoff/infiltration/evapotranspiration shares and post-relaxation soil/rockhead diagnostics. Gates cover divergence, speed floor, signed-coordinate continuity, windward/lee response, substrate counterfactuals, exact source conversion and unchanged root dimensions.
- CONTINENT HYDRO V4 / FLUVIAL V2: complete-family minimax drainage and exact effective-runoff ledgers feed erosion, gravity relaxation, face-connected post-erosion lake objects, area/order, continuity-checked hydraulics, lake-mouth backwater and fixed-work curved channel forms. Gates include independent topology/source walks, discharge and material counterfactuals, curve/lake/braid invariants, wider support, cache/concurrency and adapter water columns. See [contracts and limits](research/continental-hydrology.md).
- TECTONIC VIEWER: 46 top-bar layers, 21 world parameters, separately versioned climate/wind/substrate metadata, native world-height/sea-level reporting, climate/ground/hydraulic/lake inspection, block contours, tiled panning and a 512² fullscreen map mode. HTTP tests cover size-2 output, coupled fields, curved channels, fine lakes, crop/zoom, exact identifiers/fluxes and zero rain; application tests cover fullscreen transitions, configuration, navigation, stale responses and exports. See [viewer contracts](docs/Tectonic-Viewer.md).
- CHANNEL: exact shared-face ownership and inherited finer-edge containment, independent integer distance reference, crop/zoom/shuffle/eviction/concurrency tests, and zero-pixel-difference stitching of independently generated tiles. Emits actual code-rendered visual diagnostics.
- RUNOFF: independent forward accumulation of the same world graph, exact window ownership ledgers, unknown-frontier propagation, bounded worst-case dependency counts, search-radius extension, shared-port flow and 5,000 shuffled concurrent/evicted queries. Runoff and terrain-plus-flow renders have separate performance measurements.
- GEOLOGY: contact ownership, thickness/age ordering, bounded fold joins, material coefficient counterfactuals, scalar/column agreement, structured bulk/order/concurrency, independence from existing terrain/water, eight exact snapshots, and code-rendered map/section diagnostics.
- REFINE: 600 exhaustive four-child path/budget comparisons, inherited/cross-parent ownership, deterministic rainfall splitting, input order/concurrency tests, composed-subdivision smoke test, overflow rejection, and eight exact JSON baselines. Emits six code-rendered diagnostic images.
- LINT: state/dependency guards, cross-subsystem imports, numeric allowlists, and an integer-only topology source check. These are guardrails, not a formal whole-program purity proof. Future hydrology must extend them.
- HTTP: assets, field metadata, inspection, image composition against direct core output, and malformed request rejection.
- BUD: all current fields, p95 cold ≤25 ms/chunk, p95 cached ≤1 ms/chunk, and median of five 512² renders <1 s after JVM warmup for both macro elevation alone and elevation with guides/ports. Results and environment are recorded in `build/budget.json`. This excludes browser painting, PNG transport, and future terrain fields.

CI exercises the PowerShell runner on Windows and the Bash runner on Linux with JDK 21, then uploads gallery images, timing reports, and the core JAR. Browser automation was unavailable during implementation, so the HTTP contract and generated PNGs were checked, but interactive layout and gestures still need browser verification. Lint/hash checks are targeted guards rather than formal proofs.

The inspector has scalar values, exact plate/child identities, and the M5a layered material column. Scenario loading, full geological event histories, cascade windows, scientific terrain metrics, arbitrary-size stitched exports, and production cascade/oracle comparison gates arrive with their owning milestones. HTTP image requests currently cap each dimension at 1024; core tiles cap at 2048; finite hydrology API requests cap each dimension at 256. There is no substituted external oracle or claim of global drainage correctness.

To intentionally change every historical golden output, run `gallery all`, inspect the candidate PNGs, then explicitly update the corresponding reference PNGs and `.properties` manifest under `harness/golden/m0`, `m1`, `m2a`, or `m3a` in the same reviewed change as the generator update. The gallery command never changes these baselines automatically. Prior milestones' references remain independent of added fields.

## Next

Open [Rivers + lakes in the tectonic viewer](http://localhost:8787/tectonics.html?layer=riverMap): sparse plates, about 30% land, eroded relief, terrain-driven climate, material drainage, flat fine lake masks and discharge-sized curved channels, with 46 top-bar fields. Follow the [active roadmap](docs/Active-Roadmap.md) for calibration, fine-descent proof, storage/groundwater, sediment and remaining morphodynamics. This remains a Java 21 experiment; the adapter prototype lives under `mc-adapter/`. [Session handoff](docs/Session-Handoff.md) preserves current context.
