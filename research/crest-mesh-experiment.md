# One-step crest-preserving terrain mesh

Date: 2026-09-09. Version: `crest-mesh-v1`. A finite, one-step geometric realization of the active D4 graph, not arbitrary-depth world refinement.

## Construction

`CrestMesh` converts a complete w-by-h coarse graph into a (2w-1)-by-(2h-1) vertex mesh:

1. Original vertices retain their bed, active state, terminal flags and runoff.
2. Each active cardinal edge gains a midpoint at max(endpoint heights, committed edge crest). An inactive endpoint prevents that edge from existing.
3. A coarse square with four active corners gains a center at the maximum of its four edge-midpoint heights. Otherwise its center is inactive.
4. Each refined quadrilateral is triangulated along the diagonal joining its two coarse-edge midpoints. A triangle exists where its required vertices are active. `surface` evaluates these triangles using integer barycentric weights divided by1024.

All added vertices have zero runoff and are not new terminals. This preserves supplied source ownership without pretending to have distributed rainfall across the new surface area. Inputs are copied into immutable mesh storage. A refined cap of1,048,576 vertices is checked before allocation.

The deliberately high centers make the surface conservative and angular. They are not plausible hillslopes by themselves. Existing creep, erosion, materials and plate-forcing profiles have not been applied to this surface.

## Why diagonals do not lower the committed barriers

Every original coarse D4 path exists on the mesh through its edge midpoints, with the same maximum bed/crest elevation. Therefore refinement cannot make an original vertex's optimal spill worse.

Conversely, the extra fine connections do not supply a cheaper shortcut. Two adjacent edge midpoints can connect through their shared original corner in the coarse graph without exceeding their heights. An inserted center is at least as high as every edge midpoint around its square, so a route through that center can be replaced by a perimeter route without increasing its maximum height. These replacements preserve original-node minimax connectivity, including active-mask holes.

The chosen triangles also matter geometrically: each original corner is no higher than either incident midpoint, and each center is no lower than its surrounding midpoints. Both straight D8 diagonals inside a fine quad are consequently monotone between their endpoint heights under this triangulation. A diagonal hop does not hide a higher intervening crest. One diagonal follows a triangle edge; the other crosses two triangles, with its only internal breakpoint at the midpoint. The tests sample that breakpoint as well as intermediate points.

This is a construction argument for this specific piecewise-linear mesh and its sampled graph. It is not a theorem about arbitrary interpolation, D8 on unrelated noise, continuous steepest-gradient flow, or later vertex displacement.

## Executable evidence

`ActiveHydrology.solveD8Surface` adds an explicitly named D8 vertex-surface audit, with no edge-crest arrays or owner restrictions. The existing D4 method and tie ordering are retained.

`CrestMeshGates` tests 150 random finite coarse graphs with different beds, crests, masks, terminals and sources. Coarse spills are checked by the existing independent D4 relaxation. Fine spills are independently checked by a separate repeated D8 relaxation, and all fine fluxes by individual source walks. At every original vertex, reachability and minimax spill level agree with the coarse graph. Supplied, discharged and unresolved totals agree. Every active fine diagonal is sampled against the triangle evaluator to check monotonicity and absence of hidden obstacles. Extreme integer heights/Long.MAX_VALUE runoff, excessive refinement size and caller-array mutation are checked too.

All three actual mixed-boundary pairs in `PairedDrainageGates` now undergo this refinement. Their49,601 coarse vertices become197,505 fine vertices; all original active spill levels match and all runoff is discharged without loss or unresolved contributions. This extends the prior coarse-edge result to a sampled triangulated surface under D8 routing.

The analytic lowered-pass example remains sensitive after refinement: with crest120 the left basin spills west at80; lowering the pass to30 changes its spill to30 across the divide. `build/gallery/crest-mesh.png` shows an inspected section through the actual two-dimensional mesh. The blue line is an eventual-overflow level, not a simulated instantaneous water surface. `build/crest-mesh.json` records the contract and fixture result; the paired report records its additional fine-mesh check.

## What is deliberately not preserved

Receiver identities, intermediate-node accumulations and the division of flow among equal-cost outlets may change. Added D8 paths can bypass a coarse intermediate vertex or select a different tied route. Thus preserved spill levels plus total mass do **not** establish preservation of a committed river network, every mouth's discharge, or inherited ports. No such claim is made by these gates. A production cascade needs explicit topology/corridor constraints in addition to this geometric compatibility result.

This is one finite refinement with a fixed source graph. It does not yet spread source budgets onto hillslopes, run erosion, generate geological detail, or prove stable arbitrary-depth queries. Production generation, viewer fields and old goldens are unchanged.

Next combine [plate-vector reference forcing](boundary-forcing-experiment.md) with shared junction constraints on a finer surface, without invalidating inherited coast/terminal/crest commitments. Add explicit outlet/port-preserving refinement tests before calling this a river cascade. The source hierarchy and boundary summaries remain the accounting components for that work.
