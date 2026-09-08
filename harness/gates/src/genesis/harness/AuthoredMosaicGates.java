package genesis.harness;

import genesis.oracle.AuthoredLandmass;
import genesis.oracle.MaritimeEnvelope;
import genesis.oracle.MaritimeEnvelope.Key;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Finite multi-domain audit. The image rectangle never supplies land or water decisions. */
final class AuthoredMosaicGates {
    private static final int SIDE = 257;
    private record Sample(Key owner, boolean land, int water, boolean reserve) {}
    private record Components(int count, int largest, int edgeTouching, int complete) {}
    private record Audit(long seed, int spacing, int percent, Sample[] samples, int roots,
                         int land, int reserve, int unconnected, Components d4, Components d8,
                         int minRootLand, int maxRootLand, double constructionMs) {}

    /** Test-local memoization; this is not an unbounded production cache or a field API. */
    private static final class Fixture {
        final long seed;
        final int spacing, step, percent;
        final MaritimeEnvelope envelope;
        final Map<Key, AuthoredLandmass> roots = new LinkedHashMap<>();
        Fixture(long seed, int spacing, int percent) {
            this.seed = seed; this.spacing = spacing; this.percent = percent; step = spacing / 64;
            envelope = new MaritimeEnvelope(seed, spacing, spacing / 32);
        }
        Sample sample(long x, long z) {
            if (Math.floorMod(x, step) != 0 || Math.floorMod(z, step) != 0)
                throw new IllegalArgumentException("Mosaic audit reads canonical mesh vertices only");
            var e = envelope.sample(x, z);
            var a = roots.computeIfAbsent(e.owner(), key ->
                new AuthoredLandmass(seed, spacing, spacing / 32, key, percent));
            int p = Math.toIntExact((z - a.z0) / step * AuthoredLandmass.SIDE + (x - a.x0) / step);
            boolean land = a.landAt(x, z); // Bounds/alignment checked before indexed access.
            return new Sample(e.owner(), land, a.water(p), e.reservedOcean());
        }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception { run(); }
    static void run() throws Exception {
        componentFixtures();
        List<Audit> audits = new ArrayList<>();
        for (int spacing : new int[] {65536, 524288}) for (long seed : new long[] {42, -1, 137}) {
            Audit lower = null;
            for (int percent : new int[] {65, 90}) {
                Audit audit = audit(seed, spacing, percent);
                if (lower != null) for (int p = 0; p < audit.samples.length; p++)
                    check(!lower.samples[p].land || audit.samples[p].land, "Mosaic coverage is not nested");
                lower = audit; audits.add(audit);
            }
        }
        stability(audits.get(6));
        write(audits); render(audits.subList(6, 12));
        System.out.println("PASS R2b MOSAIC: 12 multi-domain/scale fixtures; D4/D8 separation, nested coverage, cold crop/order/stride/concurrency; geography NOT adopted");
    }
    private static void componentFixtures() {
        Sample sea = new Sample(new Key(0,0), false, 1, true);
        Sample land = new Sample(new Key(0,0), true, 0, false);
        Sample[] grid = new Sample[SIDE * SIDE]; Arrays.fill(grid, sea);
        grid[100 * SIDE + 100] = land; grid[101 * SIDE + 101] = land;
        check(components(grid, false).equals(new Components(2,1,0,2)), "D4 diagonal fixture");
        check(components(grid, true).equals(new Components(1,2,0,1)), "D8 diagonal fixture");
        grid[0] = land; grid[1] = land;
        check(components(grid, false).equals(new Components(3,2,1,2)), "Boundary component fixture");
        check(components(grid, true).equals(new Components(2,2,1,1)), "D8 boundary component fixture");
    }
    private static Audit audit(long seed, int spacing, int percent) {
        long start = System.nanoTime();
        Fixture f = new Fixture(seed, spacing, percent);
        Sample[] samples = new Sample[SIDE * SIDE];
        int land = 0, reserve = 0, unconnected = 0;
        for (int p = 0; p < samples.length; p++) {
            var s = f.sample(-spacing + p % SIDE * (long) f.step, -spacing + p / SIDE * (long) f.step);
            samples[p] = s;
            if (s.land) { land++; check(!s.reserve && s.water == 0, "Land overlaps maritime reserve"); }
            else check(s.water == 1 || s.water == 2, "Owner's complete domain returned unmodeled water");
            if (s.reserve) { reserve++; check(s.water == 1, "Reserve lost its explicit terminal certificate"); }
            if (s.water == 2) unconnected++;
            // D8 is stricter than the growth model's D4: no diagonal escape across ownership either.
            for (int dz = -1; dz <= 0; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dz == 0 && dx >= 0) continue;
                int x = p % SIDE + dx, z = p / SIDE + dz;
                if (x < 0 || z < 0 || x >= SIDE) continue;
                var q = samples[z * SIDE + x];
                if (s.land && q.land) check(s.owner.equals(q.owner), "Different domains touch through D8 land");
            }
        }
        int min = Integer.MAX_VALUE, max = 0;
        for (var root : f.roots.values()) { min = Math.min(min, root.landCount); max = Math.max(max, root.landCount); }
        return new Audit(seed, spacing, percent, samples, f.roots.size(), land, reserve, unconnected,
            components(samples, false), components(samples, true), min, max, (System.nanoTime() - start) / 1e6);
    }
    /** Independent flood of the assembled display. Cut components are explicitly identified. */
    private static Components components(Sample[] samples, boolean diagonal) {
        boolean[] visited = new boolean[samples.length]; int[] queue = new int[samples.length];
        int count = 0, largest = 0, touching = 0;
        for (int p = 0; p < samples.length; p++) if (samples[p].land && !visited[p]) {
            int head = 0, tail = 1; queue[0] = p; visited[p] = true; boolean edge = false;
            while (head < tail) {
                int q = queue[head++], x = q % SIDE, z = q / SIDE;
                edge |= x == 0 || z == 0 || x == SIDE - 1 || z == SIDE - 1;
                check(samples[q].owner.equals(samples[p].owner), "A land component spans distinct domains");
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0 || !diagonal && Math.abs(dx) + Math.abs(dz) != 1) continue;
                    int nx = x + dx, nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= SIDE || nz >= SIDE) continue;
                    int r = nz * SIDE + nx;
                    if (samples[r].land && !visited[r]) { visited[r] = true; queue[tail++] = r; }
                }
            }
            count++; largest = Math.max(largest, tail); if (edge) touching++;
        }
        return new Components(count, largest, touching, count - touching);
    }
    private static void stability(Audit reference) throws Exception {
        int spacing = reference.spacing, step = spacing / 64;
        List<Integer> points = new ArrayList<>();
        // An interior crop across several owners, read at two strides plus arbitrary boundary points.
        for (int stride : new int[] {7, 13}) for (int z = 37; z < 219; z += stride)
            for (int x = 29; x < 225; x += stride) points.add(z * SIDE + x);
        points.addAll(List.of(0, SIDE - 1, SIDE * (SIDE - 1), SIDE * SIDE - 1, 128 * SIDE + 128));
        Collections.shuffle(points, new Random(9042));
        Fixture cold = new Fixture(reference.seed, spacing, reference.percent);
        for (int p : points) check(reference.samples[p].equals(cold.sample(-spacing + p % SIDE * (long) step,
            -spacing + p / SIDE * (long) step)), "Cold crop/order/stride changes common world sample");
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int task = 0; task < 2; task++) {
                final boolean reverse = task == 1;
                jobs.add(() -> {
                    Fixture fresh = new Fixture(reference.seed, spacing, reference.percent);
                    for (int k = 0; k < points.size(); k++) {
                        int p = points.get(reverse ? points.size() - k - 1 : k);
                        if (!reference.samples[p].equals(fresh.sample(-spacing + p % SIDE * (long) step,
                            -spacing + p / SIDE * (long) step))) return false;
                    }
                    return true;
                });
            }
            for (var result : pool.invokeAll(jobs)) check(result.get(), "Parallel cold mosaics disagree");
        }
    }
    private static void write(List<Audit> audits) throws Exception {
        StringBuilder rows = new StringBuilder();
        for (var a : audits) {
            if (!rows.isEmpty()) rows.append(",\n");
            rows.append(String.format(Locale.ROOT,
                "    {\"seed\":%d,\"spacing\":%d,\"percent\":%d,\"rootsConstructed\":%d,\"landSamples\":%d,\"landFraction\":%.6f,\"reserveSamples\":%d,\"unconnectedWaterSamples\":%d,\"d4Components\":%d,\"d8Components\":%d,\"largestD4\":%d,\"edgeTouchingD4\":%d,\"completeD4\":%d,\"minFullRootLand\":%d,\"maxFullRootLand\":%d,\"fullAuditMs\":%.3f}",
                a.seed, a.spacing, a.percent, a.roots, a.land, a.land / (double) a.samples.length,
                a.reserve, a.unconnected, a.d4.count, a.d8.count, a.d4.largest, a.d4.edgeTouching,
                a.d4.complete, a.minRootLand, a.maxRootLand, a.constructionMs));
        }
        Files.createDirectories(Path.of("build/gallery"));
        Files.writeString(Path.of("build/authored-mosaic.json"), """
            {
              "experiment":"authored-mosaic-v1",
              "status":"finite multi-domain audit; geography not adopted",
              "window":"[-S,3S] inclusive on each axis; display bounds only",
              "side":257,
              "collarRatio":"1/32",
              "scaleMeaning":"different world recipes, not viewer zoom or a mixed-scale construction",
              "componentMeaning":"sampled D4/D8 components; edge-touching components may be truncated or split",
              "rootCountMeaning":"every owner sampled, including water-only owners; full canonical roots built",
              "audits":[
            %s
              ]
            }
            """.formatted(rows));
    }
    private static void render(List<Audit> audits) throws Exception {
        BufferedImage image = new BufferedImage(1150, 870, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(new Color(245,247,249)); g.fillRect(0,0,1150,870);
        g.setColor(new Color(25,34,46)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        g.drawString("R2b: adjoining complete domains reveal the geographic restriction", 20, 28);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("4S-wide windows | S = 524288 | columns: seeds 42 / -1 / 137 | top: 65% safe area; bottom: 90%",20,52);
        for (int k = 0; k < audits.size(); k++) {
            Audit a = audits.get(k); BufferedImage map = new BufferedImage(SIDE,SIDE,BufferedImage.TYPE_INT_RGB);
            for (int p = 0; p < a.samples.length; p++) {
                var s = a.samples[p];
                Color color = s.land ? new Color(126,158,106) : s.water == 2 ? new Color(84,176,183)
                    : s.reserve ? new Color(21,54,90) : new Color(40,82,125);
                map.setRGB(p % SIDE,p / SIDE,color.getRGB());
            }
            int x = 20 + k / 2 * 375, y = 102 + k % 2 * 355;
            g.drawString(String.format(Locale.ROOT,"Seed %d | land %.1f%% | D4 components %d",a.seed,
                100.0 * a.land / a.samples.length,a.d4.count),x,y-12);
            g.drawImage(map,x,y,330,330,null);
        }
        g.drawString("Green: land. Dark blue: reserved ocean corridors. Blue: reserve-connected water. Cyan: interior water.",20,828);
        g.drawString("Each root is constructed in full before display. Land cannot bridge domains; larger S does not remove that restriction.",20,851);
        g.dispose(); ImageIO.write(image,"png",Path.of("build/gallery/authored-mosaic.png").toFile());
    }
}
