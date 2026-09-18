# Fluvial hydrology revamp: bounded design and research basis

2026-09-18. Design status: **research-backed target with a tested second implementation slice; the larger geomorphology roadmap remains open**.

This note defines a practical target for rivers, lakes, channel shape, incision and bank collapse on Genesis tectonic terrain. The target is a deterministic static world-generation model that looks and behaves like a coherent drainage system without running a full fluid-and-sediment simulation. It extends, but does not replace the evidence record in [continental hydrology](continental-hydrology.md) and [continental hydraulic erosion](continental-erosion.md).

## Implemented checkpoint

The latest 2026-09-18 checkpoint composes `tectonic-terrain-v7`, `terrain-climate-v1`, `wind-field-v1`, `terrain-substrate-v1`, `continental-hydrology-v4`, `fluvial-network-v2`, `continental-erosion-v2`, `mass-wasting-v1`, and `genesis-columns-v5` as a deliberately bounded subset of this design:

- A global analytic stream function produces continuous, divergence-free wind with a nonzero speed floor. Twenty-four fixed moisture passes transport maritime humidity over pre-incision relief, producing windward enhancement and lee rain shadows. Five broad substrate classes then divide precipitation into exact annual surface-runoff, infiltration and evapotranspiration shares from permeability, soil depth, saturation and slope.
- One linear pass over the final drainage DAG derives contributing area and Strahler order. Same-level, face-connected filled depressions become stable lake components with one flat surface, spill/outlet metadata, and a signed component-indicator shoreline mask.
- Current and bankfull widths are scaled eightfold relative to the prior Minecraft realization. Routed discharge is unchanged and Manning depth/velocity are solved again, yielding broad channels without multiplying incision depth by eight.
- The effective surface-runoff ledger is converted to mean discharge under the explicit size-1 assumption that one horizontal block is one metre. A versioned 12-times-mean bankfull proxy, rectangular Manning solve, and continuity then yield distinct current/bankfull width, depth, and speed. These are useful physical diagnostics, not calibrated climate or flood-frequency predictions.
- Deterministic bounded centerlines classify reaches as cascade, straight, meandering, or braided. Curves inherit exact coarse endpoints; braided candidates use two equal-discharge threads that split and rejoin. Point queries inspect at most a fixed 5-by-5 segment neighborhood and 16 chords per candidate.
- Six implicit stream-power passes are followed by four or fewer conservative thermal-relaxation passes. Weak material has a lower proxy stable angle than resistant material. This is a hardness-based terrain approximation, not the toe-erosion, pore-pressure, cohesion, and joint mechanics specified later in this note.
- The final route and lake levels are solved after incision and mass wasting. Lake cells receive no fake minimum-slope hydraulic profile; incoming river water blends to the exact receiving-lake stage. The adapter carves the channel before testing lake open volume, so a deep inlet is back-filled correctly, while a fractional shoreline fringe is not excavated solely to force one voxel.
- The Minecraft realization performs a general final voxel-containment pass for ocean, lake and river water. A one-block world-coordinate halo makes every dry cardinal neighbor reach the adjacent integer water stage, including across independently generated chunk seams. This is a bounded depositional bank for vanilla fluid updates, not a change to coarse water identity or a block-resolution continental flood.
- The Minecraft adapter exposes rock/soil/drainage horizons and climate-selected biomes alongside the lake/channel objects. Computed velocity and direction are metadata: vanilla 1.7.10 source-water blocks do not reproduce physical current speed.

The focused gates independently check wind divergence/speed/continuity, orographic rain, exact ground-water partition, effective source conversion, area/order recurrence, `Q = A V`, discharge-monotone dimensions, flat face-connected lakes, inlet backwater, curve bounds, confluence interpolation, braided split/rejoin conservation, conservative slope movement, cache/query-order determinism, and adapter block palettes. Coarse D8 topology, uncalibrated annual/event runoff, rectangular sections, static eventual-overflow lakes, two-thread braids, absent sediment transport, no groundwater/karst and no in-game validation remain explicit limitations.

## How to read decisions in this note

Three labels prevent a plausible-looking number from becoming accidental scientific truth:

- **Evidence** means a published relation, measured tendency or established numerical method. It still has a domain of validity.
- **Existing baseline** means behavior already documented by the current Genesis experiments and gates as of this date. It is not necessarily the desired final behavior.
- **Genesis calibration** means a proposed deterministic approximation, threshold, cap or art-direction choice. It must be exposed in configuration, tested across seeds and changed when validation disagrees.

The equations below use SI units where a physical quantity is claimed. A value derived from model blocks or arbitrary rainfall units must remain explicitly dimensionless until the block-to-metre and climate mappings are supplied.

## Research-start inventory and defects

The useful **existing baseline** is substantial:

- a complete, bounded irregular continental-family work unit rather than a cropped viewport;
- explicit maritime terminals, priority-flood depression overflow, acyclic coarse receivers and an exact supplied/discharged/unresolved runoff ledger;
- immutable cached roots and deterministic output independent of visible crop;
- a deterministic, detachment-limited coarse incision experiment with rerouting between fixed passes.

The research-start inventory documented the following limits. Closed or narrowed items are marked explicitly; the rest remain acceptance targets:

