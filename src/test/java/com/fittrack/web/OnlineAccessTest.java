package com.fittrack.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fittrack.repository.InMemoryRepository;
import com.fittrack.service.FitnessService;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class OnlineAccessTest {
  @Test
  void protectsThePageFilesAndAllRecordRoutes() throws Exception {
    var service =
        new FitnessService(
            new InMemoryRepository<>(), new InMemoryRepository<>(), new InMemoryRepository<>());
    try (var server =
        new FitnessHttpServer(
            service, new ServerSettings("127.0.0.1", 0, "owner", "a-long-test-password"))) {
      server.start();
      var client = HttpClient.newHttpClient();
      String base = "http://127.0.0.1:" + server.getPort();
      for (String path :
          new String[] {"/", "/app.js", "/members", "/workouts", "/challenges", "/api"}) {
        var result =
            client.send(
                HttpRequest.newBuilder(URI.create(base + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, result.statusCode());
        assertTrue(
            result.headers().firstValue("WWW-Authenticate").orElseThrow().contains("FitTrack"));
      }
      String valid =
          Base64.getEncoder()
              .encodeToString("owner:a-long-test-password".getBytes(StandardCharsets.UTF_8));
      String wrong =
          Base64.getEncoder().encodeToString("owner:incorrect".getBytes(StandardCharsets.UTF_8));
      var rejected =
          client.send(
              HttpRequest.newBuilder(URI.create(base + "/members"))
                  .header("Authorization", "Basic " + wrong)
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(401, rejected.statusCode());
      var allowed =
          client.send(
              HttpRequest.newBuilder(URI.create(base + "/members"))
                  .header("Authorization", "Basic " + valid)
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(200, allowed.statusCode());
      assertEquals("[]", allowed.body());
      var unauthorisedWrite =
          client.send(
              HttpRequest.newBuilder(URI.create(base + "/members"))
                  .POST(HttpRequest.BodyPublishers.ofString("{}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(401, unauthorisedWrite.statusCode());
      var health =
          client.send(
              HttpRequest.newBuilder(URI.create(base + "/health")).build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(200, health.statusCode());
      assertEquals("{\"status\":\"ok\"}", health.body());
      var fakeHealth =
          client.send(
              HttpRequest.newBuilder(URI.create(base + "/health/members")).build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(404, fakeHealth.statusCode());
    }
  }
}
