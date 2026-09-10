# Experimental tectonic terrain viewer

Open [the local tectonic laboratory](http://127.0.0.1:8787/tectonics.html). Start the server with `./genesis.ps1 serve` if needed. Existing `/` and `/continents.html` pages link to it and retain their original models.

This is a separate **Java21 research candidate**, model `tectonic-experimental`, version `tectonic-terrain-v2`. Its [sparse irregular plates](../research/sparse-irregular-plates.md) carry continental and submerged terrain, differing datum/tilt, and shared motion-driven relief. The default plate density is 1/64 of the v1 preset. World hydrology remains unresolved.

## Controls and configuration

All 26 field controls are in the top bar. World controls and an expandable parameter panel are above the map too. The side panel displays readouts and coordinate inspection.

- Terrain, crustal base, total boundary relief, positive relief, negative relief, small detail and land/low-bed classification.
- Continuous crust fraction, candidate plate crust, exact plate identity, velocity X/Z, synthetic age and junction support count.
- Closest-boundary regime, normal motion/compression and signed shear. These diagnostics use candidate crust, not old production labels. Closest-boundary selection is not how composed terrain height is selected.
- Continental-family identity and size (a maximum plate budget), plate scale, plate datum, X/Z tilt, recent-fracture class, joined plate surface, and approximate boundary distance.

Use **Apply world** to apply edited inputs. Draft edits are marked and do not silently change a displayed or exported world. The default uses 524,288-block plate spacing and a continental extent setting of 53. The latter scales crust bodies and is not an exact land fraction. Deep family margins limit connected land to at most four plates; single-member families dominate. The fixed sea-level datum never adapts to the viewport.

Drag to pan, scroll or use +/- to zoom, or focus the canvas and use arrow keys/+/- for keyboard navigation. Click a completed map for exact coordinate inspection. The default raster is384 square; requests support up to512 square. Overview centers on the origin and scales with plate spacing. No client-side resampling changes generator values at common world points.

The URL stores the applied version, seed, coordinates, step, field and all 17 parameters. Old v1 URLs are recognized by their version or removed crust-province controls: the page displays a migration notice and loads the v2 preset while preserving seed and map location. The old terrain is not reproduced. Other unknown keys/versions fail explicitly. **Export PNG + config** downloads the completed image and its matching model/version/configuration. Seeds and plate/family IDs are decimal strings; JavaScript validates signed 64-bit seeds with BigInt. Coordinates remain exact within the existing +/-2^40 support.

## State and HTTP contracts

`/api/tectonic/meta`, `/api/tectonic/render` and `/api/tectonic/sample` are separate read-only endpoints. They never pass through the legacy/continental generator model selector. Unknown keys/fields, duplicate query keys, malformed or out-of-range inputs and unsupported dimensions fail closed. Sampling near the numeric support guard does not require a fictitious raster halo. A point request accepts only absent or unit width/height.

Each render and inspection carries the full relevant world configuration. PNG responses identify model/version and report sampled local land fraction and render time. Point responses include exact seed, coordinate and plate IDs, all fields, canonical closest-boundary IDs and an explicit unresolved-water notice. Layer color scales are fixed display choices, not min/max normalization from the requested crop. Categorical identity colors hash exact IDs only for display.

Each new render aborts its predecessor where possible and increments a revision token. Late responses cannot replace the current committed frame even if cancellation is ignored. Inspections and exports are disabled while the image is stale or failed. Pending inspections are invalidated by a new render. Download configuration comes from the completed frame, not mutable draft controls. The previous image may remain under a rendering/error notice while a new request is pending; it is not presented as the completed requested world.

The candidate's `boundary(x,z)` selects the first two competing irregular plate scores. Offsets follow their weighted/perturbed edges; velocity projections still use the approximate site-separation normal. Its displayed distance is an approximation, not an exact Euclidean distance to the curved boundary. Integer spacings off the slider step remain accepted. Production field fingerprints are unchanged; the new experimental version has its own fingerprint.

## Verification and limits

`TectonicViewGates` runs with the full Java suite against a temporary loopback server. It checks all 26 layers against direct Java rasters, exact inspector/configuration behavior, common-point crop/zoom equality, invalid requests, GET-only routing, static assets and links from both older viewers. It produces `build/tectonic-viewer.json` and an inspected API-generated image at `build/gallery/tectonic-viewer-terrain.png`.

`node harness/gates/tectonic-viewer-contracts.cjs [baseURL]` adds a minimal-DOM application-logic test against a running server. It checks field buttons, full configuration, exact seeds, navigation, draft/apply behavior, inspector results, stale render/inspection isolation, export snapshots and invalid inputs. Both Windows/Linux CI jobs start a temporary server and run this script after the Java suite. Local Java-only testing still does not require Node.

Browser automation discovery in this session returned no available browser surfaces. **Interactive browser layout, painting, pointer behavior and actual download prompts have not been visually verified.** The minimal DOM is not a substitute for those checks. Code-generated terrain images and real HTTP output were inspected; no browser screenshot is claimed.

Below-sea-level bed does not establish ocean connection, lake water level or a downstream terminal. The strict plate-count limit imposes water between families and still produces a cellular tendency at wide scales. Flat-looking interiors, approximate curved-edge normals, synthetic fracture classes and thresholded crust profiles remain research limitations. Next improve continental/coast and mountain-belt morphology while preserving the plate-count, determinism and eventual drainage contracts.