- Coarse D8 receivers produce angular paths and flat-area tie bias. A blue receiver segment is not a resolved riverbed.
- **Narrowed:** production accumulation now uses terrain-aware precipitation times a material/slope runoff share, and the one-block/one-metre adapter convention converts it to mean cubic metres per second. Climate/runoff calibration and event frequency remain open; compatibility fixtures may still choose uniform all-surface runoff.
- **Closed for the rectangular proxy:** mean and 12-times-mean bankfull discharge now have separate width/depth/stage/velocity satisfying continuity. The bankfull multiplier is still uncalibrated and has no independent event ledger.
- Priority-filled elevation is a routing scratch surface. Treating it as terrain or a material-fill height would turn a lake volume into land.
- **Narrowed:** lakes remain eventual spill under sustained input and lack storage/evaporation/closed-basin equilibrium, but stable component/surface/spill/outlet records now exist. Infiltration is an explicit annual source partition, not a lake-volume loss solver.
- **Closed in JVM fixtures, open in game:** the adapter defects were traced to voxel rounding, evaluating lake wetness before channel carving, and leaving the whole-block water perimeter unsealed. Exact-sea, fractional shore, full natural zero-contour containment, solid wet subsurface and post-channel inlet fixtures now emit intended columns; GTNH fluid updates remain unvalidated.
- Fine riverbeds are not yet proven monotonically downhill to their committed spill or sea mouth, and coarse nearest-node shores can leak, dam or disconnect during rasterization.
- **Narrowed:** a deterministic proxy classifies cascade, straight, meandering and two-thread braided reaches. It is not evidence-calibrated and does not yet represent the complete step-pool/plane-bed/pool-riffle/anastomosing target.
- The incision prototype does not route sediment or deposit bars, floodplains, deltas or lake sediment.
- Bank collapse is not yet coupled to hydraulic toe erosion, pore pressure, cohesion, friction or jointed-rock geometry. A direct hardness-to-angle mapping is an artistic proxy, not a mechanics result.

Unclosed portions remain acceptance targets; passing the bounded checkpoint does not imply full Earth-like morphodynamics.

## Required outcomes

For the same seed and configuration, the design must produce:

1. one deterministic, complete drainage topology for each bounded continental root;
2. explicit lakes with flat water surfaces and valid outlets or an explicit closed/dry state;
3. mean discharge and bankfull/channel-forming discharge with declared units;
4. reach width, water depth and speed that satisfy continuity and a declared resistance law;
5. longitudinal profiles that descend to the already committed outlet;
6. channel form driven by slope, confinement, bank strength, discharge and a sediment-mobility proxy rather than noise alone;
7. erosion and bank failure that respond monotonically to water forcing and material resistance;
8. bounded work proportional to the coarse root plus the limited area of detailed channel corridors.

## Two-scale bounded architecture

Let \(N\) be the number of cells/vertices in the complete coarse continental root, and \(M\) the number of fine samples actually allocated inside river, lake-shore and bank-instability corridors. The central performance rule is \(M\) must not become the area of the entire continent at Minecraft resolution.

### Scale A: continental water graph

One immutable root solve owns:

- raw tectonic elevation and material fields;
- a separate routing elevation;
- maritime terminal components;
- a depression hierarchy with spill saddles and first-class lake/basin records;
- one or at most two downslope receiver weights per active node;
- accumulated mean runoff and channel-forming discharge;
- a reach graph split at sources, confluences, lake boundaries, slope/material changes and mouths;
- a coarse incision profile, followed by a small fixed number of reroute corrections.

Priority-Flood is an appropriate baseline depression solver. Its published complexity is \(O(N)\) for suitable integer grids and \(O(N\log N)\) for floating grids [R1]. Fill-Spill-Merge supplies the missing depression hierarchy and volume/spill bookkeeping without repeatedly filling every basin from scratch [R2]. If filling would erase a tectonically credible canyon or create an implausibly deep artificial lake, a constrained breach/fill hybrid is available, but breaching must not cut through a protected drainage divide [R3].

The existing D8 graph may remain as the first correctness reference. A D-infinity direction can later split diffuse hillslope flow between at most two adjacent receivers and reduce cardinal/diagonal bias [R4]. Once the channel-initiation criterion is crossed, contract that diffuse flow to one downstream channel receiver; only explicit braided, distributary or lake-outlet components may split channel flow, and every such component must conserve and normally rejoin its shares. Whichever routing is selected, the weights must be non-negative, sum to one and form an acyclic graph after flat resolution.

### Scale B: reach and corridor realization

Only committed reaches and lake shorelines receive fine geometry:

- a valley-following centerline and monotonically descending thalweg;
- cross-section parameters and water stage;
- reach morphology, bend wavelength and bar/step/pool phase;
- material-aware bed incision;
- bank toes, failure candidates and deposited failed volume;
- a deterministic rasterization recipe for Minecraft columns.

Fine realization may move a centerline inside its valley corridor, but it may not silently change the coarse source set, confluence ordering, lake spill, terminal or runoff ledger. If a fine path cannot connect those commitments without crossing a divide, the root is invalid and must fall back to the coarse path or rerun an explicitly bounded correction; it must not invent a chunk-edge outlet.

