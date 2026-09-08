package genesis.harness;

import genesis.core.hash.Lattice;
import genesis.oracle.AuthoredLandmass;
import genesis.oracle.MaritimeEnvelope;
import genesis.oracle.MaritimeEnvelope.Key;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** R2b connected authored-land fixture and independent discrete connectivity evidence. */
final class AuthoredLandmassGates {
    private AuthoredLandmassGates() {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("Invalid authored-domain contract accepted");
    }
    public static void main(String[] args) throws Exception { run(); }
    static void run() throws Exception {
        List<AuthoredLandmass> gallery = new ArrayList<>(); StringBuilder rows = new StringBuilder();
        int cases = 0;
        for (long seed : new long[] {42, -1, 137}) {
            AuthoredLandmass smaller = null;
            for (int percent : new int[] {40, 65}) {
                long start = System.nanoTime();
                var a = new AuthoredLandmass(seed, 524288, 16384, new Key(0, 0), percent);
                double ms = (System.nanoTime() - start) / 1e6;
                int interiorWater = verify(a, seed, 524288, 16384);
                if (smaller != null) for (int p = 0; p < a.size(); p++) check(!smaller.land(p) || a.land(p), "Coverage increase erases existing land");
                smaller = a; gallery.add(a); cases++;
                if (!rows.isEmpty()) rows.append(",\n");
                rows.append("    {\"seed\": ").append(seed).append(", \"targetPercent\": ").append(percent)
                    .append(", \"eligibleSamples\": ").append(a.eligibleCount).append(", \"reachableEligibleSamples\": ").append(a.reachableCount)
                    .append(", \"landSamples\": ").append(a.landCount).append(", \"landComponentsD4\": 1")
                    .append(", \"unconnectedInteriorWaterSamples\": ").append(interiorWater)
                    .append(", \"fullConstructionMs\": ").append(String.format(java.util.Locale.ROOT, "%.3f", ms)).append("}");
            }
        }
        var base = gallery.get(1);
        var cold = new AuthoredLandmass(42, 524288, 16384, new Key(0, 0), 65);
        List<Integer> order = new ArrayList<>(); for (int p = 0; p < base.size(); p++) order.add(p);
        Collections.shuffle(order, new Random(781));
        for (int p : order) {
            check(base.land(p) == cold.land(p) && base.water(p) == cold.water(p)
                && base.growthParent(p) == cold.growthParent(p) && base.distance(p) == cold.distance(p), "Cold/order changed committed samples");
            check(base.landAt(base.x(p), base.z(p)) == base.land(p), "Coordinate lookup differs from canonical index");
        }
        for (int stride : new int[] {1, 3, 11}) for (int p = 0; p < base.size(); p += stride)
            check(base.landAt(base.x(p), base.z(p)) == cold.land(p), "Common-point crop/zoom disagreement");
        try (var pool = Executors.newFixedThreadPool(3)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int k = 0; k < 3; k++) jobs.add(() -> {
                var reconstructed = new AuthoredLandmass(42, 524288, 16384, new Key(0, 0), 65);
                for (int p : order) if (base.land(p) != reconstructed.land(p) || base.water(p) != reconstructed.water(p)) return false;
                return true;
            });
            for (var result : pool.invokeAll(jobs)) check(result.get(), "Concurrent construction changed coastline");
        }
        // Absolute scale, signed addresses and non-default collar; not just one convenient origin.
        for (var key : new Key[] {new Key(-7, 11), new Key(314, -902)}) {
            var a = new AuthoredLandmass(Long.MIN_VALUE, 65536, 8192, key, 90);
            verify(a, Long.MIN_VALUE, 65536, 8192); cases++;
        }
        for (int spacing : new int[] {64, 1048576}) {
            var a = new AuthoredLandmass(Long.MAX_VALUE, spacing, spacing / 8, new Key(-1, 1), 1);
            verify(a, Long.MAX_VALUE, spacing, spacing / 8); cases++;
        }
        rejects(() -> new AuthoredLandmass(0, 524288, 1, new Key(0,0), 50));
        rejects(() -> new AuthoredLandmass(0, 524288, 16384, null, 50));
        rejects(() -> new AuthoredLandmass(0, 524288, 16384, new Key(0,0), 0));
        rejects(() -> new AuthoredLandmass(0, 524288, 16384, new Key(0,0), 91));
        rejects(() -> new AuthoredLandmass(0, 524288, 16384, new Key(Lattice.MAX_COORDINATE / 524288, 0), 50));
        rejects(() -> base.landAt(base.x0 - base.step, base.z0));
        rejects(() -> base.landAt(base.x0 + 1, base.z0));
        rejects(() -> base.land(-1));
        Files.createDirectories(Path.of("build/gallery")); render(gallery);
        Files.writeString(Path.of("build/authored-landmass.json"), """
            {
              "experiment": "authored-landmass-v1",
              "status": "finite canonical connected-growth fixture; not adopted as world continents",
              "domainSpacing": 524288,
              "collar": 16384,
              "domainKey": [0, 0],
              "coarseStep": 8192,
              "fullCanonicalVertices": 37249,
              "independentConnectivityCases": %d,
              "connectivity": "D4, with independent union-find water/land checks",
              "waterTerminals": "explicit reserve samples, never arbitrary box edges",
              "growthTreeIsRiverNetwork": false,
              "audits": [
            %s
              ]
            }
            """.formatted(cases, rows), StandardCharsets.UTF_8);
        System.out.println("PASS R2b AUTHORED: 10 canonical domains, exact connected coverage, independent union-find water certificates, nested growth, cold/order/coordinate/concurrent checks; NOT production continents or rivers");
    }
    private static int verify(AuthoredLandmass a, long seed, int spacing, int collar) {
        var envelope = new MaritimeEnvelope(seed, spacing, collar);
        int n = a.size(), side = AuthoredLandmass.SIDE, count = 0;
        int[] landSet = new int[n], waterSet = new int[n]; boolean[] reserve = new boolean[n];
        for (int p = 0; p < n; p++) { landSet[p] = p; waterSet[p] = p; }
        for (int p = 0; p < n; p++) {
            var sample = envelope.sample(a.x(p), a.z(p)); reserve[p] = sample.reservedOcean();
            check(a.eligible(p) == (sample.owner().equals(a.owner) && !reserve[p]), "Eligibility differs from domain certificate");
            if (a.land(p)) {
                count++; check(a.eligible(p) && a.water(p) == 0, "Land outside safe area or classified water");
                if (p != a.seedIndex) {
                    int parent = a.growthParent(p);
                    check(parent >= 0 && a.land(parent) && a.distance(parent) < a.distance(p), "Growth prefix lacks selected parent");
                    check(Math.abs(p % side - parent % side) + Math.abs(p / side - parent / side) == 1, "Nonlocal growth edge");
                }
                check(p % side > 0 && p % side < side - 1 && p / side > 0 && p / side < side - 1, "Land hits canonical solve-box edge");
            }
            int expectedClass = a.land(p) ? 0 : (sample.owner().equals(a.owner) || reserve[p] ? 2 : 3);
            check(a.water(p) == expectedClass || (expectedClass == 2 && a.water(p) == 1), "Invalid domain water class");
            for (int q : new int[] {p % side == 0 ? -1 : p - 1, p < side ? -1 : p - side}) if (q >= 0) {
                if (a.land(p) && a.land(q)) join(landSet, p, q);
                if (!a.land(p) && !a.land(q) && a.water(p) != 3 && a.water(q) != 3) join(waterSet, p, q);
            }
        }
        check(count == a.landCount && count == Math.max(1, a.reachableCount * a.percent / 100), "Coverage count differs from prefix contract");
        boolean[] hasReserve = new boolean[n];
        for (int p = 0; p < n; p++) if (reserve[p]) hasReserve[root(waterSet, p)] = true;
        int isolated = 0;
        for (int p = 0; p < n; p++) {
            if (a.land(p)) check(root(landSet, p) == root(landSet, a.seedIndex), "Independent union-find sees disconnected land");
            if (a.water(p) == 1 || a.water(p) == 2) {
                check((a.water(p) == 1) == hasReserve[root(waterSet, p)], "Ocean classification lacks independent reserve path");
                if (a.water(p) == 2) isolated++;
            }
        }
        return isolated;
    }
    private static int root(int[] set, int p) { while (set[p] != p) { set[p] = set[set[p]]; p = set[p]; } return p; }
    private static void join(int[] set, int p, int q) { set[root(set, p)] = root(set, q); }
    private static void render(List<AuthoredLandmass> maps) throws Exception {
        BufferedImage image = new BufferedImage(1160, 890, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(new Color(245,247,249)); g.fillRect(0,0,1160,890);
        g.setColor(new Color(25,34,46)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        g.drawString("R2b: connected land grown inside its available domain", 20, 28);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("Same domain scale, seeds 42 / -1 / 137. Top: 40% safe reachable area. Bottom: 65%. One connected fixture per domain.", 20, 53);
        for (int k = 0; k < maps.size(); k++) {
            var a = maps.get(k); int side = AuthoredLandmass.SIDE;
            int minX = side, minZ = side, maxX = 0, maxZ = 0;
            for (int p = 0; p < a.size(); p++) if (a.eligible(p)) {
                minX = Math.min(minX, p % side); maxX = Math.max(maxX, p % side);
                minZ = Math.min(minZ, p / side); maxZ = Math.max(maxZ, p / side);
            }
            minX = Math.max(0,minX - 5); minZ = Math.max(0,minZ - 5);
            maxX = Math.min(side - 1,maxX + 5); maxZ = Math.min(side - 1,maxZ + 5);
            int width = maxX - minX + 1, height = maxZ - minZ + 1;
            BufferedImage crop = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
            for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                int p = z * side + x;
                Color c = a.land(p) ? new Color(126,158,106) : a.water(p) == 2 ? new Color(84,176,183)
                    : a.water(p) == 3 ? new Color(220,224,229) : new Color(40,82,125);
                crop.setRGB(x-minX,z-minZ,c.getRGB());
            }
            int ox = 20 + k / 2 * 380, oy = 100 + k % 2 * 370;
            g.setColor(new Color(25,34,46));
            g.drawString("Seed " + new long[] {42,-1,137}[k/2] + " | land " + a.landCount + " / " + a.reachableCount, ox, oy - 10);
            double scale = Math.min(350.0/width,330.0/height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(crop,ox,oy,(int)(width*scale),(int)(height*scale),null);
        }
        g.setColor(new Color(25,34,46));
        g.drawString("Green: connected land. Blue: water connected to reserved ocean. Cyan: unconnected interior water. Gray: unmodeled neighbor.",20,843);
        g.drawString("Finite coarse growth, not erosion or river routing. Domain-scale / single-landmass bias remains; production viewer is unchanged.",20,869);
        g.dispose(); ImageIO.write(image,"png",Path.of("build/gallery/authored-landmass.png").toFile());
    }
}
