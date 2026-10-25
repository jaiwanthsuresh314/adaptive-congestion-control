package edu.adaptivecc.dashboard;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

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

    private static HttpResponse<String> get(HttpClient client, String uri) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
