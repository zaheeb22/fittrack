package com.fittrack.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.fittrack.model.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;

class JdbcRepositoryTest {
  private ConnectionFactory cf;

  @BeforeEach
  void schema() throws Exception {
    String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    cf = () -> DriverManager.getConnection(url);
    try (var c = cf.open();
        var s = c.createStatement()) {
      s.execute(
          "CREATE TABLE members(id INT PRIMARY KEY,name VARCHAR(200),email VARCHAR(254) UNIQUE,age"
              + " INT)");
      s.execute(
          "CREATE TABLE workouts(id INT PRIMARY KEY,member_id INT REFERENCES"
              + " members(id),activity_type VARCHAR(200),duration_minutes INT,workout_date DATE)");
      s.execute(
          "CREATE TABLE challenges(id INT PRIMARY KEY,member_id INT REFERENCES members(id),name"
              + " VARCHAR(200),type VARCHAR(20),target INT,start_date DATE,end_date DATE)");
    }
  }

  @Test
  void memberCrudAndPreparedStatementSafety() {
    var r = JdbcRepository.members(cf);
    String input = "Alex'); DROP TABLE members; --";
    r.create(new Member(1, input, "alex@example.com", 25));
    assertEquals(input, r.findById(1).orElseThrow().getName());
    assertThrows(
        ConflictException.class, () -> r.create(new Member(1, "Other", "other@example.com", 20)));
    assertTrue(r.update(new Member(1, "Updated", "alex@example.com", 26)));
    assertEquals(26, r.findAll().getFirst().getAge());
    assertFalse(r.update(new Member(99, "Absent", "absent@example.com", 20)));
    assertTrue(r.delete(1));
    assertFalse(r.delete(1));
    assertTrue(r.findById(1).isEmpty());
  }

  @Test
  void workoutAndChallengeCrudRoundTripsAndForeignKeys() {
    var m = JdbcRepository.members(cf);
    m.create(new Member(1, "Alex", "alex@example.com", 25));
    var w = JdbcRepository.workouts(cf);
    var c = JdbcRepository.challenges(cf);
    assertThrows(
        ConflictException.class, () -> w.create(new Workout(1, 99, "Run", 30, "2026-01-01")));
    w.create(new Workout(1, 1, "Run", 30, "2026-01-01"));
    assertEquals("2026-01-01", w.findById(1).orElseThrow().getDate());
    c.create(new MinutesChallenge(1, 1, "Goal", 60, "2026-01-01", "2026-01-31"));
    assertInstanceOf(MinutesChallenge.class, c.findById(1).orElseThrow());
    c.update(new SessionsChallenge(1, 1, "Goal", 2, "2026-01-01", "2026-01-31"));
    assertInstanceOf(SessionsChallenge.class, c.findAll().getFirst());
    w.update(new Workout(1, 1, "Walk", 45, "2026-01-02"));
    assertEquals(45, w.findAll().getFirst().getDurationMinutes());
    assertThrows(ConflictException.class, () -> m.delete(1));
    assertTrue(w.delete(1));
    assertTrue(c.delete(1));
    assertTrue(m.delete(1));
  }

  @Test
  void connectionFailureIsWrapped() {
    var r =
        JdbcRepository.members(
            () -> {
              throw new SQLException("unavailable", "08001");
            });
    assertThrows(StorageException.class, r::findAll);
  }
}
