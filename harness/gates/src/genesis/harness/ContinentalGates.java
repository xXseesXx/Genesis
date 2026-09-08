package genesis.harness;

import genesis.core.Generator;
import genesis.core.Params;
import genesis.core.elevation.ContinentalScaffold;
import genesis.core.fields.Fields;
import genesis.core.hash.Lattice;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Independent large-support/integer arithmetic checks and actual generator images. */
public final class ContinentalGates {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void run() throws Exception {
        Random random = new Random(761);
        for (long seed : new long[]{0, 42, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (int variant = 0; variant < 8; variant++) {
                Params params = new Params(Map.of("continentScale", (variant & 1) == 0 ? 16385.0 : 1048576.0,
                    "continentLobeRadius", (variant & 2) == 0 ? 300.0 : 400.0,
                    "continentLobeVariation", (variant & 4) == 0 ? 0.0 : 250.0,
                    "continentArmStep", (variant & 2) == 0 ? 500.0 : 250.0, "continentCoverage", 100.0));
                var world = new ContinentalScaffold(seed, params);
                for (int n = 0; n < 35; n++) {
                    long x = n < 4 ? (n % 2 == 0 ? -1 : 1) * Lattice.MAX_COORDINATE : random.nextLong(-100000000000L, 100000000000L);
                    long z = n < 4 ? (n < 2 ? -1 : 1) * Lattice.MAX_COORDINATE : random.nextLong(-100000000000L, 100000000000L);
                    long i = Math.floorDiv(x, world.spacing), j = Math.floorDiv(z, world.spacing);
                    int reference = -1000;
                    for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) {
                        var object = world.landmass(i + dx, j + dz);
                        int score = reference(object, x, z);
                        check(score == object.score(x, z), "Lobe arithmetic differs from BigInteger reference");
                        reference = Math.max(reference, score);
                    }
                    check(world.score(x, z) == reference, "3x3 omitted a contributor from 7x7 reference");
                }
                var land = world.landmass(-3, 2);
                for (int k = 1; k < land.lobes(); k++) {
                    int parent = k <= 2 ? k - 1 : k == 3 ? 0 : 3;
                    long dx = land.centerX(k) - land.centerX(parent), dz = land.centerZ(k) - land.centerZ(parent);
                    long radius = land.radius(k) + land.radius(parent);
                    check(dx * dx + dz * dz < radius * radius, "Disconnected lobe recipe");
                    for (int t = 0; t <= 100; t++) check(land.score(land.centerX(parent) + dx * t / 100,
                        land.centerZ(parent) + dz * t / 100) > 0, "Quantized land spine breaks");
                }
            }
        }
        var dry = new ContinentalScaffold(42, new Params(Map.of("continentCoverage", 0.0)));
        for (int k = -100; k <= 100; k++) check(dry.score(k * 7919L, k * 104729L) == -1000, "Zero coverage produces land");
        var world = new ContinentalScaffold(42, Params.defaults());
        int[] expected = new int[128]; List<Integer> order = new ArrayList<>();
        for (int k = 0; k < 128; k++) { expected[k] = world.score(k * 9713L - 500000, k * 1327L - 50000); order.add(k); }
        Collections.shuffle(order, random);
        for (int k = 0; k < 100; k++) world.score(k * 2000000L, -k * 3000000L);
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int k : order) jobs.add(() -> expected[k] == world.score(k * 9713L - 500000, k * 1327L - 50000));
            for (var result : pool.invokeAll(jobs)) check(result.get(), "Cache/order/concurrency changes continent");
        }
        var generator = new Generator(42, Params.defaults());
        for (var field : List.of(Fields.CONTINENT_SCAFFOLD, Fields.CONTINENT_SEA_MASK)) {
            var whole = generator.fields.values(field, -199999, -288881, 1027, 128, 128);
            var zoom = new Generator(42, Params.defaults()).fields.values(field, -199999, -288881, 2054, 64, 64);
            for (int z = 0; z < 64; z++) for (int x = 0; x < 64; x++) check(whole[z * 2 * 128 + x * 2].equals(zoom[z * 64 + x]), "Zoom changes scaffold");
            for (int q : new int[]{3, 0, 2, 1}) {
                int ox = q % 2 * 64, oz = q / 2 * 64;
                var part = new Generator(42, Params.defaults()).fields.values(field, -199999 + ox * 1027L, -288881 + oz * 1027L, 1027, 64, 64);
                for (int z = 0; z < 64; z++) for (int x = 0; x < 64; x++) check(whole[(z + oz) * 128 + ox + x].equals(part[z * 64 + x]), "Independent crop changes scaffold");
            }
        }
        golden(false);
        diagnostics();
        System.out.println("PASS CONTINENT: connected objects, 7x7 BigInteger reference, parameter extremes, crop/zoom/cache/order/concurrency; comparison PNGs");
    }
    private static int reference(ContinentalScaffold.Landmass land, long x, long z) {
        if (!land.active) return -1000;
        int score = -1000;
        for (int k = 0; k < land.lobes(); k++) {
            BigInteger dx = BigInteger.valueOf(x - land.centerX(k)), dz = BigInteger.valueOf(z - land.centerZ(k));
            BigInteger r2 = BigInteger.valueOf(land.radius(k)).pow(2);
            score = Math.max(score, r2.subtract(dx.pow(2).add(dz.pow(2))).multiply(BigInteger.valueOf(1000)).divide(r2).max(BigInteger.valueOf(-1000)).intValueExact());
        }
        return score;
    }
    public static void golden(boolean candidates) throws Exception {
        Properties baseline = new Properties();
        if (!candidates) try (var input = Files.newInputStream(Path.of("harness/golden/continents.properties"))) { baseline.load(input); }
        if (!candidates) {
            check(ContinentalScaffold.VERSION.equals(baseline.getProperty("version")), "Continental version mismatch");
            for (String id : List.of("continentScale", "continentCoverage", "continentLobeRadius", "continentLobeVariation", "continentArmStep"))
                check(Params.defaults().get(id) == Double.parseDouble(baseline.getProperty("param." + id)), "Continental default changed: " + id);
        }
        for (long seed : new long[]{0, 1, -1, 42, 137, 8675309, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var world = new ContinentalScaffold(seed, Params.defaults()); var bytes = ByteBuffer.allocate(128 * 128 * 4);
            for (int z = 0; z < 128; z++) for (int x = 0; x < 128; x++) bytes.putInt(world.score(-524288 + x * 8192L, -524288 + z * 8192L));
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
            if (candidates) System.out.println("continents:seed." + seed + "=" + hash);
            else check(hash.equals(baseline.getProperty("seed." + seed)), "Continental golden changed: " + seed);
        }
    }
    private static void diagnostics() throws Exception {
        Files.createDirectories(Path.of("build/gallery"));
        BufferedImage sheet = new BufferedImage(1184, 916, BufferedImage.TYPE_INT_RGB); var pen = sheet.createGraphics();
        pen.setColor(new Color(0x10181b)); pen.fillRect(0, 0, 1184, 916);
        pen.setFont(new Font("SansSerif", Font.PLAIN, 15)); pen.setColor(new Color(0xe3e9e5));
        var fields = List.of(Fields.SEA_MASK, Fields.CONTINENT_SEA_MASK, Fields.CONTINENT_SCAFFOLD);
        String[] titles = {"CURRENT COAST / PLATE BLEND", "CANDIDATE / CONNECTED LANDMASSES", "CANDIDATE / MACRO SUPPORT"};
        for (int row = 0; row < 2; row++) {
            long seed = row == 0 ? 42 : 137;
            var generator = new Generator(seed, Params.defaults());
            for (int col = 0; col < 3; col++) {
                int x = 8 + col * 392, y = 36 + row * 444;
                var rendered = Renderer.render(generator, new Renderer.Layer[]{new Renderer.Layer(fields.get(col), 1)}, -196608, -196608, 1024, 384, 384);
                pen.drawString(titles[col], x, y - 12); pen.drawImage(rendered.image(), x, y, null);
                pen.drawString("Seed " + seed + " / land " + Math.round(rendered.landFraction() * 100) + "% of this crop", x, y + 403);
            }
        }
        pen.drawString("Same coordinates / 393,216 blocks across. Candidate does NOT drive current elevation or drainage. No noise added.", 8, 907);
        pen.dispose(); ImageIO.write(sheet, "png", Path.of("build/gallery/continental-comparison.png").toFile());
    }
    public static void main(String[] args) throws Exception { golden(true); diagnostics(); }
}
