package com.andromedia.search;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = LuceneSearchService.class)
public class SearchConfiguration {}
