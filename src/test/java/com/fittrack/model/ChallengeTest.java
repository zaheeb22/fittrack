package com.fittrack.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChallengeTest {
  private final List<Workout> workouts =
      List.of(
          new Workout(1, 1, "Run", 30, "2026-01-01"), new Workout(2, 1, "Walk", 45, "2026-01-31"),
          new Workout(3, 2, "Run", 200, "2026-01-15"), new Workout(4, 1, "Run", 90, "2025-12-31"));

  @Test
  void dispatchesDifferentScoringRulesWithInclusiveDatesAndOwnerFiltering() {
    Challenge minutes = new MinutesChallenge(1, 1, "Goal", 60, "2026-01-01", "2026-01-31");
    Challenge sessions = new SessionsChallenge(2, 1, "Goal", 3, "2026-01-01", "2026-01-31");
    assertEquals(75, minutes.score(workouts));
    assertEquals(2, sessions.score(workouts));
    assertTrue(minutes.progress(workouts).completed());
    assertEquals(100, minutes.progress(workouts).percentage());
    assertFalse(sessions.progress(workouts).completed());
    assertEquals(200.0 / 3, sessions.progress(workouts).percentage(), 0.001);
  }

  @Test
  void emptyProgressAndInvalidGoals() {
    assertEquals(
        0, new MinutesChallenge(1, 1, "Goal", 10, "2026-01-01", "2026-01-31").score(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MinutesChallenge(1, 1, "Goal", 0, "2026-01-01", "2026-01-31"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SessionsChallenge(1, 1, "Goal", 1, "2026-02-01", "2026-01-31"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SessionsChallenge(1, 1, "Goal", 1, "bad", "2026-01-31"));
  }

  @Test
  void rejectsInvalidModelInputs() {
    assertThrows(IllegalArgumentException.class, () -> new Member(0, "A", "a@b.com", 20));
    assertThrows(IllegalArgumentException.class, () -> new Member(1, " ", "a@b.com", 20));
    assertThrows(IllegalArgumentException.class, () -> new Member(1, "A", "a@", 20));
    assertThrows(IllegalArgumentException.class, () -> new Member(1, "A", "a@b.com", 0));
    assertThrows(IllegalArgumentException.class, () -> new Workout(1, 1, "Run", 20, "2026-02-30"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Workout(1, 1, "Run", 20, java.time.LocalDate.now().plusDays(1).toString()));
    assertThrows(IllegalArgumentException.class, () -> new Workout(1, 0, "Run", 20, "2026-01-01"));
  }
}
