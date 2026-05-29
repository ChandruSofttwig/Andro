package com.andromedia.search;

import com.andromedia.common.ChunkUnitType;
import com.andromedia.common.RetrievalReference;

public record SearchHit(
    String path,
    String fileName,
    float score,
    String chunkId,
    String className,
    String methodName,
    ChunkUnitType unitType,
    int startLine,
    int endLine,
    Float bm25Score,
    Float semanticScore) {

  public SearchHit(
      String path,
      String fileName,
      float score,
      String chunkId,
      String className,
      String methodName,
      ChunkUnitType unitType,
      int startLine,
      int endLine) {
    this(
        path,
        fileName,
        score,
        chunkId,
        className,
        methodName,
        unitType,
        startLine,
        endLine,
        null,
        null);
  }

  public String locationLabel() {
    if (startLine > 0 && endLine > 0) {
      return fileName + ":" + startLine + "-" + endLine;
    }
    return fileName;
  }

  public String symbolLabel() {
    if (methodName != null && !methodName.isBlank()) {
      return methodName;
    }
    if (className != null && !className.isBlank()) {
      return className;
    }
    return "";
  }

  public RetrievalReference toRetrievalReference() {
    return new RetrievalReference(
        chunkId, path, fileName, className, methodName, unitType, startLine, endLine);
  }
}
