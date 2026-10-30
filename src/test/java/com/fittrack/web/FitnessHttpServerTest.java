package com.fittrack.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fittrack.repository.*;
import com.fittrack.service.*;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.*;

class FitnessHttpServerTest {
  private FitnessHttpServer server;
  private final HttpClient client = HttpClient.newHttpClient();

  @BeforeEach
  void start() throws Exception {
    server =
        new FitnessHttpServer(
            new FitnessService(
                new InMemoryRepository<>(), new InMemoryRepository<>(), new InMemoryRepository<>()),
            0);
    server.start();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private HttpResponse<String> request(String method, String path, String body) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + path))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static final String MEMBER =
      "{\"id\":1,\"name\":\"Alex\",\"email\":\"alex@example.com\",\"age\":25}";

  // Check that the webpage and its files are included in the Java application.
  @Test
  void servesDashboardAndKeepsApiAvailable() throws Exception {
    var page = request("GET", "/", "");
    assertEquals(200, page.statusCode());
    assertTrue(page.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
    assertTrue(page.body().contains("Your progress, in view."));
    assertTrue(
        page.headers()
            .firstValue("Content-Security-Policy")
            .orElseThrow()
            .contains("script-src 'self'"));
    assertTrue(request("GET", "/app.js", "").body().contains("function saveRecord"));
    assertTrue(
        request("GET", "/styles.css", "")
            .headers()
            .firstValue("Content-Type")
            .orElseThrow()
            .startsWith("text/css"));
    assertEquals(200, request("GET", "/favicon.svg", "").statusCode());
    assertTrue(request("GET", "/api", "").body().contains("FitTrack"));
    assertEquals(405, request("POST", "/", "{}").statusCode());
    assertEquals(404, request("GET", "/pom.xml", "").statusCode());
    var head = request("HEAD", "/", "");
    assertEquals(200, head.statusCode());
    assertEquals("", head.body());
  }

  @Test
  void memberCrudAndHttpSemantics() throws Exception {
    var created = request("POST", "/members", MEMBER);
    assertEquals(201, created.statusCode());
    assertEquals("/members/1", created.headers().firstValue("Location").orElseThrow());
    assertTrue(created.body().contains("\"id\":1"));
    assertEquals(409, request("POST", "/members", MEMBER).statusCode());
    assertEquals(200, request("GET", "/members/1", "").statusCode());
    assertEquals(200, request("PUT", "/members/1", MEMBER.replace("25", "26")).statusCode());
    assertEquals(400, request("PUT", "/members/2", MEMBER).statusCode());
    assertEquals(204, request("DELETE", "/members/1", "").statusCode());
    assertEquals(404, request("GET", "/members/1", "").statusCode());
  }

  @Test
  void workoutChallengeProgressAndDeletionRules() throws Exception {
    request("POST", "/members", MEMBER);
    String workout =
        "{\"id\":1,\"memberId\":1,\"activityType\":\"Run\",\"durationMinutes\":30,\"date\":\"2026-01-01\"}";
    String challenge =
        "{\"id\":1,\"memberId\":1,\"name\":\"Goal\",\"type\":\"MINUTES\",\"target\":60,\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}";
    assertEquals(201, request("POST", "/workouts", workout).statusCode());
    assertEquals(201, request("POST", "/challenges", challenge).statusCode());
    assertTrue(request("GET", "/challenges/1/progress", "").body().contains("50.0"));
    assertEquals(409, request("DELETE", "/members/1", "").statusCode());
    assertTrue(request("GET", "/members/1/summary", "").body().contains("\"totalMinutes\":30"));
    assertEquals(200, request("PUT", "/workouts/1", workout.replace("30", "60")).statusCode());
    assertEquals(
        200,
        request("PUT", "/challenges/1", challenge.replace("MINUTES", "SESSIONS")).statusCode());
    assertTrue(request("GET", "/challenges", "").body().contains("SESSIONS"));
    assertEquals(204, request("DELETE", "/workouts/1", "").statusCode());
    assertEquals(204, request("DELETE", "/challenges/1", "").statusCode());
    assertEquals(204, request("DELETE", "/members/1", "").statusCode());
  }

  @Test
  void rejectsMalformedRequestsAndWrongMethods() throws Exception {
    assertEquals(400, request("POST", "/members", "{").statusCode());
    assertEquals(400, request("POST", "/members", "{id:1}").statusCode());
    assertEquals(400, request("POST", "/members", MEMBER.replace("25", "25.5")).statusCode());
    assertEquals(400, request("POST", "/members", MEMBER.replace("25", "\"25\"")).statusCode());
    assertEquals(400, request("POST", "/members", "null").statusCode());
    assertEquals(404, request("GET", "/unknown", "").statusCode());
    var method = request("PATCH", "/members/1", "");
    assertEquals(405, method.statusCode());
    assertEquals("GET, PUT, DELETE", method.headers().firstValue("Allow").orElseThrow());
    assertEquals(400, request("GET", "/members/zero", "").statusCode());
    assertEquals(413, request("POST", "/members", " ".repeat(65537)).statusCode());
    var wrongType =
        client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + "/members"))
                .POST(HttpRequest.BodyPublishers.ofString(MEMBER))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(415, wrongType.statusCode());
  }
}
