package com.andromedia.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = {
      "com.andromedia.cli",
      "com.andromedia.indexing",
      "com.andromedia.search",
      "com.andromedia.llm"
    })
public class AndromediaCliApplication {

  public static void main(String[] args) {
    SpringApplication.run(AndromediaCliApplication.class, args);
  }
}
