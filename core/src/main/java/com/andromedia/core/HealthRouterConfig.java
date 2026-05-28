package com.andromedia.core;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class HealthRouterConfig {

  @Bean
  RouterFunction<ServerResponse> healthRouter() {
    return route(
        GET("/api/v1/health"),
        request -> ServerResponse.ok().bodyValue(Map.of("status", "UP")));
  }
}
