package com.andromedia.indexing;

import java.time.Duration;
import java.time.Instant;

public record IndexingStats(
    long filesVisited,
    long filesIndexed,
    long chunksIndexed,
    long chunksEmbedded,
    long oversizedChunks,
    long bytesIndexed,
    Instant startedAt,
    Instant finishedAt) {

  public Duration duration() {
    return Duration.between(startedAt, finishedAt);
  }
}
