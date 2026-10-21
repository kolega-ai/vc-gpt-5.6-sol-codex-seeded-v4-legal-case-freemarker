package com.lexbridge.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/core")
class CoreController {
  private final byte[] key;
  private final String credential;
  private final Map<UUID, List<Map<String, Object>>> records = new ConcurrentHashMap<>();

  CoreController(@Value("${app.signing-key}") String k, @Value("${app.credential}") String c) {
    key = k.getBytes(StandardCharsets.UTF_8);
    credential = c;
  }

  @PostMapping("/matters/{matterId}/records")
  ResponseEntity<?> record(
      @PathVariable UUID matterId,
      @RequestHeader("X-Core-Credential") String supplied,
      @RequestHeader("X-Scope-Token") String token,
      @RequestBody Map<String, String> payload) {
    Scope s = verify(supplied, token);
    if (!s.matterId().equals(matterId))
      return ResponseEntity.status(403).body(Map.of("code", "SCOPE_MISMATCH"));
    Map<String, Object> row =
        Map.of(
            "id",
            UUID.randomUUID(),
            "type",
            payload.getOrDefault("type", "CASE_EVENT"),
            "recordedAt",
            Instant.now());
    records.computeIfAbsent(matterId, k -> new ArrayList<>()).add(row);
    return ResponseEntity.status(201).body(row);
  }

  @GetMapping("/matters/{matterId}/records")
  ResponseEntity<?> list(
      @PathVariable UUID matterId,
      @RequestHeader("X-Core-Credential") String supplied,
      @RequestHeader("X-Scope-Token") String token) {
    Scope s = verify(supplied, token);
    if (!s.matterId().equals(matterId))
      return ResponseEntity.status(403).body(Map.of("code", "SCOPE_MISMATCH"));
    return ResponseEntity.ok(records.getOrDefault(matterId, List.of()));
  }

  private Scope verify(String supplied, String token) {
    if (!MessageDigest.isEqual(
        credential.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
      throw new CoreDenied("CORE_CREDENTIAL_INVALID");
    try {
      String[] p = token.split("\\.");
      String body = new String(Base64.getUrlDecoder().decode(p[0]), StandardCharsets.UTF_8);
      if (p.length != 2
          || !MessageDigest.isEqual(
              sign(body).getBytes(StandardCharsets.US_ASCII),
              p[1].getBytes(StandardCharsets.US_ASCII))) throw new Exception();
      String[] v = body.split(":");
      Scope s = new Scope(UUID.fromString(v[0]), UUID.fromString(v[1]), Long.parseLong(v[2]));
      if (Instant.ofEpochSecond(s.exp()).isBefore(Instant.now())) throw new Exception();
      return s;
    } catch (Exception e) {
      throw new CoreDenied("SCOPE_TOKEN_INVALID");
    }
  }

  private String sign(String v) throws Exception {
    Mac m = Mac.getInstance("HmacSHA256");
    m.init(new SecretKeySpec(key, "HmacSHA256"));
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(m.doFinal(v.getBytes(StandardCharsets.UTF_8)));
  }

  record Scope(UUID actorId, UUID matterId, long exp) {}

  @ResponseStatus(HttpStatus.FORBIDDEN)
  static class CoreDenied extends RuntimeException {
    CoreDenied(String c) {
      super(c);
    }
  }

  @ExceptionHandler(CoreDenied.class)
  ResponseEntity<Map<String, String>> denied(CoreDenied e) {
    return ResponseEntity.status(403).body(Map.of("code", e.getMessage()));
  }
}
