package com.fittrack.model;

/**
 * A member's sign-in details. Only the password hash is stored.
 *
 * @param memberId owner of this account
 * @param username unique sign-in name
 * @param passwordHash salted password hash
 */
public record MemberAccount(int memberId, String username, String passwordHash)
    implements Identifiable {
  /**
   * Checks the account details before saving.
   *
   * @param memberId owner of this account
   * @param username unique sign-in name
   * @param passwordHash salted password hash
   */
  public MemberAccount {
    if (memberId < 1) throw new IllegalArgumentException("Member ID must be positive");
    if (username == null || !username.matches("[a-zA-Z0-9@._+-]{3,100}"))
      throw new IllegalArgumentException("Username must be 3 to 100 letters, numbers or @ . _ + -");
    username = username.toLowerCase(java.util.Locale.ROOT);
    if (passwordHash == null || passwordHash.isBlank())
      throw new IllegalArgumentException("Password hash is required");
  }

  /**
   * @return the member ID used to find the account
   */
  @Override
  public int getId() {
    return memberId;
  }
}
