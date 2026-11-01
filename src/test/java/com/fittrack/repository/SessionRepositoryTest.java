package com.fittrack.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionRepositoryTest {
  @Test
  void memorySessionsExpireAndCanBeDeleted() {
    var sessions = new MemorySessionRepository();
    sessions.save("first", 200, 100);
    assertTrue(sessions.isActive("first", 199));
    assertFalse(sessions.isActive("first", 200));
    sessions.save("second", 300, 200);
    assertFalse(sessions.isActive("first", 200));
    sessions.delete("second");
    assertFalse(sessions.isActive("second", 201));
  }

  @Test
  void databaseSessionsAreSharedAndSurviveRepositoryRecreation() throws Exception {
    String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    ConnectionFactory connections = () -> DriverManager.getConnection(url);
    var first = new JdbcSessionRepository(connections);
    first.initialise();
    first.save("a".repeat(64), 200, 100);
    var second = new JdbcSessionRepository(connections);
    second.initialise();
    assertTrue(second.isActive("a".repeat(64), 199));
    assertFalse(second.isActive("a".repeat(64), 200));
    second.delete("a".repeat(64));
    assertFalse(first.isActive("a".repeat(64), 101));
  }

  @Test
  void databaseFailuresAreReported() {
    var sessions = new JdbcSessionRepository(() -> { throw new SQLException("offline", "08001"); });
    assertThrows(StorageException.class, sessions::initialise);
    assertThrows(StorageException.class, () -> sessions.save("x", 200, 100));
    assertThrows(StorageException.class, () -> sessions.isActive("x", 100));
    assertThrows(StorageException.class, () -> sessions.delete("x"));
  }
}
