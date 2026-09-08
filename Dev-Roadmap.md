# Genesis — Development Roadmap & Testing Harness Specification

Status: v1.0 · Scope: deterministic virtual-planet generator, developed fully outside Minecraft until M9.

---

## 0. Ground Rules (non-negotiable invariants)

These are enforced by CI from M0 onward. Every milestone below is tested against them.

**G1 — Purity.** Every world quantity is a pure function `f(seed, ...coords) -> value`. No mutable world state, no generation order, no cross-chunk side effects. Caches are allowed only as memoization of pure functions (cache hit ≡ cache miss, bit-for-bit).

**G2 — Coarse is canonical.** A coarser cascade level is never an *approximation* of a finer one. Fine levels are conditioned on coarse commitments (drainage corridors, plate boundaries, climate bands). A committed feature may only *sharpen* as resolution increases, never move.

**G3 — Field interface only.** Subsystems communicate exclusively through the typed field registry (`Field<T> get(FieldId, x, z)` / bulk tile variant). No subsystem reads another's internals. This is what keeps the causal architecture from rotting.

**G4 — One parameter registry.** Every magic number lives in a single `Params` registry (name, default, min/max, doc string), surfaced automatically as viewer sliders and serialized into golden-seed snapshots. A constant hard-coded in generator code is a CI lint failure.

**G5 — Integer/fixed determinism where it matters.** Hash core and routing decisions use integer math only. Float use is allowed for field values but never for tie-breaking or topology decisions (flow direction, plate assignment) — those go through hashed integer tie-breaks so results are platform-independent.

**Language:** Java (or Kotlin) for the core library — the prototype core *is* the production core; no port, no divergence risk. The harness viewer is a thin local web app talking to the core over HTTP. A small JS "oracle" implementation (already built, see §6) exists as an independent cross-check.

---

## 1. Repository Layout

```
genesis/
  core/                     # pure generator library (no MC deps)
    hash/                   # G5 hash core, lattice utils
    fields/                 # field registry + implementations
    tectonics/              # M1
    elevation/              # M2
    hydro/                  # M3 cascade + M4 conditioning
    geology/                # M5–M6
    climate/                # M7
    surface/                # M8 sediment/soil/vegetation
  oracle/                   # slow global reference implementations (test-only)
  harness/
    server/                 # local HTTP server exposing core
    viewer/                 # canvas web UI
    gates/                  # CI gate runners
    scenarios/              # hand-authored tectonic/geology inputs (M6)
    golden/                 # golden seed snapshots + renders
  mc-adapter/               # M9 only; the ONLY module with Minecraft deps
```

---

## 2. Testing Harness Specification

The harness is the primary product until M9. Build it before building terrain.

### 2.1 Viewer (interactive)

- Pan/zoom map canvas, world coords, 3 zoom regimes (planetary → regional → block-scale).
- **Layer system:** any registered field renders as a layer (auto colormap by type); toggles + opacity; overlay compositing (e.g. rivers over hillshade).
- **Column inspector:** click any point → every field value at that column, plus the stratigraphic column (M5+) and the cascade window contents per level (M3+) for debugging routing.
- **Live params:** every `Params` entry is a slider; change → invalidate caches → re-render viewport. Target < 1 s re-render at 512² viewport.
- **A/B mode:** two seeds or two param sets side-by-side, synced pan/zoom.
- **Scenario loader:** load a hand-authored input file that overrides tectonic/geologic fields (M6).
- PNG batch export: any layer stack, any region, any resolution (feeds golden gallery).

### 2.2 CI Gates (run on every commit to `core/`)

| Gate | What it does | Pass criterion |
|---|---|---|
| **DET** Determinism | Generate 64×64-chunk region three ways: raster order, random order, cold-cache per-chunk isolation. Hash all field tiles. | All three hashes identical, per field |
| **BUD** Budget | Cold + warm chunk query benchmark, all fields needed for realization | Warm ≤ 1 ms/chunk core-side; cold ≤ 25 ms (tune per M9 target hardware) |
| **GOLD** Golden seeds | Render fixed gallery (8 seeds × standard layer stacks) | Pixel-diff vs. stored renders; any diff requires explicit snapshot update in the PR |
| **ORACLE** Cross-check | Compare cascade outputs vs. slow global oracle on 512² test regions (M3+) | Topology match: ≥ 99.5 % of river pixels within 1 corridor width; flux within 5 % at mouths |
| **HYD** Hydrology invariants | See §2.3 | All invariants hold |
| **LINT** Ground rules | No hard-coded constants, no cross-subsystem imports, no float tie-breaks | Zero violations |

### 2.3 Hydrology invariant suite (M3–M4, the novel-tech safety net)

