package com.fittrack.repository;

import java.util.HashMap;
import java.util.Map;

/** Keeps local demonstration sessions in memory until the app stops. */
public final class MemorySessionRepository implements SessionRepository {
  private final Map<String, Long> sessions = new HashMap<>();

  /** Creates empty session storage. */
  public MemorySessionRepository() {}

  /** {@inheritDoc} */
  @Override
  public synchronized void save(String tokenHash, long expiresAt, long now) {
    sessions.values().removeIf(expiry -> expiry <= now);
    sessions.put(tokenHash, expiresAt);
  }

  /** {@inheritDoc} */
  @Override
  public synchronized boolean isActive(String tokenHash, long now) {
    Long expiry = sessions.get(tokenHash);
    return expiry != null && expiry > now;
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void delete(String tokenHash) {
    sessions.remove(tokenHash);
  }
}
