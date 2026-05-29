package com.andromedia.common;

import java.util.Set;

public final class IndexConstants {

  public static final String INDEX_PATH_PROPERTY = "andromedia.index.path";
  public static final int PROJECT_ID_HEX_LENGTH = 8;

  public static final String FIELD_CHUNK_ID = "chunkId";
  public static final String FIELD_PATH = "path";
  public static final String FIELD_FILE_NAME = "fileName";
  public static final String FIELD_EXTENSION = "extension";
  public static final String FIELD_LANGUAGE = "language";
  public static final String FIELD_CONTENT = "content";
  public static final String FIELD_CLASS_NAME = "className";
  public static final String FIELD_METHOD_NAME = "methodName";
  public static final String FIELD_UNIT_TYPE = "unitType";
  public static final String FIELD_START_LINE = "startLine";
  public static final String FIELD_END_LINE = "endLine";
  public static final String FIELD_EMBEDDING = "embedding";

  public static final Set<String> DEFAULT_EXTENSIONS =
      Set.of("java", "kt", "kts", "groovy");

  private IndexConstants() {}
}
