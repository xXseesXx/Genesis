# Sparse irregular plates and bounded continental families

Date: 2026-09-10. Implemented candidate: `tectonic-terrain-v2`, `irregular-plates-v1`, `continental-groups-v1`.

The requested change is fewer plates, usually one and at most five per continent, mixed land/submerged terrain on most plates, irregular edges, varied sizes, and individual plate elevations and slopes. This replaces the v1 experimental viewer recipe. It is a static procedural construction; synthetic ages and a young small-plate class do not constitute a simulated fracture history.

## Plate partition

The default spacing is 524,288 blocks, eight times v1's 65,536. Nominal plate density is consequently 1/64 of v1 at the same physical map extent. The default map covers three spacings across. Changing map zoom never changes any world decision.

Each lattice address has an exact packed signed-64-bit identity and one jittered site. Five seeded size classes set an additive power weight. Their nominal scale ranges are 600–750, 760–900, 920–1120, 1200–1400 and 1500–1800 permille; these are size preferences, not measured polygon diameters. The first class has age at most floor(ageMax/12), giving small young fragments. Some weak sites can lose their entire cell to stronger neighbors; the generator does not promise one visible plate per lattice address or a connected cell for every site.

Ownership minimizes the integer score

`1024 * squaredDistance(site, warpedQuery) - weightQ * S² + edgeNoiseQ * S²`.

The shared three-octave coordinate warp is bounded by 0.3S per axis. Plate-specific quintic value perturbations at approximately S/6 add serrated edges; their quantized score amplitude is at most 200. Power weights span -700 through 1250. Ties use a hash ordering followed by exact identity. Floating interpolation uses Java's strict arithmetic/StrictMath, then bounded integer quantization. This is a warped, perturbed power diagram, not plain Voronoi and not a geological time simulation.

Every query reads 7×7 sites. With maximum jitter and warp, the home site is within 1.05S on each axis, so its score is at most 3158 S². A site outside the support is at least 2.95S away on one axis: its score is above 7460 S². It therefore cannot own the query or enter the nearest-score +512 S² forcing band. For the best outside-family competitor, at least one adjacent cell outside a 2×2 family tile supplies a candidate within 2.05S and 1.05S per axis, whose score is below 6335 S². The extended-support gate compares all terrain fields as well as the winning plates with an 11×11 neighborhood.

At S≤1,048,576, local squared-distance/score operations fit signed long, including the test halo. Checked arithmetic rejects unsupported explicit site addresses. The public ±2^40 coordinate guard is a numeric interface bound; it is never a coast or water terminal. Internal site positions extend through the required halo.

## Continents and their plate budget

Independently seeded 2×2 lattice tiles choose a rotated partition: 60% four singles, 30% two pairs, 6% a triple plus a singleton, 4% a four-member family. Each site belongs to exactly one reciprocal family of size 1–4. The unsigned-minimum member's packed key is its exact identifier; hashing never substitutes for identity. Single families dominate by count.

A family authors a union of rotated, elongated, harmonically shaped continental lobes and narrow bridges between neighboring member anchors. Crust is spatially varying within each plate. A plate's velocity, datum, tilt and age remain constant descriptors of that plate; a global continental/oceanic bit no longer defines its entire terrain.

The family limit is enforced in the **bed surface**. Let g be the difference between the minimum outside-family score and minimum inside-family score, divided by 1024 S². A smooth envelope rises from zero at g≤0.035 to one at g≥0.285. At zero envelope, all detail, plate datum/tilt and tectonic forcing vanish and the bed is `-max(4000, oceanDepth)`. That is below every allowed sea level. Within a family, both score minima remain the same as plate ownership changes. Between families, their equal-score frontier has a band of deep bed. Thus a connected land path cannot cross families in the ideal continuous recipe; the implementation also checks one-block frontier crossings with maximum relief and minimum sea level. Integer quantization remains part of the versioned recipe.

This is a maximum of **four** rather than five, satisfying the requested upper bound. Family size is a plate budget, not a measured count of land-bearing plates: a family may have multiple islands, absent cells, or drowned member plates. The inspector labels this distinction.

The strict bound deliberately imposes water between different families. This returns to a geographic restriction related to the earlier rejected maritime mosaic, now motivated by the user's explicit small plate-count requirement. It remains visible at sufficiently wide zoom. Increasing S reduces plates per physical area but does not erase this normalized cellular bias. Broader ocean basins, less correlated coasts and better continental interiors remain morphology work; do not describe this checkpoint as final Earth-like geography or use families as certified hydrology roots.

## Elevation and forcing

Each plate has a deterministic datum and two tilt components. A score-weighted blend joins its local plane to neighboring plate planes. The continental/oceanic base, joined plane, subordinate detail, and positive/negative boundary contributions all feed the same absolute terrain. The family envelope attenuates these contributions consistently and supplies the deep margin term. Land means final height exceeds the configured sea level; sea level never modifies the bed or adapts to a crop.

Pair offsets use the **same power-score differences** that define irregular ownership, so profile centerlines follow these edges. Motion classification still projects velocity onto the site-separation direction: that is an approximate normal after warping, not the exact curved-edge normal. Local crust classification also remains a thresholded illustrative profile choice. Exact curved normals, smooth mixed-crust profile transitions, physical crust thickness/isostasy, fracture ancestry and erosion are future improvements.

`continentalPercent` is retained as a compatibility name for the extent control. It scales lobe radii; it is not an exact land quantile. Even 100 keeps deep family margins. Zero removes continental lobes but does not prohibit motion-driven oceanic relief.

## Evidence

The standard Java gates cover 288 cold and extended-support cases across three seeds and four scales, including signed numeric extremes; 10,000 reciprocal family memberships; the size/young-fragment distribution; warped versus unwarped ownership; zero-relief and zero-forcing counterfactuals; concurrent queries; and actual frontier bed checks under extreme controls. The legacy tectonic reference additionally tests its newly allowed maximum spacing.

The terrain fingerprint is `e9f11af63c11466bf6ffaea5af7752ff3d4ceb8b22e999337e23563a831c2583`.

Six fixed 128S-wide stratified area audits use 16,384 samples each and a common preset. Results are 51.3245%, 51.0376%, 50.8789%, 51.2390%, 50.3113%, and 50.8423% land (seeds 42, -1, 137, 8675309, 0, Long.MIN_VALUE), averaging **50.9389%**. This is a finite screening result, not a proof of infinite-area frequency for every seed.

Separate 193² maps spanning 12S measure plate occupancy. Plates with at least sixteen samples are mixed land/water in approximately 93–99% of cases. Crop-cut plates and subpixel fragments can bias this statistic. D8 land-component audits refine cross-family pixel connections by sampling the intervening bed; differing identifiers alone never count as an invisible water barrier. These measurements do not prove continuous ocean connectivity.

Generated and inspected images: `build/gallery/tectonic-terrain.png` (six wide crops), `tectonic-plate-architecture.png` (same-coordinate three-spacing terrain/identity/crust/plane/size comparison), and `tectonic-viewer-terrain.png` (real API output). The live viewer exposes 26 top-bar fields and 17 configuration controls. Old v1 links load the v2 preset with a visible migration notice and preserve seed/map location.

No browser layout/gesture screenshot or completed world hydrology is claimed. Production Java8 default fields and their golden outputs remain unchanged; only the accepted `plateSpacing` maximum expands from 262,144 to 1,048,576. The experimental generator remains in the Java21 oracle/harness.
