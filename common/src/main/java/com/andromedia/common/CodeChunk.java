package com.andromedia.common;

public record CodeChunk(
    String chunkId,
    String content,
    String path,
    String fileName,
    String extension,
    String language,
    String className,
    String methodName,
    ChunkUnitType unitType,
    int startLine,
    int endLine,
    boolean oversized) {}
