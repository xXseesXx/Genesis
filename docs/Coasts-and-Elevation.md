# M2a: committed coasts and macro elevation

M2a is a tested first portion of M2, not a claim that global ocean connectivity or the hydrology cascade is solved. It retains the existing nonperiodic ±2^40-block plane. It does not introduce artificial ocean rims, repeat a finite planet tile, or perform a global sea-level quantile.

## Coarse commitments

Continental support is blended from tectonic plate crust using a compact integer kernel. The support radius is `crustBlendRadius / 1000 * plateSpacing`; 4096-unit kernel values are cubed and normalized to a 0–1000 crust fraction. The radius bounds keep every contributing site in the existing 7×7 neighborhood and ensure a nonzero denominator. The largest products fit signed long. This exposes a registered `crustFraction` field; elevation never reads tectonic internals.

At each canonical `coarseSpacing` vertex, the crust fraction is mixed with broad integer lattice variation and compared to the fixed `seaThreshold`. The resulting signed score is an integer. Broad variation modulates continental structure; it is not the earlier fBm diagnostic. Its products are bounded by `1000 * continentScale²`, below 2^50 at the allowed scale limit.

Each cell uses a seed-hashed diagonal to divide it into two triangles. Integer barycentric weights interpolate the four signed scores. The numerator's sign determines land or sea exactly. Shared edges interpolate the same two vertices regardless of the chosen diagonals. This provides a single continuous, piecewise-linear coast model with explicit handling of alternating corner signs.

Zoom changes sampling density only. Changing `landHeight`, `oceanDepth`, or `mountainHeight` does not move the coast. Changing topology parameters, such as `coarseSpacing`, `crustInfluence`, or `seaThreshold`, creates a different world configuration; changing `coarseSpacing` is not a refinement of an existing commitment. Refinement of the committed geometry is future cascade work.

## Elevation

Let `c` be the interpolated signed score divided by 1000. With sea level defined as zero model meters:

```text
c <= 0: height = c * oceanDepth
c > 0:  height = c * landHeight
                 + c² * mountainHeight * positiveUplift / (1 + positiveUplift)
```

The uplift contribution is nonnegative and vanishes at the coast. Thus elevation agrees with the committed land/sea sign, including exact zero crossings. Uplift is consumed through the field registry. Continuous elevation has slope changes at coarse triangle edges and the sea-level transition; these are macro-scale surfaces, without fluvial carving, geological differential erosion, or fine terrain detail.

The renderer adds display-only northwest hillshade to land and distinct bathymetry/land palettes. It does not feed rendered values back into generation. Land fraction in the viewer is a sampled viewport metric, not a worldwide sea-level calibration.

## Bounded coarse drainage diagnostic

The coarse graph is the cardinal lattice of coast anchors. Every vertex with score ≤0 is a sea-level terminal. This does not yet distinguish an ocean from an enclosed inland sea.

`coarseDrainageRank` searches Manhattan rings out to `seaSearchRadius`, returning the exact nearest-terminal distance in coarse steps when one is found. Integer hashes choose among equidistant terminals. `coarseFlowDirection` selects a cardinal step toward the chosen terminal.

For any resolved rank `r > 0`, that neighbor has rank exactly `r-1`: the selected terminal is one step closer, and a shorter neighbor route would contradict the original nearest-distance result. Re-evaluating the destination's tie-break cannot change that decrease. Resolved paths therefore contain no cycles and end at a sea-level terminal within `r` steps. They are not yet routed down the elevation surface and must not be rendered as carved rivers.

When no terminal is found in the configured search radius, rank, direction, and distance return **−1 (unresolved)**. The viewer colors these cells pink. There is no fabricated outlet, hidden flat-water fallback, or assertion of global reachability. An all-land fixture explicitly exercises this case.

These diagnostic fields refer to the cell's lower-left coarse anchor, not the exact clicked column. Consequently a fine land pixel can have anchor rank zero near a coast. `coarseSeaDistance = rank * coarseSpacing` is a Manhattan graph distance in blocks, not a Euclidean coast distance. Direction codes are `0` terminal, `1` north (−z), `2` east (+x), `3` south (+z), `4` west (−x), `−1` unresolved.

The bounded search costs O(R²) anchor evaluations in the worst case and is explicitly not the proposed O(log R) cascade. A generator memoizes up to 8192 vertex scores and 4096 routes. Caches hold pure immutable results and are scoped to the complete seed/parameter configuration. Tests cover eviction and concurrent access.

Near the outer supported coordinate limit, crust sampling for the interpolation halo clamps to the boundary; variation remains algebraically defined in that halo. Routing searches only valid anchors and clamps the lower-left anchor inward at a non-dividing negative edge. This is a computational-domain edge policy, not a periodic planet model.

## Added fields and verification

The top bar now includes `crustFraction`, `continentality`, `baseElevation`, `seaMask`, `coarseSeaDistance`, `coarseDrainageRank`, and `coarseFlowDirection`, alongside all existing M0/M1 fields.

The automated suite checks exact coast/elevation sign agreement, an analytic straight-coast fixture, parameter changes that must preserve coastlines, shared edges, zoom sampling, independent square/diamond distance references, strict decrease along every sampled resolved path, extreme seeds/domain edges, all-land unresolved behavior, cache eviction, and concurrent queries. DET covers all 18 fields. Golden references include 56 new M2a PNGs and preserve the 88 M0/M1 references. Local timing measures all current fields together and a 512² macro-elevation render.

## Work still needed for M2/M3

Global ocean/inland-sea classification, root/outlet contracts for arbitrarily large land regions, coarse-to-fine corridor refinement, flux conservation and accumulation, a global routing oracle, and terrain conditioned on those rivers remain open. The original `height + lambda * oceanDistance` potential is not implemented as a claimed drainage guarantee. The current rank field is an independently tested precursor with an explicit unresolved state.