Reach identifiers, random phases and tie breaks derive from canonical world coordinates and the world/configuration seed. Chunk order, viewer crop and cache eviction therefore cannot change the result. Warm chunk rasterization consults cached immutable reach/lake data and must not trigger a new continent solve per chunk.

## Water quantities and units

### Mean runoff and discharge

For cell \(i\), let:

- \(\bar R_i\) be long-term effective runoff depth in millimetres per year after interception, evapotranspiration and infiltration;
- \(A_i\) be represented horizontal area in square metres;
- \(T_y=31\,557\,600\) seconds per mean year.

Then local mean source discharge is

\[
q_{\mathrm{mean},i}
=
\frac{10^{-3}\bar R_i A_i}{T_y}
\quad [\mathrm{m^3\,s^{-1}}],
\]

and topological accumulation is

\[
Q_{\mathrm{mean},i}
=q_{\mathrm{mean},i}
+\sum_{j\rightarrow i}w_{ji}Q_{\mathrm{mean},j}.
\]

The exact ledger remains

\[
\sum_i q_{\mathrm{mean},i}
=Q_{\mathrm{terminal}}+Q_{\mathrm{closed}}+Q_{\mathrm{unresolved}}.
\]

**Implemented subset of Genesis calibration G-H1:** size 1 declares one horizontal block as one metre, and `TerrainSubstrate` supplies explicit annual runoff/infiltration/evapotranspiration shares before routing. The exact integer effective-runoff ledger converts to mean \(\mathrm{m^3\,s^{-1}}\). The partition is not regionally calibrated, infiltrated water is not stored/returned, and compatibility fixture providers may still use model units. These limits must remain visible rather than hidden in a multiplier.

### Channel-forming or bankfull discharge

\(Q_{\mathrm{mean}}\) determines ordinary water availability and default stage. \(Q_{\mathrm{bf}}\) determines the channel-forming cross-section. They are not interchangeable. “Bankfull equals the 1.5-year flood” is a useful regional observation, not a universal law; USGS studies show recurrence and bankfull indicators vary by basin and method [R8, R9].

The target root therefore accepts a versioned channel-forming event-runoff field \(r_{\mathrm{bf},i}\,[\mathrm{m\,s^{-1}}]\). The v2 checkpoint does **not** implement this independent field; it retains a clearly named 12-times-mean proxy. The target local source and accumulation are

\[
q_{\mathrm{bf},i}=r_{\mathrm{bf},i}A_i,
\qquad
Q_{\mathrm{bf},i}
=q_{\mathrm{bf},i}
+\sum_{j\rightarrow i}w_{ji}Q_{\mathrm{bf},j}
\quad[\mathrm{m^3\,s^{-1}}].
\]

This is a steady design-event approximation, not a routed flood hydrograph; simultaneous accumulation can overstate a large basin peak. A regional relation such as \(Q_{\mathrm{bf}}=k_RA^\eta\) is useful for calibrating/checking selected outlets, with units embedded in \(k_R\), but independently overwriting every reach from that relation would break the source and confluence ledger.

**Genesis calibration G-H2:** use the conservative event field until regional attenuation or a travel-time model is calibrated, retain its return-period/event tag, and require \(Q_{\mathrm{bf}}\ge Q_{\mathrm{mean}}\) wherever a perennial channel is rendered.

Channel initiation should not use discharge alone. Field evidence relates channel heads to both contributing area and local slope [R10]. A practical candidate is \(A S^2\ge C_h\), but \(C_h\) is a regional/material **Genesis calibration**, with hysteresis to prevent single-cell channel flicker.

## Width, depth, stage and speed

Downstream hydraulic geometry is commonly written [R5, R6]

\[
W=aQ^b,\qquad D=cQ^f,\qquad V=kQ^m.
\]

Here \(Q\) is in \(\mathrm{m^3\,s^{-1}}\), \(W,D\) are in metres and \(V\) is in \(\mathrm{m\,s^{-1}}\); \(a,c,k\) therefore carry units that depend on their exponents. For a rectangular section, continuity requires \(Q=WDV\), \(ack=1\) and \(b+f+m=1\). For a shaped section, a shape factor enters the coefficient constraint. Published regional compilations make \(b\approx0.45\)–\(0.55\), \(f\approx0.30\)–\(0.40\) and \(m\approx0\)–\(0.20\) reasonable search ranges, not universal constants [R6]. Coefficients and exponents must be calibrated by channel family; changing discharge units or scaling exponents without consistent coefficients violates continuity.

The recommended implementation does not independently guess all three variables:

1. Set a bankfull top width \(W_{\mathrm{bf}}=a_WQ_{\mathrm{bf}}^b\).
2. Select a cross-section family from substrate/bank material: narrow V or step-pool in confined rock, trapezoid for stable single-thread alluvium, or shallow compound section for weak-bank/braided reaches.
3. Select a side slope \(z\), enforce \(W_{\mathrm{bf}}=B+2zD_{\mathrm{bf}}\), and solve bankfull depth from discharge and Manning resistance rather than applying unrelated depth noise. If the solution would make bottom width \(B\) non-positive, switch to the declared V/compound family instead of silently clamping it.
4. Hold the carved section fixed and solve the current water depth from \(Q_{\mathrm{mean}}\), or from a clearly named seasonal display discharge.
5. Compute velocity from continuity.

For a trapezoid with bottom width \(B\), side slope \(z\) horizontal per vertical and depth \(d\),

