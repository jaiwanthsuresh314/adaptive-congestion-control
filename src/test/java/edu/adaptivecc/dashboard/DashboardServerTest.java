package edu.adaptivecc.dashboard;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServerTest {
    @Test void servesHealthArtifactsAndFrontend() throws Exception {
        try (DashboardServer server = DashboardServer.start(Path.of("frontend"), 0)) {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();
            assertEquals(200, get(client, base + "/api/health").statusCode());
            assertTrue(get(client, base + "/api/results").body().contains("adaptive.csv"));
            assertTrue(get(client, base + "/api/results/adaptive.csv").body().contains("scenario"));
            assertEquals(200, get(client, base + "/").statusCode());
            assertTrue(get(client, base + "/").body().contains("AdaptiveCC Results Dashboard"));
            assertEquals(404, get(client, base + "/api/results/../README.md").statusCode());
        }
    }

    @Test void runsOnlyAllowlistedLocalPreset() throws Exception {
        Path root = Files.createTempDirectory("adaptivecc-dashboard-");
        Files.createDirectories(root.resolve("demo-results"));
        Files.writeString(root.resolve("index.html"), "dashboard");
        try (DashboardServer server = DashboardServer.start(root, 0)) {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();
            HttpResponse<String> rejected = post(client, base + "/api/runs/arbitrary");
            assertEquals(400, rejected.statusCode());

            HttpResponse<String> accepted = post(client, base + "/api/runs/quick");
            assertEquals(202, accepted.statusCode());
            assertTrue(accepted.body().contains("\"status\":\"queued\"")
                    || accepted.body().contains("\"status\":\"running\""));
            String id = accepted.body().split("\\\"id\\\":\\\"")[1].split("\\\"")[0];
            long deadline = System.nanoTime() + 15_000_000_000L;
            String status;
            do {
                Thread.sleep(100);
                status = get(client, base + "/api/run-status/" + id).body();
            } while ((status.contains("\"status\":\"queued\"")
                    || status.contains("\"status\":\"running\""))
                    && System.nanoTime() < deadline);
            assertTrue(status.contains("\"status\":\"completed\""), status);
            assertTrue(get(client, base + "/api/results").body().contains("live-" + id));
        }
    }

    private static HttpResponse<String> get(HttpClient client, String uri) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(HttpClient client, String uri) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(uri)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
