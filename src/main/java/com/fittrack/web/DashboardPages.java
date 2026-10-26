package com.fittrack.web;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Sends the dashboard files from inside the application JAR to the browser. */
final class DashboardPages {
  // Only these known files can be requested. A URL cannot read other local files.
  private static final Map<String, String> FILES =
      Map.of(
          "/",
          "index.html",
          "/index.html",
          "index.html",
          "/app.js",
          "app.js",
          "/styles.css",
          "styles.css",
          "/favicon.svg",
          "favicon.svg");

  private DashboardPages() {}

  /** Returns true when the request is for a dashboard file and has been handled. */
  static boolean serve(HttpExchange request) throws IOException {
    String fileName = FILES.get(request.getRequestURI().getPath());
    if (fileName == null) return false;

    String method = request.getRequestMethod();
    if (!method.equals("GET") && !method.equals("HEAD")) {
      byte[] message = "{\"error\":\"Method not allowed\"}".getBytes(StandardCharsets.UTF_8);
      request.getResponseHeaders().set("Allow", "GET, HEAD");
      request.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      request.sendResponseHeaders(405, message.length);
      request.getResponseBody().write(message);
      return true;
    }

    // Class resources work both in Eclipse and in the packaged JAR.
    try (var file = DashboardPages.class.getResourceAsStream("/web/" + fileName)) {
      if (file == null) throw new IOException("Dashboard file is missing: " + fileName);
      byte[] content = file.readAllBytes();
      String contentType =
          fileName.endsWith(".css")
              ? "text/css; charset=utf-8"
              : fileName.endsWith(".js")
                  ? "text/javascript; charset=utf-8"
                  : fileName.endsWith(".svg") ? "image/svg+xml" : "text/html; charset=utf-8";
      request.getResponseHeaders().set("Content-Type", contentType);
      request.getResponseHeaders().set("Cache-Control", "no-cache");
      request.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
      request
          .getResponseHeaders()
          .set(
              "Content-Security-Policy",
              "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src"
                  + " 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none';"
                  + " form-action 'self'");
      if (method.equals("HEAD")) {
        request.getResponseHeaders().set("Content-Length", Integer.toString(content.length));
        request.sendResponseHeaders(200, -1);
      } else {
        request.sendResponseHeaders(200, content.length);
        request.getResponseBody().write(content);
      }
      return true;
    }
  }
}
