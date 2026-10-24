package com.fittrack.repository;

/** Reports a database error without sending sign-in details in the API response. */
public final class StorageException extends RuntimeException {
  /**
   * Keeps the original error so it can be recorded in the server log.
   *
   * @param cause original database error
   */
  public StorageException(Throwable cause) {
    super("Database operation failed", cause);
  }
}
