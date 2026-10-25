package com.fittrack.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fittrack.model.*;
import com.fittrack.repository.*;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class FitnessServiceTest {
  private final FitnessService s =
      new FitnessService(
          new InMemoryRepository<>(), new InMemoryRepository<>(), new InMemoryRepository<>());

  @Test
  void fullCrudAndRelationshipProtection() {
    s.createMember(new Member(1, "Alex", "a@b.com", 25));
    s.createWorkout(new Workout(1, 1, "Run", 30, "2026-01-01"));
    s.createChallenge(new MinutesChallenge(1, 1, "Goal", 60, "2026-01-01", "2026-01-31"));
    assertEquals(50, s.progress(1).percentage());
    assertEquals(new FitnessService.Summary(1, 30, 30), s.summary(1));
    assertThrows(ConflictException.class, () -> s.deleteMember(1));
    s.updateWorkout(new Workout(1, 1, "Run", 60, "2026-01-01"));
    assertTrue(s.progress(1).completed());
    s.updateChallenge(new SessionsChallenge(1, 1, "Sessions", 2, "2026-01-01", "2026-01-31"));
    assertEquals(1, s.progress(1).achieved());
    s.updateMember(new Member(1, "Alex Updated", "a@b.com", 26));
    assertEquals(26, s.member(1).getAge());
    s.deleteWorkout(1);
    s.deleteChallenge(1);
    s.deleteMember(1);
    assertTrue(s.members().isEmpty());
    assertTrue(s.workouts().isEmpty());
    assertTrue(s.challenges().isEmpty());
  }

  @Test
  void rejectsDuplicatesAndMissingEntities() {
    s.createMember(new Member(1, "Alex", "a@b.com", 25));
    assertThrows(
        ConflictException.class, () -> s.createMember(new Member(1, "Alex", "a@b.com", 25)));
    assertThrows(
        ConflictException.class, () -> s.createMember(new Member(2, "Other", "A@B.COM", 25)));
    assertThrows(
        NoSuchElementException.class,
        () -> s.createWorkout(new Workout(1, 2, "Run", 30, "2026-01-01")));
    assertThrows(
        NoSuchElementException.class,
        () -> s.updateWorkout(new Workout(9, 1, "Run", 30, "2026-01-01")));
    assertThrows(NoSuchElementException.class, () -> s.deleteChallenge(99));
    assertThrows(NoSuchElementException.class, () -> s.progress(99));
  }

  @Test
  void snapshotsCannotMutateStorage() {
    s.createMember(new Member(1, "Alex", "a@b.com", 25));
    var snapshot = s.members();
    assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    s.createMember(new Member(2, "Other", "other@b.com", 20));
    assertEquals(1, snapshot.size());
  }
}