- **H1 Reachability:** every land drainage node reaches ocean in ≤ K steps at its level (no orphan flow).
- **H2 No cycles:** routing graph is a forest, verified per test window.
- **H3 Flux conservation:** flux out of a window = rainfall in + boundary inflow (exact, integer flux units).
- **H4 Corridor stability:** for each committed coarse river, finer levels stay inside the corridor (max deviation ≤ corridor half-width).
- **H5 Hack exponent:** measured over ≥ 200 basins per test seed, fitted h ∈ [0.48, 0.58] (we *design* for compact basins — this is what makes far-field info O(log R), so it's a gate, not a curiosity).
- **H6 Monotone drainage potential:** Φ strictly decreases along every routed path (guarantees H1/H2 by construction; tested anyway).
- **V1 Valley/river duality (M4):** every river pixel lies in locally concave terrain; every carved valley contains its river within corridor width.

### 2.4 Metrics dashboard (rendered per golden seed)

Land fraction · hypsometric curve · drainage density · basin-area distribution (should be power-law) · river-length vs. basin-area scatter + Hack fit · relief distribution · (M7+) precipitation vs. elevation correlation, rain-shadow contrast ratio.

---

## 3. Milestones

Effort assumes one focused developer. Each milestone ends with its gates green in CI — "done" is a CI state, not a feeling.

### M0 — Skeleton + Harness Core (1–2 weeks)
**Build:** hash core (`hash64(seed, level, i, j)`, splittable streams), lattice/window utils, field registry, `Params` registry, harness server + viewer with one dummy fbm field, gates DET/BUD/GOLD/LINT wired.
**Tests:** DET green on fbm; hash avalanche test (bit-flip independence χ²); viewer renders 512² < 1 s.
**Exit:** you can pan around procedural noise with live sliders and CI is enforcing purity.

### M1 — Tectonics (1–2 weeks)
**Build:** hierarchical jittered-Voronoi plates (2 scales: plates → sub-plates), per-plate velocity, boundary classification (convergent / divergent / transform) from relative velocity, fields: `PlateId, PlateVelocity, BoundaryType, BoundaryDistance, Uplift, CrustType, CrustAge`.
**Tests:** DET; uplift is continuous across plate-cell borders (max gradient bound); boundary classification consistent from both sides (evaluate from each plate's frame → same type).
**Exit:** `plates` layer visually predicts `uplift` layer; golden renders committed.

### M2 — Macro Elevation + Drainage Potential (1 week)
**Build:** continentality field, base elevation = f(continentality, uplift, crust), sea level calibration (fixed calibration constant, *not* runtime global quantile), **distance-to-ocean field via the cascade** (coarse levels commit ocean topology; fine levels refine), drainage potential Φ = elevation + λ·distToOcean.
**Tests:** DET; Φ monotonicity sampling (H6 precursor): random walks along −∇Φ reach ocean in bounded steps; coastline stable across zoom (G2 check: coast may sharpen, never jump > 1 coarse cell).
**Exit:** plausible continents/coasts at planetary zoom; Φ layer shows no closed land basins above threshold size.

### M3 — Hydrology Cascade ★ the centerpiece (3–5 weeks)
**Build:**
1. Global **oracle**: D8 routing + flow accumulation on a 1024² grid (slow, exact, test-only).
2. Cascade: per-level jittered drainage nodes, 3×3 steepest-descent routing on Φ with hashed integer tie-breaks, integer flux accumulation, boundary-flux injection from level ℓ+1 into level ℓ windows, river extraction (flux ≥ τ) → corridor commitment, per-chunk query = W×W window per level.
3. Lakes as local decoration: hashed spill direction per coarse cell where Φ would pond.
**Tests:** full §2.3 suite; ORACLE gate; DET with cache on/off; BUD (this is where the budget lives or dies — target ≤ W²·log R node evals cold, O(W²) warm).
**Exit:** rivers render over hillshade at any zoom, sharpen without moving on zoom-in (record a zoom video for the golden gallery), Hack fit in band.

### M4 — Terrain Conditioned on Hydrology (2–3 weeks)
**Build:** the inversion — carve elevation to fit the committed network: valley cross-sections parameterized by flux (V→U shape), analytic incision depth ∝ flux^m·slope^n, hillslope field = f(distance-to-network, uplift), floodplain flats near high-flux channels. Ridged detail noise *perturbs* structure (never creates it).
**Tests:** V1 duality gate; DET; visual: no "river climbing a hill" artifacts in 20 random golden crops (checked by automated Φ-vs-flow-direction audit, not eyeballs).
**Exit:** terrain at regional zoom reads as *drained landscape*, not noise with blue lines.

### M5 — Geology: Events, Stratigraphy, Deformation (2–3 weeks)
**Build:** per-region compact event list (T0…Tn from doc §11), `column(x,z) → RockLayer[]` evaluated lazily, fold deformation (strain field tilts/undulates layer boundaries), fault objects (offset columns across fault plane), erosion-depth field from M4 → **exposed layer = column ∩ erosion depth**. Fields: `RockType, Hardness, Weatherability, LayerOrientation, FormationAge`.
**Tests:** DET; column continuity (adjacent columns share layer sequence except across faults); fault offset visible and consistent from both sides; exposed-rock map changes coherently across a fold (golden render).
**Exit:** column inspector shows believable stratigraphy anywhere; hillsides expose banded strata.

### M6 — Differential Erosion + Scenario Mode (2–3 weeks)
**Build:** one-way v2 coupling — coarse hardness modulates Φ and incision (hard rock → narrow valleys, soft → wide), hardness modulates hillslope amplitude and cliff formation (hard-over-soft → scarp/mesa primitive). **Scenario mode:** hand-authored override files for tectonic/geologic inputs.
**Tests (the doc's §39–42 as acceptance tests, scripted):**
- `scenario_zhangjiajie`: thick hard sandstone over soft shale + high uplift + high precip → tower/pillar field emerges (automated check: count of isolated high-relief columns > threshold).
- `scenario_batholith`: granite intrusion in soft sediment → exposed dome after erosion.
- `scenario_foldrange`: compression event → asymmetric ridges with banded exposure.
- DET + all M3/M4 gates still green (regression risk is high here — hardness feeding Φ must not break corridor stability H4).
**Exit:** all three scenarios pass automated checks and look right in golden renders.

### M7 — Climate (1–2 weeks)
**Build:** temperature = latitude-band field + lapse rate; **coarse 1D upwind moisture sweep** along prevailing-wind direction at regional cascade level (deterministic, windowed like hydrology); precipitation → weights hydrology rainfall term; snowline field.
**Tests:** DET; rain-shadow contrast ratio ≥ target behind M6 ranges (golden metric); precipitation-elevation correlation sign correct on windward vs. leeward flanks; river flux visibly responds to precip (wet side denser drainage).
**Exit:** climate layers causally coherent with terrain, not independent noise.

### M8 — Sediment, Soils, Vegetation Potential (2 weeks)
**Build:** sediment flux rides the drainage graph (erosion sources ∝ incision × weatherability), deposition where carrying capacity drops (slope break, basin entry, mouth) → alluvial fan / floodplain / delta primitives; `SoilDepth, SoilType, Moisture, VegetationPotential` as pure consumers.
**Tests:** DET; sediment conservation per window (H3 analog); fans present at ≥ 80 % of mountain-front canyon mouths in golden seeds; deltas at major river mouths.
**Exit:** lowlands read as depositional landscapes; soil/vegetation layers ready for ecology consumers.

### M9 — Minecraft Realization (2–4 weeks)
**Build:** `mc-adapter`: 16×16 column rasterization of fields → blocks (surface from exposed rock + soil + vegetation potential; water from flux/lake fields), concurrency-safe memo cache keyed by (level, i, j), integration into chunk generator, in-game profiling.
**Tests:** BUD on target hardware in-game (chunk gen ≥ vanilla-comparable throughput); DET in-game (regenerate same chunk from cold world → identical blocks); seam audit (generate world in two different chunk orders → identical region files, binary diff).
**Exit:** playable world. Everything before this milestone survives unchanged — the adapter is a renderer.

**Deliberately parked:** ML-assisted parameter search (doc §35–36) — revisit only after M6 exists. Caves, ores, ecology-full, structures — all consumers of the field registry, all post-M9.

---

## 4. Risk Register

| Risk | Trigger | Mitigation |
|---|---|---|
| Cascade seams (rivers disagree across windows) | H4/ORACLE red | Coarse-canonical enforcement; widen corridor margin; never let fine level re-route |
| Budget blowout | BUD red at M3/M9 | Reduce W; cache level ≥ 2 aggressively (shared by 4^ℓ chunks); upstream-cone culling |
| Hardness↔Φ feedback breaks hydrology | M6 regressions | Coupling is one-way and coarse-only; hardness enters Φ at level ≥ 2, never level 0 |
| Field-count explosion | BUD creep | Minimal spanning set (~10 fields) until a consumer demands more; LINT counts fields |
| Parameter hell | endless tuning | G4 registry + A/B viewer + scenario mode; tune in scenarios, verify in golden seeds |

---

## 5. Definition of Done (project-level)

A chunk anywhere in an infinite world, generated in any order, on any machine, is bit-identical; rivers, valleys, strata, climate and soils are mutually consistent consequences of committed coarse structure; total cost per chunk is O(W²) amortized; and the §39–42 scenarios emerge from causes, not special cases.

---

## 6. Already built (external oracle, v0)

A JS reference oracle implementing the M0–M3 slice globally (plates → uplift → elevation → Φ → D8 routing → flux → rivers) exists and produced the first rendered maps + measured metrics (land fraction, Hack exponent, determinism check). It lives outside the Java core and becomes the seed of `oracle/` — the cross-check the cascade is validated against in the ORACLE gate.
