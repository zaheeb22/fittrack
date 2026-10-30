package com.fittrack.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ServerSettingsTest {
  @Test
  void localDefaultsDoNotRequireSignIn() {
    var settings = ServerSettings.fromEnvironment(Map.of());
    assertEquals("127.0.0.1", settings.getHost());
    assertEquals(8080, settings.getPort());
    assertFalse(settings.hasSignIn());
  }

  @Test
  void readsHostingPortAndAllowsAnExplicitOverride() {
    assertEquals(9000, ServerSettings.fromEnvironment(Map.of("PORT", "9000")).getPort());
    assertEquals(
        8081,
        ServerSettings.fromEnvironment(Map.of("PORT", "9000", "FITTRACK_PORT", "8081")).getPort());
  }

  @Test
  void rejectsPublicAccessWithoutValidSignInDetails() {
    assertThrows(IllegalArgumentException.class, () -> new ServerSettings("0.0.0.0", 8080, "", ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ServerSettings("0.0.0.0", 8080, "owner", "short"));
    assertThrows(
        IllegalArgumentException.class, () -> new ServerSettings("127.0.0.1", 8080, "owner", ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ServerSettings("127.0.0.1", 8080, "a:b", "a-long-test-password"));
    assertTrue(new ServerSettings("0.0.0.0", 8080, "owner", "a-long-test-password").hasSignIn());
  }

  @Test
  void rejectsInvalidPorts() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ServerSettings.fromEnvironment(Map.of("PORT", "abc")));
    assertThrows(
        IllegalArgumentException.class,
        () -> ServerSettings.fromEnvironment(Map.of("PORT", "65536")));
  }
}
