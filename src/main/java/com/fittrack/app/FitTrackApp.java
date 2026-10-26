package com.fittrack.app;

import com.fittrack.model.Member;
import com.fittrack.model.Workout;
import com.fittrack.service.WorkoutService;

/** Main entry point for the FitTrack application. */
public final class FitTrackApp {

  private FitTrackApp() {}

  /**
   * Starts the FitTrack demonstration.
   *
   * @param args --demo, --memory (default), or --mysql
   * @throws java.io.IOException if the HTTP port cannot be bound
   */
  public static void main(String[] args) throws java.io.IOException {
    // This selects the console demo or the HTTP server mode from the startup argument.
    if (args.length > 0 && args[0].equals("--demo")) {
      demo();
      return;
    }
    boolean mysql = args.length > 0 && args[0].equals("--mysql");
    if (args.length > 0 && !mysql && !args[0].equals("--memory"))
      throw new IllegalArgumentException("Use --demo, --memory or --mysql");
    var settings = com.fittrack.web.ServerSettings.fromEnvironment(System.getenv());
    com.fittrack.service.FitnessService service;
    // MySQL mode stores records in the database; memory mode keeps them until the app stops.
    if (mysql) {
      String url = required("FITTRACK_DB_URL"),
          user = required("FITTRACK_DB_USER"),
          password = required("FITTRACK_DB_PASSWORD");
      com.fittrack.repository.ConnectionFactory connections =
          () -> java.sql.DriverManager.getConnection(url, user, password);
      service =
          new com.fittrack.service.FitnessService(
              com.fittrack.repository.JdbcRepository.members(connections),
              com.fittrack.repository.JdbcRepository.workouts(connections),
              com.fittrack.repository.JdbcRepository.challenges(connections));
      service.members(); // Fail at startup if schema or connection is unavailable.
    } else
      service =
          new com.fittrack.service.FitnessService(
              new com.fittrack.repository.InMemoryRepository<>(),
              new com.fittrack.repository.InMemoryRepository<>(),
              new com.fittrack.repository.InMemoryRepository<>());
    // This starts the web server and closes its resources when the application stops.
    var server = new com.fittrack.web.FitnessHttpServer(service, settings);
    Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    server.start();
    System.out.println(
        "FitTrack "
            + (mysql ? "MySQL" : "volatile memory")
            + " server listening on "
            + settings.getHost()
            + ":"
            + server.getPort());
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Set " + name);
    return value;
  }

  private static void demo() {

    System.out.println("=== FITTRACK ===");
    System.out.println("Fitness Challenge Tracker");
    System.out.println();

    Member member = new Member(1, "Alex Johnson", "alex@example.com", 25);

    member.displayDetails();

    WorkoutService workoutService = new WorkoutService();

    workoutService.addWorkout(new Workout(1, "Running", 30));

    workoutService.addWorkout(new Workout(2, "Cycling", 45));

    workoutService.addWorkout(new Workout(3, "Walking", 60));

    System.out.println();
    System.out.println("=== WORKOUT RECORDS ===");

    for (Workout workout : workoutService.getWorkouts()) {
      workout.displayWorkout();
    }

    System.out.println();
    System.out.println("Total Workouts: " + workoutService.getWorkoutCount());

    System.out.println(
        "Total Exercise Time: " + workoutService.calculateTotalMinutes() + " minutes");

    Workout longest = workoutService.findLongestWorkout();

    if (longest != null) {
      System.out.println(
          "Longest Workout: "
              + longest.getActivityType()
              + " ("
              + longest.getDurationMinutes()
              + " minutes)");
    }
  }
}
