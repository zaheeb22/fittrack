package com.fittrack.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fittrack.repository.*;
import com.fittrack.service.FitnessService;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SharedLoginTest {
  private FitnessHttpServer server(SessionRepository sessions, String password, String host) throws Exception {
    var service = new FitnessService(new InMemoryRepository<>(), new InMemoryRepository<>(), new InMemoryRepository<>());
    var server = new FitnessHttpServer(service, new ServerSettings(host, 0, "owner", password), sessions);
    server.start();
    return server;
  }

  private HttpResponse<String> request(FitnessHttpServer server, String method, String path,
      String body, String cookie) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + path))
        .header("Content-Type", "application/json").header("X-FitTrack-Request", "true")
        .method(method, HttpRequest.BodyPublishers.ofString(body));
    if (cookie != null) builder.header("Cookie", cookie);
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void loginWorksAcrossInstancesAndLogoutRevokesItEverywhere() throws Exception {
    String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    ConnectionFactory connections = () -> DriverManager.getConnection(url);
    var storage = new JdbcSessionRepository(connections);
    storage.initialise();
    try (var first = server(storage, "a-long-test-password", "0.0.0.0");
         var second = server(new JdbcSessionRepository(connections), "a-long-test-password", "0.0.0.0");
         var changed = server(new JdbcSessionRepository(connections), "a-different-password", "0.0.0.0")) {
      var login = request(first, "POST", "/auth/login",
          "{\"username\":\"owner\",\"password\":\"a-long-test-password\"}", null);
      assertEquals(200, login.statusCode());
      String header = login.headers().firstValue("Set-Cookie").orElseThrow();
      assertTrue(header.contains("; Secure"));
      String cookie = header.split(";", 2)[0];
      assertEquals(200, request(second, "GET", "/members", "", cookie).statusCode());
      assertEquals(401, request(changed, "GET", "/members", "", cookie).statusCode());
      try (var c = connections.open(); var s = c.createStatement();
           var rows = s.executeQuery("SELECT token_hash FROM login_sessions")) {
        assertTrue(rows.next());
        assertEquals(64, rows.getString(1).length());
        assertFalse(cookie.contains(rows.getString(1)));
      }
      assertEquals(204, request(second, "POST", "/auth/logout", "", cookie).statusCode());
      assertEquals(401, request(first, "GET", "/members", "", cookie).statusCode());
      var again = request(first, "POST", "/auth/login",
          "{\"username\":\"owner\",\"password\":\"a-long-test-password\"}", null);
      String newCookie = again.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
      try (var c = connections.open(); var s = c.createStatement()) {
        s.executeUpdate("UPDATE login_sessions SET expires_at=0");
      }
      assertEquals(401, request(first, "GET", "/members", "", newCookie).statusCode());
    }
  }
}
