package com.fittrack.web;

import com.fittrack.model.*;
import com.fittrack.repository.*;
import com.fittrack.service.FitnessService;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/** Handles web requests for members, workouts and challenges using Java HttpServer and JSON. */
public final class FitnessHttpServer implements AutoCloseable {
  private static final Logger LOG = Logger.getLogger(FitnessHttpServer.class.getName());
  private final HttpServer server;
  private final ExecutorService executor = Executors.newFixedThreadPool(4);
  private final FitnessService service;
  private final LoginSessions sessions;
  private final Repository<MemberAccount> accounts;
  private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();

  /**
   * Creates an API server that accepts connections only from this computer.
   *
   * @param service application service
   * @param port server port, or zero to let Java choose a free port for a test
   * @throws IOException if the server cannot open the address or port
   */
  public FitnessHttpServer(FitnessService service, int port) throws IOException {
    this(service, new ServerSettings("127.0.0.1", port, "", ""));
  }

  /**
   * Creates a server for local use or password-protected online hosting.
   *
   * @param service application service
   * @param settings checked address and sign-in settings
   * @throws IOException if the port cannot be opened
   */
  public FitnessHttpServer(FitnessService service, ServerSettings settings) throws IOException {
    this(service, settings, new MemorySessionRepository());
  }

  /**
   * Creates a server with shared session storage for hosted sign-in and logout.
   *
   * @param service application rules and records
   * @param settings server address and admin credentials
   * @param sessionStorage memory or database storage for signed-in sessions
   * @throws IOException if the port cannot be opened
   */
  public FitnessHttpServer(
      FitnessService service, ServerSettings settings, SessionRepository sessionStorage)
      throws IOException {
    this(service, settings, sessionStorage, new InMemoryRepository<>());
  }

  /**
   * Creates a server with member accounts and shared session storage.
   *
   * @param service application records and rules
   * @param settings server address and admin credentials
   * @param sessionStorage saved sign-in sessions
   * @param accounts saved member accounts
   * @throws IOException if the port cannot be opened
   */
  public FitnessHttpServer(
      FitnessService service,
      ServerSettings settings,
      SessionRepository sessionStorage,
      Repository<MemberAccount> accounts)
      throws IOException {
    this.service = Objects.requireNonNull(service);
    this.accounts = Objects.requireNonNull(accounts);
    sessions = new LoginSessions(settings, sessionStorage, accounts);
    server = HttpServer.create(new InetSocketAddress(settings.getHost(), settings.getPort()), 0);
    server.createContext("/", this::handle);
    // Hosting health checks can read this small status response without a password.
    // No member, workout or challenge data is available here.
    server.createContext("/health", this::health);
    server.setExecutor(executor);
  }

  private void health(HttpExchange request) throws IOException {
    try {
      if (!request.getRequestURI().getPath().equals("/health")) {
        reply(request, 404, Map.of("error", "Route not found"));
      } else if (request.getRequestMethod().equals("GET")) {
        reply(request, 200, Map.of("status", "ok"));
      } else if (request.getRequestMethod().equals("HEAD")) {
        request.sendResponseHeaders(200, -1);
      } else {
        methodNotAllowed(request, "GET, HEAD");
      }
    } finally {
      request.close();
    }
  }

  /** Starts accepting requests. */
  public void start() {
    server.start();
  }

  /**
   * Returns the port the server is using.
   *
   * @return server port number
   */
  public int getPort() {
    return server.getAddress().getPort();
  }

