# Minecraft-native tectonic terrain v4

2026-09-16. `tectonic-terrain-v4` replaces the experimental model-metre vertical convention with a block-native surface contract. It remains Java21 research code and is not yet the GTNH adapter.

## Native column contract

Size 1 has a 256-block build column, surface Y in `1..255`, and the Minecraft 1.7.10 overworld sea datum at Y=63. The generator composes crust, plate datum/tilt, detail and boundary relief in size-1 block units. A monotone exponential fit maps the unbounded signal into the finite column separately above and below sea level. The fit preserves the coast sign: every positive relative signal remains at least Y64 and every nonpositive signal remains at or below Y63. It avoids large flat clamp shelves at Y1/Y255 while retaining exact integer surface heights.

Default size-1 vertical controls are: continental relief56, oceanic relief131, detail6, plate datum14, plate tilt19 and relative offset−53 blocks. The previous values were approximately divided by32 before retuning. Boundary profiles retain their dimensionless kinematic classification and convert their old reference anomaly by the same factor. Six wide deterministic samples produce28.90–29.88% land, mean29.4342%; this is a preset result, never a viewport quota.

Default base plate spacing is65,536 blocks. This is large enough for continent-scale travel while being eight times more practical than the old524,288-block research preset. Continental families remain1–4 plates, usually one.

## Exact native upscales

`Settings.size` accepts1–4. It is part of the world configuration and scales the world itself, not a rendered PNG:

- world height = `256 * size`;
- sea level = `63 * size`;
- effective plate spacing and hydrology lattice spacing multiply by size;
- a world query uses `floorDiv(coordinate, size)` in the canonical size-1 generator;
- returned site coordinates, all vertical contributions, surface Y and boundary distance multiply by size.

Therefore the size-N block footprint for canonical `(x,z)` is `[Nx, Nx+N-1] × [Nz, Nz+N-1]`, including negative coordinates under mathematical floor division. Plate/continent IDs, crust fraction, motion and receiver topology are identical. River beds and spill levels multiply byN; rainfall source and accumulated flux multiply byN² because each coarse cell covers N² area. Gates check sizes2,3,4 directly and compare the full size-2 continental drainage graph.

This deliberately creates an integral nearest-neighbor upscale, useful for larger-height worlds and for inspecting scale sensitivity. It is not a claim that vanilla1.7.10 supports taller than256 blocks. The eventual standard GTNH adapter should select size1 unless a compatible tall-world stack is explicitly in scope.

## Viewer

The tectonic viewer exposes20 world parameters including `size`. Metadata, point JSON and PNG headers report size, effective world height and effective sea level. Height colors normalize back to size-1 block meaning; contour spacing is specified in actual blocks. Fullscreen mode uses the browser Fullscreen API on the map panel and rerenders the canvas at512², returning to384² on exit.

## Remaining limits

The output is a 2.5D integer surface. It has no caves, arches, overhangs, block/material column, erosion, sediment, biome climate or fine carved channels. Size>1 intentionally repeats one canonical surface value over an N×N horizontal footprint and emits vertical heights in multiples ofN. Continental hydrology is still coarse complete-family overflow over prescribed maritime margins. These constraints must remain explicit during later Minecraft realization.
