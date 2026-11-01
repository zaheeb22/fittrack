package com.fittrack.repository;

import com.fittrack.model.MemberAccount;
import java.sql.SQLException;
import java.util.*;

/** Stores one sign-in account for each member in MySQL. */
public final class JdbcAccountRepository implements Repository<MemberAccount> {
  private final ConnectionFactory connections;

  /**
   * Creates account storage using the supplied connection method.
   *
   * @param connections method that opens a database connection
   */
  public JdbcAccountRepository(ConnectionFactory connections) {
    this.connections = Objects.requireNonNull(connections);
  }

  /** Creates the account table on first use when the database account allows it. */
  public void initialise() {
    try (var c = connections.open();
        var s = c.prepareStatement("SELECT member_id FROM member_accounts WHERE 1=0")) {
      s.executeQuery().close();
      return;
    } catch (SQLException e) {
      if (e.getSQLState() == null || !e.getSQLState().startsWith("42"))
        throw new StorageException(e);
    }
    try (var c = connections.open();
        var s = c.createStatement()) {
      s.executeUpdate(
          "CREATE TABLE IF NOT EXISTS member_accounts (member_id INT PRIMARY KEY, "
              + "username VARCHAR(100) NOT NULL UNIQUE, password_hash VARCHAR(200) NOT NULL, "
              + "FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE)");
    } catch (SQLException e) {
      throw new StorageException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<MemberAccount> findAll() {
    try (var c = connections.open();
        var s =
            c.prepareStatement(
                "SELECT member_id,username,password_hash FROM member_accounts ORDER BY member_id");
        var rows = s.executeQuery()) {
      var accounts = new ArrayList<MemberAccount>();
      while (rows.next())
        accounts.add(new MemberAccount(rows.getInt(1), rows.getString(2), rows.getString(3)));
      return accounts;
    } catch (SQLException e) {
      throw new StorageException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public Optional<MemberAccount> findById(int id) {
    try (var c = connections.open();
        var s =
            c.prepareStatement(
                "SELECT member_id,username,password_hash FROM member_accounts WHERE member_id=?")) {
      s.setInt(1, id);
      try (var rows = s.executeQuery()) {
        return rows.next()
            ? Optional.of(new MemberAccount(rows.getInt(1), rows.getString(2), rows.getString(3)))
            : Optional.empty();
      }
    } catch (SQLException e) {
      throw new StorageException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void create(MemberAccount account) {
    change(account, true);
  }

  /** {@inheritDoc} */
  @Override
  public boolean update(MemberAccount account) {
    return change(account, false);
  }

  private boolean change(MemberAccount account, boolean create) {
    // Values are passed separately from SQL so they cannot change the SQL command.
    String sql =
        create
            ? "INSERT INTO member_accounts (username,password_hash,member_id) VALUES (?,?,?)"
            : "UPDATE member_accounts SET username=?,password_hash=? WHERE member_id=?";
    try (var c = connections.open();
        var s = c.prepareStatement(sql)) {
      s.setString(1, account.username());
      s.setString(2, account.passwordHash());
      s.setInt(3, account.memberId());
      return s.executeUpdate() > 0;
    } catch (SQLException e) {
      if (e.getSQLState() != null && e.getSQLState().startsWith("23"))
        throw new ConflictException("Username or member account already exists");
      throw new StorageException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean delete(int id) {
    try (var c = connections.open();
        var s = c.prepareStatement("DELETE FROM member_accounts WHERE member_id=?")) {
      s.setInt(1, id);
      return s.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new StorageException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean deleteMatching(int id, java.util.function.Predicate<MemberAccount> permitted) {
    try (var c = connections.open()) {
      c.setAutoCommit(false);
      try {
        boolean removed = false;
        try (var find =
            c.prepareStatement(
                "SELECT member_id,username,password_hash FROM member_accounts WHERE member_id=? FOR"
                    + " UPDATE")) {
          find.setInt(1, id);
          try (var rows = find.executeQuery()) {
            if (rows.next()
                && permitted.test(
                    new MemberAccount(rows.getInt(1), rows.getString(2), rows.getString(3)))) {
              try (var remove =
                  c.prepareStatement("DELETE FROM member_accounts WHERE member_id=?")) {
                remove.setInt(1, id);
                removed = remove.executeUpdate() > 0;
              }
            }
          }
        }
        c.commit();
        return removed;
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new StorageException(e);
    }
  }
}
