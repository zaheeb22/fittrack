package com.fittrack.service;

import com.fittrack.model.Workout;
import java.util.ArrayList;
import java.util.List;

/** Manages workout records and performs fitness-related calculations. */
public class WorkoutService {

  private final List<Workout> workouts;

  /** Creates an empty workout collection. */
  public WorkoutService() {
    workouts = new ArrayList<>();
  }

  /**
   * Adds a workout to the collection.
   *
   * @param workout workout to add
   */
  public void addWorkout(Workout workout) {
    if (workout == null) {
      throw new IllegalArgumentException("Workout cannot be null");
    }

    for (Workout existing : workouts) {
      if (existing.getWorkoutId() == workout.getWorkoutId()) {
        throw new IllegalArgumentException("Workout ID already exists");
      }
    }

    workouts.add(workout);
  }

  /**
   * Calculates total workout duration.
   *
   * @return total minutes exercised
   */
  public long calculateTotalMinutes() {
    long total = 0;

    for (Workout workout : workouts) {
      total += workout.getDurationMinutes();
    }

    return total;
  }

  /**
   * Finds the longest workout.
   *
   * @return longest workout, or null if empty
   */
  public Workout findLongestWorkout() {
    Workout longest = null;

    for (Workout workout : workouts) {
      if (longest == null || workout.getDurationMinutes() > longest.getDurationMinutes()) {

        longest = workout;
      }
    }

    return longest;
  }

  /**
   * Returns the number of workouts.
   *
   * @return workout count
   */
  public int getWorkoutCount() {
    return workouts.size();
  }

  /**
   * Returns a separate workout list that cannot be changed.
   *
   * @return list of workouts
   */
  public List<Workout> getWorkouts() {
    return List.copyOf(workouts);
  }
}
