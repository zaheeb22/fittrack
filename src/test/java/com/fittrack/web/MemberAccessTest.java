package com.fittrack.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fittrack.model.*;
import com.fittrack.repository.*;
import com.fittrack.service.FitnessService;
import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// These requests check permissions at the server, including requests made without the webpage.
class MemberAccessTest {
  private static final String PASSWORD = "a-member-test-password";
  private final HttpClient client = HttpClient.newHttpClient();

  private FitnessService records() {
    var service =
        new FitnessService(
            new InMemoryRepository<>(), new InMemoryRepository<>(), new InMemoryRepository<>());
    service.createMember(new Member(1, "First member", "first@example.com", 25));
    service.createMember(new Member(2, "Second member", "second@example.com", 30));
    service.createWorkout(new Workout(11, 1, "Walking", 30, "2026-01-01"));
    service.createWorkout(new Workout(22, 2, "Running", 40, "2026-01-01"));
    service.createChallenge(
        new MinutesChallenge(111, 1, "First goal", 60, "2026-01-01", "2026-01-31"));
    service.createChallenge(
        new MinutesChallenge(222, 2, "Second goal", 80, "2026-01-01", "2026-01-31"));
    return service;
  }

  private HttpResponse<String> request(
      FitnessHttpServer server, String method, String path, String body, String cookie)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + path))
            .header("Content-Type", "application/json")
            .header("X-FitTrack-Request", "true")
            .method(method, HttpRequest.BodyPublishers.ofString(body));
    if (cookie != null) builder.header("Cookie", cookie);
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String login(FitnessHttpServer server, String username, String password)
      throws Exception {
    var response =
        request(
            server,
            "POST",
            "/auth/login",
            new Gson().toJson(java.util.Map.of("username", username, "password", password)),
            null);
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
  }

  private String accountBody(String username) {
    return new Gson().toJson(java.util.Map.of("username", username, "password", PASSWORD));
  }

  @Test
  void membersOnlyReadTheirRecordsAndDeleteTheirOwnGoals() throws Exception {
    var service = records();
    var accounts = new InMemoryRepository<MemberAccount>();
    try (var server =
        new FitnessHttpServer(
            service,
            new ServerSettings("127.0.0.1", 0, "admin", "a-long-admin-password"),
            new MemorySessionRepository(),
            accounts)) {
      server.start();
      String admin = login(server, "admin", "a-long-admin-password");
      assertEquals(
          200, request(server, "PUT", "/accounts/1", accountBody("first"), admin).statusCode());
      assertEquals(
          200, request(server, "PUT", "/accounts/2", accountBody("second"), admin).statusCode());
      String member = login(server, "FIRST", PASSWORD);
      for (String path : new String[] {"/members", "/workouts", "/challenges"}) {
        var list =
            JsonParser.parseString(request(server, "GET", path, "", member).body())
                .getAsJsonArray();
        assertEquals(1, list.size());
        assertFalse(list.toString().contains("Second"));
        assertEquals(
            2,
            JsonParser.parseString(request(server, "GET", path, "", admin).body())
                .getAsJsonArray()
                .size());
      }
      for (String path :
          new String[] {
            "/members/1",
            "/members/1/summary",
            "/workouts/11",
            "/challenges/111",
            "/challenges/111/progress"
          }) assertEquals(200, request(server, "GET", path, "", member).statusCode(), path);
      for (String path :
          new String[] {
            "/members/2",
            "/members/2/summary",
            "/workouts/22",
            "/challenges/222",
            "/challenges/222/progress"
          }) assertEquals(404, request(server, "GET", path, "", member).statusCode(), path);
      for (String path : new String[] {"/members", "/workouts", "/challenges"})
        assertEquals(403, request(server, "POST", path, "{}", member).statusCode());
      for (String path : new String[] {"/members/1", "/workouts/11", "/challenges/111"})
        assertEquals(403, request(server, "PUT", path, "{}", member).statusCode());
      for (String path : new String[] {"/members/1", "/workouts/11"})
        assertEquals(403, request(server, "DELETE", path, "", member).statusCode());
      assertEquals(404, request(server, "DELETE", "/challenges/222", "", member).statusCode());
      assertEquals(204, request(server, "DELETE", "/challenges/111", "", member).statusCode());
      assertEquals(222, service.challenges().getFirst().getId());
      for (String method : new String[] {"GET", "PUT", "DELETE"})
        assertEquals(
            403,
            request(
                    server,
                    method,
                    method.equals("GET") ? "/accounts" : "/accounts/1",
                    "{}",
                    member)
                .statusCode());
      assertEquals(
          "MEMBER",
          JsonParser.parseString(request(server, "GET", "/auth/session", "", member).body())
              .getAsJsonObject()
              .get("role")
              .getAsString());
      // Editing the member number in a cookie never changes the authenticated owner.
      assertEquals(
          401, request(server, "GET", "/members", "", member.replace("m1.", "m2.")).statusCode());
      assertEquals(
          401, request(server, "GET", "/members", "", member.replace("m1.", "")).statusCode());
      assertEquals(
          401,
          request(server, "GET", "/members", "", "FITTRACK_SESSION=m9999999999." + "a".repeat(43))
              .statusCode());
      assertEquals(204, request(server, "POST", "/auth/logout", "", member).statusCode());
      assertEquals(401, request(server, "GET", "/members", "", member).statusCode());
    }
  }

  @Test
  void accountResetRemovalAndMemberDeletionEndAccessAcrossInstances() throws Exception {
    String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    ConnectionFactory connections = () -> DriverManager.getConnection(url);
    try (var c = connections.open();
        var s = c.createStatement()) {
      s.executeUpdate("CREATE TABLE members (id INT PRIMARY KEY)");
      s.executeUpdate("INSERT INTO members VALUES (1),(2)");
    }
    var accounts = new JdbcAccountRepository(connections);
    accounts.initialise();
    var sessions = new JdbcSessionRepository(connections);
    sessions.initialise();
    var settings = new ServerSettings("127.0.0.1", 0, "admin", "a-long-admin-password");
    var service = records();
    try (var first = new FitnessHttpServer(service, settings, sessions, accounts);
        var second =
            new FitnessHttpServer(
                service,
                settings,
                new JdbcSessionRepository(connections),
                new JdbcAccountRepository(connections))) {
      first.start();
      second.start();
      String admin = login(first, "admin", "a-long-admin-password");
      assertEquals(
          200, request(first, "PUT", "/accounts/1", accountBody("first"), admin).statusCode());
      String member = login(first, "first", PASSWORD);
      assertEquals(200, request(second, "GET", "/members/1", "", member).statusCode());
      assertEquals(
          200, request(second, "PUT", "/accounts/1", accountBody("first"), admin).statusCode());
      assertEquals(401, request(first, "GET", "/members", "", member).statusCode());
      member = login(first, "first", PASSWORD);
      assertEquals(204, request(second, "DELETE", "/accounts/1", "", admin).statusCode());
      assertEquals(401, request(first, "GET", "/members", "", member).statusCode());
      assertEquals(
          200, request(first, "PUT", "/accounts/1", accountBody("first"), admin).statusCode());
      member = login(first, "first", PASSWORD);
      assertEquals(204, request(first, "DELETE", "/workouts/11", "", admin).statusCode());
      assertEquals(204, request(first, "DELETE", "/challenges/111", "", admin).statusCode());
      assertEquals(204, request(first, "DELETE", "/members/1", "", admin).statusCode());
      assertEquals(401, request(second, "GET", "/members", "", member).statusCode());
      assertTrue(accounts.findById(1).isEmpty());
    }
  }

  @Test
  void accountsValidateNamesPasswordsAndNeverReturnHashes() throws Exception {
    var accounts = new InMemoryRepository<MemberAccount>();
    try (var server =
        new FitnessHttpServer(
            records(),
            new ServerSettings("127.0.0.1", 0, "admin", "a-long-admin-password"),
            new MemorySessionRepository(),
            accounts)) {
      server.start();
      String admin = login(server, "admin", "a-long-admin-password");
      assertEquals(
          409, request(server, "PUT", "/accounts/1", accountBody("ADMIN"), admin).statusCode());
      assertEquals(
          400,
          request(
                  server,
                  "PUT",
                  "/accounts/1",
                  "{\"username\":\"first\",\"password\":\"short\"}",
                  admin)
              .statusCode());
      assertEquals(
          400, request(server, "PUT", "/accounts/1", accountBody("bad name"), admin).statusCode());
      assertEquals(
          404, request(server, "PUT", "/accounts/99", accountBody("missing"), admin).statusCode());
      assertEquals(
          200, request(server, "PUT", "/accounts/1", accountBody("first"), admin).statusCode());
      String hash = accounts.findById(1).orElseThrow().passwordHash();
      assertFalse(hash.contains(PASSWORD));
      assertTrue(Passwords.matches(PASSWORD, hash));
      assertFalse(Passwords.matches("wrong", hash));
      assertNotEquals(hash, Passwords.hash(PASSWORD));
      assertEquals(
          409, request(server, "PUT", "/accounts/2", accountBody("FIRST"), admin).statusCode());
      String body = request(server, "GET", "/accounts", "", admin).body();
      assertFalse(body.contains(hash));
      assertFalse(body.contains(PASSWORD));
      assertFalse(body.contains("password"));
    }
  }
}
