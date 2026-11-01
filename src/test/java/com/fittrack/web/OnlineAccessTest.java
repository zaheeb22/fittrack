package com.fittrack.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fittrack.repository.*;
import com.fittrack.service.FitnessService;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.*;

class OnlineAccessTest {
  private FitnessHttpServer server;
  private final HttpClient client = HttpClient.newHttpClient();
  private String base;
  private static final String LOGIN =
      "{\"username\":\"owner\",\"password\":\"a-long-test-password\"}";

  @BeforeEach
  void start() throws Exception {
    server = new FitnessHttpServer(
        new FitnessService(new InMemoryRepository<>(), new InMemoryRepository<>(),
            new InMemoryRepository<>()),
        new ServerSettings("127.0.0.1", 0, "owner", "a-long-test-password"));
    server.start();
    base = "http://127.0.0.1:" + server.getPort();
  }

  @AfterEach
  void stop() {
    if (server != null) server.close();
  }

  private HttpResponse<String> request(String method, String path, String body,
      String cookie, boolean safe) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create(base + path))
        .header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body));
    if (cookie != null) builder.header("Cookie", cookie);
    if (safe) builder.header("X-FitTrack-Request", "true");
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String signIn() throws Exception {
    var result = request("POST", "/auth/login", LOGIN, null, true);
    assertEquals(200, result.statusCode());
    return result.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
  }

  @Test
  void showsLoginAndProtectsPagesAndRecordRoutes() throws Exception {
    var page = request("GET", "/", "", null, false);
    assertEquals(303, page.statusCode());
    assertEquals("/login", page.headers().firstValue("Location").orElseThrow());
    for (String path : new String[] {"/app.js", "/members", "/workouts", "/challenges", "/api", "/auth/session"}) {
      var result = request("GET", path, "", null, false);
      assertEquals(401, result.statusCode());
      assertTrue(result.headers().firstValue("WWW-Authenticate").isEmpty());
      assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
    }
    assertTrue(request("GET", "/login", "", null, false).body().contains("login-form"));
    assertEquals(200, request("GET", "/login.js", "", null, false).statusCode());
    assertEquals(200, request("GET", "/styles.css", "", null, false).statusCode());
    assertEquals(200, request("GET", "/favicon.ico", "", null, false).statusCode());
    assertEquals(200, request("GET", "/health", "", null, false).statusCode());
    assertEquals(404, request("GET", "/health/members", "", null, false).statusCode());
  }

  @Test
  void logoutInvalidatesOldCookieAndBasicCredentialsDoNotSignBackIn() throws Exception {
    String cookie = signIn();
    assertEquals(200, request("GET", "/members", "", cookie, false).statusCode());
    assertTrue(request("GET", "/auth/session", "", cookie, false).body().contains("\"authenticated\":true"));
    assertEquals(303, request("GET", "/login", "", cookie, false).statusCode());
    var logout = request("POST", "/auth/logout", "", cookie, true);
    assertEquals(204, logout.statusCode());
    assertTrue(logout.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
    assertEquals(401, request("GET", "/members", "", cookie, false).statusCode());
    String basic = Base64.getEncoder().encodeToString(
        "owner:a-long-test-password".getBytes(StandardCharsets.UTF_8));
    var cached = client.send(HttpRequest.newBuilder(URI.create(base + "/members"))
        .header("Authorization", "Basic " + basic).build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(401, cached.statusCode());
    String fresh = signIn();
    assertNotEquals(cookie, fresh);
    assertEquals(200, request("GET", "/", "", fresh, false).statusCode());
  }

  @Test
  void rejectsCrossSiteChangesAndAllowsNormalSignedInCrud() throws Exception {
    assertEquals(403, request("POST", "/auth/login", LOGIN, null, false).statusCode());
    String cookie = signIn();
    String member = "{\"id\":77,\"name\":\"Test\",\"email\":\"test@example.com\",\"age\":25}";
    assertEquals(403, request("POST", "/members", member, cookie, false).statusCode());
    var crossSite = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/logout"))
        .header("Cookie", cookie).header("X-FitTrack-Request", "true")
        .header("Sec-Fetch-Site", "cross-site").POST(HttpRequest.BodyPublishers.noBody())
        .build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(403, crossSite.statusCode());
    assertEquals(201, request("POST", "/members", member, cookie, true).statusCode());
    assertEquals(204, request("DELETE", "/members/77", "", cookie, true).statusCode());
    assertEquals(405, request("GET", "/auth/logout", "", cookie, false).statusCode());
    assertEquals(200, request("GET", "/members", "", cookie, false).statusCode());
  }

  @Test
  void checksLoginInputAndLimitsRepeatedWrongPasswords() throws Exception {
    assertEquals(400, request("POST", "/auth/login", "{", null, true).statusCode());
    assertEquals(413, request("POST", "/auth/login", " ".repeat(4097), null, true).statusCode());
    for (int i = 0; i < 5; i++) {
      var failed = request("POST", "/auth/login", LOGIN.replace("a-long-test-password", "wrong"), null, true);
      assertEquals(401, failed.statusCode());
      assertTrue(failed.headers().firstValue("Set-Cookie").isEmpty());
    }
    var blocked = request("POST", "/auth/login", LOGIN, null, true);
    assertEquals(429, blocked.statusCode());
    assertTrue(blocked.headers().firstValue("Retry-After").isPresent());
  }

  @Test
  void loginRotatesTheCookieAndDoesNotCachePrivatePages() throws Exception {
    String cookie = signIn();
    var login = request("POST", "/auth/login", LOGIN, cookie, true);
    String header = login.headers().firstValue("Set-Cookie").orElseThrow();
    assertTrue(header.contains("HttpOnly"));
    assertTrue(header.contains("SameSite=Strict"));
    assertTrue(header.contains("Path=/"));
    assertTrue(header.contains("Max-Age=28800"));
    assertFalse(header.contains("Domain="));
    assertEquals(401, request("GET", "/members", "", cookie, false).statusCode());
    String fresh = header.split(";", 2)[0];
    assertEquals("no-store", request("GET", "/", "", fresh, false)
        .headers().firstValue("Cache-Control").orElseThrow());
  }
}
