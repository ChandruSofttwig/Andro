package com.andromedia.indexing;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = LuceneIndexingService.class)
public class IndexingConfiguration {}
