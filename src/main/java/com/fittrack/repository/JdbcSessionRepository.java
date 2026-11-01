package com.fittrack.repository;

import java.sql.SQLException;
import java.util.Objects;

/** Saves session hashes in MySQL so all hosted app instances share the same sign-ins. */
public final class JdbcSessionRepository implements SessionRepository {
  private final ConnectionFactory connections;

  /**
   * Creates session storage using the supplied database connection method.
   *
   * @param connections method that opens a database connection
   */
  public JdbcSessionRepository(ConnectionFactory connections) {
    this.connections = Objects.requireNonNull(connections);
  }

  /**
   * Checks the session table and creates it on first use if the database account allows this.
   * Existing tables only need read and write access. The schema script can also create the table.
   */
  public void initialise() {
    try (var c = connections.open();
        var s = c.prepareStatement("SELECT token_hash FROM login_sessions WHERE 1=0")) {
      s.executeQuery().close();
      return;
    } catch (SQLException error) {
      if (error.getSQLState() == null || !error.getSQLState().startsWith("42"))
        throw new StorageException(error);
    }
    try (var c = connections.open(); var s = c.createStatement()) {
      s.executeUpdate(
          "CREATE TABLE IF NOT EXISTS login_sessions ("
              + "token_hash CHAR(64) PRIMARY KEY, expires_at BIGINT NOT NULL)");
    } catch (SQLException error) {
      throw new StorageException(error);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void save(String tokenHash, long expiresAt, long now) {
    // Only a hash is saved. The database does not hold the browser's session cookie.
    try (var c = connections.open();
        var old = c.prepareStatement("DELETE FROM login_sessions WHERE expires_at <= ?");
        var add = c.prepareStatement("INSERT INTO login_sessions (token_hash,expires_at) VALUES (?,?)")) {
      old.setLong(1, now);
      old.executeUpdate();
      add.setString(1, tokenHash);
      add.setLong(2, expiresAt);
      add.executeUpdate();
    } catch (SQLException error) {
      throw new StorageException(error);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isActive(String tokenHash, long now) {
    try (var c = connections.open();
        var s = c.prepareStatement(
            "SELECT token_hash FROM login_sessions WHERE token_hash=? AND expires_at>?")) {
      s.setString(1, tokenHash);
      s.setLong(2, now);
      try (var rows = s.executeQuery()) {
        return rows.next();
      }
    } catch (SQLException error) {
      throw new StorageException(error);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void delete(String tokenHash) {
    try (var c = connections.open();
        var s = c.prepareStatement("DELETE FROM login_sessions WHERE token_hash=?")) {
      s.setString(1, tokenHash);
      s.executeUpdate();
    } catch (SQLException error) {
      throw new StorageException(error);
    }
  }
}
