# Continental scaffold candidate

The original coast is mostly a blend of plate crust and low-frequency variation. It makes many similarly sized land patches. This experiment introduces explicit, connected macro land objects before adding coastline detail. Since `genesis-m3a-v4`, the separate **continental world model** uses this scaffold to drive its elevation, coast, bounded drainage and guide geometry. The original/default world model remains unchanged.

Open `/continents.html`, or **Open continental terrain viewer** from the main page. It defaults to actual terrain elevation, with all layers in the top bar. A/B comparison, pan/zoom, inspection, exports and the finite hydrology link carry `model=continental`. The core constructor is `new Generator(seed, params, Generator.Model.CONTINENTAL)`; the two-argument constructor still selects `LEGACY`. Model is part of world identity along with seed, complete parameters and version, not a drawing option. Tile caches remain scoped to a complete generator instance.

## Connected field pipeline

`continentScaffold` (raw integer object support) → canonical coarse coast interpolation → `continentality`, `seaMask`, elevation and bounded drainage → guides and shared ports.

`CoastTopology` consumes the registered integer scaffold score at coarse vertices instead of the legacy crust/variation recipe. Its existing fixed world-space triangulation defines the committed shoreline. The raw scaffold layer is explicitly **pre-interpolation**, so its exact outline can differ from the committed mask. In continental mode `continentSeaMask` aliases the committed `seaMask`; it is hidden as a redundant viewer layer. The legacy API retains the original raw candidate mask for historical diagnostics. Coarse sampling is not a guarantee that every thin raw connection survives, especially at extreme spacing/scale combinations.

The height formula for continental land is:

`height = c * landHeight + tectonicRelief + terrainDetail`

`tectonicRelief = c * mountainHeight * positiveUplift / (1 + positiveUplift)`

`terrainDetail = c * terrainDetailHeight * ridges²`

Here `c` is the positive committed continentality and `ridges` is the existing normalized ridged noise. Water uses `c * oceanDepth`, with zero land-relief contributions. The two relief contributions are exposed as real metre-valued fields, not color effects. Changing noise or relief settings leaves the integer coast unchanged. This is provisional structural uplift plus noise detail, **not drainage-conditioned valley carving**.

The continental page hides the unused legacy `crustInfluence` and `seaThreshold` controls. Plate/crust layers remain upstream tectonic diagnostics, not aliases for land/sea. `continentalPercent` still controls plate crust statistics, not scaffold coverage. Shape controls use `continentCoverage`; roughness uses `terrainDetailHeight` and the existing noise controls. The refinement link remains an explicitly separate synthetic fixture, not a world-driven river simulation.

`/api/meta`, `/api/render`, `/api/sample`, and `/api/hydrology` accept `model=legacy|continental`; unknown models are rejected. Metadata, samples, render headers and finite-analysis JSON identify the model. The finite viewer has a model selector and retains incoming parameters. Its filling, catchment and runoff layers therefore operate on continental heights and masks when opened from the continental page, but its artificial-region boundary semantics are unchanged.

## Construction and contracts

Each object has an exact signed lattice address `(i,j)` and an independently hashed presence, location, orientation, bends and lobe radii. Object spacing is twice `continentScale`; centers jitter within the middle half of their address cells. Five disks form two bent, two-link arms attached to a central disk. The representation and eight Q1024 directions are versioned protocol choices, not mutable state. Individual objects can cross address-cell boundaries and merge; cell edges are not forced ocean channels.

All shape tuning is in `Params`: `continentScale`, `continentCoverage`, `continentLobeRadius`, `continentLobeVariation`, and `continentArmStep`. Coverage is an object probability, **not a requested land percentage**. These candidate-only controls do not change old field values. The legacy coast also uses `continentScale`, but for a different construction. Noise, plate spacing and relief settings do not affect the candidate.

Lobe scores are `max(-1000, (r*r - distanceSquared)*1000/(r*r))`, using signed integer division; the union takes their maximum. The thin score-zero band counts as water. Each link is at most 0.5 times the scale and each radius is at least 0.3 times the scale, leaving ample overlap even with block/score quantization. Thus each active object's land is connected. This does **not** prove that the union has bounded components or that all water belongs to one ocean.

The address lattice is an index, not a repeating map. Seed plus absolute address defines every object without sequential random draws. A fixed 3x3 neighborhood is enough: an omitted root center is at least 2.5 scales away along one axis; two arm steps extend at most 1 scale, and a lobe can contribute above the -1000 floor only within sqrt(2) * 0.65 scales. Their sum is below 1.92 scales. Integer rounding cannot close that margin at the minimum supported scale. Numeric query limits are not coastline terminals; nearby object centers are evaluated beyond them without clipping.

Before squaring, per-lobe queries reject coordinates more than twice the radius away on either axis. At the maximum scale, the remaining score products fit signed 64-bit integers. Exact objects are memoized in a bounded per-generator neighborhood cache; eviction only affects cost.

## Evidence and limits

Tests compare fixed 3x3 queries against 7x7 objects with independent BigInteger score arithmetic, covering five seeds and eight extreme parameter combinations, including non-step-aligned scales and numeric coordinate limits. They check overlap and sampled connected spines, zero coverage, independent crops, common-point zoom equivalence, shuffled queries, cache eviction and concurrent reads. Eight signed-integer grid hashes freeze the candidate recipe. The existing 160 PNG baselines remain unchanged; historical parameter manifests describe their own milestone, while the new manifest freezes candidate inputs.

The coast recipe still has visibly rounded lobes; adding real relief does not make it a finished natural coastline or simulated tectonic history. Discrete orientations, lattice statistics, merged components and ocean connectivity still need evaluation. Do not promote the scaffold directly to global basin roots: overlapping objects do not supply unique connected-component identities or guaranteed terminal paths.

The continental-world gate checks height composition, relief/coast independence, mask agreement, coverage propagation through terrain and drainage, rank descent to continental sea anchors, shared crossings, all 24 fields under independent crops and zoom, numeric limits and concurrent/evicted caches. Eight additional exact field-grid hashes freeze the connected model and all 28 parameters. API tests compare continental rendered PNGs, samples and finite analysis with direct core results. BUD measures both continental terrain and terrain with guides/ports at 512². All 160 legacy PNG references and the raw scaffold hashes remain unchanged.

Code-generated visual evidence: `build/gallery/continental-terrain-overview.png`, `continental-terrain-regional.png`, and `continental-world-layers.png`. These were inspected directly. Optional `node harness/gates/viewer-contracts.cjs` against a running server checks actual shared viewer logic with a minimal DOM and live HTTP requests: top-bar selection, A/B, inspection, exports, hydrology handoff, model identity, odd-step zoom and latest configuration. It does not claim real-browser layout/gesture QA and is not required by the Java-only gate runner.

Next: develop restrained, coast-preserving shape refinement and a root/terminal ownership rule for merged continental domains. Keep global drainage unresolved until that rule is explicit and testable. Then use the existing conditioned-refinement kernel to inherit those commitments, before terrain carving or Minecraft integration.