\[
A_c=d(B+zd),\quad
P_w=B+2d\sqrt{1+z^2},\quad
R_h=A_c/P_w.
\]

Manning's SI relation is [R7]

\[
V=\frac{1}{n}R_h^{2/3}S_e^{1/2},
\qquad
Q=A_cV,
\]

where \(n\) has units \(\mathrm{s\,m^{-1/3}}\), \(S_e\) is energy slope, \(R_h\) is hydraulic radius and \(A_c\) is wetted area. With top width, shape, \(n\) and positive reach-scale \(S_e\) fixed, solve the scalar bankfull depth by bisection; then freeze \(B,z,D_{\mathrm{bf}}\) and solve lower stages in that section. This guarantees continuity within tolerance. Use a smoothed water-surface/thalweg reach slope, not a noisy single-block terrain derivative. A zero or adverse reach-scale slope denotes a lake/backwater or an invalid profile; do not conceal it with an arbitrary positive slope floor.

USGS photographic guidance supports roughness search bands roughly \(n=0.025\)–\(0.055\) for many lowland natural channels and \(0.030\)–\(0.070\) for rough, rocky mountain channels [R7]. These are initialization bounds only. Wood, vegetation, boulders, ice, bars and floodplain inundation can move values outside them.

At confluences, incoming \(Q\) must be conserved before solving the downstream section. Width and depth should blend over several widths to avoid a one-block jump. Minecraft water blocks do not encode physical metres-per-second speed, so velocity is stored as reach metadata and may select surface/particle/audio treatment; vanilla fluid updates are not the hydrology solver.

## Lakes are water bodies, not filled terrain

Every depression selected as a visible lake needs a component record:

- stable basin/lake identifier and member cells;
- original bed elevation;
- spill saddle, outlet reach and downstream basin/terminal;
- a compact hypsometric curve \(A(h)\) and \(V(h)\);
- inflow ledgers for mean and channel-forming discharge;
- water level and state: dry, closed equilibrium, below-spill lake, or open/overflowing lake.

The routing fill surface is scratch data. It must never replace terrain elevation or choose a solid material. The visible lake surface is one constant quantized elevation \(h_L\) across the connected component. Terrain remains at its original or explicitly eroded bed below it.

For an open lake under the current eventual-overflow approximation, set \(h_L\) to the spill elevation and connect exactly one canonical outlet (or an explicitly represented tied saddle). For a closed or partly filled lake, solve the scalar steady budget

\[
0=Q_{\mathrm{in}}-Q_{\mathrm{out}}(h)-E\,A(h)-I(h)
\]

without simulating time. If no equilibrium exists below the spill, the lake becomes open and its excess enters the outlet. A later transient model may use \(dV/dt\), but is not required by this revamp.

Minecraft realization has its own mandatory checks:

- place actual water blocks from the lake bed's open volume through \(h_L\);
- keep lakebed sediment/rock below the water rather than substituting dirt for the water column;
- seal shoreline columns against one-block leaks while retaining the canonical outlet;
- use still-water state in the interior and deliberate downstream flow treatment at the outlet;
- connect outlet water to the first river cross-section with no dry plug or vertical step;
- prevent post-generation fluid updates from redefining the hydrological topology.

The reported “plain dirt lake” must get a minimal saved fixture containing basin ID, bed, spill, intended water level, emitted block palette and a vertical column assertion before broader lake work proceeds.

## Reach morphology and planform

No single slope-discharge line reliably separates straight, meandering and braided rivers. Bank strength, sediment calibre/supply, width-depth ratio, valley confinement and disturbance history matter [R12–R15]. Classification therefore occurs after hydraulics and uses a scored, hysteretic decision with an auditable reason code.

### Mountain-bed morphology

Montgomery and Buffington's field classification gives useful overlapping priors for mountain channels [R11]:

| Reach gradient \(S\) | Evidence-informed initial family | Important override |
| --- | --- | --- |
| \(S\gtrsim0.065\) | cascade | large immobile roughness and confinement |
| \(0.030\lesssim S<0.065\) | step-pool | wood/boulder supply and valley width |
| \(0.015\lesssim S<0.030\) | plane-bed | sediment mobility and forcing |
| \(S\lesssim0.015\) | pool-riffle/alluvial candidate | confinement, bank strength and planform |

The bands overlap in nature and are not biome-independent laws. **Genesis calibration G-P1:** blend morphology across a transition band rather than switch on one cell's slope. Cascades and step-pools require a net descending thalweg; their local bed oscillation may rise only below the upstream water surface and may never create a closed artificial dam.

### Single-thread, meandering, braided and anastomosing

Use these decision signals:

- **confined straight/sinuous:** valley width only a few channel widths, bedrock or high bank strength, or insufficient lateral accommodation;
- **meandering single-thread:** low-to-moderate gradient, unconfined floodplain, moderate cohesive/vegetated bank resistance, and mobile alluvium without overwhelming bed-material supply;
- **braided:** unconfined valley, weak or readily eroded banks, mobile bed, high coarse-sediment/supply proxy, variable discharge and a broad shallow section;
- **anastomosing/anabranching:** multiple persistent channels separated by stable floodplain islands, commonly requiring resistant banks and avulsion/floodplain history rather than merely “more braiding” [R15].

