# Genesis session handoff

Checkpoint: 2026-09-08, `genesis-m5a-v1`. Read this first after a context reset; inspect `git status` and the current commit/CI before making changes. This note preserves project context, not a claim that the conversation runtime was compacted.

## Latest direction and research checkpoint

The user requested an algorithmic research pause, then explicitly asked to save it in `./research` and continue with an updated roadmap. Research is in `research/2026-09-08-coherent-world-generation.md`; **`docs/Active-Roadmap.md` is now authoritative**. The original roadmap is preserved with a supersession notice. Earlier geology-first next-work preferences below are historical.

R0 is complete. R1a adds Java21-only `oracle/BoundaryFlowSummary` and `BoundarySummaryGates`: lossless single-receiver finite DAG elimination into exact boundary transfers, nested coarsening, stable original edge-source identities, multiple exits and region re-entry. Complete upstream graph is an input, not discovered cheaply. 400 seeded independent source-walk comparisons pass; spatial fixture reduces 384 nodes to 105 with 1,608 exact runoff units. Worst-case alternating-owner graph retains 256/256. Reports: `build/boundary-summary.json`, inspected `build/gallery/boundary-summary.png`. No new dependencies, production fields, Params, version change, viewer changes or historical golden updates.

R1b is now implemented in Java21-only `oracle/ConservativeRunoff` and `ConservativeRunoffGates`. Supplied finite cell forest + unique coordinates + nonnegative reference preferences + one net-runoff budget per terminal; precomputed subtree weights; top-down exact largest-remainder allocation along the queried ancestor chain. Each upstream catchment partitions into the local cell and immediate tributary subtrees. BigInteger protects preference sums/products; results are immutable longs. 500 allocation cases and 200 forests, independent source/preference walks, R1a composition, dry/extreme/invalid/cold/spatial/order/concurrency pass. Spatial fixture: 384 cells, 1,000,003 root units for seeds 42/-1, max 22 splits/73 slots; 1024-chain requires 1024 splits/2047 slots, star root 1024 slots. Inspected image: `build/gallery/conservative-runoff.png`; report: `build/conservative-runoff.json` (CI artifact). See `research/conservative-runoff-experiment.md`.

Important R1b limitations: full finite preprocessing still required, drainage-tree depth can be linear, input topology is fixed rather than a proven world refinement, and largest-remainder allocation is not budget-monotone (tested 25->26 unit counterexample). Choose a monotone alternative or explicitly bounded quantization before physical climate coupling. No new production fields, parameters, version, viewer layers or old goldens.

R2a now adds Java21-only `MaritimeEnvelope` and `MaritimeEnvelopeGates`. One middle-half-jittered site per S cell; exact nearest Voronoi owner from 25 sites; ocean reserve if a +/-m box fails strict owner clearance (`gap <= 2*m*siteL1`). S divisible64 in [64,2^20], m in [1,S/8]. Larger 9x9 BigInteger/corner reference: 1280 cases plus bounds/interior/extreme/cold/concurrent checks. A domain lies in its address-centered 3S box; it is not a watershed identity. See `research/root-terminal-design.md` for derivations and the terrain-first/constructive candidate comparison. Continuous ocean-reserve reasoning is not claimed as completed discrete ocean/terminal classification.

The seed42 current-continent audit at S524288, step8192, 256^2 samples has 17626 land points and 1040 cross-owner land edges. Collars 8192/16384/32768 remove 935/1851/3608 land samples respectively and eliminate cross-owner candidate-land edges. Inspected image `build/gallery/maritime-envelope.png` shows visible cuts through land. **Do not adopt the late-clipped mask**; it is a cost baseline, not a production fix. `build/maritime-envelope.json` is a CI artifact. No core fields/version/Params/viewer or old goldens changed.

