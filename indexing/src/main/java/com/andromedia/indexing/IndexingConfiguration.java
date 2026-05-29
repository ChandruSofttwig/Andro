package com.andromedia.indexing;

import com.andromedia.ingestion.ChunkingService;
import com.andromedia.ingestion.SemanticBoundaryChunker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IndexingConfiguration {

  @Bean
  ChunkingService chunkingService() {
    return new SemanticBoundaryChunker();
  }
}
