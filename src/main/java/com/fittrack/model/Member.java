package com.fittrack.model;

/**
 * Represents a registered member of the FitTrack Fitness Challenge Tracker application.
 *
 * <p>Stores the member's ID, name, email and age.
 */
public final class Member implements Identifiable {

  // These fields store the member details. They cannot change after the object is created.
  private final int memberId;
  private final String name;
  private final String email;
  private final int age;

  /**
   * Constructs a new fitness member.
   *
   * @param memberId unique member ID
   * @param name member's full name
   * @param email member's email address
   * @param age member's age
   */
  public Member(int memberId, String name, String email, int age) {

    // This checks the member details before the object is created.
    if (memberId <= 0) {
      throw new IllegalArgumentException("Member ID must be positive");
    }

    if (name == null || name.isBlank() || name.length() > 200) {
      throw new IllegalArgumentException("Name must contain 1 to 200 characters");
    }

    if (email == null || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
      throw new IllegalArgumentException("Invalid email address");
    }

    if (age <= 0) {
      throw new IllegalArgumentException("Age must be positive");
    }

    // This saves the checked details and removes spaces at the start and end of text.
    this.memberId = memberId;
    this.name = name.strip();
    this.email = email.strip();
    this.age = age;
  }

  /** {@inheritDoc} */
  @Override
  public int getId() {
    return memberId;
  }

  /**
   * Returns the member's unique ID.
   *
   * @return member ID
   */
  public int getMemberId() {
    return memberId;
  }

  /**
   * Returns the member's name.
   *
   * @return full name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the member's email address.
   *
   * @return email address
   */
  public String getEmail() {
    return email;
  }

  /**
   * Returns the member's age.
   *
   * @return age in years
   */
  public int getAge() {
    return age;
  }

  /** Displays the member's information. */
  public void displayDetails() {
    System.out.println("Member ID: " + memberId);
    System.out.println("Name: " + name);
    System.out.println("Email: " + email);
    System.out.println("Age: " + age);
  }
}
