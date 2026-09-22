# Minecraft-native tectonic terrain v8

The block-native surface contract began with v4 on 2026-09-16 and is current in `tectonic-terrain-v8`. The Java 21 oracle is consumed by the experimental GTNH adapter; it remains separate from the older Java 8 `Generator.Model.CONTINENTAL` path.

## Native column contract

Size 1 has a 256-block build column, surface Y in `1..255`, and the Minecraft 1.7.10 overworld sea datum at Y63. The generator composes crust, plate datum/tilt, detail and boundary relief in size-1 block units. Below sea, a monotone exponential fit avoids a hard floor. Above sea, v8 removes the former concave hyperbola: normalized tectonic rise maps linearly into the available column and clamps only at Y255. Detail now evaluates analytic quintic value-noise derivatives and accumulates each octave as `w*n/(1+dx²+dz²)`, following Iñigo Quílez's modified-fBm construction. Integer block rounding still applies.

Core size-1 defaults remain continental relief 56, oceanic relief 131, detail 6, plate datum 14, plate tilt 19 and relative offset −53 blocks. The viewer and Minecraft adapter use a more dramatic preset: continental relief 64, boundary gain 1,450‰, detail 10, ridge roughness 170‰, plate datum 18 and plate tilt 24. The horizontal v8 preset uses **2,048-block base plate spacing**, four times smaller in X/Z than the prior 8,192 setting and 32 times smaller than the original v4 65,536 setting. World height and sea level did not change. Continental families remain 1–4 plates, usually one.

All dependent horizontal scales derive from plate spacing: site geometry, detail wavelengths, substrate contacts, wind modes, hydrology lattice and river corridors. Default hydrology step is therefore 32 blocks (`ceil(2048 / 64)`), down from 128. Normalized complete-family root dimensions are unchanged, so a single root keeps the same asymptotic work; an equal explored X/Z area can encounter 16 times as many roots, increasing cache churn.

## Exact native upscales

`Settings.size` accepts 1–4. It is part of world configuration and scales the world itself, not a rendered PNG:

- world height = `256 * size`;
- sea level = `63 * size`;
- effective plate spacing and hydrology lattice spacing multiply by size;
- a world query uses `floorDiv(coordinate, size)` in the canonical size-1 generator;
- returned site coordinates, vertical contributions, surface Y and boundary distance multiply by size.

Therefore the size-N block footprint for canonical `(x,z)` is `[Nx, Nx+N-1] × [Nz, Nz+N-1]`, including negative coordinates under mathematical floor division. Plate/continent IDs, crust fraction, motion and receiver topology are identical. River beds and spill levels multiply by N; a coarse cell's area and effective-runoff flux multiply by N². Gates check sizes 2, 3 and 4 directly and compare the size-2 drainage graph.

This nearest-neighbor upscale is useful for inspecting scale sensitivity. It is not a claim that vanilla 1.7.10 supports worlds taller than 256 blocks. The adapter selects size 1 unless a compatible tall-world stack is explicitly added.

## Viewer and migration

The tectonic viewer exposes 21 world parameters including `size`, plus 46 terrain/climate/ground/water layers. Metadata, point JSON and PNG headers report size, world height, sea level and the terrain/hydrology/fluvial/climate/wind/substrate versions. Height colors normalize to size-1 block meaning; contours use actual blocks. Saved v5/v6 links divide X, Z, sampling step and plate spacing by four exactly once when migrated to the current compact world; compatible vertical settings remain.

## Remaining limits

The output is a 2.5D integer surface. It has no caves, arches or overhangs. The coupled path now adds erosion, material horizons, climate-driven effective runoff, rivers and lakes, but it still lacks seasonal temperature/snow, groundwater/karst, lake storage balance, fluvial sediment and a proof of fine block-level drainage. Size >1 intentionally repeats one canonical value over an N-by-N footprint and emits vertical heights in multiples of N. Smaller continents also mean more root-cache turnover per travelled distance; a pinned flight-speed benchmark remains necessary.