Width/depth ratio strongly controls bar mode in reduced models [R13], but it is not sufficient by itself. **Genesis calibration G-P2:** treat 40–60 only as an initial braid-warning band, require at least three agreeing signals—high width/depth, weak banks, high mobility/sediment proxy, unconfined valley or discharge variability—and use separate enter/leave thresholds. The classic slope-discharge divider [R12] is likewise a sanity plot, not the production classifier. Default to a single thread when evidence is ambiguous.

Persistent anastomosing requires floodplain accretion and avulsion timescales absent from the current model. It remains deferred; a multi-thread result produced only by lateral bars should be named braided, not anastomosing.

### Meander geometry

Alluvial observations make

\[
\lambda_m=C_\lambda W_{\mathrm{bf}},
\qquad
R_c=C_R W_{\mathrm{bf}}
\]

with \(C_\lambda\) initially around 10–14 and modal \(C_R\) around 2–3 useful starting ranges [R16]. Scatter is large. These ratios initialize a deterministic valley-constrained curve; they do not license a sine wave across ridges.

The fine solver should:

1. find a valley-axis corridor between committed reach endpoints;
2. seed bend phase from the stable reach ID;
3. relax curvature for a fixed small number of iterations while penalizing valley walls, excessive slope and self-intersection;
4. carve a deeper outer-bank thalweg and a shallower inner point-bar surface;
5. permit a neck cutoff only when both candidate legs remain descending and the abandoned loop becomes an explicit oxbow/depression object.

Local sinuosity must fall toward one in confined or steep reaches. Bend amplitude must taper at confluences, lake outlets and sea mouths so the fine path meets the committed node exactly.

## Longitudinal incision, sediment proxy and banks

### Incision

The stream-power family is a suitable bounded longitudinal model [R17]:

\[
E=K A^m S^n
\]

or, when physical discharge is available,

\[
E=K_Q\,[Q^\mu S^n-\Theta_c]_+ .
\]

\(E\) is bed lowering rate; the units of \(K\) depend on the exponents and input units. Published landscape studies commonly constrain the concavity ratio \(m/n\) to roughly 0.35–0.6, but lithology, climate, sediment cover and uplift alter it [R17]. The current Genesis pass count and coefficients remain calibration, not converted physical time.

Use the implicit FastScape formulation for the coarse receiver graph: it is linear-time per fixed-network solve and numerically stable for large time steps [R18]. Apply only a small fixed number of erosion/reroute corrections. Protect maritime levels and lake water surfaces; erode a lake bed only through a separately declared sediment/basin process.

Hydraulic diagnostics make the controls interpretable:

\[
\Omega=\rho gQS
\quad[\mathrm{W\,m^{-1}}],
\qquad
\omega=\frac{\rho gQS}{W}
\quad[\mathrm{W\,m^{-2}}],
\qquad
\tau_b=\rho gR_hS
\quad[\mathrm{Pa}].
\]

Harder or more massive rock lowers erodibility \(K\) and/or raises a threshold \(\Theta_c\). Sediment cover can either armor the bed or provide tools for abrasion; until an Exner solver exists, expose one bounded sediment-supply/mobility proxy and never report it as conserved sediment mass.

### Gravity-driven bank and hillslope failure

Loose sediment has a friction-controlled limiting slope, while intact or jointed rock can temporarily stand steeper because cohesion and structure matter. “Hard rock equals a larger angle of repose” is therefore insufficient.

For a candidate failure slab, an infinite-slope factor of safety is [R20]

\[
FS=
\frac{c'+(\gamma z\cos^2\beta-u)\tan\phi'}
{\gamma z\sin\beta\cos\beta},
\]

where \(c'\) is effective cohesion, \(\phi'\) friction angle, \(\gamma\) unit weight, \(z\) failure depth, \(\beta\) slope and \(u\) pore-water pressure. Failure is possible when \(FS<1\). Jointed rock additionally needs a structure/weak-plane modifier; apparent hardness alone cannot supply it.

Away from a discrete bank failure, nonlinear critical-slope diffusion provides a useful relaxation law [R19]:

\[
\mathbf q_s=
-D\frac{\nabla z}
{1-(|\nabla z|/S_c)^2}.
\]

The numerical implementation must limit the singularity near \(S_c\), conserve moved volume and deposit it downslope rather than delete it.

**Genesis calibration G-B1:** material families initially search friction angles of roughly 28–40 degrees for loose granular/alluvial material. Cohesive soil and rock use nonzero cohesion and joint/weak-plane modifiers, not an arbitrary angle up to vertical. Moisture/saturation raises pore pressure and reduces stability. All ranges require visual and invariance gates; they are not a global geotechnical database.

Bank evolution order is:

1. compute excess bed/bank shear or specific stream power at the bank toe;
2. erode/undercut a bounded toe volume;
3. recompute \(FS\) only for affected bank columns;
4. move failed volume to the toe/channel as a deposit;
5. update the local cross-section and, only if topology changes, run one bounded reach repair.

This follows the process separation used by the USDA Bank Stability and Toe Erosion Model, which couples hydraulic toe erosion to geotechnical bank failure [R21]. Work is restricted to the fine corridor and an active queue of changed cells.

## Performance contract and budgets

