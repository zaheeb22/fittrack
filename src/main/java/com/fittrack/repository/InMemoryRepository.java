package com.fittrack.repository;

import com.fittrack.model.Identifiable;
import java.util.*;

/**
 * Thread-safe volatile storage for demonstrations and isolated unit tests.
 *
 * @param <T> type of record being stored
 */
public final class InMemoryRepository<T extends Identifiable> implements Repository<T> {
  private final Map<Integer, T> entities = new TreeMap<>();

  /** Creates an empty repository. */
  public InMemoryRepository() {}

  /** {@inheritDoc} */
  @Override
  public synchronized List<T> findAll() {
    return List.copyOf(entities.values());
  }

  /** {@inheritDoc} */
  @Override
  public synchronized Optional<T> findById(int id) {
    return Optional.ofNullable(entities.get(id));
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void create(T entity) {
    Objects.requireNonNull(entity);
    if (entities.putIfAbsent(entity.getId(), entity) != null)
      throw new ConflictException("ID already exists");
  }

  /** {@inheritDoc} */
  @Override
  public synchronized boolean update(T entity) {
    return entities.replace(entity.getId(), entity) != null;
  }

  /** {@inheritDoc} */
  @Override
  public synchronized boolean delete(int id) {
    return entities.remove(id) != null;
  }
}
