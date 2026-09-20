package dev.datnguyen.missionscheduler.network;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NetworkApiTest {
    @Autowired TestRestTemplate http;
    @Test void servesDashboardPlanAndValidatedControls() {
        var page = http.getForEntity("/", String.class);
        assertEquals(HttpStatus.OK, page.getStatusCode()); assertTrue(page.getBody().contains("Follow the signal"));
        assertEquals(HttpStatus.OK, http.getForEntity("/api/plan", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.postForEntity("/api/start", null, String.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.postForEntity("/api/pause", null, String.class).getStatusCode());
        var invalid = http.exchange("/api/controls", HttpMethod.PUT,
            new HttpEntity<>(Map.of("primaryUp", true, "backupUp", true, "delayMs", 999, "lossPercent", 0)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        var changed = http.exchange("/api/controls", HttpMethod.PUT,
            new HttpEntity<>(new NetworkLab.Controls(false, true, 120, 20)), String.class);
        assertEquals(HttpStatus.OK, changed.getStatusCode()); assertTrue(changed.getBody().contains("BACKUP"));
        assertEquals(HttpStatus.OK, http.postForEntity("/api/reset", null, String.class).getStatusCode());
    }
}