Next: R2b compare continents authored with maritime reservations known from the start, multiple seeds and high-coverage/merged layouts, and complete interior-water/terminal policies. R2 remains in progress; bounded terrain-first solving is the proposed first R3 baseline only after geography acceptance, with constructive watershed generation still an alternative. Do not confuse the 25-site lookup with full cold watershed cost. Full R4-R8 geology/climate/surface acceptance precedes Minecraft R9.

R2b screening additionally compares raw scaffold, clipped raw scaffold, and whole-object admission (all disk bounding-square corners safely inside one convex domain). Three seeds x coverages45/100, grid128^2 step16384, same domain spacing/collar. Whole-object policy retains only about half or less of land; high-coverage seed42 largest sampled D8 component is 7702 original, 580 clipped, 294 admitted. Both retrofit policies are rejected, not promoted. These raw counts differ from the prior interpolated-coast audit. Reports/images: `build/maritime-object-admission.json`, inspected `build/gallery/maritime-object-admission.png`. Crop-touching component counts are recorded, not global-continent claims. Next work still needs authored geography or a better separation of continent-scale and watershed-scale commitments; no automatic one-island-per-root fix.

## User requirements

- Minecraft 1.7.10 / GTNH eventually, raw generation first. Follow `docs/Active-Roadmap.md`; preserve the historical `Dev-Roadmap.md` and concept notes. Do not overwrite this repository's Git metadata.
- Infinite, non-repeating plane; deterministic regardless of generation order, crop or zoom. Numeric support ±2^40 is not a planet edge or terminal.
- Continental landmasses, not noise as the sole organizing structure. Placeholder relief is acceptable while larger systems are developed.
- All field controls in the viewer's **top bar**. Dedicated continental viewer requested: actual terrain plus all relevant coast/drainage fields wired to continents.
- Generate diagnostic images from code and inspect them. The old JS prototype is unavailable, not a dependency; do not search for it again. Browser setup is not a blocker.
- Git commits/push authorized. Origin is `https://github.com/xXseesXx/Genesis`, branch main. No force push. No subagents unless explicitly authorized by user/instructions.

## Run and validation

PowerShell cwd: `C:\Users\fabib\Documents\Minecraft\1.7.10\Genesis`.

- `./genesis.ps1 test`: Java 8 core, Java 21 harness/oracle, all Java gates; about 25 seconds locally at this checkpoint.
- `./genesis.ps1 serve`: loopback `http://127.0.0.1:8787/`. Check the listening process before restarting; do not kill unrelated Java processes.
- `./genesis.ps1 gallery`: code-generated candidate images/hashes; does not replace golden baselines.
- Optional `node harness/gates/viewer-contracts.cjs` against the running server: minimal-DOM application-logic tests with real HTTP, not browser layout QA. Node is not required for the Java-only test command.
- CI `.github/workflows/core.yml`: Windows + Ubuntu, JDK21, uploads gallery, core JAR and timings. v4 actions emit deprecation warnings but have passed; upgrading them is separate maintenance.
- `build/` is ignored. Use `apply_patch` for source/text edits. Preserve unrelated user changes.

## Current implementation

`Generator` is the composition root. Two-argument construction is LEGACY; third argument `Generator.Model.CONTINENTAL` selects the integrated continent model. Model + seed + full Params + version define a world. FieldRegistry/TileCache preserve exact typed values; Long identities are JSON strings. 32 scalar fields, one structured column field, 42 numeric parameters. Noise/plate topology remain upstream inputs in both models.

Latest user direction: do independent/easier milestones before returning to water where possible. Implemented M5a geology groundwork, not full M5. `geology/Stratigraphy` consumes registered BASE_ELEVATION/CRUST_TYPE/CRUST_AGE; independent Q4096 seed-domain broad deformation translates three contacts D-3t, D-t, D. Basement basalt/granite, shale (2t), limestone (t), sandstone above; outer units are unbounded material-volume intervals clipped by terrain. Contact ties belong to upper unit. Surface=floor(current uncarved height). Synthetic ages crustAge*(4-index)/4, not historical simulation. Four geometry parameters + ten relative material coefficients. Existing terrain/water unchanged, verified by counterfactuals. Do not feed final eroded exposure back into its own routing/incision inputs. See docs/Stratigraphy.md.

