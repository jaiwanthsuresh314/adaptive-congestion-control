package edu.adaptivecc.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/** Local-only HTTP server for the static dashboard and generated result files. */
public final class DashboardServer implements AutoCloseable {
    private final HttpServer server;
    private final Path webRoot;
    private final Path resultsRoot;
    private final ExecutorService runExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, RunStatus> runs = new ConcurrentHashMap<>();

    private DashboardServer(Path webRoot, int port) throws IOException {
        this.webRoot = webRoot.toAbsolutePath().normalize();
        this.resultsRoot = this.webRoot.resolve("demo-results").normalize();
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/health", this::health);
        server.createContext("/api/results", this::results);
        server.createContext("/api/runs", this::runs);
        server.createContext("/api/run-status", this::runStatus);
        server.createContext("/", this::staticFile);
    }

    public static DashboardServer start(Path webRoot, int port) throws IOException {
        DashboardServer dashboard = new DashboardServer(webRoot, port);
        dashboard.server.start();
        return dashboard;
    }

    public int port() { return server.getAddress().getPort(); }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, "method not allowed", "text/plain"); return; }
        respond(exchange, 200, "{\"status\":\"ok\",\"service\":\"adaptivecc-dashboard\"}", "application/json");
    }

    private void results(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, "method not allowed", "text/plain"); return; }
        String path = exchange.getRequestURI().getPath();
        if ("/api/results".equals(path) || "/api/results/".equals(path)) {
            if (!Files.isDirectory(resultsRoot)) { respond(exchange, 200, "[]", "application/json"); return; }
            try (Stream<Path> files = Files.list(resultsRoot)) {
                List<String> entries = files.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString))
                        .map(file -> "{\"name\":\"" + escape(file.getFileName().toString()) + "\",\"size\":" + size(file) + "}")
                        .toList();
                respond(exchange, 200, "[" + String.join(",", entries) + "]", "application/json");
            }
            return;
        }
        String name = path.substring("/api/results/".length());
        Path file = resultsRoot.resolve(name).normalize();
        if (!file.startsWith(resultsRoot) || !Files.isRegularFile(file)) { respond(exchange, 404, "not found", "text/plain"); return; }
        respond(exchange, 200, Files.readString(file), contentType(file));
    }

    private void runs(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/api/runs".equals(path) || "/api/runs/".equals(path)) {
            respond(exchange, 405, "use POST /api/runs/{quick|loss|delay}", "text/plain");
            return;
        }
        String preset = path.substring("/api/runs/".length());
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed", "text/plain");
            return;
        }
        if (!Set.of("quick", "loss", "delay").contains(preset)) {
            respond(exchange, 400, "unknown preset", "text/plain");
            return;
        }
        if (runs.values().stream().anyMatch(run -> run.state.equals("running"))) {
            respond(exchange, 409, "a benchmark is already running", "text/plain");
            return;
        }
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        RunStatus run = new RunStatus(id, preset);
        runs.put(id, run);
        runExecutor.submit(() -> executePreset(run));
        respond(exchange, 202, runJson(run), "application/json");
    }

    private void runStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed", "text/plain");
            return;
        }
        String id = exchange.getRequestURI().getPath().substring("/api/run-status/".length());
        RunStatus run = runs.get(id);
        if (run == null) { respond(exchange, 404, "not found", "text/plain"); return; }
        respond(exchange, 200, runJson(run), "application/json");
    }

    private void executePreset(RunStatus run) {
        run.state = "running";
        try {
            String scenario = "live-" + run.preset;
            String impairment = switch (run.preset) {
                case "loss" -> "--drop-sequence=5 --drop-count=1";
                case "delay" -> "--delay-sequence=5 --delay-ms=60";
                default -> "";
            };
            for (String controller : List.of("fixed", "aimd", "adaptive")) {
                String output = resultsRoot.resolve("live-" + run.id + "-" + controller + ".csv").toString();
                List<String> args = new java.util.ArrayList<>(List.of(
                        "--controller=" + controller, "--packets=40", "--payload=256",
                        "--seed=20261025", "--scenario=" + scenario,
                        "--network=application-emulation", "--output=" + output));
                if (!impairment.isBlank()) args.addAll(List.of(impairment.split(" ")));
                edu.adaptivecc.experiments.BenchmarkMain.main(args.toArray(String[]::new));
            }
            run.state = "completed";
        } catch (Exception exception) {
            run.state = "failed";
            run.error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
    }

    private static String runJson(RunStatus run) {
        return "{\"id\":\"" + escape(run.id) + "\",\"preset\":\"" + escape(run.preset)
                + "\",\"status\":\"" + escape(run.state) + "\""
                + (run.error == null ? "" : ",\"error\":\"" + escape(run.error) + "\"") + "}";
    }

    private static final class RunStatus {
        private final String id;
        private final String preset;
        private volatile String state = "queued";
        private volatile String error;

        private RunStatus(String id, String preset) { this.id = id; this.preset = preset; }
    }

    private void staticFile(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, "method not allowed", "text/plain"); return; }
        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath.equals("/")) requestPath = "/index.html";
        Path file = webRoot.resolve(requestPath.substring(1)).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) { respond(exchange, 404, "not found", "text/plain"); return; }
        respond(exchange, 200, Files.readAllBytes(file), contentType(file));
    }

    private static long size(Path file) {
        try { return Files.size(file); } catch (IOException e) { return 0; }
    }

    private static String contentType(Path file) {
        return switch (file.getFileName().toString().substring(file.getFileName().toString().lastIndexOf('.') + 1)) {
            case "html" -> "text/html; charset=utf-8";
            case "js" -> "text/javascript; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "json" -> "application/json; charset=utf-8";
            case "csv", "properties" -> "text/plain; charset=utf-8";
            default -> "application/octet-stream";
        };
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        respond(exchange, status, body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private static void respond(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
    }

    @Override public void close() { runExecutor.shutdownNow(); server.stop(0); }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(argument(args, "root", "frontend"));
        int port = Integer.parseInt(argument(args, "port", "8000"));
        DashboardServer server = DashboardServer.start(root, port);
        System.out.println("Dashboard server listening at http://127.0.0.1:" + server.port() + "/");
        try { Thread.currentThread().join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String argument(String[] args, String name, String fallback) {
        String prefix = "--" + name + "=";
        for (String arg : args) if (arg.startsWith(prefix)) return arg.substring(prefix.length());
        return fallback;
    }
}
