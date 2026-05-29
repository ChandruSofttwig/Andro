package com.andromedia.search;

import com.andromedia.common.ChunkUnitType;
import com.andromedia.common.IndexConstants;
import org.apache.lucene.document.Document;

final class IndexDocumentMapper {

  private IndexDocumentMapper() {}

  static SearchHit toSearchHit(Document document, float score) {
    String unitTypeValue = document.get(IndexConstants.FIELD_UNIT_TYPE);
    ChunkUnitType unitType =
        unitTypeValue == null ? ChunkUnitType.FILE : ChunkUnitType.valueOf(unitTypeValue);
    return new SearchHit(
        document.get(IndexConstants.FIELD_PATH),
        document.get(IndexConstants.FIELD_FILE_NAME),
        score,
        document.get(IndexConstants.FIELD_CHUNK_ID),
        document.get(IndexConstants.FIELD_CLASS_NAME),
        document.get(IndexConstants.FIELD_METHOD_NAME),
        unitType,
        parseLine(document.get(IndexConstants.FIELD_START_LINE)),
        parseLine(document.get(IndexConstants.FIELD_END_LINE)));
  }

  private static int parseLine(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return 0;
    }
  }
}
