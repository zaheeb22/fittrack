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
    this.service = Objects.requireNonNull(service);
    server = HttpServer.create(new InetSocketAddress(settings.getHost(), settings.getPort()), 0);
    var pagesAndApi = server.createContext("/", this::handle);
    if (settings.hasSignIn()) {
      // The browser shows its standard username and password box.
      pagesAndApi.setAuthenticator(
          new BasicAuthenticator("FitTrack", StandardCharsets.UTF_8) {
            @Override
            public boolean checkCredentials(String username, String password) {
              boolean userMatches = sameText(username, settings.username());
              boolean passwordMatches = sameText(password, settings.password());
              return userMatches & passwordMatches;
            }
          });
    }
    // Hosting health checks can read this small status response without a password.
    // No member, workout or challenge data is available here.
    server.createContext("/health", this::health);
    server.setExecutor(executor);
  }

  private static boolean sameText(String supplied, String expected) {
    return java.security.MessageDigest.isEqual(
        supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
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
      String path = x.getRequestURI().getPath();
      if (DashboardPages.serve(x)) return;
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
      if (summary) {
        reply(x, 200, service.summary(id));
        return;
      }
      if (progress) {
        reply(x, 200, service.progress(id));
        return;
      }
      // This sends requests to the service methods that read, add, change or delete records.
      switch (method) {
        case "GET" -> reply(x, 200, read(resource, id));
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
            case "members" -> service.deleteMember(id);
            case "workouts" -> service.deleteWorkout(id);
            case "challenges" -> service.deleteChallenge(id);
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

  private Object read(String resource, Integer id) {
    return switch (resource) {
      case "members" -> id == null ? service.members() : service.member(id);
      case "workouts" -> id == null ? service.workouts() : service.workout(id);
      case "challenges" ->
          id == null
              ? service.challenges().stream().map(this::challengeView).toList()
              : challengeView(service.challenge(id));
      default -> throw new IllegalStateException();
    };
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
