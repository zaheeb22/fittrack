package com.fittrack.model;

import java.time.LocalDate;
import java.util.List;

/** A member's goal between two dates, including both dates. Each subclass supplies its own scoring rule. */
public abstract class Challenge implements Identifiable {
  private final int id;
  private final int memberId;
  private final String name;
  private final int target;
  private final String startDate;
  private final String endDate;

  /**
   * Constructs a validated goal.
   *
   * @param id positive ID
   * @param memberId owner ID
   * @param name descriptive goal name
   * @param target positive goal
   * @param startDate first date that counts, in YYYY-MM-DD format
   * @param endDate last date that counts, in YYYY-MM-DD format
   */
  protected Challenge(
      int id, int memberId, String name, int target, String startDate, String endDate) {
    // This checks the goal details and makes sure its end date is not before its start date.
    if (id <= 0 || memberId <= 0 || target <= 0)
      throw new IllegalArgumentException("IDs and target must be positive");
    if (name == null || name.isBlank() || name.length() > 200)
      throw new IllegalArgumentException("Challenge name must contain 1 to 200 characters");
    try {
      if (LocalDate.parse(endDate).isBefore(LocalDate.parse(startDate)))
        throw new IllegalArgumentException("End date precedes start date");
    } catch (java.time.format.DateTimeParseException | NullPointerException e) {
      throw new IllegalArgumentException("Dates must use YYYY-MM-DD");
    }
    this.id = id;
    this.memberId = memberId;
    this.name = name.strip();
    this.target = target;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  /** {@inheritDoc} */
  @Override
  public int getId() {
    return id;
  }

  /**
   * Returns goal owner.
   *
   * @return member ID
   */
  public int getMemberId() {
    return memberId;
  }

  /**
   * Returns goal name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the number of minutes or sessions needed to reach the goal.
   *
   * @return target
   */
  public int getTarget() {
    return target;
  }

  /**
   * Returns first included date.
   *
   * @return date in YYYY-MM-DD format
   */
  public String getStartDate() {
    return startDate;
  }

  /**
   * Returns last included date.
   *
   * @return date in YYYY-MM-DD format
   */
  public String getEndDate() {
    return endDate;
  }

  /**
   * Returns the goal type saved in the database.
   *
   * @return MINUTES or SESSIONS
   */
  public abstract String getType();

  /**
   * Counts minutes or sessions using the rule for this goal type.
   *
   * @param workouts candidate workouts
   * @return achieved units
   */
  public abstract long score(List<Workout> workouts);

  /**
   * Selects this member's workouts between the goal's start and end dates.
   *
   * @param workouts all candidate workouts
   * @return a list of matching workouts that cannot be changed
   */
  protected List<Workout> qualifying(List<Workout> workouts) {
    return workouts.stream()
        .filter(
            w ->
                w.getMemberId() == memberId
                    && w.getDate().compareTo(startDate) >= 0
                    && w.getDate().compareTo(endDate) <= 0)
        .toList();
  }

  /**
   * Calculates progress by calling the scoring method supplied by the goal subclass.
   *
   * @param workouts candidate workouts
   * @return the calculated goal progress
   */
  public Progress progress(List<Workout> workouts) {
    long achieved = score(workouts);
    return new Progress(
        achieved, target, Math.min(100.0, 100.0 * achieved / target), achieved >= target);
  }

  /**
   * Stores the calculated goal progress in values that cannot be changed.
   *
   * @param achieved minutes or sessions completed
   * @param target minutes or sessions needed to reach the goal
   * @param percentage progress percentage, limited to 100
   * @param completed whether the goal has been reached
   */
  public record Progress(long achieved, int target, double percentage, boolean completed) {}
}
