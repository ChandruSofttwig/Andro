package com.andromedia.common;

import java.util.Set;

public final class IndexConstants {

  public static final String INDEX_PATH_PROPERTY = "andromedia.index.path";
  public static final int PROJECT_ID_HEX_LENGTH = 8;

  public static final String FIELD_PATH = "path";
  public static final String FIELD_FILE_NAME = "fileName";
  public static final String FIELD_EXTENSION = "extension";
  public static final String FIELD_LANGUAGE = "language";
  public static final String FIELD_CONTENT = "content";

  public static final Set<String> DEFAULT_EXTENSIONS =
      Set.of("java", "kt", "kts", "groovy");

  private IndexConstants() {}
}
