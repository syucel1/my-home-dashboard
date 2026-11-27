package dev.homehub.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** Phase 2 exit check: the app starts, reports healthy and serves the first API endpoint. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HomeHubApplicationTests {

    @Value("${local.server.port}")
    int port;

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void systemInfoEndpointReportsNameAndJavaVersion() throws Exception {
        HttpResponse<String> response = get("/api/v1/system/info");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"name\":\"home-hub\"")
                .contains("\"javaVersion\":\"" + Runtime.version());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .GET()
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
