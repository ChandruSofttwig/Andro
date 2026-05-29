package com.andromedia.common;

public record RetrievalReference(
    String chunkId,
    String path,
    String fileName,
    String className,
    String methodName,
    ChunkUnitType unitType,
    int startLine,
    int endLine) {}
