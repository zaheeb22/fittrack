package com.fittrack.model;

import java.util.List;

/** A challenge measured in exercise minutes. */
public final class MinutesChallenge extends Challenge {
  /**
   * Creates a goal.
   *
   * @param id positive ID
   * @param memberId owner ID
   * @param name goal name
   * @param target target number greater than zero
   * @param startDate first date that counts, in YYYY-MM-DD format
   * @param endDate last date that counts, in YYYY-MM-DD format
   */
  public MinutesChallenge(
      int id, int memberId, String name, int target, String startDate, String endDate) {
    super(id, memberId, name, target, startDate, endDate);
  }

  /** {@inheritDoc} */
  @Override
  public String getType() {
    return "MINUTES";
  }

  /** {@inheritDoc} */
  @Override
  public long score(List<Workout> workouts) {
    return qualifying(workouts).stream().mapToLong(Workout::getDurationMinutes).sum();
  }
}