New scalar fields: rockType, rockHardness, rockWeatherability, formationAge, strataDisplacement. `Generator.columns` is a separate typed FieldRegistry with Fields.ROCK_COLUMN (immutable fields/RockColumn DTO); get/values support structured data, numeric tile rejects it. `/api/sample` appends structured column; both inspectors show rock intervals/age/surface marker. All scalar layers remain top-bar controls. Code-generated geology-strata.png inspected (three maps + terrain-clipped section). Eight new column/scalar hashes in geology.properties; continental-world.properties gains new default params only, old hashes unchanged. GeologyGates includes contact/quantization/thickness/age/coefficients, structured cold/bulk/zoom/concurrency, no terrain/water feedback. Budget includes continentalGeology512MedianMs. Full M5 faults/intrusions/event histories/orientation, M6 erosion/scenario loader still absent.

Continental flow: immutable `ContinentalScaffold` → registered raw integer support → `CoastTopology` optional registered score input → fixed coarse triangulation → shared mask/continentality/elevation/bounded drainage → `CoarseChannels`/`BoundaryPorts`. In continental mode the candidate mask aliases committed seaMask, not the un-interpolated raw shape. The raw support remains a clearly labeled diagnostic.

Continental height = coast score * landHeight + coast-tapered tectonic uplift + coast-tapered squared ridged-noise detail (`terrainDetailHeight`, default 900). Those last two terms are exposed separately. Water has bathymetry and zero land-relief terms. This is NOT hydrology-conditioned carving. Legacy field outputs and all 160 historical PNG baselines are unchanged.

The five-lobe scaffold uses connected, seeded macro objects, exact lattice identities, bounded 3x3 support and a bounded immutable-object cache. Objects can merge. Neither the raw union nor its coarse sampled coast establishes globally unique basin identities, global ocean connectivity, or finite upstream dependencies. `docs/Continental-Scaffold.md` contains bounds, exact formulas and limitations.

Routing still searches a bounded Manhattan radius for a sea anchor; rank/direction/distance are -1 when unresolved. Resolved paths strictly decrease rank, but terminals are sea-level anchors, not proven ocean mouths. Guides may cross uphill terrain and are not carved rivers. Never replace unresolved cases with canvas-edge outlets.