This table is the **future target budget**, not a description of the v2 checkpoint. The implemented climate runs 24 fixed moisture sweeps; erosion v2 retains six fixed incision passes and up to four gravity passes. Reducing those counts requires a versioned quality comparison.

| Stage | Complexity target | Initial Genesis work budget |
| --- | --- | --- |
| depression hierarchy / fill | \(O(N\log N)\) float, \(O(N)\) eligible integer/radix case | once per immutable root/configuration |
| receivers and two discharge accumulations | \(O(N)\) | one topological pass per field; at most two receiver weights |
| implicit coarse incision | \(O(N)\) per fixed graph | at most two erosion-reroute correction passes |
| lake hypsometry | \(O(N)\) build plus scalar level solves | one compact histogram/tree per basin; no time stepping |
| fine centerline/cross-sections | \(O(MI_p)\) | \(I_p\le8\) relaxation iterations |
| bank failure | proportional to changed corridor cells | at most four local sweeps per realization request |
| warm chunk emission | chunk columns plus intersecting reach/lake samples | no continental solve and no scan of all reaches |

The iteration counts are **Genesis calibration G-X1**, chosen to make the bound explicit; quality gates may lower them or justify a versioned increase. Every allocation is preflighted. When the fine-sample cap is exhausted, degrade deterministically in this order: omit secondary braid threads, reduce bar/bank detail, use the committed single centerline. Never crop the hydrological root or synthesize an outlet.

Retain the current viewer safeguards—12 cached roots per hydrology context, two viewer configurations, and a 20-family/4-million-support-vertex preflight. Compact climate/substrate state raises the retained estimate from 69.5 to about 80.5 bytes per support vertex, so the byte guard is 322,000,000 bytes; temporary climate arrays, raster/map allocations and JVM overhead are additional. These are viewer work/memory limits, not promises about chunk latency. The full-suite climate-root fixture measured about 0.47 seconds locally, but a pinned warm-chunk/memory benchmark remains open.

Acceptance benchmarking must pin seeds, terrain settings, hardware/runtime and report \(N\), \(M\), lake count, reach count, wall time, CPU time, peak retained memory, cache hit/miss and fallback count. On that pinned suite:

- the macro revamp median must initially stay within 2 times the current coarse median and p95 within 3 times it;
- warm chunk realization must perform zero \(O(N)\) work;
- no data-dependent convergence loop is allowed;
- allocations and counted work must not exceed the limits implied by declared \(N\) and \(M\) caps;
- output hashes must be independent of thread count, query order and cache eviction.

These are engineering budgets, not scientific evidence.

## Validation and acceptance invariants

### Topology and ledgers

- Every non-terminal wet node reaches a declared lake, closed basin or maritime terminal; no cycles or chunk-edge exits.
- Mean-flow and bankfull ledgers conserve their own sources independently, including weighted splits.
- A confluence has \(Q_{\mathrm{out}}=\sum Q_{\mathrm{in}}+q_{\mathrm{local}}\) within the declared numeric tolerance.
- Fine paths never cross a committed drainage divide and meet coarse sources, confluences, spills and mouths exactly.
- Zero effective runoff produces zero discharge and no flowing river, while leaving topographic basin diagnostics available.

### Hydraulics

- Every sampled section satisfies \(|Q-A_cV|/\max(Q,\epsilon)\) and the Manning residual within tolerance.
- Increasing discharge with all other inputs fixed cannot reduce solved stage; increasing roughness cannot increase velocity at fixed geometry/stage.
- Width/depth/velocity remain finite at low slope; dry reaches do not receive a numerical minimum river.
- Water surface and thalweg do not rise downstream except across a declared flat lake surface or a bounded sub-grid morphology perturbation below the hydraulic grade.

### Lakes and adapter blocks

- All cells of one lake component have exactly one water-surface level.
- Routing fill elevation never overwrites raw terrain or selects a solid block palette.
- Every open lake has a connected outlet; every closed lake has an explicit water/loss balance state.
- A lake-column fixture contains water in every intended open voxel, lakebed material below it, a sealed shoreline and no dirt plug at the outlet.
- Generation order, neighboring chunk presence and post-generation queries do not change the lake surface or outlet.

### Forms, erosion and failure

- Mountain morphology follows reach-scale slope bands continuously, without single-cell family flicker.
- Meander wavelength/radius statistics fall inside configured distributions and bends remain inside the valley corridor.
- Braided reaches conserve discharge over splits and rejoins; secondary channels cannot become isolated decorative water.
- Larger \(K\), \(Q\) or slope cannot reduce incision when other inputs are fixed; greater resistance/threshold cannot increase it.
- Bank/hillslope moves conserve solid volume within quantization tolerance; failed material appears at or downstream of the toe.
- Increased cohesion or friction cannot lower factor of safety; increased pore pressure cannot raise it.
- Hard/soft material fixtures demonstrate steeper stable rock faces only through declared cohesion/structure, not silent noise.

### Determinism and cost

- Identical seed/configuration yields identical lake IDs, reach graph, geometry and block hashes across crop, query order and thread count.
- Adjacent chunks agree on every boundary column and cross-section.
- Work counters obey the fixed pass/iteration/sample caps, including adversarial flat terrain and many nested depressions.

## Staged implementation plan

Each stage has its own fixtures and can ship only when the preceding contracts remain green.