  /** Stops the web server and the threads that handle its requests. */
  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }

  private void handle(HttpExchange x) throws IOException {
    try {
      // Private pages and record responses are not saved in the browser cache.
      x.getResponseHeaders().set("Cache-Control", "no-store");
      x.getResponseHeaders().set("Pragma", "no-cache");
      String path = x.getRequestURI().getPath();
      if (authentication(x, path)) return;
      if (DashboardPages.serve(x)) return;
      int viewer = sessions.enabled() ? sessions.memberId(x) : 0;
      if (path.startsWith("/accounts")) {
        accountRequest(x, path, viewer);
        return;
      }
      if (path.equals("/api")) {
        if (!x.getRequestMethod().equals("GET")) {
          methodNotAllowed(x, "GET");
          return;
        }
        reply(
            x,
            200,
            Map.of(
                "application",
                "FitTrack",
                "resources",
                List.of("/members", "/workouts", "/challenges")));
        return;
      }
      // This reads the URL to find the record type, record ID and requested calculation.
      String[] parts = path.substring(1).split("/", -1);
      String resource = parts[0];
      if (!Set.of("members", "workouts", "challenges").contains(resource) || parts.length > 3) {
        reply(x, 404, Map.of("error", "Route not found"));
        return;
      }
      Integer id = parts.length >= 2 ? positiveId(parts[1]) : null;
      boolean summary =
          parts.length == 3 && resource.equals("members") && parts[2].equals("summary");
      boolean progress =
          parts.length == 3 && resource.equals("challenges") && parts[2].equals("progress");
      if (parts.length == 3 && !progress && !summary) {
        reply(x, 404, Map.of("error", "Route not found"));
        return;
      }
      String method = x.getRequestMethod();
      String allowed =
          (progress || summary) ? "GET" : id == null ? "GET, POST" : "GET, PUT, DELETE";
      if (!Arrays.asList(allowed.split(", ")).contains(method)) {
        methodNotAllowed(x, allowed);
        return;
      }
      // A member can only read their own records and delete their own challenges.
      if (viewer != 0) {
        if (!method.equals("GET")
            && !(method.equals("DELETE") && resource.equals("challenges") && id != null)) {
          reply(x, 403, Map.of("error", "Only the admin can change these records"));
          return;
        }
      }
      if (summary) {
        if (viewer != 0 && id != viewer) throw new NoSuchElementException("Member not found");
        reply(x, 200, service.summary(id));
        return;
      }
      if (progress) {
        Challenge challenge = service.challenge(id);
        if (viewer != 0 && challenge.getMemberId() != viewer)
          throw new NoSuchElementException("Challenge not found");
        reply(x, 200, challenge.progress(service.workouts()));
        return;
      }
      // This sends requests to the service methods that read, add, change or delete records.
      switch (method) {
        case "GET" -> reply(x, 200, read(resource, id, viewer));
        case "POST", "PUT" -> {
          // This checks the JSON request type, size and record ID before changing data.
          String contentType = x.getRequestHeaders().getFirst("Content-Type");
          if (contentType == null
              || !contentType.split(";", 2)[0].strip().equalsIgnoreCase("application/json")) {
            reply(x, 415, Map.of("error", "Content-Type must be application/json"));
            return;
          }
          byte[] bytes = x.getRequestBody().readNBytes(65537);
          if (bytes.length > 65536) {
            reply(x, 413, Map.of("error", "Request exceeds 64 KiB"));
            return;
          }
          JsonElement parsed =
              gson.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonElement.class);
          if (parsed == null || !parsed.isJsonObject())
            throw new IllegalArgumentException("Body must be a JSON object");
          JsonObject body = parsed.getAsJsonObject();
          int bodyId = integer(body, "id");
          if (id != null && bodyId != id)
            throw new IllegalArgumentException("Path ID must match body ID");
          Object entity = write(resource, body, method.equals("POST"));
          if (method.equals("POST"))
            x.getResponseHeaders().set("Location", "/" + resource + "/" + bodyId);
          reply(x, method.equals("POST") ? 201 : 200, entity);
        }
        case "DELETE" -> {
          switch (resource) {
            case "members" -> {
              service.deleteMember(id);
              accounts.delete(id);
            }
            case "workouts" -> service.deleteWorkout(id);
            case "challenges" -> {
              if (viewer == 0) service.deleteChallenge(id);
              else service.deleteOwnChallenge(id, viewer);
            }
            default -> throw new IllegalStateException();
          }
          x.sendResponseHeaders(204, -1);
        }
        default -> throw new IllegalStateException();
      }
      // This sends a clear response for each error and records unexpected errors in the server log.
    } catch (ConflictException e) {
      reply(x, 409, Map.of("error", e.getMessage()));
    } catch (NoSuchElementException e) {
      reply(x, 404, Map.of("error", e.getMessage()));
    } catch (IllegalArgumentException | JsonParseException e) {
      reply(x, 400, Map.of("error", "Invalid request: " + e.getMessage()));
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Request failed", e);
      reply(x, 500, Map.of("error", "Internal server error"));
    } finally {
      LOG.info(
          x.getRequestMethod() + " " + x.getRequestURI().getPath() + " -> " + x.getResponseCode());
      x.close();
    }
  }

  private boolean authentication(HttpExchange request, String path) throws IOException {
    if (path.equals("/auth/session")) {
      if (!request.getRequestMethod().equals("GET")) methodNotAllowed(request, "GET");
      else {
        boolean signedIn = sessions.signedIn(request);
        reply(
            request,
            signedIn ? 200 : 401,
            Map.of(
                "authEnabled",
                sessions.enabled(),
                "authenticated",
                signedIn,
                "role",
                signedIn ? (sessions.memberId(request) == 0 ? "ADMIN" : "MEMBER") : "NONE",
                "memberId",
                signedIn ? sessions.memberId(request) : 0));
      }
      return true;
    }
    if (path.equals("/auth/login") || path.equals("/auth/logout")) {
      if (!request.getRequestMethod().equals("POST")) methodNotAllowed(request, "POST");
      else if (!safeWrite(request)) reply(request, 403, Map.of("error", "Request blocked"));
      else if (path.equals("/auth/logout")) {
        sessions.end(request);
        request.sendResponseHeaders(204, -1);
      } else login(request);
      return true;
    }

    // The login page needs its own script, styles and icon before the visitor signs in.
    if (Set.of("/login.js", "/styles.css", "/favicon.svg", "/favicon.ico").contains(path))
      return false;
    if (path.equals("/login") || path.equals("/login.html")) {
      if (sessions.signedIn(request)) redirect(request, "/");
      else DashboardPages.serve(request);
      return true;
    }
    boolean signedIn = sessions.signedIn(request);
    if (signedIn && sessions.enabled() && sessions.memberId(request) != 0) {
      try {
        service.member(sessions.memberId(request));
      } catch (NoSuchElementException error) {
        signedIn = false;
      }
    }
    if (!signedIn) {
      if ((path.equals("/") || path.equals("/index.html"))
          && Set.of("GET", "HEAD").contains(request.getRequestMethod()))
        redirect(request, "/login");
      else reply(request, 401, Map.of("error", "Please sign in to continue"));
      return true;
    }
    if (sessions.enabled()
        && !Set.of("GET", "HEAD").contains(request.getRequestMethod())
        && !safeWrite(request)) {
      reply(request, 403, Map.of("error", "Request blocked"));
      return true;
    }
    return false;
  }

  private void login(HttpExchange request) throws IOException {
    if (!sessions.enabled()) {
      reply(request, 200, Map.of("authenticated", true, "authEnabled", false));
      return;
    }
    if (!sessions.allowAttempt(request)) {
      request.getResponseHeaders().set("Retry-After", "300");
      reply(request, 429, Map.of("error", "Too many sign-in attempts. Try again in five minutes."));
      return;
    }
    String type = request.getRequestHeaders().getFirst("Content-Type");
    if (type == null || !type.split(";", 2)[0].strip().equalsIgnoreCase("application/json")) {
      reply(request, 415, Map.of("error", "Content-Type must be application/json"));
      return;
    }
    byte[] bytes = request.getRequestBody().readNBytes(4097);
    if (bytes.length > 4096) {
      reply(request, 413, Map.of("error", "Sign-in request is too large"));
      return;
    }
    JsonElement parsed =
        gson.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonElement.class);
    if (parsed == null || !parsed.isJsonObject())
      throw new IllegalArgumentException("Body must be a JSON object");
    JsonObject body = parsed.getAsJsonObject();
    Integer memberId = sessions.authenticate(string(body, "username"), string(body, "password"));
    if (memberId != null && memberId != 0) {
      try {
        service.member(memberId);
      } catch (NoSuchElementException error) {
        memberId = null;
      }
    }
    if (memberId == null) {
      sessions.failedAttempt(request);
      reply(request, 401, Map.of("error", "Username or password is incorrect"));
      return;
    }
    sessions.start(request, memberId);
    reply(request, 200, Map.of("authenticated", true, "authEnabled", true));
  }

  private void accountRequest(HttpExchange x, String path, int viewer) throws IOException {
    if (viewer != 0 || !sessions.enabled()) {
      reply(x, 403, Map.of("error", "Only a signed-in admin can manage accounts"));
      return;
    }
    String method = x.getRequestMethod();
    if (path.equals("/accounts") && method.equals("GET")) {
      // Password hashes never appear in an API response.
      reply(
          x,
          200,
          accounts.findAll().stream()
              .map(a -> Map.of("memberId", a.memberId(), "username", a.username()))
              .toList());
      return;
    }
    String[] parts = path.split("/", -1);
    if (parts.length != 3 || !parts[1].equals("accounts")) {
      reply(x, 404, Map.of("error", "Route not found"));
      return;
    }
    int id = positiveId(parts[2]);
    service.member(id);
    if (method.equals("DELETE")) {
      if (!accounts.delete(id)) throw new NoSuchElementException("Account not found");
      x.sendResponseHeaders(204, -1);
      return;
    }
    if (!method.equals("PUT")) {
      methodNotAllowed(x, "PUT, DELETE");
      return;
    }
    String type = x.getRequestHeaders().getFirst("Content-Type");
    if (type == null || !type.split(";", 2)[0].strip().equalsIgnoreCase("application/json")) {
      reply(x, 415, Map.of("error", "Content-Type must be application/json"));
      return;
    }
    byte[] bytes = x.getRequestBody().readNBytes(4097);
    if (bytes.length > 4096) {
      reply(x, 413, Map.of("error", "Account request is too large"));
      return;
    }
    JsonElement parsed =
        gson.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonElement.class);
    if (parsed == null || !parsed.isJsonObject())
      throw new IllegalArgumentException("Body must be a JSON object");
    JsonObject body = parsed.getAsJsonObject();
    String username = string(body, "username");
    if (sessions.reservedUsername(username))
      throw new ConflictException("That username is reserved for the admin");
    if (accounts.findAll().stream()
        .anyMatch(a -> a.memberId() != id && a.username().equalsIgnoreCase(username)))
      throw new ConflictException("Username already exists");
    var account = new MemberAccount(id, username, Passwords.hash(string(body, "password")));
    if (accounts.findById(id).isPresent()) accounts.update(account);
    else accounts.create(account);
    reply(x, 200, Map.of("memberId", id, "username", account.username()));
  }

  private boolean safeWrite(HttpExchange request) {
    // Other websites cannot add this header without a successful cross-origin permission check.
    return "true".equals(request.getRequestHeaders().getFirst("X-FitTrack-Request"))
        && !"cross-site".equals(request.getRequestHeaders().getFirst("Sec-Fetch-Site"));
  }

  private void redirect(HttpExchange request, String location) throws IOException {
    request.getResponseHeaders().set("Location", location);
    request.sendResponseHeaders(303, -1);
  }

  private void methodNotAllowed(HttpExchange x, String allowed) throws IOException {
    x.getResponseHeaders().set("Allow", allowed);
    reply(x, 405, Map.of("error", "Method not allowed"));
  }

  private static int positiveId(String value) {
    try {
      int id = Integer.parseInt(value);
      if (id > 0) return id;
    } catch (NumberFormatException ignored) {
    }
    throw new IllegalArgumentException("ID must be a positive integer");
  }

  private static int integer(JsonObject body, String key) {
    JsonElement e = body.get(key);
    if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException(key + " must be an integer");
    try {
      return e.getAsBigDecimal().intValueExact();
    } catch (ArithmeticException | NumberFormatException ex) {
      throw new IllegalArgumentException(key + " must be an integer");
    }
  }

  private static String string(JsonObject body, String key) {
    JsonElement e = body.get(key);
    if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException(key + " must be a string");
    return e.getAsString();
  }

  private Object read(String resource, Integer id, int viewer) {
    if (id == null) {
      return switch (resource) {
        case "members" ->
            service.members().stream().filter(m -> viewer == 0 || m.getId() == viewer).toList();
        case "workouts" ->
            service.workouts().stream()
                .filter(w -> viewer == 0 || w.getMemberId() == viewer)
                .toList();
        case "challenges" ->
            service.challenges().stream()
                .filter(c -> viewer == 0 || c.getMemberId() == viewer)
                .map(this::challengeView)
                .toList();
        default -> throw new IllegalStateException();
      };
    }
    // The checked object is also the returned object, even if another request updates the database.
    return switch (resource) {
      case "members" -> {
        Member member = service.member(id);
        requireOwner(viewer, member.getId());
        yield member;
      }
      case "workouts" -> {
        Workout workout = service.workout(id);
        requireOwner(viewer, workout.getMemberId());
        yield workout;
      }
      case "challenges" -> {
        Challenge challenge = service.challenge(id);
        requireOwner(viewer, challenge.getMemberId());
        yield challengeView(challenge);
      }
      default -> throw new IllegalStateException();
    };
  }

  private void requireOwner(int viewer, int owner) {
    if (viewer != 0 && viewer != owner) throw new NoSuchElementException("Record not found");
  }

  private Map<String, Object> challengeView(Challenge c) {
    return Map.of(
        "id",
        c.getId(),
        "memberId",
        c.getMemberId(),
        "name",
        c.getName(),
        "type",
        c.getType(),
        "target",
        c.getTarget(),
        "startDate",
        c.getStartDate(),
        "endDate",
        c.getEndDate());
  }

  private Object write(String resource, JsonObject b, boolean create) {
    int id = integer(b, "id");
    switch (resource) {
      case "members":
        {
          Member m = new Member(id, string(b, "name"), string(b, "email"), integer(b, "age"));
          if (create) service.createMember(m);
          else service.updateMember(m);
          return m;
        }
      case "workouts":
        {
          Workout w =
              new Workout(
                  id,
                  integer(b, "memberId"),
                  string(b, "activityType"),
                  integer(b, "durationMinutes"),
                  string(b, "date"));
          if (create) service.createWorkout(w);
          else service.updateWorkout(w);
          return w;
        }
      case "challenges":
        {
          int owner = integer(b, "memberId"), target = integer(b, "target");
          String name = string(b, "name"),
              start = string(b, "startDate"),
              end = string(b, "endDate");
          Challenge c =
              switch (string(b, "type")) {
                case "MINUTES" -> new MinutesChallenge(id, owner, name, target, start, end);
                case "SESSIONS" -> new SessionsChallenge(id, owner, name, target, start, end);
                default ->
                    throw new IllegalArgumentException(
                        "Challenge type must be MINUTES or SESSIONS");
              };
          if (create) service.createChallenge(c);
          else service.updateChallenge(c);
          return challengeView(c);
        }
      default:
        throw new IllegalStateException();
    }
  }

  private void reply(HttpExchange x, int status, Object value) throws IOException {
    // This converts record IDs to the common id field used in API responses.
    JsonElement json = gson.toJsonTree(value);
    normaliseIds(json);
    byte[] bytes = gson.toJson(json).getBytes(StandardCharsets.UTF_8);
    x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    x.sendResponseHeaders(status, bytes.length);
    x.getResponseBody().write(bytes);
  }

  private void normaliseIds(JsonElement e) {
    if (e.isJsonArray()) {
      e.getAsJsonArray().forEach(this::normaliseIds);
      return;
    }
    if (e.isJsonObject()) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("workoutId")) o.add("id", o.remove("workoutId"));
      else if (o.has("memberId") && o.has("email")) o.add("id", o.remove("memberId"));
    }
  }
}
