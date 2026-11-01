package com.fittrack.web;

import com.fittrack.model.MemberAccount;
import com.fittrack.repository.Repository;
import com.fittrack.repository.SessionRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Checks admin and member sign-in and gives the browser a random session cookie. */
final class LoginSessions {
  private static final String COOKIE = "FITTRACK_SESSION";
  private static final long LIFETIME = 8 * 60 * 60;
  private final ServerSettings settings;
  private final Repository<MemberAccount> accounts;
  private final SessionRepository sessions;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

  LoginSessions(
      ServerSettings settings, SessionRepository sessions, Repository<MemberAccount> accounts) {
    this.settings = Objects.requireNonNull(settings);
    this.sessions = Objects.requireNonNull(sessions);
    this.accounts = Objects.requireNonNull(accounts);
    this.clock = Clock.systemUTC();
  }

  int memberId(HttpExchange request) {
    String token = cookie(request);
    return token != null && token.startsWith("m") && token.contains(".")
        ? Integer.parseInt(token.substring(1, token.indexOf('.')))
        : 0;
  }

  boolean enabled() {
    return settings.hasSignIn();
  }

  boolean signedIn(HttpExchange request) {
    if (!enabled()) return true;
    String token = cookie(request);
    return token != null && sessions.isActive(hash(token), clock.instant().getEpochSecond());
  }

  Integer authenticate(String username, String password) {
    boolean userMatches = MessageDigest.isEqual(digest(username), digest(settings.username()));
    boolean passwordMatches = MessageDigest.isEqual(digest(password), digest(settings.password()));
    if (userMatches & passwordMatches) return 0;
    return accounts.findAll().stream()
        .filter(a -> a.username().equalsIgnoreCase(username))
        .filter(a -> Passwords.matches(password, a.passwordHash()))
        .map(MemberAccount::memberId)
        .findFirst()
        .orElse(null);
  }

  boolean reservedUsername(String username) {
    return username.equalsIgnoreCase(settings.username());
  }

  boolean allowAttempt(HttpExchange request) {
    long now = clock.instant().getEpochSecond();
    attempts.values().removeIf(value -> value.until <= now);
    Attempts previous = attempts.get(address(request));
    return attempts.size() < 1000 && (previous == null || previous.count < 5);
  }

  void failedAttempt(HttpExchange request) {
    long now = clock.instant().getEpochSecond();
    attempts.compute(
        address(request),
        (key, value) ->
            value == null || value.until <= now
                ? new Attempts(1, now + 300)
                : new Attempts(value.count + 1, value.until));
  }

  void start(HttpExchange request, int memberId) {
    // Each successful login gets a new code. An older cookie is ended before replacement.
    end(request);
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token =
        (memberId == 0 ? "" : "m" + memberId + ".")
            + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    long now = clock.instant().getEpochSecond();
    sessions.save(hash(token), now + LIFETIME, now);
    request.getResponseHeaders().set("Set-Cookie", cookieHeader(request, token, LIFETIME));
    attempts.remove(address(request));
  }

  void end(HttpExchange request) {
    String token = cookie(request);
    if (token != null) sessions.delete(hash(token));
    request.getResponseHeaders().set("Set-Cookie", cookieHeader(request, "", 0));
  }

  private String cookieHeader(HttpExchange request, String token, long age) {
    boolean local =
        settings.getHost().equals("127.0.0.1")
            || settings.getHost().equals("localhost")
            || settings.getHost().equals("::1");
    boolean secure =
        !local
            || request instanceof HttpsExchange
            || "https".equalsIgnoreCase(request.getRequestHeaders().getFirst("X-Forwarded-Proto"));
    return COOKIE
        + "="
        + token
        + "; Path=/; HttpOnly; SameSite=Strict; Max-Age="
        + age
        + (secure ? "; Secure" : "");
  }

  private String cookie(HttpExchange request) {
    for (String header : request.getRequestHeaders().getOrDefault("Cookie", java.util.List.of())) {
      for (String part : header.split(";")) {
        String[] pair = part.strip().split("=", 2);
        if (pair.length == 2
            && pair[0].equals(COOKIE)
            && pair[1].matches("(?:m[1-9][0-9]{0,9}\\.)?[A-Za-z0-9_-]{43}")) {
          if (pair[1].startsWith("m") && pair[1].contains(".")) {
            try {
              Integer.parseInt(pair[1].substring(1, pair[1].indexOf('.')));
            } catch (NumberFormatException error) {
              continue;
            }
          }
          return pair[1];
        }
      }
    }
    return null;
  }

  private String hash(String token) {
    // The member number is covered by the hash. Changing it cannot grant another member's access.
    // Changing or removing an account also invalidates its old cookies.
    String memberHash = "";
    if (token.startsWith("m") && token.contains(".")) {
      int id = Integer.parseInt(token.substring(1, token.indexOf('.')));
      memberHash = accounts.findById(id).map(MemberAccount::passwordHash).orElse("removed");
    }
    return HexFormat.of()
        .formatHex(
            digest(
                token
                    + "\0"
                    + settings.username()
                    + "\0"
                    + settings.password()
                    + (memberHash.isEmpty() ? "" : "\0" + memberHash)));
  }

  private static byte[] digest(String text) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }

  private String address(HttpExchange request) {
    // The connection address is used rather than trusting a client-supplied forwarding header.
    return request.getRemoteAddress().getAddress().getHostAddress();
  }

  private record Attempts(int count, long until) {}
}
