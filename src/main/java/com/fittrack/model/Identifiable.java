package com.fittrack.model;

/** A shared interface for records that have a positive ID. */
public interface Identifiable {
  /**
   * Returns the record ID used to find it in storage.
   *
   * @return positive entity ID
   */
  int getId();
}
