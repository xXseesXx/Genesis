package genesis.harness;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import genesis.core.Generator;
import genesis.core.Params;
import genesis.core.fields.FieldId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Local, read-only HTTP harness. Requests carry their entire seed/configuration. */
public final class Server {
    private static final Path VIEWER = Path.of("harness/viewer").toAbsolutePath().normalize();
    private Server() {}

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8787 : Integer.parseInt(args[0]);
        Running running = start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(running::close));
        System.out.println("Genesis " + Generator.VERSION + " viewer: http://127.0.0.1:" + running.port() + " (Ctrl+C to stop)");
    }

    public static final class Running implements AutoCloseable {
        private final HttpServer server;
        private final ThreadPoolExecutor executor;
        private Running(HttpServer server, ThreadPoolExecutor executor) { this.server = server; this.executor = executor; }
        public int port() { return server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); executor.shutdownNow(); }
    }

    public static Running start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);
        server.createContext("/", Server::handle);
        server.start();
        return new Running(server, executor);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equals("GET")) { send(exchange, 405, "text/plain", bytes("GET only")); return; }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/meta")) { send(exchange, 200, "application/json", bytes(metadata(model(query(exchange.getRequestURI().getRawQuery(), false))))); return; }
            if (path.startsWith("/api/")) {
                boolean refinement = path.equals("/api/refinement") || path.equals("/api/refinement.png");
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery(), refinement);
                if (refinement) {
                    var demo = RefinementDemo.create(query);
                    if (path.endsWith(".png")) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ImageIO.write(RefinementDemo.render(demo, query.getOrDefault("layer", "network")), "png", out);
                        send(exchange, 200, "image/png", out.toByteArray());
                    } else send(exchange, 200, "application/json", bytes(RefinementDemo.json(demo)));
                    return;
                }
                if (query.containsKey("outlets") && !path.equals("/api/hydrology"))
                    throw new IllegalArgumentException("outlets is a finite hydrology option, not a world parameter");
                Generator generator = generator(query);
                long x = number(query, "x", -16384), z = number(query, "z", -16384);
                if (path.equals("/api/hydrology")) {
                    if (query.containsKey("layers")) throw new IllegalArgumentException("Analysis returns all reference fields; layers is not accepted");
                    String result = HydrologyAnalysis.json(generator, x, z, number(query, "step", 1024),
                        Math.toIntExact(number(query, "width", 128)), Math.toIntExact(number(query, "height", 128)), query.getOrDefault("outlets", "edges"));
                    send(exchange, 200, "application/json", bytes(result)); return;
                }
                if (path.equals("/api/sample")) {
                    StringBuilder result = new StringBuilder("{\"model\":").append(quote(generator.model.id)).append(",\"x\":\"").append(x).append("\",\"z\":\"").append(z).append("\",\"fields\":{");
                    for (FieldId<?> id : generator.fields.ids()) {
                        if (result.charAt(result.length() - 1) != '{') result.append(',');
                        Object value = generator.fields.get(id, x, z);
                        result.append(quote(id.name)).append(':').append(value instanceof Long ? quote(value.toString()) : value.toString());
                    }
                    send(exchange, 200, "application/json", bytes(result.append("}}").toString())); return;
                }
                if (path.equals("/api/render")) {
                    int w = Math.toIntExact(number(query, "width", 512)), h = Math.toIntExact(number(query, "height", 512));
                    if (w < 1 || h < 1 || w > 1024 || h > 1024) throw new IllegalArgumentException("Render dimensions must be 1..1024");
                    long step = number(query, "step", 64);
                    List<Renderer.Layer> layers = new ArrayList<>();
                    for (String item : query.getOrDefault("layers", "noise:1").split(",")) {
                        String[] pair = item.split(":", -1);
                        if (pair.length != 2) throw new IllegalArgumentException("Layers use field:opacity");
                        layers.add(new Renderer.Layer(generator.fields.find(pair[0]), Double.parseDouble(pair[1])));
                    }
                    Renderer.Result render = Renderer.render(generator, layers.toArray(Renderer.Layer[]::new), x, z, step, w, h);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ImageIO.write(render.image(), "png", out);
                    exchange.getResponseHeaders().set("X-Render-Ms", Double.toString(render.nanos() / 1e6));
                    exchange.getResponseHeaders().set("X-World-Model", generator.model.id);
                    exchange.getResponseHeaders().set("X-Field-Min", Double.toString(render.min()));
                    exchange.getResponseHeaders().set("X-Field-Max", Double.toString(render.max()));
                    exchange.getResponseHeaders().set("X-Field-Mean", Double.toString(render.mean()));
                    exchange.getResponseHeaders().set("X-Land-Fraction", Double.toString(render.landFraction()));
                    send(exchange, 200, "image/png", out.toByteArray()); return;
                }
                send(exchange, 404, "text/plain", bytes("Unknown endpoint")); return;
            }
            String file = switch (path) { case "/" -> "index.html"; case "/app.js" -> "app.js"; case "/style.css" -> "style.css";
                case "/continents.html" -> "continents.html";
                case "/hydrology.html" -> "hydrology.html"; case "/hydrology.js" -> "hydrology.js"; case "/hydrology.css" -> "hydrology.css";
                case "/refinement.html" -> "refinement.html"; case "/refinement.js" -> "refinement.js"; case "/refinement.css" -> "refinement.css"; default -> null; };
            if (file == null) { send(exchange, 404, "text/plain", bytes("Not found")); return; }
            String type = file.endsWith("html") ? "text/html; charset=utf-8" : file.endsWith("js") ? "text/javascript; charset=utf-8" : "text/css; charset=utf-8";
            send(exchange, 200, type, Files.readAllBytes(VIEWER.resolve(file)));
        } catch (IllegalArgumentException | ArithmeticException e) {
            send(exchange, 400, "application/json", bytes("{\"error\":" + quote(e.getMessage()) + "}"));
        } catch (IOException e) {
            // Canceled viewport requests commonly disconnect while an image is being sent.
            // Once headers were sent there is no second response to write.
            if (exchange.getResponseCode() == -1) {
                System.err.println("Harness I/O error before response: " + e.getMessage());
                send(exchange, 500, "application/json", bytes("{\"error\":\"Harness I/O error; see server console\"}"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            send(exchange, 500, "application/json", bytes("{\"error\":\"Internal harness error; see server console\"}"));
        } finally { exchange.close(); }
    }
    private static Map<String, String> query(String raw, boolean refinement) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null) return result;
        if (raw.length() > 8192) throw new IllegalArgumentException("Query too long");
        for (String item : raw.split("&")) {
            String[] pair = item.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            if (result.put(key, value) != null) throw new IllegalArgumentException("Duplicate query key: " + key);
        }
        for (String key : result.keySet())
            if (!List.of("seed", "model", "x", "z", "width", "height", "step", "layers", "outlets").contains(key) && !Params.SPECS.containsKey(key)
                && !(refinement && List.of("level", "rain", "inflow", "layer").contains(key)))
                throw new IllegalArgumentException("Unknown query key: " + key);
        return result;
    }
    private static Generator generator(Map<String, String> query) {
        Map<String, Double> overrides = new LinkedHashMap<>();
        for (String key : Params.SPECS.keySet()) if (query.containsKey(key)) overrides.put(key, Double.parseDouble(query.get(key)));
        return new Generator(number(query, "seed", 42), new Params(overrides), model(query));
    }
    private static Generator.Model model(Map<String, String> query) {
        return switch (query.getOrDefault("model", "legacy")) {
            case "legacy" -> Generator.Model.LEGACY;
            case "continental" -> Generator.Model.CONTINENTAL;
            default -> throw new IllegalArgumentException("model must be legacy or continental");
        };
    }
    private static long number(Map<String, String> query, String key, long fallback) {
        return Long.parseLong(query.getOrDefault(key, Long.toString(fallback)));
    }
    private static String metadata(Generator.Model model) {
        StringBuilder result = new StringBuilder("{\"version\":").append(quote(Generator.VERSION)).append(",\"model\":").append(quote(model.id)).append(",\"params\":[");
        boolean comma = false;
        for (Params.Spec spec : Params.SPECS.values()) {
            if (comma) result.append(','); comma = true;
            result.append("{\"id\":").append(quote(spec.id)).append(",\"description\":").append(quote(spec.description))
                .append(",\"default\":").append(spec.defaultValue).append(",\"min\":").append(spec.min)
                .append(",\"max\":").append(spec.max).append(",\"step\":").append(spec.step).append('}');
        }
        result.append("],\"fields\":["); comma = false;
        for (FieldId<?> id : new Generator(0, Params.defaults(), model).fields.ids()) {
            if (comma) result.append(','); comma = true;
            result.append("{\"id\":").append(quote(id.name)).append(",\"label\":").append(quote(id.label))
                .append(",\"type\":").append(quote(id.type.getSimpleName())).append(",\"units\":").append(quote(id.units))
                .append(",\"min\":").append(id.displayMin).append(",\"max\":").append(id.displayMax).append('}');
        }
        return result.append("]}").toString();
    }
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
    private static void send(HttpExchange exchange, int status, String type, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
    }
}
