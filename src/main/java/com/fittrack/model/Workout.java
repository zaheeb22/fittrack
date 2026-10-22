package com.fittrack.model;

/**
 * Represents a workout completed by a fitness member.
 *
 * <p>A workout records an activity type and its duration.
 */
public final class Workout implements Identifiable {

  // These fields link the workout to a member and store its activity, date and duration.
  private final int memberId;
  private final String date;
  private final int workoutId;
  private final String activityType;
  private final int durationMinutes;

  /**
   * Creates a workout after checking its details.
   *
   * @param workoutId unique workout ID
   * @param activityType type of fitness activity
   * @param durationMinutes duration in minutes
   * @throws IllegalArgumentException if any value is invalid
   */
  public Workout(int workoutId, String activityType, int durationMinutes) {

    this(workoutId, 1, activityType, durationMinutes, java.time.LocalDate.now().toString());
  }

  /**
   * Creates a workout with a date and a link to one member.
   *
   * @param workoutId positive workout ID
   * @param memberId positive owner ID
   * @param activityType activity name
   * @param durationMinutes positive duration
   * @param date date in YYYY-MM-DD format, no later than today
   */
  public Workout(
      int workoutId, int memberId, String activityType, int durationMinutes, String date) {
    // This checks the owner and makes sure the workout date is valid and not in the future.
    if (memberId <= 0) throw new IllegalArgumentException("Member ID must be positive");
    try {
      if (java.time.LocalDate.parse(date).isAfter(java.time.LocalDate.now()))
        throw new IllegalArgumentException("Workout date cannot be in the future");
    } catch (java.time.format.DateTimeParseException | NullPointerException e) {
      throw new IllegalArgumentException("Date must use YYYY-MM-DD");
    }
    this.memberId = memberId;
    this.date = date;
    // This checks that the workout has a positive ID, an activity name and a positive duration.
    if (workoutId <= 0) {
      throw new IllegalArgumentException("Workout ID must be positive");
    }

    if (activityType == null || activityType.isBlank() || activityType.length() > 200) {
      throw new IllegalArgumentException("Activity type must contain 1 to 200 characters");
    }

    if (durationMinutes <= 0) {
      throw new IllegalArgumentException("Workout duration must be positive");
    }

    this.workoutId = workoutId;
    this.activityType = activityType.strip();
    this.durationMinutes = durationMinutes;
  }

  /** {@inheritDoc} */
  @Override
  public int getId() {
    return workoutId;
  }

  /**
   * Returns the associated member.
   *
   * @return member ID
   */
  public int getMemberId() {
    return memberId;
  }

  /**
   * Returns the completion date.
   *
   * @return date in YYYY-MM-DD format
   */
  public String getDate() {
    return date;
  }

  /**
   * Returns the unique workout ID.
   *
   * @return unique workout ID
   */
  public int getWorkoutId() {
    return workoutId;
  }

  /**
   * Returns the activity name.
   *
   * @return workout activity type
   */
  public String getActivityType() {
    return activityType;
  }

  /**
   * Returns the exercise duration.
   *
   * @return workout duration in minutes
   */
  public int getDurationMinutes() {
    return durationMinutes;
  }

  /** Displays workout information. */
  public void displayWorkout() {
    System.out.println(
        "Workout #"
            + workoutId
            + " | Activity: "
            + activityType
            + " | Duration: "
            + durationMinutes
            + " minutes");
  }
}
