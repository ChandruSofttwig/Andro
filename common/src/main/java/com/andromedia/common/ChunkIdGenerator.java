package com.andromedia.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ChunkIdGenerator {

  private ChunkIdGenerator() {}

  public static String generate(
      String absolutePath, ChunkUnitType unitType, String className, String methodName, int startLine) {
    String payload =
        absolutePath
            + '|'
            + unitType.name()
            + '|'
            + nullToEmpty(className)
            + '|'
            + nullToEmpty(methodName)
            + '|'
            + startLine;
    return sha256Hex(payload).substring(0, ChunkConstants.CHUNK_ID_HEX_LENGTH);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }
}
