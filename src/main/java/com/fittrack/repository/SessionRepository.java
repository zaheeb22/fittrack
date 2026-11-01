package com.fittrack.repository;

/** Stores signed-in sessions so the server can check and end them. */
public interface SessionRepository {
  /**
   * Saves a session and removes old sessions.
   *
   * @param tokenHash hash of the random session code
   * @param expiresAt time when the session ends, in seconds since 1970
   * @param now current time in seconds since 1970
   */
  void save(String tokenHash, long expiresAt, long now);

  /**
   * Checks whether a session is saved and has not expired.
   *
   * @param tokenHash hash to find
   * @param now current time in seconds since 1970
   * @return true when the session can still be used
   */
  boolean isActive(String tokenHash, long now);

  /**
   * Ends a session immediately.
   *
   * @param tokenHash hash of the session to remove
   */
  void delete(String tokenHash);
}
