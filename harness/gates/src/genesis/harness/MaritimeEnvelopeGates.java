package genesis.harness;

import genesis.core.Generator;
import genesis.core.Params;
import genesis.core.fields.Fields;
import genesis.core.elevation.ContinentalScaffold;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import genesis.oracle.MaritimeEnvelope;
import genesis.oracle.MaritimeEnvelope.Key;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Tests the restriction itself and measures its geographic cost; not production promotion. */
final class MaritimeEnvelopeGates {
    private MaritimeEnvelopeGates() {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("Invalid envelope configuration accepted");
    }
    public static void main(String[] args) throws Exception { run(); }
    static void run() throws Exception {
        int comparisons = 0;
        for (long seed : new long[] {0, 42, -1, Long.MIN_VALUE, Long.MAX_VALUE})
            for (int spacing : new int[] {64, 1024, 65536, 1048576}) for (int collar : new int[] {1, spacing / 8}) {
                var envelope = new MaritimeEnvelope(seed, spacing, collar);
                Random random = new Random(seed ^ spacing ^ collar);
                for (int trial = 0; trial < 32; trial++) {
                    long x = trial < 4 ? new long[] {-Lattice.MAX_COORDINATE, -1, 0, Lattice.MAX_COORDINATE}[trial]
                        : random.nextLong(-Lattice.MAX_COORDINATE, Lattice.MAX_COORDINATE + 1);
                    long z = trial < 4 ? x : random.nextLong(-Lattice.MAX_COORDINATE, Lattice.MAX_COORDINATE + 1);
                    var actual = envelope.sample(x, z);
                    var expected = reference(envelope, x, z);
                    check(actual.equals(expected), "5x5 envelope differs from 9x9 BigInteger reference"); comparisons++;
                    var bounds = envelope.bounds(actual.owner());
                    check(x > bounds.x0() && x < bounds.x1() && z > bounds.z0() && z < bounds.z1(), "Owner outside bounded canonical box");
                    if (!actual.reservedOcean()) for (int dx : new int[] {-collar, collar}) for (int dz : new int[] {-collar, collar}) {
                        long xx = x + dx, zz = z + dz;
                        if (Math.abs(xx) <= Lattice.MAX_COORDINATE && Math.abs(zz) <= Lattice.MAX_COORDINATE)
                            check(envelope.sample(xx, zz).owner().equals(actual.owner()), "Certified interior box changes owner");
                    }
                }
                for (int j = -2; j <= 2; j++) for (int i = -2; i <= 2; i++) {
                    var site = envelope.site(i, j); var sample = envelope.sample(site.x(), site.z());
                    check(sample.owner().equals(site.key()) && !sample.reservedOcean(), "A domain lost its nonempty safe interior");
                }
            }
        var envelope = new MaritimeEnvelope(42, 524288, 16384);
        List<Long> points = new ArrayList<>();
        for (long p = -128; p < 128; p++) points.add(p * 8191);
        Collections.shuffle(points, new Random(417L));
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (long p : points) jobs.add(() -> envelope.sample(p, -p).equals(new MaritimeEnvelope(42, 524288, 16384).sample(p, -p)));
            for (var result : pool.invokeAll(jobs)) check(result.get(), "Cold concurrent envelope mismatch");
        }
        rejects(() -> new MaritimeEnvelope(0, 63, 1)); rejects(() -> new MaritimeEnvelope(0, 65, 1));
        rejects(() -> new MaritimeEnvelope(0, 1048640, 1)); rejects(() -> new MaritimeEnvelope(0, 64, 0));
        rejects(() -> new MaritimeEnvelope(0, 64, 9));
        rejects(() -> envelope.sample(Lattice.MAX_COORDINATE + 1, 0));
        rejects(() -> envelope.bounds(new Key(Long.MAX_VALUE, 0)));
        rejects(() -> envelope.site(Long.MAX_VALUE, 0));
        auditAndRender(comparisons);
        wholeObjectComparison();
        System.out.println("PASS R2a ENVELOPE: " + comparisons + " larger-support BigInteger comparisons, clearance/bounds, cold concurrency and continental separation audit; geographic restriction NOT adopted");
    }
    private static MaritimeEnvelope.Sample reference(MaritimeEnvelope envelope, long x, long z) {
        List<MaritimeEnvelope.Site> sites = new ArrayList<>();
        long i = Math.floorDiv(x, envelope.spacing), j = Math.floorDiv(z, envelope.spacing);
        MaritimeEnvelope.Site winner = null; BigInteger best = null;
        for (int dz = -4; dz <= 4; dz++) for (int dx = -4; dx <= 4; dx++) {
            var site = envelope.site(i + dx, j + dz); sites.add(site);
            BigInteger d2 = squared(x, z, site);
            if (best == null || d2.compareTo(best) < 0 || (d2.equals(best) && site.key().compareTo(winner.key()) < 0)) {
                best = d2; winner = site;
            }
        }
        boolean ocean = false;
        // Independently check each corner against every competitor rather than use the L1 formula.
        for (int dx : new int[] {-envelope.collar, envelope.collar}) for (int dz : new int[] {-envelope.collar, envelope.collar}) {
            BigInteger winningDistance = squared(x + dx, z + dz, winner);
            for (var site : sites) if (!site.key().equals(winner.key()) && squared(x + dx, z + dz, site).compareTo(winningDistance) <= 0)
                ocean = true;
        }
        return new MaritimeEnvelope.Sample(winner.key(), ocean, 25);
    }
    private static BigInteger squared(long x, long z, MaritimeEnvelope.Site site) {
        BigInteger dx = BigInteger.valueOf(x).subtract(BigInteger.valueOf(site.x()));
        BigInteger dz = BigInteger.valueOf(z).subtract(BigInteger.valueOf(site.z()));
        return dx.multiply(dx).add(dz.multiply(dz));
    }
    /** Conservative admission, not a new world coast. Each disk lies inside its bounding
     * square; the eroded Voronoi cell is convex, so four safe corners certify the disk.
     * Rejecting an object can expose formerly overlapped coasts, but never slices its disks.
     */
    private static boolean admits(MaritimeEnvelope envelope, ContinentalScaffold.Landmass land) {
        if (!land.active) return false;
        Key owner = envelope.sample(land.centerX(0), land.centerZ(0)).owner();
        for (int lobe = 0; lobe < land.lobes(); lobe++) {
            long r = land.radius(lobe);
            for (long dx : new long[] {-r, r}) for (long dz : new long[] {-r, r}) {
                var corner = envelope.sample(land.centerX(lobe) + dx, land.centerZ(lobe) + dz);
                if (corner.reservedOcean() || !corner.owner().equals(owner)) return false;
            }
        }
        return true;
    }
    private record Components(int count, int largest, int boundaryTouching) {}
    private static Components components(boolean[] land, int size) {
        boolean[] visited = new boolean[land.length]; int[] queue = new int[land.length];
        int count = 0, largest = 0, boundary = 0;
        for (int start = 0; start < land.length; start++) if (land[start] && !visited[start]) {
            count++; int head = 0, tail = 0; queue[tail++] = start; visited[start] = true;
            boolean touches = false;
            while (head < tail) {
                int p = queue[head++], x = p % size, z = p / size;
                touches |= x == 0 || z == 0 || x == size - 1 || z == size - 1;
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, zz = z + dz;
                    if (xx < 0 || zz < 0 || xx >= size || zz >= size) continue;
                    int q = zz * size + xx;
                    if (land[q] && !visited[q]) { visited[q] = true; queue[tail++] = q; }
                }
            }
            largest = Math.max(largest, tail); if (touches) boundary++;
        }
        return new Components(count, largest, boundary);
    }
    private static void wholeObjectComparison() throws Exception {
        check(components(new boolean[9], 3).equals(new Components(0, 0, 0)), "Empty component reference");
        check(components(new boolean[] {true,false,false,false,true,false,false,false,true}, 3)
            .equals(new Components(1, 3, 1)), "D8 diagonal component reference");
        check(components(new boolean[] {false,false,false,false,true,false,false,false,false}, 3)
            .equals(new Components(1, 1, 0)), "Interior component/crop flag reference");
        final int size = 128, spacing = 524288, step = spacing / 32;
        final long origin = -2L * spacing;
        StringBuilder rows = new StringBuilder();
        for (long seed : new long[] {42, -1, 137}) for (int coverage : new int[] {45, 100}) {
            var envelope = new MaritimeEnvelope(seed, spacing, spacing / 32);
            var scaffold = new ContinentalScaffold(seed, new Params(Map.of("continentCoverage", (double) coverage)));
            var objects = new HashMap<Key, ContinentalScaffold.Landmass>();
            var allowed = new HashMap<Key, Boolean>();
            boolean[][] maps = new boolean[3][size * size];
            int[] totals = new int[3];
            for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
                long wx = origin + x * (long) step, wz = origin + z * (long) step;
                long i = Math.floorDiv(wx, scaffold.spacing), j = Math.floorDiv(wz, scaffold.spacing);
                int p = z * size + x;
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    Key key = new Key(i + dx, j + dz);
                    var object = objects.computeIfAbsent(key, k -> scaffold.landmass(k.i(), k.j()));
                    boolean admit = allowed.computeIfAbsent(key, k -> admits(envelope, object));
                    if (object.score(wx, wz) > 0) {
                        maps[0][p] = true;
                        if (admit) maps[2][p] = true;
                    }
                }
                var sample = envelope.sample(wx, wz);
                maps[1][p] = maps[0][p] && !sample.reservedOcean();
                check(!maps[2][p] || maps[1][p], "Admitted object leaked outside safe maritime interior");
                check(maps[0][p] == (scaffold.score(wx, wz) > 0), "Raw scaffold union reference mismatch");
                for (int panel = 0; panel < 3; panel++) if (maps[panel][p]) totals[panel]++;
            }
            List<Key> order = new ArrayList<>(objects.keySet()); Collections.shuffle(order, new Random(718));
            int active = 0, accepted = 0;
            var coldEnvelope = new MaritimeEnvelope(seed, spacing, spacing / 32);
            var coldScaffold = new ContinentalScaffold(seed, new Params(Map.of("continentCoverage", (double) coverage)));
            for (Key key : order) {
                check(allowed.get(key) == admits(coldEnvelope, coldScaffold.landmass(key.i(), key.j())), "Cold/shuffled admission changed whole object");
                if (objects.get(key).active) { active++; if (allowed.get(key)) accepted++; }
            }
            check(totals[0] > totals[1] && totals[1] > totals[2] && totals[2] > 0, "Comparison fixture failed to expose tradeoff");
            if (!rows.isEmpty()) rows.append(",\n");
            rows.append("    {\"seed\": ").append(seed).append(", \"coverage\": ").append(coverage)
                .append(", \"activeSupportObjects\": ").append(active).append(", \"admittedSupportObjects\": ").append(accepted)
                .append(", \"policies\": [");
            for (int panel = 0; panel < 3; panel++) {
                Components c = components(maps[panel], size);
                if (panel > 0) rows.append(", ");
                rows.append("{\"name\": \"").append(new String[] {"raw", "clipped", "wholeObject"}[panel])
                    .append("\", \"landSamples\": ").append(totals[panel]).append(", \"components\": ").append(c.count())
                    .append(", \"largestComponentSamples\": ").append(c.largest())
                    .append(", \"boundaryTouchingComponents\": ").append(c.boundaryTouching()).append("}");
            }
            rows.append("]}");
            if (seed == 42 && coverage == 100) {
                BufferedImage image = new BufferedImage(1230, 525, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics(); g.setColor(new Color(245, 247, 249)); g.fillRect(0, 0, 1230, 525);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19)); g.setColor(new Color(25, 34, 46));
                g.drawString("R2b screening: avoid cutting objects, but at what cost?", 20, 27);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                g.drawString("Seed 42, 100% object presence. Raw object support, NOT the viewer's interpolated coastline. Neither candidate is adopted.", 20, 52);
                String[] labels = {"Original raw land", "Late clipping at ocean reserve", "Whole-object admission only"};
                for (int panel = 0; panel < 3; panel++) {
                    g.drawString(labels[panel] + " | " + totals[panel] + " land samples", 20 + 407 * panel, 80);
                    BufferedImage map = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
                    for (int p = 0; p < maps[panel].length; p++) map.setRGB(p % size, p / size,
                        (maps[panel][p] ? new Color(126, 158, 106) : new Color(40, 82, 125)).getRGB());
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(map, 20 + panel * 407, 94, 384, 384, null);
                }
                g.drawString("Whole objects retain their shape, but many disappear. This is a rejection baseline, not geography authored to fit the domain.", 20, 507);
                g.dispose(); ImageIO.write(image, "png", Path.of("build/gallery/maritime-object-admission.png").toFile());
            }
        }
        Files.writeString(Path.of("build/maritime-object-admission.json"), """
            {
              "experiment": "maritime-object-admission-v1",
              "status": "R2b screening only; not a production coast or terminal construction",
              "source": "raw five-lobe scaffold, before coarse interpolation",
              "sampleOrigin": [-1048576, -1048576],
              "sampleSize": [128, 128],
              "sampleStep": 16384,
              "domainSpacing": 524288,
              "collar": 16384,
              "componentConnectivity": "D8; crop-truncated components included, not global basin statistics",
              "audits": [
            %s
              ]
            }
            """.formatted(rows), StandardCharsets.UTF_8);
    }
    private record Audit(int collar, int sourceLand, int removedLand, int reserve,
                         int originalCrossings, int candidateCrossings) {}
    private static void auditAndRender(int comparisons) throws Exception {
        int size = 256, spacing = 524288, step = spacing / 64;
        long origin = -2L * spacing;
        var world = new Generator(42, Params.defaults(), Generator.Model.CONTINENTAL);
        boolean[] land = new boolean[size * size];
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++)
            land[z * size + x] = world.fields.get(Fields.SEA_MASK, origin + x * step, origin + z * step) == 0;
        List<Audit> audits = new ArrayList<>();
        MaritimeEnvelope.Sample[] display = null;
        for (int collar : new int[] {spacing / 64, spacing / 32, spacing / 16}) {
            var envelope = new MaritimeEnvelope(42, spacing, collar);
            var samples = new MaritimeEnvelope.Sample[size * size];
            int source = 0, removed = 0, reserved = 0, originalCrossings = 0, candidateCrossings = 0;
            for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
                int p = z * size + x;
                samples[p] = envelope.sample(origin + x * step, origin + z * step);
                if (land[p]) { source++; if (samples[p].reservedOcean()) removed++; }
                if (samples[p].reservedOcean()) reserved++;
            }
            for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
                int p = z * size + x;
                for (int dz = 0; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    if ((dz == 0 && dx <= 0) || x + dx < 0 || x + dx >= size || z + dz >= size) continue;
                    int q = (z + dz) * size + x + dx;
                    if (!samples[p].owner().equals(samples[q].owner())) {
                        check(samples[p].reservedOcean() || samples[q].reservedOcean(), "Safe land can bridge domain boundary on this mesh");
                        if (land[p] && land[q]) {
                            originalCrossings++;
                            if (!samples[p].reservedOcean() && !samples[q].reservedOcean()) candidateCrossings++;
                        }
                    }
                }
                // Same world point sampled through a shifted crop and changed image density.
                if (x % 17 == 0 && z % 19 == 0) check(samples[p].equals(new MaritimeEnvelope(42, spacing, collar)
                    .sample((origin - step * 5) + (x + 5L) * step, origin + (z / 19L) * (step * 19L))), "Crop/common-point zoom changed domain");
            }
            check(source > 0 && removed > 0 && originalCrossings > 0 && candidateCrossings == 0, "Fixture failed to expose real land/ownership conflict");
            audits.add(new Audit(collar, source, removed, reserved, originalCrossings, candidateCrossings));
            if (collar == spacing / 32) display = samples;
        }
        check(audits.get(0).removedLand() <= audits.get(1).removedLand() && audits.get(1).removedLand() <= audits.get(2).removedLand(), "Wider ocean collar removed less land");
        writeImage(land, display, audits.get(1));
        StringBuilder rows = new StringBuilder();
        for (Audit audit : audits) {
            if (!rows.isEmpty()) rows.append(",\n");
            rows.append("    {\"collar\": ").append(audit.collar()).append(", \"sampledOriginalLand\": ").append(audit.sourceLand())
                .append(", \"sampledRemovedLand\": ").append(audit.removedLand()).append(", \"sampledOceanReserve\": ").append(audit.reserve())
                .append(", \"originalCrossOwnerLandEdges\": ").append(audit.originalCrossings())
                .append(", \"candidateCrossOwnerLandEdges\": ").append(audit.candidateCrossings()).append("}");
        }
        Files.writeString(Path.of("build/maritime-envelope.json"), """
            {
              "experiment": "maritime-envelope-v1",
              "status": "geographic restriction under evaluation; not adopted into production",
              "seed": 42,
              "spacing": 524288,
              "sampleOrigin": [-1048576, -1048576],
              "sampleStep": 8192,
              "sampleSize": [256, 256],
              "sourceModel": "continental",
              "sourceParams": "Params.defaults at genesis-m5a-v1",
              "largerSupportReferenceComparisons": %d,
              "sitesPerPoint": 25,
              "canonicalBoxSideInSpacings": 3,
              "proposed64CellsPerSpacingSolveVertices": 37249,
              "globalSampledWaterConnectivityProved": false,
              "audits": [
            %s
              ]
            }
            """.formatted(comparisons, rows), StandardCharsets.UTF_8);
    }
    private static void writeImage(boolean[] land, MaritimeEnvelope.Sample[] samples, Audit audit) throws Exception {
        BufferedImage image = new BufferedImage(1230, 535, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(245, 247, 249)); g.fillRect(0, 0, 1230, 535);
        g.setColor(new Color(25, 34, 46)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        g.drawString("R2a: what bounded maritime domains would cost the current continents", 20, 27);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("Experimental restriction only. Same world coordinates in all panels; domain boundaries are not viewport edges.", 20, 51);
        String[] titles = {"Current continental land / water", "Candidate: red land would become sea", "Domain identities and reserved ocean"};
        for (int panel = 0; panel < 3; panel++) {
            BufferedImage map = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
            for (int p = 0; p < land.length; p++) {
                Color color;
                if (panel < 2) color = land[p] ? (panel == 1 && samples[p].reservedOcean() ? new Color(210, 71, 45) : new Color(126, 158, 106)) : new Color(40, 82, 125);
                else {
                    long h = Hash64.hash(42, 0, samples[p].owner().i(), samples[p].owner().j());
                    color = samples[p].reservedOcean() ? new Color(40, 82, 125)
                        : new Color(125 + (int) (h & 63), 140 + (int) ((h >>> 8) & 63), 130 + (int) ((h >>> 16) & 63));
                }
                map.setRGB(p % 256, p / 256, color.getRGB());
            }
            g.setColor(new Color(25, 34, 46)); g.drawString(titles[panel], 20 + panel * 407, 80);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(map, 20 + panel * 407, 94, 384, 384, null);
        }
        g.setColor(new Color(25, 34, 46));
        g.drawString("Removed sampled land: " + audit.removedLand() + " / " + audit.sourceLand()
            + ". Cross-domain land edges: " + audit.originalCrossings() + " before, " + audit.candidateCrossings() + " after.", 20, 503);
        g.drawString("This buys bounded separation by changing geography. It is not a watershed solve or a natural-coast acceptance result.", 20, 526);
        g.dispose(); Files.createDirectories(Path.of("build/gallery"));
        ImageIO.write(image, "png", Path.of("build/gallery/maritime-envelope.png").toFile());
    }
}
