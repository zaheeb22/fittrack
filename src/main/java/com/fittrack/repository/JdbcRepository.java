package com.fittrack.repository;

import com.fittrack.model.*;
import java.sql.*;
import java.util.*;

/**
 * Adds, reads, changes and deletes database records using JDBC.
 * Prepared statements keep user values separate from SQL commands.
 * Connections, statements and results are closed after use.
 *
 * @param <T> entity type
 */
public final class JdbcRepository<T extends Identifiable> implements Repository<T> {
  private final ConnectionFactory connections;
  private final String table;
  private final String columns;
  private final String assignments;
  private final String placeholders;
  private final RowCodec<T> codec;

  private interface RowCodec<E> {
    E read(ResultSet row) throws SQLException;

    void bind(PreparedStatement statement, E entity) throws SQLException;
  }

  private JdbcRepository(
      ConnectionFactory connections, String table, String columns, RowCodec<T> codec) {
    this.connections = connections;
    this.table = table;
    this.columns = columns;
    this.codec = codec;
    var fields = columns.split(",");
    placeholders = String.join(",", Collections.nCopies(fields.length, "?"));
    assignments = String.join(",", Arrays.stream(fields).map(f -> f + "=?").toList());
  }

  /**
   * Creates member storage.
   *
   * @param connections connection supplier
   * @return repository
   */
  public static JdbcRepository<Member> members(ConnectionFactory connections) {
    return new JdbcRepository<>(
        connections,
        "members",
        "name,email,age",
        new RowCodec<>() {
          public Member read(ResultSet r) throws SQLException {
            return new Member(
                r.getInt("id"), r.getString("name"), r.getString("email"), r.getInt("age"));
          }

          public void bind(PreparedStatement s, Member m) throws SQLException {
            s.setString(1, m.getName());
            s.setString(2, m.getEmail());
            s.setInt(3, m.getAge());
          }
        });
  }

  /**
   * Creates workout storage.
   *
   * @param connections connection supplier
   * @return repository
   */
  public static JdbcRepository<Workout> workouts(ConnectionFactory connections) {
    return new JdbcRepository<>(
        connections,
        "workouts",
        "member_id,activity_type,duration_minutes,workout_date",
        new RowCodec<>() {
          public Workout read(ResultSet r) throws SQLException {
            return new Workout(
                r.getInt("id"),
                r.getInt("member_id"),
                r.getString("activity_type"),
                r.getInt("duration_minutes"),
                r.getString("workout_date"));
          }

          public void bind(PreparedStatement s, Workout w) throws SQLException {
            s.setInt(1, w.getMemberId());
            s.setString(2, w.getActivityType());
            s.setInt(3, w.getDurationMinutes());
            s.setString(4, w.getDate());
          }
        });
  }

  /**
   * Creates database storage that reads and saves both challenge types.
   *
   * @param connections connection supplier
   * @return repository
   */
  public static JdbcRepository<Challenge> challenges(ConnectionFactory connections) {
    return new JdbcRepository<>(
        connections,
        "challenges",
        "member_id,name,type,target,start_date,end_date",
        new RowCodec<>() {
          public Challenge read(ResultSet r) throws SQLException {
            int id = r.getInt("id"), owner = r.getInt("member_id"), target = r.getInt("target");
            String name = r.getString("name"),
                start = r.getString("start_date"),
                end = r.getString("end_date");
            return switch (r.getString("type")) {
              case "MINUTES" -> new MinutesChallenge(id, owner, name, target, start, end);
              case "SESSIONS" -> new SessionsChallenge(id, owner, name, target, start, end);
              default -> throw new SQLException("Unknown challenge type");
            };
          }

          public void bind(PreparedStatement s, Challenge c) throws SQLException {
            s.setInt(1, c.getMemberId());
            s.setString(2, c.getName());
            s.setString(3, c.getType());
            s.setInt(4, c.getTarget());
            s.setString(5, c.getStartDate());
            s.setString(6, c.getEndDate());
          }
        });
  }

  private RuntimeException failure(SQLException e) {
    return e.getSQLState() != null && e.getSQLState().startsWith("23")
        ? new ConflictException("Duplicate ID or related record conflict")
        : new StorageException(e);
  }

  /** {@inheritDoc} */
  @Override
  public List<T> findAll() {
    try (var c = connections.open();
        var s = c.prepareStatement("SELECT id," + columns + " FROM " + table + " ORDER BY id");
        var r = s.executeQuery()) {
      List<T> result = new ArrayList<>();
      while (r.next()) result.add(codec.read(r));
      return List.copyOf(result);
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public Optional<T> findById(int id) {
    try (var c = connections.open();
        var s = c.prepareStatement("SELECT id," + columns + " FROM " + table + " WHERE id=?")) {
      s.setInt(1, id);
      try (var r = s.executeQuery()) {
        return r.next() ? Optional.of(codec.read(r)) : Optional.empty();
      }
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void create(T entity) {
    write("INSERT INTO " + table + " (" + columns + ",id) VALUES (" + placeholders + ",?)", entity);
  }

  /** {@inheritDoc} */
  @Override
  public boolean update(T entity) {
    return write("UPDATE " + table + " SET " + assignments + " WHERE id=?", entity) > 0;
  }

  private int write(String sql, T entity) {
    try (var c = connections.open();
        var s = c.prepareStatement(sql)) {
      codec.bind(s, entity);
      s.setInt(columns.split(",").length + 1, entity.getId());
      return s.executeUpdate();
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean delete(int id) {
    try (var c = connections.open();
        var s = c.prepareStatement("DELETE FROM " + table + " WHERE id=?")) {
      s.setInt(1, id);
      return s.executeUpdate() > 0;
    } catch (SQLException e) {
      throw failure(e);
    }
  }
}
