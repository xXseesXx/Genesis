package genesis.harness;

import genesis.core.Generator;
import genesis.core.Params;
import genesis.core.fields.FieldId;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;
import genesis.core.fields.TileCache;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Dependency-free gates. Randomness and timings are test inputs, never world state. */
public final class Gates {
    private static final long[] SEEDS = {0, 1, -1, 42, 137, 8675309, Long.MIN_VALUE, Long.MAX_VALUE};
    private static volatile double blackhole;
    private Gates() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("gallery")) { golden(true); RefinementGates.golden(true); RefinementDemo.writeDiagnostics(); ContinentalGates.main(args); ContinentalWorldGates.main(args); return; }
        long start = System.nanoTime();
        contracts();
        avalanche();
        TectonicGates.run();
        CoastGates.run();
        ContinentalGates.run();
        ContinentalWorldGates.run();
        HydrologyGates.run();
        ChannelGates.run();
        RefinementGates.run();
        determinism();
        golden(false);
        lint();
        http();
        budget();
        System.out.printf("PASS M0/M1/M2a/M3a geometry + finite reference gates (%.2f s). Production global hydrology/cascade remain deferred.%n", (System.nanoTime() - start) / 1e9);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("Expected invalid input to be rejected");
    }
    private static void equal(double a, double b, String message) {
        check(Double.doubleToLongBits(a) == Double.doubleToLongBits(b), message);
    }
    private static void contracts() throws Exception {
        check(Lattice.cell(-1, 16) == -1 && Lattice.fraction(-1, 16) == 15.0 / 16, "Negative lattice floor");
        check(Lattice.cell(-16, 16) == -1 && Lattice.fraction(-16, 16) == 0, "Negative lattice boundary");
        check(Hash64.mix(0) == 0 && Hash64.mix(1) == 0x5692161d100b05e5L, "Hash protocol vector changed");
        Generator g = new Generator(42, Params.defaults());
        rejects(() -> new Params(Map.of("unknown", 1.0)));
        rejects(() -> new Params(Map.of("octaves", 2.5)));
        rejects(() -> new Params(Map.of("amplitude", Double.NaN)));
        rejects(() -> new Params(Map.of("persistence", Double.POSITIVE_INFINITY)));
        rejects(() -> g.fields.find("ocean"));
        rejects(() -> g.fields.tile(Fields.PLATE_ID, 0, 0, 1, 16, 16));
        rejects(() -> g.fields.tile(Fields.NOISE, 0, 0, 0, 16, 16));
        rejects(() -> g.fields.tile(Fields.NOISE, 0, 0, Long.MAX_VALUE, 16, 16));
        rejects(() -> g.fields.tile(Fields.NOISE, Lattice.MAX_COORDINATE, 0, 1, 2, 2));
        rejects(() -> g.fields.get(Fields.NOISE, Long.MIN_VALUE, 0));
        rejects(() -> new FieldRegistry.Builder().add(Fields.NOISE, (x, z) -> 0.0).add(Fields.NOISE, (x, z) -> 1.0));
        Map<String, Double> mutable = new LinkedHashMap<>(); mutable.put("amplitude", 1.0);
        Params frozen = new Params(mutable); mutable.put("amplitude", 2.0);
        check(frozen.get("amplitude") == 1, "Params aliased caller map");
        for (long origin : new long[] {-4097, -16, -1, 0, 4095, -Lattice.MAX_COORDINATE, Lattice.MAX_COORDINATE - 4096}) {
            double[] tile = g.fields.tile(Fields.NOISE, origin, origin, 8, 32, 32);
            for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++)
                equal(tile[z * 32 + x], g.fields.get(Fields.NOISE, origin + x * 8, origin + z * 8), "Bulk vs scalar/zoom mismatch");
        }
        double[] whole = g.fields.tile(Fields.NOISE, -32, -16, 1, 64, 32);
        for (int chunk = 0; chunk < 4; chunk++) {
            double[] part = g.fields.tile(Fields.NOISE, -32 + chunk * 16, -16, 1, 16, 32);
            for (int z = 0; z < 32; z++) for (int x = 0; x < 16; x++)
                equal(whole[z * 64 + chunk * 16 + x], part[z * 16 + x], "Chunk seam");
        }
        TileCache cache = new TileCache(g, 2);
        double expected = cache.tile(Fields.NOISE, 0, 0, 1, 16, 16)[0];
        double[] changed = cache.tile(Fields.NOISE, 0, 0, 1, 16, 16); changed[0] = 999;
        equal(expected, cache.tile(Fields.NOISE, 0, 0, 1, 16, 16)[0], "Cache leaks mutable array");
        cache.tile(Fields.NOISE, 16, 0, 1, 16, 16); cache.tile(Fields.NOISE, 32, 0, 1, 16, 16);
        equal(expected, cache.tile(Fields.NOISE, 0, 0, 1, 16, 16)[0], "Eviction affects value");
        FieldId<Double> forged = new FieldId<>("noise", "fake", "", Double.class, -1, 1);
        rejects(() -> cache.tile(forged, 0, 0, 1, 16, 16));
        Generator other = new Generator(42, new Params(Map.of("amplitude", 2.0)));
        equal(expected * 2, other.fields.get(Fields.NOISE, 0, 0), "Parameter propagation");
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int i = 0; i < 128; i++) {
                final long x = i * 13L - 512;
                jobs.add(() -> Arrays.equals(cache.tile(Fields.NOISE, x, -17, 2, 16, 16), g.fields.tile(Fields.NOISE, x, -17, 2, 16, 16)));
            }
            for (var result : pool.invokeAll(jobs)) check(result.get(), "Concurrent cache mismatch");
        }
        System.out.println("PASS API: negative/extreme coordinates, tile seams, zoom samples, immutable params, cache isolation/eviction/concurrency");
    }
    private static void avalanche() {
        final int trials = 2048;
        double worst = 0;
        for (int inputBit = 0; inputBit < 64; inputBit++) {
            int[] counts = new int[64];
            for (int t = 0; t < trials; t++) {
                long seed = Hash64.mix(t);
                long diff = Hash64.hash(seed, 3, -t, t * 17L) ^ Hash64.hash(seed ^ (1L << inputBit), 3, -t, t * 17L);
                for (int outputBit = 0; outputBit < 64; outputBit++) counts[outputBit] += (int) ((diff >>> outputBit) & 1);
            }
            for (int count : counts) worst = Math.max(worst, Math.abs(count / (double) trials - 0.5));
        }
        check(worst < 0.06, "Hash avalanche marginal bias > 6 percentage points");
        // Separate coordinate/level/domain changes also need to influence the output.
        for (int i = 0; i < 512; i++) {
            long h = Hash64.hash(i, 2, -i, i);
            check(h != Hash64.hash(i, 3, -i, i), "Level ignored");
            check(h != Hash64.hash(i, 2, -i + 1, i), "X ignored");
            check(h != Hash64.hash(i, 2, -i, i + 1), "Z ignored");
            check(Hash64.stream(i, 1) != Hash64.stream(i, 2), "Streams collide");
        }
        System.out.printf("PASS HASH: 64x64 bit-flip marginal frequencies, worst bias %.4f (not a full independence proof)%n", worst);
    }
    private static void determinism() throws Exception {
        final int side = 64, count = side * side;
        List<Integer> order = new ArrayList<>(); for (int i = 0; i < count; i++) order.add(i);
        Generator g = new Generator(42, Params.defaults());
        for (FieldId<?> field : g.fields.ids()) {
            String[] baseline = new String[count];
            for (int index : order) baseline[index] = digest(g.fields.values(field, (index % side - side / 2) * 16L, (index / side - side / 2) * 16L, 1, 16, 16));
            Collections.shuffle(order, new Random(0xdecaf));
            TileCache cache = new TileCache(g, 64);
            for (int index : order) {
                long x = (index % side - side / 2) * 16L, z = (index / side - side / 2) * 16L;
                check(baseline[index].equals(digest(cache.values(field, x, z, 1, 16, 16))), "DET random cached order: " + field.name);
                Generator isolated = new Generator(42, Params.defaults());
                check(baseline[index].equals(digest(isolated.fields.values(field, x, z, 1, 16, 16))), "DET cold isolated chunk: " + field.name);
            }
            System.out.println("PASS DET " + field.name + ": 64x64 chunks, raster/random cached/cold per-chunk isolation");
        }
        for (long seed : SEEDS) {
            Generator fresh = new Generator(seed, Params.defaults());
            for (FieldId<?> field : fresh.fields.ids())
                check(Arrays.equals(fresh.fields.values(field, -16777216, 16777200, 17, 16, 16), new Generator(seed, Params.defaults()).fields.values(field, -16777216, 16777200, 17, 16, 16)), "Seed/extreme DET");
        }
    }
    private static String digest(Object[] values) throws Exception {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * 8);
        for (Object value : values) bytes.putLong(value instanceof Long || value instanceof Integer ? ((Number) value).longValue() : Double.doubleToLongBits(((Number) value).doubleValue()));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
    }
    private static String pixels(BufferedImage image) throws Exception {
        ByteBuffer bytes = ByteBuffer.allocate(image.getWidth() * image.getHeight() * 4);
        for (int z = 0; z < image.getHeight(); z++) for (int x = 0; x < image.getWidth(); x++) bytes.putInt(image.getRGB(x, z));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
    }
    private static void golden(boolean candidates) throws Exception {
        goldenSet(candidates, "m0", List.of(Fields.NOISE, Fields.RIDGES));
        goldenSet(candidates, "m1", List.of(Fields.PLATE_ID, Fields.SUB_PLATE_ID, Fields.VELOCITY_X, Fields.VELOCITY_Z, Fields.BOUNDARY_TYPE, Fields.BOUNDARY_DISTANCE, Fields.UPLIFT, Fields.CRUST_TYPE, Fields.CRUST_AGE));
        goldenSet(candidates, "m2a", List.of(Fields.CRUST_FRACTION, Fields.CONTINENTALITY, Fields.BASE_ELEVATION, Fields.SEA_MASK, Fields.SEA_DISTANCE, Fields.DRAINAGE_RANK, Fields.FLOW_DIRECTION));
        goldenSet(candidates, "m3a", List.of(Fields.CHANNEL_DISTANCE, Fields.PORT_DISTANCE));
    }
    private static void goldenSet(boolean candidates, String milestone, List<FieldId<?>> fields) throws Exception {
        Properties baseline = new Properties();
        try (var input = Files.newInputStream(Path.of("harness/golden/" + milestone + ".properties"))) { baseline.load(input); }
        check(("genesis-" + milestone + "-v1").equals(baseline.getProperty("version")), "Golden version mismatch");
        Map<String, Double> parameters = new LinkedHashMap<>();
        for (String id : Params.SPECS.keySet()) {
            // Milestone snapshots freeze the parameters that existed when those fields were added.
            if (!baseline.containsKey("param." + id)) continue;
            parameters.put(id, Double.parseDouble(baseline.getProperty("param." + id)));
            check(parameters.get(id).equals(Params.defaults().get(id)), "Default changed: explicitly update golden params " + id);
        }
        Params params = new Params(parameters);
        Files.createDirectories(Path.of("build/gallery"));
        for (long seed : SEEDS) {
            Generator g = new Generator(seed, params);
            for (FieldId<?> field : fields) {
                long origin = milestone.equals("m0") ? -16384 : -65536, step = milestone.equals("m0") ? 256 : 1024;
                Renderer.Result render = Renderer.render(g, new Renderer.Layer[] {new Renderer.Layer(field, 1)}, origin, origin, step, 128, 128);
                String key = "seed." + seed + "." + field.name;
                String hash = pixels(render.image());
                ImageIO.write(render.image(), "png", Path.of("build/gallery", key + ".png").toFile());
                if (candidates) System.out.println(milestone + ":" + key + "=" + hash);
                else {
                    BufferedImage reference = ImageIO.read(Path.of("harness/golden/" + milestone, key + ".png").toFile());
                    check(pixels(reference).equals(baseline.getProperty(key)), "GOLD reference image/manifest disagree: " + key);
                    if (!hash.equals(baseline.getProperty(key))) {
                        BufferedImage delta = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
                        for (int row = 0; row < 128; row++) for (int col = 0; col < 128; col++)
                            delta.setRGB(col, row, reference.getRGB(col, row) == render.image().getRGB(col, row) ? 0x142024 : 0xff695d);
                        ImageIO.write(delta, "png", Path.of("build/gallery", key + ".diff.png").toFile());
                        throw new AssertionError("GOLD pixels changed: " + key + "; inspect build/gallery diff and explicitly update baseline");
                    }
                }
            }
        }
        System.out.println(candidates ? "Candidate images in build/gallery; baseline was NOT changed." : "PASS GOLD " + milestone + ": 8 seeds x " + fields.size() + " fields, exact pixels and reference PNGs");
    }
    private static void lint() throws Exception {
        Pattern forbidden = Pattern.compile("\\b(?:Random|ThreadLocalRandom|SplittableRandom)\\b|Math\\.random|System\\.(?:nanoTime|currentTimeMillis)|java\\.(?:io|nio|net)\\.|net\\.minecraft|cpw\\.mods|genesis\\.harness");
        Pattern imports = Pattern.compile("import\\s+(genesis\\.core\\.([a-z]+)\\.[\\w.]+)");
        var subsystems = List.of("tectonics", "elevation", "hydro", "geology", "climate", "surface");
        try (var paths = Files.walk(Path.of("core/src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(path).replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "");
                check(!forbidden.matcher(code).find(), "LINT forbidden nondeterministic/external dependency: " + path);
                var staticFields = Pattern.compile("(?m)^\\s*(?:public|private|protected)\\s+static\\s+(?!final\\b)([\\w<>?, ]+)\\s+\\w+\\s*(?:=|;)").matcher(code);
                check(!staticFields.find(), "LINT mutable static state: " + path);
                String normalized = path.toString().replace('\\', '/');
                String owner = subsystems.stream().filter(s -> normalized.contains("/" + s + "/")).findFirst().orElse("");
                var matcher = imports.matcher(code);
                while (matcher.find()) {
                    String imported = matcher.group(2);
                    if (!owner.isEmpty()) check(!subsystems.contains(imported) || imported.equals(owner), "LINT cross-subsystem import: " + path);
                }
                if (owner.equals("hydro")) {
                    check(List.of("BoundaryPorts.java", "CoarseChannels.java", "DrainageRefinement.java").contains(path.getFileName().toString()),
                        "Extend hydrology integer/literal gates before adding a new module: " + path);
                    check(!Pattern.compile("\\b(?:double|float)\\b|Math\\.(?:sqrt|pow|hypot)|signedUnit|params\\.get\\(").matcher(code).find(),
                        "LINT floating drainage geometry/topology: " + path);
                }
                if (path.getFileName().toString().equals("PlateTopology.java"))
                    check(!Pattern.compile("\\b(?:double|float)\\b|Math\\.(?:sqrt|pow)|signedUnit").matcher(code).find(), "LINT floating topology: " + path);
                if (List.of("CoastTopology.java", "ContinentalScaffold.java").contains(path.getFileName().toString()))
                    check(!Pattern.compile("\\b(?:double|float)\\b|signedUnit|Fields\\.UPLIFT").matcher(code).find(), "LINT floating coast decisions: " + path);
                // Generator expressions may use only structural 0/1; Noise has a documented math/domain allowlist.
                if (path.getFileName().toString().equals("Noise.java") || !owner.isEmpty()) {
                    String numericCode = code.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
                    var numbers = Pattern.compile("(?<![\\w.])(?:0x[\\da-fA-F]+[lL]?|\\d+(?:\\.\\d+)?[dDfFlL]?)(?![\\w.])").matcher(numericCode);
                    while (numbers.find()) {
                        String n = numbers.group();
                        String file = path.getFileName().toString();
                        boolean allowed = n.equals("0") || n.equals("1") || (file.equals("Noise.java") && List.of("0x4e4f495345L", "6", "15", "10").contains(n))
                            || (file.equals("PlateTopology.java") && List.of("2", "3", "4", "7", "16", "32", "64", "100", "256", "1000", "4096", "1L", "0xffffffffL", "0x504c415445L").contains(n))
                            || (file.equals("Tectonics.java") && n.equals("6"))
                            || (file.equals("CoastTopology.java") && List.of("2", "3", "4", "16", "1000", "8192", "4096", "0x434f415354L").contains(n))
                            || (file.equals("ContinentalScaffold.java") && List.of("2", "3", "4", "4L", "5", "9", "16", "64", "100", "724", "1000", "1024", "0x434f4e54494eL").contains(n))
                            || (file.equals("MacroElevation.java") && n.equals("1000.0"))
                            || (file.equals("BoundaryPorts.java") && List.of("2", "3", "4", "20", "0x504f525453L").contains(n))
                            || (file.equals("CoarseChannels.java") && List.of("2", "3", "4", "16", "512").contains(n))
                            || (file.equals("DrainageRefinement.java") && List.of("2", "3", "4", "0x524546494e45L").contains(n));
                        check(allowed, "LINT unregistered numeric tuning: " + path + " literal " + n);
                    }
                }
            }
        }
        System.out.println("PASS LINT: dependency/state guards and generator literal allowlist (source guardrails, not a proof of purity)");
    }
    private static double percentile(long[] values, double p) { Arrays.sort(values); return values[(int) Math.ceil(p * values.length) - 1] / 1e6; }
    private static void http() throws Exception {
        try (Server.Running server = Server.start(0); HttpClient client = HttpClient.newHttpClient()) {
            String base = "http://127.0.0.1:" + server.port();
            for (String path : List.of("/", "/continents.html", "/style.css", "/app.js", "/hydrology.html", "/hydrology.js", "/hydrology.css", "/refinement.html", "/refinement.js", "/refinement.css", "/api/meta", "/api/meta?model=continental", "/api/sample?seed=-9223372036854775808&x=-1&z=16")) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base + path)).build(), HttpResponse.BodyHandlers.ofString());
                check(response.statusCode() == 200 && !response.body().isEmpty(), "HTTP GET " + path);
            }
            var exact = client.send(HttpRequest.newBuilder(URI.create(base + "/api/sample?seed=42&x=1099511627776&z=-1099511627776")).build(), HttpResponse.BodyHandlers.ofString());
            long exactId = new Generator(42, Params.defaults()).fields.get(Fields.PLATE_ID, Lattice.MAX_COORDINATE, -Lattice.MAX_COORDINATE);
            check(exact.statusCode() == 200 && exact.body().contains("\"plateId\":\"" + exactId + "\""), "HTTP loses 64-bit plate identity");
            String path = "/api/render?seed=42&x=-32&z=-32&width=32&height=32&step=8&layers=noise:1,ridges:0.25";
            var png = client.send(HttpRequest.newBuilder(URI.create(base + path)).build(), HttpResponse.BodyHandlers.ofByteArray());
            check(png.statusCode() == 200 && png.headers().firstValue("Content-Type").orElse("").equals("image/png"), "HTTP PNG type");
            BufferedImage actual = ImageIO.read(new ByteArrayInputStream(png.body()));
            BufferedImage expected = Renderer.render(new Generator(42, Params.defaults()), new Renderer.Layer[] {
                new Renderer.Layer(Fields.NOISE, 1), new Renderer.Layer(Fields.RIDGES, .25)}, -32, -32, 8, 32, 32).image();
            check(pixels(actual).equals(pixels(expected)), "HTTP composition differs from core render");
            String continentalQuery = "model=continental&seed=42&x=-65536&z=-65536&width=32&height=32&step=4096&terrainDetailHeight=1500";
            var continentalWorld = new Generator(42, new Params(Map.of("terrainDetailHeight",1500.0)), Generator.Model.CONTINENTAL);
            var terrain = client.send(HttpRequest.newBuilder(URI.create(base + "/api/render?"+continentalQuery+"&layers=baseElevation:1,coarseChannelDistance:1,drainagePortDistance:1")).build(), HttpResponse.BodyHandlers.ofByteArray());
            var terrainExpected = Renderer.render(continentalWorld,new Renderer.Layer[]{new Renderer.Layer(Fields.BASE_ELEVATION,1),new Renderer.Layer(Fields.CHANNEL_DISTANCE,1),new Renderer.Layer(Fields.PORT_DISTANCE,1)},-65536,-65536,4096,32,32).image();
            check(terrain.statusCode()==200 && terrain.headers().firstValue("X-World-Model").orElse("").equals("continental") && pixels(ImageIO.read(new ByteArrayInputStream(terrain.body()))).equals(pixels(terrainExpected)),"HTTP continental render silently uses legacy model");
            var terrainSample=client.send(HttpRequest.newBuilder(URI.create(base+"/api/sample?"+continentalQuery)).build(),HttpResponse.BodyHandlers.ofString());
            check(terrainSample.statusCode()==200 && terrainSample.body().contains("\"model\":\"continental\"") && terrainSample.body().contains("\"baseElevation\":"+continentalWorld.fields.get(Fields.BASE_ELEVATION,-65536,-65536)),"HTTP continental inspector mismatch");
            var continentalHydro=client.send(HttpRequest.newBuilder(URI.create(base+"/api/hydrology?"+continentalQuery+"&outlets=connectedWater")).build(),HttpResponse.BodyHandlers.ofString());
            String continentalDirect=HydrologyAnalysis.json(continentalWorld,-65536,-65536,4096,32,32,"connectedWater");
            check(continentalHydro.statusCode()==200 && continentalHydro.body().replaceAll("\"milliseconds\":[0-9.E+-]+","\"milliseconds\":0").equals(continentalDirect.replaceAll("\"milliseconds\":[0-9.E+-]+","\"milliseconds\":0")),"Finite analysis does not use continental terrain");
            for(String endpoint:List.of("meta","render","sample","hydrology")) {
                var invalid=client.send(HttpRequest.newBuilder(URI.create(base+"/api/"+endpoint+"?model=unknown")).build(),HttpResponse.BodyHandlers.ofString());
                check(invalid.statusCode()==400,"Unknown world model accepted: "+endpoint);
            }
            var continent = client.send(HttpRequest.newBuilder(URI.create(base + "/api/render?seed=42&x=-65536&z=-65536&width=32&height=32&step=4096&layers=continentSeaMask:1&continentCoverage=0")).build(), HttpResponse.BodyHandlers.ofByteArray());
            check(continent.statusCode() == 200, "HTTP candidate field unavailable");
            var candidateImage = ImageIO.read(new ByteArrayInputStream(continent.body()));
            for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) check((candidateImage.getRGB(x,z) & 0xffffff) == 0x245b78, "HTTP candidate parameters ignored");
            var refined = client.send(HttpRequest.newBuilder(URI.create(base + "/api/refinement?seed=42")).build(), HttpResponse.BodyHandlers.ofString());
            check(refined.statusCode() == 200 && refined.body().equals(RefinementDemo.json(RefinementDemo.create(Map.of("seed","42")))), "HTTP refinement contract mismatch");
            var refinedPng = client.send(HttpRequest.newBuilder(URI.create(base + "/api/refinement.png?seed=42&layer=flux")).build(), HttpResponse.BodyHandlers.ofByteArray());
            check(refinedPng.statusCode() == 200 && pixels(ImageIO.read(new ByteArrayInputStream(refinedPng.body())))
                .equals(pixels(RefinementDemo.render(RefinementDemo.create(Map.of("seed","42")),"flux"))), "HTTP refinement PNG mismatch");
            var hugeFlux = client.send(HttpRequest.newBuilder(URI.create(base + "/api/refinement?rain=0&inflow=9223372036854775807")).build(), HttpResponse.BodyHandlers.ofString());
            check(hugeFlux.statusCode() == 200 && hugeFlux.body().contains("\"finalOutflow\":\"9223372036854775807\""), "HTTP lost exact 64-bit flow units");
            for (String query : List.of("level=0", "level=21", "rain=-1", "inflow=-1", "rain=9223372036854775807", "rain=1&inflow=9223372036854775807", "x=1099511627776", "z=-1099511627777", "layer=noise", "width=512", "seaThreshold=700", "outlets=edges", "seed=1&seed=2")) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base + "/api/refinement?" + query)).build(), HttpResponse.BodyHandlers.ofString());
                check(response.statusCode() == 400, "HTTP invalid refinement contract: " + query);
            }
            String hydroQuery = "seed=-9223372036854775808&x=-65536&z=-65536&width=8&height=8&step=4096";
            var hydro = client.send(HttpRequest.newBuilder(URI.create(base + "/api/hydrology?" + hydroQuery)).build(), HttpResponse.BodyHandlers.ofString());
            String direct = HydrologyAnalysis.json(new Generator(Long.MIN_VALUE, Params.defaults()), -65536, -65536, 4096, 8, 8);
            check(hydro.statusCode() == 200 && hydro.body().replaceAll("\"milliseconds\":[0-9.E+-]+", "\"milliseconds\":0")
                .equals(direct.replaceAll("\"milliseconds\":[0-9.E+-]+", "\"milliseconds\":0")), "HTTP finite analysis differs from direct solver");
            var waterHydro = client.send(HttpRequest.newBuilder(URI.create(base + "/api/hydrology?" + hydroQuery + "&outlets=connectedWater")).build(), HttpResponse.BodyHandlers.ofString());
            String waterDirect = HydrologyAnalysis.json(new Generator(Long.MIN_VALUE, Params.defaults()), -65536, -65536, 4096, 8, 8, "connectedWater");
            check(waterHydro.statusCode() == 200 && waterHydro.body().replaceAll("\"milliseconds\":[0-9.E+-]+", "\"milliseconds\":0")
                .equals(waterDirect.replaceAll("\"milliseconds\":[0-9.E+-]+", "\"milliseconds\":0")), "HTTP connected-water analysis differs");
            var dryHydro = client.send(HttpRequest.newBuilder(URI.create(base + "/api/hydrology?width=8&height=8&outlets=connectedWater&continentalPercent=100&crustInfluence=1000")).build(), HttpResponse.BodyHandlers.ofString());
            check(dryHydro.statusCode() == 200 && dryHydro.body().contains("\"unresolvedLandCells\":64,\"terminalCount\":0"), "HTTP fabricated dry-grid terminal");
            for (String query : List.of("width=1", "height=257", "width=2147483648", "step=0", "step=1048577", "x=1099511627776&step=1", "z=-1099511627777", "layers=noise:1", "seaThreshold=NaN", "outlets=ocean", "outlets=")) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base + "/api/hydrology?" + query)).build(), HttpResponse.BodyHandlers.ofString());
                check(response.statusCode() == 400 && response.body().startsWith("{\"error\":"), "HTTP invalid hydrology query: " + query);
            }
            for (String query : List.of("width=0", "width=1025", "step=0", "step=9223372036854775807", "octaves=2.5", "amplitude=NaN", "layers=noise:NaN", "layers=missing:1", "seed=9223372036854775808", "x=1099511627777", "seed=1&seed=2", "typo=1", "outlets=edges")) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base + "/api/render?" + query)).build(), HttpResponse.BodyHandlers.ofString());
                check(response.statusCode() == 400 && response.body().startsWith("{\"error\":"), "HTTP invalid query: " + query);
            }
            var post = client.send(HttpRequest.newBuilder(URI.create(base + "/api/meta")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            check(post.statusCode() == 405, "HTTP method guard");
            var missing = client.send(HttpRequest.newBuilder(URI.create(base + "/missing")).build(), HttpResponse.BodyHandlers.ofString());
            check(missing.statusCode() == 404, "HTTP static allowlist");
        }
        System.out.println("PASS HTTP: viewer assets, metadata, inspector, PNG composition, malformed requests and method/path guards");
    }
    private static void budget() throws Exception {
        Generator g = new Generator(42, Params.defaults()); TileCache cache = new TileCache(g, 32);
        for (int i = 0; i < 400; i++) for (FieldId<?> field : g.fields.ids()) blackhole += ((Number) g.fields.values(field, i, -i, 1, 16, 16)[0]).doubleValue();
        for (FieldId<?> field : g.fields.ids()) cache.values(field, 0, 0, 1, 16, 16);
        long[] cold = new long[256], warm = new long[256];
        for (int i = 0; i < cold.length; i++) {
            long start = System.nanoTime();
            Generator isolated = new Generator(42, Params.defaults());
            for (FieldId<?> field : isolated.fields.ids()) blackhole += ((Number) isolated.fields.values(field, i * 16L, -i * 16L, 1, 16, 16)[0]).doubleValue();
            cold[i] = System.nanoTime() - start; start = System.nanoTime();
            for (FieldId<?> field : g.fields.ids()) blackhole += ((Number) cache.values(field, 0, 0, 1, 16, 16)[0]).doubleValue();
            warm[i] = System.nanoTime() - start;
        }
        double cold95 = percentile(cold, .95), warm95 = percentile(warm, .95);
        long[] frames = new long[5];
        for (int i = 0; i < frames.length; i++) frames[i] = Renderer.render(g,
            new Renderer.Layer[] {new Renderer.Layer(Fields.BASE_ELEVATION, 1)}, -65536, -65536, 256, 512, 512).nanos();
        double frameMedian = percentile(frames, .5);
        long[] guideFrames = new long[5];
        for (int i = 0; i < guideFrames.length; i++) guideFrames[i] = Renderer.render(g,
            new Renderer.Layer[] {new Renderer.Layer(Fields.BASE_ELEVATION, 1), new Renderer.Layer(Fields.CHANNEL_DISTANCE, 1), new Renderer.Layer(Fields.PORT_DISTANCE, 1)},
            -65536, -65536, 256, 512, 512).nanos();
        double guideMedian = percentile(guideFrames, .5);
        long[] continentFrames = new long[5];
        for (int i = 0; i < continentFrames.length; i++) continentFrames[i] = Renderer.render(g,
            new Renderer.Layer[] {new Renderer.Layer(Fields.CONTINENT_SCAFFOLD, 1)}, -262144, -262144, 1024, 512, 512).nanos();
        double continentMedian = percentile(continentFrames, .5);
        Generator continentalWorld = new Generator(42, Params.defaults(), Generator.Model.CONTINENTAL);
        long[] terrainFrames = new long[5], terrainGuideFrames = new long[5];
        for (int i = 0; i < terrainFrames.length; i++) {
            terrainFrames[i] = Renderer.render(continentalWorld,new Renderer.Layer[]{new Renderer.Layer(Fields.BASE_ELEVATION,1)},-262144,-262144,1024,512,512).nanos();
            terrainGuideFrames[i] = Renderer.render(continentalWorld,new Renderer.Layer[]{new Renderer.Layer(Fields.BASE_ELEVATION,1),new Renderer.Layer(Fields.CHANNEL_DISTANCE,1),new Renderer.Layer(Fields.PORT_DISTANCE,1)},-262144,-262144,1024,512,512).nanos();
        }
        double terrainMedian = percentile(terrainFrames,.5), terrainGuideMedian = percentile(terrainGuideFrames,.5);
        String report = String.format(java.util.Locale.ROOT,
            "{\"version\":\"%s\",\"java\":\"%s\",\"os\":\"%s\",\"processors\":%d,\"chunkColdP95Ms\":%.4f,\"chunkWarmP95Ms\":%.4f,\"render512MedianMs\":%.2f,\"guideRender512MedianMs\":%.2f,\"continentRender512MedianMs\":%.2f,\"continentalTerrain512MedianMs\":%.2f,\"continentalGuides512MedianMs\":%.2f}%n",
            Generator.VERSION, System.getProperty("java.version"), System.getProperty("os.name"), Runtime.getRuntime().availableProcessors(), cold95, warm95, frameMedian, guideMedian, continentMedian, terrainMedian, terrainGuideMedian);
        Files.writeString(Path.of("build/budget.json"), report);
        check(cold95 <= 25 && warm95 <= 1 && frameMedian < 1000 && guideMedian < 1000 && continentMedian < 1000 && terrainMedian < 1000 && terrainGuideMedian < 1000, "BUD over current thresholds: " + report);
        System.out.printf("PASS BUD continental terrain: 512-square %.1f ms; with guides/ports %.1f ms median%n",terrainMedian,terrainGuideMedian);
        System.out.printf("PASS BUD continental scaffold: 512-square %.1f ms median%n", continentMedian);
        System.out.printf("PASS BUD: all %d fields cold p95 %.3f ms/chunk, cached p95 %.3f ms/chunk; 512-square elevation %.1f ms, guides+ports %.1f ms median%n", g.fields.ids().size(), cold95, warm95, frameMedian, guideMedian);
    }
}
