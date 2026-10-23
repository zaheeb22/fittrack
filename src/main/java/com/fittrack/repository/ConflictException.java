package com.fittrack.repository;

/** Reports a duplicate ID or a conflict with a linked record. */
public final class ConflictException extends RuntimeException {
  /**
   * Creates a conflict.
   *
   * @param message safe explanation
   */
  public ConflictException(String message) {
    super(message);
  }
}
