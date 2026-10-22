package com.fittrack.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fittrack.model.Workout;
import org.junit.jupiter.api.Test;

/** Unit tests for WorkoutService business logic. */
class WorkoutServiceTest {

  @Test
  void shouldCalculateTotalWorkoutMinutes() {
    WorkoutService service = new WorkoutService();

    service.addWorkout(new Workout(1, "Running", 30));
    service.addWorkout(new Workout(2, "Cycling", 45));
    service.addWorkout(new Workout(3, "Walking", 60));

    assertEquals(135, service.calculateTotalMinutes());
  }

  @Test
  void shouldCountWorkouts() {
    WorkoutService service = new WorkoutService();

    service.addWorkout(new Workout(1, "Running", 30));
    service.addWorkout(new Workout(2, "Cycling", 45));

    assertEquals(2, service.getWorkoutCount());
  }

  @Test
  void shouldFindLongestWorkout() {
    WorkoutService service = new WorkoutService();

    service.addWorkout(new Workout(1, "Running", 30));
    service.addWorkout(new Workout(2, "Walking", 60));

    assertEquals("Walking", service.findLongestWorkout().getActivityType());
  }

  @Test
  void shouldRejectNegativeDuration() {
    assertThrows(IllegalArgumentException.class, () -> new Workout(1, "Running", -10));
  }

  @Test
  void shouldRejectDuplicateWorkoutId() {
    WorkoutService service = new WorkoutService();

    service.addWorkout(new Workout(1, "Running", 30));

    assertThrows(
        IllegalArgumentException.class, () -> service.addWorkout(new Workout(1, "Cycling", 45)));
  }

  @Test
  void shouldReturnZeroForEmptyWorkouts() {
    WorkoutService service = new WorkoutService();

    assertEquals(0, service.calculateTotalMinutes());
    assertNull(service.findLongestWorkout());
  }
}
