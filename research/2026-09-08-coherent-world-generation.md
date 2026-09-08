# Coherent generation on an infinite plane

Date: 2026-09-08. Status: researched design direction, not a completed algorithm.

## Decision in brief

Generate landscape and drainage together. Use canonical hydrological systems with bounded coarse computations, exact water budgets, and drainage-preserving refinement. Keep arbitrary terrain-first hydrology as a finite reference and morphology comparison.

The target remains an infinite, non-repeating plane with generation-order-independent queries. Finite canonical computational regions are allowed; a finite world, repeating planet, viewport outlets, or numeric-coordinate-edge sinks are not. A maximum hydrological root scale is a proposed model restriction to evaluate, not a proven property of the current continent scaffold.

Aim for **exact consistency within a deliberately chosen procedural model**, with physically informed approximations where full Earth simulation is impractical. None of the reviewed papers supplies all of arbitrary continents, exact discharge, infinite extent, plausible erosion, and bounded cold-query work in one ready-made algorithm.

## 1. Why upstream context matters

For fixed nondivergent routing without storage or transmission losses:

`Q(v) = r(v) + sum(Q(u) for p(u) = v) = sum(r(u) over the upstream catchment of v)`.

Discharge does not require every upstream detail in memory, but it requires every contribution to be represented. Two arbitrary landscapes can agree on every point examined by a bounded local query while differing in an unexamined distant tributary or rainfall source. Their correct discharge differs. This is an indistinguishability argument for arbitrary input fields, not a lower-bound theorem against every structured finite-seed generator.

A seed guarantees reproducibility, not a cheap exact sum of arbitrary pseudorandom samples. The escape routes are computation of the required context, precomputed summaries, analytically aggregatable sources, or a constructed budget model that conditions fine detail.

| Property | Meaning | Does not imply |
| --- | --- | --- |
| Determinism | Same immutable inputs give the same result | Locality or physical correctness |
| Local evaluation | Limited surrounding information | Complete upstream contributions |
| Bounded work | Known worst-case computational limit | Natural-looking basins |
| Hydrological consistency | Routing and water accounting agree | Cheap arbitrary-terrain queries |

An infinite plane can contain infinitely many finite catchments. However, arbitrarily large finite catchments do not imply bounded cold-query work. Almost-sure finiteness or an expected cost is not a guarantee for all seeds and coordinates.

An infinite acyclic graph can have a downstream path with no terminal. Strict descent of a real potential can continue forever. A decreasing nonnegative integer rank proves finite downstream paths from individual nodes, but not a uniform path bound or finite upstream catchments. With an infinite number of upstream cells each contributing at least a fixed positive amount and no losses, discharge diverges. Any allowed infinite catchment needs an explicit finite-sum/loss model.

## 2. Algorithmic alternatives

| Direction | Main advantage | Restriction or risk |
| --- | --- | --- |
| Terrain-first global drainage and erosion | Strong coupling; basins emerge from terrain | Complete finite domain or potentially unbounded dependency exploration |
| Lazy upstream discovery with memoization | Preserves independently defined terrain | Cold work is generally unbounded; cache eviction cannot change answers |
| Canonical finite hydrological regions across the plane | Bounded coarse coordination is practical | Must construct compatible divides, terminals, and root ownership |
| Constructive nested watershed hierarchy | Conserved budgets and potentially cheap refinement | Spatial embedding, root lookup, topology, and complete upstream decomposition need proof |
| Local dendritic/erosion-style patterns | Useful fast geometric detail | Pattern plausibility does not establish runoff or basin completeness |

Recommended investigation: hybrid canonical regions plus constructive refinement, with bounded physical or constrained optimization stages inside coarse regions. This restricts the world model intentionally. Do not camouflage an arbitrary tile boundary with noise and call its hydrology natural.

## 3. Relevant computational science

### Boundary transfer summaries

