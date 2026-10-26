package com.fittrack.web;

import java.util.Map;

/** Holds the server address and optional sign-in details without printing passwords. */
public final class ServerSettings {
  private final String host;
  private final int port;
  private final String username;
  private final String password;

  /**
   * Checks the settings before the server opens a network connection.
   *
   * @param host address to listen on
   * @param port port number, or zero for tests
   * @param username sign-in name, or empty for local use
   * @param password sign-in password, or empty for local use
   */
  public ServerSettings(String host, int port, String username, String password) {
    if (host == null || host.isBlank())
      throw new IllegalArgumentException("Server address is required");
    if (port < 0 || port > 65535)
      throw new IllegalArgumentException("Port must be between 0 and 65535");
    this.host = host;
    this.port = port;
    this.username = username == null ? "" : username;
    this.password = password == null ? "" : password;
    // This checks the sign-in details before allowing access from other computers.
    if (this.username.isBlank() != this.password.isBlank())
      throw new IllegalArgumentException(
          "Set both FITTRACK_ADMIN_USER and FITTRACK_ADMIN_PASSWORD");
    if (this.username.contains(":"))
      throw new IllegalArgumentException("Sign-in name cannot contain a colon");
    if (!this.password.isEmpty() && this.password.length() < 16)
      throw new IllegalArgumentException("Use a sign-in password of at least 16 characters");
    boolean localOnly = host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1");
    if (!localOnly && !hasSignIn())
      throw new IllegalArgumentException(
          "Set sign-in details before allowing access from other computers");
  }

  /**
   * Reads settings supplied by a terminal or hosting provider.
   *
   * @param environment environment variables
   * @return checked settings
   */
  public static ServerSettings fromEnvironment(Map<String, String> environment) {
    String port =
        environment.getOrDefault("FITTRACK_PORT", environment.getOrDefault("PORT", "8080"));
    try {
      return new ServerSettings(
          environment.getOrDefault("FITTRACK_BIND_HOST", "127.0.0.1"),
          Integer.parseInt(port),
          environment.getOrDefault("FITTRACK_ADMIN_USER", ""),
          environment.getOrDefault("FITTRACK_ADMIN_PASSWORD", ""));
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("The server port must be a whole number");
    }
  }

  /**
   * Returns the address to listen on.
   *
   * @return host address
   */
  public String getHost() {
    return host;
  }

  /**
   * Returns the port.
   *
   * @return port number
   */
  public int getPort() {
    return port;
  }

  /**
   * Returns whether sign-in is enabled.
   *
   * @return true if credentials are set
   */
  public boolean hasSignIn() {
    return !username.isBlank() && !password.isBlank();
  }

  // These methods let other classes in this package check the sign-in details.
  String username() {
    return username;
  }

  String password() {
    return password;
  }
}
