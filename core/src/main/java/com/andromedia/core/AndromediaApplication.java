package com.andromedia.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.andromedia")
public class AndromediaApplication {

  public static void main(String[] args) {
    SpringApplication.run(AndromediaApplication.class, args);
  }
}