Barnes computes local tile flow and perimeter connections, resolves a global inter-tile dependency graph, and propagates contributions back into tiles. This accelerates finite large-scale accumulation without eliminating the need to resolve external dependencies. [Barnes 2017, Parallel Non-divergent Flow Accumulation](https://arxiv.org/abs/1608.04431)

For fixed routing, a region can be represented as `q_out = T q_in + b`: incoming boundary flow is transferred to specified exits, and `b` contains internally generated runoff exactly once. In a lossless single-receiver model, transfers are 0/1. Region summaries compose, analogous to eliminating interior unknowns in a larger system.

Genesis proposal: represent this behavior explicitly and test it independently of terrain generation. Crossings need canonical directed-edge identities. A river may cross the same region boundary repeatedly; the region adjacency graph can have a cycle even when the underlying drainage graph is acyclic. Preserve port-level connectivity instead of replacing an entire region with one node/outlet.

Limitations: arbitrary boundaries can have many crossings, so summaries are not generally constant-sized. Exact composition does not generate missing root inflows. Lake thresholds, transient storage, sediment transport, and nonlinear losses may require richer summaries rather than one affine map.

### Conservative rainfall and runoff cascades

Microcanonical rainfall cascades split a parent amount between children while exactly preserving its total. Canonical cascades preserve only an ensemble average. This is an established statistical downscaling principle, not an infinite watershed algorithm. [Schleiss 2020, Discrete multiplicative rainfall cascades](https://hess.copernicus.org/articles/24/3699/2020/)

Proposed adaptation: define an additive net-runoff measure on a hydrologically compatible partition, `M(B) = sum M(B_i)`. A basin contributing 100 units can allocate 30 to one tributary catchment, 50 to another, and 20 to direct lower-mainstem drainage. Later subdivision of the 30 into 12, 10, and 8 does not change the mouth's 100. New fine tributaries use previously allocated water; they do not add water a second time.

Rainfall and net runoff are distinct. Evaporation, infiltration, and storage must be represented consistently before treating rainfall as discharge. Coarse climate can bias allocations; deterministic integer remainder rules preserve exact totals. Fine climate is conditioned on commitments rather than independent noise later integrated at enormous cost.

Important unresolved contract: an arbitrary watershed boundary cuts a spatial quadtree into potentially many fragments. A conservative rainfall quadtree alone does not make arbitrary basin integration cheap. Co-design the catchment partition and measure, or prove a bounded-cost exact aggregation. Root budgets still need an explicit upstream definition: finite coarse climate solve or an analytic/coarse input model, not circular dependence on final erosion.

### Hydrology-first terrain and local branching

Genevaux et al. construct hierarchical drainage networks and watersheds and build terrain around their structure over a supplied domain. This supports network-conditioned terrain, but supplies no infinite-plane root guarantee. [Genevaux et al. 2013](https://cgvlab.github.io/cgvlab/www/publications/Genevaux13ToG/)

Dendry demonstrates locally evaluable hierarchical branching patterns controlled by a scalar field. It is relevant for fine geometric structure, not proof of exact discharge, complete catchments, or ocean reachability. Its implementation also has geometric limitations such as possible unwanted crossings under strong perturbation. [Gaillard et al. 2019, Dendry](https://cgvlab.github.io/cgvlab/www/publications/Gaillard19I3D/)

Planet generation research similarly uses a finite coarse base followed by adaptive reproducible refinement. On-the-fly refinement is not the same as having no coarse global context. [Derzapf et al. 2011, River Networks for Instant Procedural Planets](https://doi.org/10.1111/j.1467-8659.2011.02052.x)

### Acyclic topology is not sufficient terrain geometry

Balister et al. characterize rooted trees realizable by drainage toward the lowest neighboring vertex on a finite graph: path obstacles can prevent realization. Lowering heights along selected tree edges alone does not guarantee that other available neighbor routes will not win. The theorem's graph assumptions matter; it is not automatically a theorem about every weighted D8 or continuous surface model. [Balister et al. 2018, River landscapes and optimal channel networks](https://pmc.ncbi.nlm.nih.gov/articles/PMC6042144/)

Genesis implication: valleys and watershed divides must be designed together. A descending channel can still leak into a steeper neighboring basin. Test the final terrain using independent drainage analysis with matched boundary conditions, distinguishing exact topology contracts from expected morphology differences.

### Uplift, material resistance, and channel profiles

The stream-power model has the form `dz/dt = U - K A^m S^n`, with uplift U, erodibility K, drainage area A, and channel slope S. FastScape provides an efficient implicit finite-domain method; it assumes a drainage structure and does not eliminate accumulation dependencies. With positive quantities, the simplified equilibrium relation is `S = (U / (K A^m))^(1/n)`. [Braun and Willett 2013, FastScape](https://doi.org/10.1016/j.geomorph.2012.10.008)

Use these relations to guide longitudinal profiles or a bounded evolution stage. They are not a claim that every river reaches equilibrium. Discharge-based variants require their own parameterization; do not silently substitute Q for area with unchanged units.

Graphics research combines tectonic uplift with fluvial erosion on a coarse domain, followed by terrain amplification. This is a useful pattern for the proposed coarse coupled stage. [Cordonnier et al. 2016](https://diglib.eg.org/items/13e52c36-0200-4652-aacf-17aa3098c5fd)

Optimal channel networks connect energy-related objectives to branching drainage structure. This motivates investigating tributary aggregation rather than independently sending every cell toward the nearest sea. [Rinaldo et al. 1992](https://doi.org/10.1029/92WR00801)

One experimental objective is `E = sum L_e Q_e^gamma`, `0 < gamma < 1`, plus terrain/geology constraints. The concave cost rewards shared routes. This length-weighted application is a Genesis proposal, not a universal physical law or a reason to seek an expensive global optimum. Natural forms need not correspond to the most globally optimized tree.

### Lake storage and climate context

Fill-Spill-Merge uses a depression hierarchy to model water filling, spilling, and merging across finite terrain. Real depressions should not all be discarded as errors. [Barnes et al. 2021](https://esurf.copernicus.org/articles/9/105/2021/)

Closed basins need water balance: persistent positive input requires evaporation, infiltration, increasing storage, or eventual overflow. First define a static reference mean/seasonal state, not full weather dynamics. Never invent a spill direction by hashing away unresolved topology.

Orographic precipitation models include uplift, moisture transport, and downwind evaporation. Climate therefore also has nonlocal context. [Smith and Barstad 2004](https://journals.ametsoc.org/abstract/journals/atsc/61/12/1520-0469_2004_061_1377_altoop_2.0.co_2.xml)

A finite atmospheric halo is an approximation or an explicit finite-support model, not automatically exact physics. A finite Fourier-based method is not itself an infinite-plane solution. Coarse climate establishes budgets; conditioned microclimate can refine them without changing committed continental flux.

## 4. Proposed coherent architecture

1. Seed and compact geological history define reference rock volumes, tectonic forcing, and continental structure.
2. A canonical coarse model establishes compatible relief, climate, hydrological roots, divides, and terminal outlets. Any internal feedback is a bounded, versioned calculation with canonical initial/boundary conditions and operation order.
3. Commit coarse drainage topology, longitudinal constraints, lake storage/spill contracts, and conservative runoff budgets.
4. Refine catchments and channels under inherited port identities, external-only inflows, locally owned runoff, divide constraints, and height envelopes.
5. Generate hillslopes and exposed geology around those constraints. Fine noise can perturb permissible detail, not introduce basin leaks or dams.
6. Compute sediment sources/transport/deposition, soils, and vegetation as consistent consumers. Sediment composition and storage require more than a water-discharge scalar.
7. Rasterize the same pure core into Minecraft blocks only after integrated standalone acceptance.

Reference geology must be available before erosion. Exposed geology is the intersection of that material volume with the resulting terrain. Avoid final elevation -> exposed hardness -> erosion -> final elevation recursion; handle intended feedback explicitly within a bounded stage.

A root contract must answer basin ownership, actual terminal kind, complete upstream accounting, and bounded lookup. Below sea level is not proof of ocean connectivity. Neither tectonic plates, overlapping continent objects, nor square cells are automatically hydrological roots. Multiple basins may belong to one continent. Large-scale artificial boundaries cannot be justified by merely warping their geometry.

Initial recommendation: evaluate an explicit maximum hydrological root-region scale to obtain a hard work bound. Do not claim it is implemented. A future alternative permitting unbounded finite basin sizes must state the cold-latency tradeoff. O(log R) queries are conditional on a bounded-size disjoint upstream decomposition, bounded port complexity, and bounded root lookup; none follows from Hack's exponent.

Minecraft player dams and dynamic fluids are mutable simulations separate from the stateless baseline generator.

## 5. Existing Genesis components and missing connections

Continents provide structural support; coastward ranks certify progress only where resolved; coarse runoff accounts exactly on that partial graph; canonical ports and four-child refinement demonstrate shared contracts; stratigraphy supplies reference material groundwork. These are useful components, not a completed shared generative model.

Current nearest-sea routing may favor many parallel coast-directed channels. More visual noise will not by itself create believable basin competition. The continental scaffold can merge objects and does not establish finite upstream systems or global ocean connectivity. The existing four-child kernel's crossing restrictions also need examination before generic multilevel use.

Viewport-border artifacts disappear only when viewport edges have no causal role in production generation. Finite oracle edge policies remain diagnostic inputs, never production terminals.

## 6. Next research acceptance experiment

Before adding more disconnected layers, compare (A) finite canonical coarse solving plus refinement and (B) constructive watershed/budget refinement under the same supplied forcing and terminal contracts.

First isolate the reusable boundary-transfer algebra. Compare exact compressed flow with an independent source-by-source full graph walk, including multiple exits, re-entry, confluences, uneven runoff, zero sources, nested partitions, and overflow. This tests a finite building block, not either complete candidate.

Then require:

- Complete root/terminal ownership, including closed-basin policy and a finite dependency certificate.
- Exact source ownership and conservation, including storage/loss terms where introduced.
- Channel-bed/water-surface profiles and actual surface drainage agreement; no cross-divide shortcuts.
- Cold/order/crop/zoom/cache invariance of the same canonical world.
- Geological counterfactuals: changing resistance or uplift affects resulting forms, not just colors.
- Morphology diagnostics: drainage density, basin sizes, tributary aggregation, longitudinal profiles, Horton/Strahler/Hack statistics with scale and uncertainty, not one universal hard exponent.
- Instrumented construction work, retained-port count, refinement depth, memory, and cold-query tails. Warm-cache speed is not a worst-case proof.

The active implementation sequence and completion status live in [Active-Roadmap.md](../docs/Active-Roadmap.md).
