# Shared soft plate-junction forcing

Date: 2026-09-09. Version: `junction-forcing-v1`. Java21-only reference toward the tectonic generation viewer. This is a proposed Genesis composition rule and an executable experiment, not a geological simulation or an adopted hydrological boundary model.

## Decision and scope

Use a bounded, continuous-in-real-arithmetic multi-plate anomaly as the next viewer's candidate forcing input. Keep the existing `BoundaryForcing` closest-edge sampler as a comparison, not the elevation recipe. Reuse the existing core plate sites, motion vectors, crust and synthetic ages. No production field, parameter, coastline, river or viewer changes in this checkpoint.

The experiment removes the need to choose a single winning profile at a junction. It does **not** construct true shared Voronoi-face segments, slope-continuous mountains, preserved ridge constraints, broad continental interiors or absolute elevation. Close non-face-sharing plate pairs can contribute, especially around four-way configurations. This is explicitly a **soft multi-plate influence model**, not a new exact plate-boundary topology. Its morphology must remain inspectable before architecture adoption.

## Pointwise recipe

For query q, spacing S and site i, let d_i be squared distance to q and m = min(d_i). Set B = S^2/2 and t_i = (d_i-m)/B. The site weight is zero for t_i >= 1; otherwise it is `(1-t_i)^2 * (1+2*t_i)`.

For every supported unordered pair i,j:

1. Obtain the existing canonical `BoundaryForcing.describe` descriptor. Motion classification and fixed-point normal/shear rates are unchanged; neither query ownership nor nearest-edge selection controls this descriptor.
2. Project q onto its canonical A-to-B normal, relative to the shared bisector. Profile half-width remains S/4.
3. Evaluate the same illustrative collision, subduction, rift and spreading profile families with unquantized cubic bumps. This intentionally removes the old per-mille/height quantization as well as replacing its spatial selector; the comparison image is not a blend-only ablation.
4. Let p_ij = w_i*w_j. Sum `p_ij * profile_ij` and divide by `max(1, sum(p_ij))`.

At an isolated two-site tie, the profile retains its center gain. At a three-site tie all three pairs receive equal weight, preventing a threefold additive peak. Near the edge of all pair influence, the numerator tends to zero while the denominator stays at least one. Positive and negative **net pair contributions** are exposed separately, with their sum equal to the returned anomaly. They are not separate time-integrated uplift/subsidence budgets.

This is a convex combination of pair heights and, when total pair weight is below one, zero. It cannot exceed the contributing range merely because more pairs overlap. This does not mean it preserves any individual ridge height.

## Continuity and deterministic work

In ideal real arithmetic, squared distances, their minimum, the compact weights and the profiles are continuous. The denominator is continuous and never zero. New contributors enter with zero weight, so the formula has no nearest-owner/profile-selection jump. The minimum and `max(1,total)` can have derivative kinks: **C0, not a C1 slope-continuity claim**. The actual output is IEEE floating-point, with finite numerical rounding, not an exact real field.

Each site's position stays in the central half of its lattice cell. A home-cell site gives `m <= 9*S^2/8`. A supported site therefore has `d_i < 13*S^2/8`. Every site omitted by a query-cell-centered 5x5 neighborhood is at least `9*S/4` away on one axis, whose squared distance exceeds that bound. Thus 25 candidates suffice for the ideal infinite jittered lattice, independent of crop or query order. Neighborhoods change at lattice seams, but every entering/leaving omitted candidate has zero weight. This argument uses the existing jitter bound, not random sampling as a proof.

There are at most 300 pair candidates; unsupported pairs are skipped. Supported pair separation is less than `2*sqrt(13/8)*S`, within the inherited descriptor guard at the largest supported S. Geometry uses bounded local coordinate differences, never large world-coordinate products. Required sites beyond the public +/-2^40 query guard remain available through the inherited internal halo; the guard is not an ocean edge.

Each point reconstructs its own sites, with no memoization or global state. Contributors are sorted by exact unsigned plate ID before all floating-point reductions. `StrictMath.sqrt` handles profile projection lengths; Java21 strict floating-point semantics and the fixed reduction order define the numerical recipe. A versioned 1024-point SHA-256 fingerprint is required on both CI platforms. This reference has not been ported to the Java8 core; preserve strict arithmetic explicitly when doing so.

## Evidence and an important negative result

`JunctionForcingGates` is in the normal suite:

- 720 world cases spanning three seeds, minimum/default/maximum spacing, zero/maximum jitter, signed/extreme coordinates and query-neighborhood seams. The 25-site output equals a 121-site composition exactly. A separate ordered-pair reduction independently computes weights/projections and agrees within numerical tolerance; it intentionally shares the versioned profile function.
- Controlled collision and mixed-crust/motion triple junctions; exact tie weights, pair center gain, compact support, common position/velocity invariance, explicit pair-speed doubling, quiet motion and signed-contribution accounting.
- Input permutations, cold reconstruction, shared-instance concurrency and overlapping coordinate samples agree. A 1024-point output fingerprint records exact floating-point values and support metadata.
- One-block steps are regression-tested, not a universal gradient theorem. Maximum observed height change was about 6.641 model units at the tested extreme configurations. Raw check timing is reported, not a chunk or viewer latency guarantee.
- A **blending-induced divide failure** is required: an isolated collision profile supplies +64 over a base pass20, giving crest84 and basin spill80. A third converging continental plate reduces the convex junction anomaly to about30.87; the explicitly floored crest becomes50. The unrestricted finite saddle solve spills across it at50, while the stale isolated-cell solve incorrectly retains80. This is a controlled scalar-to-saddle counterexample, not a full sampled tectonic catchment.

The last test is why visually nicer junctions cannot be applied after committing drainage without revalidation. Even all-collision composition can weaken an existing divide; no rift is necessary.

Artifacts: `build/junction-forcing.json` and inspected code-generated `build/gallery/junction-forcing.png`. The image compares the old/new anomalies and site support, with two controlled junction sections. Warm and blue indicate signed anomalies, not land/ocean. Existing goldens and production version remain unchanged.

## Next viewer-oriented milestone

Define a broad crustal/continental elevation recipe upstream of drainage, then combine it with this separately inspectable forcing. Compare continental morphology and land fraction across multiple seeds and fixed world-coordinate samples. The 50-55% land preference must not be implemented as per-viewport sea-level adjustment, nor confused with plate crust probability.

After this candidate exists, expose a separate experimental tectonic viewer with top-bar fields for plate motion, signed normal/shear motion, crust, reference base elevation, positive/negative pair contributions, composed height and coast. Initially leave global rivers explicitly unresolved rather than attach the old viewport- or placeholder-conditioned ones to a new surface. Keep the production viewer intact.

Larger connected land/root layouts, certified terminal context, outlet-preserving refinement and terrain-drainage compatibility remain open. Broad elevation and an exploratory viewer need not wait for full hydrology, climate, geological histories or erosion. Neither this smooth composition nor the existing crest mesh is a replacement for those contracts.
