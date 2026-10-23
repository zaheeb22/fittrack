package com.fittrack.repository;

import com.fittrack.model.Identifiable;
import java.util.List;
import java.util.Optional;

/**
 * Shared methods for adding, reading, changing and deleting records in memory or a database (CRUD).
 *
 * @param <T> type of record being stored
 */
public interface Repository<T extends Identifiable> {
  /**
   * Returns a separate list of records sorted by ID.
   *
   * @return saved records
   */
  List<T> findAll();

  /**
   * Finds a record by its ID.
   *
   * @param id record ID
   * @return the matching record, or an empty result if it does not exist
   */
  Optional<T> findById(int id);

  /**
   * Adds a new record.
   *
   * @param entity new record
   * @throws ConflictException if ID already exists
   */
  void create(T entity);

  /**
   * Updates an existing record.
   *
   * @param entity updated record
   * @return true if a record was updated
   */
  boolean update(T entity);

  /**
   * Deletes a record.
   *
   * @param id record ID
   * @return true if a record was deleted
   */
  boolean delete(int id);
}
