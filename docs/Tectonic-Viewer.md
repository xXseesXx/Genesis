# Experimental tectonic terrain viewer

Open [the local tectonic laboratory](http://127.0.0.1:8787/tectonics.html). Start the server with `./genesis.ps1 serve` if needed. Existing `/` and `/continents.html` pages link to it and retain their original models.

This is a separate **Java21 research candidate**, model `tectonic-experimental`, version `tectonic-terrain-v1`. It consumes [correlated crust, broad absolute elevation and shared boundary forcing](../research/tectonic-terrain-experiment.md). It does not promote that algorithm to the Java8 production core or claim complete world hydrology.

## Controls and configuration

All seventeen field controls are in the top bar. World controls and an expandable parameter panel are above the map too; there is no new field sidebar. The side panel displays readouts and coordinate inspection.

- Terrain, crustal base, total boundary relief, positive relief, negative relief, small detail and land/low-bed classification.
- Continuous crust fraction, candidate plate crust, exact plate identity, velocity X/Z, synthetic age and junction support count.
- Closest-boundary regime, normal motion/compression and signed shear. These diagnostics use candidate crust, not old production labels. Closest-boundary selection is not how composed terrain height is selected.

Use **Apply world** to apply edited inputs. Draft edits are marked and do not silently change a displayed or exported world. Province grouping4 uses the correlated candidate;1 retains the independent-crust comparison. Province probability is not an exact land fraction. The fixed sea-level datum never adapts to the viewport.

Drag to pan, scroll or use +/- to zoom, or focus the canvas and use arrow keys/+/- for keyboard navigation. Click a completed map for exact coordinate inspection. The default raster is384 square; requests support up to512 square. Overview centers on the origin and scales with plate spacing. No client-side resampling changes generator values at common world points.

The URL stores the applied seed, coordinates, step, field and all14 relevant parameters. **Export PNG + config** downloads the completed image and its matching model/version/configuration. Downloads are user-triggered, not automatic. If the browser restricts consecutive downloads, its normal user permission flow applies. Seeds and plate IDs are strings in JSON; JavaScript uses BigInt to validate and canonicalize signed64-bit seeds. Coordinates remain exact within the existing +/-2^40 support.

## State and HTTP contracts

`/api/tectonic/meta`, `/api/tectonic/render` and `/api/tectonic/sample` are separate read-only endpoints. They never pass through the legacy/continental generator model selector. Unknown keys/fields, duplicate query keys, malformed or out-of-range inputs and unsupported dimensions fail closed. Sampling near the numeric support guard does not require a fictitious raster halo. A point request accepts only absent or unit width/height.

Each render and inspection carries the full relevant world configuration. PNG responses identify model/version and report sampled local land fraction and render time. Point responses include exact seed, coordinate and plate IDs, all fields, canonical closest-boundary IDs and an explicit unresolved-water notice. Layer color scales are fixed display choices, not min/max normalization from the requested crop. Categorical identity colors hash exact IDs only for display.

Each new render aborts its predecessor where possible and increments a revision token. Late responses cannot replace the current committed frame even if cancellation is ignored. Inspections and exports are disabled while the image is stale or failed. Pending inspections are invalidated by a new render. Download configuration comes from the completed frame, not mutable draft controls. The previous image may remain under a rendering/error notice while a new request is pending; it is not presented as the completed requested world.

The candidate's additional `boundary(x,z)` method preserves the core's closest-face geometry but reconstructs both participating plate descriptors with candidate crust. It is only diagnostic. The original field fingerprint is unchanged. Off-slider integer plate spacings remain accepted; subordinate noise wavelength uses floor(S/8), with no change to default/reference outputs.

## Verification and limits

`TectonicViewGates` runs with the full Java suite against a temporary loopback server. It checks all17 layers against direct Java rasters, exact inspector/configuration behavior, common-point crop/zoom equality, invalid requests, GET-only routing, static assets and links from both older viewers. It produces `build/tectonic-viewer.json` and an inspected API-generated image at `build/gallery/tectonic-viewer-terrain.png`.

`node harness/gates/tectonic-viewer-contracts.cjs [baseURL]` adds a minimal-DOM application-logic test against a running server. It checks field buttons, full configuration, exact seeds, navigation, draft/apply behavior, inspector results, stale render/inspection isolation, export snapshots and invalid inputs. Both Windows/Linux CI jobs start a temporary server and run this script after the Java suite. Local Java-only testing still does not require Node.

Browser automation discovery in this session returned no available browser surfaces. **Interactive browser layout, painting, pointer behavior and actual download prompts have not been visually verified.** The minimal DOM is not a substitute for those checks. Code-generated terrain images and real HTTP output were inspected; no browser screenshot is claimed.

This viewer deliberately has no global rivers. Below-sea-level bed does not establish ocean connection, lake water level or a downstream terminal. Simple interior plateaus, straight boundary geometry and provisional crust grouping remain visible research limitations. Next use this shared viewer to assess and improve mountain-belt width, interior relief and coast morphology, preserving the separate unresolved root/terminal and drainage-refinement contracts.
