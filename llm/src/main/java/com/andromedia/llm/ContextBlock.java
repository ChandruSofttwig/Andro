package com.andromedia.llm;

public record ContextBlock(String chunkId, String path, String label, String content) {}
