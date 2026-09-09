# Plate vectors -> shared boundary forcing

Date: 2026-09-09. Version: `boundary-forcing-v1`. Implemented Java21 reference profiles driven by **existing core plate data**, not a new plate generator or production elevation field.

## What is connected

`BoundaryForcing.sample(x,z)` reads `PlateTopology.sample`: the existing owner and neighboring plate, their exact IDs, site coordinates, velocities, crust flags and synthetic ages. Sites and plate identities are unchanged. A shared descriptor sorts IDs using unsigned order, so either caller obtains the same orientation, regime, subduction side and profile. The physical boundary location is still the original straight Voronoi bisector; velocities do not advect or reshape it in this static snapshot.

Let d be the vector from canonical A to B and v = velocity(A) - velocity(B). The model computes:

- Normal motion: dot(v,d), positive for convergence.
- Signed shear: vx*dz - vz*dx, using the corresponding clockwise tangent convention.
- Rates: raw projections multiplied by1024 and divided by floor(length(d)). This is explicit integer fixed-point normalization, not exact real Euclidean division or physical cm/year.

Classification uses the unnormalized integer projections and the existing `transformRatio` threshold. Zero normal AND zero shear is explicitly `QUIET`, rather than treating identical motion as active transform faulting. Dominant shear is `TRANSFORM`; otherwise the normal sign and the two crust types select a regime. All projections remain available even when the dominant regime is transform.

The descriptor permits bounded internal plate sites just beyond the public coordinate guard. Testing at +/-2^40 initially exposed the difference between legal query coordinates and required neighboring-site geometry; the reference now retains a bounded internal halo rather than clipping plates. Public queries still obey the existing guard. Bounded separation/velocity/attribute checks keep projection and profile arithmetic within long range.

## Illustrative cross-edge profiles

Profiles return a signed **reference height anomaly**, not absolute height, sea level, crustal displacement history or a guaranteed drainage divide. Half-width is currently plateSpacing/4. The cross-edge coordinate spans -1000..1000 permille, with A on the negative side. Compact integer cubic-shaped bumps combine into the following deliberately simple versioned recipes:

| Motion and crust | Reference response |
| --- | --- |
| Convergent continental/continental | Broad positive collision belt |
| Convergent with oceanic crust | Negative trench on descending side, positive offset arc on the other side |
| Divergent continental/continental | Central negative rift with two positive shoulders |
| Divergent oceanic/oceanic | Broad positive spreading ridge with a narrower axial notch |
| Divergent mixed crust | Central low and a shoulder on the continental side |
| Dominant shear | Shear metadata retained; no invented vertical anomaly |
| Equal velocities | Zero new relative-motion forcing |

For mixed-crust convergence, the oceanic side is chosen as descending. For ocean/ocean convergence, older synthetic crust wins, with canonical A breaking age ties. This is an explicit model convention, not a full density/buoyancy/slab-history calculation. Shape distinctions are informed by [USGS plate-motion descriptions](https://pubs.usgs.gov/gip/dynamic/understanding.html); numerical coefficients, widths and side-selection heuristics are Genesis fixture choices, not measured geology.

Amplitude depends on relative normal speed in fixed model units. It is not normalized away by the global `plateSpeed` control. Width is fixed in this version, not speed-dependent. Changing `plateSpeed` in the existing generator rehashes bounded velocity components; it is not the same operation as multiplying a fixed pair's vectors. The speed test therefore doubles explicit fixture vectors.

For an axis-aligned relative normal speed of8, center anomalies are collision+64, continental rift-48 and ocean spreading+24. Transform and quiet have zero vertical anomaly, for different reasons. Subduction is intentionally asymmetric. The underlying reference terrain may still contain older relief even when new forcing is quiet.

## Tests and water coupling

`BoundaryForcingGates` runs in the normal suite:

- 300 samples from actual core plates across seeds42/-1/Long.MIN_VALUE and signed/extreme coordinates. Independent BigInteger projections agree; regime groups match the existing core convergent/divergent/transform classes. Plate ownership and IDs are reused, not replaced.
- Swapping callers yields the same shared descriptor. Common coordinate translation and a common added velocity leave projections/profiles unchanged. Doubling explicit relative speed doubles the collision center anomaly.
- Controlled collision, trench/arc polarity, rift shoulders, spreading-axis notch, mixed margin, shear and quiet fixtures; ocean-age tie rule; compact support and invalid inputs.
- Cold reconstruction and concurrent sampling agree; minimum/maximum supported plate spacing and maximum core speed remain within the reference arithmetic bounds.

An analytic drainage fixture uses the existing explicit-crest D4 solver and its independent minimax/source-walk reference. Start from a120-unit pass and a basin with westward spill80. Collision forcing raises the pass to184 and preserves spill80. Apply the continental-rift anomaly to one pass: it drops to72, the unrestricted solve crosses the boundary at72, and an isolated-cell solve incorrectly keeps spill80. The test requires this mismatch and transferred runoff.

This is the first executable forcing-to-drainage compatibility counterexample. It uses controlled plate vectors and a synthetic basin, not an end-to-end tectonic world or permission to overwrite the paired prototype's divides. Geological forcing can invalidate a drainage boundary; it must trigger revalidation/reconstruction, not be applied blindly after drainage is committed.

Artifacts: `build/boundary-forcing.json` and inspected code-generated `build/gallery/boundary-forcing.png`. The top row uses actual seed42 core plate vectors; lower cross-sections use controlled fixtures. Warm/cool colors represent positive/negative anomalies, **not land/ocean**. No threshold is being adjusted to achieve the user's 50-55% land-area preference; existing continental fields are unchanged.

## Limits and next step

The spatial reference selects one closest boundary. Switching between edges can create discontinuities, including near triple junctions; the image exposes these. It is not a continuous multi-edge terrain field and must not be registered as finished elevation. Integer profile quantization is also explicit. There is no time integration, moving boundary geometry, fault displacement realization, flexure, volcanism simulation, erosion or atmospheric coupling.

Next compose neighboring edge profiles with shared junction constraints and realize them on the finer crest/terrain mesh. Preserve or explicitly revise coast, terminal and runoff contracts; independently test diagonal routes and saddle spills. Tectonic plates and hydrological cells remain distinct identities/scales. The existing plate labels are geological inputs, not automatic mountain/ocean classifications for every drainage-cell edge.

Production core/version, Params, viewer fields and old golden outputs remain unchanged. R2/R3 and integrated geological terrain remain incomplete.
