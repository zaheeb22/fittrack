package com.fittrack.web;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

// This turns a password into a salted hash that cannot be used as the original password.
final class Passwords {
  private static final int ITERATIONS = 600000;

  private Passwords() {}

  static String hash(String password) {
    if (password == null || password.length() < 16 || password.length() > 200)
      throw new IllegalArgumentException("Use a password of 16 to 200 characters");
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return Base64.getEncoder().encodeToString(salt)
        + ":"
        + Base64.getEncoder().encodeToString(derive(password, salt));
  }

  static boolean matches(String password, String saved) {
    if (password == null || password.length() > 200) return false;
    String[] parts = saved.split(":", -1);
    if (parts.length != 2) return false;
    return MessageDigest.isEqual(
        derive(password, Base64.getDecoder().decode(parts[0])),
        Base64.getDecoder().decode(parts[1]));
  }

  private static byte[] derive(String password, byte[] salt) {
    var spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (java.security.GeneralSecurityException error) {
      throw new IllegalStateException("Password protection is unavailable", error);
    } finally {
      spec.clearPassword();
    }
  }
}
