package com.andromedia.indexing;

import java.time.Duration;
import java.time.Instant;

public record IndexingStats(
    long filesVisited,
    long filesIndexed,
    long bytesIndexed,
    Instant startedAt,
    Instant finishedAt) {

  public Duration duration() {
    return Duration.between(startedAt, finishedAt);
  }
}
