package com.lexbridge.decision;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class CoreGateway {
  private final RestClient client;
  private final String credential;
  private final AuthService auth;

  CoreGateway(
      RestClient.Builder b,
      @Value("${app.core-url}") String url,
      @Value("${app.core-credential}") String c,
      AuthService a) {
    client = b.baseUrl(url).build();
    credential = c;
    auth = a;
  }

  void record(Actor actor, UUID matterId, String type) {
    client
        .post()
        .uri("/core/matters/{id}/records", matterId)
        .header("X-Core-Credential", credential)
        .header("X-Scope-Token", auth.scopeToken(actor, matterId))
        .body(Map.of("type", type))
        .retrieve()
        .toBodilessEntity();
  }
}