1. **Reproduce and fence the lake defect.** Capture the reported dirt/non-water basin as a minimal adapter fixture; assert terrain, routing fill, water surface and emitted blocks separately.
2. **Make quantities honest.** Add block-to-metre area, effective runoff, distinct \(Q_{\mathrm{mean}}\)/\(Q_{\mathrm{bf}}\), independent ledgers and versioned providers. Preserve a clearly named model-unit mode.
3. **Create basin/lake components.** Add depression hierarchy, spill/outlet records, hypsometry, flat water level and explicit open/closed/dry state; rasterize actual water.
4. **Create the reach graph and hydraulics.** Split reaches, solve bankfull cross-section and current stage/velocity, and store auditable material/roughness/slope inputs.
5. **Fix longitudinal profiles.** Apply bounded implicit stream-power incision, reroute at most twice, and prove fine thalwegs descend to committed outlets.
6. **Add mountain and single-thread alluvial form.** Cascade/step-pool/plane-bed/pool-riffle families, valley-constrained meanders, point bars, cutbanks and explicit cutoffs.
7. **Add braided candidates.** Only after a sediment-mobility proxy, split-flow conservation and deterministic fallback are gated. Defer anastomosing behavior.
8. **Couple toe erosion and mass wasting.** Material parameters, local factor of safety, conservative failure/deposition and bounded repair.
9. **Adopt in the Minecraft adapter.** Version data, benchmark cold roots and warm chunks, test seams/fluid updates, then make the new path the default.

## Explicit non-goals for this revamp

- no full transient 2-D shallow-water or Saint-Venant flood solver;
- no full Exner equation, multi-grain sediment routing or morphodynamic time integration;
- no event hydrographs, inundation maps, dam-break waves or seasonal lake animation;
- no groundwater aquifer model, karst routing or groundwater-surface coupling;
- no globally calibrated rainfall, evapotranspiration, lithology or return-period database;
- no claim that a static Minecraft water block reproduces computed velocity;
- no complete vegetation/ecology/floodplain-succession model;
- no arbitrary 3-D fracture mechanics, overhang collapse or landslide runout;
- no independently solved per-chunk drainage and no unbounded whole-world solve.

These omissions are deliberate. The data contracts should allow later transient water and sediment modules without pretending they exist now.

## Implementation-status checklist

This checklist records accepted behavior, not mere presence of experimental code in a working tree. Update it only with a corresponding gate/research record.

- [x] Bounded complete-family coarse solve with explicit maritime terminals.
- [x] Continuous divergence-free/non-stagnating wind with fixed-work humidity transport and controlled orographic rain response.
- [x] Material/slope/soil annual precipitation partition with exact runoff/infiltration/evapotranspiration closure.
- [x] Acyclic coarse priority-flood routing and exact effective surface-runoff ledger.
- [x] Deterministic coarse detachment-limited incision experiment.
- [x] Exact-sea, sub-voxel shoreline and post-channel lake-inlet regressions fenced by adapter block-column fixtures.
- [x] Raw terrain, routing elevation, fine bed and water surface separated through adapter emission.
- [x] Stable face-connected lake components with flat fine masks and spill/outlet records.
- [x] No fake hydraulic profile on lake cells; profiled inlet stage meets the receiving-lake surface.
- [ ] Depression hierarchy, hypsometry and explicit dry/closed/open lake states.
- [ ] Effective runoff in declared SI units plus independent \(Q_{\mathrm{mean}}\) and \(Q_{\mathrm{bf}}\) ledgers.
- [x] Per-segment rectangular Manning proxies with separate current/bankfull width, depth, stage and speed satisfying continuity.
- [ ] Explicit reach graph and calibrated shaped cross-sections with independent event-flow accumulation.
- [ ] Fine monotonically descending thalwegs connected across chunk boundaries.
- [x] Fixed-work curved cascade/straight/meandering candidates and conserving two-thread braid split/rejoin geometry.
- [ ] Full mountain-bed families, valley-constrained meanders, bar mobility and deterministic detail-budget fallback.
- [x] Fixed-pass conservative coarse slope relaxation and hardness-dependent adapter bank runout.
- [x] Five broad parent-rock classes, post-relaxation soil/rockhead depth and drainage-sensitive Minecraft horizons.
- [ ] Toe-erosion-coupled failure with cohesion, pore pressure, structure and deposited runout.
- [ ] Pinned cold-root, warm-chunk, memory and determinism performance gates.
- [x] New Minecraft adapter chunks consume the shared fluvial/lake realization by default.
- [ ] Minecraft integration validated in game, including fluid updates and saved-world versioning.

## Primary and authoritative references