`CoarseRunoff` now consumes registered ranks/directions, with exact reverse accumulation of one unit per resolved land anchor; sea contributes zero. Three new fields: `coarseRunoff` (-1 unknown), `coarseRunoffStatus` (-1 unknown / 0 closed under current routing / 1 open frontier), `coarseChannelFlow` (nearest guide's source transfer, zero absent). Unknown adjacent routing and numeric support cuts propagate an open flag downstream. Reverse rank increases bound ancestry to a diamond of radius R-r, max 2,113 anchors, depth 33; per-instance 4,096-entry synchronized LRU. This does not bound the future cascade or solve world roots. `CoarseChannels.channelFlow` carries identical source runoff on both sides of ports, not the destination's total. Renderer uses display-only flow-weighted stroke width, no physical-width/bed claims. All new controls are in both viewers' top bars. See `docs/World-Runoff.md`.

`DrainageRefinement` is a pure four-child kernel: inherited canonical ports, exact parent inflow/local rain/outflow ledger, integer shortest-path resistance, canonical rain remainder splitting and exact accumulation. Tests include exhaustive four-node paths, adjacent parents, composed subdivision, overflow and concurrency. Still lacks world root hierarchy, full corridor H4 enforcement, weighted climate rainfall and physical channel profiles. `refinement.html` is explicitly a synthetic fixture.

The independent Java21 `FiniteHydrology` oracle is finite priority-flood D8 routing, water components and exact flux/window ledgers. `hydrology.html` supports legacy or continental terrain; model survives incoming links, requests and exports. Connected-water mode is conditional on that sampled finite region, not global ocean truth. Oracle code is excluded from the Java8 core JAR.

## Viewer surfaces

- `/`: original field laboratory, legacy model. Continental candidate controls moved off this page; link opens the dedicated page.
- `/continents.html`: separate HTML, shared `app.js`/`style.css`, fixed `data-world=continental`. Top-bar layers, actual height by default, continental overview, pan/zoom, A/B, exact inspector, PNG/config exports. Inapp requests assert returned model. Legacy-only coast controls hidden; tectonic/crust diagnostics explicitly distinguished from land masks.
- `/hydrology.html?model=continental`: finite fill/catchment/runoff analysis of continental heights and mask. Model selector visible.
- `/refinement.html`: independent parent-ledger fixture, not derived from continental terrain.

Relevant code images inspected: `continental-terrain-overview.png`, `continental-terrain-regional.png`, `continental-world-layers.png`. Existing reference/canonical/refinement diagnostics remain available. Do not claim real-browser gesture/layout checks from the minimal-DOM smoke test.

Runoff image inspected: `build/gallery/continental-runoff.png`, seed -1, x=-131072, z=-122880, step256. Four same-coordinate panels show terrain+flow, accumulated units, completeness, and flow guides. It makes excessive parallel coast-directed routes visible; real basin organization and downhill channel conditioning remain next work, not solved by displaying discharge.

## Gates and continuation

New `ContinentalWorldGates`: coast/height/mask agreement, relief independence of coast, shape coverage propagation through drainage and guides, descending routes to continental sea, all-field cold crops/zoom, edge coordinates/nonstandard spacing, evicted concurrent relief queries, eight exact multi-field hashes (`continental-world.properties`). Raw scaffold has its own eight hashes; old PNG baselines unchanged. HTTP tests compare continental render/sample/finite analysis to direct core outputs and reject unknown model values. Timings include continental terrain and guides/ports separately (roughly 100/340 ms locally for 512²).

`RunoffGates`: independent source-forward reference in both models/three seeds; exact arbitrary-window rain+inflow=outflow+terminal+unresolved accounting; maximal dependency diamonds (R32: 10,565 rank reads), water/invalid graph/numeric-edge fixtures; radius-extension stability; source-vs-destination crossing flow; 5,000 shuffled concurrent/evicted odd-spacing points. Existing all-field cold crop/zoom gates now cover 27 fields. HTTP and optional live viewer-logic tests include all three new layers. `build/budget.json` adds continentalRunoff512MedianMs and continentalRivers512MedianMs. Historical world/continent/refinement goldens unchanged.

Remaining before the original M9 scope: infinite-world root/ocean contract and full hydrology cascade; channel/valley terrain conditioning; geological events/stratigraphy/folds/faults; differential erosion and scenario loading; climate; sediment/soil/vegetation; integrated correctness/morphology/performance acceptance. Introduce coarse hardness/rainfall inputs before final dependent commitments to avoid circular generation. Uniform inputs and explicit fixtures are valid early stages.

Latest next-work preference: do independently useful geology faults/intrusions or explicit input fixtures/scenario loading before returning to water where practical. M5a is only a material-volume foundation. When returning to water: multilevel refinement under explicit parent contracts, then downhill channel-bed/valley primitives on current placeholder relief. Continents' visual polish can wait, but global hydrology cannot be called complete without constructive terminal/root commitments. Do not infer that a field interface makes arbitrary noise a valid drainage-root provider. Full M6 erosion, M7 rainfall coupling and M8 sediment still have unfinished water dependencies.

When integrating Minecraft later, import the GTNH ExampleMod starter under `mc-adapter/` without replacing Genesis Git/root files. Caves, ores, full ecology and structures remain parked beyond initial realization.
