# Deterministic continental hydraulic erosion

Implemented 2026-09-17 and extended 2026-09-18 in `HydraulicErosion`, `MassWasting`, `TerrainHardness`, `TerrainSubstrate`, and `ContinentalHydrology`. Versions: continental-erosion-v2, mass-wasting-v1 and terrain-substrate-v1; composed hydrology: continental-hydrology-v4.

## Research and choice

| Approach | Useful properties | Cost for this generator |
| --- | --- | --- |
| Droplet/particle erosion | Local sediment pickup and deposition; detailed channels | Must seed and finish all trajectories for a canonical domain; chunk-local runs truncate upstream dependencies. Sequential updates require a fixed particle schedule. |
| Shallow-water grid erosion | Water depth, velocity, sediment capacity and deposition | Multiple coupled arrays and small stable time steps; significantly more work per complete continent. |
| Implicit stream-power incision | Upstream discharge, spatial erodibility, downstream-first stable bed updates | Coarse landscape evolution; no automatic fine channels or sediment deposition. |
| Fixed-pass thermal erosion | Conserved local collapse/deposition above a material-dependent stable slope | Coarse talus approximation; no pore pressure, fracture mechanics or long-runout landslides. |

Braun and Willett's [2013 paper](https://doi.org/10.1016/j.geomorph.2012.10.008) provides the implicit stream-power approach. [Fastscapelib's implementation documentation](https://fastscapelib.readthedocs.io/en/latest/api_python/_api_generated/fastscapelib.SPLEroder.html) specifies spatial erodibility and implicit finite differences. These support the selected incision kernel, not our artistic parameter calibration. [Mei, Decaudin and Hu's GPU hydraulic erosion paper](https://www-evasion.imag.fr/Publications/2007/MDH07/FastErosion_PG07.pdf) describes the alternative grid water/sediment approach. [r.sim.terrain](https://gmd.copernicus.org/articles/12/2837/2019/) compares erosion/deposition regimes and dynamic hydrology.

Our design choice is detachment-limited incision followed by bounded thermal relaxation. Incised bedrock is treated as material exported from the modeled bedrock system. Collapse material is conserved locally and deposited downslope, but it is not routed as fluvial sediment. This remains an open sediment boundary assumption, not a transport-capacity model: lake trapping, alluvium, bars and deltas are absent. The wider evidence and target are recorded in [the fluvial hydrology revamp](fluvial-hydrology-revamp.md).

## Implemented model

For six fixed passes, route and accumulate rainfall on the complete family, then visit nodes downstream first. For slope exponent n=1 and discharge exponent m=1/2:

`a = (strength / 1000 / 6) * exposure * (1 - 0.95 * hardness) * sqrt(Q / (1000 * step^2)) / D`

`newHeight = receiverHeight + (oldHeight - receiverHeight) / (1 + a)`

Here D is 1 or sqrt(2) in lattice-cell units, Q is accumulated effective surface-runoff millimetres times block area/year, and hardness is 0..1. In the coupled path, terrain-aware rain is first partitioned into runoff/infiltration/evapotranspiration by ground composition; only runoff enters Q. This normalization intentionally provides a dimensionless landscape preset, not a calibrated physical erosion rate. No second local rain multiplier double-counts precipitation. A cell with zero local runoff can still erode if it receives upstream flow.

Exposure is `0.2 + 0.8 * clamp(plateAge, 0, 4500) / 4500`. Older synthetic plates receive more exposure at otherwise equal conditions. Crust age is only a proxy: it does not reconstruct surface age, uplift history or weathering. Existing tectonic forces shape the starting relief; the erosion solver does not invent uplift rates from those height offsets.

The compatibility hardness field remains independent seeded three-octave noise bounded to 0.05..0.95. The production viewer/adapter instead supplies `TerrainSubstrate`: basalt, granite, shale, limestone and sandstone have independent resistance, permeability and weatherability, and the same resistance drives incision and stable-angle proxies. Seeded folded bands expose broad units. This does not consume the separate legacy-model stratigraphy or evolve lithology with depth; maximum resistance remains weakly erodible. `ClimateField` supplies spatially varying humidity/orographic rain on pre-incision relief, avoiding climate/erosion recursion.

Submerged maritime terminals remain fixed. Flooded lake beds are protected from incision; their spill surfaces act as receiver water levels. Recompute minimax filling, receivers and exact upstream runoff between incision passes. Incision rounds upward to integer model height millimetres and cannot lower above-sea terrain below `sea + 0.001`. This deliberately preserves coast sign and the submerged family separation contract.

After the sixth incision pass, up to four fixed Jacobi-style mass-wasting passes inspect each undirected D8 edge, exiting early when nothing moves. The stable angle is a Genesis calibration, `28 + 34 * hardness^0.75` degrees: weak regolith collapses at a lower slope while resistant rock retains steeper faces. Proposed excess-height transfers are scaled by each donor's available material, then applied simultaneously. Every pass has an exact zero-sum integer-height ledger; maritime cells neither donate nor receive, and the final drainage is routed once after relaxation. This is the inexpensive process commonly called thermal erosion or mass wasting, not a claim that hardness alone captures cohesion, joints, saturation or rock-wall mechanics.

Net open-system removal and absolute gravity-redistributed volume are separately available as `BigInteger` height-mm times model-block squared. Neither is sediment mass or an explicit downstream sediment-routing ledger.

## Infinite world and random-order contract

The canonical work unit is the complete, irregular, bounded 1–4-plate family. It can contain multiple disconnected islands and many drainage basins. Its support, sources, lattice, climate/erosion iteration counts and deterministic flood ties are independent of requested chunk, crop, zoom and cache state. Whole roots are built before publication. Equal cold requests coalesce; a bounded number of distinct roots may build concurrently and publish through the same bounded LRU. Arrays are private. Climate/material providers must be immutable deterministic functions. Cache identity includes terrain, climate, wind, substrate, rainfall scale and erosion configuration.

There is no finite world map or repeating precomputed erosion tile. The existing numeric guard remains ±2^40 blocks; a root whose full support exceeds it is unsupported, never assigned an artificial outlet. Isolation relies on the existing deep inter-family maritime margins. Future cross-family land bridges require a different solve-domain contract.

Fine sampling bilinearly interpolates the signed final-minus-original terrain change, so both incision and local talus deposition are realized while preserving small terrain detail and fixed maritime height. At canonical non-maritime vertices it matches the final bed. This is deterministic continuous realization, not a proof that every fine river bed descends; the starting integer-block terrain and coast floor still affect continuity. Channel-preserving refinement remains required. Native scaling retains normalized forcing, but millimetre quantization means outputs need not be exact integer multiples across sizes.

## Use and verification

Open `/tectonics.html`: the initial layer is **Eroded terrain**. **Before erosion**, **Erosion depth**, **Terrain hardness**, **Rock type**, climate/ground maps and **Rivers + lakes** allow comparison. **Erosion exposure** defaults to 700; zero disables hydraulic incision. River/lake diagnostics use the final post-mass-wasting coarse bed. **Height + contours** explicitly shows the original tectonic surface.

The original three-argument Java hydrology constructor retains erosion disabled for baseline research. For erosion use the five-argument constructor with cache capacity, strength and a `TerrainHardness` provider. Query `Root.elevation(x,z,rawHeight,seaLevel)` for the realized surface; `TectonicTerrain.sample` remains the pre-erosion source, avoiding recursive hydrology sampling. This Java21 experimental path is consumed by both the tectonic viewer and Minecraft adapter; it is not part of the legacy Java8 generator.

Run `./genesis.sh check rivers` or `.\genesis.ps1 check rivers` for the incision equation fixture, hardness-sensitive stable angles, exact collapse conservation/determinism, age/hardness/discharge effects, dry/disabled identity, variable rain, whole-family material counterfactuals, independent final upstream source walks, coast/maritime invariants, invalid material rejection, wider-support equivalence, eviction and concurrency. `check tectonic-viewer` additionally covers actual HTTP layers, crop/zoom equivalence, saved parameters, versioned exports and viewer logic. Results: `build/hydraulic-erosion.json`; images: `build/gallery/tectonic-erodedTerrain.png`, `tectonic-erosionDepth.png`, `tectonic-hardness.png`.