- **R1.** Barnes, Lehman and Mulla (2014), “Priority-Flood: An Optimal Depression-Filling and Watershed-Labeling Algorithm for Digital Elevation Models,” *Computers & Geosciences*. [doi:10.1016/j.cageo.2013.04.024](https://doi.org/10.1016/j.cageo.2013.04.024)
- **R2.** Barnes et al. (2021), “Fill-Spill-Merge: Flow Routing in Depression Hierarchies,” *Earth Surface Dynamics*. [doi:10.5194/esurf-9-105-2021](https://doi.org/10.5194/esurf-9-105-2021)
- **R3.** Lindsay (2016), “Efficient Hybrid Breaching-Filling Sink Removal Methods for Flow Path Enforcement in Digital Elevation Models,” *Hydrological Processes*. [doi:10.1002/hyp.10648](https://doi.org/10.1002/hyp.10648)
- **R4.** Tarboton (1997), “A New Method for the Determination of Flow Directions and Upslope Areas in Grid Digital Elevation Models,” *Water Resources Research*. [doi:10.1029/96WR03137](https://doi.org/10.1029/96WR03137)
- **R5.** Leopold and Maddock (1953), *The Hydraulic Geometry of Stream Channels and Some Physiographic Implications*, USGS Professional Paper 252. [doi:10.3133/pp252](https://doi.org/10.3133/pp252)
- **R6.** Osterkamp, Lane and Foster (1983), *An Analytical Treatment of Channel-Morphology Relations*, USGS Professional Paper 1288. [doi:10.3133/pp1288](https://doi.org/10.3133/pp1288)
- **R7.** Barnes (1967), *Roughness Characteristics of Natural Channels*, USGS Water-Supply Paper 1849; and Arcement and Schneider (1989), *Guide for Selecting Manning's Roughness Coefficients*, USGS Water-Supply Paper 2339. [doi:10.3133/wsp1849](https://doi.org/10.3133/wsp1849), [doi:10.3133/wsp2339](https://doi.org/10.3133/wsp2339)
- **R8.** Williams (1978), “Bank-Full Discharge of Rivers,” *Water Resources Research*. [doi:10.1029/WR014i006p01141](https://doi.org/10.1029/WR014i006p01141)
- **R9.** USGS (2005), *Bankfull Characteristics of Ohio Streams and Their Relation to Peak Streamflows*. [Scientific Investigations Report 2005-5153](https://pubs.usgs.gov/sir/2005/5153/)
- **R10.** Montgomery and Dietrich (1989), “Source Areas, Drainage Density, and Channel Initiation,” *Water Resources Research*. [doi:10.1029/WR025i008p01907](https://doi.org/10.1029/WR025i008p01907)
- **R11.** Montgomery and Buffington (1997), “Channel-Reach Morphology in Mountain Drainage Basins,” *Geological Society of America Bulletin*. [doi:10.1130/0016-7606(1997)109%3C0596:CRMIMD%3E2.3.CO;2](https://doi.org/10.1130/0016-7606(1997)109%3C0596:CRMIMD%3E2.3.CO;2)
- **R12.** Leopold and Wolman (1957), *River Channel Patterns: Braided, Meandering, and Straight*, USGS Professional Paper 282-B. [doi:10.3133/pp282B](https://doi.org/10.3133/pp282B)
- **R13.** Crosato and Mosselman (2009), “Simple Physics-Based Predictor for the Number of River Bars and the Transition Between Meandering and Braiding,” *Water Resources Research*. [doi:10.1029/2008WR007242](https://doi.org/10.1029/2008WR007242)
- **R14.** Eaton, Millar and Davidson (2010), “Channel Patterns: Braided, Anabranching, and Single-Thread,” *Geomorphology*. [doi:10.1016/j.geomorph.2010.04.010](https://doi.org/10.1016/j.geomorph.2010.04.010)
- **R15.** Nanson and Knighton (1996), “Anabranching Rivers: Their Cause, Character and Classification,” *Earth Surface Processes and Landforms*. [doi:10.1002/(SICI)1096-9837(199603)21:3%3C217::AID-ESP611%3E3.0.CO;2-U](https://doi.org/10.1002/(SICI)1096-9837(199603)21:3%3C217::AID-ESP611%3E3.0.CO;2-U)
- **R16.** Williams (1986), “River Meanders and Channel Size,” *Journal of Hydrology*. [doi:10.1016/0022-1694(86)90202-7](https://doi.org/10.1016/0022-1694(86)90202-7)
- **R17.** Whipple and Tucker (1999), “Dynamics of the Stream-Power River Incision Model,” *Journal of Geophysical Research*. [doi:10.1029/1999JB900120](https://doi.org/10.1029/1999JB900120)
- **R18.** Braun and Willett (2013), “A Very Efficient O(n), Implicit and Parallel Method to Solve the Stream Power Equation,” *Geomorphology*. [doi:10.1016/j.geomorph.2012.10.008](https://doi.org/10.1016/j.geomorph.2012.10.008)
- **R19.** Roering, Kirchner and Dietrich (1999), “Evidence for Nonlinear, Diffusive Sediment Transport on Hillslopes,” *Water Resources Research*. [doi:10.1029/1998WR900090](https://doi.org/10.1029/1998WR900090)
- **R20.** Baum, Savage and Godt (2008), *TRIGRS—A Fortran Program for Transient Rainfall Infiltration and Grid-Based Regional Slope-Stability Analysis*. [USGS Open-File Report 2008-1159](https://pubs.usgs.gov/of/2008/1159/)
- **R21.** Simon and Pollen (2006), “A Model of Streambank Stability Incorporating Hydraulic Erosion and the Effects of Riparian Vegetation,” *Proceedings of the 8th Federal Interagency Sedimentation Conference*. [USDA Agricultural Research Service publication record](https://www.ars.usda.gov/research/publications/publication/?seqNo115=186452)
