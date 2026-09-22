# Drainage-guided dendritic terrain fork

Status: experimental implementation (`dendritic-erosion-v1`) on the
`experiment/dendritic-terrain` branch.

## Decision

Rune Skovbo Johansen's [Fast and Gorgeous Erosion Filter](https://blog.runevision.com/2026/03/fast-and-gorgeous-erosion-filter.html)
is a strong starting point for *erosion morphology*: multiscale directional
stripes are combined so coarse creases steer progressively finer detail. It is
not, by itself, a drainage solver. Treating every local error or depression as
water would make disconnected puddles, allow apparent gullies to cross divides,
and make a lake depend on a decorative signal rather than a water budget.

Genesis therefore splits those responsibilities:

```text
canonical eroded terrain + canonical receiver graph
                         |
        drainage-guided morphology filter
                         |
        canonical channel and lake realization
                         |
                 Minecraft voxelization
```

The filter makes the mountain surface dendritic. `ContinentalHydrology` and
`FluvialNetwork` remain authoritative for where water actually flows, which
depressions are connected lakes, their flat stages, and how channels reach
them. Channel carving and lake filling happen after the filter, so decorative
relief cannot dam a real river or create a fake lake.

## Implemented field

`DendriticErosion` is an independent Java implementation inspired by the
article's point-evaluable construction; it is not a copy or shader translation.
At every absolute block coordinate it:

1. samples the already incised and mass-wasted continental surface;
2. derives local downhill slope with stable central differences;
3. bilinearly blends nearby canonical receiver edges, avoiding angle-wrap
   seams at coarse hydrology cells;
4. blends the physical descent and receiver directions;
5. evaluates four directional phase-cell octaves at default wavelengths 128,
   64, 32 and 16 blocks;
6. feeds each octave's derivative into the next direction and suppresses finer
   detail at established creases/ridges;
7. masks the result by slope, coast distance, rainfall and substrate hardness;
8. clamps the signed relief and never changes sea or canonical lake cells.

The phase kernel uses a 5-by-5 anchor neighborhood with a two-cell compact
support radius. Given the bounded anchor jitter, every contribution reaches
exactly zero before an anchor enters or leaves that neighborhood. This avoids
phase-cell boundary cuts—the most likely source of visibly truncated or tiled
gullies in a pointwise implementation. The 8-block fifth octave was removed
after image inspection because it read as surface speckle rather than branching
landform.

## Determinism and boundaries

The field is a pure function of seed, immutable root state and absolute X/Z.
It has no chunk, crop, zoom, request order, mutable cache or iteration-order
input. The Minecraft fork evaluates it before the existing analytic channel and
lake pass and then uses the same one-block containment halo as classic Genesis.
The result is therefore chunk-order independent and cannot expose a water face
merely because a neighboring chunk has not generated.

The separate `Genesis Dendritic` world type selects this field. The existing
`Genesis` world type remains byte-for-byte on the classic terrain path, making
A/B worlds and rollback straightforward.

## Validation

The executable gate checks cold-instance equality, normalized drainage guides,
bounded signed relief, visible activation, and exact protection of sea/lake
cells. Adapter tests compare cold instances and verify that the fork changes
surface columns while retaining canonical water kind, flux and stream order.
The viewer exposes `dendriticTerrain`, `dendriticDelta` and `dendriticRidges`
at overview and one-block-contour detail scales.

## Honest limits and next step

This is a landform filter, not transported sediment or a block-scale hydraulic
simulation. Small decorative creases do not carry discharge unless the
canonical network also places a channel there. Conversely, an actual river is
always cut by the fluvial layer even if the morphology filter does not draw a
strong crease at that exact point.

The next meaningful upgrade is not “flood every error.” It is to derive a fine
conditioned drainage graph inside each coarse receiver corridor, classify only
closed depressions that have a canonical spill commitment as lakes, and use its
flow accumulation to modulate the same morphology field. That would turn more
of the visual tributaries into audited downhill paths without sacrificing the
smooth point-evaluable fork delivered here.

## Provenance

Conceptual reference: Johansen, *Fast and Gorgeous Erosion Filter* (2026). The
author's related [Bevy example](https://github.com/korbindeman/bevy_erosion_filter)
is MPL-2.0. No upstream source was copied into Genesis; this implementation uses
Genesis data structures, hashing, hydrology and Java code throughout.
