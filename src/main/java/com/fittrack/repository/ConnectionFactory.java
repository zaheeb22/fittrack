package com.fittrack.repository;

import java.sql.Connection;
import java.sql.SQLException;

/** Opens a separate database connection for each operation. The calling code closes it after use. */
@FunctionalInterface
public interface ConnectionFactory {
  /**
   * Opens a connection.
   *
   * @return connection
   * @throws SQLException on connection failure
   */
  Connection open() throws SQLException;
}
