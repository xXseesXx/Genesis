# Experimental tectonic terrain viewer

Open [the local tectonic laboratory](http://127.0.0.1:8787/tectonics.html). Start the server with `./genesis.ps1 serve` or `./genesis.sh serve` if needed. Existing `/` and `/continents.html` pages retain their original models.

This is the separate Java21 research candidate `tectonic-experimental`, terrain `tectonic-terrain-v4`, hydrology `continental-hydrology-v1`. Its [Minecraft-native scale contract](../research/minecraft-native-tectonic-terrain.md) makes size 1 a 256-block column with sea Y=63. Sizes 2-4 are exact integral world upscales. Continental rainfall/overflow supplies coarse rivers and filled-lake diagnostics; fine channel realization remains open.

## Controls and configuration

All 33 fields are in the top bar. The expandable panel contains 20 world parameters, including `size`. The main layers cover native terrain height, crust/plate identity, motion, datum/tilt, boundary regime, rainfall, rivers, lakes and drainage status.

- **Height + contours** uses fixed colors and display-only isolines. Spacing accepts 0 (off) or 1-256 actual blocks. Every fifth line is heavier. Intervals coarsen deterministically at wide zooms. Colors normalize to size-1 height meaning; inspection reports effective surface Y.
- **Rivers + lakes** and its five diagnostics use the complete irregular continental-family footprint, never the canvas boundary. Rainfall defaults to 1000 model mm/year. Zero rain removes flux, not potential spill geometry. Pink is unresolved/unavailable support.
- **Size** scales the world, not the PNG. World height is `256 * size`, sea level is `63 * size`, and effective plate spacing is `base plate spacing * size`. The default base plate spacing is 65,536 blocks.

Use **Apply world** to commit edited inputs. Draft controls never silently alter the displayed/exported frame. The native preset averages 29.4342% land across six wide samples after the fixed -53-block relative offset. This is not a per-viewport quota. Continental families still contain 1-4 plates and are usually singletons.

Drag to pan, scroll or use +/- to zoom, and click a completed map to inspect it. The normal raster is 384 square. **Fullscreen** gives the map panel the browser fullscreen surface and rerenders at 512 square; exit or Escape restores 384. Overview centers on the origin using effective plate spacing.

The URL stores terrain/hydrology versions, seed, coordinates, step, field, all 20 parameters and separate contour spacing. v1/v2/v3 links visibly migrate to v4, preserving seed/location while loading native defaults. Export downloads the completed PNG plus matching JSON configuration. IDs and water ledgers remain exact decimal strings.

## HTTP and state contracts

`/api/tectonic/meta`, `/api/tectonic/render` and `/api/tectonic/sample` are read-only endpoints. Unknown/duplicate/out-of-range inputs fail closed. Each response carries its full world configuration.

PNG headers include model/version, hydrology version, render time, local land fraction, actual contour interval, `X-Native-Scale`, `X-World-Height` and `X-Sea-Level`. Point JSON includes the same native dimensions, all fields, exact IDs, closest-boundary data and whole-family hydrology ledgers. Colors use fixed scales, never crop min/max normalization.

New renders use cancellation plus revision tokens. Late renders or inspections cannot replace a newer frame. Exports remain disabled for stale/failed frames and serialize the last completed configuration. Hydrology preflights complete continent support and rejects views beyond 24 families or 8 million support vertices; zoom in when that bound is reached. Near the numeric guard, unavailable support remains explicit status 0 rather than a clipped outlet.

## Verification and limits

`TectonicTerrainGates` verifies size-1 Y bounds/sea level, 29-30% land, motion relief, mountain joins, fixed support and exact sizes 2/3/4. `ContinentalHydrologyGates` verifies full-support water budgets and exact size-2 graph scaling: doubled beds/spills, unchanged receiver topology and quadrupled rainfall/flux. `TectonicViewGates` checks all 33 layers, size-2 response headers, direct/HTTP equality, crop/zoom invariance, rivers and one-block contours.

`node harness/gates/tectonic-viewer-contracts.cjs [baseURL]` checks all 20 parameters and a Fullscreen API enter/exit cycle with 512/384 rerenders, plus navigation, draft/apply behavior, exact seeds, inspector data, stale responses and exports. Generated terrain, height, mountain and river images were inspected. The current automation environment exposed no real browser surface, so browser painting/pointer QA is not claimed.

The output remains a 2.5D integer surface: no caves, arches, erosion, sediment, biome climate or fine carved channels. Size greater than 1 intentionally repeats a canonical value over an N-by-N footprint and emits heights in multiples of N. Standard Minecraft 1.7.10 integration should use size 1 unless a tall-world stack is explicitly supported.
