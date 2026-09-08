# M5a: layered material volume before complete water

The user authorized doing independent milestones before returning to hydrology. This is a useful early **part of M5**, not completion of M4, M5 or M6. `genesis-m5a-v1` adds `stratigraphy-v1`; all previous terrain, continent and runoff outputs remain unchanged.

## Safe dependency order

The present graph is `tectonic crust + current uncarved elevation -> stratigraphy and surface material properties`. Geology does **not** feed back into elevation, coast masks, routing or runoff. Its material volume can be evaluated before final water, and later sampled at an explicitly committed erosion surface.

Hardness used to commit a future river network must come from upstream geology at a defined reference surface, not from an exposure map that depends on that same river's incision. This avoids a hardness/incision dependency cycle. Today's exposure is simply where the existing surface intersects the material volume; it is not simulated erosion.

Other worthwhile pre-water tasks remain faults/intrusions and explicit geological input fixtures/scenario loading. Full M6 morphology needs terrain conditioning; precipitation-fed runoff and sediment/soil/deposition conservation cannot be declared complete while their routing inputs remain partial. Hydrology has been deferred, not removed from the remaining work.

## Material contract

`Stratigraphy` consumes only registered `CRUST_TYPE`, `CRUST_AGE` and `BASE_ELEVATION`. The horizontal deformation is an independent seed-domain function of world coordinates. It uses fixed-point smoothstep interpolation on four hashed lattice anchors; there is no repeating tile, viewport dependency, query-order state, or unbounded search.

Let `D = geologyDatum + foldDisplacement` and `t = geologyLayerThickness`. Layer intervals are lower-inclusive and upper-exclusive:

| Vertical interval (model metres) | Material | Synthetic formation age |
| --- | --- | --- |
| below `D-3t` | Basalt on oceanic crust; granite on continental crust | crust age |
| `D-3t` to `D-t` | Shale | floor(3 × crust age / 4) |
| `D-t` to `D` | Limestone | floor(crust age / 2) |
| `D` and above | Sandstone | floor(crust age / 4) |

The basement and upper unit are unbounded **material-volume intervals**, not claims that an infinitely thick deposition event took place. A renderer clips them against the queried terrain surface. This globally draped three-unit cover is an intentionally simple fixture-like world recipe, not a reconstructed geological history or realistic regional sediment provenance. Ages are synthetic and may tie at low crust ages. Basement material/chronology can change at existing plate boundaries; those are not newly implemented fault planes.

All three contacts share the same vertical displacement, so their ordering and thickness cannot invert. These are idealized broad folds/swells, not directional compression, overturned folds or mechanical deformation. Q4096 fixed-point interpolation and integer model-metre contacts introduce bounded quantization; tests cover lattice joins rather than claiming mathematically continuous floating surfaces. At numeric support limits, neighboring hash anchors are still defined—no geological edge is inserted.

`floor(BASE_ELEVATION)` is the sampled surface height. At an exact contact, the upper unit owns the point. Geological contact positions are integer-only; the already-deterministic elevation field is quantized once for material lookup. Water columns still report **seabed bedrock**, not water as a rock type, and no soil mantle is supplied yet.

## Fields and inspection

Five scalar map layers appear in both viewers' top bars:

- `rockType`: exposed material at the current terrain or seabed surface.
- `rockHardness`: relative erosion-resistance coefficient, **not Mohs hardness**.
- `rockWeatherability`: independent relative model coefficient; no chemical weathering is simulated.
- `formationAge`: synthetic age of the sampled unit.
- `strataDisplacement`: contact translation, not a contribution to terrain height.

Four geometry controls and ten material coefficients are registered in `Params` and surfaced automatically. The numerical defaults are explicit model assumptions, not measured material-property data. Changing strength alone cannot move contacts or change rock identity.

Structured values use the same typed field interface in a separate catalog so they are not accidentally treated as numeric image layers:

```java
RockColumn column = world.columns.get(Fields.ROCK_COLUMN, x, z);
RockColumn.Layer material = column.at(y);
```

`RockColumn` is immutable, validates a complete non-overlapping interval sequence and returns defensive array copies. `columns.values(...)` supports exact bulk queries; numerical `tile(...)` rejects structured values clearly. Numeric raster fields remain in `world.fields` (32 fields); the structured catalog has one column field.

`/api/sample` now includes `column` beside `fields`. Both A/B inspectors show top-down intervals, synthetic ages and the surface unit. Null JSON bounds mean unbounded ends; long heights/bounds travel as strings. This separates inspector data from the numeric raster metadata without introducing direct cross-subsystem reads.

## Evidence and limits

`GeologyGates` checks analytic contact ties including negative and extreme vertical coordinates, exact thickness under deformation, bounded one-block jumps at interpolation joins, chronological ordering, material coefficient counterfactuals, raster/column agreement, structured bulk/cold/common-point zoom, shuffled concurrent columns and unchanged pre-geology fields under altered geology parameters. Existing all-field gates now cover 32 scalar fields in cold/shuffled/crop/zoom modes. HTTP images and structured columns are compared against direct core outputs; live viewer-logic smoke tests exercise all five map controls and both column inspectors.

Eight exact SHA-256 snapshots in `harness/golden/geology.properties` cover structured columns and all five scalar fields. Complete default parameters are frozen by the existing continental-world manifest, extended with the 14 new defaults without changing its old field hashes. All 160 historical PNG baselines remain unchanged.

`build/gallery/geology-strata.png` is generated from actual core values and inspected: three same-coordinate maps plus a separate labeled cross-section clipped by terrain. It visibly exposes the simple uniform sediment sequence and abrupt basement province boundaries; neither is presented as finished geological realism. The budget gate now records `continentalGeology512MedianMs` and includes all 32 scalar fields in chunk timings.

Still required for full M5: compact event/provenance lists, regional depositional histories, intrusions, faults with two-sided offset tests, layer orientation, mechanically informed deformation, and exposure driven by committed erosion. M6 scenarios and differential erosion remain separate work. No Minecraft integration has begun.
