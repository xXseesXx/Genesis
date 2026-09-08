# Continental scaffold candidate

The existing coast is mostly a blend of plate crust and low-frequency variation. It makes many similarly sized land patches. This experiment introduces explicit, connected macro land objects before adding coastline detail. It is a comparison field, **not yet the coast used by elevation or drainage**. This avoids moving existing river commitments while the root model is unfinished.

Open the main viewer and choose **Continental overview**. The two new layers are first in the top bar. `continentScaffold` is an integer support score, not height or a true distance; `continentSeaMask` uses score <= 0 as water. The old `seaMask` remains available for comparison. The code-generated `build/gallery/continental-comparison.png` compares identical coordinates for seeds 42 and 137.

## Construction and contracts

Each object has an exact signed lattice address `(i,j)` and an independently hashed presence, location, orientation, bends and lobe radii. Object spacing is twice `continentScale`; centers jitter within the middle half of their address cells. Five disks form two bent, two-link arms attached to a central disk. The representation and eight Q1024 directions are versioned protocol choices, not mutable state. Individual objects can cross address-cell boundaries and merge; cell edges are not forced ocean channels.

All shape tuning is in `Params`: `continentScale`, `continentCoverage`, `continentLobeRadius`, `continentLobeVariation`, and `continentArmStep`. Coverage is an object probability, **not a requested land percentage**. These candidate-only controls do not change old field values. The legacy coast also uses `continentScale`, but for a different construction. Noise, plate spacing and relief settings do not affect the candidate.

Lobe scores are `max(-1000, (r*r - distanceSquared)*1000/(r*r))`, using signed integer division; the union takes their maximum. The thin score-zero band counts as water. Each link is at most 0.5 times the scale and each radius is at least 0.3 times the scale, leaving ample overlap even with block/score quantization. Thus each active object's land is connected. This does **not** prove that the union has bounded components or that all water belongs to one ocean.

The address lattice is an index, not a repeating map. Seed plus absolute address defines every object without sequential random draws. A fixed 3x3 neighborhood is enough: an omitted root center is at least 2.5 scales away along one axis; two arm steps extend at most 1 scale, and a lobe can contribute above the -1000 floor only within sqrt(2) * 0.65 scales. Their sum is below 1.92 scales. Integer rounding cannot close that margin at the minimum supported scale. Numeric query limits are not coastline terminals; nearby object centers are evaluated beyond them without clipping.

Before squaring, per-lobe queries reject coordinates more than twice the radius away on either axis. At the maximum scale, the remaining score products fit signed 64-bit integers. Exact objects are memoized in a bounded per-generator neighborhood cache; eviction only affects cost.

## Evidence and limits

Tests compare fixed 3x3 queries against 7x7 objects with independent BigInteger score arithmetic, covering five seeds and eight extreme parameter combinations, including non-step-aligned scales and numeric coordinate limits. They check overlap and sampled connected spines, zero coverage, independent crops, common-point zoom equivalence, shuffled queries, cache eviction and concurrent reads. Eight signed-integer grid hashes freeze the candidate recipe. The existing 160 PNG baselines remain unchanged; historical parameter manifests describe their own milestone, while the new manifest freezes candidate inputs.

The first images intentionally show smooth, visibly rounded lobes. They demonstrate continental scale and connected interiors, **not finished natural coastlines or simulated tectonic history**. Discrete orientations, lattice statistics, merged components and ocean connectivity still need evaluation. Do not promote the scaffold directly to global basin roots: overlapping objects do not supply unique connected-component identities or guaranteed terminal paths.

Next: develop restrained, coast-preserving shape refinement and a root/terminal ownership rule for merged continental domains. Keep global drainage unresolved until that rule is explicit and testable. Then use the existing conditioned-refinement kernel to inherit those commitments, before terrain carving or Minecraft integration.
